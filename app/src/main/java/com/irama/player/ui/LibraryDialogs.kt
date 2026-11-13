@file:OptIn(ExperimentalMaterial3Api::class)

package com.irama.player.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irama.player.data.MediaFiles
import com.irama.player.data.MediaOps
import com.irama.player.data.NamedGroup
import com.irama.player.data.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/** Dialog / menu yang sedang tampil di layar utama. */
sealed interface LibDialog {
    data class SongMenu(val song: Song, val playlist: PlaylistView? = null) : LibDialog
    data class FolderMenu(val group: NamedGroup) : LibDialog
    data class PlaylistMenu(val playlist: PlaylistView) : LibDialog
    data class RenameSong(val song: Song) : LibDialog
    data class MoveTo(val songs: List<Song>) : LibDialog
    /** [songs] null → setelah memberi nama, pilih lagu yang mau dimasukkan. */
    data class NewFolder(val songs: List<Song>?) : LibDialog
    data class RenameFolder(val group: NamedGroup) : LibDialog
    data class DeleteFolder(val group: NamedGroup) : LibDialog
    data class DeleteSong(val song: Song) : LibDialog
    data class AddToPlaylist(val songs: List<Song>) : LibDialog
    data class NewPlaylist(val songs: List<Song>?) : LibDialog
    data class RenamePlaylist(val playlist: PlaylistView) : LibDialog
    data class DeletePlaylist(val playlist: PlaylistView) : LibDialog
    data class Info(val song: Song) : LibDialog
    data object SpeedPitch : LibDialog
}

/** Aksi yang dipakai dialog; disediakan oleh MainScreen. */
class LibraryActions(
    val vm: PlayerViewModel,
    val allSongs: () -> List<Song>,
    val folders: () -> List<NamedGroup>,
    val playlists: () -> List<PlaylistView>,
    val runOp: (MediaOps.Op) -> Unit,
    val openPicker: (PickerRequest) -> Unit,
    val show: (LibDialog?) -> Unit,
    val onFolderRenamed: (oldKey: String, newKey: String) -> Unit,
    val share: (Song) -> Unit,
)

