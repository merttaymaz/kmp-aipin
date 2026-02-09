package com.jetbrains.kmpapp.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

class AndroidAudioRecorder : AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecordingFlag = false

    companion object {
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun startRecording(onPCMData: (ByteArray) -> Unit) {
        if (isRecordingFlag) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        isRecordingFlag = true
        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            while (isRecordingFlag) {
                val readBytes = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readBytes > 0) {
                    // PCM verisi burada
                    onPCMData(buffer.copyOf(readBytes))
                }
            }
        }
    }

    override fun stopRecording() {
        isRecordingFlag = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun isRecording(): Boolean = isRecordingFlag
}
