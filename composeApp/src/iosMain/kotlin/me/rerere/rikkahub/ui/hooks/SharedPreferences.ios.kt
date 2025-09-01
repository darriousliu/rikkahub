package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import me.rerere.common.PlatformContext
import platform.Foundation.*
import platform.darwin.NSObject

@Composable
actual fun rememberSharedPreferenceString(
    keyForString: String,
    defaultValue: String?
): MutableState<String?> {
    val userDefaults = NSUserDefaults.standardUserDefaults
    val stateFlow = remember(keyForString, defaultValue) {
        userDefaults.getStringFlowForKey(keyForString, defaultValue)
    }
    // 收集 Flow 更新状态
    val state by stateFlow.collectAsStateWithLifecycle(userDefaults.stringForKey(keyForString) ?: defaultValue)

    // 写入逻辑
    return remember(state) {
        object : MutableState<String?> {
            override var value: String?
                get() = state
                set(value) {
                    userDefaults.setValue(value, keyForString)
                    userDefaults.synchronize()
                }

            override fun component1(): String? = value
            override fun component2(): (String?) -> Unit = { value = it }
        }
    }
}

@Composable
actual fun rememberSharedPreferenceBoolean(
    keyForBoolean: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val userDefaults = NSUserDefaults.standardUserDefaults
    val stateFlow = remember(keyForBoolean, defaultValue) {
        userDefaults.getBooleanFlowForKey(keyForBoolean, defaultValue)
    }
    // 收集 Flow 更新状态
    val state by stateFlow.collectAsStateWithLifecycle(
        if (userDefaults.objectForKey(keyForBoolean) != null) userDefaults.boolForKey(keyForBoolean) else defaultValue
    )

    // 写入逻辑
    return remember(state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state
                set(value) {
                    userDefaults.setBool(value, keyForBoolean)
                    userDefaults.synchronize()
                }

            override fun component1(): Boolean = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

actual fun PlatformContext.writeStringPreference(key: String, value: String?) {
    NSUserDefaults.standardUserDefaults.setValue(value, key)
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun PlatformContext.readStringPreference(
    key: String,
    defaultValue: String?
): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey(key) ?: defaultValue
}

actual fun PlatformContext.writeBooleanPreference(key: String, value: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(value, key)
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun PlatformContext.readBooleanPreference(
    key: String,
    defaultValue: Boolean
): Boolean {
    val userDefaults = NSUserDefaults.standardUserDefaults
    return if (userDefaults.objectForKey(key) != null) {
        userDefaults.boolForKey(key)
    } else {
        defaultValue
    }
}

private fun NSUserDefaults.getStringFlowForKey(
    key: String,
    defaultValue: String? = null
) = callbackFlow {
    val notificationCenter = NSNotificationCenter.defaultCenter

    // 发送初始值
    val initialValue = stringForKey(key) ?: defaultValue
    trySend(initialValue)

    // 创建观察者
    val observer = object : NSObject() {
        @Suppress("unused")
        fun onUserDefaultsChanged(notification: NSNotification?) {
            val newValue = stringForKey(key) ?: defaultValue
            trySend(newValue)
        }
    }

    // 注册监听
    val token = notificationCenter.addObserverForName(
        name = NSUserDefaultsDidChangeNotification,
        `object` = null,
        queue = null
    ) { notification ->
        observer.onUserDefaultsChanged(notification)
    }

    awaitClose {
        notificationCenter.removeObserver(observer)
        notificationCenter.removeObserver(token)
    }
}.conflate() // 防止背压，只保留最新值

private fun NSUserDefaults.getBooleanFlowForKey(
    key: String,
    defaultValue: Boolean = false
) = callbackFlow {
    val notificationCenter = NSNotificationCenter.defaultCenter

    // 发送初始值
    val initialValue = if (objectForKey(key) != null) boolForKey(key) else defaultValue
    trySend(initialValue)

    // 创建观察者
    val observer = object : NSObject() {
        @Suppress("unused")
        fun onUserDefaultsChanged(notification: NSNotification?) {
            val newValue = boolForKey(key)
            trySend(newValue)
        }
    }

    // 注册监听
    val token = notificationCenter.addObserverForName(
        name = NSUserDefaultsDidChangeNotification,
        `object` = null,
        queue = null
    ) { notification ->
        observer.onUserDefaultsChanged(notification)
    }

    awaitClose {
        notificationCenter.removeObserver(observer)
        notificationCenter.removeObserver(token)
    }
}.conflate() // 防止背压，只保留最新值
