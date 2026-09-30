package com.shlok.sam.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.shlok.sam.MainActivity
import com.shlok.sam.engine.overlay.SamOverlayService

class OverlayHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startForegroundService(Intent(this, SamOverlayService::class.java)) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }
}
