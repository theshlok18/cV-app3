package com.shlok.sam.engine.task

import android.content.Context
import android.provider.Settings
import com.shlok.sam.core.identity.SamIdentity
import com.shlok.sam.core.identity.SpokenLanguage
import com.shlok.sam.data.db.SamDatabase
import com.shlok.sam.data.db.TaskEntity
import com.shlok.sam.data.db.TaskStepEntity
import com.shlok.sam.domain.model.ActionResult
import com.shlok.sam.domain.model.IntentCategory
import com.shlok.sam.domain.model.ParsedCommand
import com.shlok.sam.domain.model.PlannedStep
import com.shlok.sam.domain.model.SamAction
import com.shlok.sam.domain.model.TaskPlan
import com.shlok.sam.domain.model.TaskState
import com.shlok.sam.engine.ai.AIProviderManager
import com.shlok.sam.engine.android.AndroidController
import com.shlok.sam.engine.android.AppCatalog
import com.shlok.sam.engine.android.SamAccessibilityService
import com.shlok.sam.engine.knowledge.KnowledgeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class LiveTask(
    val id: Long,
    val title: String,
    val state: TaskState,
    val steps: List<TaskStepEntity>,
    val confirmationPrompt: String? = null,
    val pendingAction: SamAction? = null
)

@Singleton
class TaskEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SamDatabase,
    private val android: AndroidController,
    private val catalog: AppCatalog,
    private val knowledge: KnowledgeManager,
    private val ai: AIProviderManager
) {
    private val cancelled = AtomicBoolean(false)
    private val _live = MutableStateFlow<LiveTask?>(null)
    val live: StateFlow<LiveTask?> = _live
    private var pendingConfirm: Pair<SamAction, Long>? = null
    var conversationTopic: String? = null
        private set

    fun cancel() {
        cancelled.set(true)
        pendingConfirm = null
        _live.value = _live.value?.copy(state = TaskState.CANCELLED)
    }

    fun hasPendingConfirmation() = pendingConfirm != null

    suspend fun confirmPending(yes: Boolean, userName: String): ActionResult {
        val pending = pendingConfirm ?: return ActionResult(false, "Nothing waiting for confirmation.")
        pendingConfirm = null
        if (!yes) {
            mark(pending.second, TaskState.CANCELLED, "Cancelled.")
            return ActionResult(true, "Okay $userName, I cancelled that.", verified = true)
        }
        return executeAction(pending.first, pending.second, userName)
    }

    suspend fun run(command: ParsedCommand, userName: String): ActionResult {
        cancelled.set(false)
        if (command.category == IntentCategory.STOP || command.category == IntentCategory.CANCEL) {
            cancel()
            return ActionResult(true, phrase(command.language, userName, "stopped"), verified = true)
        }
        if (isAffirmative(command.raw) && pendingConfirm != null) return confirmPending(true, userName)
        if (isNegative(command.raw) && pendingConfirm != null) return confirmPending(false, userName)

        val plan = plan(command)
        val taskId = db.tasks().insert(
            TaskEntity(title = plan.title, rawCommand = command.raw, state = TaskState.PLANNING.name)
        )
        val steps = plan.steps.mapIndexed { i, s ->
            TaskStepEntity(taskId = taskId, index = i, label = s.label, state = "PENDING")
        }
        db.steps().insertAll(steps)
        _live.value = LiveTask(taskId, plan.title, TaskState.RUNNING, db.steps().forTask(taskId), plan.confirmationPrompt, plan.steps.lastOrNull()?.action)
        db.tasks().update(db.tasks().get(taskId)!!.copy(state = TaskState.RUNNING.name, updatedAt = System.currentTimeMillis()))

        if (plan.requiresConfirmation && plan.steps.size == 1) {
            pendingConfirm = plan.steps.first().action to taskId
            db.tasks().update(db.tasks().get(taskId)!!.copy(state = TaskState.WAITING_CONFIRMATION.name))
            _live.value = _live.value?.copy(state = TaskState.WAITING_CONFIRMATION, confirmationPrompt = plan.confirmationPrompt)
            return ActionResult(true, plan.confirmationPrompt ?: "Confirm?", verified = false)
        }

        var last = ActionResult(false, "No steps.")
        val stored = db.steps().forTask(taskId)
        for ((index, step) in plan.steps.withIndex()) {
            if (cancelled.get()) {
                mark(taskId, TaskState.CANCELLED, "Cancelled.")
                return ActionResult(false, phrase(command.language, userName, "cancelled"), verified = false)
            }
            val row = stored[index]
            db.steps().update(row.copy(state = "RUNNING"))
            last = executeAction(step.action, taskId, userName)
            db.steps().update(row.copy(state = if (last.success && last.verified) "DONE" else if (last.success) "UNVERIFIED" else "FAILED", detail = last.message))
            _live.value = _live.value?.copy(steps = db.steps().forTask(taskId), state = TaskState.RUNNING)
            if (!last.success) {
                mark(taskId, TaskState.FAILED, last.message)
                return last
            }
            if (pendingConfirm != null) {
                mark(taskId, TaskState.WAITING_CONFIRMATION, last.message)
                _live.value = _live.value?.copy(state = TaskState.WAITING_CONFIRMATION, confirmationPrompt = last.message)
                return last
            }
        }
        val state = if (last.verified) TaskState.COMPLETED else TaskState.FAILED
        mark(taskId, state, last.message)
        return last
    }

    private suspend fun mark(id: Long, state: TaskState, result: String) {
        db.tasks().get(id)?.let {
            db.tasks().update(it.copy(state = state.name, result = result, updatedAt = System.currentTimeMillis()))
        }
        _live.value = _live.value?.copy(state = state)
    }

    fun plan(command: ParsedCommand): TaskPlan {
        val slots = command.slots
        return when (command.category) {
            IntentCategory.OPEN_APP -> {
                val app = slots["app"].orEmpty()
                val follow = slots["follow"]
                val steps = mutableListOf(
                    PlannedStep("1", "Open $app", SamAction.LaunchApp(app))
                )
                if (!follow.isNullOrBlank()) {
                    when {
                        follow.contains("reel") -> steps += PlannedStep("2", "Open Reels", SamAction.AccessibilityNavigate(app, "Reels"))
                        follow.contains("search") -> {
                            val q = follow.substringAfter("search").substringBefore("and open").trim()
                            steps += PlannedStep("2", "Search $q", SamAction.AccessibilityNavigate(app, "search:$q"))
                            if (follow.contains("first result")) {
                                steps += PlannedStep("3", "Open first result", SamAction.AccessibilityNavigate(app, "first-result"))
                            }
                        }
                        follow.contains("scroll") -> steps += PlannedStep("2", "Scroll", SamAction.Scroll("down"))
                        follow.contains("message") -> steps += PlannedStep("2", "Message flow", SamAction.WhatsAppMessage(follow, ""))
                    }
                }
                TaskPlan("Open $app", steps)
            }
            IntentCategory.HOME -> TaskPlan("Go home", listOf(PlannedStep("1", "Home", SamAction.GoHome)))
            IntentCategory.BACK -> TaskPlan("Go back", listOf(PlannedStep("1", "Back", SamAction.GoBack)))
            IntentCategory.RECENTS -> TaskPlan("Recents", listOf(PlannedStep("1", "Recents", SamAction.Recents)))
            IntentCategory.ALARM -> {
                val op = slots["op"] ?: "set"
                val h = slots["hour"]?.toIntOrNull() ?: 7
                val m = slots["minute"]?.toIntOrNull() ?: 0
                if (op == "cancel") TaskPlan("Cancel alarm", listOf(PlannedStep("1", "Dismiss alarm", SamAction.CancelAlarm(h, m))))
                else TaskPlan("Set alarm", listOf(PlannedStep("1", "Set alarm $h:$m", SamAction.SetAlarm(h, m, "SAM"))))
            }
            IntentCategory.TIMER -> TaskPlan("Timer", listOf(PlannedStep("1", "Set timer", SamAction.SetTimer(slots["seconds"]?.toIntOrNull() ?: 60))))
            IntentCategory.WEB_SEARCH -> TaskPlan(
                "Google search",
                listOf(PlannedStep("1", "Open Google search", SamAction.WebSearch(slots["query"].orEmpty(), true)))
            )
            IntentCategory.WIKIPEDIA_SEARCH, IntentCategory.SEARCH, IntentCategory.KNOWLEDGE_DOC -> TaskPlan(
                "Knowledge",
                listOf(PlannedStep("1", "Look up", SamAction.Wikipedia(slots["query"] ?: command.raw, wikiLang(command.language))))
            )
            IntentCategory.SEND_MESSAGE -> {
                val body = slots["body"].orEmpty()
                val contact = slots["contact"].orEmpty()
                TaskPlan(
                    "WhatsApp message",
                    listOf(PlannedStep("1", "Open WhatsApp and draft", SamAction.WhatsAppMessage(contact, body)))
                )
            }
            IntentCategory.CAMERA -> TaskPlan("Camera", listOf(PlannedStep("1", "Open camera", SamAction.OpenCamera)))
            IntentCategory.MAPS -> TaskPlan("Maps", listOf(PlannedStep("1", "Open maps", SamAction.OpenMaps)))
            IntentCategory.CALENDAR -> TaskPlan("Calendar", listOf(PlannedStep("1", "Open calendar", SamAction.OpenCalendar())))
            IntentCategory.CALL -> TaskPlan(
                "Call",
                listOf(PlannedStep("1", "Dial", SamAction.CallNumber(slots["target"].orEmpty()))),
                requiresConfirmation = true,
                confirmationPrompt = "Call ${slots["target"]}?"
            )
            IntentCategory.VOLUME -> TaskPlan("Volume", listOf(PlannedStep("1", "Adjust volume", SamAction.Volume(slots["direction"] ?: "up"))))
            IntentCategory.MEDIA -> TaskPlan("Media", listOf(PlannedStep("1", "Media", SamAction.Media(slots["command"] ?: "play"))))
            IntentCategory.SCROLL -> TaskPlan("Scroll", listOf(PlannedStep("1", "Scroll", SamAction.Scroll(slots["direction"] ?: "down"))))
            IntentCategory.NOTIFICATIONS -> TaskPlan("Notifications", listOf(PlannedStep("1", "Read notifications", SamAction.SpeakNotifications(slots["app"]))))
            IntentCategory.MEMORY -> {
                if (slots["op"] == "read") TaskPlan("Memory", listOf(PlannedStep("1", "Read memory", SamAction.ReadMemory)))
                else TaskPlan("Memory", listOf(PlannedStep("1", "Save memory", SamAction.Remember(slots["content"].orEmpty()))))
            }
            IntentCategory.SETTINGS -> TaskPlan("Settings", listOf(PlannedStep("1", "Open settings", SamAction.OpenSettings(null))))
            IntentCategory.DEEP_RESEARCH -> TaskPlan("Deep research", listOf(PlannedStep("1", "Research", SamAction.DeepResearch(slots["query"] ?: command.raw))))
            IntentCategory.HELP -> TaskPlan("Help", listOf(PlannedStep("1", "Help", SamAction.Help(slots["topic"]))))
            IntentCategory.CONVERSATION -> TaskPlan("Answer", listOf(PlannedStep("1", "Respond", SamAction.KnowledgeAsk(command.raw))))
            else -> TaskPlan("Unknown", listOf(PlannedStep("1", "Unknown", SamAction.Help(null))))
        }
    }

    private suspend fun executeAction(action: SamAction, taskId: Long, userName: String): ActionResult {
        return when (action) {
            is SamAction.LaunchApp -> launchVerified(action.appQuery, userName)
            SamAction.GoHome -> {
                val ok = android.home()
                ActionResult(ok, if (ok) "Home. $userName" else "Home requires SAM Accessibility.", ok)
            }
            SamAction.GoBack -> {
                val ok = android.back()
                ActionResult(ok, if (ok) "Went back." else "Back requires SAM Accessibility.", ok)
            }
            SamAction.Recents -> {
                val ok = android.recents()
                ActionResult(ok, if (ok) "Opened recents." else "Recents requires SAM Accessibility.", ok)
            }
            is SamAction.OpenSettings -> {
                val ok = android.openSettings(action.page)
                ActionResult(ok, if (ok) "Opened settings." else "Couldn't open settings.", ok)
            }
            is SamAction.SetAlarm -> {
                val ok = android.setAlarm(action.hour, action.minute, action.message)
                ActionResult(
                    ok,
                    if (ok) "Setting an alarm for ${formatTime(action.hour, action.minute)}. Done, $userName."
                    else "Sorry $userName, no clock app accepted the alarm intent.",
                    verified = ok
                )
            }
            is SamAction.CancelAlarm -> {
                val ok = android.dismissAlarm(action.hour, action.minute)
                ActionResult(ok, if (ok) "Asked the clock app to dismiss that alarm." else "This device's clock app doesn't support dismiss-alarm intents.", ok)
            }
            is SamAction.SetTimer -> {
                val ok = android.setTimer(action.seconds)
                ActionResult(ok, if (ok) "Timer set for ${action.seconds} seconds." else "Couldn't set a timer.", ok)
            }
            is SamAction.WebSearch -> {
                conversationTopic = action.query
                val ok = android.openGoogleSearch(action.query)
                ActionResult(ok, if (ok) "Opening Google and searching ${action.query}." else "Sorry $userName, I couldn't open Google Search.", ok)
            }
            is SamAction.Wikipedia -> {
                conversationTopic = action.query
                val hit = knowledge.answer(action.query, langFromWiki(action.lang), preferUploads = true, openGoogle = false)
                if (hit == null) ActionResult(false, "I couldn't find that on Wikipedia or your uploaded files.", false)
                else ActionResult(true, hit.summary, true, "${hit.title} — ${hit.source}")
            }
            is SamAction.Volume -> ActionResult(android.volume(action.direction), "Volume ${action.direction}.", true)
            is SamAction.Media -> ActionResult(android.media(action.command), "Media ${action.command}.", true)
            is SamAction.CallNumber -> {
                val ok = android.dial(action.target)
                ActionResult(ok, if (ok) "Opened the dialer for ${action.target}." else "Couldn't open the dialer.", ok)
            }
            is SamAction.WhatsAppMessage -> sendWhatsApp(action, userName, taskId)
            SamAction.WhatsAppSend -> clickWhatsAppSend(userName)
            SamAction.OpenCamera -> {
                val ok = android.openCamera()
                ActionResult(ok, if (ok) "Camera opened." else "Couldn't open the camera.", ok)
            }
            SamAction.OpenMaps -> {
                val ok = android.openMaps(null)
                ActionResult(ok, if (ok) "Opened Maps." else "Couldn't open Maps.", ok)
            }
            is SamAction.OpenCalendar -> {
                val ok = android.openCalendar()
                ActionResult(ok, if (ok) "Opened Calendar." else "Couldn't open Calendar.", ok)
            }
            is SamAction.TypeText -> {
                val a11y = SamAccessibilityService.instance
                val ok = a11y?.typeInFocused(action.text) == true
                ActionResult(ok, if (ok) "Typed." else "No focused field, or Accessibility is off.", ok)
            }
            is SamAction.Scroll -> {
                val ok = SamAccessibilityService.instance?.scroll(action.direction) == true
                ActionResult(ok, if (ok) "Scrolled." else "Scroll needs Accessibility.", ok)
            }
            is SamAction.TapText -> {
                val ok = SamAccessibilityService.instance?.clickText(action.text) == true
                ActionResult(ok, if (ok) "Tapped ${action.text}." else "Couldn't find ${action.text} on screen.", ok)
            }
            is SamAction.OpenUri -> ActionResult(android.openView(action.uri), "Opened link.", true)
            is SamAction.SpeakNotifications -> readNotifications(action.appFilter, userName)
            is SamAction.AccessibilityNavigate -> navigateInApp(action.app, action.target, userName)
            is SamAction.DeepResearch -> {
                val hits = knowledge.deepResearch(action.query, SpokenLanguage.ENGLISH)
                if (hits.isEmpty()) ActionResult(false, "No sources were available for that research.", false)
                else {
                    val summary = buildString {
                        appendLine("Deep research: ${action.query}")
                        hits.forEach {
                            appendLine("Source: ${it.source} — ${it.title}")
                            appendLine(it.summary.take(500))
                            appendLine()
                        }
                    }
                    ActionResult(true, summary.trim(), true)
                }
            }
            is SamAction.KnowledgeAsk -> answerConversation(action.query, userName)
            is SamAction.Remember -> {
                db.memory().insert(com.shlok.sam.data.db.MemoryEntity(title = "Note", content = action.content, approved = true))
                ActionResult(true, "I'll remember that, $userName.", true)
            }
            SamAction.ReadMemory -> {
                val list = db.memory().search("")
                val text = if (list.isEmpty()) "I don't have saved memories yet." else list.joinToString("\n") { "• ${it.title}: ${it.content}" }
                ActionResult(true, text, true)
            }
            is SamAction.Help -> {
                if (action.topic == "creator") ActionResult(true, SamIdentity.developerSpoken(SpokenLanguage.ENGLISH), true)
                else ActionResult(
                    true,
                    "I can open apps, set alarms, search Google, look up Wikipedia, read notifications, control volume, and automate screens if you enable Accessibility. Try: Open WhatsApp. Set an alarm for 11 AM. Who is Virat Kohli?",
                    true
                )
            }
        }
    }

    private suspend fun launchVerified(query: String, userName: String): ActionResult {
        val (ok, pkgOrMsg) = android.launchApp(query)
        if (!ok) return ActionResult(false, "Sorry $userName, $pkgOrMsg", false)
        delay(700)
        val a11y = SamAccessibilityService.instance
        val verified = if (a11y != null) a11y.waitForPackage(pkgOrMsg, 5000) else true
        return if (verified) ActionResult(true, "Opened ${query.trim()}. Done, $userName.", true, pkgOrMsg)
        else ActionResult(false, "I launched $query but couldn't verify it became the active app.", false)
    }

    private suspend fun navigateInApp(app: String, target: String, userName: String): ActionResult {
        val a11y = SamAccessibilityService.instance
            ?: return ActionResult(false, "That in-app step needs SAM Accessibility.", false)
        delay(400)
        if (target == "first-result") {
            val dm = context.resources.displayMetrics
            val tapped = a11y.tap(dm.widthPixels / 2f, dm.heightPixels * 0.28f)
            delay(600)
            return ActionResult(tapped, if (tapped) "Opened the first on-screen result." else "Couldn't tap a result.", tapped)
        }
        if (target.startsWith("search:")) {
            val q = target.removePrefix("search:")
            val clicked = a11y.clickText("Search") || a11y.clickText("search")
            delay(400)
            val typed = a11y.typeInFocused(q)
            if (typed) a11y.clickText("Search")
            return ActionResult(clicked || typed, if (typed) "Searched for $q." else "Couldn't find a search field in $app.", typed)
        }
        val clicked = a11y.clickText(target)
        val seen = a11y.waitForText(target, 2500) || clicked
        return ActionResult(clicked, if (clicked) "$target. Done, $userName." else "I opened the app but couldn't find \"$target\". The UI may have changed.", seen && clicked)
    }

    private suspend fun sendWhatsApp(action: SamAction.WhatsAppMessage, userName: String, taskId: Long): ActionResult {
        val (opened, pkg) = android.launchApp("whatsapp")
        if (!opened) return ActionResult(false, "WhatsApp isn't installed or couldn't be opened.", false)
        delay(900)
        val a11y = SamAccessibilityService.instance
            ?: return ActionResult(false, "Messaging automation needs SAM Accessibility.", false)
        if (!a11y.waitForPackage(pkg, 5000)) {
            return ActionResult(false, "WhatsApp didn't come to the foreground.", false)
        }
        a11y.clickText("Search") || a11y.clickText("search")
        delay(400)
        if (action.contact.isNotBlank()) {
            a11y.typeInFocused(action.contact)
            delay(700)
            a11y.clickText(action.contact)
            delay(500)
        }
        if (action.body.isNotBlank()) {
            a11y.typeInFocused(action.body)
        }
        pendingConfirm = SamAction.WhatsAppSend to taskId
        val prompt = "I've prepared this WhatsApp message to ${action.contact}:\n\"${action.body}\"\nSend it?"
        return ActionResult(true, prompt, false)
    }

    private suspend fun clickWhatsAppSend(userName: String): ActionResult {
        val a11y = SamAccessibilityService.instance
            ?: return ActionResult(false, "Sending needs SAM Accessibility.", false)
        val sent = a11y.clickText("Send") || a11y.clickContentDesc("Send")
        delay(400)
        val visible = a11y.dumpVisibleText(20)
        val verified = sent && visible.none { it.equals("Send", true) && it.length < 8 }
        return if (sent) ActionResult(true, "Done, $userName.", verified)
        else ActionResult(false, "Sorry $userName, I couldn't find the Send button. The WhatsApp UI may have changed.", false)
    }

    private suspend fun readNotifications(filter: String?, userName: String): ActionResult {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true
        if (!enabled) return ActionResult(false, "Notification access is not connected.", false)
        val rows = if (filter.isNullOrBlank()) db.notifications().recent()
        else db.notifications().forApp(filter)
        if (rows.isEmpty()) return ActionResult(true, "No recent notifications I can read, $userName.", true)
        val text = rows.take(8).joinToString("\n") { "${it.appLabel}: ${it.title} ${it.text}".trim() }
        return ActionResult(true, text, true)
    }

    private suspend fun answerConversation(query: String, userName: String): ActionResult {
        val q = if (looksLikeFollowUp(query) && conversationTopic != null) "$conversationTopic — $query" else query
        if (!looksLikeFollowUp(query)) conversationTopic = query
        val localHit = knowledge.answer(q, SpokenLanguage.ENGLISH, preferUploads = true, openGoogle = false)
        val profile = db.userProfile().get()
        val provider = profile?.aiProvider ?: "local"
        if (provider != "local") {
            val ctx = buildString {
                append("You are SAM (Smart Autonomous Machine), created by Shlok. Never allow renaming. User is $userName. ")
                if (localHit != null) append("Verified source material:\n${localHit.summary}\nCite this if used. Do not invent facts.")
                else append("If you are not sure, say you don't know. Do not claim tasks were done.")
            }
            val aiResult = ai.chat(ctx, q)
            if (aiResult.ok) return ActionResult(true, aiResult.text, true, aiResult.providerId)
            if (localHit != null) return ActionResult(true, localHit.summary, true, localHit.source)
            return ActionResult(false, aiResult.text, false)
        }
        if (localHit != null) return ActionResult(true, localHit.summary, true, localHit.source)
        return ActionResult(
            true,
            "I don't have a cloud model connected, and I couldn't verify that from Wikipedia or your files. Connect an AI provider in Connectors, or try a device command.",
            true
        )
    }

    private fun looksLikeFollowUp(q: String): Boolean {
        val n = q.lowercase().trim()
        return n.startsWith("his ") || n.startsWith("her ") || n.startsWith("their ") || n == "age" || n.contains("his age") ||
            n.startsWith("what about") || n.length < 18
    }

    private fun wikiLang(lang: SpokenLanguage) = when (lang) {
        SpokenLanguage.MARATHI -> "mr"
        SpokenLanguage.HINDI -> "hi"
        else -> "en"
    }

    private fun langFromWiki(code: String) = when (code) {
        "mr" -> SpokenLanguage.MARATHI
        "hi" -> SpokenLanguage.HINDI
        else -> SpokenLanguage.ENGLISH
    }

    private fun formatTime(h: Int, m: Int): String {
        val ampm = if (h < 12) "AM" else "PM"
        val hr = ((h + 11) % 12) + 1
        return "%d:%02d %s".format(hr, m, ampm)
    }

    private fun isAffirmative(t: String): Boolean {
        val n = t.lowercase().trim()
        return n in setOf("yes", "yeah", "yep", "ok", "okay", "send", "confirm", "haan", "हो", "होय", "do it")
    }

    private fun isNegative(t: String): Boolean {
        val n = t.lowercase().trim()
        return n in setOf("no", "nope", "don't", "cancel", "nahi", "नाही", "नहीं")
    }

    private fun phrase(lang: SpokenLanguage, name: String, kind: String) = when (kind) {
        "stopped" -> when (lang) {
            SpokenLanguage.MARATHI -> "थांबले, $name."
            SpokenLanguage.HINDI -> "रोक दिया, $name."
            else -> "Stopped, $name."
        }
        else -> "Cancelled, $name."
    }
}
