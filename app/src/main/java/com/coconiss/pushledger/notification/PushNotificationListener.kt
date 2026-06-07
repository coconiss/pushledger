package com.coconiss.pushledger.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.coconiss.pushledger.MainActivity
import com.coconiss.pushledger.R
import com.coconiss.pushledger.data.InsertResult
import com.coconiss.pushledger.data.SettingsStore
import com.coconiss.pushledger.data.TransactionRepository

class PushNotificationListener : NotificationListenerService() {
    private val repository by lazy { TransactionRepository(this) }
    private val settings by lazy { SettingsStore(this) }
    private val locationReader by lazy { LocationReader(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val allowed = settings.allowedPackages
        if (allowed.isNotEmpty() && sbn.packageName !in allowed) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()
        val appName = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()

        val parsed = NotificationParser.parse(
            sourcePackage = sbn.packageName,
            sourceAppName = appName,
            notificationKey = sbn.key,
            title = title,
            text = text,
            postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            repository = repository
        ) ?: return

        val location = if (settings.locationCaptureEnabled) locationReader.lastKnownSnapshot() else null
        when (val result = repository.insertIfNew(parsed, location)) {
            is InsertResult.Created -> showCategoryNotification(result.id, parsed.amount, parsed.merchantName, parsed.category)
            is InsertResult.Duplicate -> Unit
        }
    }

    private fun showCategoryNotification(id: Long, amount: Long, merchant: String?, recommended: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "거래 분류", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "새로 저장된 거래를 빠르게 분류합니다."
                }
            )
        }

        val openIntent = PendingIntent.getActivity(
            this,
            id.toInt(),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val categories = buildList {
            recommended?.takeIf { it != "미분류" }?.let { add(it) }
            add("식비")
            add("교통")
            add("더보기")
        }.distinct().take(3)

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("거래가 저장되었습니다")
            .setContentText("${merchant ?: "가맹점 미확인"} · ${"%,d".format(amount)}원")
            .setContentIntent(openIntent)
            .setAutoCancel(true)

        categories.forEach { category ->
            val intent = Intent(this, CategoryActionReceiver::class.java).apply {
                action = CategoryActionReceiver.ACTION_SET_CATEGORY
                putExtra(CategoryActionReceiver.EXTRA_TRANSACTION_ID, id)
                putExtra(CategoryActionReceiver.EXTRA_CATEGORY, if (category == "더보기") "미분류" else category)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                (id.toInt() * 31) + category.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification, category, pendingIntent)
        }

        manager.notify(id.toInt(), builder.build())
    }

    companion object {
        private const val CHANNEL_ID = "transaction_category"
    }
}
