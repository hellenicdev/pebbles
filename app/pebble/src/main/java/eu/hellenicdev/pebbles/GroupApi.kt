package eu.hellenicdev.pebbles

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val GROUPS_API = "https://api.hellenicdev.eu"

data class GroupSnapshot(
    val youAreOwner: Boolean,
    val members: List<GroupMember>,
    val invites: List<GroupInviteLink>,
)

internal fun parseGroup(json: String): GroupSnapshot {
    val root = JSONObject(json)
    val group = root.optJSONObject("group") ?: return GroupSnapshot(true, emptyList(), emptyList())
    val members = mutableListOf<GroupMember>()
    val memberArray = group.optJSONArray("members")
    if (memberArray != null) {
        for (index in 0 until memberArray.length()) {
            val item = memberArray.optJSONObject(index) ?: continue
            members.add(
                GroupMember(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    colourName = item.optString("colourName"),
                    colour = item.optLong("colour"),
                    you = item.optBoolean("you"),
                ),
            )
        }
    }
    val invites = mutableListOf<GroupInviteLink>()
    val inviteArray = group.optJSONArray("invites")
    if (inviteArray != null) {
        for (index in 0 until inviteArray.length()) {
            val item = inviteArray.optJSONObject(index) ?: continue
            invites.add(
                GroupInviteLink(
                    code = item.optString("code"),
                    url = item.optString("url"),
                    expiresAt = item.optLong("expiresAt"),
                ),
            )
        }
    }
    return GroupSnapshot(
        youAreOwner = group.optBoolean("youAreOwner"),
        members = members,
        invites = invites,
    )
}

internal fun apiError(json: String, fallback: String): String {
    return runCatching { JSONObject(json).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}

internal class GroupHttp(private val token: String) {
    fun sync(name: String, colourName: String, colour: Long): GroupSnapshot {
        val body = JSONObject()
            .put("name", name)
            .put("colourName", colourName)
            .put("colour", colour)
            .toString()
        val (code, text) = call("POST", "/v1/me", body)
        if (code !in 200..299) error(apiError(text, "Couldn't reach the group just now."))
        return parseGroup(text)
    }

    fun createInvite(): String {
        val (code, text) = call("POST", "/v1/invites", "{}")
        if (code !in 200..299) error(apiError(text, "Couldn't make an invite."))
        return JSONObject(text).optString("url")
    }

    fun revoke(code: String) {
        val (status, text) = call("DELETE", "/v1/invites/$code", null)
        if (status !in 200..299) error(apiError(text, "Couldn't revoke that invite."))
    }

    fun accept(code: String): GroupSnapshot {
        val (status, text) = call("POST", "/v1/invites/$code/accept", "{}")
        if (status !in 200..299) error(apiError(text, "Couldn't join that group."))
        return parseGroup(text)
    }

    private fun call(method: String, path: String, body: String?): Pair<Int, String> {
        val connection = (URL(GROUPS_API + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "pebbles-android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.responseCode to text
        } finally {
            connection.disconnect()
        }
    }
}
