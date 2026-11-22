package eu.hellenicdev.pebbles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun newerSemverIsDetected() {
        assertTrue(isNewerVersion("0.5.0", "0.4.0"))
        assertTrue(isNewerVersion("v0.10.0", "0.9.0"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(isNewerVersion("0.4.0", "0.4.0"))
        assertFalse(isNewerVersion("0.3.0", "0.4.0"))
        assertFalse(isNewerVersion("v0.4.0", "0.4.0"))
        assertEquals("pebbles-0.5.0.apk", assetNameFor("v0.5.0"))
    }

    @Test
    fun availableReleaseUsesTheNamedApk() {
        val json = """
            {
              "tag_name": "v0.5.0",
              "body": "A calmer home.\n",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://example.test/notes"},
                {"name": "pebbles-0.5.0.apk", "browser_download_url": "https://example.test/pebbles-0.5.0.apk"}
              ]
            }
        """.trimIndent()
        val lookup = interpretRelease(json, "0.4.0") as UpdateLookup.Available
        assertEquals("0.5.0", lookup.version)
        assertEquals("A calmer home.", lookup.notes)
        assertEquals("https://example.test/pebbles-0.5.0.apk", lookup.apkUrl)
        assertEquals("pebbles-0.5.0.apk", lookup.apkName)
    }

    @Test
    fun currentAndMissingAsset() {
        val current = """{"tag_name":"v0.4.0","body":"Same","assets":[]}"""
        assertTrue(interpretRelease(current, "0.4.0") is UpdateLookup.Current)
        val missing = """{"tag_name":"v0.6.0","body":"","assets":[{"name":"pebble-debug.apk","browser_download_url":"https://example.test/debug"}]}"""
        val lookup = interpretRelease(missing, "0.4.0") as UpdateLookup.MissingAsset
        assertEquals("pebbles-0.6.0.apk", lookup.expectedName)
        assertEquals("No notes with this release.", lookup.notes)
    }
}
