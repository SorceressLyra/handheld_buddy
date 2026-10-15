package dev.handheld.buddy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    companion object {
        private const val GENERAL_TAB = 0
        private const val QUICK_TAB = 1
    }
    private lateinit var settings: BuddySettings
    private var settingsScroll: ScrollView? = null
    private val navigationKeys = mutableSetOf<Pair<Int, Int>>()
    private var capture: ((KeyEvent) -> Boolean)? = null
    private var status: TextView? = null
    private var accessButton: MaterialButton? = null
    private var writeButton: MaterialButton? = null
    private var selectedTopTab = 0
    private var selectedSubtab = 0
    private var pendingNeedsBrightness: Boolean? = null
    private var highlightedPermission: PermissionRequirement? = null
    private val subtabTitles = arrayOf("Setup and Appearance", "Brightness", "Volume")
    private val styles = arrayOf("Retro segments", "Minimal pill")
    private val styleValues = listOf(0, 2)

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = BuddySettings(this)
        theme.applyStyle(settings.accent.themeStyle, true)
        super.onCreate(savedInstanceState)
        selectedTopTab = savedInstanceState?.getInt("topTab", 0)?.coerceIn(0, 2) ?: 0
        selectedSubtab = savedInstanceState?.getInt("subtab", 0)?.coerceIn(0, 2) ?: 0
        if (savedInstanceState?.containsKey("pendingBrightness") == true) {
            pendingNeedsBrightness = savedInstanceState.getBoolean("pendingBrightness")
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun color(attr: Int) = MaterialColors.getColor(this, attr, "Handheld Buddy")
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(6))
    }
    private fun body(parent: LinearLayout, value: String) {
        parent.addView(label(value, 13f).apply {
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = title
            tag = title.substringBefore("·").trim()
            minHeight = dp(48)
            textSize = 13f
            isAllCaps = false
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    private fun card(parent: LinearLayout, title: String, subtitle: String? = null, build: (LinearLayout) -> Unit) {
        val card = MaterialCardView(this).apply { radius = dp(16).toFloat(); cardElevation = 0f }
        val inner = column().apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        inner.addView(label(title, 18f, true))
        subtitle?.let { body(inner, it) }
        build(inner)
        card.addView(inner)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun tabStrip(tabs: TabLayout, previous: String, next: String): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(previous, 12f, true).apply {
                contentDescription = "$previous: previous tab"
                setPadding(dp(8), 0, dp(8), 0)
            })
            addView(tabs, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(label(next, 12f, true).apply {
                contentDescription = "$next: next tab"
                setPadding(dp(8), 0, dp(8), 0)
            })
        }

    private fun render(focusTag: String? = currentFocus?.tag as? String) {
        val scrollPosition = settingsScroll?.scrollY ?: 0
        val wide = resources.configuration.screenWidthDp >= 720
        val root = column().apply {
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(12) + bars.left, dp(4) + bars.top, dp(12) + bars.right, dp(4) + bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        header.addView(label("Handheld Buddy", 20f, true).apply { setPadding(dp(8), 0, dp(16), 0) })
        val topTabs = TabLayout(this).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            isInlineLabel = true
            arrayOf("General", "Quick Controls", "About").forEachIndexed { index, title ->
                val tab = newTab().setText(title)
                addTab(tab, index == selectedTopTab)
                getTabAt(index)!!.view.tag = "top:$index"
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedTopTab = tab.position
                    highlightedPermission = null
                    pendingNeedsBrightness = null
                    settingsScroll = null
                    render("top:$selectedTopTab")
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
        header.addView(tabStrip(topTabs, "L1", "R1"), LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f))
        root.addView(header)
        if (selectedTopTab == QUICK_TAB) {
            val subtabs = TabLayout(this).apply {
                tabMode = TabLayout.MODE_SCROLLABLE
                tabGravity = TabLayout.GRAVITY_START
                isInlineLabel = true
                subtabTitles.forEachIndexed { index, title ->
                    val tab = newTab().setText(title)
                    when (index) {
                        1 -> tab.setIcon(R.drawable.ic_backlight_high)
                        2 -> tab.setIcon(R.drawable.ic_volume_up)
                    }
                    addTab(tab, index == selectedSubtab)
                    getTabAt(index)!!.view.tag = "subtab:$index"
                }
                addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        selectedSubtab = tab.position
                        settingsScroll = null
                        render("subtab:$selectedSubtab")
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                    override fun onTabReselected(tab: TabLayout.Tab) = Unit
                })
            }
            root.addView(tabStrip(subtabs, "L2", "R2"))
        }
        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        val scroll = ScrollView(this).apply { addView(columns); isFillViewport = true }
        settingsScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun addColumn(): LinearLayout {
            val box = column().apply { setPadding(dp(6), dp(8), dp(6), 0) }
            columns.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            return box
        }
        val controls = addColumn()
        val appearance = if (wide && selectedTopTab == QUICK_TAB && selectedSubtab == 0) addColumn() else controls
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        writeButton = null
        status = null
        accessButton = null
        if (selectedTopTab == QUICK_TAB && selectedSubtab == 0) {
            card(controls, "Hotkeys") { box ->
                box.addView(MaterialSwitch(this).apply {
                    tag = "enable-hotkeys"
                    text = "Enable hotkeys"
                    minHeight = dp(48)
                    textSize = 14f
                    isChecked = settings.enabled
                    setOnCheckedChangeListener { _, checked ->
                        settings.enabled = checked
                        if (!checked || !showRequiredPermission(settings.brightness.enabled)) updateStatus()
                    }
                })
                status = label("", 14f)
                box.addView(status)
                button(box, "Global hotkey  ·  ${comboName(settings.global)}") {
                    chooseKeys(settings.global, true) { keys ->
                        val bindings = settings.features.map { it.binding(keys) }
                        if (valid(bindings)) { settings.global = keys; render() }
                    }
                }
            }
            card(controls, "Reset") { box ->
                body(box, "Restore the default overlay style and button bindings.")
                button(box, "Reset Quick Controls") {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Reset Quick Controls?")
                        .setMessage("Restore Back + L1/R1 for brightness, Back + L2/R2 for volume, and Retro segments. Hotkeys and both controls will be enabled. Android permissions stay unchanged.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset") { _, _ ->
                            settings.resetQuickControls()
                            if (!showRequiredPermission(settings.brightness.enabled)) render()
                        }
                        .show()
                }
            }
            card(appearance, "Appearance", "Shared style · Hides after 1.2 seconds.") { box ->
                box.addView(MaterialSwitch(this).apply {
                    tag = "compact-overlays"
                    text = "Compact overlays"
                    textSize = 14f
                    minHeight = dp(48)
                    isChecked = settings.compactOverlays
                    setOnCheckedChangeListener { _, checked -> settings.compactOverlays = checked; render() }
                })
                val preview = ControlOverlay(this).apply {
                    percent = (Brightness.read(this@MainActivity) * 100f / 255).roundToInt()
                    compact = settings.compactOverlays
                    style = settings.style
                    accent = settings.accent.overlayColor
                }
                box.addView(preview, LinearLayout.LayoutParams(dp(if (settings.compactOverlays) 200 else 260), dp(if (settings.compactOverlays) 30 else 52)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(2); bottomMargin = dp(6)
                })
                button(box, "Style  ·  ${styles[styleValues.indexOf(settings.style)]}") {
                    MaterialAlertDialogBuilder(this).setTitle("Overlay style")
                        .setSingleChoiceItems(styles, styleValues.indexOf(settings.style)) { dialog, which ->
                            settings.style = styleValues[which]
                            dialog.dismiss()
                            render()
                        }.setNegativeButton("Cancel", null).show()
                }
            }
        }
        if (selectedTopTab == QUICK_TAB && selectedSubtab == 1) featureCard(controls, settings.brightness)
        if (selectedTopTab == QUICK_TAB && selectedSubtab == 2) featureCard(controls, settings.volume)
        if (selectedTopTab == GENERAL_TAB) {
            card(controls, "Permissions") { box ->
                body(box, "Button access enables hotkeys across apps. Brightness access is only needed for brightness control.")
                accessButton = permissionButton(box, PermissionRequirement.BUTTON_ACCESS, "Enable button access") {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Allow controller shortcuts")
                        .setMessage("Handheld Buddy uses an accessibility service to receive button events across apps and show overlays. It does not read screen content or send data.\n\nHotkey buttons are reserved while controls are enabled. Releasing Back without a trigger performs normal Back navigation.\n\nSelect Handheld Buddy controls on the next screen and enable it.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Open settings") { _, _ -> openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        .show()
                }
                writeButton = permissionButton(box, PermissionRequirement.BRIGHTNESS, "Allow brightness changes") {
                    openSettings(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            card(controls, "Accent color", "Applies to the UI and both overlays.") { box ->
                button(box, "Color  ·  ${settings.accent.title}") {
                    MaterialAlertDialogBuilder(this).setTitle("Accent color")
                        .setSingleChoiceItems(Accent.entries.map { it.title }.toTypedArray(), settings.accent.ordinal) { dialog, which ->
                            settings.accent = Accent.entries[which]
                            dialog.dismiss()
                            recreate()
                        }.setNegativeButton("Cancel", null).show()
                }
            }
        }
        if (selectedTopTab == 2) {
            card(controls, "About Handheld Buddy") { box ->
                val version = packageManager.getPackageInfo(packageName, 0).versionName
                body(box, "Version $version")
                body(box, "Developer · SorceressLyra")
                body(box, "License · GNU GPL v3.0")
                body(box, "Copyright 2026 SorceressLyra")
                body(box, "You may redistribute and modify this app under GPLv3. No warranty is provided, to the extent permitted by law.")
                button(box, "Open-source licenses") {
                    val licenses = label(assets.open("open_source_licenses.txt").bufferedReader().use { it.readText() }, 12f).apply {
                        setPadding(dp(20), dp(12), dp(20), dp(12))
                    }
                    MaterialAlertDialogBuilder(this).setTitle("Open-source licenses")
                        .setView(ScrollView(this).apply { addView(licenses) })
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        scroll.post {
            scroll.scrollTo(0, scrollPosition)
            if (focusTag != null) root.findViewWithTag<View>(focusTag)?.requestFocus()
            highlightedPermission?.let { permission ->
                root.findViewWithTag<View>("permission:${permission.name}")?.let { target ->
                    val bounds = Rect()
                    target.getDrawingRect(bounds)
                    scroll.offsetDescendantRectToMyCoords(target, bounds)
                    scroll.scrollTo(0, (bounds.top - dp(12)).coerceAtLeast(0))
                }
            }
        }
        updateStatus()
    }

    private fun featureCard(parent: LinearLayout, feature: FeatureSettings) {
        val binding = feature.binding(settings.global)
        card(parent, "${feature.control.title} control") { box ->
            box.addView(MaterialSwitch(this).apply {
                tag = "feature-enabled"
                text = if (feature.enabled) "On" else "Off"
                contentDescription = "${feature.control.title} control"
                minHeight = dp(48)
                textSize = 14f
                isChecked = feature.enabled
                setOnCheckedChangeListener { _, checked ->
                    feature.enabled = checked
                    if (!checked || !showRequiredPermission(feature.control == Control.BRIGHTNESS)) render()
                }
            })
            if (feature.enabled) {
                body(box, "Hold ${comboName(binding.modifiers)} + ${keyName(feature.decrease)} / ${keyName(feature.increase)}")
                box.addView(MaterialSwitch(this).apply {
                    tag = "override-hotkey"
                    text = "Override global hotkey"
                    minHeight = dp(48)
                    textSize = 14f
                    isChecked = feature.override != null
                    setOnCheckedChangeListener { _, checked ->
                        val override = if (checked) settings.global else null
                        if (validFeature(feature, binding.copy(modifiers = override ?: settings.global))) {
                            feature.override = override
                        }
                        render()
                    }
                })
                if (feature.override != null) button(box, "Hotkey  ·  ${comboName(binding.modifiers)}") {
                    chooseKeys(binding.modifiers, true) { keys ->
                        if (validFeature(feature, binding.copy(modifiers = keys))) { feature.override = keys; render() }
                    }
                }
                val keyButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                box.addView(keyButtons)
                button(keyButtons, "Decrease  ·  ${keyName(feature.decrease)}") {
                    chooseKeys(setOf(feature.decrease), false) { keys ->
                        if (validFeature(feature, binding.copy(decrease = keys.single()))) { feature.decrease = keys.single(); render() }
                    }
                }
                button(keyButtons, "Increase  ·  ${keyName(feature.increase)}") {
                    chooseKeys(setOf(feature.increase), false) { keys ->
                        if (validFeature(feature, binding.copy(increase = keys.single()))) { feature.increase = keys.single(); render() }
                    }
                }
                for (index in 0 until keyButtons.childCount) {
                    keyButtons.getChildAt(index).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = dp(8)
                    }
                }
                if (feature.control == Control.BRIGHTNESS) {
                    body(box, "Uses manual brightness. Minimum stays above zero.")
                } else body(box, "Media and game audio. Uses Android volume steps.")
            }
        }
    }

    private fun updateStatus() {
        val access = HotkeyService.connected
        val write = Settings.System.canWrite(this)
        status?.text = when {
            !settings.enabled -> "Paused"
            settings.features.none { it.enabled } -> "Turn on brightness or volume control."
            !validBindings(settings.activeBindings()) -> "Key conflict. Choose different feature keys or hotkeys."
            !access -> "Enable button access to use hotkeys across apps."
            settings.brightness.enabled && !write -> if (settings.volume.enabled) "Volume ready. Allow brightness changes to use both." else "Allow brightness changes to finish setup."
            else -> "Ready"
        }
        accessButton?.text = (if (highlightedPermission == PermissionRequirement.BUTTON_ACCESS) "Required · " else "") +
            if (access) "Button access enabled · Manage" else "Enable button access"
        writeButton?.text = (if (highlightedPermission == PermissionRequirement.BRIGHTNESS) "Required · " else "") +
            if (write) "Brightness permission granted · Manage" else "Allow brightness changes"
    }

    private fun permissionButton(parent: LinearLayout, permission: PermissionRequirement, title: String, action: () -> Unit): MaterialButton =
        button(parent, title, action).apply {
            tag = "permission:${permission.name}"
            if (highlightedPermission == permission) {
                strokeWidth = dp(3)
                strokeColor = ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorPrimary))
                backgroundTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorPrimaryContainer))
            }
        }

    private fun showRequiredPermission(needsBrightness: Boolean): Boolean {
        val permission = requiredPermission(HotkeyService.connected, Settings.System.canWrite(this), needsBrightness) ?: return false
        pendingNeedsBrightness = needsBrightness
        highlightedPermission = permission
        selectedTopTab = GENERAL_TAB
        settingsScroll = null
        render("permission:${permission.name}")
        return true
    }

    private fun refreshPermissionSetup() {
        val needsBrightness = pendingNeedsBrightness
        if (needsBrightness != null) {
            val permission = requiredPermission(HotkeyService.connected, Settings.System.canWrite(this), needsBrightness)
            if (permission == null) pendingNeedsBrightness = null
            if (permission != highlightedPermission) {
                highlightedPermission = permission
                selectedTopTab = GENERAL_TAB
                settingsScroll = null
                render(permission?.let { "permission:${it.name}" })
                return
            }
        }
        updateStatus()
    }

    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "This device has no settings page for this permission", Toast.LENGTH_LONG).show() }
    }
    private fun validFeature(feature: FeatureSettings, proposed: KeyBinding): Boolean =
        valid(settings.features.map { if (it === feature) proposed else it.binding(settings.global) })

    private fun valid(bindings: List<KeyBinding>): Boolean {
        val valid = validBindings(bindings)
        if (!valid) MaterialAlertDialogBuilder(this).setTitle("Choose different buttons")
            .setMessage("Hotkey buttons cannot be trigger keys. Increase and decrease must differ. Features with the same hotkey must use different trigger keys. D-pad is reserved for navigation.")
            .setPositiveButton("OK", null).show()
        return valid
    }
    private fun chooseKeys(current: Set<Int>, multiple: Boolean, save: (Set<Int>) -> Unit) {
        val options = (listOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        ) + current).distinct()
        val selected = current.toMutableSet()
        val names = options.map(::keyName).toTypedArray()
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (multiple) "Choose hotkey buttons" else "Choose trigger button")
        if (multiple) {
            builder.setMultiChoiceItems(names, BooleanArray(options.size) { options[it] in selected }) { dialog, index, checked ->
                if (checked) selected.add(options[index]) else selected.remove(options[index])
                (dialog as androidx.appcompat.app.AlertDialog)
                    .getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = selected.isNotEmpty()
            }
        } else {
            builder.setSingleChoiceItems(names, options.indexOf(current.single())) { _, index ->
                selected.clear()
                selected.add(options[index])
            }
        }
        builder.setNegativeButton("Cancel", null)
            .setNeutralButton("Record button") { _, _ -> record(multiple, save) }
            .setPositiveButton("Save") { _, _ -> save(selected.toSet()) }
            .show()
    }

    private fun record(multiple: Boolean, save: (Set<Int>) -> Unit) {
        val recorder = KeyRecorder(multiple)
        val box = column().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        body(box, if (multiple) "Hold the hotkey buttons together, then release. Use D-pad and A to save." else "Press and release your trigger button. Use D-pad and A to save.")
        val detected = label("Waiting for a button…", 18f, true)
        box.addView(detected)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (multiple) "Record hotkey" else "Record trigger")
            .setView(box)
            .setNeutralButton("Record again", null)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> if (recorder.keys.isNotEmpty()) save(recorder.keys) }
            .create()
        HotkeyService.recording = true
        val listener: (KeyEvent) -> Boolean = { event ->
            if (isDpadKey(event.keyCode)) {
                detected.text = "D-pad is for navigation. Choose another button."
                true
            } else if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                recorder.event(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
                detected.text = comboName(recorder.keys)
                if (recorder.complete) {
                    capture = null
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
                        isEnabled = true
                        requestFocus()
                    }
                }
                true
            } else false
        }
        capture = listener
        dialog.setOnKeyListener { _, _, event -> capture?.invoke(event) ?: false }
        dialog.setOnDismissListener { capture = null; HotkeyService.recording = false }
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            recorder.reset()
            capture = listener
            detected.text = "Waiting for a button…"
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("topTab", selectedTopTab)
        outState.putInt("subtab", selectedSubtab)
        pendingNeedsBrightness?.let { outState.putBoolean("pendingBrightness", it) }
        super.onSaveInstanceState(outState)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        capture?.let { return it(event) }
        val input = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP && navigationKeys.remove(input)) return true
        if (input in navigationKeys) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) {
            val target = bumperNavigation(event.keyCode, selectedTopTab, selectedSubtab)
            if (target != null) {
                navigationKeys.add(input)
                settingsScroll = null
                when (target.level) {
                    TabLevel.TOP -> {
                        selectedTopTab = target.index
                        highlightedPermission = null
                        pendingNeedsBrightness = null
                        render("top:$selectedTopTab")
                    }
                    TabLevel.SUB -> {
                        selectedSubtab = target.index
                        render("subtab:$selectedSubtab")
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onResume() {
        super.onResume()
        HotkeyService.onConnectionChanged = { refreshPermissionSetup() }
        refreshPermissionSetup()
    }
    override fun onPause() {
        HotkeyService.onConnectionChanged = null
        navigationKeys.clear()
        capture = null
        HotkeyService.recording = false
        super.onPause()
    }
}
