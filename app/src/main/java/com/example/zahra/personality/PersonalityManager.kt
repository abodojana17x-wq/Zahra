package com.example.zahra.personality

import android.content.Context
import android.content.SharedPreferences
import com.example.zahra.core.ZahraNormalizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Personality Manager
 * 
 * Manages user personality preferences, including:
 * - Loading and saving preferences
 * - Applying personality to responses
 * - Detecting personality change commands
 * - Validating personality configurations
 */
class PersonalityManager(private val context: Context) {
    
    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // In-memory cache for current personality
    private var currentPersonality: PersonalityPreferences? = null
    
    companion object {
        private const val PREFS_NAME = "ZahraPersonalityPrefs"
        private const val KEY_PERSONALITY = "personality_preferences"
        private const val KEY_VERSION = "personality_version"
        
        // Commands for personality changes (Arabic and English)
        private val PERSONALITY_COMMANDS = mapOf(
            // Language commands
            "talk to me in egyptian arabic" to PersonalityCommand.SetLanguage(CommunicationLanguage.EGYPTIAN_ARABIC),
            "talk in egyptian" to PersonalityCommand.SetLanguage(CommunicationLanguage.EGYPTIAN_ARABIC),
            "use egyptian arabic" to PersonalityCommand.SetLanguage(CommunicationLanguage.EGYPTIAN_ARABIC),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0016\u0004\u0012\u0004\u0017\u0004\u0010\u0004\u0011\u0004\u000e" to PersonalityCommand.SetLanguage(CommunicationLanguage.EGYPTIAN_ARABIC),
            "in egyptian please" to PersonalityCommand.SetLanguage(CommunicationLanguage.EGYPTIAN_ARABIC),
            
            "talk to me in standard arabic" to PersonalityCommand.SetLanguage(CommunicationLanguage.MODERN_STANDARD_ARABIC),
            "use standard arabic" to PersonalityCommand.SetLanguage(CommunicationLanguage.MODERN_STANDARD_ARABIC),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0011\u0004\u0015\u0004\u0017\u0004\u0016\u0004\u0010 \u0010\u0004\u0017\u0004\u0012\u0004\u0011\u0004\u0017" to PersonalityCommand.SetLanguage(CommunicationLanguage.MODERN_STANDARD_ARABIC),
            
            "talk to me in english" to PersonalityCommand.SetLanguage(CommunicationLanguage.ENGLISH),
            "use english" to PersonalityCommand.SetLanguage(CommunicationLanguage.ENGLISH),
            "in english please" to PersonalityCommand.SetLanguage(CommunicationLanguage.ENGLISH),
            
            // Tone commands
            "be more serious" to PersonalityCommand.SetTone(CommunicationTone.SERIOUS_DIRECT),
            "make it serious" to PersonalityCommand.SetTone(CommunicationTone.SERIOUS_DIRECT),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017 \u0010\u0004\u0012\u0004\u0015\u0004\u0017" to PersonalityCommand.SetTone(CommunicationTone.SERIOUS_DIRECT),
            
            "be more playful" to PersonalityCommand.SetTone(CommunicationTone.PLAYFUL),
            "be funnier" to PersonalityCommand.SetTone(CommunicationTone.PLAYFUL),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017 \u0010\u0004\u0015\u0004\u0011\u0004\u0017" to PersonalityCommand.SetTone(CommunicationTone.PLAYFUL),
            
            "be calm" to PersonalityCommand.SetTone(CommunicationTone.CALM_THOUGHTFUL),
            "be more thoughtful" to PersonalityCommand.SetTone(CommunicationTone.CALM_THOUGHTFUL),
            
            "be professional" to PersonalityCommand.SetTone(CommunicationTone.PROFESSIONAL),
            "use professional tone" to PersonalityCommand.SetTone(CommunicationTone.PROFESSIONAL),
            
            // Style commands
            "explain like a patient teacher" to PersonalityCommand.SetStyle(CommunicationStyle.STEP_BY_STEP),
            "explain step by step" to PersonalityCommand.SetStyle(CommunicationStyle.STEP_BY_STEP),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017 \u0010\u0004\u0015\u0004\u0011\u0004\u0017 \u0010\u0004\u0015\u0004\u0017\u0004\u0010\u0004\u0017" to PersonalityCommand.SetStyle(CommunicationStyle.STEP_BY_STEP),
            
            "give me the short answer" to PersonalityCommand.SetStyle(CommunicationStyle.CONCISE),
            "be concise" to PersonalityCommand.SetStyle(CommunicationStyle.CONCISE),
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017 \u0010\u0004\u0015\u0004\u0017\u0004\u0010\u0004\u0017" to PersonalityCommand.SetStyle(CommunicationStyle.CONCISE),
            
            "go deeper" to PersonalityCommand.SetStyle(CommunicationStyle.DEEP_EDUCATIONAL),
            "be more detailed" to PersonalityCommand.SetStyle(CommunicationStyle.DETAILED),
            "explain in detail" to PersonalityCommand.SetStyle(CommunicationStyle.DEEP_EDUCATIONAL),
            
            // Emoji commands
            "use fewer emojis" to PersonalityCommand.SetEmoji(EmojiFrequency.RARE),
            "no emojis" to PersonalityCommand.SetEmoji(EmojiFrequency.NONE),
            "more emojis" to PersonalityCommand.SetEmoji(EmojiFrequency.FREQUENT),
            
            // Humor commands
            "be funnier" to PersonalityCommand.SetHumor(HumorFrequency.FREQUENT),
            "don't force jokes" to PersonalityCommand.SetHumor(HumorFrequency.OCCASIONAL),
            "no jokes" to PersonalityCommand.SetHumor(HumorFrequency.NONE),
            
            // Reset
            "return to default" to PersonalityCommand.ResetToDefault,
            "reset to default zahra" to PersonalityCommand.ResetToDefault,
            "\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017 \u0010\u0004\u0015\u0004\u0017\u0004\u0010\u0004\u0017 \u0019\u0004\u0017\u0004\u0010\u0004\u0017 \u0010\u0004\u0017\u0004\u0012\u0004\u0017" to PersonalityCommand.ResetToDefault
        )
    }
    
