package com.shlok.sam

import android.app.Application
import com.shlok.sam.data.seed.DatabaseSeeder
import com.shlok.sam.engine.voice.TtsEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SamApp : Application() {

    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var tts: TtsEngine

    override fun onCreate() {
        super.onCreate()
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
        CoroutineScope(Dispatchers.IO).launch {
            seeder.seedIfNeeded()
        }
        tts.init()
    }
}
