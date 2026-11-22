package eu.hellenicdev.pebbles

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * First launch introduces the pebble, then setup. After that Home shows the
 * group, and Settings holds everything else. Touches are sent on the pebble.
 */
class MainActivity : ComponentActivity() {
    private val statusState = mutableStateOf("Not paired yet.")
    private lateinit var latest: PebbleSettings
    private lateinit var pairer: PebblePairer
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.isNotEmpty() && grants.values.all { it }) pairer.start(latest)
        else statusState.value = "Bluetooth permission is required to pair."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(this)
        latest = store.load()
        pairer = PebblePairer(this) { message -> runOnUiThread { statusState.value = message } }
        val start = when {
            store.setupDone() -> Phase.App
            store.introSeen() -> Phase.Setup
            else -> Phase.Intro
        }
        setContent {
            val dark = isSystemInDarkTheme()
            val status by statusState
            var settings by remember { mutableStateOf(latest) }
            var phase by rememberSaveable { mutableStateOf(start) }
            var crashReports by remember { mutableStateOf(store.crashReportsEnabled()) }
            var includeIp by remember { mutableStateOf(store.crashReportsIncludeIp()) }
            var occasions by remember { mutableStateOf(store.enabledOccasions()) }
            val scope = rememberCoroutineScope()
            val updates = remember { UpdateController(applicationContext, scope, store.updateCheckOnLaunch()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, updates) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) updates.resumeInstall()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(phase) {
                if (phase == Phase.App && updates.checkOnLaunch) updates.check(manual = false)
            }
            BrandTheme(dark = dark, accent = pebbleColor(settings.colour)) {
                Surface(Modifier.fillMaxSize().imePadding(), color = PebblesBrand.current.bg) {
                    when (phase) {
                        Phase.Intro -> Box(Modifier.safeDrawingPadding()) { IntroScreen(
                            colour = pebbleColor(settings.colour),
                            motion = motionAllowed(),
                            onDone = {
                                store.markIntroSeen()
                                phase = Phase.Setup
                            },
                        ) }
                        Phase.Setup -> Box(Modifier.safeDrawingPadding()) { SetupScreen(
                            settings = settings,
                            status = status,
                            motion = motionAllowed(),
                            onChange = {
                                settings = it
                                latest = it
                                store.save(it)
                            },
                            onPair = { askToPair() },
                            onDone = {
                                store.markSetupDone()
                                phase = Phase.App
                            },
                        ) }
                        Phase.App -> AppNav(
                            settings = settings,
                            status = status,
                            motion = motionAllowed(),
                            crashReports = crashReports,
                            includeIp = includeIp,
                            occasions = occasions,
                            updates = updates,
                            onChange = {
                                settings = it
                                latest = it
                                store.save(it)
                            },
                            onPair = { askToPair() },
                            onCrashReports = { enabled ->
                                if (store.setCrashReportsEnabled(enabled)) {
                                    crashReports = enabled
                                    if (enabled) CrashReports.start(this@MainActivity)
                                    else CrashReports.stop()
                                }
                            },
                            onIncludeIp = { include ->
                                if (store.setCrashReportsIncludeIp(include)) includeIp = include
                            },
                            onOccasion = { occasion, enabled ->
                                store.setOccasionEnabled(occasion.id, enabled)
                                occasions = store.enabledOccasions()
                            },
                            onQuietUpdates = { enabled ->
                                store.setUpdateCheckOnLaunch(enabled)
                                updates.checkOnLaunch = enabled
                            },
                            onSendTestError = { CrashReports.sendTestError() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        pairer.close()
        super.onDestroy()
    }

    private fun askToPair() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.launch(needed)
    }
}

private enum class Phase { Intro, Setup, App }

@Composable
private fun motionAllowed(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale > 0f
}
