package com.jetbrains.kmpapp.stt

import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle

/**
 * iOS STT Engine using Sherpa-ONNX via C interop.
 * Uses FP16 models for better quality on modern iPhones (6-8 GB RAM).
 *
 * The config map passed to initialize() must contain:
 *   - "modelBasePath" → absolute path to the directory containing model files
 *                        (defaults to NSBundle.mainBundle.resourcePath)
 *   - "modelType" → "whisper" or "zipformer" (defaults to "whisper")
 *   - "language" → ISO 639-1 language code (defaults to "en")
 *
 * Note: Actual Sherpa-ONNX C bindings require the sherpa-onnx-ios framework
 * to be integrated via CocoaPods or SPM. The C function calls below are
 * structured to match the Sherpa-ONNX C API and will compile once the
 * framework cinterop definition is added.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSSTTEngine : STTEngine {

    override val engineId: String = "ios-stt-sherpa-onnx"

    // C pointers to Sherpa-ONNX recognizer and stream
    private var onlineRecognizer: COpaquePointer? = null   // Zipformer
    private var offlineRecognizer: COpaquePointer? = null   // Whisper
    private var onlineStream: COpaquePointer? = null

    // State
    private var initialized = false
    private var currentModelId: String? = null
    private var currentModelType: String = "whisper"
    private var currentLanguage: String = "en"
    private var recognizing = false
    private var sampleRate: Int = 16000

    // Result flow
    private val resultFlow = MutableSharedFlow<STTResult>(extraBufferCapacity = 64)

    // Accumulated audio buffer for Whisper
    private val whisperAudioBuffer = mutableListOf<Float>()

    // Memory tracking (FP16 models are larger)
    private var estimatedMemory: Long = 120 * 1024 * 1024L

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        if (initialized) release()

        currentModelId = modelId
        val defaultPath = NSBundle.mainBundle.resourcePath ?: ""
        val modelBasePath = config["modelBasePath"] as? String ?: defaultPath
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

    /**
     * Initialize Zipformer (online/streaming) recognizer via Sherpa-ONNX C API.
     *
     * C API equivalent:
     *   SherpaOnnxOnlineRecognizerConfig config = { ... };
     *   const SherpaOnnxOnlineRecognizer* recognizer = SherpaOnnxCreateOnlineRecognizer(&config);
     */
    private fun initializeZipformer(modelBasePath: String) {
        // TODO: Replace with actual C interop calls when sherpa-onnx-ios framework is integrated
        //
        // Expected C interop usage:
        // memScoped {
        //     val config = alloc<SherpaOnnxOnlineRecognizerConfig>()
        //     config.model_config.transducer.encoder = "$modelBasePath/encoder.onnx".cstr.ptr
        //     config.model_config.transducer.decoder = "$modelBasePath/decoder.onnx".cstr.ptr
        //     config.model_config.transducer.joiner = "$modelBasePath/joiner.onnx".cstr.ptr
        //     config.model_config.tokens = "$modelBasePath/tokens.txt".cstr.ptr
        //     config.model_config.model_type = "zipformer2".cstr.ptr
        //     config.feat_config.sample_rate = 16000
        //     config.feat_config.feature_dim = 80
        //     config.enable_endpoint = 1
        //     config.decoding_method = "greedy_search".cstr.ptr
        //     onlineRecognizer = SherpaOnnxCreateOnlineRecognizer(config.ptr)
        // }

        estimatedMemory = 60 * 1024 * 1024L  // Zipformer is smaller
    }

    /**
     * Initialize Whisper (offline) recognizer via Sherpa-ONNX C API.
     *
     * C API equivalent:
     *   SherpaOnnxOfflineRecognizerConfig config = { ... };
     *   const SherpaOnnxOfflineRecognizer* recognizer = SherpaOnnxCreateOfflineRecognizer(&config);
     */
    private fun initializeWhisper(modelBasePath: String, language: String) {
        // TODO: Replace with actual C interop calls when sherpa-onnx-ios framework is integrated
        //
        // Expected C interop usage:
        // memScoped {
        //     val config = alloc<SherpaOnnxOfflineRecognizerConfig>()
        //     config.model_config.whisper.encoder = "$modelBasePath/whisper-encoder.onnx".cstr.ptr
        //     config.model_config.whisper.decoder = "$modelBasePath/whisper-decoder.onnx".cstr.ptr
        //     config.model_config.whisper.language = language.cstr.ptr
        //     config.model_config.whisper.task = "transcribe".cstr.ptr
        //     config.model_config.tokens = "$modelBasePath/whisper-tokens.txt".cstr.ptr
        //     config.model_config.model_type = "whisper".cstr.ptr
        //     config.feat_config.sample_rate = 16000
        //     config.feat_config.feature_dim = 80
        //     config.decoding_method = "greedy_search".cstr.ptr
        //     offlineRecognizer = SherpaOnnxCreateOfflineRecognizer(config.ptr)
        // }

        estimatedMemory = if (currentModelId?.contains("base") == true) {
            150 * 1024 * 1024L  // FP16 base model
        } else {
            75 * 1024 * 1024L   // FP16 tiny model
        }
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        stopRecognition()

        // TODO: Release C resources when framework is integrated
        // onlineRecognizer?.let { SherpaOnnxDestroyOnlineRecognizer(it) }
        // offlineRecognizer?.let { SherpaOnnxDestroyOfflineRecognizer(it) }

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
                // TODO: Create online stream via C API
                // onlineStream = SherpaOnnxCreateOnlineStream(onlineRecognizer)
            }
            "whisper" -> {
                whisperAudioBuffer.clear()
            }
        }

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

        val floatSamples = FloatArray(audioData.size) { audioData[it].toFloat() / 32768.0f }

        // TODO: Feed audio and decode via C API
        // memScoped {
        //     val samplesPtr = floatSamples.toCValues().ptr
        //     SherpaOnnxOnlineStreamAcceptWaveform(stream, sampleRate, samplesPtr, floatSamples.size)
        //
        //     while (SherpaOnnxIsOnlineStreamReady(recognizer, stream) != 0) {
        //         SherpaOnnxDecodeOnlineStream(recognizer, stream)
        //     }
        //
        //     val resultPtr = SherpaOnnxGetOnlineStreamResult(recognizer, stream)
        //     val text = resultPtr?.pointed?.text?.toKString() ?: ""
        //
        //     if (text.isNotEmpty()) {
        //         val isEndpoint = SherpaOnnxOnlineStreamIsEndpoint(recognizer, stream) != 0
        //         resultFlow.tryEmit(STTResult(text = text.trim(), isFinal = isEndpoint, language = currentLanguage))
        //         if (isEndpoint) {
        //             SherpaOnnxOnlineStreamReset(recognizer, stream)
        //         }
        //     }
        //     SherpaOnnxDestroyOnlineStreamResult(resultPtr)
        // }
    }

    private fun processWhisper(audioData: ShortArray) {
        for (sample in audioData) {
            whisperAudioBuffer.add(sample.toFloat() / 32768.0f)
        }

        val bufferDurationSec = whisperAudioBuffer.size.toFloat() / sampleRate
        if (bufferDurationSec >= 3.0f) {
            decodeWhisperBuffer(isFinal = false)
        }
    }

    private fun decodeWhisperBuffer(isFinal: Boolean) {
        if (whisperAudioBuffer.isEmpty()) return
        val recognizer = offlineRecognizer ?: return

        val samples = whisperAudioBuffer.toFloatArray()

        // TODO: Decode via C API when framework is integrated
        // memScoped {
        //     val stream = SherpaOnnxCreateOfflineStream(recognizer)
        //     val samplesPtr = samples.toCValues().ptr
        //     SherpaOnnxAcceptWaveformOffline(stream, sampleRate, samplesPtr, samples.size)
        //     SherpaOnnxDecodeOfflineStream(recognizer, stream)
        //
        //     val resultPtr = SherpaOnnxGetOfflineStreamResult(stream)
        //     val text = resultPtr?.pointed?.text?.toKString() ?: ""
        //
        //     if (text.isNotEmpty()) {
        //         resultFlow.tryEmit(STTResult(text = text.trim(), isFinal = isFinal, language = currentLanguage))
        //     }
        //
        //     SherpaOnnxDestroyOfflineStreamResult(resultPtr)
        //     SherpaOnnxDestroyOfflineStream(stream)
        // }

        if (isFinal) {
            whisperAudioBuffer.clear()
        }
    }

    override fun stopRecognition() {
        if (!recognizing) return
        recognizing = false

        when (currentModelType) {
            "zipformer" -> {
                // TODO: Final decode and release stream via C API
                // onlineStream?.let { stream ->
                //     onlineRecognizer?.let { recognizer ->
                //         while (SherpaOnnxIsOnlineStreamReady(recognizer, stream) != 0) {
                //             SherpaOnnxDecodeOnlineStream(recognizer, stream)
                //         }
                //         val resultPtr = SherpaOnnxGetOnlineStreamResult(recognizer, stream)
                //         val text = resultPtr?.pointed?.text?.toKString() ?: ""
                //         if (text.isNotEmpty()) {
                //             resultFlow.tryEmit(STTResult(text = text.trim(), isFinal = true, language = currentLanguage))
                //         }
                //         SherpaOnnxDestroyOnlineStreamResult(resultPtr)
                //     }
                //     SherpaOnnxDestroyOnlineStream(it)
                // }
                onlineStream = null
            }
            "whisper" -> {
                decodeWhisperBuffer(isFinal = true)
            }
        }
    }

    override fun isRecognizing(): Boolean = recognizing
}
