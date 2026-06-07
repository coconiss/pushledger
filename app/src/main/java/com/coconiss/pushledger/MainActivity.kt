package com.coconiss.pushledger

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coconiss.pushledger.data.CategorySource
import com.coconiss.pushledger.data.LedgerSummary
import com.coconiss.pushledger.data.LedgerTransaction
import com.coconiss.pushledger.data.SettingsStore
import com.coconiss.pushledger.data.TransactionDirection
import com.coconiss.pushledger.data.TransactionRepository
import com.coconiss.pushledger.security.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PushLedgerRoot(this)
        }
    }
}

@Composable
private fun PushLedgerRoot(activity: Activity) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val lockManager = remember { AppLockManager(settings) }
    var unlocked by remember { mutableStateOf(!settings.appLockEnabled) }

    PushLedgerTheme {
        if (!unlocked) {
            LockScreen(
                lockManager = lockManager,
                activity = activity,
                onUnlocked = { unlocked = true }
            )
        } else {
            PushLedgerApp(settings = settings, lockManager = lockManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushLedgerApp(settings: SettingsStore, lockManager: AppLockManager) {
    val context = LocalContext.current
    val repository = remember { TransactionRepository(context) }
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var transactions by remember { mutableStateOf(emptyList<LedgerTransaction>()) }
    var summary by remember { mutableStateOf(emptySummary()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            val range = currentMonthRange()
            val recent = withContext(Dispatchers.IO) { repository.recent(100) }
            val newSummary = withContext(Dispatchers.IO) { repository.summary(range.first, range.second) }
            transactions = recent
            summary = newSummary
        }
    }

    LaunchedEffect(Unit) { reload() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("PushLedger", fontWeight = FontWeight.Bold)
                            Text("알림으로 작성되는 로컬 가계부", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        AppScreen.values().forEach { item ->
                            NavigationBarItem(
                                selected = item == screen,
                                onClick = { screen = item },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        AppScreen.values().forEach { item ->
                            NavigationRailItem(
                                selected = item == screen,
                                onClick = { screen = item },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        AppScreen.Home -> HomeScreen(summary, transactions.take(8), onCategory = { id, category ->
                            scope.launch(Dispatchers.IO) {
                                repository.updateCategory(id, category)
                                withContext(Dispatchers.Main) { reload() }
                            }
                        })
                        AppScreen.Transactions -> TransactionScreen(transactions, onCategory = { id, category ->
                            scope.launch(Dispatchers.IO) {
                                repository.updateCategory(id, category)
                                withContext(Dispatchers.Main) { reload() }
                            }
                        })
                        AppScreen.Settings -> SettingsScreen(
                            settings = settings,
                            lockManager = lockManager,
                            repository = repository,
                            onDataChanged = { reload() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    summary: LedgerSummary,
    recent: List<LedgerTransaction>,
    onCategory: (Long, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("이번 달 지출", money(summary.totalExpense), ExpenseRed, Modifier.weight(1f))
                SummaryCard("미분류", "${summary.uncategorizedCount}건", AccentGold, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("카테고리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    CategoryDonut(summary.categoryTotals)
                }
            }
        }
        item {
            Text("최근 거래", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(recent, key = { it.id }) { item ->
            TransactionRow(item, onCategory)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("식비", "교통", "쇼핑", "생활", "기타").forEach {
                    FilterChip(selected = false, onClick = {}, label = { Text(it) })
                }
            }
        }
    }
}

@Composable
private fun TransactionScreen(
    transactions: List<LedgerTransaction>,
    onCategory: (Long, String) -> Unit
) {
    var filter by remember { mutableStateOf("전체") }
    val filtered = when (filter) {
        "미분류" -> transactions.filter { it.category.isNullOrBlank() || it.category == "미분류" }
        "지출" -> transactions.filter { it.direction == TransactionDirection.EXPENSE }
        "수입" -> transactions.filter { it.direction == TransactionDirection.INCOME }
        else -> transactions
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "미분류", "지출", "수입").forEach {
                    FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
                }
            }
        }
        items(filtered, key = { it.id }) { item ->
            TransactionRow(item, onCategory)
        }
    }
}

@Composable
private fun TransactionRow(item: LedgerTransaction, onCategory: (Long, String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.merchantName ?: item.sourceAppName ?: "가맹점 미확인",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(date(item.capturedAt), style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
                Text(
                    money(item.amount),
                    color = if (item.direction == TransactionDirection.INCOME) IncomeGreen else ExpenseRed,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.category ?: "미분류", color = PrimaryGreen, style = MaterialTheme.typography.labelLarge)
                listOf("식비", "교통", "쇼핑", "기타").forEach { category ->
                    OutlinedButton(onClick = { onCategory(item.id, category) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(category)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: SettingsStore,
    lockManager: AppLockManager,
    repository: TransactionRepository,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    var notificationAccess by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var locationEnabled by remember { mutableStateOf(settings.locationCaptureEnabled) }
    var lockEnabled by remember { mutableStateOf(settings.appLockEnabled) }
    var pin by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        settings.locationCaptureEnabled = ok
        locationEnabled = ok
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PermissionCard(
                title = "알림 접근",
                body = "결제성 푸쉬 알림을 읽어 로컬 거래로 저장합니다. SMS 권한은 사용하지 않습니다.",
                enabled = notificationAccess,
                button = "설정 열기",
                onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    notificationAccess = isNotificationListenerEnabled(context)
                }
            )
        }
        item {
            PermissionCard(
                title = "앱 알림",
                body = "새 거래를 분류할 수 있는 최대 3개의 빠른 버튼을 표시합니다.",
                enabled = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                button = "권한 요청",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        item {
            ToggleCard(
                title = "저장 시점 위치 기록",
                body = "선택 기능입니다. 배터리를 아끼기 위해 마지막으로 알려진 위치만 저장합니다.",
                checked = locationEnabled,
                onCheckedChange = {
                    if (it) {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                        )
                    } else {
                        settings.locationCaptureEnabled = false
                        locationEnabled = false
                    }
                }
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("앱 잠금", fontWeight = FontWeight.Bold)
                            Text("PIN과 등록된 생체 인증으로 앱 진입을 보호합니다.", style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                        Switch(checked = lockEnabled, onCheckedChange = {
                            if (!it) {
                                lockManager.clear()
                                lockEnabled = false
                            }
                        })
                    }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = { Text("PIN 4~6자리") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (pin.length >= 4) {
                                lockManager.setPin(pin)
                                lockEnabled = true
                                pin = ""
                            }
                        },
                        enabled = pin.length >= 4
                    ) {
                        Text("잠금 설정")
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("로컬 데이터", fontWeight = FontWeight.Bold)
                    Text("거래와 설정은 기기 안에만 저장합니다. 백업 규칙에서도 DB와 설정을 제외했습니다.", style = MaterialTheme.typography.bodySmall, color = MutedText)
                    OutlinedButton(onClick = { showDeleteDialog = true }) {
                        Text("모든 거래 삭제")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        repository.deleteAll()
                        withContext(Dispatchers.Main) {
                            showDeleteDialog = false
                            onDataChanged()
                        }
                    }
                }) { Text("삭제") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
            title = { Text("모든 거래를 삭제할까요?") },
            text = { Text("로컬 DB의 거래와 파싱 로그가 삭제됩니다.") }
        )
    }
}

@Composable
private fun LockScreen(lockManager: AppLockManager, activity: Activity, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PushLedger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            if (error) Text("PIN이 맞지 않습니다.", color = ExpenseRed)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (lockManager.verify(pin)) onUnlocked() else error = true
                }) {
                    Text("열기")
                }
                OutlinedButton(
                    onClick = { lockManager.authenticateWithBiometric(activity, onUnlocked) {} },
                    enabled = lockManager.canUsePlatformBiometric()
                ) {
                    Text("생체 인증")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, enabled: Boolean, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MutedText)
                Text(if (enabled) "사용 가능" else "권한 필요", color = if (enabled) IncomeGreen else ExpenseRed, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun ToggleCard(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MutedText)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryDonut(totals: Map<String, Long>) {
    if (totals.isEmpty()) {
        Text("아직 이번 달 거래가 없습니다.", color = MutedText)
        return
    }
    val colors = listOf(PrimaryGreen, AccentGold, ExpenseRed, IncomeGreen, Color(0xFF6D6A75))
    val sum = totals.values.sum().toFloat().coerceAtLeast(1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp)) {
            var start = -90f
            totals.entries.take(5).forEachIndexed { index, entry ->
                val sweep = entry.value / sum * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(10f, 10f),
                    size = Size(size.width - 20f, size.height - 20f),
                    style = Stroke(width = 18f, cap = StrokeCap.Round)
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            totals.entries.take(5).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).background(colors[index % colors.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("${entry.key} ${money(entry.value)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private enum class AppScreen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("홈", Icons.Default.Home),
    Transactions("거래", Icons.Default.List),
    Settings("설정", Icons.Default.Settings)
}

@Composable
private fun PushLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryGreen,
            secondary = AccentGold,
            background = Background,
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = AndroidSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val component = ComponentName(context.packageName, "com.coconiss.pushledger.notification.PushNotificationListener")
    return !TextUtils.isEmpty(flat) && flat.split(":").any {
        ComponentName.unflattenFromString(it)?.flattenToString() == component.flattenToString()
    }
}

private fun currentMonthRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to (cal.timeInMillis - 1)
}

private fun emptySummary() = LedgerSummary(0, 0, 0, emptyMap())

private fun money(value: Long): String = NumberFormat.getCurrencyInstance(Locale.KOREA).format(value)

private fun date(value: Long): String = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(value))

private val Background = Color(0xFFF7F8F4)
private val PrimaryGreen = Color(0xFF2F6B5F)
private val AccentGold = Color(0xFFD59E3D)
private val ExpenseRed = Color(0xFFC85B4B)
private val IncomeGreen = Color(0xFF3E7C59)
private val Ink = Color(0xFF1F2723)
private val MutedText = Color(0xFF66706B)
