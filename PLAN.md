# KMP AiPin - Phase 1: On-Device STT → Translation → TTS Pipeline

## Genel Mimari Bakış

```
┌─────────────────────────────────────────────────────────┐
│                    PipelineManager                       │
│  (Orchestrates the full STT → Translation → TTS flow)   │
└─────────┬──────────────────┬──────────────────┬─────────┘
          │                  │                  │
          ▼                  ▼                  ▼
   ┌─────────────┐   ┌──────────────┐   ┌─────────────┐
   │  STTEngine  │   │  Translator  │   │  TTSEngine  │
   │ (interface)  │   │ (interface)  │   │ (interface)  │
   └──────┬──────┘   └──────┬───────┘   └──────┬──────┘
          │                  │                  │
          ▼                  ▼                  ▼
   ┌─────────────┐   ┌──────────────┐   ┌─────────────┐
   │SherpaOnnx   │   │ OnnxTranslator│  │SherpaOnnx   │
   │STTEngine    │   │ (NLLB/OPUS)  │   │TTSEngine    │
   │             │   │              │   │(VITS/Piper) │
   └─────────────┘   └──────────────┘   └─────────────┘
```

Tüm engine'ler ortak `Engine` interface'inden türer. Her engine:
- Model bağımsızdır (model ID ile çalışır)
- ModelRegistry üzerinden model indirir
- Platform-specific (`expect/actual`) implementasyonlara sahiptir

---

## Modül Yapısı (Yeni + Mevcut)

```
shared/src/commonMain/kotlin/com/jetbrains/kmpapp/
├── engine/                          ← YENİ: Generic engine framework
│   ├── Engine.kt                    # Base engine interface
│   ├── EngineConfig.kt              # Generic configuration
│   └── EngineFactory.kt             # Engine creation factory
│
├── stt/                             ← YENİ (speech/ yerine refactor)
│   ├── STTEngine.kt                 # STT-specific interface
│   ├── STTResult.kt                 # Recognition results
│   └── STTConfig.kt                 # STT configuration
│
├── translation/                     ← YENİ
│   ├── TranslationEngine.kt        # Translation interface
│   ├── TranslationResult.kt        # Translation results
│   └── TranslationConfig.kt        # Translation configuration
│
├── tts/                             ← YENİ
│   ├── TTSEngine.kt                # TTS interface
│   ├── TTSResult.kt                # Audio output results
│   └── TTSConfig.kt                # TTS configuration
│
├── pipeline/                        ← YENİ
│   ├── TranslationPipeline.kt      # STT→Translation→TTS orchestrator
│   ├── PipelineConfig.kt           # Pipeline configuration
│   └── PipelineViewModel.kt        # UI state management
│
├── model/                           ← MEVCUT + GENİŞLETİLECEK
│   ├── ModelRegistry.kt             # YENİ: Merkezi model kataloğu
│   ├── ModelType.kt                 # YENİ: STT, TTS, TRANSLATION enum
│   ├── ModelManager.kt              # Mevcut (güncellenir)
│   ├── DefaultModelManager.kt       # Mevcut (güncellenir)
│   └── ModelManagerViewModel.kt     # Mevcut (güncellenir)
│
├── audio/                           ← MEVCUT
│   ├── AudioRecorder.kt            # Mevcut
│   └── AudioPlayer.kt              # YENİ: PCM/WAV playback
│
└── di/
    └── Koin.kt                      # Güncellenir (tüm engine'ler eklenir)
```

---

## Adım Adım Uygulama Planı

### ADIM 1: Generic Engine Framework (`engine/`)

**Amaç:** Tüm engine'lerin (STT, Translation, TTS) uyması gereken ortak sözleşme.

```kotlin
// Engine.kt
interface Engine {
    val engineId: String
    val supportedModels: List<String>  // Model ID'leri

    suspend fun initialize(modelId: String, config: Map<String, Any> = emptyMap())
    suspend fun isReady(): Boolean
    suspend fun release()
}

// EngineConfig.kt
data class EngineModelConfig(
    val modelId: String,
    val modelType: ModelType,       // STT, TTS, TRANSLATION
    val language: String,           // ISO 639-1 (tr, en, de...)
    val targetLanguage: String?,    // Translation için hedef dil
    val backendType: String,        // "sherpa-onnx", "onnx-runtime", "custom"
    val modelFiles: Map<String, String>,  // relative path'ler
    val downloadUrl: String,
    val sizeBytes: Long,
    val checksum: String?
)
```

