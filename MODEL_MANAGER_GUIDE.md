# 📦 Model Manager - Offline Model İndirme Sistemi

Generic ve esnek bir model indirme ve yönetim sistemi. **Wi-Fi kontrolü**, **kullanıcı onayı**, ve **offline çalışma** desteği ile.

---

## 🎯 Özellikler

✅ **Wi-Fi Kontrolü**: Otomatik olarak bağlantı tipini kontrol eder
✅ **Kullanıcı Onayı**: Cellular data kullanımı için onay ister
✅ **Progress Tracking**: İndirme ilerlemesini gösterir
✅ **Offline Çalışma**: Model indirildikten sonra internet gerektirmez
✅ **Model Yönetimi**: İndirme, silme, listeleme
✅ **Platform Agnostic**: Android ve iOS desteği
✅ **Generic Mimari**: Herhangi bir model tipi için kullanılabilir

---

## 📁 Proje Yapısı

```
shared/src/
├── commonMain/kotlin/com/jetbrains/kmpapp/model/
│   ├── ModelManager.kt              # Interface tanımları
│   ├── DefaultModelManager.kt       # Ortak implementasyon
│   └── ModelManagerViewModel.kt     # ViewModel
├── androidMain/kotlin/com/jetbrains/kmpapp/model/
│   ├── NetworkManager.android.kt    # Android Wi-Fi kontrolü
│   ├── ModelDownloader.android.kt   # Android model indirme
│   └── ModelManager.android.kt      # Android factory
└── iosMain/kotlin/com/jetbrains/kmpapp/model/
    ├── NetworkManager.ios.kt        # iOS Wi-Fi kontrolü
    ├── ModelDownloader.ios.kt       # iOS model indirme
    └── ModelManager.ios.kt          # iOS factory

composeApp/src/androidMain/kotlin/com/jetbrains/kmpapp/screens/
└── ModelManagerScreen.kt            # UI ekranı
```

---

## 🚀 Hızlı Başlangıç

### 1. Koin Dependency Injection Kurulumu

`shared/src/commonMain/kotlin/com/jetbrains/kmpapp/di/Koin.kt` dosyasını güncelleyin:

```kotlin
import com.jetbrains.kmpapp.model.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sharedModule = module {
    // Platform-specific implementations will be provided
    // Android: Use createAndroidModelManager(context)
    // iOS: Use createModelManager()
}
```

### 2. Android Application Sınıfında Initialize

```kotlin
// composeApp/src/androidMain/kotlin/YourApplication.kt
import android.app.Application
import com.jetbrains.kmpapp.model.createAndroidModelManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MyApplication)
            modules(
                module {
                    single { createAndroidModelManager(get()) }
                }
            )
        }
    }
}
```

### 3. UI'da Kullanım

```kotlin
import androidx.compose.runtime.*
import com.jetbrains.kmpapp.model.*
import com.jetbrains.kmpapp.screens.ModelManagerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.compose.koinInject

@Composable
fun MyApp() {
    val modelManager: ModelManager = koinInject()
    val scope = rememberCoroutineScope()

    val viewModel = remember {
        ModelManagerViewModel(modelManager, scope)
    }

    ModelManagerScreen(viewModel = viewModel)
}
```

---

## 💻 Kullanım Örnekleri

### Örnek 1: Basit Model İndirme

```kotlin
import com.jetbrains.kmpapp.model.*

class SpeechRecognitionSetup(
    private val modelManager: ModelManager,
    private val scope: CoroutineScope
) {
    suspend fun setupTurkishModel() {
        val result = modelManager.ensureModelAvailable(
            modelId = "whisper-base-tr",
            onConfirmationNeeded = { modelInfo, networkType ->
                // Kullanıcıya sor
                if (networkType == NetworkType.CELLULAR) {
                    showConfirmationDialog(modelInfo)
                    true // veya false (kullanıcı cevabına göre)
                } else {
                    true // WiFi ise doğrudan indir
                }
            },
            onProgress = { status ->
                when (status) {
                    is ModelDownloadStatus.Downloading -> {
                        println("İndiriliyor: ${(status.progress * 100).toInt()}%")
                    }
                    is ModelDownloadStatus.Downloaded -> {
                        println("✓ İndirme tamamlandı")
                    }
                    is ModelDownloadStatus.Failed -> {
                        println("✗ Hata: ${status.error}")
                    }
                    else -> {}
                }
            }
        )

        when (result) {
            is ModelDownloadResult.Success -> {
                println("Model hazır: ${result.modelInfo.localPath}")
                // Speech recognizer'ı başlat
            }
            is ModelDownloadResult.Error -> {
                println("Hata: ${result.message}")
            }
            is ModelDownloadResult.Cancelled -> {
                println("İndirme iptal edildi")
            }
        }
    }
}
```

