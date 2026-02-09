package com.jetbrains.kmpapp.audio

interface AudioRecorder {
    fun startRecording(onPCMData: (ByteArray) -> Unit)
    fun stopRecording()
    fun isRecording(): Boolean
}

expect fun createAudioRecorder(): AudioRecorder