@Composable
fun LibraryDialogHost(
    dialog: LibDialog?,
    favoriteIds: Set<Long>,
    state: PlaybackState,
    actions: LibraryActions,
) {
    val d = dialog ?: return
    val dismiss: () -> Unit = { actions.show(null) }
    val notBlank: (String) -> String? = { if (it.isBlank()) "Nama tidak boleh kosong" else null }

    when (d) {
        is LibDialog.SongMenu -> SongMenuSheet(d, d.song.id in favoriteIds, actions, dismiss)
        is LibDialog.FolderMenu -> FolderMenuSheet(d.group, actions, dismiss)
        is LibDialog.PlaylistMenu -> PlaylistMenuSheet(d.playlist, actions, dismiss)

        is LibDialog.RenameSong -> TextInputDialog(
            title = "Ganti Nama Lagu",
            label = "Judul baru",
            initial = d.song.title,
            confirm = "Simpan",
            validate = notBlank,
            onConfirm = { name ->
                dismiss()
                actions.runOp(MediaOps.Rename(d.song, name.trim()))
            },
            onDismiss = dismiss
        )

        is LibDialog.MoveTo -> MoveToDialog(d.songs, actions, dismiss)

        is LibDialog.NewFolder -> TextInputDialog(
            title = "Folder Baru",
            label = "Nama folder",
            initial = "",
            confirm = if (d.songs != null) "Buat & Pindahkan" else "Lanjut",
            validate = { MediaFiles.folderNameError(it) },
            onConfirm = { name ->
                val n = name.trim()
                val path = "Music/$n/"
                dismiss()
                val songs = d.songs
                if (songs != null) {
                    actions.runOp(MediaOps.Move(songs, path, n))
                } else {
                    actions.openPicker(
                        PickerRequest("Isi folder \"$n\"", "Pindahkan", actions.allSongs()) { chosen ->
                            actions.runOp(MediaOps.Move(chosen, path, n))
                        }
                    )
                }
            },
            onDismiss = dismiss
        )

        is LibDialog.RenameFolder -> {
            val parent = MediaOps.parentOf(d.group.key)
            TextInputDialog(
                title = "Ganti Nama Folder",
                label = "Nama baru",
                initial = d.group.name,
                confirm = "Simpan",
                validate = {
                    MediaFiles.folderNameError(it)
                        ?: if (parent.isEmpty()) "Folder utama tidak bisa diganti namanya" else null
                },
                onConfirm = { name ->
                    val n = name.trim()
                    val newKey = "$parent$n/"
                    dismiss()
                    actions.onFolderRenamed(d.group.key, newKey)
                    actions.runOp(MediaOps.Move(d.group.songs, newKey, n))
                },
                onDismiss = dismiss
            )
        }

        is LibDialog.DeleteFolder -> ConfirmDialog(
            title = "Hapus Folder?",
            message = "Folder \"${d.group.name}\" beserta ${d.group.songs.size} lagu di dalamnya akan dihapus dari HP. " +
                "Ini tidak bisa dibatalkan.",
            confirm = "Hapus",
            destructive = true,
            onConfirm = {
                dismiss()
                actions.runOp(MediaOps.Delete(d.group.songs))
            },
            onDismiss = dismiss
        )

        is LibDialog.DeleteSong -> ConfirmDialog(
            title = "Hapus Lagu?",
            message = "\"${d.song.title}\" akan dihapus dari HP.",
            confirm = "Hapus",
            destructive = true,
            onConfirm = {
                dismiss()
                actions.runOp(MediaOps.Delete(listOf(d.song)))
            },
            onDismiss = dismiss
        )

        is LibDialog.AddToPlaylist -> AddToPlaylistDialog(d.songs, actions, dismiss)

        is LibDialog.NewPlaylist -> TextInputDialog(
            title = "Playlist Baru",
            label = "Nama playlist",
            initial = "",
            confirm = "Buat",
            validate = notBlank,
            onConfirm = { name ->
                dismiss()
                val songs = d.songs
                val id = actions.vm.createPlaylist(name, songs ?: emptyList())
                if (songs == null) {
                    actions.openPicker(
                        PickerRequest("Tambah lagu ke \"${name.trim()}\"", "Tambah", actions.allSongs()) { chosen ->
                            actions.vm.addToPlaylist(id, chosen)
                        }
                    )
                }
            },
            onDismiss = dismiss
        )

        is LibDialog.RenamePlaylist -> TextInputDialog(
            title = "Ganti Nama Playlist",
            label = "Nama baru",
            initial = d.playlist.name,
            confirm = "Simpan",
            validate = notBlank,
            onConfirm = { name ->
                dismiss()
                actions.vm.renamePlaylist(d.playlist.id, name)
            },
            onDismiss = dismiss
        )

        is LibDialog.DeletePlaylist -> ConfirmDialog(
            title = "Hapus Playlist?",
            message = "Playlist \"${d.playlist.name}\" akan dihapus. Lagu-lagunya tetap ada di HP.",
            confirm = "Hapus",
            destructive = true,
            onConfirm = {
                dismiss()
                actions.vm.deletePlaylist(d.playlist.id)
            },
            onDismiss = dismiss
        )

        is LibDialog.Info -> SongInfoDialog(d.song, dismiss)
        LibDialog.SpeedPitch -> SpeedPitchDialog(state, actions.vm, dismiss)
    }
}

// ---------- Menu bawah (bottom sheet) ----------

@Composable
private fun SheetHeader(song: Song?, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(song?.uri, Modifier.size(52.dp), corner = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) cs.error else cs.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (destructive) cs.error else cs.onSurface)
    }
}

