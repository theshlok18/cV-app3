package com.shlok.sam.engine.command

import com.shlok.sam.core.identity.SamIdentity
import com.shlok.sam.core.identity.SpokenLanguage
import com.shlok.sam.domain.model.IntentCategory
import com.shlok.sam.domain.model.ParsedCommand
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandParser @Inject constructor() {

    fun parse(raw: String): ParsedCommand {
        val text = raw.trim()
        if (text.isBlank()) {
            return ParsedCommand(raw, SpokenLanguage.ENGLISH, IntentCategory.UNKNOWN, confidence = 0f)
        }
        val lang = detectLanguage(text)
        val n = normalize(text)
        val slots = mutableMapOf<String, String>()

        if (isStop(n)) {
            return ParsedCommand(text, lang, IntentCategory.STOP, confidence = 1f)
        }
        if (isCancel(n)) {
            return ParsedCommand(text, lang, IntentCategory.CANCEL, confidence = 1f)
        }
        if (SamIdentity.isCreatorQuestion(n) || SamIdentity.isCreatorQuestion(text)) {
            return ParsedCommand(text, lang, IntentCategory.HELP, slots = mapOf("topic" to "creator"), confidence = 1f)
        }
        if (isHelp(n)) {
            return ParsedCommand(text, lang, IntentCategory.HELP, confidence = 0.9f)
        }
        if (isHome(n)) {
            return ParsedCommand(text, lang, IntentCategory.HOME, confidence = 0.95f)
        }
        if (isBack(n)) {
            return ParsedCommand(text, lang, IntentCategory.BACK, confidence = 0.95f)
        }
        if (n.contains("recent")) {
            return ParsedCommand(text, lang, IntentCategory.RECENTS, confidence = 0.9f)
        }

        extractWhatsApp(n, text)?.let { return it.copy(language = lang) }
        if (n.contains("camera") || n.contains("कॅमेरा") || n.contains("कैमरा")) {
            return ParsedCommand(text, lang, IntentCategory.CAMERA, confidence = 0.9f)
        }
        if (n.contains("maps") || n.contains("google maps") || n.contains("नकाशा")) {
            return ParsedCommand(text, lang, IntentCategory.MAPS, confidence = 0.9f)
        }
        if (n.contains("calendar") || n.contains("कॅलेंडर") || n.contains("कैलेंडर")) {
            return ParsedCommand(text, lang, IntentCategory.CALENDAR, confidence = 0.9f)
        }
        extractCall(n)?.let { return it.copy(raw = text, language = lang) }
        extractAlarm(n)?.let { return it.copy(raw = text, language = lang) }
        extractTimer(n)?.let { return it.copy(raw = text, language = lang) }
        extractNotifications(n)?.let { return it.copy(raw = text, language = lang) }
        extractVolume(n)?.let { return it.copy(raw = text, language = lang) }
        extractMedia(n)?.let { return it.copy(raw = text, language = lang) }
        extractScroll(n)?.let { return it.copy(raw = text, language = lang) }
        extractMemory(n, text)?.let { return it.copy(language = lang) }
        extractSearch(n, text, lang)?.let { return it }
        extractOpen(n, text, lang)?.let { return it }

        if (n.contains("settings") || n.contains("सेटिंग") || n.contains("setting")) {
            return ParsedCommand(text, lang, IntentCategory.SETTINGS, confidence = 0.8f)
        }
        if (n.contains("deep research") || n.contains("research")) {
            slots["query"] = text
            return ParsedCommand(text, lang, IntentCategory.DEEP_RESEARCH, slots, 0.7f)
        }

        return ParsedCommand(text, lang, IntentCategory.CONVERSATION, mapOf("query" to text), 0.5f)
    }

    fun detectLanguage(text: String): SpokenLanguage {
        val hasDeva = text.any { it in '\u0900'..'\u097F' }
        val lower = text.lowercase(Locale.ROOT)
        val mrHints = listOf("aahe", "ahe", "ugad", "उघड", "आहे", "काय", "मला", "तुला", "vajta", "lav", "kon aahe", "sang")
        val hiHints = listOf("kholo", "hai", "kya", "baje", "lagao", "kaun", "है", "कौन", "खोलो", "बजे")
        val enHints = listOf("open", "search", "who", "what", "set", "alarm", "please", "the")
        val mr = mrHints.count { lower.contains(it) || text.contains(it) }
        val hi = hiHints.count { lower.contains(it) || text.contains(it) }
        val en = enHints.count { lower.contains(it) }
        return when {
            hasDeva && mr >= hi -> SpokenLanguage.MARATHI
            hasDeva -> SpokenLanguage.HINDI
            mr > 0 && en > 0 -> SpokenLanguage.MIXED
            hi > 0 && en > 0 -> SpokenLanguage.MIXED
            mr > hi && mr > en -> SpokenLanguage.MARATHI
            hi > en -> SpokenLanguage.HINDI
            en > 0 -> SpokenLanguage.ENGLISH
            hasDeva -> SpokenLanguage.HINDI
            else -> SpokenLanguage.ENGLISH
        }
    }

    private fun normalize(text: String): String {
        var t = text.lowercase(Locale.ROOT)
        t = t.replace(Regex("[?.!,]"), " ")
        t = t.replace("hey sam", " ").replace("ok sam", " ").replace("hi sam", " ")
        t = Regex("\\bsam\\b").replace(t, " ")
        t = t.replace("सॅम", " ").replace("सैम", " ").replace("सैम", " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun isStop(n: String) = n == "stop" || n.contains("sam stop") || n == "रुको" || n == "थांब" ||
        n.contains("stop speaking") || n == "wait" || n.contains("short answer")
    private fun isCancel(n: String) = n.contains("cancel") || n.contains("रद्द") || n.contains("cancel task") || n.contains("cancel this")
    private fun isHelp(n: String) = n == "help" || n.contains("what can you do") || n.contains("तु काय करू") || n.contains("क्या कर सकते")
    private fun isHome(n: String) = n == "home" || n.contains("go home") || n.contains("go to home") || n.contains("होम")
    private fun isBack(n: String) = n == "back" || n.contains("go back") || n.contains("पीछे") || n.contains("मागे")

    private fun extractOpen(n: String, raw: String, lang: SpokenLanguage): ParsedCommand? {
        val open = Regex("(?:open|launch|start|ugad|उघड|kholo|खोलो|खोल|open kar)\\s+(.+)").find(n)
            ?: Regex("(.+)\\s+(?:open kar|ugad|kholo|उघड|खोलो|खोल)").find(n)
        val app = when {
            open != null -> open.groupValues[1]
            n.startsWith("open ") -> n.removePrefix("open ")
            else -> return null
        }.trim()
        if (app.isBlank()) return null
        val rest = app
        val andParts = rest.split(Regex("\\s+(?:and|&|ani|aur|और|आणि)\\s+"), limit = 2)
        val appName = andParts[0].trim()
        val follow = andParts.getOrNull(1)
        val slots = mutableMapOf("app" to appName)
        follow?.let { slots["follow"] = it }
        return ParsedCommand(raw, lang, IntentCategory.OPEN_APP, slots, 0.9f)
    }

    private fun extractSearch(n: String, raw: String, lang: SpokenLanguage): ParsedCommand? {
        when {
            n.contains("wikipedia") || n.contains("wiki") -> {
                val q = n.substringAfter("for", n.substringAfter("wikipedia").substringAfter("search")).trim()
                return ParsedCommand(raw, lang, IntentCategory.WIKIPEDIA_SEARCH, mapOf("query" to q.ifBlank { raw }), 0.9f)
            }
            n.contains("who is") || n.contains("who are") || n.contains("kon aahe") || n.contains("कौन है") || n.contains("कोण आहे") -> {
                val q = n.replace("who is", "").replace("who are", "").replace("kon aahe", "")
                    .replace("कौन है", "").replace("कोण आहे", "").replace("kaun hai", "").trim()
                return ParsedCommand(raw, lang, IntentCategory.WIKIPEDIA_SEARCH, mapOf("query" to q.ifBlank { raw }), 0.88f)
            }
            n.contains("google") && (n.contains("search") || n.contains("open")) -> {
                val q = n.substringAfter("search").replace("google", "").replace("for", "").trim()
                    .ifBlank { n.substringAfter("google").replace("search", "").replace("open", "").replace("and", "").trim() }
                return ParsedCommand(raw, lang, IntentCategory.WEB_SEARCH, mapOf("query" to q, "open" to "true"), 0.9f)
            }
            n.startsWith("search google") || n.startsWith("google search") -> {
                val q = n.removePrefix("search google for").removePrefix("search google")
                    .removePrefix("google search for").removePrefix("google search").trim()
                return ParsedCommand(raw, lang, IntentCategory.WEB_SEARCH, mapOf("query" to q, "open" to "true"), 0.9f)
            }
            n.startsWith("tell me about") -> {
                val q = n.removePrefix("tell me about").trim()
                return ParsedCommand(raw, lang, IntentCategory.WIKIPEDIA_SEARCH, mapOf("query" to q), 0.85f)
            }
            n.contains("madhle") || n.contains("unit ") -> {
                return ParsedCommand(raw, lang, IntentCategory.KNOWLEDGE_DOC, mapOf("query" to raw), 0.8f)
            }
            n.startsWith("search ") || (n.contains("sang") && n.length > 8) -> {
                val q = n.removePrefix("search").trim()
                return ParsedCommand(raw, lang, IntentCategory.SEARCH, mapOf("query" to q), 0.75f)
            }
        }
        return null
    }

    private fun extractWhatsApp(n: String, raw: String): ParsedCommand? {
        if (!n.contains("whatsapp") && !n.contains("message") && !n.contains("message")) return null
        if (!(n.contains("message") || n.contains("msg") || n.contains("bol") || n.contains("kahi") || n.contains("saying"))) {
            if (n.contains("whatsapp")) return null
        }
        if (!n.contains("whatsapp") && !n.contains("message")) return null
        if (!n.contains("whatsapp") && !n.contains("message ")) return null
        val to = Regex("(?:message|msg|text|ping)\\s+([\\w\\s]+?)(?:\\s+(?:saying|that|ki|bol)|$)").find(n)?.groupValues?.get(1)?.trim()
            ?: Regex("(?:to|la)\\s+([\\w\\s]+?)(?:\\s+(?:saying|that)|$)").find(n)?.groupValues?.get(1)?.trim()
        val body = when {
            n.contains("saying") -> n.substringAfter("saying").trim()
            n.contains(" that ") -> n.substringAfter(" that ").trim()
            n.contains(" bol ") -> n.substringAfter(" bol ").trim()
            else -> n.substringAfter("message").substringAfter(to ?: "").trim()
        }
        if (n.contains("whatsapp") || (to != null && body.isNotBlank())) {
            return ParsedCommand(
                raw,
                SpokenLanguage.ENGLISH,
                IntentCategory.SEND_MESSAGE,
                mapOf("app" to "whatsapp", "contact" to (to ?: ""), "body" to body.trim('"', ' ')),
                0.85f
            )
        }
        return null
    }

    private fun extractCall(n: String): ParsedCommand? {
        if (!(n.contains("call ") || n.contains("phone ") || n.startsWith("call"))) return null
        val target = n.removePrefix("call").replace("phone", "").trim()
        if (target.isBlank()) return null
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.CALL, mapOf("target" to target), 0.85f)
    }

    private fun extractAlarm(n: String): ParsedCommand? {
        val isAlarm = listOf("alarm", "wake me", "अलार्म", "जाग").any { n.contains(it) }
        if (!isAlarm) return null
        if (n.contains("cancel") || n.contains("delete") || n.contains("remove")) {
            val t = parseTime(n)
            return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.ALARM, mapOf("op" to "cancel", "hour" to "${t?.first ?: -1}", "minute" to "${t?.second ?: 0}"), 0.8f)
        }
        val t = parseTime(n) ?: return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.ALARM, mapOf("op" to "set"), 0.5f)
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.ALARM, mapOf("op" to "set", "hour" to "${t.first}", "minute" to "${t.second}"), 0.9f)
    }

    private fun extractTimer(n: String): ParsedCommand? {
        if (!n.contains("timer") && !n.contains("countdown")) return null
        val mins = Regex("(\\d+)\\s*(?:minute|min|मिनिट)").find(n)?.groupValues?.get(1)?.toIntOrNull()
        val secs = Regex("(\\d+)\\s*(?:second|sec)").find(n)?.groupValues?.get(1)?.toIntOrNull()
        val total = (mins ?: 0) * 60 + (secs ?: 0)
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.TIMER, mapOf("seconds" to "$total"), 0.85f)
    }

    private fun extractNotifications(n: String): ParsedCommand? {
        if (!(n.contains("notification") || n.contains("messages") || n.contains("सूचना") || n.contains("नोटिफ"))) return null
        val app = when {
            n.contains("whatsapp") -> "whatsapp"
            n.contains("instagram") -> "instagram"
            else -> ""
        }
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.NOTIFICATIONS, mapOf("app" to app), 0.9f)
    }

    private fun extractVolume(n: String): ParsedCommand? {
        val dir = when {
            n.contains("volume up") || n.contains("increase volume") || n.contains("आवाज वाढ") -> "up"
            n.contains("volume down") || n.contains("decrease volume") || n.contains("mute") -> if (n.contains("mute")) "mute" else "down"
            else -> return null
        }
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.VOLUME, mapOf("direction" to dir), 0.9f)
    }

    private fun extractMedia(n: String): ParsedCommand? {
        val cmd = when {
            n.contains("play") -> "play"
            n.contains("pause") -> "pause"
            n.contains("next") -> "next"
            n.contains("previous") || n.contains("last song") -> "previous"
            else -> return null
        }
        if (!n.contains("song") && !n.contains("music") && !n.contains("media") && cmd == "play" && n.contains("play ")) {
            if (!n.contains("music") && !n.contains("song")) return null
        }
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.MEDIA, mapOf("command" to cmd), 0.8f)
    }

    private fun extractScroll(n: String): ParsedCommand? {
        if (!n.contains("scroll")) return null
        val dir = if (n.contains("up")) "up" else "down"
        return ParsedCommand(n, SpokenLanguage.ENGLISH, IntentCategory.SCROLL, mapOf("direction" to dir), 0.85f)
    }

    private fun extractMemory(n: String, raw: String): ParsedCommand? {
        if (n.contains("remember") || n.contains("लक्षात ठेव") || n.contains("याद रख")) {
            val content = n.substringAfter("remember").substringAfter("that").trim()
            return ParsedCommand(raw, SpokenLanguage.ENGLISH, IntentCategory.MEMORY, mapOf("op" to "save", "content" to content.ifBlank { raw }), 0.85f)
        }
        if (n.contains("what do you remember") || n.contains("show memory") || n.contains("my notes")) {
            return ParsedCommand(raw, SpokenLanguage.ENGLISH, IntentCategory.MEMORY, mapOf("op" to "read"), 0.85f)
        }
        return null
    }

    fun parseTime(n: String): Pair<Int, Int>? {
        val ampm = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").find(n)
        if (ampm != null) {
            var h = ampm.groupValues[1].toInt()
            val m = ampm.groupValues[2].toIntOrNull() ?: 0
            val ap = ampm.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            return h to m
        }
        val colon = Regex("(\\d{1,2})[:.](\\d{2})").find(n)
        if (colon != null) return colon.groupValues[1].toInt() to colon.groupValues[2].toInt()
        val hindi = Regex("(\\d{1,2})\\s*(?:baje|vajta|वाजता|बजे)").find(n)
        if (hindi != null) {
            var h = hindi.groupValues[1].toInt()
            if (n.contains("raat") || n.contains("night") || n.contains("sham") || n.contains("evening") || n.contains("pm")) {
                if (h < 12) h += 12
            }
            return h to 0
        }
        val hourWord = Regex("\\b(\\d{1,2})\\b").find(n)
        if (hourWord != null && (n.contains("alarm") || n.contains("wake"))) {
            var h = hourWord.groupValues[1].toInt()
            if (h in 1..12 && (n.contains("pm") || n.contains("night") || n.contains("evening"))) h = (h % 12) + 12
            return h to 0
        }
        return null
    }
}
