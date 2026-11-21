package eu.hellenicdev.pebbles

import io.sentry.JsonObjectWriter
import io.sentry.NoOpLogger
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class CrashReportIdentityTest {
    @Test
    fun anonymousSendsNoIpAndDisablesServerInference() {
        val event = reportWithSecrets()
        val prepared = CrashReports.prepareCrashReport(event, includeIp = false)
        val json = jsonOf(prepared!!)

        assertEquals("never", inferIp(json))
        assertFalse(json.contains("{{auto}}"))
        assertFalse(json.contains("ip_address"))
        assertNull(prepared.user?.ipAddress)
        assertNull(prepared.user?.id)
        assertNull(prepared.user?.email)
        assertNull(prepared.user?.username)
        assertNull(prepared.request)
        assertScrubbed(json)
    }

    @Test
    fun includeIpSendsTheAutoMarkerAndNoOtherPii() {
        val event = reportWithSecrets()
        val prepared = CrashReports.prepareCrashReport(event, includeIp = true)
        val json = jsonOf(prepared!!)

        assertEquals("auto", inferIp(json))
        assertTrue(json.contains("\"ip_address\":\"{{auto}}\""))
        assertEquals("{{auto}}", prepared.user?.ipAddress)
        assertNull(prepared.user?.id)
        assertNull(prepared.user?.email)
        assertNull(prepared.user?.username)
        assertNull(prepared.request)
        assertScrubbed(json)
    }

    private fun reportWithSecrets(): SentryEvent {
        val event = SentryEvent()
        event.user = User().apply {
            id = "install-id"
            email = "ada@example.com"
            username = "ada"
            ipAddress = "{{auto}}"
        }
        event.request = Request().apply { url = "https://example.test/ada@example.com" }
        event.serverName = "phone"
        event.setExtra("ssid", "Cafe")
        event.setExtra("password", "hunter2")
        event.setExtra("note", "mail ada@example.com")
        return event
    }

    private fun assertScrubbed(json: String) {
        assertFalse(json.contains("Cafe"))
        assertFalse(json.contains("hunter2"))
        assertFalse(json.contains("ada@example.com"))
        assertFalse(json.contains("install-id"))
    }

    private fun inferIp(json: String): String {
        val marker = "\"infer_ip\":\""
        val start = json.indexOf(marker)
        assertTrue(json, start >= 0)
        val from = start + marker.length
        return json.substring(from, json.indexOf('"', from))
    }

    private fun jsonOf(event: SentryEvent): String {
        val writer = StringWriter()
        event.serialize(JsonObjectWriter(writer, 100), NoOpLogger.getInstance())
        return writer.toString()
    }
}