**Neden Generic?** İleride online API backend'i (Google Translate, DeepL, OpenAI Whisper API) eklerken aynı interface'i kullanabiliriz. `backendType` field'ı hangi implementasyonun kullanılacağını belirler.

---

### ADIM 2: Model Registry (`model/ModelRegistry.kt`)

**Amaç:** Tüm indirilebilir modellerin merkezi kataloğu. İlk kurulumda kullanıcıya hangi modelleri indirmek istediğini sorar.

```kotlin
// ModelType.kt
enum class ModelType { STT, TTS, TRANSLATION }

// ModelRegistry.kt
interface ModelRegistry {
    fun getAvailableModels(type: ModelType? = null): List<EngineModelConfig>
    fun getAvailableModels(type: ModelType, language: String): List<EngineModelConfig>
    fun getModelConfig(modelId: String): EngineModelConfig?
    fun getRequiredModelsForPipeline(
        sourceLanguage: String,
        targetLanguage: String
    ): List<EngineModelConfig>  // STT + Translation + TTS otomatik seçim
}
```

**İlk Desteklenecek Modeller:**

| Model ID | Tip | Dil | Boyut | Backend |
|----------|-----|-----|-------|---------|
| `sherpa-stt-whisper-tiny-tr` | STT | tr | ~40 MB | sherpa-onnx |
| `sherpa-stt-whisper-base-en` | STT | en | ~75 MB | sherpa-onnx |
| `sherpa-stt-zipformer-en` | STT | en | ~15 MB | sherpa-onnx |
| `sherpa-tts-vits-tr` | TTS | tr | ~30 MB | sherpa-onnx |
| `sherpa-tts-piper-en` | TTS | en | ~20 MB | sherpa-onnx |
| `onnx-translate-nllb-tr-en` | TRANSLATION | tr→en | ~300 MB | onnx-runtime |
| `onnx-translate-nllb-en-tr` | TRANSLATION | en→tr | ~300 MB | onnx-runtime |

> **Not:** Translation modeli en büyük parça. NLLB-200 (distilled-600M) veya OPUS-MT modelleri kullanılabilir. İlk sürümde CTranslate2/ONNX Runtime ile Helsinki-NLP OPUS-MT modelleri tercih edilebilir (~50-150MB per pair).

---

### ADIM 3: STT Engine Refactörü (`stt/`)

**Amaç:** Mevcut `SpeechRecognizer` → yeni `STTEngine` interface'ine dönüştürmek.

```kotlin
// STTEngine.kt
interface STTEngine : Engine {
    fun startRecognition(config: STTConfig): Flow<STTResult>
    fun processAudioData(audioData: ShortArray)
    fun stopRecognition()
    fun isRecognizing(): Boolean
}

// STTResult.kt
data class STTResult(
    val text: String,
    val isFinal: Boolean,
    val language: String?,
    val confidence: Float?,
    val timestamp: Long = currentTimeMillis()
)

// STTConfig.kt
data class STTConfig(
    val language: String,
    val sampleRate: Int = 16000,
    val enablePunctuation: Boolean = true,
    val modelType: String = "whisper"  // "whisper" veya "zipformer"
)
```

**Platform implementasyonları:**
- `androidMain/stt/SherpaOnnxSTTEngine.kt` → Mevcut Android kodundan refactor
- `iosMain/stt/SherpaOnnxSTTEngine.kt` → iOS Sherpa-ONNX binding'leri

---

### ADIM 4: Translation Engine (`translation/`)

**Amaç:** On-device çeviri. İlk aşamada ONNX Runtime ile çalışan translation modelleri.

```kotlin
// TranslationEngine.kt
interface TranslationEngine : Engine {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult

    fun getSupportedLanguagePairs(): List<Pair<String, String>>
}

// TranslationResult.kt
data class TranslationResult(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val confidence: Float?
)
```

