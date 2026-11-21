package eu.hellenicdev.pebbles

/**
 * Strips values that must never leave the phone: Wi-Fi names and passwords,
 * emails, user ids, and touch or message content.
 */
internal object CrashReportScrubber {
    private val sensitiveKey = Regex(
        "(?i)(password|passwd|pwd|passphrase|ssid|bssid|wifi|e-?mail|user_?id|userid|username|ip_?address|token|secret|authorization|cookie|credential|extra_?info)",
    )
    private val jsonNames = Regex("""(?i)"names"\s*:\s*\[[^\[\]]*\]""")
    private val jsonSecret = Regex(
        """(?i)"(ssid|bssid|pass|password|passwd|pwd|passphrase|email|e-mail|username|user_id|userid|token|secret)"\s*:\s*"(?:\\.|[^"\\])*"""",
    )
    private val assignment = Regex(
        """(?i)\b(ssid|bssid|password|passwd|pwd|passphrase)\b\s*[:=]\s*("[^"]*"|'[^']*'|\S+)""",
    )
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    fun keyIsSensitive(key: String): Boolean {
        val normalized = key.trim()
        if (normalized.equals("pass", ignoreCase = true)) return true
        if (normalized.equals("name", ignoreCase = true)) return true
        if (normalized.equals("names", ignoreCase = true)) return true
        if (normalized.equals("ip", ignoreCase = true)) return true
        if (normalized.equals("touch", ignoreCase = true)) return true
        if (normalized.equals("hug", ignoreCase = true)) return true
        if (normalized.equals("message", ignoreCase = true)) return true
        return sensitiveKey.containsMatchIn(normalized)
    }

    fun redact(value: String): String {
        val withoutNames = jsonNames.replace(value, """"names":["[filtered]"]""")
        val withoutJson = jsonSecret.replace(withoutNames) { match ->
            """"${match.groupValues[1]}":"[filtered]""""
        }
        val withoutAssignments = assignment.replace(withoutJson) { match ->
            "${match.groupValues[1]}=[filtered]"
        }
        return email.replace(withoutAssignments, "[filtered]")
    }

    fun breadcrumbAllowed(category: String?, type: String?): Boolean {
        if (category == "ui.click") return false
        if (type == "user") return false
        return true
    }

    fun scrubMap(map: MutableMap<String, Any>?) {
        if (map == null) return
        for (key in map.keys.toList()) {
            if (keyIsSensitive(key)) {
                map.remove(key)
                continue
            }
            when (val value = map[key]) {
                is String -> map[key] = redact(value)
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    scrubMap(value as MutableMap<String, Any>)
                }
            }
        }
    }
}
