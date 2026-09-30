package com.shlok.sam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shlok.sam.ui.orb.SamOrb
import com.shlok.sam.ui.screens.AboutScreen
import com.shlok.sam.ui.screens.AccessibilityScreen
import com.shlok.sam.ui.screens.AuraScreen
import com.shlok.sam.ui.screens.ChatScreen
import com.shlok.sam.ui.screens.ConnectorsScreen
import com.shlok.sam.ui.screens.DeveloperScreen
import com.shlok.sam.ui.screens.DiscoverScreen
import com.shlok.sam.ui.screens.HomeScreen
import com.shlok.sam.ui.screens.KnowledgeScreen
import com.shlok.sam.ui.screens.MemoryScreen
import com.shlok.sam.ui.screens.OrbCustomizeScreen
import com.shlok.sam.ui.screens.PermissionsScreen
import com.shlok.sam.ui.screens.PrivacyScreen
import com.shlok.sam.ui.screens.ProfileScreen
import com.shlok.sam.ui.screens.ResearchScreen
import com.shlok.sam.ui.screens.SettingsHub
import com.shlok.sam.ui.screens.TaskCenterScreen
import com.shlok.sam.ui.screens.VisualLensScreen
import com.shlok.sam.ui.theme.LocalSamPalette
import com.shlok.sam.ui.theme.SamTheme
import com.shlok.sam.ui.theme.paletteFor
import kotlinx.coroutines.delay

@Composable
fun SamRoot(startRoute: String?, vm: SamViewModel = hiltViewModel()) {
    val orb by vm.orb.collectAsState()
    val palette = paletteFor(orb.aura, orb.customAuraArgb)
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1400)
        splash = false
    }
    SamTheme(palette) {
        Box(Modifier.fillMaxSize().background(palette.bg)) {
            if (!splash) SamNav(vm, startRoute)
            AnimatedVisibility(splash, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF05070D), Color(0xFF0B1530)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    val ui by vm.runtime.ui.collectAsState()
                    SamOrb(ui.state, ui.amplitude, orb, palette.aura, Modifier.size(180.dp))
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun SamNav(vm: SamViewModel, startRoute: String?) {
    val nav = rememberNavController()
    val palette = LocalSamPalette.current
    val tabs = listOf(
        Tab("home", "Home", Icons.Outlined.Home),
        Tab("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
        Tab("core", "SAM Core", Icons.Outlined.Tonality),
        Tab("discover", "Discover", Icons.Outlined.Explore),
        Tab("settings", "Settings", Icons.Outlined.Settings)
    )
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    LaunchedEffect(startRoute) {
        if (startRoute == "chat") nav.navigate("chat")
    }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar(containerColor = Color(0xEE0A101C)) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.aura,
                                selectedTextColor = palette.aura,
                                indicatorColor = Color(0x332EE6FF),
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("chat") { ChatScreen(vm) }
            composable("core") { OrbCustomizeScreen(vm) }
            composable("discover") { DiscoverScreen(nav) }
            composable("settings") { SettingsHub(nav) }
            composable("profile") { ProfileScreen(vm, nav) }
            composable("permissions") { PermissionsScreen(nav) }
            composable("accessibility") { AccessibilityScreen(nav) }
            composable("connectors") { ConnectorsScreen(vm, nav) }
            composable("memory") { MemoryScreen(vm, nav) }
            composable("knowledge") { KnowledgeScreen(vm, nav) }
            composable("tasks") { TaskCenterScreen(vm, nav) }
            composable("lens") { VisualLensScreen(vm, nav) }
            composable("research") { ResearchScreen(vm, nav) }
            composable("aura") { AuraScreen(vm, nav) }
            composable("about") { AboutScreen(nav) }
            composable("privacy") { PrivacyScreen(nav) }
            composable("developer") { DeveloperScreen(vm, nav) }
        }
    }
}
