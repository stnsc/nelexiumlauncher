package com.example.nelexiumlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

internal fun blurredSnapshot(view: View): Bitmap {
        // Blur a small snapshot in software so Android 6+ gets the same backdrop.
        val width = (view.width / 6).coerceAtLeast(1)
        val height = (view.height / 6).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap).apply {
            scale(width.toFloat() / view.width.coerceAtLeast(1), height.toFloat() / view.height.coerceAtLeast(1))
        })
        var pixels = IntArray(width * height)
        var scratch = IntArray(pixels.size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        // Three separable box passes approximate a Gaussian blur.
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                val length = if (horizontal) width else height
                val lines = if (horizontal) height else width
                for (line in 0 until lines) {
                    fun index(position: Int) = if (horizontal) line * width + position else position * width + line
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (offset in -3..3) {
                        val color = pixels[index(offset.coerceIn(0, length - 1))]
                        red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
                    }
                    for (position in 0 until length) {
                        scratch[index(position)] = Color.rgb(red / 7, green / 7, blue / 7)
                        val leaving = pixels[index((position - 3).coerceIn(0, length - 1))]
                        val entering = pixels[index((position + 4).coerceIn(0, length - 1))]
                        red += Color.red(entering) - Color.red(leaving)
                        green += Color.green(entering) - Color.green(leaving)
                        blue += Color.blue(entering) - Color.blue(leaving)
                    }
                }
                val previous = pixels
                pixels = scratch
                scratch = previous
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
