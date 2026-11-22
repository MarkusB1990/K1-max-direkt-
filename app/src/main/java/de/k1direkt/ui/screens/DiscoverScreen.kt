package de.k1direkt.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import de.k1direkt.sources.ModelSite
import de.k1direkt.sources.ProviderOutcome
import de.k1direkt.sources.SearchResult
import de.k1direkt.sources.UnifiedSearch
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay
import java.util.Locale

@Composable
fun DiscoverScreen(vm: AppViewModel, nav: Navigator) {
    val state by vm.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.query) { if (query.isBlank()) query = state.query }
    val keyboard = LocalSoftwareKeyboardController.current
    val apiSites = UnifiedSearch.providers.map { it.site }.toSet()

    val allResults = interleave(state.outcomes.map { it.results })
    val shown = state.filter?.let { f -> allResults.filter { it.site == f } } ?: allResults
    val hasSearch = state.outcomes.isNotEmpty() || state.loading

    val gridState = rememberLazyGridState()
    // Endlos-Scrollen: kurz vor dem Ende die nächsten Treffer nachladen
    LaunchedEffect(gridState, state.canLoadMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.collect { nearEnd -> if (nearEnd) vm.loadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PageHeader("Entdecken", "Modelle von allen großen Plattformen", horizontalPadding = 4.dp)
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("Wonach suchst du?") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Leeren") }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        vm.search(query)
                    }),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (query.isNotBlank()) "Direkt auf der Seite suchen" else "Seiten",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ModelSite.entries) { site ->
                        SiteTile(site) {
                            val url = if (query.isNotBlank()) site.searchUrl(query) else site.homeUrl
                            nav.open(Overlay.Browser(url))
                        }
                    }
                }

                if (hasSearch) {
                    Spacer(Modifier.height(20.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.filter == null,
                                onClick = { vm.setSearchFilter(null) },
                                label = { Text("Alle · ${countLabel(state.outcomes.filter { it.results.isNotEmpty() })}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            )
                        }
                        items(state.outcomes.filter { it.results.isNotEmpty() }) { o ->
                            FilterChip(
                                selected = state.filter == o.site,
                                onClick = { vm.setSearchFilter(o.site) },
                                label = { Text("${o.site.displayName} · ${countLabel(listOf(o))}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            )
                        }
                    }
                    val missing = state.outcomes.filter { it.skipped != null }.map { it.site.displayName }
                    val failed = state.outcomes.filter { it.error != null }.map { it.site.displayName }
                    val webOnly = ModelSite.entries.filter { it !in apiSites }
                    val note = buildList {
                        if (failed.isNotEmpty()) add("${failed.joinToString()} gerade nicht erreichbar")
                        if (missing.isNotEmpty()) add("${missing.joinToString()} ohne API-Key")
                    }.joinToString(" · ")
                    Text(
                        (if (note.isNotBlank()) "$note. " else "") + "Auch suchen auf: " + webOnly.joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(webOnly + state.outcomes.filter { it.results.isEmpty() }.map { it.site }) { site ->
                            Surface(
                                onClick = { nav.open(Overlay.Browser(site.searchUrl(state.query))) },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(site.displayName, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        when {
            state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            !hasSearch -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Filled.TravelExplore,
                    "Finde dein nächstes Modell",
                    "Suche oben über alle Plattformen gleichzeitig – oder öffne eine Seite. " +
                        "Was du dort herunterlädst, landet automatisch unter „Modelle“.",
                )
            }
            else -> {
                items(shown, key = { "${it.site.name}-${it.id}-${it.pageUrl}" }) { r ->
                    ResultCard(r) { nav.open(Overlay.Browser(r.pageUrl)) }
                }
                if (allResults.isNotEmpty() && (state.loadingMore || state.canLoadMore)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                            else OutlinedButton(onClick = { vm.loadMore() }) { Text("Mehr laden") }
                        }
                    }
                }
            }
        }
    }
}

/** Trefferzahl für die Filter: Gesamtzahl der Plattform, sonst geladene Anzahl (mit „+“, wenn es mehr gibt). */
private fun countLabel(outcomes: List<ProviderOutcome>): String {
    var sum = 0L
    var open = false
    for (o in outcomes) {
        val t = o.total
        if (t != null) sum += maxOf(t, o.results.size) else {
            sum += o.results.size
            if (!o.exhausted && o.results.isNotEmpty()) open = true
        }
    }
    return "%,d".format(Locale.GERMANY, sum) + if (open) "+" else ""
}

/** Mischt die Ergebnislisten abwechselnd, damit keine Seite die Liste dominiert. */
private fun interleave(lists: List<List<SearchResult>>): List<SearchResult> {
    val out = ArrayList<SearchResult>()
    val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) for (l in lists) if (i < l.size) out += l[i]
    return out.distinctBy { "${it.site}-${it.id}-${it.pageUrl}" }
}

@Composable
private fun SiteTile(site: ModelSite, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            SiteLogo(site, Modifier.size(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(site.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Logo der Seite; solange es lädt oder falls es fehlt: farbiger Anfangsbuchstabe. */
@Composable
fun SiteLogo(site: ModelSite, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // feste Größe → das Laden startet sofort, unabhängig vom Zeichnen
    val request = remember(site) { ImageRequest.Builder(context).data(site.logoUrl).size(128).crossfade(true).build() }
    val painter = rememberAsyncImagePainter(request)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (painter.state is AsyncImagePainter.State.Success) {
            Image(painter, site.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(site.displayName.take(1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(site.color))
        }
    }
}

@Composable
private fun ResultCard(r: SearchResult, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Box {
                if (r.imageUrl != null) {
                    AsyncImage(
                        model = r.imageUrl,
                        contentDescription = r.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).background(
                            Brush.linearGradient(listOf(Color(r.site.color).copy(alpha = 0.45f), MaterialTheme.colorScheme.surfaceContainerHigh))
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ViewInAr, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.8f))
                    }
                }
                Row(
                    Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                        SiteLogo(r.site, Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(r.site.displayName, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.author.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    r.likes?.let {
                        Icon(Icons.Filled.Favorite, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" ${compact(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    r.downloads?.let {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Download, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" ${compact(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
