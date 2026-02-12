# Otomatik Dil Algılama ve Konuşma Tanıma

Proje artık **otomatik dil algılama** destekliyor! Türkçe mi, İngilizce mi konuşuyorsunuz otomatik algılar ve o dile göre konuşmalarınızı metne çevirir.

## 🎯 Özellikler

- ✅ **Otomatik dil algılama** - Türkçe ve İngilizce
- ✅ **Desteklenmeyen dil uyarısı** - Diğer diller için bildirim
- ✅ **Güven skoru** - Dil algılama güvenilirliği
- ✅ **Multi-language recognizer** - Tek bir interface ile her iki dil

---

## 📦 Gerekli Modeller

### 🇺🇸 İngilizce Model (Önerilir)

**En yeni ve önerilen:**
```bash
# Streaming Zipformer - En güncel (2023-06-26)
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
```

**Alternatifler:**
- `sherpa-onnx-streaming-zipformer-en-2023-06-21` - Daha büyük veri seti (LibriSpeech + GigaSpeech)
- `sherpa-onnx-streaming-zipformer-en-2023-02-21` - Eski versiyon

**HuggingFace (Tavsiye edilen):**
```bash
# Model sayfası
https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26
```

### 🇹🇷 Türkçe Model

⚠️ **ÖNEMLİ:** Sherpa-ONNX resmi dokümantasyonunda Türkçe streaming model bulunmuyor.

**Seçenekler:**

