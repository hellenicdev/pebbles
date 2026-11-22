package eu.hellenicdev.pebbles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupController(
    private val store: SettingsStore,
    private val scope: CoroutineScope,
    private val onMembers: (List<String>) -> Unit,
) {
    var loading by mutableStateOf(false)
    var snapshot by mutableStateOf(GroupSnapshot(true, emptyList(), emptyList()))
    var message by mutableStateOf<String?>(null)
    var inviteCode by mutableStateOf(store.pendingInvite())

    fun refresh() {
        val settings = store.load()
        if (settings.yourName.isBlank()) {
            message = "Add your name before inviting someone."
            return
        }
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GroupHttp(store.deviceToken()).sync(settings.yourName, settings.colourName, settings.colour)
                }
            }
            loading = false
            result.fold(
                onSuccess = { apply(it) },
                onFailure = { message = it.message ?: "Couldn't reach the group just now." },
            )
        }
    }

    fun invite(onLink: (String) -> Unit) {
        val settings = store.load()
        if (settings.yourName.isBlank()) {
            message = "Add your name before inviting someone."
            return
        }
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val http = GroupHttp(store.deviceToken())
                    http.sync(settings.yourName, settings.colourName, settings.colour)
                    http.createInvite()
                }
            }
            loading = false
            result.fold(
                onSuccess = { url ->
                    message = null
                    refresh()
                    onLink(url)
                },
                onFailure = { message = it.message ?: "Couldn't make an invite." },
            )
        }
    }

    fun revoke(code: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GroupHttp(store.deviceToken()).revoke(code) }
            }
            result.fold(
                onSuccess = { refresh() },
                onFailure = { message = it.message ?: "Couldn't revoke that invite." },
            )
        }
    }

    fun accept() {
        val code = inviteCode ?: return
        val settings = store.load()
        if (settings.yourName.isBlank()) {
            message = "Add your name, then join."
            return
        }
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val http = GroupHttp(store.deviceToken())
                    http.sync(settings.yourName, settings.colourName, settings.colour)
                    http.accept(code)
                }
            }
            loading = false
            result.fold(
                onSuccess = {
                    apply(it)
                    inviteCode = null
                    store.setPendingInvite(null)
                },
                onFailure = { message = it.message ?: "Couldn't join that group." },
            )
        }
    }

    fun noteInvite(code: String?) {
        inviteCode = code?.takeIf { it.isNotBlank() }
        store.setPendingInvite(inviteCode)
    }

    fun dismissInvite() {
        inviteCode = null
        store.setPendingInvite(null)
        message = null
    }

    private fun apply(next: GroupSnapshot) {
        snapshot = next
        message = null
        onMembers(next.members.filter { !it.you }.map { it.name })
    }
}
