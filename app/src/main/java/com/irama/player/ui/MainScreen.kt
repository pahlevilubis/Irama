package com.irama.player.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.irama.player.audio.AudioFx
import com.irama.player.audio.SleepTimer
import com.irama.player.data.MediaFiles
import com.irama.player.data.MediaOps
import com.irama.player.download.OpenInbox
import com.irama.player.download.ShareInbox
import com.irama.player.download.YtDownloader

@Composable
fun IramaRoot(vm: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    fun isGranted() =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }

    // Cek ulang saat kembali dari Pengaturan
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isGranted() }
    LaunchedEffect(granted) { if (granted) vm.ensureLoaded() }

    // File audio yang dibuka lewat "Buka dengan → Irama" (tetap diputar walau izin belum diberikan)
    val opened by OpenInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(opened) {
        val uri = opened ?: return@LaunchedEffect
        OpenInbox.consume()
        vm.openExternal(uri)
    }

    if (granted) {
        MainScreen(vm)
    } else {
        PermissionScreen(
            askedOnce = askedOnce,
            onRequest = { launcher.launch(permission) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        )
    }
}

@Composable
private fun PermissionScreen(askedOnce: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Irama", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "by $APP_CREATOR",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Izinkan Irama membaca file musik di HP kamu supaya lagu-lagunya bisa diputar. " +
                    "Semua diputar offline, tidak ada data yang dikirim ke mana pun.",
                textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Izinkan Akses Musik")
            }
            if (askedOnce) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Tidak muncul? Buka Pengaturan → Izin → Musik dan audio")
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: PlayerViewModel) {
    val context = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val sortedSongs by vm.sortedSongs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val state by vm.ui.collectAsStateWithLifecycle()
    val fx by AudioFx.state.collectAsStateWithLifecycle()
    val sleepEndsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<Detail?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showEq by rememberSaveable { mutableStateOf(false) }
    var showDownload by rememberSaveable { mutableStateOf(false) }
    var showRecorder by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerRequest?>(null) }
    var dialog by remember { mutableStateOf<LibDialog?>(null) }

    // Pesan singkat dari ViewModel
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Hapus / ganti nama / pindah: minta izin sistem Android dulu kalau perlu
    var pendingOp by remember { mutableStateOf<MediaOps.Op?>(null) }
    val opLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val op = pendingOp
        pendingOp = null
        if (op != null && result.resultCode == Activity.RESULT_OK) {
            vm.runOp(op, systemDeleted = op is MediaOps.Delete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        }
    }
    val runOp: (MediaOps.Op) -> Unit = { op ->
        val sender = try {
            MediaOps.permissionRequest(context, op)
        } catch (e: Exception) {
            null
        }
        if (sender != null) {
            pendingOp = op
            opLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            vm.runOp(op, systemDeleted = false)
        }
    }
    LaunchedEffect(Unit) {
        vm.permissionRequests.collect { (op, sender) ->
            pendingOp = op
            opLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // File dibuka dari aplikasi lain → langsung tampilkan layar pemutar
    val openPlayer by vm.openPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            vm.consumeOpenPlayer()
            showEq = false
            showRecorder = false
            showDownload = false
            picker = null
            dialog = null
            showPlayer = true
        }
    }

    // Link YouTube yang dibagikan ke Irama → buka layar Download dan langsung proses
    val shared by ShareInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        ShareInbox.consume()
        if (YtDownloader.enqueue(text)) {
            showPlayer = false
            showEq = false
            showRecorder = false
            picker = null
            showDownload = true
        } else {
            Toast.makeText(context, "Yang dibagikan bukan link YouTube", Toast.LENGTH_SHORT).show()
        }
    }

    // Isi layar daftar (album/folder/playlist) ikut ter-update setelah lagu diubah
    val byId = remember(songs) { songs.associateBy { it.id } }
    val liveDetail: Detail? = detail?.let { d ->
        when (d.kind) {
            DetailKind.PLAYLIST -> playlists.firstOrNull { it.id == d.key }?.let { p -> d.copy(title = p.name, songs = p.songs) }
            DetailKind.FOLDER -> folders.firstOrNull { it.key == d.key }
                ?.let { g -> d.copy(title = g.name, subtitle = g.key, songs = g.songs) }
                ?: d.copy(songs = emptyList())
            DetailKind.LIST -> d.copy(songs = d.songs.mapNotNull { byId[it.id] })
        }
    }
    LaunchedEffect(liveDetail == null, detail != null) {
        if (liveDetail == null && detail != null) detail = null // playlist sudah dihapus
    }

    val play: PlayAction = { list, index, shuffle -> vm.play(list, index, shuffle) }
    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(
            state = state,
            onOpen = { showPlayer = true },
            onToggle = vm::togglePlay,
            onNext = vm::next,
        )
    }
    val fxLabel = if (!fx.enabled) "Efek: mati" else "Mode: ${fx.preset}"

    val actions = LibraryActions(
        vm = vm,
        allSongs = { sortedSongs },
        folders = { folders },
        playlists = { playlists },
        runOp = runOp,
        openPicker = { picker = it },
        show = { dialog = it },
        onFolderRenamed = { oldKey, newKey ->
            val d = detail
            if (d != null && d.kind == DetailKind.FOLDER && d.key == oldKey) detail = d.copy(key = newKey)
        },
        share = { song ->
            try {
                MediaFiles.share(context, song)
            } catch (e: Exception) {
                Toast.makeText(context, "Tidak bisa membagikan lagu ini", Toast.LENGTH_SHORT).show()
            }
        },
    )
    val homeCallbacks = HomeCallbacks(
        onPlay = play,
        onOpenDetail = { detail = it },
        onOpenEq = { showEq = true },
        onOpenDownload = { showDownload = true },
        onOpenRecorder = { showRecorder = true },
        onRefresh = vm::reloadLibrary,
        onSortChange = vm::setSort,
        onSongMore = { dialog = LibDialog.SongMenu(it) },
        onFolderMore = { dialog = LibDialog.FolderMenu(it) },
        onNewFolder = { dialog = LibDialog.NewFolder(null) },
        onPlaylistMore = { dialog = LibDialog.PlaylistMenu(it) },
        onNewPlaylist = { dialog = LibDialog.NewPlaylist(null) },
    )

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            songs = sortedSongs,
            albums = albums,
            artists = artists,
            folders = folders,
            playlists = playlists,
            favoriteIds = favoriteIds,
            sort = sort,
            loading = loading,
            state = state,
            overlayOpen = detail != null || showPlayer || showEq || showDownload || showRecorder || picker != null,
            cb = homeCallbacks,
            miniPlayer = miniPlayer,
        )

        SlideLayer(liveDetail) { d ->
            val playlist = if (d.kind == DetailKind.PLAYLIST) playlists.firstOrNull { it.id == d.key } else null
            val folder = if (d.kind == DetailKind.FOLDER) folders.firstOrNull { it.key == d.key } else null
            DetailScreen(
                detail = d,
                state = state,
                favoriteIds = favoriteIds,
                onBack = { detail = null },
                onPlay = play,
                onSongMore = { s -> dialog = LibDialog.SongMenu(s, playlist) },
                onMore = when {
                    playlist != null -> {
                        { dialog = LibDialog.PlaylistMenu(playlist) }
                    }
                    folder != null -> {
                        { dialog = LibDialog.FolderMenu(folder) }
                    }
                    else -> null
                },
                onAddSongs = when {
                    playlist != null -> {
                        {
                            picker = PickerRequest("Tambah ke \"${playlist.name}\"", "Tambah", sortedSongs) { chosen ->
                                vm.addToPlaylist(playlist.id, chosen)
                            }
                        }
                    }
                    folder != null -> {
                        {
                            picker = PickerRequest(
                                "Pindahkan ke \"${folder.name}\"",
                                "Pindahkan",
                                sortedSongs.filter { it.folder != folder.key }
                            ) { chosen -> runOp(MediaOps.Move(chosen, folder.key, folder.name)) }
                        }
                    }
                    else -> null
                },
                miniPlayer = miniPlayer,
            )
        }

        SlideLayer(if (showDownload) Unit else null) {
            DownloadScreen(onBack = { showDownload = false }, miniPlayer = miniPlayer)
        }

        SlideLayer(if (showRecorder) Unit else null) {
            RecorderScreen(onBack = { showRecorder = false }, onPauseMusic = vm::pause)
        }

        SlideLayer(picker, fromBottom = true) { req ->
            SongPickerScreen(req, onClose = { picker = null })
        }

        SlideLayer(if (showPlayer && state.hasMedia) Unit else null, fromBottom = true) {
            val currentSong = vm.songByMediaId(state.mediaId)
            NowPlayingScreen(
                state = state,
                fxLabel = fxLabel,
                sleepEndsAt = sleepEndsAt,
                onClose = { showPlayer = false },
                onToggle = vm::togglePlay,
                onNext = vm::next,
                onPrev = vm::previous,
                onSeek = vm::seekTo,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::cycleRepeat,
                onOpenEq = { showEq = true },
                onSleep = { minutes ->
                    if (minutes == null) SleepTimer.cancel() else SleepTimer.start(minutes)
                },
                isFavorite = currentSong != null && currentSong.id in favoriteIds,
                onToggleFavorite = { currentSong?.let { vm.toggleFavorite(it) } },
                onMore = { currentSong?.let { dialog = LibDialog.SongMenu(it) } },
                onSpeed = { dialog = LibDialog.SpeedPitch },
            )
        }

        SlideLayer(if (showEq) Unit else null) {
            EqualizerScreen(fx = fx, onBack = { showEq = false })
        }
    }

    LibraryDialogHost(dialog = dialog, favoriteIds = favoriteIds, state = state, actions = actions)

    // Tombol Back: tutup lapisan paling atas dulu
    BackHandler(enabled = showEq || showPlayer || picker != null || showRecorder || showDownload || detail != null) {
        when {
            showEq -> showEq = false
            showPlayer -> showPlayer = false
            picker != null -> picker = null
            showRecorder -> showRecorder = false
            showDownload -> showDownload = false
            else -> detail = null
        }
    }
}
