package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHub(
    settings: PebbleSettings,
    crashReports: Boolean,
    occasions: Set<String>,
    networkCount: Int,
    updateReady: Boolean,
    onOpen: (String) -> Unit,
    onSendTestError: () -> Boolean,
) {
    val brand = PebblesBrand.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = Type.display, color = brand.ink)
        if (updateReady) {
            Text("An update is ready.", style = Type.body, color = brand.accent)
        }
        SectionLabel("Pebble")
        SettingRow("Colour", settings.colourName) { onOpen(Routes.Colour) }
        SettingRow("Quiet hours", quietLine(settings.quietStart, settings.quietEnd)) { onOpen(Routes.Quiet) }
        SettingRow("Battery and pairing", "Charge on the dock. Pair from here.") { onOpen(Routes.Pebble) }
        SectionLabel("Group")
        val people = settings.names.count { it.isNotBlank() }
        SettingRow("People", "$people of 5") { onOpen(Routes.Group) }
        SettingRow("Occasion glows", if (occasions.isEmpty()) "Off" else "${occasions.size} on") { onOpen(Routes.Occasions) }
        SectionLabel("Connection")
        SettingRow("Wi-Fi", if (networkCount == 0) "None saved" else "$networkCount saved") { onOpen(Routes.Wifi) }
        SectionLabel("This phone")
        SettingRow("Crash reports", if (crashReports) "On" else "Off") { onOpen(Routes.Reports) }
        SettingRow("App updates", "Version ${BuildConfig.VERSION_NAME}") { onOpen(Routes.Updates) }
        if (BuildConfig.DEBUG) {
            SectionLabel("Debug")
            DebugTestError(crashReports, onSendTestError)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = Type.eyebrow, color = PebblesBrand.current.accent, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val brand = PebblesBrand.current
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = brand.elev, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = Type.body, color = brand.ink)
            Text(value, style = Type.body, color = brand.inkSoft)
        }
    }
}

@Composable
fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { content() },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ColourPage(
    settings: PebbleSettings,
    motion: Boolean,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    onChange: (PebbleSettings) -> Unit,
) {
    val brand = PebblesBrand.current
    PageColumn {
        Text("The glow they see.", style = Type.body, color = brand.inkSoft)
        BreathingPebble(
            colour = pebbleColor(settings.colour),
            scene = PebbleScene.Rest,
            motion = motion,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .then(pebbleShared(sharedScope, visibilityScope, motion)),
        )
        ColourChoices(settings.colourName) { name, value ->
            onChange(settings.copy(colourName = name, colour = value))
        }
    }
}

@Composable
fun QuietPage(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    PageColumn {
        Text(quietLine(settings.quietStart, settings.quietEnd), style = Type.body, color = brand.ink)
        Text(
            if (inQuietHours(settings.quietStart, settings.quietEnd)) {
                "Quiet hours would be on at this UTC hour."
            } else {
                "Quiet hours would be off at this UTC hour."
            },
            style = Type.body,
            color = brand.inkSoft,
        )
        HourFields(
            start = settings.quietStart,
            end = settings.quietEnd,
            onStart = { onChange(settings.copy(quietStart = it)) },
            onEnd = { onChange(settings.copy(quietEnd = it)) },
        )
    }
}

@Composable
fun GroupPage(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    PageColumn {
        Text("Add the people in this group. One is enough. Five is the most.", style = Type.body, color = brand.inkSoft)
        PeopleEditor(settings.names) { onChange(settings.copy(names = it)) }
    }
}

