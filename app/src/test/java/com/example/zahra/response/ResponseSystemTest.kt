package com.example.zahra.response

import org.junit.Test
import org.junit.Assert.*
import com.example.zahra.personality.PersonalityPreferences
import com.example.zahra.context.ConversationState
import com.example.zahra.context.ContextManager
import com.example.zahra.knowledge.engine.KnowledgeEngine
import android.content.Context
import org.mockito.Mockito.mock

/**
 * Tests for the Response System
 * 
 * Tests cover:
 * - ResponseBuilder functionality
 * - TemplateSelector logic
 * - TemplateExpander functionality
 * - ResponseRanker scoring
 * - ResponseValidator checks
 */
class ResponseSystemTest {
    
    @Test
    fun testTemplateSelector_returnsTemplatesForIntent() {
        val selector = TemplateSelector()
        val personality = PersonalityPreferences.getDefault()
        val context = ConversationState()
        
        val templates = selector.selectTemplates(
            intent = "greet",
            entities = emptyMap(),
            context = context,
            personality = personality
        )
        
        assertNotNull("Templates should not be null", templates)
        assertTrue("Should return at least one template for greet", templates.isNotEmpty())
        
        val greetTemplates = templates.filter { it.intent == "greet" }
        assertTrue("Should have greet templates", greetTemplates.isNotEmpty())
    }
    
    @Test
    fun testTemplateSelector_returnsDifferentTemplatesForDifferentIntents() {
        val selector = TemplateSelector()
        val personality = PersonalityPreferences.getDefault()
        val context = ConversationState()
        
        val greetTemplates = selector.selectTemplates("greet", emptyMap(), context, personality)
        val farewellTemplates = selector.selectTemplates("farewell", emptyMap(), context, personality)
        
        assertNotNull("Greet templates should not be null", greetTemplates)
        assertNotNull("Farewell templates should not be null", farewellTemplates)
        
        // They should have different template IDs
        val greetIds = greetTemplates.map { it.id }.toSet()
        val farewellIds = farewellTemplates.map { it.id }.toSet()
        
        // Some overlap is okay (generic templates), but should have unique ones
        val uniqueGreet = greetIds.filter { it.contains("greet") }
        val uniqueFarewell = farewellIds.filter { it.contains("farewell") }
        
        assertTrue("Should have greet-specific templates", uniqueGreet.isNotEmpty())
        assertTrue("Should have farewell-specific templates", uniqueFarewell.isNotEmpty())
    }
    
    @Test
    fun testTemplateExpander_replacesSlots() {
        val expander = TemplateExpander()
        val personality = PersonalityPreferences.getDefault()
        val context = ConversationState()
        
        val template = ResponseTemplate(
            id = "test_template",
            template = "Hello {name}! Welcome to {app_name}.",
            intent = "greet",
            priority = 5
        )
        
        val slots = mapOf(
            "name" to "John",
            "app_name" to "Zahra"
        )
        
        val expanded = expander.expandTemplate(template, slots, context, personality)
        
        assertEquals("Slots should be replaced", "Hello John! Welcome to Zahra.", expanded)
    }
    
    @Test
    fun testTemplateExpander_replacesContextSlots() {
        val expander = TemplateExpander()
        val personality = PersonalityPreferences.getDefault()
        
        val context = ConversationState()
        context.currentTopic = "Kotlin programming"
        
        val template = ResponseTemplate(
            id = "test_context",
            template = "Let's discuss {topic}.",
            intent = "greet",
            priority = 5
        )
        
        val expanded = expander.expandTemplate(template, emptyMap(), context, personality)
        
        assertEquals("Topic should be replaced", "Let's discuss Kotlin programming.", expanded)
    }
    
    @Test
    fun testTemplateExpander_replacesAppName() {
        val expander = TemplateExpander()
        val personality = PersonalityPreferences.getDefault()
        val context = ConversationState()
        
        val template = ResponseTemplate(
            id = "test_app",
            template = "Welcome to {app_name}!",
            intent = "greet",
            priority = 5
        )
        
        val expanded = expander.expandTemplate(template, emptyMap(), context, personality)
        
        assertEquals("App name should be replaced", "Welcome to Zahra!", expanded)
    }
    
    @Test
    fun testResponseRanker_ranksTemplates() {
        val ranker = ResponseRanker()
        val context = ConversationState()
        
        val template1 = ResponseTemplate(
            id = "low_priority",
            template = "Response one",
            intent = "greet",
            priority = 1
        )
        
        val template2 = ResponseTemplate(
            id = "high_priority",
            template = "Response two",
            intent = "greet",
            priority = 10
        )
        
        val templates = listOf(template1, template2)
        val ranked = ranker.rankTemplates(templates, "greet", context)
        
        assertNotNull("Ranked list should not be null", ranked)
        assertEquals("Should have same size", 2, ranked.size)
        assertEquals("Highest priority should be first", "high_priority", ranked[0].id)
        assertEquals("Lowest priority should be last", "low_priority", ranked[1].id)
    }
    
