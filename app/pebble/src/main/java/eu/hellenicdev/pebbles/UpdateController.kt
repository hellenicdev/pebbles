package eu.hellenicdev.pebbles

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data object Current : UpdatePhase
    data class Available(val version: String, val notes: String, val url: String, val name: String) : UpdatePhase
    data class MissingAsset(val version: String, val expectedName: String) : UpdatePhase
    data class Downloading(val version: String) : UpdatePhase
    data class NeedsPermission(val file: File) : UpdatePhase
    data class Failed(val message: String) : UpdatePhase
}

class UpdateController(
    private val app: Context,
    private val scope: CoroutineScope,
    checkOnLaunch: Boolean,
) {
    var phase by mutableStateOf<UpdatePhase>(UpdatePhase.Idle)
    var checkOnLaunch by mutableStateOf(checkOnLaunch)
    val updateReady: Boolean
        get() = phase is UpdatePhase.Available || phase is UpdatePhase.MissingAsset

    fun check(manual: Boolean) {
        if (phase is UpdatePhase.Checking || phase is UpdatePhase.Downloading) return
        scope.launch {
            phase = UpdatePhase.Checking
            val result = withContext(Dispatchers.IO) {
                runCatching { interpretRelease(readUrl(LATEST_RELEASE_URL), BuildConfig.VERSION_NAME) }
            }
            phase = result.fold(
                onSuccess = { lookup ->
                    when (lookup) {
                        UpdateLookup.Current -> if (manual) UpdatePhase.Current else UpdatePhase.Idle
                        is UpdateLookup.Available -> UpdatePhase.Available(
                            lookup.version,
                            lookup.notes,
                            lookup.apkUrl,
                            lookup.apkName,
                        )
                        is UpdateLookup.MissingAsset -> UpdatePhase.MissingAsset(lookup.version, lookup.expectedName)
                    }
                },
                onFailure = {
                    if (manual) UpdatePhase.Failed("Couldn't reach GitHub.") else UpdatePhase.Idle
                },
            )
        }
    }

    fun download(available: UpdatePhase.Available) {
        scope.launch {
            phase = UpdatePhase.Downloading(available.version)
            val file = File(app.cacheDir, "updates/${available.name}")
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    downloadTo(available.url, file)
                    file
                }
            }
            phase = saved.fold(
                onSuccess = { offerInstall(it) },
                onFailure = { UpdatePhase.Failed("The download didn't finish.") },
            )
        }
    }

    fun resumeInstall() {
        val waiting = phase as? UpdatePhase.NeedsPermission ?: return
        if (canInstallPackages(app)) {
            launchInstaller(waiting.file)
            phase = UpdatePhase.Idle
        }
    }

    private fun offerInstall(file: File): UpdatePhase {
        return if (canInstallPackages(app)) {
            launchInstaller(file)
            UpdatePhase.Idle
        } else {
            UpdatePhase.NeedsPermission(file)
        }
    }

    fun allowInstalls() {
        app.startActivity(unknownSourcesIntent(app))
    }

    private fun launchInstaller(file: File) {
        app.startActivity(installIntent(app, file))
    }
}
