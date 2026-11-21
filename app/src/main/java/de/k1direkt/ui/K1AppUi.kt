package de.k1direkt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.LibraryFile
import de.k1direkt.ui.screens.BrowserScreen
import de.k1direkt.ui.screens.DiscoverScreen
import de.k1direkt.ui.screens.LibraryScreen
import de.k1direkt.ui.screens.PrinterScreen
import de.k1direkt.ui.screens.SettingsScreen
import de.k1direkt.ui.screens.SliceScreen
import de.k1direkt.ui.screens.ThreeMfScreen
import de.k1direkt.ui.screens.ViewerScreen

enum class Tab(val label: String, val icon: ImageVector, val iconSelected: ImageVector) {
    DISCOVER("Entdecken", Icons.Outlined.Explore, Icons.Filled.Explore),
    LIBRARY("Modelle", Icons.Outlined.ViewInAr, Icons.Filled.ViewInAr),
    PRINTER("Drucker", Icons.Outlined.Print, Icons.Filled.Print),
    SETTINGS("Einstellungen", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Vollbild-Seiten, die über den Tabs liegen. */
sealed interface Overlay {
    data class Browser(val url: String) : Overlay
    data class Viewer(val file: LibraryFile) : Overlay
    data class Slice(val file: LibraryFile) : Overlay
    data class ThreeMf(val file: LibraryFile) : Overlay
}

class Navigator {
    var tab by mutableStateOf(Tab.DISCOVER)
    val stack = mutableStateListOf<Overlay>()
    fun open(o: Overlay) { stack.add(o) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun replace(o: Overlay) { back(); open(o) }
    fun openTab(t: Tab) { stack.clear(); tab = t }
}

@Composable
fun K1AppUi(vm: AppViewModel) {
    val nav = remember { Navigator() }
    var tabName by rememberSaveable { mutableStateOf(Tab.DISCOVER.name) }
    LaunchedEffect(Unit) { nav.tab = Tab.valueOf(tabName) }
    LaunchedEffect(nav.tab) { tabName = nav.tab.name }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val transfer by vm.transfer.collectAsStateWithLifecycle()

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(transfer.active) { TransferBar(transfer) }
                if (nav.stack.isEmpty()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            val selected = nav.tab == t
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.openTab(t) },
                                icon = { Icon(if (selected) t.iconSelected else t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                } else {
                    // Vollbild-Seiten: Platz für die System-Navigationsleiste (Samsung-Tasten/Gestenleiste) freihalten
                    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            when (nav.tab) {
                Tab.DISCOVER -> DiscoverScreen(vm, nav)
                Tab.LIBRARY -> LibraryScreen(vm, nav)
                Tab.PRINTER -> PrinterScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
            // Overlays liegen über dem Tab-Inhalt; der Browser bleibt so beim Zurückkehren erhalten.
            nav.stack.forEachIndexed { index, overlay ->
                val visible = index == nav.stack.lastIndex
                key(index, overlay) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (visible || overlay is Overlay.Browser) {
                            when (overlay) {
                                is Overlay.Browser -> BrowserScreen(vm, nav, overlay.url)
                                is Overlay.Viewer -> ViewerScreen(vm, nav, overlay.file)
                                is Overlay.Slice -> SliceScreen(vm, nav, overlay.file)
                                is Overlay.ThreeMf -> ThreeMfScreen(vm, nav, overlay.file)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferBar(t: TransferState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (t.progress == null) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("${((t.progress ?: 0f) * 100).toInt()} %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (t.progress != null) {
                    LinearProgressIndicator(progress = { t.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }
}