**On-device translation stratejisi:**

1. **Tercih edilen:** Helsinki-NLP OPUS-MT modelleri (ONNX formatında)
   - Model başına ~50-150MB
   - Hızlı inference
   - Dil çifti bazlı modeller (tr→en, en→tr, vb.)
   - ONNX Runtime ile çalıştırılır

2. **Alternatif:** Facebook NLLB-200 (distilled-600M)
   - Tek model, 200+ dil desteği
   - ~600MB (quantized ~300MB)
   - Daha yavaş ama çok dilli

3. **Whisper Translation modu** (sadece X→EN yönünde)
   - Ek model gerektirmez (mevcut Whisper modeli kullanılır)
   - Sadece İngilizce'ye çeviri yapabilir, tersine çalışmaz

**Önerilen ilk implementasyon:**
- Whisper'ın translate modunu kullanarak X→EN çevirisini hemen destekle
- OPUS-MT ile EN→X çevirisini ekle
- Böylece çift yönlü çalışır: TR→EN (Whisper), EN→TR (OPUS-MT)

**Platform implementasyonları:**
- `androidMain/translation/OnnxTranslationEngine.kt` → ONNX Runtime Android
- `iosMain/translation/OnnxTranslationEngine.kt` → ONNX Runtime iOS
- `commonMain/translation/WhisperTranslationEngine.kt` → Whisper translate modu (STTEngine üzerine wrapper)

---

### ADIM 5: TTS Engine (`tts/`)

**Amaç:** Sherpa-ONNX TTS modelleri ile on-device metin okuma.

```kotlin
// TTSEngine.kt
interface TTSEngine : Engine {
    suspend fun synthesize(text: String, config: TTSConfig): TTSResult
    fun stop()
    fun isSpeaking(): Boolean
}

// TTSResult.kt
data class TTSResult(
    val audioData: FloatArray,    // PCM float samples
    val sampleRate: Int,
    val durationMs: Long
)

// TTSConfig.kt
data class TTSConfig(
    val language: String,
    val speed: Float = 1.0f,
    val speakerId: Int = 0,       // Multi-speaker modellerde
    val sampleRate: Int = 22050   // TTS genelde 22050 Hz
)
```

**Sherpa-ONNX TTS modelleri:**
- **VITS** modelleri: Hafif, hızlı, iyi kalite (Turkish: ~30MB, English: ~30MB)
- **Piper** modelleri: Daha doğal ses, biraz daha büyük (~20-50MB)
- **Kokoro** modelleri: En doğal ses kalitesi

**Platform implementasyonları:**
- `androidMain/tts/SherpaOnnxTTSEngine.kt` → Android Sherpa-ONNX TTS
- `iosMain/tts/SherpaOnnxTTSEngine.kt` → iOS Sherpa-ONNX TTS

---

### ADIM 6: Audio Player (`audio/AudioPlayer.kt`)

**Amaç:** TTS'den gelen PCM verisini hoparlörden çalmak.

```kotlin
// AudioPlayer.kt
interface AudioPlayer {
    fun play(audioData: FloatArray, sampleRate: Int)
    fun stop()
    fun isPlaying(): Boolean
    fun setVolume(volume: Float)
}
```

**Platform implementasyonları:**
- `androidMain/audio/AndroidAudioPlayer.kt` → Android AudioTrack API
- `iosMain/audio/IOSAudioPlayer.kt` → iOS AVAudioPlayer

---

### ADIM 7: Translation Pipeline (`pipeline/`)

**Amaç:** STT + Translation + TTS akışını orkestra eden yapı.

