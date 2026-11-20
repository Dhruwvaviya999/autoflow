package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakVoiceLabelTest {

    @Test
    fun `a gendered Google voice name reads as its gender and index`() {
        assertEquals("Male 1", speakVoiceGenderLabel("en-us-x-sfg#male_1-local"))
        assertEquals("Female 2", speakVoiceGenderLabel("en-us-x-tpd#female_2-local"))
        assertEquals("Male 3", speakVoiceGenderLabel("en-gb-x-gbb#male_3-local"))
    }

    @Test
    fun `gender is matched whatever separator the engine used`() {
        assertEquals("Female", speakVoiceGenderLabel("some-engine-female-voice"))
        assertEquals("Male 2", speakVoiceGenderLabel("some_engine_male_2"))
    }

    @Test
    fun `gender is matched case-insensitively`() {
        assertEquals("Male 1", speakVoiceGenderLabel("EN-US-X-SFG#MALE_1-LOCAL"))
    }

    @Test
    fun `a name that says nothing about gender has no gender label`() {
        // Google's Indian-language voices carry only an opaque variant code.
        assertNull(speakVoiceGenderLabel("hi-in-x-hid-local"))
        assertNull(speakVoiceGenderLabel("gu-in-x-guf-local"))
        assertNull(speakVoiceGenderLabel(""))
    }

    @Test
    fun `the picker falls back to the voice's position`() {
        assertEquals("Voice 1", speakVoiceLabel("hi-in-x-hia-local", position = 1))
        assertEquals("Voice 4", speakVoiceLabel("hi-in-x-hid-local", position = 4))
    }

    @Test
    fun `a gendered name wins over the position fallback`() {
        assertEquals("Male 1", speakVoiceLabel("en-us-x-sfg#male_1-local", position = 7))
    }
}
