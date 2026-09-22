package com.example.nelexiumlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider

internal object UpdateDialog {
    fun show(activity: Activity): AlertDialog {
        val manager = UpdateManager.get(activity)
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(activity).apply {
            text = "Installed: ${manager.installed.versionName} (${UpdateManager.versionCode(manager.installed)})\n"
        })
//        val url = EditText(activity).apply {
//            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
//            setSingleLine()
//            hint = UpdateManager.DEFAULT_FEED_URL
//            setText(manager.feedUrl)
//        }
//        layout.addView(url)
        val automatic = CheckBox(activity).apply {
            text = "Automatically check and download while launcher is open"
            isChecked = manager.automatic
        }
        layout.addView(automatic)
        layout.addView(TextView(activity).apply {
            text = "Downloads use any internet connection, including a phone hotspot. Checks run every 15 minutes. Installation closes the launcher."
        })
        val status = TextView(activity).apply { setPadding(0, padding, 0, padding) }
        layout.addView(status)
        val check = Button(activity).apply { text = "Check and download now" }
        val install = Button(activity).apply { text = "Install downloaded update" }
        layout.addView(check)
        layout.addView(install)
        val dialog = AlertDialog.Builder(activity).setTitle("App updates")
            .setView(ScrollView(activity).apply { addView(layout) })
            .setNegativeButton("Close", null).create()
//        fun saveSettings(): Boolean = try {
//            manager.configure(url.text.toString().trim(), automatic.isChecked)
//            url.setText(manager.feedUrl)
//            url.error = null
//            true
//        } catch (e: Exception) { url.error = e.message; false }
//        save.setOnClickListener { saveSettings() }
//        check.setOnClickListener { if (saveSettings()) manager.checkNow() }
        install.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("Install update?")
                .setMessage("Park before continuing. Android will close the launcher to install the update. You can reopen it using the Home button.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Continue") { _, _ ->
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}")))
                            android.widget.Toast.makeText(activity, "Allow updates from this app, then return and tap Install again.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            manager.prepareInstall { file ->
                                if (file != null && !activity.isDestroyed && !activity.isFinishing) {
                                    try {
                                        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
                                        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        })
                                    } catch (e: Exception) { showError(activity, e) }
                                }
                            }
                        }
                    } catch (e: Exception) { showError(activity, e) }
                }.show()
        }
        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                status.text = manager.status
                check.isEnabled = !manager.busy
                install.isEnabled = !manager.busy && manager.ready != null
                handler.postDelayed(this, 500)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        dialog.show()
        refresh.run()
        return dialog
    }

    private fun showError(activity: Activity, error: Exception) {
        AlertDialog.Builder(activity).setTitle("Could not open installer")
            .setMessage(error.message ?: "No package installer is available on this device.")
            .setPositiveButton("OK", null).show()
    }
}
