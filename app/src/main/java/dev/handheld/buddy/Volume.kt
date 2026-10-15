package dev.handheld.buddy

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

object Volume {
    fun read(context: Context): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f /
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)).roundToInt()
    }
    fun adjust(context: Context, direction: Int): Int? {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.isVolumeFixed) return null
        return try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            read(context)
        } catch (_: SecurityException) { null }
    }
}
