package com.smspanel1.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.telephony.SmsMessage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smspanel1.app.data.*
import com.smspanel1.app.service.SmsForegroundService
import com.smspanel1.app.service.SendControl
import com.smspanel1.app.util.JalaliCalendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

// Session-scoped state; cancelled refreshes cannot replace newer results.
class MainViewModel : ViewModel() {
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _campaigns = MutableStateFlow<List<QueueItem>>(emptyList())
    val campaigns: StateFlow<List<QueueItem>> = _campaigns

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates

    private val _counts = MutableStateFlow(QueueCounts())
    val counts: StateFlow<QueueCounts> = _counts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var loadJob: Job? = null
    private var generation = 0

    fun loadAll(api: ApiService) {
        loadJob?.cancel()
        val request = ++generation
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                coroutineScope {
                    val groups = async { api.getGroups() }
                    val contacts = async { api.getContacts() }
                    val queue = async { api.getQueue() }
                    val templates = async { api.getTemplates() }
                    val counts = async { api.getQueueCounts() }
                    val g = groups.await()
                    val c = contacts.await()
                    val q = queue.await()
                    val t = templates.await()
                    val count = counts.await()
                    ensureActive()
                    if (request == generation) {
                        _groups.value = g
                        _contacts.value = c
                        _campaigns.value = q
                        _templates.value = t
                        _counts.value = count
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) _error.value = e.message ?: "دریافت اطلاعات ناموفق بود"
            } finally {
                if (request == generation) _isLoading.value = false
            }
        }
    }

    fun refresh(api: ApiService) = loadAll(api)

    fun reset() {
        generation++
        loadJob?.cancel()
        _groups.value = emptyList()
        _contacts.value = emptyList()
        _campaigns.value = emptyList()
        _templates.value = emptyList()
        _counts.value = QueueCounts()
        _error.value = null
        _isLoading.value = false
    }
}