@Composable
private fun SongMenuSheet(d: LibDialog.SongMenu, isFavorite: Boolean, a: LibraryActions, dismiss: () -> Unit) {
    val song = d.song
    val playlist = d.playlist
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SheetHeader(song, song.title, song.artist)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            MenuItem(Icons.Rounded.QueuePlayNext, "Putar berikutnya") {
                dismiss()
                a.vm.playNext(song)
            }
            MenuItem(Icons.Rounded.AddToQueue, "Tambah ke antrean") {
                dismiss()
                a.vm.addToQueue(song)
            }
            MenuItem(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit"
            ) {
                dismiss()
                a.vm.toggleFavorite(song)
            }
            MenuItem(Icons.Rounded.PlaylistAdd, "Tambah ke playlist…") {
                a.show(LibDialog.AddToPlaylist(listOf(song)))
            }
            if (playlist != null && !playlist.isFavorites) {
                MenuItem(Icons.Rounded.RemoveCircleOutline, "Keluarkan dari playlist ini") {
                    dismiss()
                    a.vm.removeFromPlaylist(playlist.id, song)
                }
            }
            MenuItem(Icons.Rounded.Edit, "Ganti nama") {
                a.show(LibDialog.RenameSong(song))
            }
            MenuItem(Icons.Rounded.DriveFileMove, "Pindah ke folder…") {
                a.show(LibDialog.MoveTo(listOf(song)))
            }
            MenuItem(Icons.Rounded.Share, "Bagikan (WhatsApp, dll.)") {
                dismiss()
                a.share(song)
            }
            MenuItem(Icons.Rounded.Info, "Info lagu") {
                a.show(LibDialog.Info(song))
            }
            MenuItem(Icons.Rounded.Delete, "Hapus dari HP", destructive = true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ menampilkan dialog konfirmasi sistem sendiri
                    dismiss()
                    a.runOp(MediaOps.Delete(listOf(song)))
                } else {
                    a.show(LibDialog.DeleteSong(song))
                }
            }
        }
    }
}

@Composable
private fun FolderMenuSheet(group: NamedGroup, a: LibraryActions, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SheetHeader(group.songs.firstOrNull(), group.name, group.subtitle)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            if (group.songs.isNotEmpty()) {
                MenuItem(Icons.Rounded.PlayArrow, "Putar semua") {
                    dismiss()
                    a.vm.play(group.songs, 0)
                }
                MenuItem(Icons.Rounded.Shuffle, "Putar acak") {
                    dismiss()
                    a.vm.play(group.songs, Random.nextInt(group.songs.size), shuffle = true)
                }
            }
            MenuItem(Icons.Rounded.Add, "Masukkan lagu ke folder ini…") {
                dismiss()
                a.openPicker(
                    PickerRequest(
                        "Pindahkan ke \"${group.name}\"",
                        "Pindahkan",
                        a.allSongs().filter { it.folder != group.key }
                    ) { chosen -> a.runOp(MediaOps.Move(chosen, group.key, group.name)) }
                )
            }
            MenuItem(Icons.Rounded.Edit, "Ganti nama folder") {
                a.show(LibDialog.RenameFolder(group))
            }
            MenuItem(Icons.Rounded.Delete, "Hapus folder & lagunya", destructive = true) {
                a.show(LibDialog.DeleteFolder(group))
            }
        }
    }
}

@Composable
private fun PlaylistMenuSheet(p: PlaylistView, a: LibraryActions, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SheetHeader(p.songs.firstOrNull(), p.name, "${p.songs.size} lagu")
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            if (p.songs.isNotEmpty()) {
                MenuItem(Icons.Rounded.PlayArrow, "Putar") {
                    dismiss()
                    a.vm.play(p.songs, 0)
                }
                MenuItem(Icons.Rounded.Shuffle, "Putar acak") {
                    dismiss()
                    a.vm.play(p.songs, Random.nextInt(p.songs.size), shuffle = true)
                }
            }
            MenuItem(Icons.Rounded.Add, "Tambah lagu…") {
                dismiss()
                a.openPicker(
                    PickerRequest("Tambah ke \"${p.name}\"", "Tambah", a.allSongs()) { chosen ->
                        a.vm.addToPlaylist(p.id, chosen)
                    }
                )
            }
            if (!p.isFavorites) {
                MenuItem(Icons.Rounded.Edit, "Ganti nama playlist") {
                    a.show(LibDialog.RenamePlaylist(p))
                }
                MenuItem(Icons.Rounded.Delete, "Hapus playlist", destructive = true) {
                    a.show(LibDialog.DeletePlaylist(p))
                }
            }
        }
    }
}

