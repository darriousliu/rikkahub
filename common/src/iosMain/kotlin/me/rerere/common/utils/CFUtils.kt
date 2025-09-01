@file:Suppress("NOTHING_TO_INLINE")

package me.rerere.common.utils

import platform.CoreFoundation.CFTypeRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain

@Suppress("UNCHECKED_CAST")
fun <T : Any> Any?.retainBridgeAs(): T? = retainBridge()?.let { it as T }
fun Any?.retainBridge(): CFTypeRef? = CFBridgingRetain(this)

@Suppress("UNCHECKED_CAST")
fun <T : Any> CFTypeRef?.releaseBridgeAs(): T? = releaseBridge()?.let { it as T }
fun CFTypeRef?.releaseBridge(): Any? = CFBridgingRelease(this)
