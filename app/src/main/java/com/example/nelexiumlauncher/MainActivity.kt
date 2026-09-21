package com.example.nelexiumlauncher

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.Calendar
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity(), android.location.LocationListener {
    private lateinit var rootFrame: FrameLayout
    private lateinit var backgroundArt: ImageView
    private lateinit var artworkScrim: View
    private lateinit var root: LinearLayout
    private lateinit var focusHost: FrameLayout
    private lateinit var compactHost: LinearLayout
    private lateinit var song: SongModuleView
    private lateinit var speed: SpeedModuleView
    private lateinit var trip: TripModuleView
    private lateinit var bluetoothStatus: TextView
    private lateinit var wifiStatus: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var themeButton: Button
    private lateinit var updateButton: Button
    private var updateDialog: android.app.AlertDialog? = null
    private val updateManager by lazy { UpdateManager.get(this) }
    private lateinit var dashboardHost: FrameLayout
    private lateinit var playPauseButton: Button
    private val dividerViews = mutableListOf<View>()
    private val borderedControls = mutableListOf<View>()
    private var nightBorders: Boolean? = null
    private val themePrefs by lazy { getSharedPreferences("theme_presets", Context.MODE_PRIVATE) }
    private var themePresets = ThemePresets.decode(null)
    private var lightPresetIndex = 0
    private var darkPresetIndex = 1
    private var previewTheme: ThemePreset? = null
    private var themeEditor: ThemeEditorView? = null
    private var lastGpsFixAt = 0L

    private var focusedIndex = 0
    private val compactOrder = mutableListOf(1, 2)
    private lateinit var modules: List<View>
    private var mediaBrowser: MediaBrowser? = null
    private var mediaController: MediaController? = null
    private var mediaConnected = false
    private var currentState: PlaybackState? = null
    private var durationMs = 0L
    private var currentTrackKey = ""
    private var locationManager: android.location.LocationManager? = null
    private var lastLocation: android.location.Location? = null
    private val tripClock = TripClock()
    private var tripDistanceMeters = 0.0
    private var averageSpeedTotal = 0.0
    private var averageSpeedSamples = 0
    private var topSpeed = 0
    private var currentSpeed = 0
    private var latestAltitude = 0.0
    private var latestBearing = 0f
    private val artworkExecutor = Executors.newFixedThreadPool(2)

    private val handler = Handler(Looper.getMainLooper())
    private val uiTicker = object : Runnable {
        override fun run() {
            updateProgress()
            updateConnectionIndicators()
            updateTripElapsed()
            updateNightStyling()
            if (hasWindowFocus()) updateManager.tick()
            updateButton.text = if (updateManager.ready != null) "↓ Update ready" else "⚙ Settings"
            handler.postDelayed(this, 1000)
        }
    }

    private val mediaConnection = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            mediaConnected = true
            mediaBrowser?.sessionToken?.let {
                mediaController = MediaController(this@MainActivity, it)
                mediaController?.registerCallback(mediaCallback)
                updateMetadata(mediaController?.metadata)
                updatePlayback(mediaController?.playbackState)
            }
        }
        override fun onConnectionFailed() { mediaConnected = false; song.setState("BLUETOOTH MEDIA UNAVAILABLE") }
    }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = updatePlayback(state)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        hideSystemBars()
        themePresets = ThemePresets.decode(themePrefs.getString("presets", null))
        if (themePresets.isEmpty()) themePresets = ThemePresets.defaults.toMutableList()
        val presetMaxIndex = themePresets.lastIndex
        lightPresetIndex = themePrefs.getInt("light_index", 0).coerceIn(0, presetMaxIndex)
        darkPresetIndex = themePrefs.getInt("dark_index", minOf(1, presetMaxIndex)).coerceIn(0, presetMaxIndex)
        buildUi()
        hideSystemBars()
        connectMedia()
        locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        startLocationUpdates()
        handler.post(uiTicker)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun buildUi() {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(currentTheme().backgroundTint)
        }
        backgroundArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(currentTheme().backgroundTint)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP))
            }
        }
        rootFrame.addView(backgroundArt, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        artworkScrim = View(this)
        rootFrame.addView(artworkScrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(buildTopBar(), LinearLayout.LayoutParams.MATCH_PARENT, dp(82))
        root.addView(divider())

        song = SongModuleView(this)
        song.onSeekRequested = { position ->
            val state = currentState
            if (state != null && (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L) {
                mediaController?.transportControls?.seekTo(position)
            }
        }
        speed = SpeedModuleView(this)
        trip = TripModuleView(this)
        modules = listOf(song, speed, trip)

        val moduleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, dp(12)) }
        focusHost = FrameLayout(this)
        compactHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        moduleRow.addView(focusHost, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.1f))
        moduleRow.addView(compactHost, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f))
        dashboardHost = FrameLayout(this)
        dashboardHost.addView(moduleRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(dashboardHost, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply { weight = 1f })
        showFocusedModule(0, false)

        root.addView(divider())
        root.addView(buildBottomBar(), LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
        rootFrame.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(rootFrame)
        applyTheme()
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(control("◀◀") { mediaController?.transportControls?.skipToPrevious() })
        playPauseButton = control("▶") { if (currentState?.state == PlaybackState.STATE_PLAYING) mediaController?.transportControls?.pause() else mediaController?.transportControls?.play() }
        controls.addView(playPauseButton)
        controls.addView(control("▶▶") { mediaController?.transportControls?.skipToNext() })
        bar.addView(controls, LinearLayout.LayoutParams(0, dp(70), 1f))
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val time = TextView(this).apply { textSize = 31f; setTextColor(android.graphics.Color.WHITE); gravity = Gravity.END; typeface = nelexiumFont(true) }
        val date = TextView(this).apply { textSize = 18f; setTextColor(android.graphics.Color.LTGRAY); gravity = Gravity.END; typeface = nelexiumFont(true) }
        val clockUpdate = object : Runnable { override fun run() { val now = java.util.Date(); time.text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(now); date.text = java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now); handler.postDelayed(this, 1000) } }
        handler.post(clockUpdate)
        clock.addView(time); clock.addView(date); bar.addView(clock, LinearLayout.LayoutParams(dp(190), dp(70)))
        return bar
    }

    private fun buildBottomBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bluetoothStatus = statusText("BT --", R.drawable.ic_bluetooth_status)
        wifiStatus = statusText("Wi-Fi --", R.drawable.ic_wifi_status)
        gpsStatus = statusText("GPS --", R.drawable.ic_gps_status)
        bar.addView(bluetoothStatus); bar.addView(wifiStatus); bar.addView(gpsStatus)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        themeButton = button("◉  Themes") { toggleThemeEditor() }
        bar.addView(themeButton)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(button("⌖  Maps") { openMaps() }); bar.addView(button("▦  Apps") { showAppDrawer() })
        updateButton = button("⚙ Settings") {
            android.app.AlertDialog.Builder(this).setTitle("Settings")
                .setItems(arrayOf("App updates", "Android settings")) { _, which ->
                    if (which == 0) {
                        if (updateDialog?.isShowing != true) updateDialog = UpdateDialog.show(this)
                    } else startActivity(Intent(Settings.ACTION_SETTINGS))
                }.setNegativeButton("Close", null).show()
        }
        bar.addView(updateButton)
        return bar
    }

    private fun showFocusedModule(index: Int, animate: Boolean) {
        val firstLayout = focusHost.childCount == 0
        if (!firstLayout && index == focusedIndex) return

        val outgoingIndex = focusedIndex
        if (!firstLayout) {
            val tappedSlot = compactOrder.indexOf(index)
            if (tappedSlot < 0) return
            compactOrder[tappedSlot] = outgoingIndex
        }
        focusedIndex = index

        modules.forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.translationX = 0f
            it.scaleX = 1f
            it.scaleY = 1f
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        focusHost.removeAllViews()
        compactHost.removeAllViews()

        val focused = modules[focusedIndex]
        setCompact(focused, false)
        focused.setOnClickListener(null)
        focusHost.addView(focused, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        compactOrder.forEachIndexed { slot, moduleIndex ->
            val view = modules[moduleIndex]
            setCompact(view, true)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                if (slot == 0) bottomMargin = dp(6) else topMargin = dp(6)
            }
            compactHost.addView(view, params)
            view.setOnClickListener { showFocusedModule(moduleIndex, true) }
        }

        if (animate) {
            focused.alpha = 0f
            focused.translationX = dp(48).toFloat()
            focused.scaleX = 0.98f
            focused.scaleY = 0.98f
            focused.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(300).start()

            val outgoing = modules[outgoingIndex]
            outgoing.alpha = 0f
            outgoing.translationX = -dp(28).toFloat()
            outgoing.animate().alpha(1f).translationX(0f).setStartDelay(80).setDuration(240).start()
        }
    }
    private fun setCompact(view: View, compact: Boolean) { when (view) { is SongModuleView -> view.setCompact(compact); is SpeedModuleView -> view.setCompact(compact); is TripModuleView -> view.setCompact(compact) } }

    private fun connectMedia() {
        mediaBrowser = MediaBrowser(this, ComponentName("com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService"), mediaConnection, null)
        try { mediaBrowser?.connect() } catch (_: Exception) { mediaConnected = false; song.setState("BLUETOOTH MEDIA UNAVAILABLE") }
    }
    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata == null) {
            currentTrackKey = ""
            durationMs = 0L
            song.setData("No track", "—", "")
            song.setProgress(0L, 0L)
            applyArtwork(null)
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.description?.title?.toString()
            ?: "Unknown title"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.description?.subtitle?.toString()
            ?: "Unknown artist"
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val trackKey = "$artist|$title|$album"

        song.setData(title, artist, album)
        durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        updateProgress()

        val embeddedArtwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        currentTrackKey = trackKey
        if (embeddedArtwork != null) {
            applyArtwork(embeddedArtwork)
            artworkExecutor.execute { saveArtworkToCache(trackKey, embeddedArtwork) }
            return
        }

        applyArtwork(null)
        artworkExecutor.execute {
            val cached = loadArtworkFromCache(trackKey)
            if (cached != null) {
                showArtworkIfCurrent(trackKey, cached)
            } else {
                fetchArtworkOnline(artist, title, album, trackKey)
            }
        }
    }

    private fun applyArtwork(bitmap: Bitmap?) {
        song.setArtwork(bitmap)
        if (bitmap == null) {
            backgroundArt.setImageDrawable(null)
            backgroundArt.setBackgroundColor(android.graphics.Color.rgb(8, 8, 9))
            return
        }

        backgroundArt.setImageBitmap(bitmap)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            val expectedTrack = currentTrackKey
            artworkExecutor.execute {
                val blurred = try { blurArtwork(bitmap) } catch (_: Exception) { bitmap }
                runOnUiThread {
                    if (currentTrackKey == expectedTrack) backgroundArt.setImageBitmap(blurred)
                }
            }
        }
    }

    private fun showArtworkIfCurrent(trackKey: String, bitmap: Bitmap) {
        runOnUiThread {
            if (currentTrackKey == trackKey) applyArtwork(bitmap)
        }
    }

    private fun artworkDirectory() = File(filesDir, "artwork").apply {
        if (!exists()) mkdirs()
    }

    private fun artworkFile(trackKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(artworkDirectory(), "$digest.jpg")
    }

    private fun loadArtworkFromCache(trackKey: String): Bitmap? = try {
        artworkFile(trackKey).takeIf { it.exists() && it.length() > 0L }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    } catch (_: Exception) {
        null
    }

    private fun saveArtworkToCache(trackKey: String, bitmap: Bitmap) {
        try {
            FileOutputStream(artworkFile(trackKey)).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        } catch (_: Exception) {
        }
    }

    private fun fetchArtworkOnline(
        artist: String,
        title: String,
        album: String,
        trackKey: String
    ) {
        var searchConnection: HttpURLConnection? = null
        var imageConnection: HttpURLConnection? = null
        try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            searchConnection = URL(
                "https://itunes.apple.com/search?term=$query&entity=song&limit=10"
            ).openConnection() as HttpURLConnection
            searchConnection.connectTimeout = 7000
            searchConnection.readTimeout = 7000
            searchConnection.setRequestProperty("User-Agent", "NelexiumLauncher/1.0")
            val response = searchConnection.inputStream.bufferedReader().use { it.readText() }
            val results = org.json.JSONObject(response).optJSONArray("results") ?: return

            var artworkUrl: String? = null
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val resultArtist = item.optString("artistName")
                val resultTitle = item.optString("trackName")
                val resultAlbum = item.optString("collectionName")
                val artistMatches = resultArtist.contains(artist, true) || artist.contains(resultArtist, true)
                val titleMatches = resultTitle.contains(title, true) || title.contains(resultTitle, true)
                val albumMatches = album.isNotBlank() && (resultAlbum.contains(album, true) || album.contains(resultAlbum, true))
                if ((artistMatches && titleMatches) || (artistMatches && albumMatches)) {
                    artworkUrl = item.optString("artworkUrl100")
                    break
                }
            }
            if (artworkUrl.isNullOrBlank() && results.length() > 0) {
                artworkUrl = results.optJSONObject(0)?.optString("artworkUrl100")
            }
            if (artworkUrl.isNullOrBlank()) return

            val highResolutionUrl = artworkUrl
                .replace("100x100bb", "600x600bb")
                .replace("100x100", "600x600")
            imageConnection = URL(highResolutionUrl).openConnection() as HttpURLConnection
            imageConnection.connectTimeout = 7000
            imageConnection.readTimeout = 7000
            imageConnection.setRequestProperty("User-Agent", "NelexiumLauncher/1.0")
            val bitmap = imageConnection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return
            saveArtworkToCache(trackKey, bitmap)
            showArtworkIfCurrent(trackKey, bitmap)
        } catch (_: Exception) {
        } finally {
            searchConnection?.disconnect()
            imageConnection?.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun blurArtwork(source: Bitmap): Bitmap {
        val width = maxOf(1, source.width / 6)
        val height = maxOf(1, source.height / 6)
        val input = Bitmap.createScaledBitmap(source, width, height, true)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val renderScript = RenderScript.create(this)
        val inputAllocation = Allocation.createFromBitmap(renderScript, input)
        val outputAllocation = Allocation.createFromBitmap(renderScript, output)
        val blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        try {
            blur.setRadius(12f)
            blur.setInput(inputAllocation)
            blur.forEach(outputAllocation)
            outputAllocation.copyTo(output)
        } finally {
            inputAllocation.destroy()
            outputAllocation.destroy()
            blur.destroy()
            renderScript.destroy()
        }
        return output
    }

    private fun updatePlayback(state: PlaybackState?) { currentState = state; val playing = state?.state == PlaybackState.STATE_PLAYING; song.setPlaying(playing); playPauseButton.text = if (playing) "Ⅱ" else "▶"; updateProgress() }
    private fun updateProgress() {
        val state = currentState
        if (state == null) {
            song.setProgress(0L, durationMs)
            return
        }
        var position = state.position.coerceAtLeast(0L)
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        song.setProgress(position.coerceAtMost(durationMs), durationMs,
            (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L)
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001); return }
        try { locationManager?.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0f, this); locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) } } catch (_: Exception) { }
    }
    override fun onLocationChanged(location: android.location.Location) {
        val now = System.currentTimeMillis()
        lastGpsFixAt = now
        val speedKph = if (location.hasSpeed()) (location.speed * 3.6f).coerceAtLeast(0f) else 0f
        val current = speedKph.roundToInt()
        currentSpeed = current
        latestAltitude = if (location.hasAltitude()) location.altitude else latestAltitude
        latestBearing = if (location.hasBearing()) location.bearing else latestBearing

        val wasActive = tripClock.isActive
        val reset = tripClock.onSpeed(now, speedKph)
        if (reset) resetTripData()
        if (tripClock.isActive && (!wasActive || reset)) lastLocation = location

        if (tripClock.isActive) {
            if (current >= 2) lastLocation?.let { tripDistanceMeters = it.distanceTo(location).toDouble() }
            topSpeed = maxOf(topSpeed, current)
            if (current >= 5) {
                averageSpeedTotal += current
                averageSpeedSamples++
            }
        }

        speed.update(current, if (averageSpeedSamples == 0) 0 else (averageSpeedTotal / averageSpeedSamples).roundToInt(), topSpeed)
        trip.update(
            tripDistanceMeters / 1000.0,
            tripClock.elapsed(now),
            latestAltitude,
            direction(latestBearing),
            latestBearing
        )
    }
    private fun resetTripData() { tripDistanceMeters = 0.0; averageSpeedTotal = 0.0; averageSpeedSamples = 0; topSpeed = 0; lastLocation = null }
    private fun updateTripElapsed() {
        val now = System.currentTimeMillis()
        if (tripClock.tick(now)) {
            resetTripData()
            speed.update(currentSpeed, 0, 0)
        }
        trip.update(
            tripDistanceMeters / 1000.0,
            tripClock.elapsed(now),
            latestAltitude,
            direction(latestBearing),
            latestBearing
        )
    }
    private fun direction(bearing: Float): String { val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW"); return dirs[((bearing / 45f).roundToInt() + 8) % 8] }

    private fun updateConnectionIndicators() {
        val bluetoothColor = if (mediaConnected) android.graphics.Color.rgb(95, 220, 150) else android.graphics.Color.rgb(220, 100, 100)
        bluetoothStatus.text = "BT"
        bluetoothStatus.setTextColor(if (isBrightBackground(currentTheme().backgroundTint)) android.graphics.Color.BLACK else bluetoothColor)
        bluetoothStatus.compoundDrawableTintList = ColorStateList.valueOf(bluetoothColor)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val caps = cm.getNetworkCapabilities(cm.activeNetwork); val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiColor = if (wifi) android.graphics.Color.rgb(95, 220, 150) else android.graphics.Color.rgb(220, 100, 100)
        wifiStatus.text = "Wi-Fi"
        wifiStatus.setTextColor(if (isBrightBackground(currentTheme().backgroundTint)) android.graphics.Color.BLACK else wifiColor)
        wifiStatus.compoundDrawableTintList = ColorStateList.valueOf(wifiColor)
        val gpsEnabled = try { locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true } catch (_: Exception) { false }
        val gpsReady = gpsEnabled && System.currentTimeMillis() - lastGpsFixAt < 7_000L
        val gpsColor = if (gpsReady) android.graphics.Color.rgb(95, 220, 150) else android.graphics.Color.rgb(220, 100, 100)
        gpsStatus.text = "GPS"
        gpsStatus.setTextColor(if (isBrightBackground(currentTheme().backgroundTint)) android.graphics.Color.BLACK else gpsColor)
        gpsStatus.compoundDrawableTintList = ColorStateList.valueOf(gpsColor)
    }
    private fun currentTheme(): ThemePreset {
        if (themePresets.isEmpty()) return ThemePresets.defaults.first()
        val index = (if (isNightTime()) darkPresetIndex else lightPresetIndex).coerceIn(0, themePresets.lastIndex)
        return previewTheme ?: themePresets[index]
    }
    private fun isNightTime(): Boolean { val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return hour >= 20 || hour < 6 }
    private fun persistThemes() {
        themePrefs.edit()
            .putString("presets", ThemePresets.encode(themePresets))
            .putInt("light_index", lightPresetIndex)
            .putInt("dark_index", darkPresetIndex)
            .apply()
    }
    private fun applyTheme() {
        rootFrame.setBackgroundColor(currentTheme().backgroundTint)
        if (::backgroundArt.isInitialized) backgroundArt.setBackgroundColor(currentTheme().backgroundTint)
        artworkScrim.setBackgroundColor(if (isBrightBackground(currentTheme().backgroundTint))
            android.graphics.Color.argb(212, 255, 255, 255) else android.graphics.Color.argb(212, 0, 0, 0))
        nightBorders = null
        updateNightStyling()
        updateConnectionIndicators()
    }

    private fun toggleThemeEditor() {
        if (themeEditor != null) {
            closeThemeEditor()
            return
        }
        previewTheme = null
        val selected = currentTheme()
        themeEditor = ThemeEditorView(
            this,
            selected.backgroundTint,
            selected.lineColor,
            themePresets,
            lightPresetIndex,
            darkPresetIndex,
            onPreviewChanged = { background, line ->
                previewTheme = ThemePreset("Preview", background, line)
                applyTheme()
            },
            onSavePreset = { name, background, line ->
                themePresets.add(ThemePreset(name, background, line))
                persistThemes()
                themeEditor?.refreshPresets(themePresets, lightPresetIndex, darkPresetIndex)
                applyTheme()
            },
            onAssignPreset = { index, dark ->
                if (dark) darkPresetIndex = index else lightPresetIndex = index
                previewTheme = null
                persistThemes()
                applyTheme()
                themeEditor?.refreshPresets(themePresets, lightPresetIndex, darkPresetIndex)
            },
            onDeletePreset = { index ->
                if (themePresets.size > 1 && index in themePresets.indices) {
                    themePresets.removeAt(index)
                    fun shifted(selection: Int) = when {
                        selection > index -> selection - 1
                        selection == index -> selection.coerceAtMost(themePresets.lastIndex)
                        else -> selection
                    }
                    lightPresetIndex = shifted(lightPresetIndex)
                    darkPresetIndex = shifted(darkPresetIndex)
                    previewTheme = null
                    persistThemes()
                    applyTheme()
                    themeEditor?.refreshPresets(themePresets, lightPresetIndex, darkPresetIndex)
                }
            },
            onDashboard = { closeThemeEditor() }
        )
        dashboardHost.removeAllViews()
        dashboardHost.addView(themeEditor!!, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        themeEditor?.setThemeBackground(selected.backgroundTint, selected.lineColor)
        themeButton.text = "▣  Dashboard"
    }

    private fun closeThemeEditor() {
        themeEditor = null
        previewTheme = null
        dashboardHost.removeAllViews()
        // Recreate the module host so the existing focus/compact references stay intact.
        (focusHost.parent as? android.view.ViewGroup)?.removeView(focusHost)
        (compactHost.parent as? android.view.ViewGroup)?.removeView(compactHost)
        val moduleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, dp(12)) }
        moduleRow.addView(focusHost, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.1f))
        moduleRow.addView(compactHost, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f))
        dashboardHost.addView(moduleRow, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        showFocusedModule(focusedIndex, false)
        themeButton.text = "◉  Themes"
        applyTheme()
    }

    private fun control(label: String, action: () -> Unit) = button(label, action).apply { textSize = 22f; layoutParams = LinearLayout.LayoutParams(dp(118), dp(66)).apply { marginEnd = dp(10) } }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 15f; typeface = nelexiumFont(true); isAllCaps = false; setTextColor(android.graphics.Color.WHITE); setOnClickListener { action() }; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0; setPadding(dp(16), 0, dp(16), 0); background = controlBackground(false); stateListAnimator = null; elevation = 0f; translationZ = 0f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(50)).apply { marginStart = dp(10) }; borderedControls.add(this) }
    private fun statusText(label: String, icon: Int) = TextView(this).apply { text = label; textSize = 14f; gravity = Gravity.CENTER; typeface = nelexiumFont(true); setTextColor(android.graphics.Color.LTGRAY); setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0); compoundDrawablePadding = dp(8); setPadding(dp(14), 0, dp(14), 0); background = controlBackground(false); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(50)).apply { marginEnd = dp(10) }; borderedControls.add(this) }
    private fun divider() = View(this).apply { setBackgroundColor(android.graphics.Color.rgb(72, 72, 77)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1) + 1); dividerViews.add(this) }
    private fun controlBackground(night: Boolean) = GradientDrawable().apply { setColor(currentTheme().backgroundTint); setStroke(dp(1) + 1, currentTheme().lineColor) }
    private fun updateNightStyling() {
        val night = isNightTime()
        if (nightBorders == night) return
        nightBorders = night
        val theme = currentTheme()
        val lineColor = theme.lineColor
        applyThemeText(root, theme.backgroundTint)
        themeEditor?.setThemeBackground(theme.backgroundTint, theme.lineColor)
        dividerViews.forEach { it.setBackgroundColor(lineColor) }
        borderedControls.forEach { it.background = controlBackground(night) }
        song.setNightMode(night, theme)
        speed.setNightMode(night, theme)
        trip.setNightMode(night, theme)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun openMaps() { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))) } catch (_: Exception) { } }
    private fun showAppDrawer() { val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }; val apps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }.sortedBy { it.loadLabel(packageManager).toString() }; android.app.AlertDialog.Builder(this).setTitle("Apps").setItems(apps.map { it.loadLabel(packageManager) }.toTypedArray()) { _, which -> startActivity(packageManager.getLaunchIntentForPackage(apps[which].activityInfo.packageName)) }.setNegativeButton("Close", null).show() }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) { if (provider == android.location.LocationManager.GPS_PROVIDER) speed.update(0, 0, topSpeed) }
    override fun onDestroy() { updateDialog?.dismiss(); handler.removeCallbacksAndMessages(null); artworkExecutor.shutdownNow(); mediaController?.unregisterCallback(mediaCallback); try { mediaBrowser?.disconnect() } catch (_: Exception) { }; locationManager?.removeUpdates(this); super.onDestroy() }
}
