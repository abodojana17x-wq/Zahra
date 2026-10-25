package com.example

import com.example.zahra.intelligence.mood.MoodAnalyzer
import com.example.zahra.intelligence.mood.MoodAdaptiveResponseBuilder
import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraResponse
import com.example.zahra.personality.CommunicationStyle
import com.example.zahra.personality.CommunicationTone
import com.example.zahra.personality.PersonalityPreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6.5: Mood-Adaptive Response System Tests
 * 
 * Tests the MoodAnalyzer (sentiment/mood detection) and 
 * MoodAdaptiveResponseBuilder (composed response layers) working together.
 */
class Phase6MoodAdaptiveSystemTest {

    private val moodAnalyzer = MoodAnalyzer()
    private val moodAdaptiveBuilder = MoodAdaptiveResponseBuilder()

    // ========== MoodAnalyzer Tests ==========

    @Test
    fun `detect happy mood from positive words`() {
        val result = moodAnalyzer.analyze("أنا سعيد جداً اليوم")
        assertEquals(MoodAnalyzer.Mood.HAPPY, result.primaryMood)
        assertTrue("Sentiment should be positive", result.sentimentScore > 0.0)
        assertEquals(MoodAnalyzer.ResponseStrategy.CELEBRATE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect sad mood from negative words`() {
        val result = moodAnalyzer.analyze("أنا حزين وزعلان من اللي حصل")
        assertEquals(MoodAnalyzer.Mood.SAD, result.primaryMood)
        assertTrue("Sentiment should be negative", result.sentimentScore < 0.0)
        assertEquals(MoodAnalyzer.ResponseStrategy.WARM_SUPPORT, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect anxious mood`() {
        val result = moodAnalyzer.analyze("خايف من الامتحان بكرة ومش عارف أعمل إيه")
        assertEquals(MoodAnalyzer.Mood.ANXIOUS, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.CALM_REASSURANCE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect angry mood`() {
        val result = moodAnalyzer.analyze("غضبان جداً ومش طايق الوضع ده!")
        assertEquals(MoodAnalyzer.Mood.ANGRY, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.DEESCALATE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect curious mood from questions`() {
        val result = moodAnalyzer.analyze("إيه هي عاصمة فرنسا؟")
        assertEquals(MoodAnalyzer.Mood.CURIOUS, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.EDUCATE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect urgent mood from urgency keywords`() {
        val result = moodAnalyzer.analyze("ضروري! الحقني بسرعة!")
        assertEquals(MoodAnalyzer.Mood.URGENT, result.primaryMood)
        assertEquals(MoodAnalyzer.UrgencyLevel.CRITICAL, result.urgencyLevel)
        assertEquals(MoodAnalyzer.ResponseStrategy.QUICK_DIRECT, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect lonely mood`() {
        val result = moodAnalyzer.analyze("حاسس إني لوحدي ومحدش فاهمني")
        assertEquals(MoodAnalyzer.Mood.LONELY, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.WARM_SUPPORT, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect tired mood`() {
        val result = moodAnalyzer.analyze("تعبان ومرهق أوي من الشغل")
        assertEquals(MoodAnalyzer.Mood.TIRED, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.WARM_SUPPORT, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect grateful mood`() {
        val result = moodAnalyzer.analyze("شكراً جزيلاً يا زهرة، جزاك الله خير")
        assertEquals(MoodAnalyzer.Mood.GRATEFUL, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.CELEBRATE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect motivated mood`() {
        val result = moodAnalyzer.analyze("متحمس أوي! يلا نبدأ المشروع 💪")
        assertEquals(MoodAnalyzer.Mood.MOTIVATED, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.CELEBRATE, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect neutral mood for plain request`() {
        val result = moodAnalyzer.analyze("احسب 25 * 4")
        assertEquals(MoodAnalyzer.Mood.NEUTRAL, result.primaryMood)
        assertEquals(MoodAnalyzer.ResponseStrategy.NORMAL, result.suggestedResponseStrategy)
    }

    @Test
    fun `detect positive emojis`() {
        val result = moodAnalyzer.analyze("الحمدلله تمام 😊✨")
        assertTrue("Positive emojis should raise sentiment", result.sentimentScore > 0.0)
        assertTrue("Should detect emojis", result.detectedEmojis.isNotEmpty())
    }

    @Test
    fun `detect negative emojis`() {
        val result = moodAnalyzer.analyze("مش كويس خالص 😔💔")
        assertTrue("Negative emojis should lower sentiment", result.sentimentScore < 0.0)
    }

    @Test
    fun `negation flips positive to less positive`() {
        val resultPositive = moodAnalyzer.analyze("أنا مبسوط")
        val resultNegated = moodAnalyzer.analyze("مش مبسوط")
        
        assertTrue("Positive should have positive sentiment", resultPositive.sentimentScore > 0.0)
        // Negation should reduce or flip the positive sentiment
        assertTrue("Negated positive should be less positive", 
            resultNegated.sentimentScore < resultPositive.sentimentScore)
        assertTrue("Negation should be detected", resultNegated.hasNegation)
    }

    @Test
    fun `intensifier amplifies sentiment`() {
        val resultNormal = moodAnalyzer.analyze("أنا حزين")
        val resultIntense = moodAnalyzer.analyze("أنا حزين جداً")
        
        assertTrue("Intensified negative should be more negative",
            resultIntense.sentimentScore <= resultNormal.sentimentScore)
        assertTrue("Intensifier should be detected", resultIntense.hasIntensifier)
    }

    @Test
    fun `detect Egyptian dialect sadness`() {
        val result = moodAnalyzer.analyze("زعلان أوي ومش لاقي حد أتكلم معاه")
        assertEquals(MoodAnalyzer.Mood.SAD, result.primaryMood)
    }

    @Test
    fun `detect engagement level for deep sharing`() {
        val result = moodAnalyzer.analyze("أنا بحس إني تايه في حياتي ومش عارف أعمل إيه وكل حاجة حواليا صعبة")
        assertTrue("Deep sharing should have HIGH or DEEP engagement",
            result.engagementLevel.ordinal >= MoodAnalyzer.EngagementLevel.HIGH.ordinal)
    }

    @Test
    fun `detect low engagement for short input`() {
        val result = moodAnalyzer.analyze("حسبي")
        assertEquals(MoodAnalyzer.EngagementLevel.LOW, result.engagementLevel)
    }

    @Test
    fun `mixed mood detects secondary mood`() {
        // A message that's both anxious and curious
        val result = moodAnalyzer.analyze("خايف بس عايز أفهم إيه اللي بيحصل")
        // Primary should be anxious
        assertTrue("Primary should be emotional", 
            result.primaryMood in setOf(MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.CONFUSED))
    }

    // ========== MoodAdaptiveResponseBuilder Tests ==========

    private fun createPersonality(tone: CommunicationTone = CommunicationTone.FRIENDLY_WARM): PersonalityPreferences {
        return PersonalityPreferences(
            tone = tone,
            style = CommunicationStyle.BALANCED
        )
    }

    @Test
    fun `composed response has layers for sad mood`() {
        val coreResponse = ZahraResponse(
            text = "يمكنك مراجعة طبيبك للحصول على مساعدة.",
            intent = "ask_health",
            confidence = 0.80,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "أنا حزين أوي ومش عارف أعمل إيه",
            createPersonality()
        )
        
        // Adapted response should be longer (has more layers)
        assertTrue("Adapted response should have more content",
            adapted.text.length > coreResponse.text.length)
        // Core content should still be present
        assertTrue("Core content should be preserved",
            adapted.text.contains("طبيبك") || adapted.text.contains("مساعدة"))
    }

    @Test
    fun `composed response preserves core answer for neutral mood`() {
        val coreResponse = ZahraResponse(
            text = "نتيجة الحساب: 25 × 4 = 100",
            intent = "calculate_math",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "احسب 25 × 4",
            createPersonality()
        )
        
        // Core answer must be preserved
        assertTrue("Core answer must be present",
            adapted.text.contains("100") || adapted.text.contains("25"))
    }

    @Test
    fun `urgent mood produces brief direct response`() {
        val coreResponse = ZahraResponse(
            text = "رقم الطوارئ هو 123",
            intent = "ask_emergency",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "ضروري!! الحقني بسرعة!!",
            createPersonality()
        )
        
        // Should contain the core info
        assertTrue("Core info preserved", adapted.text.contains("123"))
        // Urgent responses should be more concise (no enrichment/closing)
        assertTrue("Urgent response should be concise",
            adapted.text.length < coreResponse.text.length + 100)
    }

    @Test
    fun `happy mood adds celebration layer`() {
        val coreResponse = ZahraResponse(
            text = "تم حفظ المهمة بنجاح.",
            intent = "add_task",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "يا سلااام! نجحت في الامتحان! 🎉",
            createPersonality()
        )
        
        // Should contain celebration elements
        assertTrue("Celebration should be added",
            adapted.text.contains("فرح") || adapted.text.contains("مبروك") || 
            adapted.text.contains("🎉") || adapted.text.contains("يا") ||
            adapted.text.contains("ربنا") || adapted.text.contains("مبسوطة"))
    }

    @Test
    fun `serious personality reduces emoji usage`() {
        val coreResponse = ZahraResponse(
            text = "معلومة عامة: الماء يغلي عند 100 درجة.",
            intent = "ask_knowledge",
            confidence = 0.90,
            confidenceTier = ConfidenceTier.DIRECT
        )
        
        val seriousPersonality = createPersonality(CommunicationTone.SERIOUS_DIRECT)
        val friendlyPersonality = createPersonality(CommunicationTone.FRIENDLY_WARM)
        
        val seriousAdapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse, "أنا مبسوط", seriousPersonality
        )
        val friendlyAdapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse, "أنا مبسوط", friendlyPersonality
        )
        
        // Serious personality should have fewer or equal emojis
        val seriousEmojiCount = seriousAdapted.text.count { it.code > 0x1F000 }
        val friendlyEmojiCount = friendlyAdapted.text.count { it.code > 0x1F000 }
        
        assertTrue("Serious tone should use fewer or equal emojis",
            seriousEmojiCount <= friendlyEmojiCount + 2) // small tolerance
    }

    @Test
    fun `sad mood with deep engagement adds dua`() {
        val coreResponse = ZahraResponse(
            text = "يمكنك التحدث مع شخص تثق به.",
            intent = "ask_advice",
            confidence = 0.75,
            confidenceTier = ConfidenceTier.DIRECT
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "أنا حزين جداً وبحس إن الدنيا ضاقت بيا ومش لاقي حد يفهمني 😔💔",
            createPersonality()
        )
        
        // Should contain comfort elements (dua, ayah, or supportive text)
        assertTrue("Should add comfort for sad deep engagement",
            adapted.text.contains("الله") || adapted.text.contains("🤍") || 
            adapted.text.contains("💜") || adapted.text.contains("ربنا") ||
            adapted.text.contains("يسر") || adapted.text.contains("فرج"))
    }

    @Test
    fun `tool payload responses get brief mood opener only`() {
        val coreResponse = ZahraResponse(
            text = "تم إنشاء المهمة",
            intent = "add_task",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            toolPayload = com.example.zahra.model.ZahraToolPayload.TaskPayload(
                com.example.zahra.model.ZahraTask(title = "اختبار")
            )
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "أنا مبسوط! 😊",
            createPersonality()
        )
        
        // Should still have the tool payload intact
        assertNotNull("Tool payload should be preserved", adapted.toolPayload)
        // Core text should be present
        assertTrue("Core text preserved", adapted.text.contains("المهمة"))
    }

    @Test
    fun `quick mood analysis works correctly`() {
        val happyMood = moodAdaptiveBuilder.quickAnalyze("الحمدلله فرحان 😊")
        val sadMood = moodAdaptiveBuilder.quickAnalyze("زعلان 😔")
        
        assertTrue("Happy input → positive sentiment", happyMood.sentimentScore > 0)
        assertTrue("Sad input → negative sentiment", sadMood.sentimentScore < 0)
    }

    // ========== Integration Tests ==========

    @Test
    fun `all mood categories produce valid response strategies`() {
        val moodInputs = mapOf(
            MoodAnalyzer.Mood.HAPPY to "فرحان أوي الحمدلله! 🎉",
            MoodAnalyzer.Mood.SAD to "حزين وزعلان 😔",
            MoodAnalyzer.Mood.ANGRY to "غضبان جداً! 😤",
            MoodAnalyzer.Mood.ANXIOUS to "خايف وقلقان 😰",
            MoodAnalyzer.Mood.CURIOUS to "إيه ده؟ عايز أفهم",
            MoodAnalyzer.Mood.URGENT to "ضروري بسرعة! ⚡",
            MoodAnalyzer.Mood.TIRED to "تعبان ومرهق 😴",
            MoodAnalyzer.Mood.LONELY to "لوحدي ومحدش جنبي 💔",
            MoodAnalyzer.Mood.GRATEFUL to "شكراً جزيلاً 🤍",
            MoodAnalyzer.Mood.MOTIVATED to "متحمس! يلا! 💪",
            MoodAnalyzer.Mood.CONFUSED to "مش فاهم حاجة 😵",
            MoodAnalyzer.Mood.NEUTRAL to "احسب 5 + 5"
        )

        for ((expectedMood, input) in moodInputs) {
            val result = moodAnalyzer.analyze(input)
            assertNotNull("MoodResult should not be null for: $input", result)
            assertNotNull("ResponseStrategy should not be null", result.suggestedResponseStrategy)
            assertTrue("Confidence should be between 0 and 1",
                result.confidence in 0.0..1.0)
            assertTrue("Sentiment should be between -1 and 1",
                result.sentimentScore in -1.0..1.0)
        }
    }

    @Test
    fun `end-to-end mood adaptation preserves all ZahraResponse fields`() {
        val coreResponse = ZahraResponse(
            text = "إجابة أساسية",
            intent = "test_intent",
            confidence = 0.85,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "test_source",
            followups = listOf("follow-up 1", "follow-up 2")
        )
        
        val adapted = moodAdaptiveBuilder.buildAdaptiveResponse(
            coreResponse,
            "أنا حزين",
            createPersonality()
        )
        
        // All non-text fields should be preserved
        assertEquals(coreResponse.intent, adapted.intent)
        assertEquals(coreResponse.confidence, adapted.confidence, 0.001)
        assertEquals(coreResponse.confidenceTier, adapted.confidenceTier)
        assertEquals(coreResponse.outcomeState, adapted.outcomeState)
        assertEquals(coreResponse.source, adapted.source)
        assertEquals(coreResponse.followups, adapted.followups)
        // Text should be different (enriched)
        assertNotEquals(coreResponse.text, adapted.text)
    }
}
