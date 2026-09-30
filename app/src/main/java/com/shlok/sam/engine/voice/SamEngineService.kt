package com.shlok.sam.engine.voice

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import com.shlok.sam.MainActivity
import com.shlok.sam.R
import com.shlok.sam.data.db.SamDatabase
import com.shlok.sam.engine.overlay.SamOverlayService
import com.shlok.sam.engine.runtime.SamRuntime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

object SamNotifications {
    const val CHANNEL_ENGINE = "sam_engine"
    const val CHANNEL_OVERLAY = "sam_overlay"
    const val CHANNEL_ALERTS = "sam_alerts"
    const val ID_ENGINE = 1801
    const val ID_OVERLAY = 1802

    fun ensure(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ENGINE, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_engine))
                .setDescription("Shown while SAM Engine is using the microphone for wake-word listening.")
                .build()
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_OVERLAY, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_overlay))
                .setDescription("Shown while the SAM overlay can appear over other apps.")
                .build()
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_alerts))
                .build()
        )
    }

    fun engine(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, SamEngineService::class.java).setAction(SamEngineService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ENGINE)
            .setSmallIcon(R.drawable.ic_stat_sam)
            .setContentTitle(context.getString(R.string.engine_notification_title))
            .setContentText(context.getString(R.string.engine_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop engine", stop)
            .build()
    }
}

@AndroidEntryPoint
class SamEngineService : LifecycleService() {

    @Inject lateinit var runtime: SamRuntime
    @Inject lateinit var db: SamDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        SamNotifications.ensure(this)
        runtime.engineActive = true
        runtime.overlayCallback = { show ->
            if (show) startOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        startForeground(SamNotifications.ID_ENGINE, SamNotifications.engine(this))
        runtime.startWakeListening()
        startOverlay()
        scope.launch {
            val p = db.userProfile().get() ?: return@launch
            db.userProfile().upsert(p.copy(engineEnabled = true))
        }
        return START_STICKY
    }

    private fun startOverlay() {
        startService(Intent(this, SamOverlayService::class.java))
    }

    private fun stopSelfSafely() {
        runtime.engineActive = false
        runtime.cancelEverything()
        stopService(Intent(this, SamOverlayService::class.java))
        scope.launch {
            db.userProfile().get()?.let { db.userProfile().upsert(it.copy(engineEnabled = false)) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val ACTION_STOP = "com.shlok.sam.STOP_ENGINE"
        fun start(context: Context) {
            context.startForegroundService(Intent(context, SamEngineService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, SamEngineService::class.java).setAction(ACTION_STOP))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Engine stays off after reboot unless the user starts it. Privacy-first.
    }
}
