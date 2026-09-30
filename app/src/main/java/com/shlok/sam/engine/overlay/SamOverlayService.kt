package com.shlok.sam.engine.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shlok.sam.MainActivity
import com.shlok.sam.R
import com.shlok.sam.domain.model.SamCoreState
import com.shlok.sam.engine.runtime.SamRuntime
import com.shlok.sam.engine.voice.SamNotifications
import com.shlok.sam.ui.orb.MiniOrb
import com.shlok.sam.ui.theme.SamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SamOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    @Inject lateinit var runtime: SamRuntime

    private val appViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = appViewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        SamNotifications.ensure(this)
        startForeground(SamNotifications.ID_OVERLAY, overlayNotification())
        attach()
    }

    private fun overlayNotification() = androidx.core.app.NotificationCompat.Builder(this, SamNotifications.CHANNEL_OVERLAY)
        .setSmallIcon(R.drawable.ic_stat_sam)
        .setContentTitle(getString(R.string.overlay_notification_title))
        .setContentText(getString(R.string.overlay_notification_text))
        .setOngoing(true)
        .build()

    private fun attach() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 36
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SamOverlayService)
            setViewTreeViewModelStoreOwner(this@SamOverlayService)
            setViewTreeSavedStateRegistryOwner(this@SamOverlayService)
            setContent {
                val ui by runtime.ui.collectAsState()
                SamTheme {
                    OverlayCapsule(
                        state = ui.state,
                        text = ui.spoken.ifBlank { ui.transcript }.ifBlank { "SAM" },
                        amplitude = ui.amplitude,
                        expanded = ui.overlayExpanded || ui.state != SamCoreState.IDLE,
                        onExpand = { runtime.toggleOverlayExpanded() },
                        onStop = { runtime.cancelEverything() },
                        onOpen = {
                            startActivity(
                                Intent(this@SamOverlayService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra("open", "chat")
                            )
                        }
                    )
                }
            }
        }
        composeView = view
        windowManager?.addView(view, params)
    }

    override fun onDestroy() {
        composeView?.let { runCatching { windowManager?.removeView(it) } }
        composeView = null
        appViewModelStore.clear()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, SamOverlayService::class.java))
        }
    }
}

@androidx.compose.runtime.Composable
fun OverlayCapsule(
    state: SamCoreState,
    text: String,
    amplitude: Float,
    expanded: Boolean,
    onExpand: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(Color(0xE6121828), Color(0xE60B1020)))
            )
            .clickable { onExpand() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniOrb(state = state, amplitude = amplitude, modifier = Modifier.size(28.dp))
            Text(
                text = when (state) {
                    SamCoreState.LISTENING -> "Listening"
                    SamCoreState.THINKING -> "Thinking"
                    SamCoreState.EXECUTING -> "Working"
                    SamCoreState.SPEAKING -> "Speaking"
                    SamCoreState.SUCCESS -> "Done"
                    SamCoreState.ERROR -> "Issue"
                    SamCoreState.IDLE -> "SAM"
                },
                color = Color(0xFF2EE6FF),
                fontSize = 13.sp
            )
        }
        if (expanded) {
            Text(
                text = text.take(140),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onExpand) { Text("Collapse", color = Color(0xFF9AA6C2), fontSize = 12.sp) }
                TextButton(onClick = onStop) { Text("Stop", color = Color(0xFFFF8A8A), fontSize = 12.sp) }
                TextButton(onClick = onOpen) { Text("Chat", color = Color(0xFF2EE6FF), fontSize = 12.sp) }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
    }
}
