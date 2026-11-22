package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val Colour = "settings/colour"
    const val Quiet = "settings/quiet"
    const val Group = "settings/group"
    const val Wifi = "settings/wifi"
    const val Occasions = "settings/occasions"
    const val Pebble = "settings/pebble"
    const val Reports = "settings/reports"
    const val Updates = "settings/updates"
    const val Support = "settings/support"
    const val Join = "join"
}

private val titles = mapOf(
    Routes.Colour to "Colour",
    Routes.Quiet to "Quiet hours",
    Routes.Group to "People",
    Routes.Wifi to "Wi-Fi",
    Routes.Occasions to "Occasion glows",
    Routes.Pebble to "Battery and pairing",
    Routes.Reports to "Crash reports",
    Routes.Updates to "App updates",
    Routes.Join to "Join",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    crashReports: Boolean,
    includeIp: Boolean,
    occasions: Set<String>,
    updates: UpdateController,
    group: GroupController,
    onShare: (String) -> Unit,
    onSupport: () -> Unit,
    onChange: (PebbleSettings) -> Unit,
    onPair: () -> Unit,
    onCrashReports: (Boolean) -> Unit,
    onIncludeIp: (Boolean) -> Unit,
    onOccasion: (Occasion, Boolean) -> Unit,
    onQuietUpdates: (Boolean) -> Unit,
    onSendTestError: () -> Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.Home
    val topLevel = route == Routes.Home || route == Routes.Settings
    LaunchedEffect(group.inviteCode) {
        if (!group.inviteCode.isNullOrBlank() && route != Routes.Join) {
            navController.navigate(Routes.Join) { launchSingleTop = true }
        }
    }
    SharedTransitionLayout {
        val sharedScope = this
        Scaffold(
            containerColor = PebblesBrand.current.bg,
            topBar = {
                if (!topLevel) {
                    TopAppBar(
                        title = { Text(titles[route] ?: "Settings", style = Type.title) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (topLevel) {
                    NavigationBar(containerColor = PebblesBrand.current.elev) {
                        NavigationBarItem(
                            selected = route == Routes.Home,
                            onClick = { navController.navigateTop(Routes.Home) },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = route == Routes.Settings,
                            onClick = { navController.navigateTop(Routes.Settings) },
                            icon = {
                                if (updates.updateReady) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                    }
                                } else {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            },
                            label = { Text("Settings") },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier = Modifier.padding(padding).padding(bottom = 4.dp),
            ) {
                fun NavGraphBuilder.page(
                    route: String,
                    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
                ) {
                    composable(
                        route = route,
                        enterTransition = { enter(motion) },
                        exitTransition = { exit(motion) },
                        popEnterTransition = { popEnter(motion) },
                        popExitTransition = { popExit(motion) },
                        content = { content() },
                    )
                }
                page(Routes.Home) {
                    HomeScreen(
                        settings = settings,
                        status = status,
                        motion = motion,
                        occasions = occasions,
                        members = group.snapshot.members,
                        loading = group.loading,
                        canInvite = group.snapshot.youAreOwner || group.snapshot.members.isEmpty(),
                        onRefresh = { group.refresh() },
                        onInvite = { navController.navigate(Routes.Group) },
                        sharedScope = sharedScope,
                        visibilityScope = this,
                    )
                }
                page(Routes.Settings) {
                    SettingsHub(
                        settings = settings,
                        crashReports = crashReports,
                        occasions = occasions,
                        networkCount = settings.networks.size,
                        updateReady = updates.updateReady,
                        onOpen = { route ->
                            if (route == Routes.Support) onSupport() else navController.navigate(route)
                        },
                        onSendTestError = onSendTestError,
                    )
                }
                page(Routes.Colour) {
                    ColourPage(settings, motion, sharedScope, this, onChange)
                }
                page(Routes.Quiet) { QuietPage(settings, onChange) }
                page(Routes.Group) {
                    GroupPage(settings, group, onName = { onChange(settings.copy(yourName = it)) }, onShare)
                }
                page(Routes.Join) {
                    JoinPage(settings.yourName, group.message, group.loading, { group.accept() }, { group.dismissInvite(); navController.popBackStack() })
                }
                page(Routes.Wifi) { WifiPage(settings, onChange) }
                page(Routes.Occasions) { OccasionPage(occasions, onOccasion) }
                page(Routes.Pebble) { PebbleCarePage(status, onPair) }
                page(Routes.Reports) { ReportsPage(crashReports, includeIp, onCrashReports, onIncludeIp) }
                page(Routes.Updates) { UpdatesPage(updates, onQuietUpdates) }
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(Routes.Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<*>.enter(motion: Boolean): EnterTransition {
    if (!motion) return EnterTransition.None
    return fadeIn(tween(240, easing = SmoothOut)) +
        slideInHorizontally(tween(320, easing = SmoothOut)) { it / 6 } +
        scaleIn(tween(320, easing = SmoothOut), initialScale = 0.96f)
}

private fun AnimatedContentTransitionScope<*>.exit(motion: Boolean): ExitTransition {
    if (!motion) return ExitTransition.None
    return fadeOut(tween(160)) + slideOutHorizontally(tween(220, easing = SmoothOut)) { -it / 10 }
}

private fun AnimatedContentTransitionScope<*>.popEnter(motion: Boolean): EnterTransition {
    if (!motion) return EnterTransition.None
    return fadeIn(tween(220, easing = SmoothOut)) + slideInHorizontally(tween(280, easing = SmoothOut)) { -it / 8 }
}

private fun AnimatedContentTransitionScope<*>.popExit(motion: Boolean): ExitTransition {
    if (!motion) return ExitTransition.None
    return fadeOut(tween(160)) + slideOutHorizontally(tween(240, easing = SmoothOut)) { it / 6 }
}
