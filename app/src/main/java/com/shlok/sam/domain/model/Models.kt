package com.shlok.sam.domain.model

enum class SamCoreState {
    IDLE, LISTENING, THINKING, EXECUTING, SPEAKING, SUCCESS, ERROR
}

enum class TaskState {
    PENDING, PLANNING, RUNNING, WAITING_PERMISSION, WAITING_CONFIRMATION, VERIFYING, COMPLETED, FAILED, CANCELLED
}

enum class IntentCategory {
    OPEN_APP, SEARCH, WEB_SEARCH, WIKIPEDIA_SEARCH, SEND_MESSAGE, CALL, ALARM, TIMER,
    NAVIGATION, SCROLL, TAP, TYPE, BACK, HOME, RECENTS, NOTIFICATIONS, MEMORY, SETTINGS,
    MEDIA, VOLUME, CANCEL, STOP, HELP, CONVERSATION, VISUAL_LENS, DEEP_RESEARCH,
    CAMERA, MAPS, BROWSER, CONTACTS, PHONE, CALENDAR, KNOWLEDGE_DOC, UNKNOWN
}

enum class SystemMode { NORMAL, VOICE_ASSISTANT, DRIVING, FOCUS, SILENT, DEVELOPER }

enum class OrbType { CLASSIC, ENERGY, NEON, GALAXY, MINIMAL, CUSTOM }

enum class OrbSizePreset { SMALL, MEDIUM, LARGE, CUSTOM }

enum class AuraColor { BLUE, CYAN, PURPLE, GREEN, RED, CUSTOM }

enum class ResponseStyle { CONCISE, BALANCED, DETAILED }

enum class VoicePreference { ANDROID_DEFAULT, FEMALE, MALE, ELEVENLABS }

data class ParsedCommand(
    val raw: String,
    val language: com.shlok.sam.core.identity.SpokenLanguage,
    val category: IntentCategory,
    val slots: Map<String, String> = emptyMap(),
    val confidence: Float = 0.7f
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val verified: Boolean = false,
    val details: String? = null
)

data class TaskPlan(
    val title: String,
    val steps: List<PlannedStep>,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)

data class PlannedStep(
    val id: String,
    val label: String,
    val action: SamAction
)

sealed class SamAction {
    data class LaunchApp(val appQuery: String, val packageHint: String? = null) : SamAction()
    data object GoHome : SamAction()
    data object GoBack : SamAction()
    data object Recents : SamAction()
    data class OpenSettings(val page: String? = null) : SamAction()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String? = null, val days: List<Int>? = null) : SamAction()
    data class CancelAlarm(val hour: Int?, val minute: Int?) : SamAction()
    data class SetTimer(val seconds: Int) : SamAction()
    data class WebSearch(val query: String, val openBrowser: Boolean) : SamAction()
    data class Wikipedia(val query: String, val lang: String) : SamAction()
    data class Volume(val direction: String) : SamAction()
    data class Media(val command: String) : SamAction()
    data class CallNumber(val target: String) : SamAction()
    data class WhatsAppMessage(val contact: String, val body: String) : SamAction()
    data object WhatsAppSend : SamAction()
    data class OpenCalendar(val title: String? = null) : SamAction()
    data object OpenMaps : SamAction()
    data object OpenCamera : SamAction()
    data class TypeText(val text: String) : SamAction()
    data class Scroll(val direction: String) : SamAction()
    data class TapText(val text: String) : SamAction()
    data class OpenUri(val uri: String) : SamAction()
    data class SpeakNotifications(val appFilter: String?) : SamAction()
    data class AccessibilityNavigate(val app: String, val target: String) : SamAction()
    data class DeepResearch(val query: String) : SamAction()
    data class KnowledgeAsk(val query: String) : SamAction()
    data class Remember(val content: String) : SamAction()
    data object ReadMemory : SamAction()
    data class Help(val topic: String? = null) : SamAction()
}