```kotlin
// TranslationPipeline.kt
class TranslationPipeline(
    private val sttEngine: STTEngine,
    private val translationEngine: TranslationEngine,
    private val ttsEngine: TTSEngine,
    private val audioPlayer: AudioPlayer
) {
    // Pipeline durumu
    val state: StateFlow<PipelineState>

    // Pipeline başlat
    suspend fun start(config: PipelineConfig)

    // Pipeline durdur
    fun stop()

    // Tek seferlik çeviri (metin girişi)
    suspend fun translateText(
        text: String,
        from: String,
        to: String,
        speakResult: Boolean = true
    ): TranslationResult
}

// PipelineConfig.kt
data class PipelineConfig(
    val sourceLanguage: String,          // "tr"
    val targetLanguage: String,          // "en"
    val autoDetectLanguage: Boolean = false,
    val speakTranslation: Boolean = true, // TTS ile oku
    val continuousMode: Boolean = false   // Sürekli dinleme
)

// PipelineState.kt
sealed class PipelineState {
    object Idle : PipelineState()
    object Listening : PipelineState()           // STT dinliyor
    data class Recognized(val text: String, val lang: String) : PipelineState()
    object Translating : PipelineState()          // Çeviri yapılıyor
    data class Translated(val result: TranslationResult) : PipelineState()
    object Speaking : PipelineState()             // TTS konuşuyor
    data class Error(val message: String) : PipelineState()
}
```

**Akış:**
```
1. Kullanıcı mikrofon butonuna basar → start()
2. STTEngine dinlemeye başlar → state = Listening
3. Konuşma tanınır → state = Recognized("Merhaba dünya", "tr")
4. TranslationEngine çevirir → state = Translated("Hello world")
5. TTSEngine sentezler → state = Speaking
6. AudioPlayer çalar → state = Idle (veya Listening eğer continuous)
```

---

### ADIM 8: First-Run Setup & Model Download Screen

**Amaç:** İlk kurulumda kullanıcıya dil seçimi ve model indirme ekranı göstermek.

```kotlin
// SetupViewModel.kt
class SetupViewModel(
    private val modelRegistry: ModelRegistry,
    private val modelManager: ModelManager
) : ViewModel() {

    val uiState: StateFlow<SetupUiState>

    fun selectLanguagePair(source: String, target: String)
    fun startDownload()
    fun skipSetup()  // Sonra indir seçeneği
}

data class SetupUiState(
    val availableLanguages: List<LanguageOption>,
    val selectedSource: String?,
    val selectedTarget: String?,
    val requiredModels: List<ModelDownloadItem>,  // Seçilen dil çifti için
    val totalDownloadSize: Long,
    val downloadProgress: Map<String, Float>,     // model ID → progress
    val isDownloading: Boolean,
    val isSetupComplete: Boolean
)
```

**UI Akışı:**
```
┌──────────────────────────┐
│   🌍 Dil Seçimi           │
│                          │
│   Kaynak: [Türkçe ▼]    │
│   Hedef:  [English ▼]   │
│                          │
│   📦 İndirilecek modeller:│
│   ☐ STT Whisper TR (40MB)│
│   ☐ Translation (150MB)  │
│   ☐ TTS English (30MB)   │
│   ─────────────────       │
│   Toplam: ~220MB          │
│                          │
│   [İndir ve Başla]       │
│   [Sonra İndir]          │
└──────────────────────────┘
```

---

### ADIM 9: Koin DI Güncellemesi

```kotlin
// Koin.kt güncelleme
val engineModule = module {
    // STT
    single<STTEngine> { createSTTEngine() }          // expect/actual

    // Translation
    single<TranslationEngine> { createTranslationEngine() }  // expect/actual

    // TTS
    single<TTSEngine> { createTTSEngine() }          // expect/actual

    // Audio
    single<AudioPlayer> { createAudioPlayer() }      // expect/actual

    // Pipeline
    single { TranslationPipeline(get(), get(), get(), get()) }

    // Model
    single<ModelRegistry> { DefaultModelRegistry() }

    // ViewModels
    viewModel { PipelineViewModel(get()) }
    viewModel { SetupViewModel(get(), get()) }
}
```

---

### ADIM 10: UI Ekranları (Compose)

1. **SetupScreen.kt** - İlk kurulum / model indirme
2. **TranslationScreen.kt** - Ana çeviri ekranı
   - Mikrofon butonu (basılı tut = dinle)
   - Kaynak/hedef dil seçimi
   - Tanınan metin gösterimi
   - Çevrilmiş metin gösterimi
   - TTS playback kontrolü
