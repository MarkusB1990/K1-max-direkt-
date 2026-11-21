package de.k1direkt.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.k1direkt.data.FileType
import de.k1direkt.data.LibraryFile
import de.k1direkt.viewer.Mesh
import de.k1direkt.viewer.MeshLoader
import de.k1direkt.viewer.ModelSurfaceView
import de.k1direkt.viewer.PreviewConfig
import de.k1direkt.viewer.SoftwareMeshPreview
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed interface MeshState {
    data object Loading : MeshState
    data class Ready(val mesh: Mesh) : MeshState
    data class Failed(val message: String) : MeshState
}

@Composable
fun rememberMesh(file: File): MeshState {
    var state by remember(file) { mutableStateOf<MeshState>(MeshState.Loading) }
    LaunchedEffect(file) {
        state = withContext(Dispatchers.Default) {
            try {
                MeshState.Ready(MeshLoader.load(file))
            } catch (e: OutOfMemoryError) {
                MeshState.Failed("Modell zu groß für die Vorschau")
            } catch (e: Exception) {
                MeshState.Failed(e.message ?: "Vorschau nicht möglich")
            }
        }
    }
    return state
}

fun Mesh.dimensionText(): String =
    String.format(Locale.GERMANY, "%.1f × %.1f × %.1f mm", sizeX, sizeY, sizeZ)

/** Passt das Modell auf die Druckplatte des K1 Max (300 × 300 × 300 mm)? */
fun Mesh.fitsK1Max(): Boolean = sizeX <= 300f && sizeY <= 300f && sizeZ <= 300f

@Composable
fun ModelPreview(state: MeshState, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state) {
            MeshState.Loading -> CircularProgressIndicator()
            is MeshState.Failed -> Text(
                state.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            is MeshState.Ready ->
                if (PreviewConfig.useOpenGl) GlView(state.mesh, Modifier.fillMaxSize())
                else SoftwareMeshPreview(state.mesh, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GlView(mesh: Mesh, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { ModelSurfaceView(context) }
    LaunchedEffect(mesh) { view.setMesh(mesh) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
fun ViewerScreen(vm: AppViewModel, nav: Navigator, item: LibraryFile) {
    val state = rememberMesh(item.file)
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(
            title = item.name,
            subtitle = (state as? MeshState.Ready)?.mesh?.let { "${it.dimensionText()} · ${"%,d".format(it.triangleCount)} Dreiecke" } ?: item.type.label,
            onBack = { nav.back() },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ModelPreview(state, Modifier.fillMaxSize())
            Text(
                "Ein Finger drehen · zwei Finger zoomen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
        if (state is MeshState.Ready && !state.mesh.fitsK1Max()) {
            InfoCard(
                "Größer als der Bauraum",
                "Das Modell passt nicht in 300 × 300 × 300 mm. Beim Slicen kleiner skalieren.",
                icon = Icons.Filled.Warning,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
        }
        Button(
            onClick = {
                nav.back()
                nav.open(if (item.type == FileType.THREE_MF) Overlay.ThreeMf(item) else Overlay.Slice(item))
            },
            modifier = Modifier.fillMaxWidth().padding(ScreenPadding).height(56.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Filled.Layers, null)
            Spacer(Modifier.width(8.dp))
            Text("Slicen", style = MaterialTheme.typography.titleMedium)
        }
    }
}
