package com.mimora.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateRulesTest {

    @Test
    fun isDateAllowedForWriting_disallowsFutureDates() {
        val today = LocalDate.of(2026, 11, 22)
        val tomorrow = today.plusDays(1)
        val nextYear = today.plusYears(1)

        assertFalse(DateRules.isDateAllowedForWriting(tomorrow, referenceDate = today))
        assertFalse(DateRules.isDateAllowedForWriting(nextYear, referenceDate = today))
    }

    @Test
    fun isDateAllowedForWriting_allowsTodayAndPastDates() {
        val today = LocalDate.of(2026, 11, 22)
        val yesterday = today.minusDays(1)
        val pastYear = today.minusYears(2)

        assertTrue(DateRules.isDateAllowedForWriting(today, referenceDate = today))
        assertTrue(DateRules.isDateAllowedForWriting(yesterday, referenceDate = today))
        assertTrue(DateRules.isDateAllowedForWriting(pastYear, referenceDate = today))
    }

    @Test
    fun dateFormattingAndParsing_roundtripsCorrectly() {
        val originalDate = LocalDate.of(2026, 5, 15)
        val formatted = DateRules.formatDate(originalDate)

        assertEquals("2026-05-15", formatted)
        val parsed = DateRules.parseDate(formatted)
        assertEquals(originalDate, parsed)
    }

    @Test
    fun diaryEntry_flagsComputedCorrectly() {
        val date = LocalDate.of(2026, 11, 22)
        val emptyEntry = DiaryEntry(id = "1", date = date, content = "")
        assertTrue(emptyEntry.isEmpty)
        assertFalse(emptyEntry.hasText)
        assertFalse(emptyEntry.hasPhoto)
        assertFalse(emptyEntry.hasVoice)

        val textEntry = DiaryEntry(id = "2", date = date, content = "Reflecting on the peaceful day.")
        assertFalse(textEntry.isEmpty)
        assertTrue(textEntry.hasText)

        val photoAtt = MediaAttachment("p1", date, MediaType.PHOTO, "/path/to/photo.jpg")
        val voiceAtt = MediaAttachment("v1", date, MediaType.VOICE, "/path/to/voice.m4a", durationSeconds = 12)

        val mediaEntry = DiaryEntry(id = "3", date = date, content = "", attachments = listOf(photoAtt, voiceAtt))
        assertFalse(mediaEntry.isEmpty)
        assertFalse(mediaEntry.hasText)
        assertTrue(mediaEntry.hasPhoto)
        assertTrue(mediaEntry.hasVoice)
    }
}
