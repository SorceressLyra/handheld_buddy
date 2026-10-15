package dev.handheld.buddy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/** The same renderer is used for settings previews and the system overlay. */
class ControlOverlay(context: Context) : View(context) {
    var control = Control.BRIGHTNESS
        set(value) { field = value; contentDescription = "${value.title} $percent percent" }
    var percent = 65
        set(value) { field = value; contentDescription = "${control.title} $value percent" }
    var compact = false
        set(value) { field = value; requestLayout(); invalidate() }
    var style = 0
    var accent = Accent.PURPLE.overlayColor
        set(value) { field = value; icons.values.forEach { it.setTint(value) }; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val icons = mapOf(
        Control.BRIGHTNESS to requireNotNull(context.getDrawable(R.drawable.ic_backlight_high)),
        Control.VOLUME to requireNotNull(context.getDrawable(R.drawable.ic_volume_up)),
    ).onEach { (_, icon) -> icon.setBounds(17, 20, 41, 44); icon.setTint(accent) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(((if (compact) 240 else 320) * density).roundToInt(), widthMeasureSpec), resolveSize(((if (compact) 36 else 64) * density).roundToInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val drawingHeight = if (compact) 48f else 64f
        val centerY = drawingHeight / 2
        val barStart = if (compact) 44f else 53f
        val barEnd = if (compact) 312f else 296f
        canvas.scale(width / 320f, height / drawingHeight)
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
        canvas.restore()
    }
}

class OverlayController(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ControlOverlay? = null
    private var compactMode = false
    private val hide = Runnable { close() }
    fun show(percent: Int, style: Int, control: Control, accent: Int, compact: Boolean) {
        if (compact != compactMode) { close(); compactMode = compact }
        handler.removeCallbacks(hide)
        val overlay = view ?: ControlOverlay(context).also {
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                ((if (compact) 240 else 320) * density).roundToInt(), ((if (compact) 36 else 64) * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = ((if (compact) 4 else 24) * density).roundToInt() }
            try { windows.addView(it, params); view = it }
            catch (_: WindowManager.BadTokenException) { return }
        }
        overlay.compact = compact
        overlay.control = control
        overlay.percent = percent
        overlay.style = style
        overlay.accent = accent
        overlay.invalidate()
        handler.postDelayed(hide, 1200)
    }
    fun close() {
        handler.removeCallbacks(hide)
        view?.let { windows.removeView(it) }
        view = null
    }
}
