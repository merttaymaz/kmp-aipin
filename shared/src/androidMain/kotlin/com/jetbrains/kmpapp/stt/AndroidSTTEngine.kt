package com.jetbrains.kmpapp.stt

import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Android STT Engine using Sherpa-ONNX.
 * Supports both Whisper (offline) and Zipformer (online/streaming) models.
 *
 * Model files are resolved via ModelRegistry's modelFiles map:
 *   - "encoder" → encoder ONNX file path
 *   - "decoder" → decoder ONNX file path
 *   - "tokens"  → tokens.txt path
 *
 * The config map passed to initialize() must contain:
 *   - "modelBasePath" → absolute path to the directory containing model files
 *   - "modelType" → "whisper" or "zipformer" (defaults to "whisper")
 *   - "language" → ISO 639-1 language code (defaults to "en")
 */
class AndroidSTTEngine : STTEngine {

    override val engineId: String = "android-stt-sherpa-onnx"

    // Sherpa-ONNX recognizers
    private var onlineRecognizer: OnlineRecognizer? = null   // Zipformer (streaming)
    private var offlineRecognizer: OfflineRecognizer? = null  // Whisper (offline)
    private var onlineStream: OnlineStream? = null

    // State
    private var initialized = false
    private var currentModelId: String? = null
    private var currentModelType: String = "whisper"  // "whisper" or "zipformer"
    private var currentLanguage: String = "en"
    private var recognizing = false
    private var sampleRate: Int = 16000

    // Result flow for emitting recognition results
    private val resultFlow = MutableSharedFlow<STTResult>(extraBufferCapacity = 64)

    // Accumulated audio buffer for Whisper (offline) processing
    private val whisperAudioBuffer = mutableListOf<Float>()

    // Memory tracking
    private var estimatedMemory: Long = 40 * 1024 * 1024L  // default 40 MB

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        // Release any previous model
        if (initialized) release()

        currentModelId = modelId
        val modelBasePath = config["modelBasePath"] as? String ?: ""
        currentModelType = config["modelType"] as? String ?: "whisper"
        currentLanguage = config["language"] as? String ?: "en"

