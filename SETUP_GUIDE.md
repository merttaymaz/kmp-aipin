# KMP AI Pin - Sherpa-ONNX Setup Guide

Bu proje Sherpa-ONNX kullanarak **on-device konuşma tanıma** özelliğine sahip bir KMP (Kotlin Multiplatform) uygulamasıdır.

## 🎯 Özellikler

- ✅ **Sherpa-ONNX** - On-device konuşma tanıma (internet gerektirmez)
- ✅ **Çoklu dil desteği** - Türkçe, İngilizce ve daha fazlası
- ✅ **Real-time tanıma** - Anında sonuçlar
- ✅ **Çapraz platform** - Android ve iOS
- ✅ **Koin DI** - Dependency injection ile kolay kullanım

---

## 🎤 Sherpa-ONNX Nedir?

Sherpa-ONNX, Next-gen Kaldi projesi tarafından geliştirilen, **internet bağlantısı gerektirmeyen** on-device konuşma tanıma kütüphanesidir.

### Avantajları:
- 🚫 **İnternet gerektirmez** - Tamamen offline çalışır
- 🔒 **Gizlilik** - Ses verileri cihazda kalır
- ⚡ **Hızlı** - Düşük gecikme süresi
- 💪 **Güçlü** - Yüksek doğruluk oranı
- 🌍 **Çoklu dil** - 50+ dil desteği

---

## 📦 Kurulum

### 1. Sherpa-ONNX Model Dosyaları

Konuşma tanıma için ONNX model dosyalarına ihtiyacınız var.

#### Model İndirme

