@file:OptIn(ExperimentalMaterial3Api::class)

package com.irama.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irama.player.data.Song

/** Permintaan memilih banyak lagu sekaligus (untuk playlist & folder). */
class PickerRequest(
    val title: String,
    val confirmLabel: String,
    val songs: List<Song>,
    val preselected: Set<Long> = emptySet(),
    val onDone: (List<Song>) -> Unit,
)

@Composable
fun SongPickerScreen(request: PickerRequest, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var selected by remember(request) { mutableStateOf(request.preselected) }
    var query by remember(request) { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = remember(request, q) {
        if (q.isEmpty()) request.songs
        else request.songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }
    val allFilteredSelected = filtered.isNotEmpty() && filtered.all { it.id in selected }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(request.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${selected.size} lagu dipilih",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Batal")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val chosen = request.songs.filter { it.id in selected }
                            onClose()
                            request.onDone(chosen)
                        },
                        enabled = selected.isNotEmpty()
                    ) {
                        Text(request.confirmLabel, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari lagu…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selected = if (allFilteredSelected) {
                        selected - filtered.map { it.id }.toSet()
                    } else {
                        selected + filtered.map { it.id }
                    }
                }) {
                    Text(if (allFilteredSelected) "Batal pilih semua" else "Pilih semua (${filtered.size})")
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { song ->
                    val checked = song.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - song.id else selected + song.id }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected = if (it) selected + song.id else selected - song.id }
                        )
                        AlbumArt(song.uri, Modifier.size(44.dp), corner = 8.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${song.artist}  •  ${formatTime(song.durationMs)}",
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
    }
}
