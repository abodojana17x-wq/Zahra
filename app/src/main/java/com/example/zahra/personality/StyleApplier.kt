package com.example.zahra.personality

import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.EvidenceType
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraResponse

/**
 * Style Applier
 * 
 * Applies personality preferences to response text, modifying presentation
 * while preserving factual content and maintaining all invariants.
 * 
 * Key Principle: Personality affects PRESENTATION ONLY, not TRUTH.
 * - Same facts must be presented across all personalities
 * - Only wording, structure, and style may vary
 * - Evidence and citations must remain intact
 */
object StyleApplier {
        // Emoji sets for different contexts
        private val FRIENDLY_EMOJIS = listOf("😊", "😀", "✨", "🌟", "💡", "🎉", "🙏")
        private val PLAYFUL_EMOJIS = listOf("😄", "🤔", "🎭", "🎮", "🎨", "🤣", "😜")
        private val THOUGHTFUL_EMOJIS = listOf("🤔", "💭", "📚", "🔍", "✨")
        private val PROFESSIONAL_EMOJIS = listOf("📊", "📈", "✅", "✔️")
        private val SERIOUS_EMOJIS = listOf("✨") // Minimal
        
        // Arabic punctuation and decorative elements
        private val ARABIC_DECORATIONS = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf("😊", "✨", "🌸"),
            CommunicationTone.CALM_THOUGHTFUL to listOf("💭", "📖"),
            CommunicationTone.SERIOUS_DIRECT to listOf(),
            CommunicationTone.PROFESSIONAL to listOf("✅"),
            CommunicationTone.ENCOURAGING_MENTOR to listOf("🌟", "✨"),
            CommunicationTone.PLAYFUL to listOf("😄", "🎉", "🎨")
        )
        
        // Greetings for different tones
        private val GREETINGS_ARABIC = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "أهلاً وسهلاً!",
                "مرحباً بك!",
                "أهلاً بك!",
                "مرحبا!",
                "هلُمَّ!",
                "أهلاً بكم!",
                "مرحبا بكم!",
                "هلُمَّ بكم!",
                "أهلاً وسهلاً بكم!",
                "مرحبا وسهلاً!",
                "أهلاً!",
                "مرحبا!",
                "هلُمَّ!",
                "أهلاً وسهلاً!",
                "مرحبا وسهلاً بكم!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "مرحباً بك",
                "أهلاً بك",
                "نرحب بك",
                "مرحبا",
                "أهلاً"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "مرحبا",
                "أهلاً",
                "مرحبا بك"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "مرحبا",
                "أهلاً وسهلاً",
                "نرحب بك"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "أهلاً وسهلاً!",
                "مرحبا بك!",
                "نرحب بك!",
                "أهلاً!",
                "مرحبا!",
                "هلُمَّ!",
                "أهلاً وسهلاً بكم!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "أهلاً وسهلاً! 😊",
                "مرحبا بك! ✨",
                "أهلاً! 🌟",
                "مرحبا! 😄",
                "هلُمَّ! 🎉",
                "أهلاً وسهلاً بكم! 😊",
                "مرحبا وسهلاً! ✨"
            )
        )
        
        private val GREETINGS_ENGLISH = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "Hello!",
                "Hi there!",
                "Welcome!",
                "Greetings!",
                "Hey there!",
                "Hello and welcome!",
                "Hi!",
                "Welcome back!",
                "Good to see you!",
                "Hello again!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "Hello",
                "Welcome",
                "Greetings",
                "Hi"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "Hello",
                "Hi",
                "Greetings"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "Hello",
                "Good day",
                "Greetings"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "Hello!",
                "Welcome!",
                "Greetings!",
                "Hi there!",
                "Good to see you!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "Hello! 😊",
                "Hi there! ✨",
                "Welcome! 🌟",
                "Hey! 😄",
                "Hello again! 🎉"
            )
        )
        
        // Farewells for different tones
        private val FAREWELLS_ARABIC = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "مع السلامة!",
                "إلى اللقاء!",
                "وداعاً!",
                "مع السلامة والرحمة!",
                "إلى اللقاء قريباً!",
                "وداعاً وسعداء!",
                "مع السلامة!",
                "وداعاً!",
                "إلى اللقاء!",
                "مع السلامة والرحمة والبركات!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "مع السلامة",
                "إلى اللقاء",
                "وداعاً",
                "مع السلامة والرحمة"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "مع السلامة",
                "وداعاً",
                "إلى اللقاء"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "مع السلامة",
                "إلى اللقاء",
                "وداعاً"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "مع السلامة!",
                "إلى اللقاء!",
                "وداعاً!",
                "مع السلامة والرحمة!",
                "إلى اللقاء قريباً!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "مع السلامة! 😊",
                "إلى اللقاء! ✨",
                "وداعاً! 🌟",
                "مع السلامة! 😄"
            )
        )
        
        private val FAREWELLS_ENGLISH = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "Goodbye!",
                "See you later!",
                "Farewell!",
                "Take care!",
                "See you soon!",
                "Until next time!",
                "Bye!",
                "Have a great day!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "Goodbye",
                "Farewell",
                "See you later",
                "Take care"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "Goodbye",
                "Farewell",
                "See you"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "Goodbye",
                "Farewell",
                "Have a good day"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "Goodbye!",
                "See you later!",
                "Farewell!",
                "Take care!",
                "Until next time!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "Goodbye! 😊",
                "See you! ✨",
                "Farewell! 🌟",
                "Bye! 😄"
            )
        )
        
        // Confirmations for different tones
        private val CONFIRMATIONS_ARABIC = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "تم!",
                "تم الأمر!",
                "تم ذلك!",
                "تم بنجاح!",
                "تم إنجازه!",
                "تم!",
                "تم الأمر!",
                "تم ذلك بنجاح!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "تم",
                "تم الأمر",
                "تم ذلك",
                "تم بنجاح"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "تم",
                "تم الأمر",
                "تم ذلك"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "تم",
                "تم الأمر",
                "تم بنجاح"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "تم!",
                "تم الأمر!",
                "تم ذلك!",
                "تم بنجاح!",
                "تم إنجازه!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "تم! ✅",
                "تم الأمر! ✨",
                "تم ذلك! 🎉",
                "تم! 😊"
            )
        )
        
        private val CONFIRMATIONS_ENGLISH = mapOf(
            CommunicationTone.FRIENDLY_WARM to listOf(
                "Done!",
                "It's done!",
                "Done successfully!",
                "All set!",
                "Completed!",
                "Done and dusted!",
                "Done!"
            ),
            CommunicationTone.CALM_THOUGHTFUL to listOf(
                "Done",
                "It's done",
                "Completed",
                "Done successfully"
            ),
            CommunicationTone.SERIOUS_DIRECT to listOf(
                "Done",
                "It's done",
                "Completed"
            ),
            CommunicationTone.PROFESSIONAL to listOf(
                "Done",
                "It's done",
                "Completed"
            ),
            CommunicationTone.ENCOURAGING_MENTOR to listOf(
                "Done!",
                "It's done!",
                "Completed!",
                "All set!"
            ),
            CommunicationTone.PLAYFUL to listOf(
                "Done! ✅",
                "Done! ✨",
                "Done! 🎉",
                "Done! 😊"
            )
        )

    /**
     * Apply personality style to a response
     * 
     * This method modifies the PRESENTATION of the response based on personality
     * preferences, while preserving all factual content and evidence.
     */
    fun applyToResponse(
        response: ZahraResponse,
        personality: PersonalityPreferences = PersonalityPreferences.getDefault()
    ): ZahraResponse {
        // Don't modify responses that are already styled or have specific formatting
        if (response.intent == "compound_plan" || response.intent == "plan_execution") {
            return response
        }
        
        // Apply style to the text
        val styledText = applyToText(response.text, personality)
        
        return response.copy(
            text = styledText
        )
    }
    
    /**
     * Apply personality style to plain text
     */
    fun applyToText(
        text: String,
        personality: PersonalityPreferences = PersonalityPreferences.getDefault()
    ): String {
        var result = text
        
        // Apply emoji based on frequency
        result = applyEmoji(result, personality)
        
        // Apply tone-based modifications
        result = applyToneModifications(result, personality)
        
        // Apply style-based modifications
        result = applyStyleModifications(result, personality)
        
        // Apply language-specific modifications
        result = applyLanguageModifications(result, personality)
        
        return result
    }
    
    /**
     * Apply emoji based on frequency preference
     */
    private fun applyEmoji(
        text: String,
        personality: PersonalityPreferences
    ): String {
        // Don't add emoji to certain types of responses
        if (shouldSkipEmoji(text)) {
            return text
        }
        
        val emojiList = when (personality.tone) {
            CommunicationTone.FRIENDLY_WARM -> FRIENDLY_EMOJIS
            CommunicationTone.PLAYFUL -> PLAYFUL_EMOJIS
            CommunicationTone.CALM_THOUGHTFUL -> THOUGHTFUL_EMOJIS
            CommunicationTone.PROFESSIONAL -> PROFESSIONAL_EMOJIS
            CommunicationTone.SERIOUS_DIRECT -> SERIOUS_EMOJIS
            CommunicationTone.ENCOURAGING_MENTOR -> FRIENDLY_EMOJIS
        }
        
        return when (personality.emojiFrequency) {
            EmojiFrequency.NONE -> text
            EmojiFrequency.RARE -> {
                // Add emoji at the end with 30% chance
                if (text.isNotBlank() && Math.random() < 0.3 && emojiList.isNotEmpty()) {
                    val emoji = emojiList.random()
                    "$text $emoji"
                } else {
                    text
                }
            }
            EmojiFrequency.MODERATE -> {
                // Add emoji at the end with 60% chance
                if (text.isNotBlank() && Math.random() < 0.6 && emojiList.isNotEmpty()) {
                    val emoji = emojiList.random()
                    "$text $emoji"
                } else {
                    text
                }
            }
            EmojiFrequency.FREQUENT -> {
                // Add emoji at the end with 90% chance
                if (text.isNotBlank() && Math.random() < 0.9 && emojiList.isNotEmpty()) {
                    val emoji = emojiList.random()
                    "$text $emoji"
                } else {
                    text
                }
            }
        }
    }
    
    /**
     * Check if emoji should be skipped for this text
     */
    private fun shouldSkipEmoji(text: String): Boolean {
        // Skip emoji for certain patterns
        val skipPatterns = listOf(
            "[",  // Already has structured formatting
            "•",  // Bullet points
            "1.",  // Numbered lists
            "\u0627\u0644\u0645\u0635\u062f\u0631:",  // Source citations
            "Quran",
            "Hadith",
            "\u0633\u0648\u0631\u0629",
            "\u062d\u062f\u064a\u062b"
        )
        
        return skipPatterns.any { text.contains(it) }
    }
    
    /**
     * Apply tone-based modifications
     */
    private fun applyToneModifications(
        text: String,
        personality: PersonalityPreferences
    ): String {
        // For now, tone is applied at response generation time
        // This method is a placeholder for future enhancements
        return text
    }
    
    /**
     * Apply style-based modifications
     */
    private fun applyStyleModifications(
        text: String,
        personality: PersonalityPreferences
    ): String {
        // For now, style is applied at response generation time
        // This method is a placeholder for future enhancements
        return text
    }
    
    /**
     * Apply language-specific modifications
     */
    private fun applyLanguageModifications(
        text: String,
        personality: PersonalityPreferences
    ): String {
        // For now, language is handled at response generation time
        // This method is a placeholder for future enhancements
        return text
    }
    
    fun getRandomEmoji(personality: PersonalityPreferences): String {
        if (personality.emojiFrequency == EmojiFrequency.NONE) return ""
        return ARABIC_DECORATIONS[personality.tone]?.randomOrNull() ?: ""
    }

    /**
     * Get a greeting appropriate for the personality
     */
    fun getGreeting(personality: PersonalityPreferences): String {
        return when (personality.language) {
            CommunicationLanguage.MODERN_STANDARD_ARABIC,
            CommunicationLanguage.EGYPTIAN_ARABIC -> {
                GREETINGS_ARABIC[personality.tone]?.random() 
                    ?: GREETINGS_ARABIC[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "أهلاً وسهلاً!"
            }
            CommunicationLanguage.ENGLISH,
            CommunicationLanguage.MIXED_ARABIC_ENGLISH -> {
                GREETINGS_ENGLISH[personality.tone]?.random()
                    ?: GREETINGS_ENGLISH[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "Hello!"
            }
        }
    }
    
    /**
     * Get a farewell appropriate for the personality
     */
    fun getFarewell(personality: PersonalityPreferences): String {
        return when (personality.language) {
            CommunicationLanguage.MODERN_STANDARD_ARABIC,
            CommunicationLanguage.EGYPTIAN_ARABIC -> {
                FAREWELLS_ARABIC[personality.tone]?.random()
                    ?: FAREWELLS_ARABIC[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "مع السلامة!"
            }
            CommunicationLanguage.ENGLISH,
            CommunicationLanguage.MIXED_ARABIC_ENGLISH -> {
                FAREWELLS_ENGLISH[personality.tone]?.random()
                    ?: FAREWELLS_ENGLISH[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "Goodbye!"
            }
        }
    }
    
    /**
     * Get a confirmation appropriate for the personality
     */
    fun getConfirmation(personality: PersonalityPreferences): String {
        return when (personality.language) {
            CommunicationLanguage.MODERN_STANDARD_ARABIC,
            CommunicationLanguage.EGYPTIAN_ARABIC -> {
                CONFIRMATIONS_ARABIC[personality.tone]?.random()
                    ?: CONFIRMATIONS_ARABIC[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "تم!"
            }
            CommunicationLanguage.ENGLISH,
            CommunicationLanguage.MIXED_ARABIC_ENGLISH -> {
                CONFIRMATIONS_ENGLISH[personality.tone]?.random()
                    ?: CONFIRMATIONS_ENGLISH[CommunicationTone.FRIENDLY_WARM]?.random()
                    ?: "Done!"
            }
        }
    }
    
    /**
     * Get an appropriate response prefix based on personality
     */
    fun getResponsePrefix(
        personality: PersonalityPreferences,
        isQuestion: Boolean = false
    ): String {
        return when (personality.language) {
            CommunicationLanguage.MODERN_STANDARD_ARABIC,
            CommunicationLanguage.EGYPTIAN_ARABIC -> {
                when (personality.tone) {
                    CommunicationTone.FRIENDLY_WARM -> {
                        if (isQuestion) "بالتأكيد! " else "حسناً. "
                    }
                    CommunicationTone.CALM_THOUGHTFUL -> {
                        if (isQuestion) "دعني أفكر... " else "حقيقةً، "
                    }
                    CommunicationTone.SERIOUS_DIRECT -> {
                        if (isQuestion) "نعم. " else ""
                    }
                    CommunicationTone.PROFESSIONAL -> {
                        if (isQuestion) "وفقاً للمعلومات المتاحة: " else ""
                    }
                    CommunicationTone.ENCOURAGING_MENTOR -> {
                        if (isQuestion) "طيب، دعني أوضح... " else "مهم أن تعرف أن... "
                    }
                    CommunicationTone.PLAYFUL -> {
                        if (isQuestion) "أوه، سؤال جيد! " else "هاه! "
                    }
                }
            }
            CommunicationLanguage.ENGLISH,
            CommunicationLanguage.MIXED_ARABIC_ENGLISH -> {
                when (personality.tone) {
                    CommunicationTone.FRIENDLY_WARM -> {
                        if (isQuestion) "Of course! " else "Okay. "
                    }
                    CommunicationTone.CALM_THOUGHTFUL -> {
                        if (isQuestion) "Let me think... " else "Actually, "
                    }
                    CommunicationTone.SERIOUS_DIRECT -> {
                        if (isQuestion) "Yes. " else ""
                    }
                    CommunicationTone.PROFESSIONAL -> {
                        if (isQuestion) "According to available information: " else ""
                    }
                    CommunicationTone.ENCOURAGING_MENTOR -> {
                        if (isQuestion) "Alright, let me explain... " else "It's important to know that... "
                    }
                    CommunicationTone.PLAYFUL -> {
                        if (isQuestion) "Ooh, good question! " else "Ah! "
                    }
                }
            }
        }
    }
    
    /**
     * Format a list of items according to personality style
     */
    fun formatList(
        items: List<String>,
        personality: PersonalityPreferences
    ): String {
        return when (personality.style) {
            CommunicationStyle.CONCISE -> {
                items.joinToString(", ")
            }
            CommunicationStyle.BALANCED,
            CommunicationStyle.STEP_BY_STEP -> {
                items.joinToString("\n• ")
            }
            CommunicationStyle.DETAILED,
            CommunicationStyle.DEEP_EDUCATIONAL -> {
                items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
            }
            CommunicationStyle.SOCRATIC -> {
                items.joinToString("\n\n")
            }
        }
    }
    
    /**
     * Format a number according to personality elaboration level
     */
    fun formatNumber(
        number: Double,
        personality: PersonalityPreferences,
        unit: String = ""
    ): String {
        return when (personality.elaboration) {
            ElaborationLevel.MINIMAL -> {
                if (number == number.toLong().toDouble()) {
                    "${number.toLong()}$unit"
                } else {
                    "${"%.1f".format(number)}$unit"
                }
            }
            ElaborationLevel.MODERATE -> {
                if (number == number.toLong().toDouble()) {
                    "${number.toLong()} $unit"
                } else {
                    "${"%.2f".format(number)} $unit"
                }
            }
            ElaborationLevel.DETAILED,
            ElaborationLevel.EXHAUSTIVE -> {
                "${"%.4f".format(number)} $unit"
            }
        }
    }
}
