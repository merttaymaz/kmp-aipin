@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.jetbrains.kmpapp.audio

import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun createAudioRecorder(): AudioRecorder = IOSAudioRecorder()

class IOSAudioRecorder : AudioRecorder {
    private var audioEngine: AVAudioEngine? = null
    private var isRecordingFlag = false

    override fun startRecording(onPCMData: (ByteArray) -> Unit) {
        if (isRecordingFlag) return

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode

        // PCM format: 16kHz, mono, 16-bit
        val recordingFormat = AVAudioFormat(
            commonFormat = AVAudioCommonFormatPCMFormatInt16,
            sampleRate = 16000.0,
            channels = 1u,
            interleaved = true
        )

        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,
            format = recordingFormat
        ) { buffer, _ ->
            buffer?.let {
                val audioBuffer = it.audioBufferList?.pointed?.mBuffers
                audioBuffer?.let { buf ->
                    val data = buf.mData?.reinterpret<ByteVar>()
                    val size = buf.mDataByteSize.toInt()
                    
                    if (data != null && size > 0) {
                        val byteArray = ByteArray(size) { index ->
                            data[index]
                        }
                        onPCMData(byteArray)
                    }
                }
            }
        }

        audioEngine = engine
        
        try {
            engine.startAndReturnError(null)
            isRecordingFlag = true
        } catch (e: Exception) {
            println("Error starting audio engine: ${e.message}")
        }
    }

    override fun stopRecording() {
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        audioEngine = null
        isRecordingFlag = false
    }

    override fun isRecording(): Boolean = isRecordingFlag
}
