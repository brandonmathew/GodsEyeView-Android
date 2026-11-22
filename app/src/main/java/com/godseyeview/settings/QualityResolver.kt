package com.godseyeview.settings

import android.app.ActivityManager
import android.content.Context

/** Plan Q20: quality auto-detects from device specs at runtime - nothing
 *  is hardcoded to one phone. AUTO resolves from the device's per-app
 *  memory class and low-RAM flag; a manual mode is used as-is. */
object QualityResolver {
    fun effectiveMode(context: Context, selected: QualityMode): QualityMode {
        if (selected != QualityMode.AUTO) return selected
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.isLowRamDevice) return QualityMode.BATTERY_SAVER
        val memoryClassMb = activityManager.memoryClass
        return when {
            memoryClassMb >= 384 -> QualityMode.HIGH
            memoryClassMb >= 192 -> QualityMode.BALANCED
            else -> QualityMode.BATTERY_SAVER
        }
    }
}
