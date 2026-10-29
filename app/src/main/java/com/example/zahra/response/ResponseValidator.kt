package com.example.zahra.response

import com.example.zahra.model.ZahraResponse
import com.example.zahra.personality.PersonalityPreferences

/**
 * Response Validator
 * 
 * Validates responses for:
 * - Minimum quality standards
 * - Personality consistency
 * - Safety and appropriateness
 * - Completeness
 */
class ResponseValidator {
    
    companion object {
        // Minimum response length
        private const val MIN_RESPONSE_LENGTH = 5

        // أنماط ادعاء ذاكرة سابقة بدون turn حقيقي —階段 4 سيكمل المنع، وهذه الطبقة الأولى
        private val FABRICATED_MEMORY_PATTERNS = listOf(
            "ما زال في ذاكرتي",
            "كما ذكرت سابقًا",
            "كما أخبرتني",
            "رقمي",
            "هاتفي",
            "عناوائي"
        )
        
        // Maximum response length
        private const val MAX_RESPONSE_LENGTH = 2000
        
        // Maximum number of sentences
        private const val MAX_SENTENCES = 10
        
        // Forbidden patterns
        private val FORBIDDEN_PATTERNS = listOf(
            // Never claim to be human
            "i am a person",
            "i am human",
            "i'm a person",
            "i'm human",
            // Never claim perfect knowledge
            "i know everything",
            "i know all",
            // Never make up religious rulings
            "i decree",
            "i rule that",
            // Inappropriate content
            "fuck",
            "shit",
            "bitch",
            "asshole"
        )
    }
    
    /**
     * Validate a response
     * 
     * @param response The response to validate
     * @param personality Current personality preferences
     * @return true if the response is valid, false otherwise
     */
    fun validate(
        response: ZahraResponse,
        personality: PersonalityPreferences
    ): Boolean {
        // Check basic quality
        if (!passesQualityCheck(response)) {
            return false
        }
        
        // Check personality consistency
        if (!isConsistentWithPersonality(response, personality)) {
            return false
        }
        
        // Check safety
        if (!passesSafetyCheck(response)) {
            return false
        }
        
        // Check completeness
        if (!isComplete(response)) {
            return false
        }
        
        return true
    }
    
    /**
     * Check if response meets basic quality standards
     */
    private fun passesQualityCheck(response: ZahraResponse): Boolean {
        val text = response.text
        
        // Check length
        if (text.length < MIN_RESPONSE_LENGTH) {
            return false
        }
        
        if (text.length > MAX_RESPONSE_LENGTH) {
            return false
        }
        
        // Check for empty or placeholder text
        if (text.isBlank() || text == "null" || text == "N/A") {
            return false
        }
        
        // Check for too many repetitions
        val words = text.split("\\s+".toRegex())
        val wordCount = words.size
        if (wordCount > MAX_SENTENCES * 20) {
            return false
        }
        
        // Check for repeated phrases
        val repeatedPhrases = detectRepeatedPhrases(text)
        if (repeatedPhrases.size > 3) {
            return false
        }
        
        return true
    }
    