// ---------- Dialog pilihan ----------

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = cs.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MoveToDialog(songs: List<Song>, a: LibraryActions, dismiss: () -> Unit) {
    val current = songs.map { it.folder }.distinct().singleOrNull()
    val folders = a.folders().filter { it.key != current }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Pindah ke Folder") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    ChoiceRow(Icons.Rounded.CreateNewFolder, "Folder baru…", "Dibuat di dalam folder Music") {
                        a.show(LibDialog.NewFolder(songs))
                    }
                }
                items(folders, key = { it.key }) { g ->
                    ChoiceRow(Icons.Rounded.Folder, g.name, g.key) {
                        dismiss()
                        a.runOp(MediaOps.Move(songs, g.key, g.name))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dismiss) { Text("Batal") } }
    )
}

@Composable
private fun AddToPlaylistDialog(songs: List<Song>, a: LibraryActions, dismiss: () -> Unit) {
    val playlists = a.playlists()
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Tambah ke Playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    ChoiceRow(Icons.Rounded.Add, "Playlist baru…", null) {
                        a.show(LibDialog.NewPlaylist(songs))
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    ChoiceRow(
                        if (p.isFavorites) Icons.Rounded.Favorite else Icons.Rounded.QueueMusic,
                        p.name,
                        "${p.songs.size} lagu"
                    ) {
                        dismiss()
                        a.vm.addToPlaylist(p.id, songs)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dismiss) { Text("Batal") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    var touched by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val error = validate(value.text)
    val shownError = if (touched) error else null
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    touched = true
                },
                label = { Text(label) },
                singleLine = true,
                isError = shownError != null,
                supportingText = if (shownError != null) {
                    { Text(shownError) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (error == null) onConfirm(value.text) }, enabled = error == null) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val added = if (song.dateAdded > 0) {
        SimpleDateFormat("d MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID")).format(Date(song.dateAdded * 1000))
    } else "-"
    val format = song.extension.removePrefix(".").uppercase(Locale.ROOT).ifBlank { song.mime.ifBlank { "-" } }
    val rows = listOf(
        "Judul" to song.title,
        "Artis" to song.artist,
        "Album" to song.album,
        "Durasi" to formatTime(song.durationMs),
        "Format" to format,
        "Ukuran" to String.format(Locale.US, "%.1f MB", song.sizeBytes / 1_048_576.0),
        "Nama file" to song.displayName.ifBlank { "-" },
        "Folder" to song.folder.ifBlank { "-" },
        "Ditambahkan" to added,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Info Lagu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun SpeedPitchDialog(state: PlaybackState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    var speed by remember { mutableFloatStateOf(state.speed) }
    var pitch by remember { mutableFloatStateOf(state.pitchSemitones) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Kecepatan & Nada") },
        text = {
            Column {
                Text(
                    "Kecepatan: ${String.format(Locale.US, "%.2f", speed)}x",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = speed,
                    onValueChange = {
                        speed = (it * 20).roundToInt() / 20f
                        vm.setSpeedPitch(speed, pitch)
                    },
                    valueRange = 0.5f..2f,
                    steps = 29
                )
                Spacer(Modifier.width(8.dp))
                val p = pitch.roundToInt()
                Text(
                    "Nada: " + when {
                        p > 0 -> "+$p (lebih tinggi)"
                        p < 0 -> "$p (lebih rendah)"
                        else -> "normal"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = pitch,
                    onValueChange = {
                        pitch = it.roundToInt().toFloat()
                        vm.setSpeedPitch(speed, pitch)
                    },
                    valueRange = -6f..6f,
                    steps = 11
                )
                Text(
                    "Tips karaoke: turunkan nada kalau lagunya terlalu tinggi untuk suaramu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } },
        dismissButton = {
            TextButton(onClick = {
                speed = 1f
                pitch = 0f
                vm.setSpeedPitch(1f, 0f)
            }) { Text("Reset") }
        }
    )
}
