package com.jetbrains.kmpapp.model

actual fun createModelManager(): ModelManager {
    val networkManager = IOSNetworkManager()
    val modelDownloader = IOSModelDownloader()
    return DefaultModelManager(networkManager, modelDownloader)
}
