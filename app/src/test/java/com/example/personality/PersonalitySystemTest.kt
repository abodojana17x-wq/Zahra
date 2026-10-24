package com.example.personality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the Personality System
 */
@RunWith(RobolectricTestRunner::class)
class PersonalitySystemTest {
    
    private lateinit var context: Context
    private lateinit var personalityManager: PersonalityManager
    private lateinit var personalityStorage: PersonalityStorage
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        personalityManager = PersonalityManager(context)
        personalityStorage = PersonalityStorage(context)
        
        // Clear any existing preferences
        personalityStorage.deletePersonality()
    }
    
    // ==================== PersonalityPreferences Tests ====================
    
    @Test
    fun testDefaultPersonality() {
        val default = PersonalityPreferences.getDefault()
        
        assertEquals(CommunicationTone.FRIENDLY_WARM, default.tone)
        assertEquals(CommunicationStyle.BALANCED, default.style)
        assertEquals(CommunicationLanguage.MODERN_STANDARD_ARABIC, default.language)
        assertEquals(HumorFrequency.OCCASIONAL, default.humorFrequency)
        assertEquals(EmojiFrequency.MODERATE, default.emojiFrequency)
        assertEquals(EnergyLevel.MODERATE, default.energyLevel)
        assertEquals(true, default.useExamples)
        assertEquals(true, default.useAnalogies)
        assertEquals(true, default.useStorytelling)
        assertEquals(FormalityLevel.MODERATE, default.formality)
        assertEquals(ElaborationLevel.MODERATE, default.elaboration)
        assertEquals(IslamicReferenceDepth.BALANCED, default.islamicReferenceDepth)
        assertEquals(true, default.isDefault)
    }
    
    @Test
    fun testPersonalityFromNaturalLanguage() {
        val egyptian = PersonalityPreferences.fromNaturalLanguage("talk to me in egyptian arabic")
        assertEquals(CommunicationLanguage.EGYPTIAN_ARABIC, egyptian.language)
        
        val serious = PersonalityPreferences.fromNaturalLanguage("be more serious")
        assertEquals(CommunicationTone.SERIOUS_DIRECT, serious.tone)
        
        val concise = PersonalityPreferences.fromNaturalLanguage("give me the short answer")
        assertEquals(CommunicationStyle.CONCISE, concise.style)
        
        val noEmoji = PersonalityPreferences.fromNaturalLanguage("no emojis")
        assertEquals(EmojiFrequency.NONE, noEmoji.emojiFrequency)
        
        val funny = PersonalityPreferences.fromNaturalLanguage("be funnier")
        assertEquals(HumorFrequency.FREQUENT, funny.humorFrequency)
    }
    
    @Test
    fun testPersonalitySummary() {
        val preferences = PersonalityPreferences.getDefault()
        val summary = preferences.getSummary()
        
        assertTrue(summary.contains("ودود ودافئ"))
        assertTrue(summary.contains("متوازن"))
        assertTrue(summary.contains("العربية الفصحى"))
    }
    
    @Test
    fun testPersonalityIsDifferent() {
        val default = PersonalityPreferences.getDefault()
        val modified = default.copy(tone = CommunicationTone.SERIOUS_DIRECT)
        
        assertTrue(default.isDifferentFrom(modified))
        assertFalse(default.isDifferentFrom(default))
    }
    
    // ==================== PersonalityManager Tests ====================
    
    @Test
    fun testGetCurrentPersonality_returnsDefaultWhenEmpty() {
        val personality = personalityManager.getCurrentPersonality()
        
        assertEquals(CommunicationTone.FRIENDLY_WARM, personality.tone)
        assertTrue(personality.isDefault)
    }
    
    @Test
    fun testSetPersonality() {
        val newPersonality = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.SERIOUS_DIRECT,
            style = CommunicationStyle.CONCISE
        )
        
        val result = personalityManager.setPersonality(newPersonality)
        
        assertTrue(result is PersonalityChangeResult.Success)
        
        val stored = personalityManager.getCurrentPersonality()
        assertEquals(CommunicationTone.SERIOUS_DIRECT, stored.tone)
        assertEquals(CommunicationStyle.CONCISE, stored.style)
    }
    
    @Test
    fun testSetPersonality_tracksChangedFields() {
        val newPersonality = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.SERIOUS_DIRECT
        )
        
        val result = personalityManager.setPersonality(newPersonality)
        
        if (result is PersonalityChangeResult.Success) {
            assertTrue(result.changedFields.contains("tone"))
            assertFalse(result.changedFields.contains("style"))
        } else {
            fail("Expected Success result")
        }
    }
    
    @Test
    fun testResetToDefault() {
        // First, set a custom personality
        val custom = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.PLAYFUL
        )
        personalityManager.setPersonality(custom)
        
        // Then reset
        val result = personalityManager.resetToDefault()
        
        assertTrue(result is PersonalityChangeResult.Success)
        
        val stored = personalityManager.getCurrentPersonality()
        assertEquals(CommunicationTone.FRIENDLY_WARM, stored.tone)
    }
    
    @Test
    fun testDetectPersonalityChange_command() {
        val command = personalityManager.detectPersonalityChange("be more serious")
        assertTrue(command is PersonalityCommand.SetTone)
        
        val command2 = personalityManager.detectPersonalityChange("use egyptian arabic")
        assertTrue(command2 is PersonalityCommand.SetLanguage)
    }
    
    @Test
    fun testDetectPersonalityChange_noChange() {
        val command = personalityManager.detectPersonalityChange("what is the capital of Egypt?")
        assertNull(command)
    }
    
    @Test
    fun testHandlePersonalityCommand() {
        val result = personalityManager.handlePersonalityCommand(
            PersonalityCommand.SetTone(CommunicationTone.SERIOUS_DIRECT)
        )
        
        assertTrue(result is PersonalityChangeResult.Success)
        
        val stored = personalityManager.getCurrentPersonality()
        assertEquals(CommunicationTone.SERIOUS_DIRECT, stored.tone)
    }
    
    @Test
    fun testHandlePersonalityCommand_reset() {
        // Set a custom personality first
        personalityManager.setPersonality(
            PersonalityPreferences.getDefault().copy(
                tone = CommunicationTone.PLAYFUL
            )
        )
        
        val result = personalityManager.handlePersonalityCommand(
            PersonalityCommand.ResetToDefault
        )
        
        assertTrue(result is PersonalityChangeResult.Success)
        
        val stored = personalityManager.getCurrentPersonality()
        assertEquals(CommunicationTone.FRIENDLY_WARM, stored.tone)
    }
    
    // ==================== PersonalityStorage Tests ====================
    
    @Test
    fun testSaveAndLoadPersonality() {
        val preferences = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.CALM_THOUGHTFUL,
            style = CommunicationStyle.DEEP_EDUCATIONAL
        )
        
        assertTrue(personalityStorage.savePersonality(preferences))
        
        val loaded = personalityStorage.loadPersonality()
        assertNotNull(loaded)
        assertEquals(CommunicationTone.CALM_THOUGHTFUL, loaded?.tone)
        assertEquals(CommunicationStyle.DEEP_EDUCATIONAL, loaded?.style)
    }
    
    @Test
    fun testHasPersonality() {
        assertFalse(personalityStorage.hasPersonality())
        
        val preferences = PersonalityPreferences.getDefault()
        personalityStorage.savePersonality(preferences)
        
        assertTrue(personalityStorage.hasPersonality())
    }
    
    @Test
    fun testDeletePersonality() {
        val preferences = PersonalityPreferences.getDefault()
        personalityStorage.savePersonality(preferences)
        
        assertTrue(personalityStorage.hasPersonality())
        
        assertTrue(personalityStorage.deletePersonality())
        
        assertFalse(personalityStorage.hasPersonality())
    }
    
    @Test
    fun testExportAndImportPersonality() {
        val preferences = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.PROFESSIONAL
        )
        personalityStorage.savePersonality(preferences)
        
        val exported = personalityStorage.exportPersonality()
        assertNotNull(exported)
        
        // Clear and import
        personalityStorage.deletePersonality()
        assertTrue(personalityStorage.importPersonality(exported!!))
        
        val imported = personalityStorage.loadPersonality()
        assertNotNull(imported)
        assertEquals(CommunicationTone.PROFESSIONAL, imported?.tone)
    }
    
    @Test
    fun testGetPresets() {
        val presets = personalityStorage.getPresets()
        
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.any { it.id == "default" })
        assertTrue(presets.any { it.id == "friendly_teacher" })
        assertTrue(presets.any { it.id == "serious_professional" })
    }
    
    @Test
    fun testApplyPreset() {
        assertTrue(personalityStorage.applyPreset("serious_professional"))
        
        val loaded = personalityStorage.loadPersonality()
        assertNotNull(loaded)
        assertEquals(CommunicationTone.SERIOUS_DIRECT, loaded?.tone)
        assertEquals(CommunicationStyle.CONCISE, loaded?.style)
    }
    
    @Test
    fun testApplyPreset_invalid() {
        assertFalse(personalityStorage.applyPreset("nonexistent"))
    }
    
    // ==================== StyleApplier Tests ====================
    
    @Test
    fun testGetGreeting_arabic() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.MODERN_STANDARD_ARABIC,
            tone = CommunicationTone.FRIENDLY_WARM
        )
        
        val greeting = StyleApplier.getGreeting(preferences)
        
        assertTrue(greeting.contains("أهلاً") || greeting.contains("مرحباً"))
    }
    
    @Test
    fun testGetGreeting_english() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.ENGLISH,
            tone = CommunicationTone.FRIENDLY_WARM
        )
        
        val greeting = StyleApplier.getGreeting(preferences)
        
        assertTrue(greeting.contains("Hello") || greeting.contains("Hi"))
    }
    
    @Test
    fun testGetFarewell_arabic() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.MODERN_STANDARD_ARABIC
        )
        
        val farewell = StyleApplier.getFarewell(preferences)
        
        assertTrue(farewell.contains("مع السلامة") || farewell.contains("إلى اللقاء"))
    }
    
    @Test
    fun testGetFarewell_english() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.ENGLISH
        )
        
        val farewell = StyleApplier.getFarewell(preferences)
        
        assertTrue(farewell.contains("Goodbye") || farewell.contains("Farewell"))
    }
    
    @Test
    fun testGetConfirmation() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.ENGLISH,
            tone = CommunicationTone.FRIENDLY_WARM
        )
        
        val confirmation = StyleApplier.getConfirmation(preferences)
        
        assertTrue(confirmation.contains("Done") || confirmation.contains("completed"))
    }
    
    @Test
    fun testGetResponsePrefix_question() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.ENGLISH,
            tone = CommunicationTone.FRIENDLY_WARM
        )
        
        val prefix = StyleApplier.getResponsePrefix(preferences, isQuestion = true)
        
        assertTrue(prefix.isNotEmpty())
    }
    
    @Test
    fun testGetResponsePrefix_notQuestion() {
        val preferences = PersonalityPreferences.getDefault().copy(
            language = CommunicationLanguage.ENGLISH,
            tone = CommunicationTone.SERIOUS_DIRECT
        )
        
        val prefix = StyleApplier.getResponsePrefix(preferences, isQuestion = false)
        
        // Serious direct might have empty prefix for non-questions
        assertNotNull(prefix)
    }
    
    @Test
    fun testFormatList_concise() {
        val preferences = PersonalityPreferences.getDefault().copy(
            style = CommunicationStyle.CONCISE
        )
        
        val items = listOf("Item 1", "Item 2", "Item 3")
        val formatted = StyleApplier.formatList(items, preferences)
        
        assertEquals("Item 1, Item 2, Item 3", formatted)
    }
    
    @Test
    fun testFormatList_balanced() {
        val preferences = PersonalityPreferences.getDefault().copy(
            style = CommunicationStyle.BALANCED
        )
        
        val items = listOf("Item 1", "Item 2", "Item 3")
        val formatted = StyleApplier.formatList(items, preferences)
        
        assertTrue(formatted.contains("•"))
    }
    
    @Test
    fun testFormatList_detailed() {
        val preferences = PersonalityPreferences.getDefault().copy(
            style = CommunicationStyle.DETAILED
        )
        
        val items = listOf("Item 1", "Item 2")
        val formatted = StyleApplier.formatList(items, preferences)
        
        assertTrue(formatted.contains("1."))
        assertTrue(formatted.contains("2."))
    }
    
    @Test
    fun testFormatNumber() {
        val preferences = PersonalityPreferences.getDefault().copy(
            elaboration = ElaborationLevel.MINIMAL
        )
        
        val formatted = StyleApplier.formatNumber(3.14159, preferences)
        assertEquals("3.1", formatted)
        
        val detailedPreferences = preferences.copy(
            elaboration = ElaborationLevel.DETAILED
        )
        val detailedFormatted = StyleApplier.formatNumber(3.14159, detailedPreferences)
        assertEquals("3.1416", detailedFormatted)
    }
    
    // ==================== Personality Invariants Tests ====================
    
    @Test
    fun testPersonalityInvariants_sameFactsDifferentPresentation() {
        // This test verifies that personality affects presentation, not truth
        // We can't fully test this without the response generation system,
        // but we can verify that the personality system doesn't modify facts
        
        val fact = "The capital of Egypt is Cairo"
        val personality1 = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.FRIENDLY_WARM
        )
        val personality2 = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.SERIOUS_DIRECT
        )
        
        // The fact itself should not be modified by personality
        val styled1 = StyleApplier.applyToText(fact, personality1)
        val styled2 = StyleApplier.applyToText(fact, personality2)
        
        // The core fact should still be present
        assertTrue(styled1.contains("Cairo"))
        assertTrue(styled2.contains("Cairo"))
    }
    
    @Test
    fun testPersonalityInvariants_uncertaintyPreserved() {
        // Uncertainty indicators should not be removed by personality
        val uncertainText = "I'm not sure, but it might be..."
        
        val playful = PersonalityPreferences.getDefault().copy(
            tone = CommunicationTone.PLAYFUL,
            humorFrequency = HumorFrequency.FREQUENT
        )
        
        val styled = StyleApplier.applyToText(uncertainText, playful)
        
        // The uncertainty should still be present
        assertTrue(styled.contains("not sure") || styled.contains("might"))
    }
}
