package com.example.nelexiumlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

private fun Context.moduleText(
    size: Float,
    color: Int = Color.WHITE,
    bold: Boolean = false,
    marquee: Boolean = false
) = TextView(this).apply {
    textSize = size
    includeFontPadding = false
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
    setStroke(dp(1) + 1, moduleBorderColor(night, palette))
}

private fun Context.elementBackground(night: Boolean = false, palette: ThemePreset? = null) = GradientDrawable().apply {
    setColor(palette?.backgroundTint ?: Color.argb(115, 9, 9, 11))
    setStroke(dp(1) + 1, moduleBorderColor(night, palette))
}

class SongModuleView(context: Context) : LinearLayout(context) {
    private val moduleLabel = context.moduleText(16f, Color.rgb(180, 185, 195), true)
    private val artwork = ImageView(context)
    private val title = context.moduleText(42f, Color.WHITE, true, true)
    private val artist = context.moduleText(29f, Color.rgb(216, 218, 223), marquee = true)
    private val album = context.moduleText(23f, Color.rgb(170, 176, 187), marquee = true)
    private val playback = context.moduleText(17f, Color.rgb(174, 180, 192), true)
    private val elapsed = context.moduleText(24f, Color.WHITE, true)
    private val duration = context.moduleText(24f, Color.WHITE, true)
    private val progress = SeekBar(context)
    private val songRow = LinearLayout(context)
    private val info = LinearLayout(context)
    private val progressRow = LinearLayout(context)
    private var hasArtwork = false
    private var durationMs = 0L
    private var userSeeking = false
    private var seekHoldUntil = 0L
    private var requestedPosition = 0L
    var onSeekRequested: ((Long) -> Unit)? = null

    init {
        orientation = VERTICAL
        background = context.panelBackground()

        songRow.orientation = HORIZONTAL
        songRow.gravity = Gravity.CENTER_VERTICAL
        artwork.scaleType = ImageView.ScaleType.CENTER_CROP

        info.orientation = VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        moduleLabel.text = "NOW PLAYING"
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

        progress.max = 1000
        progress.isEnabled = false
        progress.splitTrack = false
        progress.contentDescription = "Song position"
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser && durationMs > 0L) {
                    elapsed.text = formatMediaTime((durationMs.toDouble() * value / seekBar.max).toLong())
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
                if (durationMs > 0L && seekBar.isEnabled) {
                    requestedPosition = (durationMs.toDouble() * seekBar.progress / seekBar.max).toLong()
                    seekHoldUntil = SystemClock.elapsedRealtime() + 1500L
                    onSeekRequested?.invoke(requestedPosition)
                }
            }
        })

        progressRow.gravity = Gravity.CENTER_VERTICAL
        elapsed.gravity = Gravity.CENTER
        duration.gravity = Gravity.CENTER
        progressRow.addView(elapsed, LayoutParams(context.dp(82), context.dp(46)))
        progressRow.addView(progress, LayoutParams(0, context.dp(46), 1f))
        progressRow.addView(duration, LayoutParams(context.dp(82), context.dp(46)))

        addView(songRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(progressRow, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(46)))
        setData("No track", "—", "")
        setState("CONNECTING TO BLUETOOTH…")
        setProgress(0, 0)
        setCompact(false)
    }

    fun setData(trackTitle: String, trackArtist: String, trackAlbum: String) {
        seekHoldUntil = 0L
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

    fun setProgress(position: Long, total: Long, canSeek: Boolean = false) {
        durationMs = total.coerceAtLeast(0L)
        duration.text = if (durationMs > 0L) formatMediaTime(durationMs) else "--:--"
        progress.isEnabled = canSeek && durationMs > 0L
        if (userSeeking) return
        if (SystemClock.elapsedRealtime() < seekHoldUntil &&
            abs(position - requestedPosition) > 2000L) return
        seekHoldUntil = 0L
        elapsed.text = formatMediaTime(position.coerceAtLeast(0L))
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
        title.textSize = if (compact) 24f else 42f
        artist.textSize = if (compact) 20f else 29f
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
        applyThemeText(this, palette?.backgroundTint ?: Color.rgb(13, 13, 15))
        val backgroundColor = palette?.backgroundTint ?: Color.rgb(13, 13, 15)
        val foreground = if (isBrightBackground(backgroundColor)) Color.BLACK else Color.WHITE
        val track = Color.argb(80, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        progress.progressDrawable = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(track) },
            ClipDrawable(GradientDrawable().apply { setColor(foreground) }, Gravity.START, ClipDrawable.HORIZONTAL)
        )).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerHeight(0, context.dp(7))
            setLayerHeight(1, context.dp(7))
            setLayerGravity(0, Gravity.CENTER_VERTICAL)
            setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
        progress.thumb = GradientDrawable().apply {
            setColor(foreground)
            setStroke(context.dp(1) + 1, palette?.lineColor ?: foreground)
            setSize(context.dp(14), context.dp(22))
        }
        progress.thumbTintList = null
    }
}

