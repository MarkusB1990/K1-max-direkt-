package de.k1direkt.ui.screens

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.k1direkt.sources.ModelSite
import de.k1direkt.sources.SearchResult
import de.k1direkt.sources.UnifiedSearch
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay

@Composable
fun DiscoverScreen(vm: AppViewModel, nav: Navigator) {
    val state by vm.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val apiSites = UnifiedSearch.providers.map { it.site }.toSet()

    val allResults = interleave(state.outcomes.map { it.results })
    val shown = state.filter?.let { f -> allResults.filter { it.site == f } } ?: allResults

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Modelle finden", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Durchsucht mehrere Plattformen gleichzeitig",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("z. B. Kabelhalter, Vase, Benchy …") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Leeren") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        vm.search(query)
                    }),
                )
                SectionTitle("Seiten direkt öffnen")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ModelSite.entries) { site ->
                        SiteChip(site) {
                            val url = if (query.isNotBlank()) site.searchUrl(query) else site.homeUrl
                            nav.open(Overlay.Browser(url))
                        }
                    }
                }
                Text(
                    if (query.isNotBlank()) "Tippe auf eine Seite, um dort nach „$query“ zu suchen. Downloads landen automatisch in der Bibliothek."
                    else "Downloads aus dem eingebauten Browser landen automatisch in deiner Bibliothek.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (state.outcomes.isNotEmpty() || state.loading) {
                    SectionTitle("Ergebnisse für „${state.query}“")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.filter == null,
                                onClick = { vm.setSearchFilter(null) },
                                label = { Text("Alle (${allResults.size})") },
                            )
                        }
                        items(state.outcomes.filter { it.results.isNotEmpty() }) { o ->
                            FilterChip(
                                selected = state.filter == o.site,
                                onClick = { vm.setSearchFilter(o.site) },
                                label = { Text("${o.site.displayName} (${o.results.size})") },
                            )
                        }
                    }
                    state.outcomes.filter { it.error != null || it.skipped != null }.forEach { o ->
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning, null,
                                Modifier.size(14.dp),
                                tint = if (o.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${o.site.displayName}: ${o.error ?: o.skipped}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable {
                                    nav.open(Overlay.Browser(o.site.searchUrl(state.query)))
                                },
                            )
                        }
                    }
                    val webOnly = ModelSite.entries.filter { it !in apiSites }
                    Text(
                        "Ohne API, im Browser suchen:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(webOnly) { site ->
                            AssistChip(
                                onClick = { nav.open(Overlay.Browser(site.searchUrl(state.query))) },
                                label = { Text(site.displayName) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
        }

        if (state.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(shown, key = { "${it.site.name}-${it.id}-${it.pageUrl}" }) { r ->
                ResultCard(r) { nav.open(Overlay.Browser(r.pageUrl)) }
            }
            if (allResults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (state.page > 0) {
                            OutlinedButton(onClick = { vm.search(state.query, state.page - 1) }, Modifier.weight(1f)) {
                                Text("← Zurück")
                            }
                        }
                        OutlinedButton(onClick = { vm.search(state.query, state.page + 1) }, Modifier.weight(1f)) {
                            Text("Weitere Ergebnisse →")
                        }
                    }
                }
            }
        }
    }
}

/** Mischt die Ergebnislisten abwechselnd, damit keine Seite die Liste dominiert. */
private fun interleave(lists: List<List<SearchResult>>): List<SearchResult> {
    val out = ArrayList<SearchResult>()
    val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) for (l in lists) if (i < l.size) out += l[i]
    return out.distinctBy { "${it.site}-${it.id}-${it.pageUrl}" }
}

@Composable
private fun SiteChip(site: ModelSite, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(site.color)))
            Spacer(Modifier.width(8.dp))
            Text(site.displayName, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultCard(r: SearchResult, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Box {
            AsyncImage(
                model = r.imageUrl,
                contentDescription = r.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(
                r.site.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(r.site.color).copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (r.likes != null || r.downloads != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    r.likes?.let {
                        Icon(Icons.Filled.Favorite, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" $it   ", style = MaterialTheme.typography.labelSmall)
                    }
                    r.downloads?.let {
                        Icon(Icons.Filled.Download, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" $it", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
