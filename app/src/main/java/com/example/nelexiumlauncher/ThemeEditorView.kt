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
import android.view.ViewGroup
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
    private val onDeletePreset: (index: Int) -> Unit,
    private val onDashboard: () -> Unit
) : LinearLayout(context) {
    private var backgroundColor = initialBackground
    private var lineColor = initialLine
    private var activeBackground = initialBackground
    private var activeLine = initialLine
    private var editingBackground = true
    private val backgroundButton = actionButton("COLOR 1 · BACKGROUND") { }
    private val lineButton = actionButton("COLOR 2 · LINES") { }
    private val picker = ThemeColorPicker(context, backgroundColor) { color ->
        if (editingBackground) backgroundColor = color else lineColor = color
        onPreviewChanged(backgroundColor, lineColor)
        updateColorButtons()
    }
    private val name = EditText(context).apply {
        hint = "Preset name"
        isSingleLine = true
        textSize = 18f
        setTextColor(Color.WHITE)
        typeface = context.nelexiumFont()
        setPadding(context.themeDp(12), 0, context.themeDp(12), 0)
        backgroundTintList = null
    }
    private val presetList = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(context.themeDp(4), context.themeDp(4), context.themeDp(4), context.themeDp(4))
    }
    private val presetScroll = ScrollView(context).apply {
        addView(presetList)
    }

    init {
        orientation = VERTICAL
        setPadding(context.themeDp(0), context.themeDp(10), context.themeDp(0), context.themeDp(10))

        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "COLOR THEME PRESETS"
            textSize = 23f
            typeface = context.nelexiumFont(true)
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })

        val content = LinearLayout(context).apply { orientation = HORIZONTAL }
        content.addView(picker, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = context.themeDp(12)
        })

        val controls = LinearLayout(context).apply { orientation = VERTICAL }
        content.addView(controls, LayoutParams(0, LayoutParams.MATCH_PARENT, 2f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = context.themeDp(8)
        })

        val colorButtons = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        colorButtons.addView(backgroundButton, LayoutParams(0, context.themeDp(52), 1f).apply { marginEnd = context.themeDp(6) })
        colorButtons.addView(lineButton, LayoutParams(0, context.themeDp(52), 1f).apply { marginStart = context.themeDp(6) })
        controls.addView(colorButtons)

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

        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(name, LayoutParams(0, context.themeDp(48), 1f).apply {
            marginEnd = context.themeDp(6)
        })
        actions.addView(actionButton("Save preset") {
            onSavePreset(name.text.toString().trim().ifBlank { "Preset ${presets.size + 1}" }, backgroundColor, lineColor)
            name.text.clear()
            refreshPresets(presets, lightIndex, darkIndex)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, context.themeDp(48)))
        controls.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, context.themeDp(48)).apply {
            topMargin = context.themeDp(8)
        })
        controls.addView(TextView(context).apply {
            text = "PRESETS"
            textSize = 20f
            typeface = context.nelexiumFont(true)
            setTextColor(Color.WHITE)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.themeDp(10)
        })
        controls.addView(presetScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = context.themeDp(4)
        })
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
                setPadding(context.themeDp(8), context.themeDp(3), context.themeDp(8), context.themeDp(3))
                background = outlinedBackground(preset.backgroundTint, preset.lineColor)
            }
            row.addView(TextView(context).apply {
                text = preset.name + when {
                    index == lightIndex && index == darkIndex -> "  · LIGHT + DARK"
                    index == lightIndex -> "  · LIGHT"
                    index == darkIndex -> "  · DARK"
                    else -> ""
                }
                textSize = 19f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                typeface = context.nelexiumFont()
                setTextColor(Color.WHITE)
            }, LayoutParams(0, context.themeDp(44), 1f))
            row.addView(actionButton("Light") { onAssignPreset(index, false) },
                LayoutParams(context.themeDp(74), context.themeDp(42)).apply { marginStart = context.themeDp(6) })
            row.addView(actionButton("Dark") { onAssignPreset(index, true) },
                LayoutParams(context.themeDp(74), context.themeDp(42)).apply { marginStart = context.themeDp(6) })
            row.addView(actionButton("Delete") { onDeletePreset(index) }.apply {
                isEnabled = presets.size > 1
                if (!isEnabled) alpha = 0.4f
            }, LayoutParams(context.themeDp(82), context.themeDp(42)).apply { marginStart = context.themeDp(6) })
            presetList.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, context.themeDp(56)).apply {
                bottomMargin = context.themeDp(6)
            })
        }
        setThemeBackground(activeBackground, activeLine)
    }

    fun setThemeBackground(color: Int, borderColor: Int = lineColor) {
        activeBackground = color
        activeLine = borderColor
        applyThemeText(this, color)
        styleButtons(this, color, borderColor)
        name.setHintTextColor(if (isBrightBackground(color)) Color.DKGRAY else Color.LTGRAY)
        name.background = outlinedBackground(color, borderColor)
        backgroundButton.setTextColor(if (isBrightBackground(backgroundColor)) Color.BLACK else Color.WHITE)
        lineButton.setTextColor(if (isBrightBackground(lineColor)) Color.BLACK else Color.WHITE)
        presets.forEachIndexed { index, preset ->
            val row = presetList.getChildAt(index) as? ViewGroup ?: return@forEachIndexed
            row.background = outlinedBackground(preset.backgroundTint, preset.lineColor)
            for (childIndex in 0 until row.childCount) {
                (row.getChildAt(childIndex) as? TextView)?.setTextColor(
                    if (isBrightBackground(preset.backgroundTint)) Color.BLACK else Color.WHITE
                )
                (row.getChildAt(childIndex) as? Button)?.background =
                    outlinedBackground(preset.backgroundTint, preset.lineColor)
            }
        }
        backgroundButton.background = swatch(backgroundColor)
        lineButton.background = swatch(lineColor)
    }

    private fun updateColorButtons() {
        backgroundButton.alpha = if (editingBackground) 1f else 0.6f
        lineButton.alpha = if (editingBackground) 0.6f else 1f
        setThemeBackground(activeBackground, activeLine)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setTextColor(Color.WHITE)
        typeface = context.nelexiumFont(true)
        setOnClickListener { action() }
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(context.themeDp(8), 0, context.themeDp(8), 0)
        backgroundTintList = null
        stateListAnimator = null
        elevation = 0f
        translationZ = 0f
    }

    private fun styleButtons(view: View, color: Int, borderColor: Int) {
        when (view) {
            is Button -> view.background = outlinedBackground(color, borderColor)
            is ViewGroup -> for (index in 0 until view.childCount) {
                styleButtons(view.getChildAt(index), color, borderColor)
            }
        }
    }

    private fun outlinedBackground(color: Int, borderColor: Int) = GradientDrawable().apply {
        setColor(color)
        setStroke(context.themeDp(1) + 1, borderColor)
    }

    private fun swatch(color: Int) = GradientDrawable().apply {
        setColor(color)
        setStroke(context.themeDp(2) + 1, if (isBrightBackground(color)) Color.BLACK else Color.WHITE)
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
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
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
        paint.strokeWidth = context.themeDp(2) + 1f
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