class SpeedModuleView(context: Context) : LinearLayout(context) {
    private val label = context.moduleText(18f, Color.rgb(180, 185, 195), true)
    private val currentSpeed = context.moduleText(92f, Color.WHITE, true)
    private val unit = context.moduleText(23f, Color.rgb(180, 185, 195))
    private val averageValue = context.moduleText(28f, Color.WHITE, true)
    private val topValue = context.moduleText(28f, Color.WHITE, true)
    private val averageBlock = statBlock("AVERAGE", averageValue)
    private val topBlock = statBlock("TOP", topValue)
    private val statsRow = LinearLayout(context)
    private val gaugeArea = FrameLayout(context)
    private val speedReadout = LinearLayout(context)
    private val speedBar = HorizontalSpeedBarView(context)
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
            speedBar.value = displayedSpeed

            val remaining = abs(targetSpeed - displayedSpeed)
            val delay = if (remaining == 0) 0L else (240L / remaining).coerceIn(4L, 20L)
            speedHandler.postDelayed(this, delay)
        }
    }

    init {
        orientation = VERTICAL
        background = context.panelBackground()
        label.text = "GPS SPEED"
        label.gravity = Gravity.CENTER
        currentSpeed.text = "0"
        unit.text = "km/h"

        statsRow.orientation = HORIZONTAL
        statsRow.addView(averageBlock, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = context.dp(4)
        })
        statsRow.addView(topBlock, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = context.dp(4)
        })

        speedReadout.gravity = Gravity.CENTER
        speedReadout.addView(currentSpeed)
        speedReadout.addView(unit)
        gaugeArea.addView(speedBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, context.dp(130), Gravity.BOTTOM
        ).apply { bottomMargin = context.dp(12) })
        gaugeArea.addView(speedReadout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statsRow, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(64)).apply {
            topMargin = context.dp(4)
            bottomMargin = context.dp(4)
        })
        addView(gaugeArea, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        update(0, 0, 0)
        setCompact(true)
    }

    private fun statBlock(title: String, value: TextView) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = context.elementBackground()
        setPadding(0, context.dp(5), 0, context.dp(5))
        addView(context.moduleText(17f, Color.rgb(180, 185, 195), true).apply {
            text = title
            gravity = Gravity.CENTER
        })
        value.gravity = Gravity.CENTER
        addView(value)
    }

    fun update(current: Int, average: Int, top: Int) {
        targetSpeed = current.coerceAtLeast(0)
        averageValue.text = "$average km/h"
        topValue.text = "$top km/h"
        val peak = maxOf(targetSpeed, top)
        speedBar.maxSpeed = maxOf(180, ((peak + 59) / 60) * 60)
        speedBar.topSpeed = peak
        if (displayedSpeed == targetSpeed) {
            currentSpeed.text = displayedSpeed.toString()
            speedBar.value = displayedSpeed
        }
        if (!speedAnimationRunning && displayedSpeed != targetSpeed) {
            speedAnimationRunning = true
            speedHandler.post(speedStep)
        }
    }

    fun setCompact(compact: Boolean) {
        alpha = 1f
        statsRow.visibility = if (compact) GONE else VISIBLE
        speedBar.visibility = if (compact) GONE else VISIBLE
        label.visibility = if (compact) VISIBLE else GONE
        gravity = if (compact) Gravity.CENTER else Gravity.TOP
        label.textSize = 18f
        currentSpeed.textSize = if (compact) 68f else 108f
        unit.textSize = 23f
        speedReadout.orientation = if (compact) VERTICAL else HORIZONTAL
        speedReadout.gravity = if (compact) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        speedReadout.setPadding(0, 0, 0, 0)
        gaugeArea.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,
            if (compact) LayoutParams.WRAP_CONTENT else 0, if (compact) 0f else 1f)
        speedReadout.layoutParams = FrameLayout.LayoutParams(
            if (compact) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            if (compact) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        ).apply { if (!compact) marginStart = context.dp(16) }
        currentSpeed.gravity = Gravity.CENTER
        unit.gravity = Gravity.CENTER
        currentSpeed.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        unit.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = if (compact) 0 else context.dp(12)
        }
        setPadding(context.dp(14), context.dp(if (compact) 12 else 6),
            context.dp(14), context.dp(if (compact) 12 else 6))
    }

    fun setNightMode(night: Boolean, palette: ThemePreset? = null) {
        background = context.panelBackground(night, palette)
        applyThemeText(this, palette?.backgroundTint ?: Color.rgb(13, 13, 15))
        averageBlock.background = context.elementBackground(night, palette)
        topBlock.background = context.elementBackground(night, palette)
        speedBar.theme = palette
    }

    override fun onDetachedFromWindow() {
        speedHandler.removeCallbacks(speedStep)
        speedAnimationRunning = false
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        currentSpeed.text = displayedSpeed.toString()
        speedBar.value = displayedSpeed
        if (!speedAnimationRunning && displayedSpeed != targetSpeed) {
            speedAnimationRunning = true
            speedHandler.post(speedStep)
        }
    }
}

