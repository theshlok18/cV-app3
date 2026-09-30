package com.shlok.sam.engine.android

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.CalendarContract
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AppRef(val label: String, val packageName: String, val aliases: List<String>)

    private val builtins = listOf(
        AppRef("WhatsApp", "com.whatsapp", listOf("whatsapp", "whats app")),
        AppRef("Instagram", "com.instagram.android", listOf("instagram", "insta")),
        AppRef("YouTube", "com.google.android.youtube", listOf("youtube", "yt")),
        AppRef("Chrome", "com.android.chrome", listOf("chrome")),
        AppRef("Google", "com.google.android.googlequicksearchbox", listOf("google")),
        AppRef("Maps", "com.google.android.apps.maps", listOf("maps", "google maps")),
        AppRef("Gmail", "com.google.android.gm", listOf("gmail", "email", "mail")),
        AppRef("Telegram", "org.telegram.messenger", listOf("telegram")),
        AppRef("Phone", "com.android.dialer", listOf("phone", "dialer", "call app")),
        AppRef("Contacts", "com.android.contacts", listOf("contacts", "address book")),
        AppRef("Camera", "com.android.camera", listOf("camera")),
        AppRef("Settings", "com.android.settings", listOf("settings"))
    )

    fun resolve(query: String): AppRef? {
        val q = query.lowercase().trim()
        builtins.firstOrNull { it.aliases.any { a -> q.contains(a) } || it.label.lowercase() in q }?.let {
            if (isInstalled(it.packageName)) return it
        }
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return apps.mapNotNull {
            val label = it.loadLabel(pm).toString()
            val pkg = it.activityInfo.packageName
            if (label.lowercase().contains(q) || q.contains(label.lowercase()) || pkg.contains(q.replace(" ", ""))) {
                AppRef(label, pkg, listOf(label.lowercase()))
            } else null
        }.minByOrNull { it.label.length }
    }

    fun isInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    fun isForeground(packageName: String): Boolean {
        // Best-effort: usage stats are restricted. Accessibility reports the active window.
        return SamAccessibilityService.instance?.activePackage() == packageName
    }
}

@Singleton
class AndroidController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: AppCatalog
) {
    fun launchPackage(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch); true }.getOrDefault(false)
    }

    fun launchApp(query: String): Pair<Boolean, String> {
        val app = catalog.resolve(query) ?: return false to "I couldn't find an installed app matching \"$query\"."
        val ok = launchPackage(app.packageName)
        return if (ok) true to app.packageName else false to "Android refused to launch ${app.label}."
    }

    fun openView(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun openGoogleSearch(query: String): Boolean {
        val encoded = Uri.encode(query)
        val google = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(google); true }.getOrDefault(false)) return true
        return openView("https://www.google.com/search?q=$encoded")
    }

    fun setAlarm(hour: Int, minute: Int, message: String?): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message ?: "SAM")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = intent.resolveActivity(context.packageManager) != null
        if (!resolved) return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun dismissAlarm(hour: Int?, minute: Int?): Boolean {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hour != null && hour >= 0) {
            intent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute ?: 0)
        } else {
            intent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_ALL)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent); true }.getOrDefault(false)
        } else false
    }

    fun setTimer(seconds: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun openSettings(page: String?): Boolean {
        val action = when (page) {
            "overlay" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "app" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (page == "app") intent.data = Uri.parse("package:${context.packageName}")
        if (page == "overlay") intent.data = Uri.parse("package:${context.packageName}")
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun dial(target: String): Boolean {
        val uri = if (target.any { it.isDigit() }) Uri.parse("tel:${target.filter { it.isDigit() || it == '+' }}")
        else Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(target))
        val intent = Intent(Intent.ACTION_DIAL, if (target.any { it.isDigit() }) uri else Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val call = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${target.filter { it.isDigit() || it == '+' }}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(if (target.any { it.isDigit() }) call else Intent(Intent.ACTION_VIEW).setData(ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    fun openCamera(): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun volume(direction: String): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (direction) {
            "up" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            else -> return false
        }
        return true
    }

    fun media(command: String): Boolean {
        val key = when (command) {
            "play", "pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, key)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, key)
        am.dispatchMediaKeyEvent(eventDown)
        am.dispatchMediaKeyEvent(eventUp)
        return true
    }

    fun home(): Boolean = SamAccessibilityService.instance?.globalHome() == true
    fun back(): Boolean = SamAccessibilityService.instance?.globalBack() == true
    fun recents(): Boolean = SamAccessibilityService.instance?.globalRecents() == true

    fun ttsAvailable(): Boolean = true

    fun openMaps(query: String? = null): Boolean {
        val uri = if (query.isNullOrBlank()) Uri.parse("geo:0,0?q=")
        else Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        return openView(uri.toString()) || launchApp("maps").first
    }

    fun openCalendar(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false) ||
            openView("content://com.android.calendar/time/${System.currentTimeMillis()}")
    }
}

@Singleton
class AppAdapter @Inject constructor(
    private val catalog: AppCatalog
) {
    fun resolve(query: String) = catalog.resolve(query)
    fun isInstalled(packageName: String) = catalog.isInstalled(packageName)
}