class MainActivity : ComponentActivity() {
    private var pendingStart: SendControl.Permit? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startSending() }

    private val smsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestNotificationsAndStart()
        else {
            pendingStart?.let { SmsForegroundService.cancelRequest(it) }
            pendingStart = null
            Toast.makeText(this, "مجوز پیامک داده نشد؛ ارسال شروع نشد", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestSending() {
        val api = ApiService(applicationContext)
        if (!api.isLoggedIn()) return
        if (pendingStart?.let { SmsForegroundService.isAuthorized(it, api.sessionId) } == true) return
        pendingStart = SmsForegroundService.requestStart(api.sessionId)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermission.launch(Manifest.permission.SEND_SMS)
        } else requestNotificationsAndStart()
    }

    private fun requestNotificationsAndStart() {
        val permit = pendingStart ?: return
        if (!SmsForegroundService.isAuthorized(permit, ApiService(applicationContext).sessionId)) {
            pendingStart = null
            SmsForegroundService.cancelRequest(permit)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startSending()
    }

    private fun startSending() {
        val permit = pendingStart ?: return
        pendingStart = null
        try {
            val api = ApiService(applicationContext)
            if (api.isLoggedIn() && SmsForegroundService.isAuthorized(permit, api.sessionId)) {
                SmsForegroundService.start(this, permit)
            } else SmsForegroundService.cancelRequest(permit)
        } catch (_: Exception) {
            SmsForegroundService.cancelRequest(permit)
            Toast.makeText(this, "شروع سرویس ممکن نشد؛ مجوزها و نشست را بررسی کنید", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingStart?.let {
            outState.putString("pending_send_token", it.token)
            outState.putString("pending_send_session", it.sessionId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) pendingStart?.let { SmsForegroundService.cancelRequest(it) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = savedInstanceState?.getString("pending_send_token")
        val session = savedInstanceState?.getString("pending_send_session")
        if (token != null && session != null) pendingStart = SendControl.Permit(token, session)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val apiResult = remember { runCatching { ApiService(applicationContext) } }
                    val api = apiResult.getOrNull()
                    if (api == null) {
                        Text("ذخیره‌سازی امن در دسترس نیست. برنامه را دوباره باز کنید؛ اطلاعات ورود به‌صورت غیرامن ذخیره نمی‌شود.",
                            modifier = Modifier.padding(24.dp))
                    } else {
                        val sessionChanges by ApiService.sessionChanges.collectAsStateWithLifecycle()
                        val loggedIn = remember(sessionChanges) { api.isLoggedIn() }
                        if (!loggedIn) {
                            LaunchedEffect(sessionChanges) { SmsForegroundService.stop(this@MainActivity) }
                            LoginScreen(api = api, onLoggedIn = {})
                        } else {
                            val session = api.sessionId
                            key(session) {
                                val sessionApi = remember { ApiService(applicationContext, session) }
                                MainScreen(api = sessionApi,
                                    onLogout = {
                                        SmsForegroundService.stop(this@MainActivity)
                                        pendingStart = null
                                        try {
                                            api.logout()
                                        } catch (_: Exception) {
                                            Toast.makeText(this@MainActivity, "ذخیره‌سازی نشست خطا دارد؛ پیش از استفاده مجدد داده‌های برنامه را پاک کنید", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onStartService = { requestSending() })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== Login Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(api: com.smspanel1.app.data.ApiService, onLoggedIn: () -> Unit) {
    var siteUrl by remember { mutableStateOf(api.siteUrl) }
    var username by remember { mutableStateOf(api.username) }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isButtonEnabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF39C12)),
                contentAlignment = Alignment.Center
            ) {
                Text("💬", fontSize = 48.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("پنل پیامکی", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("نسخه ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = siteUrl,
                onValueChange = { siteUrl = it },
                label = { Text("آدرس سایت https://...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("نام کاربری") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("رمز عبور") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!isButtonEnabled) return@Button
                    isButtonEnabled = false
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            api.login(siteUrl, username, password)
                            onLoggedIn()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            isLoading = false
                            isButtonEnabled = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isButtonEnabled && siteUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                else Text("ورود")
            }

            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                Text(
                    "راهنما: آدرس HTTPS سایت خود را وارد کنید\nبعد از نصب افزونه وردپرس، حتما پیوندهای یکتا را ذخیره بزن",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )
            }
        }
    }
}

// Main screen: queue and contacts.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    api: com.smspanel1.app.data.ApiService,
    onLogout: () -> Unit,
    onStartService: () -> Unit,
    vm: MainViewModel = viewModel(key = api.sessionId)
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=پیام‌ها, 1=مخاطبین
    var showNewMessage by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf<QueueItem?>(null) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val campaigns by vm.campaigns.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val sendState by SmsForegroundService.state.collectAsStateWithLifecycle()
    val running = sendState.permit != null
    val serviceNotice by SmsForegroundService.notice.collectAsStateWithLifecycle()
    var sending by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Pair<List<Int>, String>?>(null) }

    DisposableEffect(vm) { onDispose { vm.reset() } }
    LaunchedEffect(api) { vm.loadAll(api) }

    confirmation?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!sending) confirmation = null },
            title = { Text("تأیید ایجاد صف") },
            text = { Text("برای ${draft.first.size} گروه صف ایجاد شود؟ فقط به مخاطبان دارای رضایت پیام بفرستید. هزینه اپراتور برای هر بخش محاسبه می‌شود.") },
            confirmButton = {
                TextButton(enabled = !sending, onClick = {
                    sending = true
                    scope.launch {
                        try {
                            val result = api.buildQueue(draft.first, draft.second)
                            Toast.makeText(context, "${result.queued} پیام در صف قرار گرفت", Toast.LENGTH_LONG).show()
                            showNewMessage = false
                            confirmation = null
                            vm.refresh(api)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Some groups may already be queued. Never retry automatically.
                            confirmation = null
                            vm.refresh(api)
                            Toast.makeText(context, "${e.message}؛ قبل از تلاش دوباره صف را بررسی کنید", Toast.LENGTH_LONG).show()
                        } finally {
                            sending = false
                        }
                    }
                }) { Text(if (sending) "در حال ثبت…" else "ایجاد صف") }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { confirmation = null }) { Text("لغو") } }
        )
    }

    BackHandler(enabled = !sending && (showNewMessage || showDetail != null)) {
        when {
            showDetail != null -> showDetail = null
            showNewMessage -> showNewMessage = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(api.username, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val today = JalaliCalendar.todayShamsi()
                        val months = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
                        Text(
                            "${today.day} ${months[today.month-1]} ${today.year} • ${counts.pending} در انتظار",
                            fontSize = 11.sp,
                            color = Color(0xFFD1D7DB)
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF39C12)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(api.username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (running) SmsForegroundService.stop(context) else onStartService()
                    }) { Text(if (running) "توقف ارسال" else "شروع ارسال", color = Color.White) }
                    IconButton(onClick = { vm.refresh(api) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی", tint = Color.White)
                    }
                    IconButton(onClick = {
                        // منوی خروج
                        android.app.AlertDialog.Builder(context)
                            .setItems(arrayOf("خروج")) { _, _ ->
                                onLogout()
                            }.show()
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "بیشتر", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF075E54))
            )
        },
        bottomBar = {
            Column {
                serviceNotice?.let { Text(it, modifier = Modifier.padding(8.dp), fontSize = 12.sp) }
                if (selectedTab == 1) error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Message, contentDescription = null) },
                        label = { Text("پیام‌ها") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                        label = { Text("مخاطبین") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewMessage = true },
                containerColor = Color(0xFF25D366)
            ) {
                Icon(Icons.Default.Add, contentDescription = "ارسال جدید", tint = Color.White)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                showDetail != null -> CampaignDetailScreen(
                    item = showDetail!!,
                    onBack = { showDetail = null }
                )
                showNewMessage -> NewMessageScreen(
                    groups = groups,
                    templates = templates,
                    contacts = contacts,
                    isSending = sending,
                    onBack = { if (!sending) showNewMessage = false },
                    onSend = { groupIds, body ->
                        if (!sending && confirmation == null && groupIds.isNotEmpty() && body.isNotBlank()) {
                            confirmation = groupIds to body
                        }
                    }
                )
                else -> {
                    when (selectedTab) {
                        0 -> ChatsScreen(
                            campaigns = campaigns,
                            counts = counts,
                            isLoading = isLoading,
                            error = error,
                            onCampaignClick = { showDetail = it },
                            onRefresh = { vm.refresh(api) }
                        )
                        1 -> ContactsScreen(
                            contacts = contacts,
                            groups = groups,
                            isLoading = isLoading,
                            onRefresh = { vm.refresh(api) }
                        )
                    }
                }
            }
        }
    }
}

// Queue list with status filters.
@Composable
fun ChatsScreen(
    campaigns: List<QueueItem>,
    counts: QueueCounts,
    isLoading: Boolean,
    error: String?,
    onCampaignClick: (QueueItem) -> Unit,
    onRefresh: () -> Unit
) {
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val visible = campaigns.filter { statusFilter == null || it.status == statusFilter }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("همه (${campaigns.size})") })
            FilterChip(selected = statusFilter == "pending", onClick = { statusFilter = "pending" }, label = { Text("در انتظار ${counts.pending}") })
            FilterChip(selected = statusFilter == "sending", onClick = { statusFilter = "sending" }, label = { Text("در حال ارسال ${counts.sending}") })
            FilterChip(selected = statusFilter == "sent", onClick = { statusFilter = "sent" }, label = { Text("ارسالی ${counts.sent}") })
            FilterChip(selected = statusFilter == "failed", onClick = { statusFilter = "failed" }, label = { Text("ناموفق ${counts.failed}") })
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("خطا: $error", color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) { Text("تلاش مجدد") }
            }
        } else if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Text(if (statusFilter == null) "هنوز پیامی در صف نیست" else "پیامی با این وضعیت یافت نشد", fontWeight = FontWeight.Bold)
                    if (statusFilter == null) Text("روی + بزن تا صف جدید بسازی", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible.reversed()) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCampaignClick(item) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (item.status == "sent") Color(0xFF25D366) else Color(0xFFF39C12)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.receiver.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.receiver, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val body = item.body.takeIf { it != "null" && it.isNotEmpty() } ?: ""
                            Text(body.take(40), fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(JalaliCalendar.chatTime(item.created_at), fontSize = 11.sp, color = Color.Gray)
                            Text(
                                when (item.status) {
                                    "sent" -> "✓ ارسال"
                                    "sending" -> "در حال ارسال / نامشخص"
                                    "pending" -> "◷"
                                    "failed" -> "ناموفق"
                                    else -> "•"
                                },
                                fontSize = 12.sp,
                                color = if (item.status == "sent") Color(0xFF53BDEB) else Color.Gray
                            )
                        }
                    }
                    Divider(color = Color(0xFFE9EDEF), thickness = 1.dp, modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// Contacts with search and group filters.
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    groups: List<Group>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("جستجوی نام یا شماره...") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            singleLine = true
        )

        // چیپ گروه‌ها - فیکس فیلتر
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroupId == -1,
                    onClick = { selectedGroupId = -1 },
                    label = { Text("همه") }
                )
                groups.forEach { g ->
                    FilterChip(
                        selected = selectedGroupId == g.id,
                        onClick = { selectedGroupId = g.id },
                        label = { Text(g.name) }
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = contacts.filter { c ->
                val matchGroup = selectedGroupId == -1 || c.group_id == selectedGroupId
                val matchSearch = searchText.isEmpty() || c.name.contains(searchText, true) || c.mobile.contains(searchText)
                matchGroup && matchSearch
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مخاطبی یافت نشد", color = Color.Gray)
                        TextButton(onClick = onRefresh) { Text("بروزرسانی") }
                    }
                }
            } else {
                LazyColumn {
                    items(filtered) { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF667781)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.name.take(1).uppercase(), color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.Bold)
                                Text(contact.mobile, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                }
            }
        }
    }
}

