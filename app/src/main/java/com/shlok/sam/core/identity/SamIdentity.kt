package com.shlok.sam.core.identity

/**
 * Protected SAM identity. There is no user-facing setting that can rename the assistant.
 */
object SamIdentity {
    const val SHORT_NAME = "SAM"
    const val STYLED_NAME = "S.A.M."
    const val EXPANSION = "Smart Autonomous Machine"
    const val WAKE_PRIMARY = "sam"
    const val WAKE_SECONDARY = "hey sam"

    const val DEVELOPER_NAME = "Shlok"
    const val DEVELOPER_ROLE = "Data Science Student"
    const val DEVELOPER_DESCRIPTION = "AI Enthusiast"
    const val INSPIRATION = "Tony Stark's JARVIS concept"
    const val GITHUB = "theshlok18"
    const val INSTAGRAM = "iishlok23"

    fun aboutText(): String = buildString {
        appendLine(STYLED_NAME)
        appendLine(EXPANSION)
        appendLine()
        appendLine("Developer: $DEVELOPER_NAME")
        appendLine(DEVELOPER_ROLE)
        appendLine(DEVELOPER_DESCRIPTION)
        appendLine()
        appendLine("Inspired by $INSPIRATION.")
        appendLine("GitHub: $GITHUB")
        appendLine("Instagram: $INSTAGRAM")
    }

    fun developerSpoken(lang: SpokenLanguage): String = when (lang) {
        SpokenLanguage.MARATHI ->
            "माझं नाव SAM आहे, Smart Autonomous Machine. मला $DEVELOPER_NAME ने बनवलं आहे. ते Data Science Student आणि AI Enthusiast आहेत. प्रेरणा $INSPIRATION आहे."
        SpokenLanguage.HINDI ->
            "मेरा नाम SAM है, Smart Autonomous Machine. मुझे $DEVELOPER_NAME ने बनाया है। वे Data Science Student और AI Enthusiast हैं। प्रेरणा $INSPIRATION है।"
        SpokenLanguage.ENGLISH,
        SpokenLanguage.MIXED ->
            "I'm SAM, Smart Autonomous Machine. I was created by $DEVELOPER_NAME, a $DEVELOPER_ROLE and $DEVELOPER_DESCRIPTION. The inspiration is $INSPIRATION."
    }

    fun isCreatorQuestion(text: String): Boolean {
        val n = text.lowercase()
        return listOf(
            "who created you", "who developed you", "who made you", "who made sam",
            "who built you", "who is your developer", "who is your creator",
            "तुला कोणी बनवल", "तुला कोणी तयार", "कोणी बनवलं", "developer कोण",
            "तुम्हें किसने बनाया", "किसने बनाया", "तुम्हारा डेवलपर", "श्लोक"
        ).any { n.contains(it) } || (n.contains("who") && n.contains("creat")) ||
            (n.contains("developer") && (n.contains("who") || n.contains("kon") || n.contains("kaun")))
    }
}

enum class SpokenLanguage { ENGLISH, HINDI, MARATHI, MIXED }
