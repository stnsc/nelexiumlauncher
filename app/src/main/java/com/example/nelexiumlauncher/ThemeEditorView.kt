package com.example.nelexiumlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

private fun Context.themeDp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

class ThemeEditorView(
    context: Context,
    initialBackground: Int,
    initialLine: Int,
    private var presets: List<ThemePreset>,
    private var lightIndex: Int,
    private var darkIndex: Int,
    private val onPreviewChanged: (background: Int, line: Int) -> Unit,
    private val onSavePreset: (name: String, background: Int, line: Int) -> Unit,
    private val onAssignPreset: (index: Int, dark: Boolean) -> Unit,
    private val onDashboard: () -> Unit
) : LinearLayout(context) {
    private var backgroundColor = initialBackground
    private var lineColor = initialLine
    private var editingBackground = true
    private val backgroundButton = colorButton("COLOR 1 · BACKGROUND", backgroundColor)
    private val lineButton = colorButton("COLOR 2 · LINES", lineColor)
    private val picker = ThemeColorPicker(context, backgroundColor) { color ->
        if (editingBackground) backgroundColor = color else lineColor = color
        updateColorButtons()
        onPreviewChanged(backgroundColor, lineColor)
    }
    private val name = EditText(context).apply {
        hint = "Preset name"
        isSingleLine = true
        setTextColor(Color.WHITE)
    }
    private val presetList = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(context.themeDp(4), context.themeDp(4), context.themeDp(4), context.themeDp(4))
    }
    private val presetScroll = ScrollView(context).apply {
        addView(presetList)
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        setPadding(context.themeDp(16), context.themeDp(10), context.themeDp(16), context.themeDp(10))

        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "COLOR THEME PRESETS"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(actionButton("Dashboard") { onDashboard() })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, context.themeDp(48)))

        val colorButtons = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        colorButtons.addView(backgroundButton, LayoutParams(0, context.themeDp(52), 1f).apply { marginEnd = context.themeDp(6) })
        colorButtons.addView(lineButton, LayoutParams(0, context.themeDp(52), 1f).apply { marginStart = context.themeDp(6) })
        addView(colorButtons)

        backgroundButton.setOnClickListener {
            editingBackground = true
            picker.setColor(backgroundColor)
            updateColorButtons()
        }
        lineButton.setOnClickListener {
            editingBackground = false
            picker.setColor(lineColor)
            updateColorButtons()
        }

        addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, context.themeDp(230)).apply {
            topMargin = context.themeDp(10)
            bottomMargin = context.themeDp(8)
        })
        addView(name, LayoutParams(LayoutParams.MATCH_PARENT, context.themeDp(48)))

        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(actionButton("Save preset") {
            onSavePreset(name.text.toString().trim().ifBlank { "Preset ${presets.size + 1}" }, backgroundColor, lineColor)
            name.text.clear()
            refreshPresets(presets, lightIndex, darkIndex)
        }, LayoutParams(0, context.themeDp(48), 1f).apply { marginEnd = context.themeDp(6) })
        actions.addView(actionButton("Presets") {
            presetScroll.visibility = if (presetScroll.visibility == VISIBLE) GONE else VISIBLE
        }, LayoutParams(0, context.themeDp(48), 1f).apply { marginStart = context.themeDp(6) })
        addView(actions)
        addView(presetScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.themeDp(6) })
        updateColorButtons()
        refreshPresets(presets, lightIndex, darkIndex)
    }

    fun refreshPresets(updated: List<ThemePreset>, updatedLight: Int, updatedDark: Int) {
        presets = updated
        lightIndex = updatedLight
        darkIndex = updatedDark
        presetList.removeAllViews()
        presets.forEachIndexed { index, preset ->
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.themeDp(4), context.themeDp(3), context.themeDp(4), context.themeDp(3))
            }
            row.addView(TextView(context).apply {
                text = preset.name + when {
                    index == lightIndex && index == darkIndex -> "  · LIGHT + DARK"
                    index == lightIndex -> "  · LIGHT"
                    index == darkIndex -> "  · DARK"
                    else -> ""
                }
                textSize = 14f
                setTextColor(preset.lineColor)
                layoutParams = LayoutParams(0, context.themeDp(44), 1f)
            })
            row.addView(actionButton("Set to light") { onAssignPreset(index, false) })
            row.addView(actionButton("Set to dark") { onAssignPreset(index, true) })
            presetList.addView(row)
        }
    }

    private fun updateColorButtons() {
        backgroundButton.background = swatch(backgroundColor)
        lineButton.background = swatch(lineColor)
        backgroundButton.alpha = if (editingBackground) 1f else 0.6f
        lineButton.alpha = if (editingBackground) 0.6f else 1f
    }

    private fun colorButton(label: String, color: Int) = actionButton(label) { }

    private fun actionButton(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(context.themeDp(8), 0, context.themeDp(8), 0)
    }

    private fun swatch(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.themeDp(3).toFloat()
        setStroke(context.themeDp(1), Color.argb(210, 255, 255, 255))
    }
}

private class ThemeColorPicker(
    context: Context,
    color: Int,
    private val onColorChanged: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = FloatArray(3)
    private val hueColors = intArrayOf(
        Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
    )

    init {
        setColor(color)
        isFocusable = true
    }

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hueHeight = context.themeDp(28).toFloat()
        val squareBottom = height - hueHeight - context.themeDp(8)
        val hue = hsv[0]
        val base = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, base, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), squareBottom, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, squareBottom, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), squareBottom, paint)

        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, hueColors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, squareBottom + context.themeDp(8), width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.themeDp(2).toFloat()
        paint.color = Color.WHITE
        val markerX = hsv[1] * width
        val markerY = (1f - hsv[2]) * squareBottom
        canvas.drawCircle(markerX, markerY, context.themeDp(8).toFloat(), paint)
        val hueMarkerX = hsv[0] / 360f * width
        canvas.drawLine(hueMarkerX, squareBottom + context.themeDp(2), hueMarkerX, height.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
        val hueHeight = context.themeDp(28).toFloat()
        val squareBottom = height - hueHeight - context.themeDp(8)
        if (event.y > squareBottom) {
            hsv[0] = (event.x / width * 360f).coerceIn(0f, 360f)
        } else {
            hsv[1] = (event.x / width).coerceIn(0f, 1f)
            hsv[2] = (1f - event.y / squareBottom).coerceIn(0f, 1f)
        }
        invalidate()
        onColorChanged(Color.HSVToColor(hsv))
        return true
    }
}
