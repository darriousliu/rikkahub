package me.rerere.rikkahub.utils

import android.database.CursorWindow
import co.touchlab.kermit.Logger

private const val TAG = "DatabaseUtil"

object DatabaseUtil {
    fun setCursorWindowSize(size: Int) {
        try {
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            val oldValue = field.get(null) as Int
            field.set(null, size)
            Logger.i(TAG) { "setCursorWindowSize: set $oldValue to $size" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
