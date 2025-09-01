package me.rerere.rikkahub.utils

interface AppLifecycleObserver {
    fun onAppForeground()
    fun onAppBackground()
}

expect object AppLifecycleManager {
    fun addObserver(observer: AppLifecycleObserver)
    fun removeObserver(observer: AppLifecycleObserver)
}
