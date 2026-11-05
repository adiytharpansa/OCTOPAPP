package cloud.octop.mobile

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

fun normalizeUrl(raw: String): String {
    var u = raw.trim().trimEnd('/')
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    return u
}

private fun errorText(code: Int) = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP -> "Alamat server tidak ditemukan"
    WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> "Server tidak merespons"
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Koneksi aman (HTTPS) gagal"
    else -> "Tidak bisa terhubung ke server"
}

@Composable
fun rememberOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(ConnectivityManager::class.java) }
    val state = remember { mutableStateOf(cm.activeNetwork != null) }
    DisposableEffect(Unit) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = false }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return state
}

@Composable
fun OctopApp(
    prefs: Prefs,
    locked: Boolean,
    onUnlock: () -> Unit,
    onFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Boolean,
    onRequestNotifPermission: () -> Unit,
    onKeepAliveChanged: (Boolean) -> Unit,
    onQuit: () -> Unit,
    onMoveToBack: () -> Unit
) {
    var url by remember { mutableStateOf(prefs.url) }
    var servers by remember { mutableStateOf(prefs.servers) }
    var textZoom by remember { mutableIntStateOf(prefs.textZoom) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    fun connect(input: String) {
        val fixed = normalizeUrl(input)
        prefs.url = fixed
        prefs.rememberServer(fixed)
        servers = prefs.servers
        url = fixed
        showSettings = false
    }

    Box(Modifier.fillMaxSize()) {
        if (url.isBlank()) {
            SetupScreen(saved = servers, onConnect = { connect(it) })
        } else {
            key(url, reloadKey) {
                WebScreen(
                    url = url,
                    prefs = prefs,
                    textZoom = textZoom,
                    onFileChooser = onFileChooser,
                    onOpenSettings = { showSettings = true },
                    onQuit = onQuit,
                    onMoveToBack = onMoveToBack
                )
            }
        }

        if (showSettings && url.isNotBlank()) {
            SettingsScreen(
                prefs = prefs,
                url = url,
                servers = servers,
                onClose = { showSettings = false },
                onConnect = { connect(it) },
                onRemoveServer = { s ->
                    prefs.servers = prefs.servers - s
                    servers = prefs.servers
                },
                onChangeServer = {
                    prefs.url = ""
                    url = ""
                    showSettings = false
                },
                onTextZoom = { z -> textZoom = z; prefs.textZoom = z },
                onClearData = {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    reloadKey++
                    showSettings = false
                },
                onRequestNotifPermission = onRequestNotifPermission,
                onKeepAliveChanged = onKeepAliveChanged
            )
        }

        if (locked) LockScreen(onUnlock)
    }
}

@Composable
fun SetupScreen(saved: List<String>, onConnect: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Octop", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Masukkan alamat server Octop kamu.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Alamat server") },
            placeholder = { Text("192.168.1.10:8088") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (text.isNotBlank()) onConnect(text) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Hubungkan") }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Server tersimpan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            saved.forEach { s ->
                TextButton(onClick = { onConnect(s) }) { Text(s) }
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { }) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Octop terkunci", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text("Buka kunci") }
        }
    }
}

