@file:OptIn(ExperimentalFoundationApi::class)

package com.irama.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    fxLabel: String,
    sleepEndsAt: Long?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenEq: () -> Unit,
    onSleep: (Int?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showSleep by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Box(Modifier.fillMaxSize()) {
            // Latar belakang: cover album yang di-blur
            AlbumArt(
                state.artUri,
                Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.5f),
                sizePx = 512,
                corner = 0.dp,
                iconSize = 0.dp
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                cs.background.copy(alpha = 0.45f),
                                cs.background.copy(alpha = 0.80f),
                                cs.background
                            )
                        )
                    )
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bar atas
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup", modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "SEDANG DIPUTAR",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 2.sp,
                            color = cs.onSurfaceVariant
                        )
                        Text(fxLabel, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                    IconButton(onClick = onOpenEq) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer")
                    }
                }

                Spacer(Modifier.weight(0.6f))

                AlbumArt(
                    state.artUri,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    sizePx = 900,
                    corner = 24.dp,
                    iconSize = 96.dp
                )

                Spacer(Modifier.weight(0.6f))

                // Judul & artis
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Seek bar
                var dragging by remember { mutableStateOf<Float?>(null) }
                val duration = state.durationMs
                val fraction = dragging
                    ?: if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Slider(
                    value = fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        val d = dragging
                        if (d != null && duration > 0) onSeek((d * duration).toLong())
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = cs.primary,
                        activeTrackColor = cs.primary,
                        inactiveTrackColor = cs.onSurface.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = if (dragging != null) (fraction * duration).toLong() else state.positionMs
                    Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))

                // Tombol kontrol
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Acak",
                            tint = if (state.shuffle) cs.primary else cs.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onPrev, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", modifier = Modifier.size(40.dp))
                    }
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary
                        )
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = onRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Ulangi",
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) cs.primary else cs.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.weight(0.5f))

                // Tombol bawah: timer tidur & equalizer
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(sleepEndsAt) {
                    while (sleepEndsAt != null) {
                        now = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                val sleepLabel = sleepEndsAt?.let { "Berhenti ${formatTime(it - now)}" } ?: "Timer Tidur"

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { showSleep = true }) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (sleepEndsAt != null) cs.primary else cs.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(sleepLabel, color = if (sleepEndsAt != null) cs.primary else cs.onSurface)
                    }
                    TextButton(onClick = onOpenEq) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Equalizer", color = cs.onSurface)
                    }
                }
            }
        }
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            title = { Text("Timer Tidur") },
            text = {
                Column {
                    Text("Musik berhenti otomatis setelah:", color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        Text(
                            "$minutes menit",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSleep(minutes)
                                    showSleep = false
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (sleepEndsAt != null) {
                    TextButton(onClick = {
                        onSleep(null)
                        showSleep = false
                    }) { Text("Matikan Timer") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleep = false }) { Text("Tutup") }
            },
            containerColor = cs.surfaceContainerHigh
        )
    }
}