### Örnek 2: Dil Seçimi ile Model İndirme

```kotlin
@Composable
fun LanguageSelectionScreen(modelManager: ModelManager) {
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Konuşma tanıma dili seçin:")

        Button(
            onClick = {
                selectedLanguage = "tr"
                scope.launch {
                    isDownloading = true
                    downloadModelForLanguage("whisper-base-tr", modelManager)
                    isDownloading = false
                }
            }
        ) {
            Text("🇹🇷 Türkçe")
        }

        Button(
            onClick = {
                selectedLanguage = "en"
                scope.launch {
                    isDownloading = true
                    downloadModelForLanguage("whisper-base-en", modelManager)
                    isDownloading = false
                }
            }
        ) {
            Text("🇺🇸 English")
        }

        if (isDownloading) {
            CircularProgressIndicator()
        }
    }
}

suspend fun downloadModelForLanguage(modelId: String, modelManager: ModelManager) {
    val result = modelManager.ensureModelAvailable(
        modelId = modelId,
        onConfirmationNeeded = { model, networkType ->
            // Show dialog (simplified)
            true
        },
        onProgress = { status ->
            // Update UI
        }
    )

    if (result is ModelDownloadResult.Success) {
        // Model ready, start speech recognition
    }
}
```

### Örnek 3: İndirilen Modelleri Listeleme

```kotlin
class ModelListViewModel(private val modelManager: ModelManager) {
    private val modelDownloader = modelManager.getModelDownloader()

    suspend fun getDownloadedModels(): List<ModelInfo> {
        return modelDownloader.getDownloadedModels()
    }

    suspend fun getAllModels(): List<ModelInfo> {
        return modelDownloader.getAvailableModels()
    }

    suspend fun deleteModel(modelId: String) {
        val success = modelDownloader.deleteModel(modelId)
        if (success) {
            println("Model silindi: $modelId")
        }
    }
}
```

### Örnek 4: Network Durumunu İzleme

```kotlin
@Composable
fun NetworkStatusIndicator(modelManager: ModelManager) {
    val networkManager = modelManager.getNetworkManager()
    val networkType by networkManager.observeNetworkChanges()
        .collectAsState(initial = NetworkType.NONE)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (networkType) {
                NetworkType.WIFI -> Icons.Default.Wifi
                NetworkType.CELLULAR -> Icons.Default.SignalCellularAlt
                NetworkType.ETHERNET -> Icons.Default.Cable
                NetworkType.NONE -> Icons.Default.SignalCellularOff
            },
            contentDescription = null
        )
        Text(
            text = when (networkType) {
                NetworkType.WIFI -> "WiFi"
                NetworkType.CELLULAR -> "Cellular"
                NetworkType.ETHERNET -> "Ethernet"
                NetworkType.NONE -> "Offline"
            }
        )
    }
}
```

### Örnek 5: İlk Çalıştırmada Model İndirme

