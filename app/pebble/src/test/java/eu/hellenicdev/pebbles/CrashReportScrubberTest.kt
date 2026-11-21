package eu.hellenicdev.pebbles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportScrubberTest {
    @Test
    fun redactsWifiNamesPasswordsPeopleAndEmail() {
        val raw = """{"ssid":"Cafe WiFi","pass":"hunter2","names":["Ada Lovelace"],"note":"ada@example.com"}"""
        val cleaned = CrashReportScrubber.redact(raw)
        assertFalse(cleaned.contains("Cafe"))
        assertFalse(cleaned.contains("hunter2"))
        assertFalse(cleaned.contains("Ada"))
        assertFalse(cleaned.contains("ada@example.com"))
        assertTrue(cleaned.contains("[filtered]"))
    }

    @Test
    fun redactsAssignmentForm() {
        val cleaned = CrashReportScrubber.redact("joined ssid=HomeNet password=secret")
        assertFalse(cleaned.contains("HomeNet"))
        assertFalse(cleaned.contains("secret"))
        assertTrue(cleaned.contains("ssid=[filtered]"))
        assertTrue(cleaned.contains("password=[filtered]"))
    }

    @Test
    fun leavesOrdinaryCrashText() {
        val text = "Could not send the settings."
        assertEquals(text, CrashReportScrubber.redact(text))
    }

    @Test
    fun sensitiveKeysCoverWifiPeopleAndMessages() {
        assertTrue(CrashReportScrubber.keyIsSensitive("ssid"))
        assertTrue(CrashReportScrubber.keyIsSensitive("password"))
        assertTrue(CrashReportScrubber.keyIsSensitive("pass"))
        assertTrue(CrashReportScrubber.keyIsSensitive("names"))
        assertTrue(CrashReportScrubber.keyIsSensitive("email"))
        assertTrue(CrashReportScrubber.keyIsSensitive("ip_address"))
        assertTrue(CrashReportScrubber.keyIsSensitive("message"))
        assertTrue(CrashReportScrubber.keyIsSensitive("extraInfo"))
        assertFalse(CrashReportScrubber.keyIsSensitive("action"))
        assertFalse(CrashReportScrubber.keyIsSensitive("network_type"))
        assertFalse(CrashReportScrubber.keyIsSensitive("level"))
    }

    @Test
    fun scrubMapRemovesSecretsAndRedactsNestedText() {
        val nested = mutableMapOf<String, Any>("ssid" to "Cafe", "state" to "up")
        val map = mutableMapOf<String, Any>(
            "password" to "hunter2",
            "note" to "mail ada@example.com",
            "extras" to nested,
        )
        CrashReportScrubber.scrubMap(map)
        assertFalse(map.containsKey("password"))
        assertEquals("mail [filtered]", map["note"])
        assertFalse(nested.containsKey("ssid"))
        assertEquals("up", nested["state"])
    }

    @Test
    fun dropsClickAndUserBreadcrumbs() {
        assertFalse(CrashReportScrubber.breadcrumbAllowed("ui.click", "user"))
        assertTrue(CrashReportScrubber.breadcrumbAllowed("device.event", "system"))
    }
}