// Queue creation; request state is owned by the caller.
@Composable
fun NewMessageScreen(
    groups: List<Group>,
    templates: List<Template>,
    contacts: List<Contact>,
    isSending: Boolean,
    onBack: () -> Unit,
    onSend: (List<Int>, String) -> Unit
) {
    var selectedGroups by remember { mutableStateOf(setOf<Int>()) }
    var messageBody by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(0) }


    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("ارسال جدید", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("تاریخ شمسی: ${JalaliCalendar.formatFull(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))}", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        Text("۱. گروه‌ها را انتخاب کن:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        groups.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedGroups.contains(g.id),
                    onCheckedChange = { checked ->
                        selectedGroups = if (checked) selectedGroups + g.id else selectedGroups - g.id
                    }
                )
                Text("${g.name} (ID:${g.id})")
            }
        }
        if (groups.isEmpty()) Text("گروهی نیست؛ گروه‌ها را در پنل وردپرس بسازید", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))
        Text("۲. قالب (اختیاری):", fontWeight = FontWeight.Bold)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedTemplate == 0) "بدون قالب" else templates.find { it.id == selectedTemplate }?.title ?: "بدون قالب")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("بدون قالب") }, onClick = { selectedTemplate = 0; expanded = false })
                templates.forEach { t ->
                    DropdownMenuItem(text = { Text(t.title) }, onClick = {
                        selectedTemplate = t.id
                        messageBody = t.body
                        expanded = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("۳. متن پیام:", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            placeholder = { Text("سلام {نام} عزیز...") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        val parts = remember(messageBody) { if (messageBody.isEmpty()) 0 else SmsMessage.calculateLength(messageBody, false)[0] }
        Text("${messageBody.length} کاراکتر - $parts بخش", fontSize = 11.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (isSending) return@Button
                onSend(selectedGroups.toList(), messageBody)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending && selectedGroups.isNotEmpty() && messageBody.isNotBlank()
        ) {
            if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            else Text("ایجاد صف برای ${selectedGroups.size} گروه - ${contacts.count { it.group_id in selectedGroups }} مخاطب")
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}

@Composable
fun CampaignDetailScreen(item: QueueItem, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("جزئیات پیام", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("تاریخ شمسی:", fontSize = 12.sp, color = Color.Gray)
        Text(JalaliCalendar.formatFull(item.created_at), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("گیرنده:", fontWeight = FontWeight.Bold)
        Text(item.receiver)
        Spacer(modifier = Modifier.height(12.dp))
        Text("متن:", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6F6))) {
            Text(item.body, modifier = Modifier.padding(12.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("وضعیت: ${item.status}", color = Color.Gray)
        Text("ارسال‌شده به معنی تأیید ارسال توسط سیستم است، نه تحویل به گیرنده. وضعیت sending پس از وقفه ممکن است نامشخص باشد؛ بدون بررسی دوباره ارسال نکنید.", fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
