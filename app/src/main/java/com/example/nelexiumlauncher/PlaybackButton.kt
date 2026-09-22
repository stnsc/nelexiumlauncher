package com.example.nelexiumlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/** Expands the actual button bounds while keeping the icon at its original size. */
internal class PlaybackButton(context: Context) : ImageButton(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private val panel = GradientDrawable()
    private var theme = ThemePresets.defaults.first()
    private var expansion = 0f
    private var motion: ValueAnimator? = null
    private var feedbackReady = false
    private val release = Runnable { animateFeedback(0f) }

    init {
        background = panel
        scaleType = ScaleType.CENTER
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        stateListAnimator = null
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(dp(118), dp(66)).apply { marginEnd = dp(10) }
        feedbackReady = true
        setTheme(theme)
    }

    fun setTheme(value: ThemePreset) {
        theme = value
        imageTintList = ColorStateList.valueOf(if (isBrightBackground(value.backgroundTint)) Color.BLACK else Color.WHITE)
        panel.setStroke(dp(1) + 1, value.lineColor)
        renderFeedback()
    }

    override fun setPressed(pressed: Boolean) {
        val changed = pressed != isPressed
        super.setPressed(pressed)
        if (!feedbackReady || !changed) return
        removeCallbacks(release)
        if (pressed) animateFeedback(1f)
        else postDelayed(release, 90L)
    }

    override fun performClick(): Boolean {
        // Accessibility and keyboard activation receive the same feedback as touch.
        removeCallbacks(release)
        animateFeedback(1f)
        postDelayed(release, 140L)
        return super.performClick()
    }

    private fun animateFeedback(target: Float) {
        motion?.cancel()
        motion = ValueAnimator.ofFloat(expansion, target).apply {
            duration = if (target > expansion) 110L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                expansion = it.animatedValue as Float
                renderFeedback()
            }
            start()
        }
    }

    private fun renderFeedback() {
        panel.setColor(ColorUtils.blendARGB(theme.backgroundTint, Color.WHITE, expansion * 0.22f))
        val desiredWidth = dp(118) + (dp(18) * expansion).roundToInt()
        layoutParams?.let {
            if (it.width != desiredWidth) {
                it.width = desiredWidth
                layoutParams = it
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(release)
        motion?.cancel()
        expansion = 0f
        renderFeedback()
        super.onDetachedFromWindow()
    }
}