```kotlin
class FirstRunSetup(private val modelManager: ModelManager) {
    suspend fun checkAndDownloadRequiredModels() {
        val requiredModels = listOf("whisper-base-tr", "whisper-base-en")

        for (modelId in requiredModels) {
            val modelDownloader = modelManager.getModelDownloader()

            if (!modelDownloader.isModelAvailable(modelId)) {
                println("Model eksik: $modelId")

                // Kullanıcıya göster
                showModelDownloadPrompt(modelId)
            }
        }
    }

    private suspend fun showModelDownloadPrompt(modelId: String) {
        val result = modelManager.ensureModelAvailable(
            modelId = modelId,
            onConfirmationNeeded = { model, networkType ->
                // "İlk kullanım için model indirmek gerekiyor" mesajı
                true
            },
            onProgress = { status ->
                // Progress göster
            }
        )

        if (result is ModelDownloadResult.Success) {
            println("✓ $modelId indirildi ve hazır")
        }
    }
}
```

---

## 🎨 UI Ekranları

### Model Yönetim Ekranı

Hazır UI komponenti ile modelleri yönetin:

```kotlin
import com.jetbrains.kmpapp.screens.ModelManagerScreen

@Composable
fun SettingsScreen() {
    val modelManager: ModelManager = koinInject()
    val scope = rememberCoroutineScope()

    val viewModel = remember {
        ModelManagerViewModel(modelManager, scope)
    }

    ModelManagerScreen(viewModel = viewModel)
}
```

**Ekran özellikleri:**
- ✅ Mevcut modelleri listeler
- ✅ İndirme butonu (WiFi kontrolü ile)
- ✅ İndirme ilerlemesi
- ✅ Model silme
- ✅ Network durumu göstergesi
- ✅ Cellular data uyarı dialog'u

---

## 🔧 Model Kataloğunu Özelleştirme

### Android'de Model Kataloğu

`shared/src/androidMain/.../ModelDownloader.android.kt`:

```kotlin
private val modelCatalog = mapOf(
    "your-model-id" to ModelInfo(
        id = "your-model-id",
        name = "your-model-name",
        displayName = "Your Model Display Name",
        language = "tr", // veya null
        size = 100 * 1024 * 1024L, // 100 MB
        downloadUrl = "https://example.com/model.tar.bz2",
        localPath = null,
        version = "1.0.0",
        checksumMd5 = "abc123..." // opsiyonel
    )
)
```

### iOS'te Model Kataloğu

`shared/src/iosMain/.../ModelDownloader.ios.kt`:

Aynı şekilde `modelCatalog` map'ini güncelleyin.

---

## 🌐 Network İzinleri

### Android

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

### iOS

`Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

**Not:** Production'da `NSAllowsArbitraryLoads` yerine specific domain exceptions kullanın.

---

## 📊 Model Durum Yönetimi

### Model Download Status'leri

```kotlin
sealed class ModelDownloadStatus {
    data object Idle                    // Boşta
    data object Checking                // Kontrol ediliyor
    data class Downloading(             // İndiriliyor
        val progress: Float,            // 0.0 - 1.0
        val downloaded: Long,           // Bytes
        val total: Long                 // Bytes
    )
    data class Downloaded(val modelId: String)  // Tamamlandı
    data class Failed(val error: String)        // Hata
}
```

### Model Download Result

```kotlin
sealed class ModelDownloadResult {
    data class Success(val modelInfo: ModelInfo)
    data class Error(val message: String, val cause: Throwable? = null)
    data object Cancelled
}
```

---

## 🎯 Best Practices

### 1. İlk Çalıştırmada Modelleri İndirin

```kotlin
LaunchedEffect(Unit) {
    val modelDownloader = modelManager.getModelDownloader()
    val isAvailable = modelDownloader.isModelAvailable("whisper-base-tr")

    if (!isAvailable) {
        // Kullanıcıya model indirme prompt'u göster
        showModelSetupScreen()
    }
}
```

### 2. WiFi Bekleyin (Opsiyonel)

```kotlin
val networkManager = modelManager.getNetworkManager()

