package com.smspanel1.app

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smspanel1.app.data.*
import com.smspanel1.app.service.SmsForegroundService
import com.smspanel1.app.util.JalaliCalendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

// ==================== ViewModel - فیکس Race condition + lifecycleScope ====================
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

    private val _hasLoadedGroups = MutableStateFlow(false)
    private val _hasLoadedContacts = MutableStateFlow(false)
    private val _hasLoadedCampaigns = MutableStateFlow(false)

    // فیکس مشکل 3: حلقه بی‌نهایت - با hasLoaded جلوی تکرار بی‌نهایت را میگیریم
    fun loadAll(api: ApiService) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // لود همه با هم - فیکس مشکل 4: گروه‌ها فقط در مخاطبین لود می‌شد
                val g = async { try { api.getGroups() } catch (e: Exception) { emptyList() } }
                val c = async { try { api.getContacts() } catch (e: Exception) { emptyList() } }
                val q = async { try { api.getQueue() } catch (e: Exception) { emptyList() } }
                val t = async { try { api.getTemplates() } catch (e: Exception) { emptyList() } }
                val cnt = async { try { api.getQueueCounts() } catch (e: Exception) { QueueCounts() } }

                _groups.value = g.await()
                _contacts.value = c.await()
                _campaigns.value = q.await()
                _templates.value = t.await()
                _counts.value = cnt.await()

                _hasLoadedGroups.value = true
                _hasLoadedContacts.value = true
                _hasLoadedCampaigns.value = true

            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadGroups(api: ApiService) {
        if (_hasLoadedGroups.value) return // جلوگیری از حلقه بی‌نهایت
        viewModelScope.launch {
            try {
                _groups.value = api.getGroups()
                _hasLoadedGroups.value = true
            } catch (e: Exception) {
                _error.value = e.message
                _hasLoadedGroups.value = true // حتی اگر خطا بود، دیگه تلاش بی‌نهایت نکن
            }
        }
    }

    fun loadContacts(api: ApiService) {
        if (_hasLoadedContacts.value) return
        viewModelScope.launch {
            try {
                _contacts.value = api.getContacts()
                _hasLoadedContacts.value = true
            } catch (e: Exception) {
                _error.value = e.message
                _hasLoadedContacts.value = true
            }
        }
    }

    fun loadCampaigns(api: ApiService) {
        if (_hasLoadedCampaigns.value) return
        viewModelScope.launch {
            try {
                _campaigns.value = api.getQueue()
                _counts.value = api.getQueueCounts()
                _hasLoadedCampaigns.value = true
            } catch (e: Exception) {
                _error.value = e.message
                _hasLoadedCampaigns.value = true
            }
        }
    }

    fun refresh(api: ApiService) {
        _hasLoadedGroups.value = false
        _hasLoadedContacts.value = false
        _hasLoadedCampaigns.value = false
        loadAll(api)
    }

    fun clearError() { _error.value = null }
}

// ==================== MainActivity - Compose + Scaffold فیکس FAB ====================
class MainActivity : ComponentActivity() {

    private var sendJob: Job? = null
    private val scope = MainScope()

