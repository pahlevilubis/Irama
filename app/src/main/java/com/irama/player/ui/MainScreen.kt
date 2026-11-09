package com.irama.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
            Spacer(Modifier.height(12.dp))
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
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val state by vm.ui.collectAsStateWithLifecycle()
    val fx by AudioFx.state.collectAsStateWithLifecycle()
    val sleepEndsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<Detail?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showEq by rememberSaveable { mutableStateOf(false) }

    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(
            state = state,
            onOpen = { showPlayer = true },
            onToggle = vm::togglePlay,
            onNext = vm::next,
        )
    }
    val play: PlayAction = { list, index, shuffle -> vm.play(list, index, shuffle) }
    val fxLabel = if (!fx.enabled) "Efek: mati" else "Mode: ${fx.preset}"

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            songs = songs,
            albums = albums,
            artists = artists,
            folders = folders,
            loading = loading,
            state = state,
            overlayOpen = detail != null || showPlayer || showEq,
            onPlay = play,
            onOpenDetail = { detail = it },
            onOpenEq = { showEq = true },
            onRefresh = vm::reloadLibrary,
            miniPlayer = miniPlayer,
        )

        SlideLayer(detail) { d ->
            DetailScreen(
                detail = d,
                state = state,
                onBack = { detail = null },
                onPlay = play,
                miniPlayer = miniPlayer,
            )
        }

        SlideLayer(if (showPlayer && state.hasMedia) Unit else null, fromBottom = true) {
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
            )
        }

        SlideLayer(if (showEq) Unit else null) {
            EqualizerScreen(fx = fx, onBack = { showEq = false })
        }
    }

    // Tombol Back: tutup lapisan paling atas dulu
    BackHandler(enabled = showEq || showPlayer || detail != null) {
        when {
            showEq -> showEq = false
            showPlayer -> showPlayer = false
            else -> detail = null
        }
    }
}