networkManager.observeNetworkChanges().collect { networkType ->
    if (networkType == NetworkType.WIFI) {
        // Otomatik olarak modelleri indir
        downloadPendingModels()
    }
}
```

### 3. Background İndirme

```kotlin
// Android WorkManager ile
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val modelManager = createAndroidModelManager(applicationContext)

        val result = modelManager.ensureModelAvailable(
            modelId = "whisper-base-tr",
            onConfirmationNeeded = { _, _ -> true },
            onProgress = { }
        )

        return when (result) {
            is ModelDownloadResult.Success -> Result.success()
            else -> Result.failure()
        }
    }
}
```

### 4. Disk Alanı Kontrolü

```kotlin
suspend fun checkDiskSpace(modelInfo: ModelInfo): Boolean {
    val availableSpace = getAvailableDiskSpace()
    val requiredSpace = modelInfo.size + (50 * 1024 * 1024) // +50 MB buffer

    return availableSpace >= requiredSpace
}

// Android
fun getAvailableDiskSpace(): Long {
    val path = context.filesDir
    val stat = android.os.StatFs(path.absolutePath)
    return stat.availableBytes
}
```

---

## ⚡ Performans İpuçları

### 1. Model Cache

Modeller cihazda saklanır:
- **Android**: `context.filesDir/models/`
- **iOS**: `Documents/models/`

### 2. Paralel İndirme

Birden fazla modeli paralel indirmek için:

```kotlin
suspend fun downloadMultipleModels(modelIds: List<String>) {
    coroutineScope {
        modelIds.map { modelId ->
            async {
                modelManager.ensureModelAvailable(
                    modelId = modelId,
                    onConfirmationNeeded = { _, _ -> true },
                    onProgress = { }
                )
            }
        }.awaitAll()
    }
}
```

### 3. Resume Download (İleride Eklenecek)

Şu anda desteklenmiyor, ancak gelecekte eklenebilir:

```kotlin
// Placeholder for future implementation
suspend fun resumeDownload(modelId: String, fromByte: Long)
```

---

## 🔍 Sorun Giderme

### 1. "Model not found" Hatası

**Sebep**: Model kataloğunda yok
**Çözüm**: `modelCatalog` map'ine model ekleyin

### 2. "No network connection" Hatası

**Sebep**: İnternet bağlantısı yok
**Çözüm**: WiFi/Cellular data açık olduğundan emin olun

### 3. İndirme Çok Yavaş

**Sebep**: Server limitleri veya yavaş bağlantı
**Çözüm**: Daha küçük model kullanın veya CDN kullanın

### 4. Disk Alanı Yetersiz

**Sebep**: Cihazda yeterli alan yok
**Çözüm**: `checkDiskSpace()` ile önce kontrol edin

---

## 📚 API Referansı

### ModelManager

```kotlin
interface ModelManager {
    suspend fun ensureModelAvailable(
        modelId: String,
        forceDownload: Boolean = false,
        onConfirmationNeeded: suspend (ModelInfo, NetworkType) -> Boolean,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult

    fun getNetworkManager(): NetworkManager
    fun getModelDownloader(): ModelDownloader
}
```

### NetworkManager

```kotlin
interface NetworkManager {
    fun getCurrentNetworkType(): NetworkType
    fun isWiFiConnected(): Boolean
    fun isNetworkAvailable(): Boolean
    fun observeNetworkChanges(): Flow<NetworkType>
}
```

### ModelDownloader

```kotlin
interface ModelDownloader {
    suspend fun isModelAvailable(modelId: String): Boolean
    suspend fun getModelInfo(modelId: String): ModelInfo?
    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult
    suspend fun deleteModel(modelId: String): Boolean
    suspend fun getAvailableModels(): List<ModelInfo>
    suspend fun getDownloadedModels(): List<ModelInfo>
    fun cancelDownload()
}
```

---

## 🎉 Tamamlandı!

Artık generic bir model indirme ve yönetim sisteminiz var!

**Özellikler:**
- ✅ Wi-Fi kontrolü
- ✅ Kullanıcı onayı
- ✅ Progress tracking
- ✅ Offline çalışma
- ✅ Model yönetimi
- ✅ Platform-agnostic
- ✅ Hazır UI

**Sonraki adımlar:**
1. Model kataloğunu kendi modellerinizle doldurun
2. UI ekranını uygulamanıza entegre edin
3. İlk çalıştırmada model indirme akışını ekleyin
4. Speech recognizer ile entegre edin

**İyi çalışmalar! 🚀**