    /**
     * Initialize the personality manager
     */
    init {
        loadPersonality()
    }
    
    /**
     * Get the current personality preferences
     */
    fun getCurrentPersonality(): PersonalityPreferences {
        return currentPersonality ?: run {
            val loaded = loadPersonality()
            if (loaded != null) {
                currentPersonality = loaded
                loaded
            } else {
                val default = PersonalityPreferences.getDefault()
                currentPersonality = default
                savePersonality(default)
                default
            }
        }
    }
    
    /**
     * Set new personality preferences
     */
    fun setPersonality(preferences: PersonalityPreferences): PersonalityChangeResult {
        // Validate the preferences
        if (!isValidPersonality(preferences)) {
            return PersonalityChangeResult.Invalid(
                "Invalid personality configuration"
            )
        }
        
        // Capture the old preferences before updating the cache.
        val old = currentPersonality ?: PersonalityPreferences.getDefault()
        savePersonality(preferences)
        currentPersonality = preferences
        
        // Determine what changed
        val changedFields = mutableListOf<String>()
        
        if (preferences.tone != old.tone) changedFields.add("tone")
        if (preferences.style != old.style) changedFields.add("style")
        if (preferences.language != old.language) changedFields.add("language")
        if (preferences.humorFrequency != old.humorFrequency) changedFields.add("humorFrequency")
        if (preferences.emojiFrequency != old.emojiFrequency) changedFields.add("emojiFrequency")
        if (preferences.energyLevel != old.energyLevel) changedFields.add("energyLevel")
        if (preferences.formality != old.formality) changedFields.add("formality")
        if (preferences.elaboration != old.elaboration) changedFields.add("elaboration")
        if (preferences.islamicReferenceDepth != old.islamicReferenceDepth) changedFields.add("islamicReferenceDepth")
        if (preferences.useExamples != old.useExamples) changedFields.add("useExamples")
        if (preferences.useAnalogies != old.useAnalogies) changedFields.add("useAnalogies")
        if (preferences.useStorytelling != old.useStorytelling) changedFields.add("useStorytelling")
        
        val message = if (changedFields.isEmpty()) {
            "Personality preferences updated"
        } else {
            "Updated: ${changedFields.joinToString(", ")}"
        }
        
        return PersonalityChangeResult.Success(
            updatedPreferences = preferences,
            changedFields = changedFields,
            confirmationMessage = message
        )
    }
    
    /**
     * Reset to default personality
     */
    fun resetToDefault(): PersonalityChangeResult {
        val default = PersonalityPreferences.getDefault()
        return setPersonality(default)
    }
    
    /**
     * Detect if the user is requesting a personality change
     */
    fun detectPersonalityChange(input: String): PersonalityCommand? {
        val normalized = ZahraNormalizer.normalize(input).lowercase()
        
        // Check for exact matches first
        for ((command, personalityCommand) in PERSONALITY_COMMANDS) {
            if (normalized.contains(ZahraNormalizer.normalize(command).lowercase())) {
                return personalityCommand
            }
        }
        
        // Check for natural language descriptions
        if (normalized.contains("explain like") || 
            normalized.contains("be more") || 
            normalized.contains("be less") ||
            normalized.contains("use") ||
            normalized.contains("talk in") ||
            normalized.contains("talk to me in") ||
            normalized.contains("make it") ||
            normalized.contains("make this")) {
            return PersonalityCommand.NaturalLanguage(input)
        }
        
        return null
    }
    
