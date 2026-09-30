package com.shlok.sam.core.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.shlok.sam.engine.android.SamAccessibilityService

data class PermissionStatus(
    val id: String,
    val title: String,
    val connected: Boolean,
    val settingsAction: String
)

object SamPermissions {
    fun microphone(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun camera(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun overlay(context: Context) = Settings.canDrawOverlays(context)

    fun accessibility(context: Context): Boolean {
        if (SamAccessibilityService.instance != null) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return list.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun notifications(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(context.packageName) == true
    }

    fun contacts(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun phone(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun calendar(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun postNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun all(context: Context): List<PermissionStatus> = listOf(
        PermissionStatus("mic", "Microphone", microphone(context), "runtime"),
        PermissionStatus("notify", "Notification Access", notifications(context), Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        PermissionStatus("a11y", "Accessibility", accessibility(context), Settings.ACTION_ACCESSIBILITY_SETTINGS),
        PermissionStatus("overlay", "Display Over Other Apps", overlay(context), Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        PermissionStatus("fgs", "Foreground Service", true, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
        PermissionStatus("camera", "Camera", camera(context), "runtime"),
        PermissionStatus("storage", "Storage / Files", true, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
        PermissionStatus("contacts", "Contacts", contacts(context), "runtime"),
        PermissionStatus("phone", "Phone", phone(context), "runtime"),
        PermissionStatus("calendar", "Calendar / Alarm", calendar(context), "runtime"),
        PermissionStatus("post", "Post Notifications", postNotifications(context), "runtime")
    )

    fun open(context: Context, action: String) {
        val intent = when (action) {
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION ->
                Intent(action, Uri.parse("package:${context.packageName}"))
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS ->
                Intent(action, Uri.parse("package:${context.packageName}"))
            else -> Intent(action)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
