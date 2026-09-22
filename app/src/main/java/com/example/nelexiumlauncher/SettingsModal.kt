package com.example.nelexiumlauncher

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

/** Full-screen modal with a frozen, blurred dashboard behind two square actions. */
internal class SettingsModal(
    activity: Activity,
    dashboard: View,
    theme: ThemePreset,
    onUpdates: () -> Unit,
    onAndroidSettings: () -> Unit
) : Dialog(activity) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private val foregroundColor = if (isBrightBackground(theme.backgroundTint)) Color.BLACK else Color.WHITE
    private val backdrop = blurredSnapshot(dashboard)

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val tiles = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Consume taps in the space between controls.
            isClickable = true
        }
        val overlay = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                val h = MeasureSpec.getSize(heightMeasureSpec)
                val side = minOf(dp(240), (w - dp(72)) / 2, h - dp(164)).coerceAtLeast(1)
                for (index in 0 until tiles.childCount) {
                    tiles.getChildAt(index).layoutParams = LinearLayout.LayoutParams(side, side).apply {
                        if (index > 0) marginStart = dp(20)
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            setOnClickListener { dismiss() }
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(backdrop)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { setBackgroundColor(Color.argb(165, 0, 0, 0)) },
                FrameLayout.LayoutParams(-1, -1))
        }
        content.addView(TextView(context).apply {
            text = "Settings"
            textSize = 24f
            typeface = context.nelexiumFont(true)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
            accessibilityHeadingCompat()
        })
        fun tile(label: String, icon: Int, action: () -> Unit) = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            contentDescription = label
            androidx.core.view.ViewCompat.setAccessibilityDelegate(this,
                object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View,
                        info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                })
            setPadding(dp(12), dp(24), dp(12), dp(24))
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(foregroundColor)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(TextView(context).apply {
                text = label
                textSize = 20f
                typeface = context.nelexiumFont(true)
                setTextColor(foregroundColor)
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            val border = GradientDrawable().apply {
                setColor(theme.backgroundTint)
                setStroke(dp(1) + 1, theme.lineColor)
            }
            background = border
            foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(55,
                Color.red(foregroundColor), Color.green(foregroundColor), Color.blue(foregroundColor))), null, ColorDrawable(Color.WHITE))
            stateListAnimator = null
            elevation = 0f
            setOnClickListener { dismiss(); action() }
        }
        tiles.addView(tile("App updates", R.drawable.ic_tabler_download, onUpdates))
        tiles.addView(tile("Android settings", R.drawable.ic_tabler_settings, onAndroidSettings))
        content.addView(tiles, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Button(context).apply {
            text = "Close"
            textSize = 16f
            typeface = context.nelexiumFont(true)
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, ColorDrawable(Color.WHITE))
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(120), dp(48)).apply { topMargin = dp(16) })
        overlay.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(overlay)
        setCancelable(true)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun View.accessibilityHeadingCompat() {
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }

}
