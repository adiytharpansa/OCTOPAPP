package cloud.octop.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var prefs: Prefs
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private var firstStart = true

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePathCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        filePathCallback = null
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Notifier.createChannels(this)
        if (prefs.notifyReplies) requestNotifPermission()

        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    OctopApp(
                        prefs = prefs,
                        locked = locked.value,
                        onUnlock = { authenticate() },
                        onFileChooser = { callback, intent ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback
                            try {
                                fileLauncher.launch(intent)
                                true
                            } catch (e: Exception) {
                                filePathCallback = null
                                false
                            }
                        },
                        onRequestNotifPermission = { requestNotifPermission() },
                        onKeepAliveChanged = { on ->
                            if (on) KeepAliveService.start(this) else KeepAliveService.stop(this)
                        },
                        onQuit = {
                            KeepAliveService.stop(this)
                            finishAndRemoveTask()
                        },
                        onMoveToBack = { moveTaskToBack(true) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
        Notifier.clearReplies(this)
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (prefs.appLock && (firstStart || away > 30_000)) {
            locked.value = true
            authenticate()
        }
        firstStart = false
        if (prefs.keepAlive) KeepAliveService.start(this)
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun authenticate() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            locked.value = false
            Toast.makeText(
                this, "Kunci layar perangkat belum diatur, kunci aplikasi dilewati", Toast.LENGTH_LONG
            ).show()
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked.value = false
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Buka Octop")
                .setSubtitle("Verifikasi identitas kamu")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}
