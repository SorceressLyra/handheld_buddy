package dev.handheld.buddy

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

class FeatureSettings(private val prefs: SharedPreferences, val control: Control, private val defaultDecrease: Int, private val defaultIncrease: Int) {
    private val prefix = control.id
    var enabled: Boolean
        get() = prefs.getBoolean("$prefix.enabled", true)
        set(value) { prefs.edit().putBoolean("$prefix.enabled", value).apply() }
    var override: Set<Int>?
        get() = prefs.getStringSet("$prefix.override", null)?.mapNotNull { it.toIntOrNull() }?.filter(::isHotkeyKeyAllowed)?.toSet()?.takeIf { it.isNotEmpty() }
        set(value) { prefs.edit().putStringSet("$prefix.override", value?.map { it.toString() }?.toSet()).apply() }
    var decrease: Int
        get() = prefs.getInt("$prefix.decrease", defaultDecrease).takeIf(::isHotkeyKeyAllowed) ?: defaultDecrease
        set(value) { prefs.edit().putInt("$prefix.decrease", value).apply() }
    var increase: Int
        get() = prefs.getInt("$prefix.increase", defaultIncrease).takeIf(::isHotkeyKeyAllowed) ?: defaultIncrease
        set(value) { prefs.edit().putInt("$prefix.increase", value).apply() }
    fun binding(global: Set<Int>) = KeyBinding(control, override ?: global, decrease, increase)
}

class BuddySettings(val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("buddy", Context.MODE_PRIVATE))
    val brightness = FeatureSettings(prefs, Control.BRIGHTNESS, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)
    val volume = FeatureSettings(prefs, Control.VOLUME, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)
    val features = listOf(brightness, volume)
    var accent: Accent
        get() = Accent.entries.getOrElse(prefs.getInt("general.accent", 0)) { Accent.PURPLE }
        set(value) { prefs.edit().putInt("general.accent", value.ordinal).apply() }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    var global: Set<Int>
        get() = prefs.getStringSet("global", setOf("4"))!!.mapNotNull { it.toIntOrNull() }.filter(::isHotkeyKeyAllowed).toSet().ifEmpty { setOf(KeyEvent.KEYCODE_BACK) }
        set(value) { prefs.edit().putStringSet("global", value.map { it.toString() }.toSet()).apply() }
    var compactOverlays: Boolean
        get() = prefs.getBoolean("overlay.compact", false)
        set(value) { prefs.edit().putBoolean("overlay.compact", value).apply() }
    var style: Int
        get() = if (prefs.getInt("overlay.style", 0) == 0) 0 else 2
        set(value) { prefs.edit().putInt("overlay.style", value).apply() }
    fun resetQuickControls() {
        val keys = listOf("enabled", "global", "overlay.style", "overlay.compact", "brightness.step") +
            features.flatMap { feature ->
                listOf("enabled", "override", "decrease", "increase").map { "${feature.control.id}.$it" }
            }
        prefs.edit().also { editor -> keys.forEach { editor.remove(it) } }.apply()
    }
    fun activeBindings() = features.filter { it.enabled }.map { it.binding(global) }
    fun engine(): HotkeyEngine {
        val bindings = activeBindings()
        return HotkeyEngine(if (validBindings(bindings)) bindings else emptyList())
    }
}

fun keyName(key: Int): String = when (key) {
    KeyEvent.KEYCODE_BACK -> "Back"
    KeyEvent.KEYCODE_HOME -> "Home"
    else -> KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_").removePrefix("BUTTON_").replace('_', ' ')
}
fun comboName(keys: Set<Int>) = keys.sorted().joinToString(" + ", transform = ::keyName)

fun isHotkeyKeyAllowed(key: Int) = !isDpadKey(key) && key != KeyEvent.KEYCODE_HOME
