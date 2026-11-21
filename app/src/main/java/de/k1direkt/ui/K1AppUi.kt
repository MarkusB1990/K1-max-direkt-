package de.k1direkt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import de.k1direkt.ui.screens.ViewerScreen

enum class Tab(val label: String, val icon: ImageVector) {
    DISCOVER("Entdecken", Icons.Filled.Explore),
    LIBRARY("Bibliothek", Icons.Filled.Folder),
    PRINTER("Drucker", Icons.Filled.Print),
    SETTINGS("Einstellungen", Icons.Filled.Settings),
}

/** Vollbild-Seiten, die über den Tabs liegen. */
sealed interface Overlay {
    data class Browser(val url: String) : Overlay
    data class Viewer(val file: LibraryFile) : Overlay
    data class Slice(val file: LibraryFile) : Overlay
}

class Navigator {
    var tab by mutableStateOf(Tab.DISCOVER)
    val stack = mutableStateListOf<Overlay>()
    fun open(o: Overlay) { stack.add(o) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun openTab(t: Tab) { stack.clear(); tab = t }
}

@Composable
fun K1AppUi(vm: AppViewModel) {
    val nav = remember { Navigator() }
    var tabName by rememberSaveable { mutableStateOf(Tab.DISCOVER.name) }
    LaunchedEffect(Unit) { nav.tab = Tab.valueOf(tabName) }
    LaunchedEffect(nav.tab) { tabName = nav.tab.name }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val transfer by vm.transfer.collectAsStateWithLifecycle()

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (transfer.active) TransferBar(transfer)
                if (nav.stack.isEmpty()) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = nav.tab == t,
                                onClick = { nav.openTab(t) },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                t.title + (t.progress?.let { " – ${(it * 100).toInt()} %" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (t.progress != null) {
                LinearProgressIndicator(progress = { t.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}
