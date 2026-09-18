package com.example.nelexiumlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Context.density get() = resources.displayMetrics.density
private fun Context.dp(value: Int) = (value * density).roundToInt()

private fun Context.nelexiumFont(bold: Boolean = false): Typeface {
    val resource = resources.getIdentifier(
        if (bold) "space_grotesk_bold" else "space_grotesk_regular",
        "font",
        packageName
    )
    return try {
        if (resource != 0 && android.os.Build.VERSION.SDK_INT >= 26) resources.getFont(resource)
        else Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    } catch (_: Exception) {
        Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}

private fun Context.moduleText(
    size: Float,
    color: Int = Color.WHITE,
    bold: Boolean = false,
    marquee: Boolean = false
) = TextView(this).apply {
    textSize = size
    setTextColor(color)
    typeface = nelexiumFont(bold)
    maxLines = 1
    if (marquee) {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSelected = true
        isSingleLine = true
    }
}

private fun moduleBorderColor(night: Boolean, palette: ThemePreset? = null) =
    palette?.lineColor ?: if (night) Color.rgb(190, 28, 38) else Color.argb(190, 70, 70, 76)

private fun Context.panelBackground(night: Boolean = false, palette: ThemePreset? = null) = GradientDrawable().apply {
    setColor(palette?.backgroundTint ?: Color.argb(165, 13, 13, 15))
    cornerRadius = dp(3).toFloat()
    setStroke(dp(1), moduleBorderColor(night, palette))
}

private fun Context.elementBackground(night: Boolean = false, palette: ThemePreset? = null) = GradientDrawable().apply {
    setColor(palette?.backgroundTint ?: Color.argb(115, 9, 9, 11))
    cornerRadius = dp(3).toFloat()
    setStroke(dp(1), moduleBorderColor(night, palette))
}

class SongModuleView(context: Context) : LinearLayout(context) {
    private val moduleLabel = context.moduleText(13f, Color.rgb(180, 185, 195), true)
    private val artwork = ImageView(context)
    private val title = context.moduleText(31f, Color.WHITE, true, true)
    private val artist = context.moduleText(21f, Color.rgb(216, 218, 223), marquee = true)
    private val album = context.moduleText(17f, Color.rgb(170, 176, 187), marquee = true)
    private val playback = context.moduleText(13f, Color.rgb(174, 180, 192), true)
    private val elapsed = context.moduleText(18f, Color.WHITE, true)
    private val duration = context.moduleText(18f, Color.WHITE, true)
    private val progress = SeekBar(context)
    private val songRow = LinearLayout(context)
    private val info = LinearLayout(context)
    private val progressRow = LinearLayout(context)
    private var hasArtwork = false

    init {
        orientation = VERTICAL
        background = context.panelBackground()

        songRow.orientation = HORIZONTAL
        songRow.gravity = Gravity.CENTER_VERTICAL
        artwork.scaleType = ImageView.ScaleType.CENTER_CROP

        info.orientation = VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        moduleLabel.text = "NOW PLAYING"
        moduleLabel.letterSpacing = 0.1f
        info.addView(moduleLabel)
        info.addView(playback)
        info.addView(spacer(context, 12))
        info.addView(title)
        info.addView(spacer(context, 6))
        info.addView(artist)
        info.addView(spacer(context, 4))
        info.addView(album)

        songRow.addView(artwork, LayoutParams(context.dp(230), context.dp(230)).apply {
            marginEnd = context.dp(20)
        })
        songRow.addView(info, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        progressRow.gravity = Gravity.CENTER_VERTICAL
        progress.max = 1000
        progress.isEnabled = false
        progress.splitTrack = false
        elapsed.gravity = Gravity.CENTER
        duration.gravity = Gravity.CENTER
        progressRow.addView(elapsed, LayoutParams(context.dp(72), context.dp(42)))
        progressRow.addView(progress, LayoutParams(0, context.dp(42), 1f))
        progressRow.addView(duration, LayoutParams(context.dp(72), context.dp(42)))

        addView(songRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(progressRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setData("No track", "—", "")
        setState("CONNECTING TO BLUETOOTH…")
        setProgress(0, 0)
        setCompact(false)
    }

    fun setData(trackTitle: String, trackArtist: String, trackAlbum: String) {
        title.text = trackTitle.ifBlank { "No track" }
        artist.text = trackArtist.ifBlank { "—" }
        album.text = trackAlbum
    }

    fun setState(value: String) {
        playback.text = value
    }

    fun setPlaying(playing: Boolean) {
        playback.text = if (playing) "PLAYING" else "PAUSED"
    }

    fun setArtwork(bitmap: android.graphics.Bitmap?) {
        hasArtwork = bitmap != null
        artwork.setImageBitmap(bitmap)
        if (playback.visibility == VISIBLE) artwork.visibility = if (hasArtwork) VISIBLE else GONE
    }

    fun setProgress(position: Long, total: Long) {
        elapsed.text = formatMediaTime(position)
        duration.text = formatMediaTime(total)
        progress.progress = if (total > 0L) {
            ((position.coerceIn(0L, total) * 1000L) / total).toInt()
        } else 0
    }

    fun setCompact(compact: Boolean) {
        alpha = 1f
        moduleLabel.visibility = if (compact) VISIBLE else GONE
        artwork.visibility = if (!compact && hasArtwork) VISIBLE else GONE
        playback.visibility = if (compact) GONE else VISIBLE
        album.visibility = if (compact) GONE else VISIBLE
        progressRow.visibility = if (compact) GONE else VISIBLE
        title.textSize = if (compact) 18f else 31f
        artist.textSize = if (compact) 15f else 21f
        info.gravity = if (compact) Gravity.CENTER else Gravity.CENTER_VERTICAL
        moduleLabel.gravity = if (compact) Gravity.CENTER else Gravity.START
        title.gravity = if (compact) Gravity.CENTER else Gravity.START
        artist.gravity = if (compact) Gravity.CENTER else Gravity.START
        setPadding(
            context.dp(if (compact) 14 else 18),
            context.dp(if (compact) 12 else 18),
            context.dp(if (compact) 14 else 18),
            context.dp(if (compact) 12 else 18)
        )
    }

    fun setNightMode(night: Boolean, palette: ThemePreset? = null) {
        background = context.panelBackground(night, palette)
    }
}

class SpeedModuleView(context: Context) : LinearLayout(context) {
    private val label = context.moduleText(14f, Color.rgb(180, 185, 195), true)
    private val currentSpeed = context.moduleText(76f, Color.WHITE, true)
    private val unit = context.moduleText(17f, Color.rgb(180, 185, 195))
    private val gauge = SpeedometerView(context)
    private val details = context.moduleText(16f, Color.rgb(216, 218, 223), true)
    private val speedRow = LinearLayout(context)
    private val speedHandler = Handler(Looper.getMainLooper())
    private var displayedSpeed = 0
    private var targetSpeed = 0
    private var speedAnimationRunning = false
    private val speedStep = object : Runnable {
        override fun run() {
            if (displayedSpeed == targetSpeed) {
                speedAnimationRunning = false
                return
            }

            displayedSpeed += if (targetSpeed > displayedSpeed) 1 else -1
            currentSpeed.text = displayedSpeed.toString()
            gauge.value = displayedSpeed
            gauge.invalidate()

            val remaining = abs(targetSpeed - displayedSpeed)
            val delay = if (remaining == 0) 0L else (240L / remaining).coerceIn(4L, 20L)
            speedHandler.postDelayed(this, delay)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = context.panelBackground()
        label.text = "GPS SPEED"
        label.letterSpacing = 0.12f
        currentSpeed.text = "0"
        unit.text = "km/h"
        speedRow.orientation = HORIZONTAL
        speedRow.gravity = Gravity.CENTER_VERTICAL
        speedRow.addView(currentSpeed, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        speedRow.addView(details, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(label)
        addView(speedRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(unit)
        addView(gauge, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(105)))
        update(0, 0, 0)
        setCompact(true)
    }

    fun update(current: Int, average: Int, top: Int) {
        targetSpeed = current.coerceAtLeast(0)
        details.text = "AVERAGE  $average km/h       TOP  $top km/h"
        if (displayedSpeed == targetSpeed) currentSpeed.text = displayedSpeed.toString()
        if (!speedAnimationRunning && displayedSpeed != targetSpeed) {
            speedAnimationRunning = true
            speedHandler.post(speedStep)
        }
    }

    fun setCompact(compact: Boolean) {
        alpha = 1f
        label.visibility = VISIBLE
        unit.visibility = VISIBLE
        gauge.visibility = if (compact) GONE else VISIBLE
        details.visibility = if (compact) GONE else VISIBLE
        currentSpeed.textSize = if (compact) 52f else 76f
        gravity = if (compact) Gravity.CENTER else Gravity.CENTER_VERTICAL
        label.gravity = if (compact) Gravity.CENTER else Gravity.START
        currentSpeed.gravity = if (compact) Gravity.CENTER else Gravity.START
        unit.gravity = if (compact) Gravity.CENTER else Gravity.START
        details.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        val focusedTextPadding = if (compact) 0 else context.dp(28)
        label.setPadding(focusedTextPadding, 0, 0, 0)
        currentSpeed.setPadding(focusedTextPadding, 0, 0, 0)
        unit.setPadding(focusedTextPadding, 0, 0, 0)
        details.setPadding(0, 0, focusedTextPadding, 0)
        listOf(label, unit).forEach {
            it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
    }

    fun setNightMode(night: Boolean, palette: ThemePreset? = null) {
        background = context.panelBackground(night, palette)
        gauge.nightMode = night
        gauge.theme = palette
        gauge.invalidate()
    }

    override fun onDetachedFromWindow() {
        speedHandler.removeCallbacks(speedStep)
        speedAnimationRunning = false
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        currentSpeed.text = displayedSpeed.toString()
        if (!speedAnimationRunning && displayedSpeed != targetSpeed) {
            speedAnimationRunning = true
            speedHandler.post(speedStep)
        }
    }
}

private class SpeedometerView(context: Context) : View(context) {
    var value = 0
    var nightMode = false
    var theme: ThemePreset? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = context.dp(28).toFloat()
        val right = width - context.dp(28).toFloat()
        val trackY = height * 0.42f
        val progressX = left + (right - left) * value.coerceIn(0, 180) / 180f

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.SQUARE
        paint.strokeWidth = context.dp(8).toFloat()
        paint.color = theme?.lineColor?.let { Color.argb(150, Color.red(it), Color.green(it), Color.blue(it)) }
            ?: if (nightMode) Color.rgb(105, 20, 26) else Color.rgb(55, 56, 62)
        canvas.drawLine(left, trackY, right, trackY, paint)
        paint.color = if (value >= 140) Color.rgb(235, 70, 70) else Color.rgb(238, 238, 242)
        canvas.drawLine(left, trackY, progressX, trackY, paint)

        paint.strokeWidth = context.dp(2).toFloat()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = context.nelexiumFont(true)
        paint.textSize = context.dp(12).toFloat()
        for (tick in 0..6) {
            val x = left + (right - left) * tick / 6f
            paint.color = if (tick * 30 <= value) Color.WHITE else theme?.lineColor
                ?: if (nightMode) Color.rgb(180, 42, 50) else Color.rgb(110, 112, 120)
            canvas.drawLine(x, trackY - context.dp(9), x, trackY + context.dp(9), paint)
            if (tick % 2 == 0) {
                canvas.drawText((tick * 30).toString(), x, height - context.dp(8).toFloat(), paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.color = if (value >= 140) Color.rgb(235, 70, 70) else Color.WHITE
        val marker = android.graphics.Path().apply {
            moveTo(progressX, trackY - context.dp(14))
            lineTo(progressX - context.dp(7), trackY - context.dp(25))
            lineTo(progressX + context.dp(7), trackY - context.dp(25))
            close()
        }
        canvas.drawPath(marker, paint)
    }
}

class TripModuleView(context: Context) : FrameLayout(context) {
    private val focusedContent = LinearLayout(context)
    private val compactContent = LinearLayout(context)
    private val compactDistance = context.moduleText(27f, Color.WHITE, true)
    private val focusedDistance = statBlock("DISTANCE", "0.0 km")
    private val focusedTime = statBlock("TIME", "00:00:00")
    private val focusedAltitude = statBlock("ALTITUDE", "0 m")
    private val compass = CompassView(context)

    init {
        background = context.panelBackground()
        setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(14))

        focusedContent.orientation = LinearLayout.VERTICAL
        val topRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(focusedDistance, quadrantParams(endMargin = 6, bottomMargin = 6))
        topRow.addView(focusedTime, quadrantParams(startMargin = 6, bottomMargin = 6))
        bottomRow.addView(focusedAltitude, quadrantParams(endMargin = 6, topMargin = 6))
        bottomRow.addView(compass, quadrantParams(startMargin = 6, topMargin = 6))
        focusedContent.addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        focusedContent.addView(bottomRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(focusedContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        compactContent.orientation = LinearLayout.VERTICAL
        compactContent.gravity = Gravity.CENTER
        compactContent.addView(context.moduleText(13f, Color.rgb(180, 185, 195), true).apply {
            text = "TRIP"
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        compactDistance.gravity = Gravity.CENTER
        compactContent.addView(compactDistance)
        addView(compactContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        update(0.0, 0L, 0.0, "N", 0f)
        setCompact(true)
    }

    private fun statBlock(label: String, initial: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = context.elementBackground()
        addView(context.moduleText(13f, Color.rgb(180, 185, 195), true).apply {
            text = label
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
        })
        addView(context.moduleText(31f, Color.WHITE, true).apply {
            text = initial
            gravity = Gravity.CENTER
        })
    }

    private fun quadrantParams(
        startMargin: Int = 0,
        topMargin: Int = 0,
        endMargin: Int = 0,
        bottomMargin: Int = 0
    ) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
        this.marginStart = context.dp(startMargin)
        this.topMargin = context.dp(topMargin)
        this.marginEnd = context.dp(endMargin)
        this.bottomMargin = context.dp(bottomMargin)
    }

    fun update(
        distanceKm: Double,
        elapsedMs: Long,
        altitudeMeters: Double,
        heading: String,
        bearing: Float
    ) {
        val distanceValue = String.format(Locale.getDefault(), "%.1f km", distanceKm)
        compactDistance.text = distanceValue
        (focusedDistance.getChildAt(1) as TextView).text = distanceValue
        (focusedTime.getChildAt(1) as TextView).text = formatTripTime(elapsedMs)
        (focusedAltitude.getChildAt(1) as TextView).text = String.format(Locale.getDefault(), "%.0f m", altitudeMeters)
        compass.heading = heading
        compass.bearing = bearing
        compass.invalidate()
    }

    fun setCompact(compact: Boolean) {
        alpha = 1f
        focusedContent.visibility = if (compact) GONE else VISIBLE
        compactContent.visibility = if (compact) VISIBLE else GONE
    }

    fun setNightMode(night: Boolean, palette: ThemePreset? = null) {
        background = context.panelBackground(night, palette)
        focusedDistance.background = context.elementBackground(night, palette)
        focusedTime.background = context.elementBackground(night, palette)
        focusedAltitude.background = context.elementBackground(night, palette)
        compass.background = context.elementBackground(night, palette)
        compass.nightMode = night
        compass.theme = palette
        compass.invalidate()
    }
}

private class CompassView(context: Context) : View(context) {
    var heading = "N"
    var bearing = 0f
    var nightMode = false
    var theme: ThemePreset? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.37f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(1).toFloat()
        paint.color = theme?.lineColor ?: if (nightMode) Color.rgb(190, 28, 38) else Color.rgb(92, 94, 102)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = context.nelexiumFont(true)
        paint.textSize = context.dp(11).toFloat()
        paint.color = Color.rgb(185, 188, 197)
        canvas.drawText("N", cx, cy - radius + context.dp(12), paint)
        canvas.drawText("E", cx + radius - context.dp(9), cy + context.dp(4), paint)
        canvas.drawText("S", cx, cy + radius - context.dp(3), paint)
        canvas.drawText("W", cx - radius + context.dp(9), cy + context.dp(4), paint)

        canvas.save()
        canvas.rotate(bearing, cx, cy)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = context.dp(2).toFloat()
        canvas.drawLine(cx, cy + radius * 0.28f, cx, cy - radius * 0.58f, paint)
        canvas.drawLine(cx, cy - radius * 0.58f, cx - context.dp(4), cy - radius * 0.45f, paint)
        canvas.drawLine(cx, cy - radius * 0.58f, cx + context.dp(4), cy - radius * 0.45f, paint)
        canvas.restore()

        paint.textSize = context.dp(18).toFloat()
        val baseline = cy - (paint.ascent() + paint.descent()) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = context.dp(3).toFloat()
        paint.color = theme?.lineColor ?: if (nightMode) Color.rgb(190, 28, 38) else Color.rgb(18, 18, 21)
        canvas.drawText(heading, cx, baseline, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawText(heading, cx, baseline, paint)
    }
}

private fun spacer(context: Context, height: Int) = View(context).apply {
    layoutParams = LinearLayout.LayoutParams(1, context.dp(height))
}

private fun formatMediaTime(ms: Long): String = String.format(
    Locale.getDefault(),
    "%d:%02d",
    ms.coerceAtLeast(0L) / 60_000L,
    (ms.coerceAtLeast(0L) / 1_000L) % 60L
)

private fun formatTripTime(ms: Long): String = String.format(
    Locale.getDefault(),
    "%02d:%02d:%02d",
    ms.coerceAtLeast(0L) / 3_600_000L,
    (ms.coerceAtLeast(0L) / 60_000L) % 60L,
    (ms.coerceAtLeast(0L) / 1_000L) % 60L
)
