package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakVoiceLabelTest {

    @Test
    fun `each position gets its own name`() {
        val names = (1..MAX_SPEAK_VOICE_CHOICES).map { speakVoiceLabel("hi-in-x-hid-local", it) }

        assertEquals(
            listOf(
                "Aarav", "Diya", "Kabir", "Meera", "Rohan",
                "Saanvi", "Vikram", "Anaya", "Arjun", "Priya"
            ),
            names
        )
    }

    @Test
    fun `no two offered voices share a name`() {
        val names = (1..MAX_SPEAK_VOICE_CHOICES).map { speakVoiceLabel("voice-$it", it) }

        assertEquals(MAX_SPEAK_VOICE_CHOICES, names.toSet().size)
    }

    @Test
    fun `ten voices are offered`() {
        assertEquals(10, MAX_SPEAK_VOICE_CHOICES)
    }

    @Test
    fun `a gender the engine revealed is appended to the name`() {
        assertEquals("Aarav (male)", speakVoiceLabel("en-us-x-sfg#male_1-local", position = 1))
        assertEquals("Kabir (female)", speakVoiceLabel("en-us-x-tpd#female_2-local", position = 3))
    }

    @Test
    fun `a name the engine says nothing about carries no gender`() {
        // Google's Indian-language voices hold only an opaque variant code.
        assertEquals("Diya", speakVoiceLabel("hi-in-x-hia-local", position = 2))
        assertEquals("Meera", speakVoiceLabel("gu-in-x-guf-local", position = 4))
    }

    @Test
    fun `gender is read whatever separator and case the engine used`() {
        assertEquals("male", speakVoiceGender("EN-US-X-SFG#MALE_1-LOCAL"))
        assertEquals("female", speakVoiceGender("some-engine-female-voice"))
        assertEquals("male", speakVoiceGender("some_engine_male_2"))
    }

    @Test
    fun `an opaque name has no gender`() {
        assertNull(speakVoiceGender("hi-in-x-hid-local"))
        assertNull(speakVoiceGender(""))
    }

    @Test
    fun `a position past the offered names still reads distinctly`() {
        // Guards the cap: were the picker ever to show more than it names,
        // the extra rows must stay selectable rather than collapse to blank.
        val extra = speakVoiceLabel("hi-in-x-hid-local", position = MAX_SPEAK_VOICE_CHOICES + 1)

        assertEquals("Voice 11", extra)
        assertTrue(extra.isNotBlank())
    }
}
