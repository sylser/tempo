package com.cappielloantonio.tempo.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.cappielloantonio.tempo.service.DesktopLyricsService

object OverlayPermissionUtil {
    /**
     * Check if the app has permission to display overlays on Android 8.0+.
     */
    @JvmStatic
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Settings.canDrawOverlays(context)
        } else {
            @Suppress("DEPRECATION")
            true // No permission needed before Oreo
        }
    }

    /**
     * Request overlay permission from the user.
     */
    @JvmStatic
    fun requestOverlayPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * Start the Desktop Lyrics Service if permission is granted.
     */
    @JvmStatic
    fun startDesktopLyricsService(context: Context) {
        if (hasOverlayPermission(context)) {
            val intent = Intent(context, DesktopLyricsService::class.java)
            context.startForegroundService(intent)
        }
    }

    /**
     * Stop the Desktop Lyrics Service.
     */
    @JvmStatic
    fun stopDesktopLyricsService(context: Context) {
        val intent = Intent(context, DesktopLyricsService::class.java)
        context.stopService(intent)
    }

    /**
     * Toggle the Desktop Lyrics Service.
     */
    @JvmStatic
    fun toggleDesktopLyrics(context: Context) {
        if (Preferences.isDesktopLyricsEnabled()) {
            Preferences.setDesktopLyricsEnabled(false)
            stopDesktopLyricsService(context)
        } else {
            if (hasOverlayPermission(context)) {
                Preferences.setDesktopLyricsEnabled(true)
                startDesktopLyricsService(context)
            }
        }
    }
}