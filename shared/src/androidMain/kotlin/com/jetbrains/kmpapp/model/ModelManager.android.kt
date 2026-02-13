package com.jetbrains.kmpapp.model

import android.content.Context

actual fun createModelManager(): ModelManager {
    throw IllegalStateException("Use createAndroidModelManager(context) instead")
}

fun createAndroidModelManager(context: Context): ModelManager {
    val networkManager = AndroidNetworkManager(context)
    val modelDownloader = AndroidModelDownloader(context)
    return DefaultModelManager(networkManager, modelDownloader)
}