@Composable
private fun TopBar(
    status: Color,
    host: String,
    onReload: () -> Unit,
    onSettings: () -> Unit,
    onBrowser: () -> Unit,
    onQuit: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(status, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("Octop", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            host,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text("⋮", fontSize = 20.sp) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Muat ulang") }, onClick = { open = false; onReload() })
                DropdownMenuItem(text = { Text("Pengaturan") }, onClick = { open = false; onSettings() })
                DropdownMenuItem(text = { Text("Buka di browser") }, onClick = { open = false; onBrowser() })
                DropdownMenuItem(text = { Text("Keluar") }, onClick = { open = false; onQuit() })
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun WebScreen(
    url: String,
    prefs: Prefs,
    textZoom: Int,
    onFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Boolean,
    onOpenSettings: () -> Unit,
    onQuit: () -> Unit,
    onMoveToBack: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var failMsg by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    val online by rememberOnline()
    val serverUri = remember(url) { Uri.parse(url) }
    val serverHost = serverUri.host
    val origin = remember(url) { "${serverUri.scheme}://${serverUri.authority}" }
    val tracker = remember(url) { ReplyTracker(context.applicationContext, prefs) }
    val docStart = remember { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    BackHandler { if (canGoBack) webView?.goBack() else onMoveToBack() }
    LaunchedEffect(online) { if (online && failed) { failed = false; webView?.reload() } }

    val status = when {
        failed || !online -> Color(0xFFD32F2F)
        progress in 1..99 -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TopBar(
            status = status,
            host = serverUri.authority ?: "",
            onReload = { failed = false; webView?.reload() },
            onSettings = onOpenSettings,
            onBrowser = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url))) }
                catch (_: Exception) { }
            },
            onQuit = onQuit
        )
        if (!online) {
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tidak ada koneksi internet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.textZoom = textZoom
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(tracker, "OctopBridge")
                        if (docStart) {
                            WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, setOf(origin))
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                                failed = false
                                if (!docStart) v.evaluateJavascript(HOOK_JS, null)
                            }
                            override fun onReceivedError(
                                v: WebView, r: WebResourceRequest, e: WebResourceError
                            ) {
                                if (r.isForMainFrame) { failMsg = errorText(e.errorCode); failed = true }
                            }
                            override fun onReceivedHttpError(
                                v: WebView, r: WebResourceRequest, e: WebResourceResponse
                            ) {
                                if (r.isForMainFrame && e.statusCode >= 500) {
                                    failMsg = "Server mengembalikan error ${e.statusCode}"
                                    failed = true
                                }
                            }
                            override fun onReceivedSslError(
                                v: WebView, handler: SslErrorHandler, error: SslError
                            ) {
                                handler.cancel()
                                failMsg = "Sertifikat HTTPS tidak tepercaya"
                                failed = true
                            }
                            override fun doUpdateVisitedHistory(v: WebView, u: String, reload: Boolean) {
                                canGoBack = v.canGoBack()
                            }
                            override fun shouldOverrideUrlLoading(
                                v: WebView, r: WebResourceRequest
                            ): Boolean {
                                val host = r.url.host
                                if (host == null || host == serverHost) return false
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                                catch (_: Exception) { }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(v: WebView, p: Int) { progress = p }
                            override fun onShowFileChooser(
                                v: WebView,
                                cb: ValueCallback<Array<Uri>>,
                                params: FileChooserParams
                            ): Boolean = onFileChooser(cb, params.createIntent())
                        }
                        setDownloadListener { dl, ua, cd, mime, _ ->
                            try {
                                val name = URLUtil.guessFileName(dl, cd, mime)
                                val req = DownloadManager.Request(Uri.parse(dl))
                                    .setMimeType(mime)
                                    .addRequestHeader("User-Agent", ua)
                                    .addRequestHeader(
                                        "Cookie", CookieManager.getInstance().getCookie(dl) ?: ""
                                    )
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS, name
                                    )
                                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                                    .enqueue(req)
                                Toast.makeText(context, "Mengunduh $name", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal mengunduh", Toast.LENGTH_SHORT).show()
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { it.settings.textZoom = textZoom },
                onRelease = { it.destroy() }
            )

            if (failed) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(failMsg, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(url, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { failed = false; webView?.reload() }) { Text("Coba lagi") }
                    TextButton(onClick = onOpenSettings) { Text("Pengaturan") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs,
    url: String,
    servers: List<String>,
    onClose: () -> Unit,
    onConnect: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onChangeServer: () -> Unit,
    onTextZoom: (Int) -> Unit,
    onClearData: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    onKeepAliveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var server by remember { mutableStateOf(url) }
    var notify by remember { mutableStateOf(prefs.notifyReplies) }
    var keepAlive by remember { mutableStateOf(prefs.keepAlive) }
    var lock by remember { mutableStateOf(prefs.appLock) }
    var zoom by remember { mutableIntStateOf(prefs.textZoom) }
    var showDiag by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) { }) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            TextButton(onClick = onClose) { Text("‹ Kembali") }
            Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

            SectionTitle("Server")
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Alamat server") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(onClick = { if (server.isNotBlank()) onConnect(server) }) { Text("Simpan dan muat ulang") }
                TextButton(onClick = onChangeServer) { Text("Server baru") }
            }
            if (servers.size > 1) {
                Text("Server tersimpan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                servers.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onConnect(s) }, modifier = Modifier.weight(1f)) {
                            Text(if (s == url) "$s  (aktif)" else s, maxLines = 1)
                        }
                        if (s != url) TextButton(onClick = { onRemoveServer(s) }) { Text("Hapus") }
                    }
                }
            }

            SectionTitle("Notifikasi")
            SwitchRow(
                "Notifikasi balasan AI",
                "Muncul saat AI selesai membalas dan aplikasi tidak sedang dibuka.",
                notify
            ) {
                notify = it; prefs.notifyReplies = it
                if (it) onRequestNotifPermission()
            }
            SwitchRow(
                "Jaga koneksi di latar belakang",
                "Menampilkan notifikasi tetap kecil agar sistem tidak menutup koneksi. Perlu aktif supaya notifikasi balasan terkirim saat layar mati.",
                keepAlive
            ) {
                keepAlive = it; prefs.keepAlive = it
                onKeepAliveChanged(it)
            }
            TextButton(onClick = {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(i)
            }) { Text("Suara dan getar (pengaturan sistem)") }
            TextButton(onClick = {
                Notifier.showReply(context, "Ini contoh notifikasi balasan AI. Kalau kamu melihat pesan ini, notifikasi sudah berfungsi.")
            }) { Text("Kirim notifikasi tes") }
            TextButton(onClick = { showDiag = true }) { Text("Diagnostik notifikasi") }

            SectionTitle("Tampilan")
            Text("Ukuran teks", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(90, 100, 115, 130).forEach { z ->
                    FilterChip(
                        selected = zoom == z,
                        onClick = { zoom = z; onTextZoom(z) },
                        label = { Text("$z%") }
                    )
                }
            }

            SectionTitle("Keamanan")
            SwitchRow(
                "Kunci aplikasi",
                "Minta sidik jari, wajah, atau PIN perangkat saat aplikasi dibuka kembali setelah 30 detik.",
                lock
            ) { lock = it; prefs.appLock = it }
            TextButton(onClick = { confirmClear = true }) {
                Text("Keluar akun dan hapus data web", color = MaterialTheme.colorScheme.error)
            }

            SectionTitle("Tentang")
            Text("Octop Mobile 1.2", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Klien tidak resmi untuk server Octop self-hosted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus data web?") },
            text = { Text("Sesi login dan data situs di aplikasi ini akan dihapus. Kamu perlu masuk lagi.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearData() }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }

    if (showDiag) {
        var frames by remember { mutableStateOf(Diagnostics.snapshot()) }
        val text = if (frames.isEmpty()) "Belum ada data. Kirim pesan ke AI dari dashboard, lalu buka lagi halaman ini."
        else frames.joinToString("\n\n")
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("Frame WebSocket terakhir") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("frames", text))
                    Toast.makeText(context, "Disalin", Toast.LENGTH_SHORT).show()
                }) { Text("Salin") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { Diagnostics.clear(); frames = emptyList() }) { Text("Kosongkan") }
                    TextButton(onClick = { showDiag = false }) { Text("Tutup") }
                }
            }
        )
    }
}