    // فیکس مشکل 7: مجوز SMS با ActivityResultContracts
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "مجوز ارسال پیامک رد شد - ارسال کار نمی‌کند", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // درخواست مجوز امن
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        setContent {
            MaterialTheme {
                val api = remember { com.smspanel1.app.data.ApiService(this) }
                var isLoggedIn by remember { mutableStateOf(api.isLoggedIn()) }

                if (!isLoggedIn) {
                    LoginScreen(
                        api = api,
                        onLoggedIn = { isLoggedIn = true }
                    )
                } else {
                    MainScreen(
                        api = api,
                        onLogout = {
                            // فیکس مشکل 2: خروج، ارسال را متوقف می‌کند
                            sendJob?.cancel()
                            sendJob = null
                            SmsForegroundService.stop(this)
                            api.logout()
                            isLoggedIn = false
                        },
                        onStartService = {
                            // فیکس مشکل 8: Foreground Service
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                                }
                            }
                            SmsForegroundService.start(this)
                            startAutoSender(api)
                        }
                    )
                }
            }
        }
    }

    // فیکس مشکل 8 + 9 + 10: ارسال مقاوم با backoff و try/catch داخل حلقه
    private fun startAutoSender(api: com.smspanel1.app.data.ApiService) {
        sendJob?.cancel()
        sendJob = scope.launch(Dispatchers.IO) {
            var backoff = 15000L
            var consecutiveErrors = 0
            while (isActive) {
                try {
                    if (!api.isLoggedIn()) { delay(30000); continue }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        delay(30000); continue // فیکس 7: اگر مجوز نیست، failed نکن
                    }
                    val batch = api.fetchQueue(5)
                    if (batch.isEmpty()) {
                        consecutiveErrors = 0; backoff = 15000L
                        delay(15000); continue
                    }
                    for (msg in batch) {
                        if (!isActive) break
                        try {
                            val sent = com.smspanel1.app.service.SmsSender.sendSms(this@MainActivity, msg.receiver, msg.body)
                            try {
                                api.updateQueueStatus(msg.id, if (sent) "sent" else "failed")
                                consecutiveErrors = 0
                            } catch (e: Exception) {
                                // فیکس 9: خطای update بقیه را رها نمی‌کند
                            }
                        } catch (e: Exception) {
                            try { api.updateQueueStatus(msg.id, "failed") } catch (_: Exception) {}
                        }
                        delay(4000)
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    if (e.message?.contains("401") == true || e.message?.contains("منقضی") == true) {
                        // فیکس 10: 401 را بی‌نهایت تکرار نکن
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "توکن منقضی - دوباره وارد شوید", Toast.LENGTH_LONG).show()
                        }
                        break
                    }
                    backoff = (backoff * 1.5).toLong().coerceAtMost(120000L)
                    delay(backoff)
                }
            }
        }
    }

    override fun onDestroy() {
        sendJob?.cancel()
        scope.cancel()
        super.onDestroy()
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
    var isButtonEnabled by remember { mutableStateOf(true) } // فیکس ضد کلیک مضاعف
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
            Text("نسخه 6.0 - شمسی + ضدکرش", fontSize = 12.sp, color = Color.Gray)
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
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            isLoading = false
                            isButtonEnabled = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isButtonEnabled
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
                    "راهنما: آدرس سایت خودت را وارد کن (نه mahdinikzad.ir)\nبعد از نصب افزونه وردپرس، حتما پیوندهای یکتا را ذخیره بزن",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )
            }
        }
    }
}

