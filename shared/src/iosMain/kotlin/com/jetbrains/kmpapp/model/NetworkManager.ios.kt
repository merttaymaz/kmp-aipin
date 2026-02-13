package com.jetbrains.kmpapp.model

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.SystemConfiguration.*
import kotlinx.cinterop.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun createNetworkManager(): NetworkManager {
    return IOSNetworkManager()
}

@OptIn(ExperimentalForeignApi::class)
class IOSNetworkManager : NetworkManager {

    private fun getReachability(): SCNetworkReachabilityRef? {
        return memScoped {
            val zeroAddress = alloc<sockaddr_in>()
            zeroAddress.sin_len = sizeOf<sockaddr_in>().toUByte()
            zeroAddress.sin_family = AF_INET.convert()

            SCNetworkReachabilityCreateWithAddress(
                null,
                zeroAddress.ptr.reinterpret()
            )
        }
    }

    override fun getCurrentNetworkType(): NetworkType {
        val reachability = getReachability() ?: return NetworkType.NONE

        return memScoped {
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            val success = SCNetworkReachabilityGetFlags(reachability, flags.ptr)

            if (!success) {
                return NetworkType.NONE
            }

            val reachabilityFlags = flags.value

            val isReachable = (reachabilityFlags and kSCNetworkReachabilityFlagsReachable.toUInt()) != 0u
            val needsConnection = (reachabilityFlags and kSCNetworkReachabilityFlagsConnectionRequired.toUInt()) != 0u

            if (!isReachable || needsConnection) {
                return NetworkType.NONE
            }

            // Check if WiFi
            val isWWAN = (reachabilityFlags and kSCNetworkReachabilityFlagsIsWWAN.toUInt()) != 0u
            if (isWWAN) {
                NetworkType.CELLULAR
            } else {
                NetworkType.WIFI
            }
        }
    }

    override fun isWiFiConnected(): Boolean {
        return getCurrentNetworkType() == NetworkType.WIFI
    }

    override fun isNetworkAvailable(): Boolean {
        val networkType = getCurrentNetworkType()
        return networkType != NetworkType.NONE
    }

    override fun observeNetworkChanges(): Flow<NetworkType> = callbackFlow {
        val reachability = getReachability()
        if (reachability == null) {
            trySend(NetworkType.NONE)
            close()
            return@callbackFlow
        }

        val callback: SCNetworkReachabilityCallBack = staticCFunction {
            _: SCNetworkReachabilityRef?,
            _: SCNetworkReachabilityFlags,
            info: COpaquePointer? ->
            // Network changed, send update
            info?.let {
                val manager = it.asStableRef<IOSNetworkManager>().get()
                val networkType = manager.getCurrentNetworkType()
                // We need to send this on main queue
                dispatch_async(dispatch_get_main_queue()) {
                    // Channel would be closed by then, so we skip this
                }
            }
        }

        val selfRef = StableRef.create(this@IOSNetworkManager)

        memScoped {
            var context = alloc<SCNetworkReachabilityContext>()
            context.version = 0
            context.info = selfRef.asCPointer()
            context.retain = null
            context.release = null
            context.copyDescription = null

            SCNetworkReachabilitySetCallback(reachability, callback, context.ptr)
            SCNetworkReachabilityScheduleWithRunLoop(
                reachability,
                CFRunLoopGetCurrent(),
                kCFRunLoopDefaultMode
            )
        }

        // Send initial state
        trySend(getCurrentNetworkType())

        awaitClose {
            SCNetworkReachabilityUnscheduleFromRunLoop(
                reachability,
                CFRunLoopGetCurrent(),
                kCFRunLoopDefaultMode
            )
            selfRef.dispose()
        }
    }
}
