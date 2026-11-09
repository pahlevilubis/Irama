@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.irama.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irama.player.data.AlbumGroup
import com.irama.player.data.NamedGroup
import com.irama.player.data.Song
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Daftar lagu yang dibuka dari Album / Artis / Folder. */
data class Detail(val title: String, val subtitle: String, val songs: List<Song>)

typealias PlayAction = (songs: List<Song>, index: Int, shuffle: Boolean) -> Unit

@Composable
fun HomeScreen(
    songs: List<Song>,
    albums: List<AlbumGroup>,
    artists: List<NamedGroup>,
    folders: List<NamedGroup>,
    loading: Boolean,
    state: PlaybackState,
    overlayOpen: Boolean,
    onPlay: PlayAction,
    onOpenDetail: (Detail) -> Unit,
    onOpenEq: () -> Unit,
    onRefresh: () -> Unit,
    miniPlayer: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    BackHandler(enabled = searching && !overlayOpen) {
        searching = false
        query = ""
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Cari lagu, artis, album…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAbout = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Irama", fontWeight = FontWeight.Bold, color = cs.primary)
                            Text(
                                "by $APP_CREATOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tutup pencarian")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Cari")
                        }
                        IconButton(onClick = onOpenEq) {
                            Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Pindai ulang lagu")
                        }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Rounded.Info, contentDescription = "Tentang aplikasi")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                loading && songs.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                songs.isEmpty() -> EmptyLibrary(onRefresh)
                searching -> SearchResults(songs, query, state.mediaId, onPlay)
                else -> LibraryPager(songs, albums, artists, folders, state.mediaId, onPlay, onOpenDetail)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Belum ada lagu", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Simpan file musik (MP3, FLAC, M4A, dll.) di HP kamu, misalnya di folder Music atau Download, lalu ketuk Pindai Ulang.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pindai Ulang")
        }
    }
}

@Composable
private fun LibraryPager(
    songs: List<Song>,
    albums: List<AlbumGroup>,
    artists: List<NamedGroup>,
    folders: List<NamedGroup>,
    currentId: String?,
    onPlay: PlayAction,
    onOpenDetail: (Detail) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tabs = listOf("Lagu", "Album", "Artis", "Folder")
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pager.currentPage,
            containerColor = cs.background,
            contentColor = cs.primary,
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(title) },
                    selectedContentColor = cs.primary,
                    unselectedContentColor = cs.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SongsTab(songs, currentId, onPlay)
                1 -> AlbumsTab(albums) { a -> onOpenDetail(Detail(a.title, a.artist, a.songs)) }
                2 -> GroupsTab(artists, icon = null) { g -> onOpenDetail(Detail(g.name, "Artis", g.songs)) }
                else -> GroupsTab(folders, icon = Icons.Rounded.Folder) { g ->
                    onOpenDetail(Detail(g.name, g.key.ifBlank { "Folder" }, g.songs))
                }
            }
        }
    }
}

@Composable
private fun SongsTab(songs: List<Song>, currentId: String?, onPlay: PlayAction) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onPlay(songs, Random.nextInt(songs.size), true) }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acak Semua")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${songs.size} lagu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, s.mediaId == currentId) { onPlay(songs, i, false) }
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<AlbumGroup>, onOpen: (AlbumGroup) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            Column(
                Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(a) }
                    .padding(6.dp)
            ) {
                AlbumArt(
                    a.songs.firstOrNull()?.uri,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    sizePx = 320,
                    corner = 14.dp,
                    iconSize = 40.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    a.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    a.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GroupsTab(groups: List<NamedGroup>, icon: ImageVector?, onOpen: (NamedGroup) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(groups, key = { it.key }) { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(g) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon == null) {
                    AlbumArt(g.songs.firstOrNull()?.uri, Modifier.size(52.dp), corner = 26.dp)
                } else {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = cs.primary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        g.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        g.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(songs: List<Song>, query: String, currentId: String?, onPlay: PlayAction) {
    val q = query.trim().lowercase()
    val results = remember(songs, q) {
        if (q.isEmpty()) emptyList()
        else songs.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }
    if (q.isNotEmpty() && results.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("Tidak ada lagu yang cocok dengan \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
            SongRow(s, s.mediaId == currentId) { onPlay(results, i, false) }
        }
    }
}

// ---------- Layar daftar lagu album / artis / folder ----------

@Composable
fun DetailScreen(
    detail: Detail,
    state: PlaybackState,
    onBack: () -> Unit,
    onPlay: PlayAction,
    miniPlayer: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val totalMin = remember(detail) { detail.songs.sumOf { it.durationMs } / 60_000 }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(detail.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(
                        detail.songs.firstOrNull()?.uri,
                        Modifier.size(120.dp),
                        sizePx = 320,
                        corner = 18.dp,
                        iconSize = 44.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            detail.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${detail.songs.size} lagu  •  $totalMin menit",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "actions") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Button(
                        onClick = { onPlay(detail.songs, 0, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Putar")
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { onPlay(detail.songs, Random.nextInt(detail.songs.size), true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Acak")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(detail.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, s.mediaId == state.mediaId) { onPlay(detail.songs, i, false) }
            }
        }
    }
}
