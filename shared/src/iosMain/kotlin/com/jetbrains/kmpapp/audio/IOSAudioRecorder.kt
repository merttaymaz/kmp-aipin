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

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)

        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,
            format = recordingFormat
        ) { buffer, _ ->
            buffer?.let {
                val abl = it.audioBufferList?.pointed
                val audioBuffer0 = abl?.mBuffers?.get(0)

                if (audioBuffer0 != null) {
                    val dataPtr = audioBuffer0.mData?.reinterpret<ByteVar>()
                    val size = audioBuffer0.mDataByteSize.toInt()

                    if (dataPtr != null && size > 0) {
                        val byteArray = ByteArray(size) { index ->
                            dataPtr[index]
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
