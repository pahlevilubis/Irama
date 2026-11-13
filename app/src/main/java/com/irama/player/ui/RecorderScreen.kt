@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.irama.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.irama.player.audio.VoiceFx
import com.irama.player.ui.RecorderViewModel.Phase

@Composable
fun RecorderScreen(
    onBack: () -> Unit,
    onPauseMusic: () -> Unit,
    vm: RecorderViewModel = viewModel(),
) {
    val cs = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            onPauseMusic()
            vm.startRecording()
        } else {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk merekam", Toast.LENGTH_LONG).show()
        }
    }

    val recording = ui.phase == Phase.RECORDING
    val hasTake = ui.phase == Phase.READY || ui.phase == Phase.PROCESSING || ui.phase == Phase.SAVING

    // Layar tetap menyala selama merekam (kalau layar mati, Android memutus mikrofon)
    val view = LocalView.current
    DisposableEffect(recording) {
        view.keepScreenOn = recording
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { vm.onScreenClosed() }
    }
    LaunchedEffect(ui.message, ui.error) {
        val msg = ui.message ?: ui.error
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            vm.clearMessages()
        }
    }

    fun startRecording() {
        if (granted) {
            onPauseMusic()
            vm.startRecording()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Rekam Suara") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            val timeText = when {
                recording -> formatTime(ui.elapsedMs)
                hasTake -> formatTime(ui.durationMs)
                else -> "0:00"
            }
            Text(
                timeText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (recording) cs.secondary else cs.onSurface
            )
            Text(
                when (ui.phase) {
                    Phase.RECORDING -> "Merekam…  (maks ${RecorderViewModel.MAX_SECONDS / 60} menit)"
                    Phase.PROCESSING -> "Memproses efek ${ui.effect.label}…"
                    Phase.READY -> "Hasil rekaman • efek ${ui.effect.label}"
                    Phase.SAVING -> "Menyimpan…"
                    Phase.IDLE -> "Siap merekam"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            LevelMeter(
                levels = ui.levels,
                active = recording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            )
            Spacer(Modifier.height(24.dp))

            RecordButton(
                recording = recording,
                enabled = ui.phase != Phase.PROCESSING && ui.phase != Phase.SAVING,
                onClick = { if (recording) vm.stopRecording() else startRecording() }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    recording -> "Ketuk untuk berhenti"
                    hasTake -> "Ketuk untuk rekam ulang"
                    else -> "Ketuk untuk mulai merekam"
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            if (hasTake) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Pilih Efek Suara",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoiceFx.Effect.entries.forEach { e ->
                        FilterChip(
                            selected = ui.effect == e,
                            onClick = { vm.selectEffect(e) },
                            label = { Text(e.label) },
                            enabled = ui.phase != Phase.SAVING,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primary,
                                selectedLabelColor = cs.onPrimary,
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.effect.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (ui.phase == Phase.PROCESSING) {
                            CircularProgressIndicator(Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Memproses efek…", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            FilledIconButton(
                                onClick = { vm.togglePreview() },
                                enabled = ui.phase == Phase.READY,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    if (ui.previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (ui.previewing) "Berhenti" else "Dengarkan",
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (ui.previewing) "Memutar hasil…" else "Dengarkan hasil",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { ui.previewProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { vm.discard() },
                        enabled = ui.phase != Phase.SAVING,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Buang")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { vm.save() },
                        enabled = ui.phase == Phase.READY,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (ui.phase == Phase.SAVING) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Tips: rekam di tempat tenang, HP sekitar sejengkal dari mulut. Pakai earphone saat " +
                    "mendengarkan hasil supaya efeknya terasa. Rekaman tersimpan di folder Music/Irama/Rekaman " +
                    "dan langsung muncul di daftar lagu.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    val color = if (recording) cs.secondary else cs.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        if (recording) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f))
            )
        }
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(if (enabled) color else color.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (recording) "Berhenti merekam" else "Mulai merekam",
                tint = if (recording) cs.onSecondary else cs.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun LevelMeter(levels: List<Float>, active: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val barColor = if (active) cs.secondary else cs.onSurface.copy(alpha = 0.18f)
    val idleColor: Color = cs.onSurface.copy(alpha = 0.10f)
    Canvas(modifier) {
        val bars = 48
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        val mid = size.height / 2
        for (i in 0 until bars) {
            // nilai terbaru di kanan
            val idx = levels.size - bars + i
            val v = if (idx >= 0) levels[idx] else 0f
            val h = maxOf(4.dp.toPx(), v * size.height)
            val x = i * (w + gap)
            drawRoundRect(
                color = if (idx >= 0) barColor else idleColor,
                topLeft = Offset(x, mid - h / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2)
            )
        }
    }
}
