package com.coconiss.pushledger.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coconiss.pushledger.data.TransactionRepository

class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        if (id <= 0L || category.isNullOrBlank()) return

        TransactionRepository(context).updateCategory(id, category)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id.toInt())
    }

    companion object {
        const val ACTION_SET_CATEGORY = "com.coconiss.pushledger.SET_CATEGORY"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_CATEGORY = "category"
    }
}
