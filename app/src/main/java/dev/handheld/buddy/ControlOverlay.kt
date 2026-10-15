package dev.handheld.buddy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.Date
import kotlin.math.roundToInt

/** The same renderer is used for settings previews and the system overlay. */
class ControlOverlay(context: Context) : View(context) {
    var control = Control.BRIGHTNESS
        set(value) { field = value; updateContentDescription() }
    var percent = 65
        set(value) { field = value; updateContentDescription() }
    var compact = false
        set(value) { field = value; requestLayout(); invalidate() }
    var style = 0
    var accent = Accent.PURPLE.overlayColor
        set(value) { field = value; icons.values.forEach { it.setTint(value) }; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var showTime = false
    private var showBattery = false
    private var timeText = ""
    private var batteryText = "??%"
    private val indicatorCount get() = (if (showTime) 1 else 0) + (if (showBattery) 1 else 0)
    private val icons = mapOf(
        Control.BRIGHTNESS to requireNotNull(context.getDrawable(R.drawable.ic_backlight_high)),
        Control.VOLUME to requireNotNull(context.getDrawable(R.drawable.ic_volume_up)),
    ).onEach { (_, icon) -> icon.setBounds(17, 20, 41, 44); icon.setTint(accent) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val scale = if (compact) 0.75f else 1f
        setMeasuredDimension(
            resolveSize(((320 + indicatorCount * 88) * density * scale).roundToInt(), widthMeasureSpec),
            resolveSize(((if (compact) 36 else 64) * density).roundToInt(), heightMeasureSpec),
        )
    }
    fun configureIndicators(showTime: Boolean, showBattery: Boolean) {
        val layoutChanged = this.showTime != showTime || this.showBattery != showBattery
        this.showTime = showTime
        this.showBattery = showBattery
        if (showTime) timeText = DateFormat.getTimeFormat(context).format(Date())
        if (showBattery) batteryText = batteryPercentage()?.let { "$it%" } ?: "??%"
        updateContentDescription()
        if (layoutChanged) requestLayout()
        invalidate()
    }
    fun windowWidthPx(): Int {
        val scale = if (compact) 0.75f else 1f
        return ((320 + indicatorCount * 88) * density * scale).roundToInt()
    }
    private fun batteryPercentage(): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).roundToInt().coerceIn(0, 100)
    }
    private fun updateContentDescription() {
        val details = buildList {
            if (showTime) add(timeText)
            if (showBattery) add("Battery $batteryText")
        }
        contentDescription = (listOf("${control.title} $percent percent") + details).joinToString(", ")
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val drawingHeight = if (compact) 48f else 64f
        val centerY = drawingHeight / 2
        val barStart = if (compact) 44f else 53f
        val barEnd = if (compact) 312f else 296f
        canvas.scale(width / (320f + indicatorCount * 88f), height / drawingHeight)
        if (showTime) drawIndicator(canvas, 0f, drawingHeight, centerY, timeText, 14f)
        if (showTime) canvas.translate(88f, 0f)
        paint.color = Color.argb(240, 43, 39, 53)
        val radius = if (style == 2) centerY else if (compact) 12f else 18f
        canvas.drawRoundRect(0f, 0f, 320f, drawingHeight, radius, radius, paint)
        icons.getValue(control).apply {
            val left = if (compact) 8 else 17
            setBounds(left, centerY.toInt() - 12, left + 24, centerY.toInt() + 12)
            draw(canvas)
        }
        if (style == 0) {
            repeat(20) { i ->
                paint.color = if (i < (percent / 5f).roundToInt()) accent else Color.rgb(82, 75, 92)
                val left = barStart + i * (if (compact) 13.4f else 12.3f)
                canvas.drawRoundRect(left, centerY - 12f, left + (if (compact) 10.4f else 9f), centerY + 12f, 2f, 2f, paint)
            }
        } else {
            paint.color = Color.rgb(82, 75, 92)
            canvas.drawRoundRect(barStart, centerY - 4f, barEnd, centerY + 4f, 4f, 4f, paint)
            paint.color = accent
            canvas.drawRoundRect(barStart, centerY - 4f, barStart + (barEnd - barStart) * percent / 100, centerY + 4f, 4f, 4f, paint)
        }
        if (showBattery) drawIndicator(canvas, 328f, drawingHeight, centerY, batteryText, 15f)
        canvas.restore()
    }
    private fun drawIndicator(canvas: Canvas, left: Float, height: Float, centerY: Float, text: String, textSize: Float) {
        val top = if (compact) 4f else 5f
        val bottom = height - top
        val radius = if (style == 2) centerY - top else if (compact) 8f else 10f
        paint.color = Color.argb(240, 43, 39, 53)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(left, top, left + 80f, bottom, radius, radius, paint)
        paint.color = Color.WHITE
        paint.textSize = textSize
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, left + 40f, centerY + textSize * 0.35f, paint)
    }
}

class OverlayController(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ControlOverlay? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var compactMode = false
    private val hide = Runnable { close() }
    fun show(percent: Int, style: Int, control: Control, accent: Int, compact: Boolean, showTime: Boolean, showBattery: Boolean) {
        if (compact != compactMode) { close(); compactMode = compact }
        handler.removeCallbacks(hide)
        val overlay = view ?: ControlOverlay(context)
        overlay.compact = compact
        overlay.configureIndicators(showTime, showBattery)
        overlay.control = control
        overlay.percent = percent
        overlay.style = style
        overlay.accent = accent
        val desiredWidth = overlay.windowWidthPx()
        val params = windowParams ?: run {
            val density = context.resources.displayMetrics.density
            WindowManager.LayoutParams(
                desiredWidth, ((if (compact) 36 else 64) * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = ((if (compact) 4 else 24) * density).roundToInt() }
        }
        if (view == null) {
            try { windows.addView(overlay, params); view = overlay; windowParams = params }
            catch (_: WindowManager.BadTokenException) { return }
        } else if (params.width != desiredWidth) {
            params.width = desiredWidth
            windows.updateViewLayout(overlay, params)
            windowParams = params
        }
        overlay.invalidate()
        handler.postDelayed(hide, 1200)
    }
    fun close() {
        handler.removeCallbacks(hide)
        view?.let { windows.removeView(it) }
        view = null
        windowParams = null
    }
}