private class HorizontalSpeedBarView(context: Context) : View(context) {
    var value: Int = 0
        set(newValue) {
            field = newValue
            invalidate()
        }
    var theme: ThemePreset? = null
        set(newTheme) {
            field = newTheme
            invalidate()
        }
    var maxSpeed: Int = 180
        set(newMaxSpeed) {
            field = newMaxSpeed
            invalidate()
        }
    var topSpeed: Int = 0
        set(newTopSpeed) {
            field = newTopSpeed
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = context.dp(24).toFloat()
        val right = width - left
        if (right <= left) return
        val baselineY = height - context.dp(30).toFloat()
        val maximumHeight = context.dp(94).toFloat()
        val foreground = if (isBrightBackground(theme?.backgroundTint ?: Color.BLACK)) Color.BLACK else Color.WHITE
        val inactive = Color.argb(90, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        val fraction = value.coerceIn(0, maxSpeed) / maxSpeed.toFloat()
        val filledUntil = left + (right - left) * fraction
        val ramp = android.graphics.Path().apply {
            moveTo(left, baselineY)
            lineTo(right, baselineY - maximumHeight)
            lineTo(right, baselineY)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = inactive
        canvas.drawPath(ramp, paint)
        canvas.save()
        canvas.clipRect(left, 0f, filledUntil, baselineY + 1f)
        paint.color = foreground
        canvas.drawPath(ramp, paint)
        canvas.restore()

        if (topSpeed > 0) {
            val topFraction = (topSpeed / maxSpeed.toFloat()).coerceIn(0f, 1f)
            val markerX = left + (right - left) * topFraction
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(2) + 1f
            paint.color = theme?.lineColor ?: foreground
            canvas.drawLine(markerX, baselineY,
                markerX, baselineY - maximumHeight * topFraction - context.dp(5), paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = foreground
        paint.typeface = context.nelexiumFont(true)
        paint.textSize = context.dp(20).toFloat()
        paint.textAlign = Paint.Align.CENTER
        val baseline = height - context.dp(3).toFloat()
        for (tick in 0..3) {
            val x = left + (right - left) * tick / 3f
            canvas.drawText((tick * maxSpeed / 3).toString(), x, baseline, paint)
        }
    }
}

class TripModuleView(context: Context) : FrameLayout(context) {
    private val focusedContent = LinearLayout(context)
    private val compactContent = LinearLayout(context)
    private val compactDistance = context.moduleText(36f, Color.WHITE, true)
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
        compactContent.addView(context.moduleText(17f, Color.rgb(180, 185, 195), true).apply {
            text = "TRIP"
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
        addView(context.moduleText(18f, Color.rgb(180, 185, 195), true).apply {
            text = label
            gravity = Gravity.CENTER
        })
        addView(context.moduleText(41f, Color.WHITE, true).apply {
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
        applyThemeText(this, palette?.backgroundTint ?: Color.rgb(13, 13, 15))
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
        paint.strokeWidth = context.dp(1) + 1f
        paint.color = theme?.lineColor ?: if (nightMode) Color.rgb(190, 28, 38) else Color.rgb(92, 94, 102)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = context.nelexiumFont(true)
        paint.textSize = context.dp(14).toFloat()
        paint.color = if (isBrightBackground(theme?.backgroundTint ?: Color.BLACK)) Color.BLACK else Color.rgb(185, 188, 197)
        canvas.drawText("N", cx, cy - radius + context.dp(12), paint)
        canvas.drawText("E", cx + radius - context.dp(9), cy + context.dp(4), paint)
        canvas.drawText("S", cx, cy + radius - context.dp(3), paint)
        canvas.drawText("W", cx - radius + context.dp(9), cy + context.dp(4), paint)

        canvas.save()
        // Location.bearing is clockwise from north. The compass graphic is
        // rendered in the opposite screen-space direction, so invert it here
        // to keep the needle aligned with the displayed heading.
        canvas.rotate(-bearing, cx, cy)
        paint.color = if (isBrightBackground(theme?.backgroundTint ?: Color.BLACK)) Color.BLACK else Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = context.dp(2) + 1f
        canvas.drawLine(cx, cy + radius * 0.28f, cx, cy - radius * 0.58f, paint)
        canvas.drawLine(cx, cy - radius * 0.58f, cx - context.dp(4), cy - radius * 0.45f, paint)
        canvas.drawLine(cx, cy - radius * 0.58f, cx + context.dp(4), cy - radius * 0.45f, paint)
        canvas.restore()

        paint.textSize = context.dp(24).toFloat()
        val baseline = cy - (paint.ascent() + paint.descent()) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = context.dp(3) + 1f
        paint.color = theme?.lineColor ?: if (nightMode) Color.rgb(190, 28, 38) else Color.rgb(18, 18, 21)
        canvas.drawText(heading, cx, baseline, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isBrightBackground(theme?.backgroundTint ?: Color.BLACK)) Color.BLACK else Color.WHITE
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