    /**
     * Check if response is consistent with personality
     */
    private fun isConsistentWithPersonality(
        response: ZahraResponse,
        personality: PersonalityPreferences
    ): Boolean {
        val text = response.text.lowercase()
        
        // Check emoji usage
        val emojiCount = text.indices.count { i ->
            Character.isHighSurrogate(text[i]) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1]) &&
                Character.toCodePoint(text[i], text[i + 1]) in 0x1F300..0x1FAFF
        }
        val maxEmojis = when (personality.emojiFrequency) {
            com.example.zahra.personality.EmojiFrequency.NONE -> 0
            com.example.zahra.personality.EmojiFrequency.RARE -> 1
            com.example.zahra.personality.EmojiFrequency.MODERATE -> 3
            com.example.zahra.personality.EmojiFrequency.FREQUENT -> 5
        }
        
        if (emojiCount > maxEmojis) {
            return false
        }
        
        // Check formality
        if (personality.formality == com.example.zahra.personality.FormalityLevel.VERY_FORMAL) {
            // Very formal should not use contractions or slang
            val informalPatterns = listOf(
                "don't", "can't", "won't", "it's", "that's", "what's",
                "gonna", "wanna", "gotta", "kinda", "sorta"
            )
            if (informalPatterns.any { text.contains(it) }) {
                return false
            }
        }
        
        // Check tone
        when (personality.tone) {
            com.example.zahra.personality.CommunicationTone.SERIOUS_DIRECT -> {
                // Serious tone should not have excessive exclamation marks
                val exclamationCount = text.count { it == '!' }
                if (exclamationCount > 2) {
                    return false
                }
            }
            com.example.zahra.personality.CommunicationTone.PLAYFUL -> {
                // Playful can have more exclamations
                // No specific restrictions
            }
            else -> {
                // Default - moderate exclamations
                val exclamationCount = text.count { it == '!' }
                if (exclamationCount > 3) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Check if response passes safety checks
     */
    private fun passesSafetyCheck(response: ZahraResponse): Boolean {
        val textLower = response.text.lowercase()
        
        // Check for forbidden patterns
        for (pattern in FORBIDDEN_PATTERNS) {
            if (textLower.contains(pattern)) {
                return false
            }
        }
        
        // Check for religious claims without proper sourcing
        val religiousKeywords = listOf(
            "quran says", "hadith says", "allah says", "prophet said",
            "islam teaches", "the quran states", "according to hadith"
        )
        
        for (keyword in religiousKeywords) {
            if (textLower.contains(keyword) && response.source.isNullOrBlank()) {
                // Religious claims must have sources
                return false
            }
        }
        
        // Check for excessive certainty in uncertain responses
        if (response.confidenceTier == com.example.zahra.model.ConfidenceTier.UNCERTAIN) {
            val certaintyWords = listOf(
                "definitely", "certainly", "absolutely", "without a doubt",
                "100%", "for sure", "no doubt"
            )
            if (certaintyWords.any { textLower.contains(it) }) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * حاجز ضد ادعاءات الذاكرة السابقة بدون turn حقيقي.
     * يُعاد true إذا كان الرد آمنًا (لا ادعاءات ذاكرة مزيفة، أو أن هناك turn حقيقي يدعمها).
     * المرحلة 4 ستكمل الربط بسجل turn الفعلي.
     */
    fun hasNoFabricatedMemoryClaims(response: com.example.zahra.model.ZahraResponse, hasRealTurn: Boolean): Boolean {
        if (hasRealTurn) return true
        val text = response.text.lowercase()
        return !FABRICATED_MEMORY_PATTERNS.any { text.contains(it) }
    }

    /**
     * Check if response is complete
     */
    private fun isComplete(response: ZahraResponse): Boolean {
        val text = response.text
        
        // Check for incomplete sentences
        val incompletePatterns = listOf(
            "...",
            "..",
            "-",
            "_"
        )
        
        if (incompletePatterns.any { text.endsWith(it) }) {
            return false
        }
        
        // Check for placeholder text
        val placeholderPatterns = listOf(
            "{name}", "{topic}", "{user}", "{previous}",
            "[INSERT", "[TODO", "FIXME"
        )
        
        if (placeholderPatterns.any { text.contains(it, ignoreCase = true) }) {
            return false
        }
        
        return true
    }
    
    /**
     * Detect repeated phrases in text
     */
    private fun detectRepeatedPhrases(text: String): List<String> {
        val words = text.split("\\s+".toRegex())
        val phrases = mutableListOf<String>()
        val seenPhrases = mutableSetOf<String>()
        
        // Check for repeated 3-word phrases
        for (i in 0..words.size - 3) {
            val phrase = "${words[i]} ${words[i+1]} ${words[i+2]}".lowercase()
            if (seenPhrases.contains(phrase)) {
                phrases.add(phrase)
            }
            seenPhrases.add(phrase)
        }
        
        return phrases
    }
}
