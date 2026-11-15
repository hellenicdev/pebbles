package eu.hellenicdev.pebbles

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * First launch introduces the pebble, then setup. After that the app is
 * settings only: home, people, the pebble, and Wi-Fi. Touches stay on the device.
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
            MaterialTheme(colorScheme = if (dark) pebbleDark() else pebbleLight()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        when (phase) {
                            Phase.Intro -> IntroScreen(
                                colour = pebbleColor(settings.colour),
                                motion = motionAllowed(),
                                onDone = {
                                    store.markIntroSeen()
                                    phase = Phase.Setup
                                },
                            )
                            Phase.Setup -> SetupScreen(
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
                            )
                            Phase.App -> AppShell(
                                settings = settings,
                                status = status,
                                motion = motionAllowed(),
                                onChange = {
                                    settings = it
                                    latest = it
                                    store.save(it)
                                },
                                onPair = { askToPair() },
                            )
                        }
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
