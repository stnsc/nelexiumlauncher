package com.example.nelexiumlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider

internal object UpdateDialog {
    fun show(activity: Activity, dashboard: android.view.View, theme: ThemePreset): android.app.Dialog {
        val manager = UpdateManager.get(activity)
        val ui = UpdateModal(activity, dashboard, theme, manager)
        val dialog = ui.dialog
        val status = ui.status
        val check = ui.check
        val install = ui.install
        val automatic = ui.automatic
        automatic.setOnCheckedChangeListener { _, enabled ->
            try {
                manager.configure(manager.feedUrl, enabled)
            } catch (error: Exception) {
                automatic.isChecked = manager.automatic
                status.text = error.message
            }
        }
        check.setOnClickListener { manager.checkNow() }
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
                automatic.isEnabled = !manager.busy
                check.isEnabled = !manager.busy
                check.alpha = if (check.isEnabled) 1f else 0.45f
                install.isEnabled = !manager.busy && manager.ready != null
                install.alpha = if (install.isEnabled) 1f else 0.45f
                ui.refreshDiagnostics()
                handler.postDelayed(this, 1000)
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
