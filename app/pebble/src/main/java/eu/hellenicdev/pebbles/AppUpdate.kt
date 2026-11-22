package eu.hellenicdev.pebbles

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub release asset the updater installs. Example: pebbles-0.5.0.apk */
internal fun assetNameFor(version: String): String {
    val clean = version.trim().removePrefix("v").substringBefore("-").substringBefore("+")
    return "pebbles-$clean.apk"
}

internal fun semverParts(raw: String): List<Int> {
    val cleaned = raw.trim().removePrefix("v").substringBefore("-").substringBefore("+")
    val parts = cleaned.split(".").map { it.toIntOrNull() ?: 0 }
    return List(3) { index -> parts.getOrElse(index) { 0 } }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    if (candidate.isBlank()) return false
    val next = semverParts(candidate)
    val installed = semverParts(current)
    for (index in next.indices) {
        if (next[index] != installed[index]) return next[index] > installed[index]
    }
    return false
}

internal sealed interface UpdateLookup {
    data object Current : UpdateLookup
    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
    ) : UpdateLookup
    data class MissingAsset(val version: String, val notes: String, val expectedName: String) : UpdateLookup
}

internal fun interpretRelease(json: String, currentVersion: String): UpdateLookup {
    val release = try {
        JSONObject(json)
    } catch (_: JSONException) {
        return UpdateLookup.Current
    }
    val version = release.optString("tag_name").trim().removePrefix("v").substringBefore("-")
    val notes = release.optString("body").trim().ifBlank { "No notes with this release." }
    if (!isNewerVersion(version, currentVersion)) return UpdateLookup.Current
    val expected = assetNameFor(version)
    val url = assetUrl(release, expected)
    return if (url.isBlank()) {
        UpdateLookup.MissingAsset(version, notes, expected)
    } else {
        UpdateLookup.Available(version, notes, url, expected)
    }
}

private fun assetUrl(release: JSONObject, expectedName: String): String {
    val assets = release.optJSONArray("assets") ?: return ""
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        if (asset.optString("name").equals(expectedName, ignoreCase = true)) {
            return asset.optString("browser_download_url")
        }
    }
    return ""
}

internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/hellenicdev/pebbles/releases/latest"

internal fun readUrl(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "pebbles-android")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            error("GitHub answered ${connection.responseCode}")
        }
        return body
    } finally {
        connection.disconnect()
    }
}

internal fun downloadTo(url: String, destination: File) {
    destination.parentFile?.mkdirs()
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "pebbles-android")
        setRequestProperty("Accept", "application/vnd.android.package-archive")
    }
    try {
        if (connection.responseCode !in 200..299) error("Download answered ${connection.responseCode}")
        connection.inputStream.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    } finally {
        connection.disconnect()
    }
}

internal fun canInstallPackages(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

internal fun unknownSourcesIntent(context: Context): Intent {
    return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun installIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        clipData = ClipData.newRawUri("Pebbles update", uri)
    }
}