        try {
            when (currentModelType) {
                "zipformer" -> initializeZipformer(modelBasePath)
                else -> initializeWhisper(modelBasePath, currentLanguage)
            }
            initialized = true
        } catch (e: Exception) {
            initialized = false
            throw e
        }
    }

    private fun initializeZipformer(modelBasePath: String) {
        val recognizerConfig = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelBasePath/encoder.onnx",
                    decoder = "$modelBasePath/decoder.onnx",
                    joiner = "$modelBasePath/joiner.onnx"
                ),
                tokens = "$modelBasePath/tokens.txt",
                modelType = "zipformer2"
            ),
            enableEndpoint = true,
            maxActivePaths = 4,
            decodingMethod = "greedy_search"
        )

        onlineRecognizer = OnlineRecognizer(assetManager = null, config = recognizerConfig)
        estimatedMemory = 40 * 1024 * 1024L
    }

    private fun initializeWhisper(modelBasePath: String, language: String) {
        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$modelBasePath/whisper-encoder.onnx",
                    decoder = "$modelBasePath/whisper-decoder.onnx",
                    language = language,
                    task = "transcribe"
                ),
                tokens = "$modelBasePath/whisper-tokens.txt",
                modelType = "whisper"
            ),
            decodingMethod = "greedy_search"
        )

        offlineRecognizer = OfflineRecognizer(assetManager = null, config = recognizerConfig)
        estimatedMemory = if (currentModelId?.contains("base") == true) {
            75 * 1024 * 1024L
        } else {
            40 * 1024 * 1024L
        }
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        stopRecognition()
        onlineRecognizer?.release()
        offlineRecognizer?.release()
        onlineRecognizer = null
        offlineRecognizer = null
        initialized = false
        currentModelId = null
        whisperAudioBuffer.clear()
    }

    override fun getMemoryUsageBytes(): Long = if (initialized) estimatedMemory else 0L

    override fun getEstimatedMemoryBytes(): Long = estimatedMemory

    override fun startRecognition(config: STTConfig): Flow<STTResult> = callbackFlow {
        if (!initialized) {
            trySend(STTResult(text = "", isFinal = true, language = config.language, confidence = 0f))
            close()
            return@callbackFlow
        }

        recognizing = true
        sampleRate = config.sampleRate

        when (currentModelType) {
            "zipformer" -> {
                val recognizer = onlineRecognizer
                if (recognizer == null) {
                    trySend(STTResult(text = "", isFinal = true, confidence = 0f))
                    close()
                    return@callbackFlow
                }
                onlineStream = recognizer.createStream()
            }
            "whisper" -> {
                whisperAudioBuffer.clear()
            }
        }

        // Collect results from the shared flow and forward them
        val job = CoroutineScope(coroutineContext).launch {
            resultFlow.collect { result ->
                trySend(result)
            }
        }

        awaitClose {
            job.cancel()
            stopRecognition()
        }
    }

    override fun processAudioData(audioData: ShortArray) {
        if (!recognizing || !initialized) return

        when (currentModelType) {
            "zipformer" -> processZipformer(audioData)
            "whisper" -> processWhisper(audioData)
        }
    }

    private fun processZipformer(audioData: ShortArray) {
        val stream = onlineStream ?: return
        val recognizer = onlineRecognizer ?: return

        // Convert PCM 16-bit to float samples
        val floatSamples = FloatArray(audioData.size) { audioData[it].toFloat() / 32768.0f }
        stream.acceptWaveform(floatSamples, sampleRate)

        // Decode available frames
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }

        val result = recognizer.getResult(stream)
        if (result.text.isNotEmpty()) {
            val isFinal = recognizer.isEndpoint(stream)
            resultFlow.tryEmit(
                STTResult(
                    text = result.text.trim(),
                    isFinal = isFinal,
                    language = currentLanguage,
                    confidence = null
                )
            )
            if (isFinal) {
                recognizer.reset(stream)
            }
        }
    }

    private fun processWhisper(audioData: ShortArray) {
        // Accumulate audio for batch processing
        for (sample in audioData) {
            whisperAudioBuffer.add(sample.toFloat() / 32768.0f)
        }

        // Whisper works best with complete utterances.
        // Emit partial result to show we're receiving audio.
        // Final decode happens in stopRecognition() or when buffer reaches threshold.
        val bufferDurationSec = whisperAudioBuffer.size.toFloat() / sampleRate

        // Process every ~3 seconds of audio for intermediate results
        if (bufferDurationSec >= 3.0f) {
            decodeWhisperBuffer(isFinal = false)
        }
    }

    private fun decodeWhisperBuffer(isFinal: Boolean) {
        val recognizer = offlineRecognizer ?: return
        if (whisperAudioBuffer.isEmpty()) return

        val samples = whisperAudioBuffer.toFloatArray()
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)

        val result = recognizer.getResult(stream)
        stream.release()

        if (result.text.isNotEmpty()) {
            resultFlow.tryEmit(
                STTResult(
                    text = result.text.trim(),
                    isFinal = isFinal,
                    language = currentLanguage,
                    confidence = null
                )
            )
        }

        if (isFinal) {
            whisperAudioBuffer.clear()
        }
    }

    override fun stopRecognition() {
        if (!recognizing) return
        recognizing = false

        when (currentModelType) {
            "zipformer" -> {
                // Process any remaining audio in the stream
                onlineStream?.let { stream ->
                    onlineRecognizer?.let { recognizer ->
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                        val result = recognizer.getResult(stream)
                        if (result.text.isNotEmpty()) {
                            resultFlow.tryEmit(
                                STTResult(
                                    text = result.text.trim(),
                                    isFinal = true,
                                    language = currentLanguage,
                                    confidence = null
                                )
                            )
                        }
                    }
                }
                onlineStream?.release()
                onlineStream = null
            }
            "whisper" -> {
                // Final decode of accumulated audio
                decodeWhisperBuffer(isFinal = true)
            }
        }
    }

    override fun isRecognizing(): Boolean = recognizing
}
