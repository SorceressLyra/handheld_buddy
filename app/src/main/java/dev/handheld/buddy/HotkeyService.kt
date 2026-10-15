package dev.handheld.buddy

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class HotkeyService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener, InputManager.InputDeviceListener {
    companion object {
        var onConnectionChanged: (() -> Unit)? = null
        var connected = false
            private set(value) { field = value; onConnectionChanged?.invoke() }
        var recording = false
    }
    private lateinit var settings: BuddySettings
    private lateinit var engine: HotkeyEngine
    private lateinit var overlay: OverlayController
    private var lastError = 0L
    override fun onServiceConnected() {
        settings = BuddySettings(this)
        engine = settings.engine()
        overlay = OverlayController(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        getSystemService(InputManager::class.java).registerInputDeviceListener(this, null)
        connected = true
    }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::engine.isInitialized) return false
        if (recording || !settings.enabled) { engine.reset(); return false }
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val result = engine.event(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.deviceId)
        if (result.back && !event.isCanceled) performGlobalAction(GLOBAL_ACTION_BACK)
        if (result.delta != 0 && !event.isCanceled) {
            val percent = when (result.control) {
                Control.BRIGHTNESS -> Brightness.adjust(this, result.delta * 5)
                Control.VOLUME -> Volume.adjust(this, result.delta)
                null -> null
            }
            if (percent != null && result.control != null) overlay.show(
                percent, settings.style, result.control, settings.accent.overlayColor, settings.compactOverlays,
                settings.showTime, settings.showBatteryPercentage,
            )
            else if (event.eventTime - lastError > 3000) {
                val message = if (result.control == Control.VOLUME) "Android does not allow media volume changes" else "Allow Handheld Buddy to change system settings"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                lastError = event.eventTime
            }
        }
        return result.consumed
    }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) { engine = settings.engine() }
    override fun onInputDeviceRemoved(deviceId: Int) { engine.reset() }
    override fun onInputDeviceAdded(deviceId: Int) = Unit
    override fun onInputDeviceChanged(deviceId: Int) { engine.reset() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { if (::engine.isInitialized) engine.reset() }
    override fun onDestroy() {
        connected = false
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(this)
        if (::settings.isInitialized) settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::overlay.isInitialized) overlay.close()
        super.onDestroy()
    }
}
