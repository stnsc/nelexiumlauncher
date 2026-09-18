package com.example.nelexiumlauncher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.util.WeakHashMap

fun Context.nelexiumFont(bold: Boolean = false): Typeface =
    ResourcesCompat.getFont(this, if (bold) R.font.space_grotesk_bold else R.font.space_grotesk_regular)
        ?: Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)

// Black has better contrast than white once relative luminance exceeds 0.179.
fun isBrightBackground(color: Int): Boolean {
    fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.04045) normalized / 12.92
        else Math.pow((normalized + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(Color.red(color)) +
        0.7152 * channel(Color.green(color)) +
        0.0722 * channel(Color.blue(color)) > 0.179
}

private val originalTextColors = WeakHashMap<TextView, ColorStateList>()

fun applyThemeText(root: View, backgroundColor: Int) {
    when (root) {
        is TextView -> {
            val original = originalTextColors.getOrPut(root) { root.textColors }
            root.setTextColor(if (isBrightBackground(backgroundColor)) ColorStateList.valueOf(Color.BLACK) else original)
        }
        is ViewGroup -> for (index in 0 until root.childCount) {
            applyThemeText(root.getChildAt(index), backgroundColor)
        }
    }
}
