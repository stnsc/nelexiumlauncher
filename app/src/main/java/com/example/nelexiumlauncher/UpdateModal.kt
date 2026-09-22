package com.example.nelexiumlauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

/** Update controls and local diagnostics, presented over the dashboard. */
internal class UpdateModal(
    private val activity: Activity,
    dashboard: View,
    private val theme: ThemePreset,
    private val manager: UpdateManager
) {
    val dialog = Dialog(activity)
    private val foregroundColor = if (isBrightBackground(theme.backgroundTint)) Color.BLACK else Color.WHITE
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    private fun outline() = GradientDrawable().apply {
        setColor(theme.backgroundTint)
        setStroke(dp(1) + 1, theme.lineColor)
    }
    private fun label(value: String, size: Float = 16f, bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        typeface = activity.nelexiumFont(bold)
        setTextColor(foregroundColor)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun action(value: String) = Button(activity).apply {
        text = value
        textSize = 16f
        typeface = activity.nelexiumFont(true)
        isAllCaps = false
        setTextColor(foregroundColor)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(52)
        background = outline()
        foreground = RippleDrawable(ColorStateList.valueOf(0x33888888), null, ColorDrawable(Color.WHITE))
        stateListAnimator = null
    }
    val status = label("", 18f, true).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val check = action("Check and download now")
    val install = action("Install downloaded update")
    val automatic = CheckBox(activity).apply {
        text = "Automatically check and download while launcher is open"
        textSize = 16f
        typeface = activity.nelexiumFont()
        setTextColor(foregroundColor)
        buttonTintList = ColorStateList.valueOf(foregroundColor)
        isChecked = manager.automatic
        minHeight = dp(56)
    }
    private val diagnostics = label("").apply { setTextIsSelectable(true) }
    private var nextDiagnosticsRefresh = 0L

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        fun panel(title: String, icon: Int) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = outline()
            isClickable = true
            addView(label(title, 24f, true).apply {
                val image = activity.getDrawable(icon)?.mutate()?.apply { setBounds(0, 0, dp(32), dp(32)) }
                setCompoundDrawablesRelative(image, null, null, null)
                compoundDrawableTintList = ColorStateList.valueOf(foregroundColor)
                compoundDrawablePadding = dp(12)
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
            })
        }
        val updates = panel("App updates", R.drawable.ic_tabler_download)
        val updateContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Installed: ${manager.installed.versionName} (${UpdateManager.versionCode(manager.installed)})", 18f))
            addView(automatic)
            addView(label("Downloads use any internet connection, including a phone hotspot. Checks run every 15 minutes. Installation closes the launcher."))
            addView(status)
            addView(check, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(install, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        updates.addView(ScrollView(activity).apply { addView(updateContent) }, LinearLayout.LayoutParams(-1, 0, 1f))
        updates.addView(action("Close").apply { setOnClickListener { dialog.dismiss() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val debug = panel("Diagnostics", R.drawable.ic_tabler_settings)
        debug.addView(ScrollView(activity).apply { addView(diagnostics) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(updates, LinearLayout.LayoutParams(0, -1, 1.1f))
            addView(debug, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(20) })
        }
        val overlay = object : FrameLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = MeasureSpec.getSize(heightMeasureSpec)
                row.layoutParams = LayoutParams(
                    minOf(dp(1080), width - dp(40)).coerceAtLeast(1),
                    minOf(dp(600), height - dp(40)).coerceAtLeast(1), Gravity.CENTER)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            setOnClickListener { dialog.dismiss() }
            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(blurredSnapshot(dashboard))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
            addView(View(activity).apply { setBackgroundColor(Color.argb(165, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
            addView(row, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }
        dialog.setContentView(overlay)
        dialog.setCancelable(true)
        dialog.window?.apply {
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

    fun refreshDiagnostics() {
        if (SystemClock.elapsedRealtime() < nextDiagnosticsRefresh) return
        nextDiagnosticsRefresh = SystemClock.elapsedRealtime() + 5000L
        val metrics = activity.resources.displayMetrics
        val config = activity.resources.configuration
        val memory = ActivityManager.MemoryInfo()
        (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val network = runCatching {
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val transport = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                else -> "Other"
            }
            "$transport · " + if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                "internet validated" else "no validated internet"
        }.getOrDefault("Unavailable")
        val gps = runCatching {
            val lm = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) "Enabled" else "Disabled"
        }.getOrDefault("Unavailable")
        val installAllowed = if (Build.VERSION.SDK_INT < 26) "Managed by Android" else
            if (activity.packageManager.canRequestPackageInstalls()) "Allowed" else "Not allowed"
        val uptime = SystemClock.elapsedRealtime() / 1000
        fun bytes(value: Long) = Formatter.formatFileSize(activity, value)
        val report = buildString {
            appendLine("DEVICE")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH.ifBlank { "Unknown" }}")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            appendLine("LAUNCHER")
            appendLine("Version: ${manager.installed.versionName} (${UpdateManager.versionCode(manager.installed)})")
            appendLine("Package: ${activity.packageName}")
            appendLine("Target SDK: ${activity.applicationInfo.targetSdkVersion}")
            appendLine("Theme: ${theme.name}")
            appendLine("Display: ${metrics.widthPixels} × ${metrics.heightPixels} px")
            appendLine("Layout: ${config.screenWidthDp} × ${config.screenHeightDp} dp")
            appendLine("Density: ${metrics.densityDpi} dpi · font scale ${config.fontScale}")
            appendLine()
            appendLine("RUNTIME")
            appendLine("RAM available: ${bytes(memory.availMem)} / ${bytes(memory.totalMem)}")
            appendLine("App storage free: ${bytes(activity.filesDir.usableSpace)}")
            appendLine("Device uptime: ${uptime / 3600}h ${uptime / 60 % 60}m")
            appendLine("Network: $network")
            appendLine("GPS provider: $gps")
            appendLine("Update installs: $installAllowed")
            appendLine("Updater: ${if (manager.busy) "Working" else "Idle"}")
            appendLine("Downloaded update: ${manager.ready?.let { "${it.name} (${it.code})" } ?: "None"}")
            append("Diagnostics refresh every 5 seconds.")
        }
        if (diagnostics.text.toString() != report) diagnostics.text = report
    }
}
