package dev.handheld.buddy

import android.content.Context
import android.provider.Settings
import kotlin.math.roundToInt

object Brightness {
    fun read(context: Context): Int = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(1, 255)
    fun adjust(context: Context, deltaPercent: Int): Int? {
        if (!Settings.System.canWrite(context)) return null
        val resolver = context.contentResolver
        val value = (read(context) + (deltaPercent * 255 / 100f).roundToInt()).coerceIn(1, 255)
        return try {
            if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) return null
            if (Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)) (value * 100f / 255).roundToInt() else null
        } catch (_: SecurityException) { null }
    }
}
