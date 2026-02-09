package com.jetbrains.kmpapp.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jetbrains.kmpapp.audio.createAudioRecorder

@Composable
fun AudioRecordScreen() {
    var isRecording by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    val audioRecorder = remember { createAudioRecorder() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            audioRecorder.startRecording { pcmData ->
                // PCM verisi burada - 16kHz, mono, 16-bit PCM
                println("PCM data received: ${pcmData.size} bytes")
                // Bu veriyi istediğiniz şekilde işleyebilirsiniz
            }
            isRecording = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (audioRecorder.isRecording()) {
                audioRecorder.stopRecording()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = {
                if (isRecording) {
                    audioRecorder.stopRecording()
                    isRecording = false
                } else {
                    // İzin kontrolü
                    val permission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        audioRecorder.startRecording { pcmData ->
                            println("PCM data received: ${pcmData.size} bytes")
                        }
                        isRecording = true
                    } else {
                        // İzin iste
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier.size(80.dp),
            containerColor = if (isRecording) Color.Red else Color.Blue
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isRecording) "Durdur" else "Kaydet",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