**📚 Kaynaklar:**
- [Sherpa-ONNX Releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- [Pre-trained Models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html)
- [HuggingFace Models](https://huggingface.co/csukuangfj)

**🇺🇸 İngilizce Model (Önerilir):**

```bash
# En güncel streaming model (2023-06-26)
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
```

**Alternatifler:**
- `sherpa-onnx-streaming-zipformer-en-2023-06-21` - GigaSpeech + LibriSpeech
- `sherpa-onnx-streaming-zipformer-en-2023-02-21` - Eski versiyon

**🇹🇷 Türkçe Model:**

⚠️ **DİKKAT:** Resmi Sherpa-ONNX dokümantasyonunda Türkçe streaming model yok.

**Alternatif çözümler:**
1. **Whisper modeli kullanın** (Türkçe destekli, daha ağır)
2. **Kendi modelinizi eğitin** ([Common Voice TR](https://commonvoice.mozilla.org/tr) + [icefall](https://github.com/k2-fsa/icefall))
3. **Sadece İngilizce kullanın**

**Model içeriği:**
- `encoder.onnx` veya `encoder-epoch-99-avg-1.onnx`
- `decoder.onnx` veya `decoder-epoch-99-avg-1.onnx`
- `joiner.onnx` veya `joiner-epoch-99-avg-1.onnx`
- `tokens.txt`

---

## 📱 Android Kurulumu

### 1. Model Dosyalarını Projeye Ekleyin

Assets klasörü oluşturun ve model dosyalarını kopyalayın:

```
kmp-aipin/
└── composeApp/
    └── src/
        └── androidMain/
            └── assets/
                └── sherpa-onnx/
                    ├── encoder.onnx
                    ├── decoder.onnx
                    ├── joiner.onnx
                    └── tokens.txt
```

### 2. build.gradle.kts Güncelleyin

`composeApp/build.gradle.kts` dosyasına ekleyin:

```kotlin
android {
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/androidMain/assets")
        }
    }
}
```

### 3. Gradle Sync

```bash
./gradlew --refresh-dependencies
```

---

## 🍎 iOS Kurulumu

### 1. Model Dosyalarını Projeye Ekleyin

```
kmp-aipin/
└── iosApp/
    └── iosApp/
        └── Resources/
            └── sherpa-onnx/
                ├── encoder.onnx
                ├── decoder.onnx
                ├── joiner.onnx
                └── tokens.txt
```

### 2. Xcode'da Bundle'a Ekleyin

1. `iosApp.xcodeproj` dosyasını Xcode ile açın
2. **iosApp** klasörüne sağ tıklayın
3. **New Group** > "Resources" oluşturun
4. **Resources** klasörüne sağ tıklayın > **Add Files to "iosApp"**
5. Model dosyalarını seçin
6. **Options** kısmında:
   - ✅ "Copy items if needed"
   - ✅ "Add to targets: iosApp"
   - ✅ "Create folder references" (klasör yapısını korumak için)
7. **Add** butonuna tıklayın

### 3. CocoaPods (Opsiyonel)

iOS için Sherpa-ONNX native kütüphanesi gerekiyorsa:

`Podfile` oluşturun:

```ruby
platform :ios, '15.0'

target 'iosApp' do
  use_frameworks!

  # Sherpa-ONNX için gerekirse
  pod 'sherpa-onnx', '~> 1.10.30'
end
```

Yükleyin:

```bash
cd iosApp
pod install
```

---

## 🚀 Kullanım

### Temel Kullanım

```kotlin
import com.jetbrains.kmpapp.speech.*
import org.koin.core.component.inject

class MyViewModel : ViewModel(), KoinComponent {
    private val speechRecognizer: SpeechRecognizer by inject()

    suspend fun startRecognition() {
        // Konfigürasyon
        val config = RecognizerConfig(
            language = RecognitionLanguage.TURKISH,
            sampleRate = 16000,
            enablePunctuation = true
        )

        // Initialize
        val success = speechRecognizer.initialize(config)
        if (!success) {
            println("Recognizer başlatılamadı")
            return
        }

        // Tanımayı başlat
        speechRecognizer.startRecognition().collect { result ->
            when (result) {
                is RecognitionResult.Partial -> {
                    // Geçici sonuç (kullanıcı konuşurken)
                    println("Partial: ${result.text}")
                }
                is RecognitionResult.Final -> {
                    // Nihai sonuç
                    println("Final: ${result.text}")
                }
                is RecognitionResult.Error -> {
                    println("Hata: ${result.message}")
                }
            }
        }
    }

    suspend fun processAudio(audioData: ShortArray) {
        // PCM audio verisi gönder
        speechRecognizer.processAudioData(audioData)
    }

    fun stopRecognition() {
        speechRecognizer.stopRecognition()
    }
}
```

### Audio Kaydı ile Kullanım

Mevcut `AudioRecorder` ile entegre edin:

```kotlin
import com.jetbrains.kmpapp.audio.AudioRecorder

val audioRecorder = createAudioRecorder()
val speechRecognizer: SpeechRecognizer by inject()

// Kayıt başlat
audioRecorder.start { audioData ->
    // Her audio chunk'ı için tanıma yap
    scope.launch {
        speechRecognizer.processAudioData(audioData)
    }
}

// Tanıma sonuçlarını dinle
speechRecognizer.startRecognition().collect { result ->
    when (result) {
        is RecognitionResult.Final -> {
            println("Tanınan metin: ${result.text}")
        }
        else -> {}
    }
}

// Durdur
audioRecorder.stop()
speechRecognizer.stopRecognition()
```

### Dil Değiştirme

```kotlin
// Türkçe için
val configTR = RecognizerConfig(
    language = RecognitionLanguage.TURKISH,
    sampleRate = 16000
)
speechRecognizer.initialize(configTR)

// İngilizce için
val configEN = RecognizerConfig(
    language = RecognitionLanguage.ENGLISH,
    sampleRate = 16000
)
speechRecognizer.initialize(configEN)
```

### Desteklenen Diller

```kotlin
enum class RecognitionLanguage {
    ENGLISH,    // İngilizce
    TURKISH,    // Türkçe
    GERMAN,     // Almanca
    FRENCH,     // Fransızca
    SPANISH,    // İspanyolca
    ITALIAN,    // İtalyanca
    RUSSIAN,    // Rusça
    CHINESE,    // Çince
    JAPANESE,   // Japonca
    KOREAN,     // Korece
    ARABIC      // Arapça
}
```

---

## 🔧 Konfigürasyon

### RecognizerConfig Parametreleri

```kotlin
data class RecognizerConfig(
    val language: RecognitionLanguage = RecognitionLanguage.ENGLISH,
    val sampleRate: Int = 16000,              // Audio sample rate (Hz)
    val enablePunctuation: Boolean = true,     // Noktalama işaretleri
    val maxAlternatives: Int = 1               // Alternatif sonuç sayısı
)
```

---

## 🧪 Test Etme

### Android

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

### iOS

```bash
cd iosApp
xcodebuild -workspace iosApp.xcworkspace \
           -scheme iosApp \
           -sdk iphonesimulator \
           -configuration Debug
```

---

## ❗ Sorun Giderme

### Model yüklenemiyor (Android)

**Hata:**
```
Failed to load model
```

**Çözüm:**
1. Model dosyalarının `composeApp/src/androidMain/assets/sherpa-onnx/` klasöründe olduğundan emin olun
2. Dosya isimlerinin doğru olduğunu kontrol edin:
   - `encoder.onnx`
   - `decoder.onnx`
   - `joiner.onnx`
   - `tokens.txt`
3. `build.gradle.kts`'de assets klasörünü tanımladığınızdan emin olun

### Model yüklenemiyor (iOS)

**Hata:**
```
Could not find model files
```

**Çözüm:**
1. Xcode'da model dosyalarının projeye eklendiğinden emin olun
2. Target Membership'in işaretli olduğunu kontrol edin
3. "Copy Bundle Resources" altında dosyaların listelendiğini doğrulayın

### Ses tanınmıyor

**Çözüm:**
1. Mikrofon izni verildiğinden emin olun
2. Audio sample rate'in model ile uyumlu olduğunu kontrol edin (genelde 16000 Hz)
3. Audio formatının PCM 16-bit mono olduğundan emin olun
4. Model dosyasının dile uygun olduğunu kontrol edin

### Gradle sync hatası

**Hata:**
```
Could not resolve com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.30
```

**Çözüm:**
1. İnternet bağlantınızı kontrol edin
2. Gradle cache'i temizleyin:
   ```bash
   ./gradlew clean
   ./gradlew --refresh-dependencies
   ```
3. Maven Central'ın erişilebilir olduğundan emin olun

---

## 📚 Model Eğitimi (İleri Seviye)

Kendi modelinizi eğitmek isterseniz:

1. [icefall](https://github.com/k2-fsa/icefall) kullanarak model eğitin
2. [sherpa-onnx-convert](https://k2-fsa.github.io/sherpa/onnx/index.html) ile ONNX formatına dönüştürün
3. Projenize entegre edin

---

## 🔗 Kaynaklar

- [Sherpa-ONNX GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [Sherpa-ONNX Documentation](https://k2-fsa.github.io/sherpa/onnx/index.html)
- [Pre-trained Models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html)
- [Next-gen Kaldi](https://github.com/k2-fsa)
- [Icefall - Training](https://github.com/k2-fsa/icefall)

---

## 💡 İpuçları

1. **Model Boyutu**: Modeller büyük olabilir (100-500 MB). İlk indirme zamanı uzun sürebilir.

2. **Performans**:
   - Daha küçük modeller daha hızlı çalışır ama daha düşük doğruluk
   - Daha büyük modeller daha yavaş ama daha yüksek doğruluk

3. **Batarya**: On-device tanıma bataryayı tüketir. Optimizasyon için:
   - Sadece gerektiğinde tanımayı başlatın
   - Kullanılmadığında kaynakları serbest bırakın
   - Düşük güç modunda daha küçük modeller kullanın

4. **Noise Cancellation**: Gürültülü ortamlarda kullanım için:
   - Noise reduction ön işleme uygulayın
   - Gürültü için eğitilmiş modeller kullanın

---

## ✅ Kurulum Tamamlandı

Artık Sherpa-ONNX ile on-device konuşma tanıma kullanabilirsiniz! 🎉

Herhangi bir sorunla karşılaşırsanız, yukarıdaki "Sorun Giderme" bölümüne bakın veya [GitHub Issues](https://github.com/k2-fsa/sherpa-onnx/issues) üzerinden yardım alabilirsiniz.