    @Test
    fun testResponseRanker_ranksByRelevance() {
        val ranker = ResponseRanker()
        val context = ConversationState()
        
        val template1 = ResponseTemplate(
            id = "relevant",
            template = "Hello there how are you",
            intent = "greet",
            priority = 5
        )
        
        val template2 = ResponseTemplate(
            id = "less_relevant",
            template = "Goodbye see you later",
            intent = "greet",
            priority = 5
        )
        
        val templates = listOf(template1, template2)
        val ranked = ranker.rankTemplates(templates, "hello how are you", context)
        
        assertNotNull("Ranked list should not be null", ranked)
        assertEquals("Should have same size", 2, ranked.size)
        // The first template should be ranked higher due to word overlap
        assertEquals("More relevant template should be first", "relevant", ranked[0].id)
    }
    
    @Test
    fun testResponseValidator_validatesValidResponse() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val response = com.example.zahra.model.ZahraResponse(
            text = "This is a valid response with sufficient length.",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(response, personality)
        
        assertTrue("Valid response should pass validation", isValid)
    }
    
    @Test
    fun testResponseValidator_rejectsTooShortResponse() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val response = com.example.zahra.model.ZahraResponse(
            text = "Hi",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(response, personality)
        
        assertFalse("Too short response should fail validation", isValid)
    }
    
    @Test
    fun testResponseValidator_rejectsEmptyResponse() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val response = com.example.zahra.model.ZahraResponse(
            text = "",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(response, personality)
        
        assertFalse("Empty response should fail validation", isValid)
    }
    
    @Test
    fun testResponseValidator_rejectsPlaceholderText() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val response = com.example.zahra.model.ZahraResponse(
            text = "Hello {name}! Welcome to {app_name}.",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(response, personality)
        
        assertFalse("Response with placeholders should fail validation", isValid)
    }
    
    @Test
    fun testResponseValidator_rejectsForbiddenPatterns() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val response = com.example.zahra.model.ZahraResponse(
            text = "I am a person and I know everything.",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(response, personality)
        
        assertFalse("Response with forbidden patterns should fail validation", isValid)
    }
    
    @Test
    fun testResponseValidator_checksEmojiFrequency() {
        val validator = ResponseValidator()
        
        // Create personality with no emojis
        val noEmojiPersonality = PersonalityPreferences.getDefault().copy(
            emojiFrequency = com.example.zahra.personality.EmojiFrequency.NONE
        )
        
        val responseWithEmoji = com.example.zahra.model.ZahraResponse(
            text = "Hello! ",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(responseWithEmoji, noEmojiPersonality)
        
        assertFalse("Response with emoji should fail for NONE emoji personality", isValid)
    }
    
    @Test
    fun testResponseValidator_checksFormality() {
        val validator = ResponseValidator()
        
        // Create very formal personality
        val formalPersonality = PersonalityPreferences.getDefault().copy(
            formality = com.example.zahra.personality.FormalityLevel.VERY_FORMAL
        )
        
        val informalResponse = com.example.zahra.model.ZahraResponse(
            text = "Hey there! What's up? Can't wait to help ya!",
            intent = "greet",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(informalResponse, formalPersonality)
        
        assertFalse("Informal response should fail for VERY_FORMAL personality", isValid)
    }
    
    @Test
    fun testResponseValidator_checksReligiousClaimsHaveSources() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val responseWithoutSource = com.example.zahra.model.ZahraResponse(
            text = "The Quran says you must do this.",
            intent = "ask_religion",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = null  // No source!
        )
        
        val isValid = validator.validate(responseWithoutSource, personality)
        
        assertFalse("Religious claim without source should fail validation", isValid)
    }
    
    @Test
    fun testResponseValidator_acceptsReligiousClaimsWithSources() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val responseWithSource = com.example.zahra.model.ZahraResponse(
            text = "The Quran says you must do this.",
            intent = "ask_religion",
            confidence = 0.95,
            confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT,
            outcomeState = com.example.zahra.model.OutcomeState.SUPPORTED,
            source = "Quran 2:183"
        )
        
        val isValid = validator.validate(responseWithSource, personality)
        
        assertTrue("Religious claim with source should pass validation", isValid)
    }
    
    @Test
    fun testResponseValidator_checksUncertaintyCalibration() {
        val validator = ResponseValidator()
        val personality = PersonalityPreferences.getDefault()
        
        val uncertainResponse = com.example.zahra.model.ZahraResponse(
            text = "I am 100% certain about this.",
            intent = "unknown",
            confidence = 0.1,
            confidenceTier = com.example.zahra.model.ConfidenceTier.UNCERTAIN,
            outcomeState = com.example.zahra.model.OutcomeState.UNSUPPORTED,
            source = "Test source"
        )
        
        val isValid = validator.validate(uncertainResponse, personality)
        
        assertFalse("Uncertain response with absolute certainty should fail", isValid)
    }
}