// ==================== Main Screen - 2 تب + Scaffold FAB درست ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    api: com.smspanel1.app.data.ApiService,
    onLogout: () -> Unit,
    onStartService: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=پیام‌ها, 1=مخاطبین
    var showNewMessage by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf<QueueItem?>(null) }
    val groups by vm.groups.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val campaigns by vm.campaigns.collectAsState()
    val templates by vm.templates.collectAsState()
    val counts by vm.counts.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.loadAll(api)
        onStartService()
    }

    // فیکس مشکل 4: Back کار می‌کند
    BackHandler(enabled = showNewMessage || showDetail != null) {
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
                    IconButton(onClick = { vm.refresh(api) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی", tint = Color.White)
                    }
                    IconButton(onClick = {
                        // منوی خروج
                        android.app.AlertDialog.Builder(context)
                            .setItems(arrayOf("تنظیمات", "خروج")) { _, which ->
                                if (which == 1) onLogout()
                            }.show()
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "بیشتر", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF075E54))
            )
        },
        bottomBar = {
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
        },
        floatingActionButton = {
            // فیکس مشکل 1: FAB الان داخل Scaffold و شناور روی محتوا
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
                    contactsCount = contacts.size,
                    onBack = { showNewMessage = false },
                    onSend = { groupIds, body ->
                        // فیکس مشکل 5: چند گروه + ضد کلیک مضاعف + نمایش تعداد
                        if (groupIds.isEmpty()) {
                            Toast.makeText(context, "یک گروه انتخاب کن", Toast.LENGTH_SHORT).show()
                            return@NewMessageScreen
                        }
                        if (body.isBlank()) {
                            Toast.makeText(context, "متن را بنویس", Toast.LENGTH_SHORT).show()
                            return@NewMessageScreen
                        }
                        // تایید قبل از ارسال انبوه
                        android.app.AlertDialog.Builder(context)
                            .setTitle("تایید ارسال")
                            .setMessage("ارسال به ${groupIds.size} گروه؟")
                            .setPositiveButton("ارسال") { _, _ ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val res = api.buildQueue(groupIds, body)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "✅ ${res.queued} پیام در صف", Toast.LENGTH_LONG).show()
                                            showNewMessage = false
                                            vm.refresh(api)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            .setNegativeButton("لغو", null)
                            .show()
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

// ==================== Chats Screen - با Empty State و شمسی ====================
@Composable
fun ChatsScreen(
    campaigns: List<QueueItem>,
    counts: QueueCounts,
    isLoading: Boolean,
    error: String?,
    onCampaignClick: (QueueItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // فیلتر چیپ‌ها - فیکس مشکل متوسط
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("همه (${campaigns.size})") })
            FilterChip(selected = false, onClick = {}, label = { Text("در انتظار ${counts.pending}") })
            FilterChip(selected = false, onClick = {}, label = { Text("ارسالی ${counts.sent}") })
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
        } else if (campaigns.isEmpty()) {
            // فیکس مشکل 3: پیام Empty State نمایش داده می‌شود
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Text("هنوز پیامی نفرستادی", fontWeight = FontWeight.Bold)
                    Text("روی + بزن تا اولین کمپین را بسازی", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(campaigns.reversed()) { item ->
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
                            // فیکس: جلوگیری از "null" string
                            val body = item.body.takeIf { it != "null" && it.isNotEmpty() } ?: ""
                            Text(body.take(40), fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(JalaliCalendar.chatTime(item.created_at), fontSize = 11.sp, color = Color.Gray)
                            Text(
                                when (item.status) {
                                    "sent" -> "✓✓"
                                    "sending" -> "✓"
                                    "pending" -> "◷"
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

// ==================== Contacts Screen - فیلتر درست ====================
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
            }.take(100) // فیکس: نمایش 100 تا + اطلاع

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("مخاطبی یافت نشد", color = Color.Gray)
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
                    if (contacts.size > 100) {
                        item {
                            Text(
                                "نمایش 100 از ${contacts.size} مخاطب - از جستجو استفاده کن",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== New Message - چند گروه + ضد کلیک مضاعف ====================
@Composable
fun NewMessageScreen(
    groups: List<Group>,
    templates: List<Template>,
    contactsCount: Int,
    onBack: () -> Unit,
    onSend: (List<Int>, String) -> Unit
) {
    var selectedGroups by remember { mutableStateOf(setOf<Int>()) }
    var messageBody by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(0) }
    var isSending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("ارسال جدید", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("تاریخ شمسی: ${JalaliCalendar.formatFull(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))}", fontSize = 11.sp, color = Color.Gray)
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
        if (groups.isEmpty()) Text("گروهی نیست - اول گروه بساز", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))
        Text("۲. قالب (اختیاری):", fontWeight = FontWeight.Bold)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedTemplate == 0) "بدون قالب" else templates.getOrNull(selectedTemplate - 1)?.title ?: "بدون قالب")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("بدون قالب") }, onClick = { selectedTemplate = 0; expanded = false })
                templates.forEachIndexed { idx, t ->
                    DropdownMenuItem(text = { Text(t.title) }, onClick = {
                        selectedTemplate = idx + 1
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
        Text("${messageBody.length} کاراکتر - ${if (messageBody.any { it.code > 127 }) (messageBody.length / 70 + 1) else (messageBody.length / 160 + 1)} بخش", fontSize = 11.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (isSending) return@Button
                isSending = true
                onSend(selectedGroups.toList(), messageBody)
                // فیکس ضد کلیک مضاعف: بعد از 2 ثانیه دوباره فعال
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    isSending = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending
        ) {
            if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            else Text("📤 ارسال به ${selectedGroups.size} گروه - $contactsCount مخاطب")
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
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
