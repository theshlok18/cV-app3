package com.shlok.sam.data.seed

import com.shlok.sam.data.db.AiProviderEntity
import com.shlok.sam.data.db.AppInfoEntity
import com.shlok.sam.data.db.CommandPatternEntity
import com.shlok.sam.data.db.OrbSettingsEntity
import com.shlok.sam.data.db.SamDatabase
import com.shlok.sam.data.db.UserProfileEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val db: SamDatabase
) {
    suspend fun seedIfNeeded() {
        if (db.userProfile().get() == null) {
            db.userProfile().upsert(UserProfileEntity())
        }
        if (db.orb().get() == null) {
            db.orb().upsert(OrbSettingsEntity())
        }
        if (db.providers().all().isEmpty()) {
            listOf(
                "gemini", "openai", "anthropic", "groq", "local", "custom", "elevenlabs"
            ).forEach { id ->
                db.providers().upsert(
                    AiProviderEntity(
                        id = id,
                        enabled = id == "local",
                        lastStatus = if (id == "local") "Ready (on-device)" else "Not connected"
                    )
                )
            }
        }
        if (db.apps().count() == 0) {
            db.apps().insertAll(defaultApps())
        }
        if (db.patterns().count() == 0) {
            db.patterns().insertAll(defaultPatterns())
        }
    }

    private fun defaultApps() = listOf(
        AppInfoEntity("com.whatsapp", "WhatsApp", "whatsapp,whats app,वाट्सअॅप,व्हाट्सएप"),
        AppInfoEntity("com.instagram.android", "Instagram", "instagram,insta,इंस्टा,इंस्टाग्राम"),
        AppInfoEntity("com.google.android.youtube", "YouTube", "youtube,yt,यूट्यूब"),
        AppInfoEntity("com.android.chrome", "Chrome", "chrome,browser,क्रोम"),
        AppInfoEntity("com.google.android.googlequicksearchbox", "Google", "google,गूगल"),
        AppInfoEntity("com.google.android.apps.maps", "Maps", "maps,google maps,मॅप्स,नकाशा"),
        AppInfoEntity("com.google.android.gm", "Gmail", "gmail,email,मेल,जीमेल"),
        AppInfoEntity("org.telegram.messenger", "Telegram", "telegram,टेलीग्राम"),
        AppInfoEntity("com.android.camera2", "Camera", "camera,कॅमेरा,कैमरा"),
        AppInfoEntity("com.google.android.deskclock", "Clock", "clock,alarm,अलार्म,घड्याळ")
    )

    private fun defaultPatterns() = listOf(
        CommandPatternEntity(language = "en", pattern = "open {app}", intent = "OPEN_APP", example = "Open WhatsApp"),
        CommandPatternEntity(language = "hi", pattern = "{app} kholo", intent = "OPEN_APP", example = "WhatsApp kholo"),
        CommandPatternEntity(language = "mr", pattern = "{app} ugad", intent = "OPEN_APP", example = "WhatsApp उघड"),
        CommandPatternEntity(language = "mixed", pattern = "{app} open kar", intent = "OPEN_APP", example = "WhatsApp open kar"),
        CommandPatternEntity(language = "en", pattern = "set alarm", intent = "ALARM", example = "Set an alarm for 11 AM"),
        CommandPatternEntity(language = "hi", pattern = "alarm lagao", intent = "ALARM", example = "11 baje alarm lagao"),
        CommandPatternEntity(language = "mr", pattern = "alarm lav", intent = "ALARM", example = "11 vajta alarm lav"),
        CommandPatternEntity(language = "en", pattern = "who is", intent = "WIKIPEDIA_SEARCH", example = "Who is Virat Kohli"),
        CommandPatternEntity(language = "hi", pattern = "kaun hai", intent = "WIKIPEDIA_SEARCH", example = "Virat Kohli कौन है"),
        CommandPatternEntity(language = "mr", pattern = "kon aahe", intent = "WIKIPEDIA_SEARCH", example = "Virat Kohli kon aahe")
    )
}