@Composable
fun WifiPage(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    PageColumn {
        Text(
            "Up to five. Pairing sends them to the pebble, which joins the first one.",
            style = Type.body,
            color = brand.inkSoft,
        )
        settings.networks.forEachIndexed { index, network ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(network.ssid, style = Type.body, color = brand.ink, modifier = Modifier.weight(1f))
                BrandQuiet("Remove", onClick = {
                    val networks = settings.networks.toMutableList()
                    networks.removeAt(index)
                    onChange(settings.copy(networks = networks))
                })
            }
        }
        if (settings.networks.size < 5) {
            BrandField(ssid, { ssid = it }, "Network name")
            BrandField(
                password,
                { password = it },
                "Password",
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            BrandButton(
                "Save network",
                enabled = ssid.isNotBlank(),
                onClick = {
                    onChange(settings.copy(networks = settings.networks + WifiNetwork(ssid.trim(), password)))
                    ssid = ""
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun OccasionPage(enabled: Set<String>, onToggle: (Occasion, Boolean) -> Unit) {
    val brand = PebblesBrand.current
    PageColumn {
        Text(
            "These glows play on the pebble in the app. Pairing does not send them yet.",
            style = Type.body,
            color = brand.inkSoft,
        )
        Occasion.entries.forEach { occasion ->
            val on = occasion.id in enabled
            Surface(shape = RoundedCornerShape(22.dp), color = brand.elev, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(occasion, it) })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(occasion.title, style = Type.body, color = brand.ink)
                        Text(occasion.line, style = Type.body, color = brand.inkSoft)
                    }
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
fun PebbleCarePage(status: String, onPair: () -> Unit) {
    val brand = PebblesBrand.current
    PageColumn {
        Text("Battery", style = Type.title, color = brand.ink)
        Text(
            "This app can't read the pebble's battery yet. It charges on the wireless dock, and the ring lights while it charges.",
            style = Type.body,
            color = brand.inkSoft,
        )
        Text("Pairing", style = Type.title, color = brand.ink)
        Text(status, style = Type.body, color = brand.ink)
        BrandButton("Pair", onClick = onPair, modifier = Modifier.fillMaxWidth())
        Text(
            "Pairing sends the colour, quiet hours, the group, and the Wi-Fi networks.",
            style = Type.body,
            color = brand.inkSoft,
        )
    }
}

@Composable
fun ReportsPage(
    enabled: Boolean,
    includeIp: Boolean,
    onEnabled: (Boolean) -> Unit,
    onIncludeIp: (Boolean) -> Unit,
) {
    val brand = PebblesBrand.current
    PageColumn {
        Surface(shape = RoundedCornerShape(22.dp), color = brand.elev, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabled),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Send crash reports",
                        style = Type.body,
                        color = brand.ink,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(checked = enabled, onCheckedChange = null)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Off unless you turn this on. A report has the error and the app version. It leaves out names, Wi-Fi, and touches.",
                    style = Type.body,
                    color = brand.inkSoft,
                )
                Spacer(Modifier.height(8.dp))
                IpOption("Anonymous", selected = !includeIp, enabled = enabled) { onIncludeIp(false) }
                IpOption("Include my IP address", selected = includeIp, enabled = enabled) { onIncludeIp(true) }
            }
        }
    }
}

@Composable
private fun IpOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val brand = PebblesBrand.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = selected, enabled = enabled, role = Role.RadioButton, onValueChange = { if (it) onSelect() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = brand.accent, unselectedColor = brand.inkSoft),
        )
        Text(label, style = Type.body, color = if (enabled) brand.ink else brand.inkSoft)
    }
}

@Composable
fun UpdatesPage(controller: UpdateController, onQuietChange: (Boolean) -> Unit) {
    val brand = PebblesBrand.current
    PageColumn {
        Text("Installed version ${BuildConfig.VERSION_NAME}.", style = Type.body, color = brand.ink)
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = controller.checkOnLaunch, role = Role.Switch, onValueChange = onQuietChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Check quietly on launch", style = Type.body, color = brand.ink, modifier = Modifier.weight(1f))
            Switch(checked = controller.checkOnLaunch, onCheckedChange = null)
        }
        BrandButton(
            "Check for updates",
            enabled = controller.phase !is UpdatePhase.Checking && controller.phase !is UpdatePhase.Downloading,
            onClick = { controller.check(manual = true) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (val phase = controller.phase) {
            UpdatePhase.Idle -> Unit
            UpdatePhase.Checking -> Text("Looking at GitHub…", style = Type.body, color = brand.inkSoft)
            UpdatePhase.Current -> Text("This is the newest version.", style = Type.body, color = brand.ink)
            is UpdatePhase.Failed -> Text(phase.message, style = Type.body, color = brand.ink)
            is UpdatePhase.MissingAsset -> Text(
                "Version ${phase.version} is published, without a ${phase.expectedName} file.",
                style = Type.body,
                color = brand.ink,
            )
            is UpdatePhase.Downloading -> Text("Downloading ${phase.version}…", style = Type.body, color = brand.inkSoft)
            is UpdatePhase.NeedsPermission -> {
                Text(
                    "Android needs permission to install updates from Pebbles.",
                    style = Type.body,
                    color = brand.ink,
                )
                BrandButton("Allow installs from this app", onClick = { controller.allowInstalls() }, modifier = Modifier.fillMaxWidth())
            }
            is UpdatePhase.Available -> {
                Text("Version ${phase.version}", style = Type.title, color = brand.ink)
                Text(phase.notes, style = Type.body, color = brand.inkSoft)
                BrandButton("Download and install", onClick = { controller.download(phase) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            "An update installs only when this app and the new APK are signed with the same release key. A debug build cannot update to a release build.",
            style = Type.body,
            color = brand.inkSoft,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun DebugTestError(crashReports: Boolean, onSendTestError: () -> Boolean) {
    val brand = PebblesBrand.current
    var testStatus by rememberSaveable { mutableStateOf("") }
    BrandButton(
        "Send test error",
        onClick = {
            testStatus = if (crashReports && onSendTestError()) {
                "Test error sent."
            } else {
                "Turn crash reports on first. Nothing is sent while this is off."
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (testStatus.isNotEmpty()) {
        Text(testStatus, style = Type.body, color = brand.inkSoft)
    }
}