1. **Kendi modelinizi eğitin:**
   - [Common Voice Türkçe](https://commonvoice.mozilla.org/tr) veri seti
   - [icefall training](https://github.com/k2-fsa/icefall) ile eğitim
   - Sherpa-ONNX'e dönüştürme

2. **Whisper modellerini kullanın:**
   - Türkçe desteği var
   - Daha ağır ama daha iyi doğruluk
   - [sherpa-onnx-whisper](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/index.html)

3. **Sadece İngilizce ile başlayın:**
   ```kotlin
   // Tek dil modu
   multiLangRecognizer.supportedLanguages = listOf(
       RecognitionLanguage.ENGLISH
   )
   ```

---

## 📱 Model Yerleştirme

### Android

```
composeApp/src/androidMain/assets/
├── sherpa-onnx/
│   ├── turkish/
│   │   ├── encoder.onnx
│   │   ├── decoder.onnx
│   │   ├── joiner.onnx
│   │   └── tokens.txt
│   └── english/
│       ├── encoder.onnx
│       ├── decoder.onnx
│       ├── joiner.onnx
│       └── tokens.txt
```

### iOS

```
iosApp/iosApp/Resources/
└── sherpa-onnx/
    ├── turkish/
    │   ├── encoder.onnx
    │   ├── decoder.onnx
    │   ├── joiner.onnx
    │   └── tokens.txt
    └── english/
        ├── encoder.onnx
        ├── decoder.onnx
        ├── joiner.onnx
        └── tokens.txt
```

**Xcode'da:**
1. Her iki klasörü de projeye ekleyin
2. "Create folder references" seçili olsun
3. Target membership işaretli olsun

---

## 🚀 Kullanım

### Temel Kullanım

```kotlin
import com.jetbrains.kmpapp.speech.*
import org.koin.core.component.inject

class MyViewModel : ViewModel(), KoinComponent {
    private val multiLangRecognizer: MultiLanguageSpeechRecognizer by inject()

    suspend fun startRecognition(initialAudio: ShortArray) {
        // 1. Otomatik dil algılama ile başlat
        val success = multiLangRecognizer.initialize(
            initialAudioSample = initialAudio,
            config = RecognizerConfig(sampleRate = 16000)
        )

        if (!success) {
            println("❌ Desteklenmeyen dil veya başlatma hatası")
            return
        }

        // 2. Algılanan dili kontrol et
        val detectedLang = multiLangRecognizer.detectedLanguage.value
        println("🌍 Algılanan dil: ${detectedLang?.displayName}")

        // 3. Tanımayı başlat
        multiLangRecognizer.startRecognition().collect { result ->
            when (result) {
                is RecognitionResult.Partial -> {
                    println("⏳ Dinleniyor: ${result.text}")
                }
                is RecognitionResult.Final -> {
                    println("✅ Sonuç: ${result.text}")
                }
                is RecognitionResult.Error -> {
                    println("❌ Hata: ${result.message}")
                }
            }
        }
    }

    suspend fun processAudio(audioChunk: ShortArray) {
        multiLangRecognizer.processAudioData(audioChunk)
    }

    fun stop() {
        multiLangRecognizer.stopRecognition()
    }
}
```

### Audio Recorder ile Kullanım

```kotlin
import com.jetbrains.kmpapp.audio.createAudioRecorder

val audioRecorder = createAudioRecorder()
val multiLangRecognizer: MultiLanguageSpeechRecognizer by inject()

var isFirstChunk = true
val audioBuffer = mutableListOf<Short>()

// Kayıt başlat
audioRecorder.start { audioData ->
    scope.launch {
        if (isFirstChunk) {
            // İlk chunk ile dil algılama
            audioBuffer.addAll(audioData.toList())
            
            if (audioBuffer.size >= 16000 * 2) { // 2 saniye audio
                val initialSample = audioBuffer.toShortArray()
                
                val success = multiLangRecognizer.initialize(
                    initialAudioSample = initialSample
                )
                
                if (success) {
                    val lang = multiLangRecognizer.getCurrentLanguage()
                    println("🌍 Dil algılandı: ${lang?.displayName}")
                    
                    // Tanımayı başlat
                    scope.launch {
                        multiLangRecognizer.startRecognition().collect { result ->
                            when (result) {
                                is RecognitionResult.Final -> {
                                    println("✅ ${result.text}")
                                }
                                else -> {}
                            }
                        }
                    }
                    
                    isFirstChunk = false
                } else {
                    println("❌ Desteklenmeyen dil!")
                    audioRecorder.stop()
                }
            }
        } else {
            // Devam eden audio'yu işle
            multiLangRecognizer.processAudioData(audioData)
        }
    }
}
```

### Sadece Dil Algılama

```kotlin
val languageDetector: LanguageDetector by inject()

suspend fun detectLanguageOnly(audioSample: ShortArray) {
    val result = languageDetector.detectLanguage(
        audioData = audioSample,
        supportedLanguages = listOf(
            RecognitionLanguage.TURKISH,
            RecognitionLanguage.ENGLISH
        )
    )

    when (result) {
        is LanguageDetectionResult.Detected -> {
            println("🌍 Dil: ${result.language.displayName}")
            println("📊 Güven: ${(result.confidence * 100).toInt()}%")
        }
        is LanguageDetectionResult.Unsupported -> {
            println("❌ Desteklenmeyen dil")
        }
        is LanguageDetectionResult.Error -> {
            println("❌ Hata: ${result.message}")
        }
    }
}
```

### Dil Değiştirme

```kotlin
// Desteklenen dilleri sınırla
multiLangRecognizer.supportedLanguages = listOf(
    RecognitionLanguage.TURKISH,
    RecognitionLanguage.ENGLISH
)

// Sadece Türkçe
multiLangRecognizer.supportedLanguages = listOf(
    RecognitionLanguage.TURKISH
)
```

---

## 🔧 Nasıl Çalışır?

1. **İlk 2-3 saniye audio** toplanır
2. **Her iki modelle** kısa tanıma denemesi yapılır
3. **En yüksek skoru** alan dil seçilir
4. **O dil ile** tanıma devam eder

### Güven Skoru

- `> 0.7` → Çok güvenilir
- `0.5 - 0.7` → Güvenilir
- `0.3 - 0.5` → Şüpheli (yine de seçilir)
- `< 0.3` → Desteklenmeyen dil

---

## 💡 İpuçları

1. **Başlangıç Süresi**: İlk 2-3 saniye konuşma yeterli dil algılama için

2. **Sessiz Ortam**: Dil algılama için temiz audio önemli

3. **Karışık Konuşma**: Eğer bir cümlede iki dil varsa, ilk konuşulan dil algılanır

4. **Performans**: Dil algılama ~1 saniye sürer, sonra normal tanıma başlar

5. **Model Boyutu**: Her iki model de ~300 MB. Toplamda ~600 MB gerekir

---

## ❓ Sık Sorulan Sorular

### Kaç dil destekleniyor?
Şu anda sadece **Türkçe** ve **İngilizce**. Daha fazla dil için model eklenebilir.

### Başka dil konuşursam ne olur?
`LanguageDetectionResult.Unsupported` döner ve "Desteklenmeyen dil" mesajı gösterilir.

### Dil algılama ne kadar sürer?
İlk 2-3 saniye audio ile ~1 saniye

### İki dili karıştırırsam?
İlk konuşulan dil algılanır. Code-switching (dil değiştirme) desteklenmez.

### Manuel dil seçimi yapabilir miyim?
Evet! Normal `SpeechRecognizer` kullanarak:

```kotlin
val speechRecognizer: SpeechRecognizer by inject()

val config = RecognizerConfig(
    language = RecognitionLanguage.TURKISH // Manuel seçim
)
speechRecognizer.initialize(config)
```

---

## 🎬 Örnek Senaryo

```kotlin
// 1. Kullanıcı konuşma butonuna basar
button.onClick {
    audioRecorder.start()
}

// 2. İlk 2 saniye toplanır, dil algılanır
// Output: "🌍 Dil algılandı: Türkçe"

// 3. Kullanıcı konuşmaya devam eder
// Output: "⏳ Dinleniyor: Merhaba"
// Output: "⏳ Dinleniyor: Merhaba nasıl"
// Output: "✅ Sonuç: Merhaba nasılsın?"

// 4. Kullanıcı durduğunda
button.onRelease {
    audioRecorder.stop()
    multiLangRecognizer.stopRecognition()
}
```

---

## 🔗 API Referansı

### MultiLanguageSpeechRecognizer

```kotlin
class MultiLanguageSpeechRecognizer {
    // Algılanan dil (StateFlow)
    val detectedLanguage: StateFlow<RecognitionLanguage?>
    
    // Desteklenen diller
    var supportedLanguages: List<RecognitionLanguage>
    
    // Başlat
    suspend fun initialize(
        initialAudioSample: ShortArray,
        config: RecognizerConfig
    ): Boolean
    
    // Tanımayı başlat
    fun startRecognition(): Flow<RecognitionResult>
    
    // Audio işle
    suspend fun processAudioData(audioData: ShortArray)
    
    // Durdur
    fun stopRecognition()
    
    // Temizle
    fun release()
    
    // Şu anki dil
    fun getCurrentLanguage(): RecognitionLanguage?
}
```

### LanguageDetector

```kotlin
interface LanguageDetector {
    // Hızlı algılama
    suspend fun detectLanguage(
        audioData: ShortArray,
        supportedLanguages: List<RecognitionLanguage>
    ): LanguageDetectionResult
    
    // Daha uzun audio ile algılama
    suspend fun detectLanguageFromStream(
        audioData: ShortArray,
        durationSeconds: Int,
        supportedLanguages: List<RecognitionLanguage>
    ): LanguageDetectionResult
}
```

---

## ✅ Kurulum Tamamlandı

Artık otomatik dil algılama ile konuşma tanıma yapabilirsiniz! 🎉

**Türkçe konuşun** → Türkçe metin  
**İngilizce konuşun** → İngilizce metin  
**Başka dil** → "Desteklenmeyen dil" mesajı