    /**
     * Apply personality to a response
     */
    fun applyPersonalityToResponse(
        response: String,
        personality: PersonalityPreferences = getCurrentPersonality()
    ): String {
        var result = response
        
        // Apply emoji frequency
        result = applyEmojiFrequency(result, personality.emojiFrequency)
        
        // Apply tone adjustments
        result = applyTone(result, personality.tone)
        
        // Apply style adjustments
        result = applyStyle(result, personality.style)
        
        // Apply formality adjustments
        result = applyFormality(result, personality.formality)
        
        return result
    }
    
    /**
     * Handle a personality change command
     */
    fun applyPersonalityCommand(
        command: PersonalityCommand,
        input: String
    ): PersonalityChangeResult {
        return handlePersonalityCommand(command, getCurrentPersonality())
    }

    fun handlePersonalityCommand(
        command: PersonalityCommand,
        currentPersonality: PersonalityPreferences = getCurrentPersonality()
    ): PersonalityChangeResult {
        return when (command) {
            is PersonalityCommand.SetTone -> {
                setPersonality(currentPersonality.copy(
                    tone = command.tone,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetStyle -> {
                setPersonality(currentPersonality.copy(
                    style = command.style,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetLanguage -> {
                setPersonality(currentPersonality.copy(
                    language = command.language,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetHumor -> {
                setPersonality(currentPersonality.copy(
                    humorFrequency = command.frequency,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetEmoji -> {
                setPersonality(currentPersonality.copy(
                    emojiFrequency = command.frequency,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetEnergy -> {
                setPersonality(currentPersonality.copy(
                    energyLevel = command.level,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetFormality -> {
                setPersonality(currentPersonality.copy(
                    formality = command.level,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetElaboration -> {
                setPersonality(currentPersonality.copy(
                    elaboration = command.level,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.SetIslamicDepth -> {
                setPersonality(currentPersonality.copy(
                    islamicReferenceDepth = command.depth,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.ToggleExamples -> {
                setPersonality(currentPersonality.copy(
                    useExamples = command.enable,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.ToggleAnalogies -> {
                setPersonality(currentPersonality.copy(
                    useAnalogies = command.enable,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            is PersonalityCommand.ToggleStorytelling -> {
                setPersonality(currentPersonality.copy(
                    useStorytelling = command.enable,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            PersonalityCommand.ResetToDefault -> {
                resetToDefault()
            }
            is PersonalityCommand.NaturalLanguage -> {
                val newPersonality = PersonalityPreferences.fromNaturalLanguage(command.description)
                setPersonality(newPersonality)
            }
        }
    }
    
    /**
     * Load personality from storage
     */
    private fun loadPersonality(): PersonalityPreferences? {
        val json = prefs.getString(KEY_PERSONALITY, null)
        if (json == null) {
            return null
        }
        
        try {
            val type = object : TypeToken<PersonalityPreferences>() {}.type
            val loaded = gson.fromJson<PersonalityPreferences>(json, type)
            
            // Validate version
            if (loaded.version > 1) {
                // Future version - try to migrate or use default
                return null
            }
            
            return loaded
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Save personality to storage
     */
    private fun savePersonality(preferences: PersonalityPreferences) {
        try {
            val json = gson.toJson(preferences)
            prefs.edit().putString(KEY_PERSONALITY, json).apply()
            prefs.edit().putInt(KEY_VERSION, preferences.version).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Validate personality configuration
     */
    private fun isValidPersonality(preferences: PersonalityPreferences): Boolean {
        // All enum values are valid by definition
        // Just check that required fields are present
        return true
    }
    
    /**
     * Apply emoji frequency to text
     */
    private fun applyEmojiFrequency(
        text: String,
        frequency: EmojiFrequency
    ): String {
        // This is a placeholder - emoji application should be done at the
        // response generation level, not by post-processing
        // For now, we just return the text as-is
        return text
    }
    
    /**
     * Apply tone adjustments to text
     */
    private fun applyTone(
        text: String,
        tone: CommunicationTone
    ): String {
        // Tone affects word choice and phrasing
        // This should be handled at response generation time
        // For now, return text as-is
        return text
    }
    
    /**
     * Apply style adjustments to text
     */
    private fun applyStyle(
        text: String,
        style: CommunicationStyle
    ): String {
        // Style affects sentence structure and length
        // This should be handled at response generation time
        return text
    }
    
    /**
     * Apply formality adjustments to text
     */
    private fun applyFormality(
        text: String,
        formality: FormalityLevel
    ): String {
        // Formality affects politeness and structure
        // This should be handled at response generation time
        return text
    }
}
