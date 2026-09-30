package com.shlok.sam.engine.command

import com.shlok.sam.domain.model.IntentCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private val parser = CommandParser()

    @Test
    fun openWhatsAppEnglish() {
        val p = parser.parse("Open WhatsApp")
        assertEquals(IntentCategory.OPEN_APP, p.category)
        assertTrue(p.slots["app"].orEmpty().contains("whatsapp"))
    }

    @Test
    fun openWhatsAppHindi() {
        val p = parser.parse("WhatsApp kholo")
        assertEquals(IntentCategory.OPEN_APP, p.category)
    }

    @Test
    fun openWhatsAppMarathiMixed() {
        val p = parser.parse("Sam WhatsApp open kar")
        assertEquals(IntentCategory.OPEN_APP, p.category)
    }

    @Test
    fun wikipediaWhoIs() {
        val p = parser.parse("Who is Virat Kohli?")
        assertEquals(IntentCategory.WIKIPEDIA_SEARCH, p.category)
    }

    @Test
    fun alarmElevenAm() {
        val p = parser.parse("Set an alarm for 11 AM")
        assertEquals(IntentCategory.ALARM, p.category)
        assertEquals("11", p.slots["hour"])
    }

    @Test
    fun stop() {
        assertEquals(IntentCategory.STOP, parser.parse("Stop").category)
    }

    @Test
    fun creator() {
        val p = parser.parse("Who created you?")
        assertEquals(IntentCategory.HELP, p.category)
        assertEquals("creator", p.slots["topic"])
    }

    @Test
    fun googleSearch() {
        val p = parser.parse("Open Google and search Virat Kohli")
        assertEquals(IntentCategory.WEB_SEARCH, p.category)
    }

    @Test
    fun marathiAlarm() {
        val p = parser.parse("11 vajta alarm lav")
        assertEquals(IntentCategory.ALARM, p.category)
        assertEquals("11", p.slots["hour"])
    }

    @Test
    fun hindiWho() {
        val p = parser.parse("Virat Kohli कौन है?")
        assertEquals(IntentCategory.WIKIPEDIA_SEARCH, p.category)
    }
}