3. **SettingsScreen.kt** - Model yönetimi, dil ekleme/çıkarma

---

## Uygulama Sırası (Dependency Order)

```
Aşama 1: Foundation (Engine Framework + Model Registry)
  ├─ 1.1 Engine interface'leri (engine/)
  ├─ 1.2 ModelType enum + ModelRegistry
  └─ 1.3 Mevcut ModelManager güncelleme

Aşama 2: STT Refactoring
  ├─ 2.1 STTEngine interface + config/result types
  ├─ 2.2 Android SherpaOnnxSTTEngine (mevcut koddan refactor)
  └─ 2.3 iOS SherpaOnnxSTTEngine (placeholder + yapı)

Aşama 3: TTS Implementation
  ├─ 3.1 TTSEngine interface + config/result types
  ├─ 3.2 AudioPlayer interface + platform impl
  ├─ 3.3 Android SherpaOnnxTTSEngine
  └─ 3.4 iOS SherpaOnnxTTSEngine (placeholder + yapı)

Aşama 4: Translation Implementation
  ├─ 4.1 TranslationEngine interface + config/result types
  ├─ 4.2 WhisperTranslationEngine (X→EN, STT wrapper)
  ├─ 4.3 OnnxTranslationEngine (OPUS-MT, her iki yön)
  └─ 4.4 Platform-specific ONNX Runtime entegrasyonu

Aşama 5: Pipeline Orchestration
  ├─ 5.1 TranslationPipeline (akış yönetimi)
  ├─ 5.2 PipelineViewModel (UI state)
  └─ 5.3 Koin DI güncellemesi

Aşama 6: UI
  ├─ 6.1 SetupScreen (ilk kurulum + model indirme)
  ├─ 6.2 TranslationScreen (ana çeviri ekranı)
  └─ 6.3 Navigation güncellemesi
```

---

## Gelecek Fazlar İçin Hazırlık

Bu mimari aşağıdaki genişlemelere hazırdır:

| Gelecek Özellik | Nasıl Eklenir |
|-----------------|---------------|
| **Online Translation** | `OnlineTranslationEngine` impl → aynı interface |
| **P2P Translation** | Pipeline'a WebRTC/Bluetooth layer ekle |
| **Online RAG** | `RAGEngine` interface + embedding model + vector DB |
| **Streaming Translation** | STTEngine partial results → incremental translate |
| **Daha fazla dil** | ModelRegistry'ye yeni model tanımları ekle |

---

## Teknik Kararlar / Tartışma Noktaları

### 1. Translation Backend Seçimi
- **Seçenek A:** OPUS-MT (Helsinki-NLP) → Küçük (50-150MB/pair), hızlı, dil çifti bazlı
- **Seçenek B:** NLLB-200 distilled → Büyük (~300MB), tek model 200+ dil
- **Seçenek C:** Whisper translate → Sadece X→EN yönü, ek model yok
- **Öneri:** Başlangıçta A + C hibrit yaklaşım

### 2. ONNX Runtime Entegrasyonu
- Android: `onnxruntime-android` Maven dependency
- iOS: `onnxruntime-objc` CocoaPods/SPM
- Her iki platformda da GPU/NPU acceleration mümkün

### 3. iOS Sherpa-ONNX
- Mevcut iOS implementasyonu placeholder
- SPM veya CocoaPods ile sherpa-onnx-ios framework eklenecek
- C-binding'ler Kotlin/Native interop ile çağrılacak

### 4. Mevcut Kodu Koruma vs Refactor
- Mevcut `speech/` altındaki kodlar **refactor** edilecek (silmeyip yeni yapıya taşıma)
- `SpeechRecognizer` → `STTEngine` adapter pattern ile geçiş
- Mevcut ekranlar (AudioRecordScreen vb.) çalışmaya devam eder

### 5. Model Boyutu Stratejisi
- İlk indirme: Minimum set (tiny modeller)
- Opsiyonel: Daha büyük/kaliteli modeller sonra indirilebilir
- WiFi zorunluluğu: 100MB+ modeller için uyarı
