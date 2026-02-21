package com.jetbrains.kmpapp.di

import com.jetbrains.kmpapp.audio.createAudioPlayer
import com.jetbrains.kmpapp.data.InMemoryMuseumStorage
import com.jetbrains.kmpapp.data.KtorMuseumApi
import com.jetbrains.kmpapp.data.MuseumApi
import com.jetbrains.kmpapp.data.MuseumRepository
import com.jetbrains.kmpapp.data.MuseumStorage
import com.jetbrains.kmpapp.engine.createSTTEngine
import com.jetbrains.kmpapp.engine.createTranslationEngine
import com.jetbrains.kmpapp.engine.createTTSEngine
import com.jetbrains.kmpapp.engine.detectMemoryStrategy
import com.jetbrains.kmpapp.model.DefaultModelRegistry
import com.jetbrains.kmpapp.model.ModelRegistry
import com.jetbrains.kmpapp.pipeline.TranslationPipeline
import com.jetbrains.kmpapp.speech.createLanguageDetector
import com.jetbrains.kmpapp.speech.createSpeechRecognizer
import com.jetbrains.kmpapp.speech.MultiLanguageSpeechRecognizer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule = module {
    single {
        val json = Json { ignoreUnknownKeys = true }
        HttpClient {
            install(ContentNegotiation) {
                // TODO Fix API so it serves application/json
                json(json, contentType = ContentType.Any)
            }
        }
    }

    single<MuseumApi> { KtorMuseumApi(get()) }
    single<MuseumStorage> { InMemoryMuseumStorage() }
    single {
        MuseumRepository(get(), get()).apply {
            initialize()
        }
    }
}

val speechModule = module {
    single { createSpeechRecognizer() }
    single { createLanguageDetector() }
    single { MultiLanguageSpeechRecognizer(get(), get()) }
}

val engineModule = module {
    single { createSTTEngine() }
    single { createTranslationEngine() }
    single { createTTSEngine() }
    single { createAudioPlayer() }
    single<ModelRegistry> { DefaultModelRegistry() }
    single {
        TranslationPipeline(
            sttEngine = get(),
            translationEngine = get(),
            ttsEngine = get(),
            audioPlayer = get(),
            memoryStrategy = detectMemoryStrategy()
        )
    }
}

fun initKoin() = initKoin(emptyList())

fun initKoin(extraModules: List<Module>) {
    startKoin {
        modules(
            dataModule,
            speechModule,
            engineModule,
            *extraModules.toTypedArray(),
        )
    }
}
