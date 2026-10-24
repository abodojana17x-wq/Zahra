package com.example.zahra.personality

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

/**
 * Personality Storage
 * 
 * Handles secure persistence of personality preferences using Android's
 * SharedPreferences with optional encryption for sensitive data.
 * 
 * Note: Personality preferences are not considered highly sensitive,
 * but we still use secure storage practices.
 */
class PersonalityStorage(private val context: Context) {
    
    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val PREFS_NAME = "ZahraPersonalityStorage"
        private const val KEY_PERSONALITY = "personality_v2"
        private const val KEY_CHECKSUM = "personality_checksum"
        private const val KEY_VERSION = "personality_version"
        
        // Current version of the storage format
        private const val STORAGE_VERSION = 2
    }
    
    /**
     * Save personality preferences
     */
    fun savePersonality(preferences: PersonalityPreferences): Boolean {
        return try {
            val json = gson.toJson(preferences)
            val checksum = calculateChecksum(json)
            
            prefs.edit()
                .putString(KEY_PERSONALITY, json)
                .putString(KEY_CHECKSUM, checksum)
                .putInt(KEY_VERSION, STORAGE_VERSION)
                .apply()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load personality preferences
     */
    fun loadPersonality(): PersonalityPreferences? {
        val json = prefs.getString(KEY_PERSONALITY, null) ?: return null
        val storedChecksum = prefs.getString(KEY_CHECKSUM, null)
        val storedVersion = prefs.getInt(KEY_VERSION, 0)
        
        // Validate checksum
        if (storedChecksum != null) {
            val calculatedChecksum = calculateChecksum(json)
            if (calculatedChecksum != storedChecksum) {
                // Checksum mismatch - data may be corrupted
                return null
            }
        }
        
        // Validate version
        if (storedVersion != STORAGE_VERSION) {
            // Version mismatch - try to migrate or return null
            return tryMigrate(json, storedVersion)
        }
        
        // Parse JSON
        try {
            val type = object : TypeToken<PersonalityPreferences>() {}.type
            return gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Delete personality preferences (reset to default)
     */
    fun deletePersonality(): Boolean {
        return try {
            prefs.edit()
                .remove(KEY_PERSONALITY)
                .remove(KEY_CHECKSUM)
                .remove(KEY_VERSION)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if personality preferences exist
     */
    fun hasPersonality(): Boolean {
        return prefs.contains(KEY_PERSONALITY)
    }
    
    /**
     * Get the storage version
     */
    fun getStorageVersion(): Int {
        return prefs.getInt(KEY_VERSION, 0)
    }
    
    /**
     * Calculate SHA-256 checksum of a string
     */
    private fun calculateChecksum(data: String): String {
        try {
            val bytes = data.toByteArray(Charsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
    
    /**
     * Attempt to migrate from an older version
     */
    private fun tryMigrate(json: String, fromVersion: Int): PersonalityPreferences? {
        // For now, we don't support migration from v1
        // In the future, we can add migration logic here
        return null
    }
    
    /**
     * Export personality preferences to a shareable format
     */
    fun exportPersonality(): String? {
        val preferences = loadPersonality() ?: return null
        return gson.toJson(preferences)
    }
    
    /**
     * Import personality preferences from a string
     */
    fun importPersonality(json: String): Boolean {
        try {
            val type = object : TypeToken<PersonalityPreferences>() {}.type
            val preferences = gson.fromJson<PersonalityPreferences>(json, type)
            
            // Validate the imported preferences
            if (preferences.version > 1) {
                return false // Future version not supported
            }
            
            return savePersonality(preferences)
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Get all available personality presets
     */
    fun getPresets(): List<PersonalityPreset> {
        return listOf(
            PersonalityPreset(
                id = "default",
                nameAr = "الإفتراضي",
                nameEn = "Default",
                descriptionAr = "الإعدادات الافتراضية لزهرة",
                descriptionEn = "Zahra's default personality settings",
                preferences = PersonalityPreferences.getDefault()
            ),
            PersonalityPreset(
                id = "friendly_teacher",
                nameAr = "المعلمة الودودة",
                nameEn = "Friendly Teacher",
                descriptionAr = "ودودة وصبورة مع شرح مفصل",
                descriptionEn = "Friendly and patient with detailed explanations",
                preferences = PersonalityPreferences.getDefault().copy(
                    tone = CommunicationTone.ENCOURAGING_MENTOR,
                    style = CommunicationStyle.STEP_BY_STEP,
                    humorFrequency = HumorFrequency.OCCASIONAL,
                    emojiFrequency = EmojiFrequency.MODERATE,
                    useExamples = true,
                    useAnalogies = true
                )
            ),
            PersonalityPreset(
                id = "serious_professional",
                nameAr = "المهنية الجادة",
                nameEn = "Serious Professional",
                descriptionAr = "جاد ومهني مع إجابات مختصرة",
                descriptionEn = "Serious and professional with concise answers",
                preferences = PersonalityPreferences.getDefault().copy(
                    tone = CommunicationTone.SERIOUS_DIRECT,
                    style = CommunicationStyle.CONCISE,
                    humorFrequency = HumorFrequency.NONE,
                    emojiFrequency = EmojiFrequency.NONE,
                    formality = FormalityLevel.FORMAL
                )
            ),
            PersonalityPreset(
                id = "playful_friend",
                nameAr = "الصديق المرح",
                nameEn = "Playful Friend",
                descriptionAr = "مرح مع لمسات من الفكاهة",
                descriptionEn = "Playful with touches of humor",
                preferences = PersonalityPreferences.getDefault().copy(
                    tone = CommunicationTone.PLAYFUL,
                    style = CommunicationStyle.BALANCED,
                    humorFrequency = HumorFrequency.FREQUENT,
                    emojiFrequency = EmojiFrequency.FREQUENT,
                    formality = FormalityLevel.CASUAL
                )
            ),
            PersonalityPreset(
                id = "deep_scholar",
                nameAr = "العالم العميق",
                nameEn = "Deep Scholar",
                descriptionAr = "شرح عميق مع أدلة ومصادر",
                descriptionEn = "Deep explanations with evidence and sources",
                preferences = PersonalityPreferences.getDefault().copy(
                    tone = CommunicationTone.CALM_THOUGHTFUL,
                    style = CommunicationStyle.DEEP_EDUCATIONAL,
                    elaboration = ElaborationLevel.DETAILED,
                    islamicReferenceDepth = IslamicReferenceDepth.SCHOLARLY
                )
            )
        )
    }
    
    /**
     * Apply a preset
     */
    fun applyPreset(presetId: String): Boolean {
        val preset = getPresets().find { it.id == presetId } ?: return false
        return savePersonality(preset.preferences)
    }
}

/**
 * Personality preset for quick configuration
 */
data class PersonalityPreset(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val preferences: PersonalityPreferences
)
