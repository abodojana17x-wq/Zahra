package com.example.zahra.personality

import androidx.compose.ui.graphics.Color
import com.example.zahra.model.ZahraMessage
import com.example.zahra.model.ZahraResponse

/**
 * Personality System for Zahra
 * 
 * This system allows users to customize Zahra's communication style, tone, language,
 * and expressiveness while maintaining the core principle that personality affects
 * PRESENTATION ONLY, not TRUTH.
 * 
 * Key Invariants:
 * - Same factual question must receive substantively consistent answers across personalities
 * - Playful personality must not fabricate evidence
 * - Serious personality must not become rude
 * - Islamic-grounded personality must not invent religious rulings
 * - Uncertainty must always be acknowledged regardless of personality
 */

/**
 * Communication tone settings
 */
enum class CommunicationTone(
    val labelAr: String,
    val labelEn: String,
    val descriptionAr: String,
    val descriptionEn: String
) {
    FRIENDLY_WARM(
        labelAr = "ودود ودافئ",
        labelEn = "Friendly & Warm",
        descriptionAr = "أسلوب ودود ودافئ مع نبرة إيجابية",
        descriptionEn = "Friendly and warm with positive tone"
    ),
    CALM_THOUGHTFUL(
        labelAr = "هادئ ومفكر",
        labelEn = "Calm & Thoughtful",
        descriptionAr = "أسلوب هادئ ومتأني مع تفكير عميق",
        descriptionEn = "Calm and thoughtful with deep reflection"
    ),
    SERIOUS_DIRECT(
        labelAr = "جاد ومباشر",
        labelEn = "Serious & Direct",
        descriptionAr = "أسلوب جاد ومباشر بدون تزويق",
        descriptionEn = "Serious and direct without embellishment"
    ),
    PROFESSIONAL(
        labelAr = "مهني",
        labelEn = "Professional",
        descriptionAr = "أسلوب مهني رسمي ومنظم",
        descriptionEn = "Professional, formal, and organized"
    ),
    ENCOURAGING_MENTOR(
        labelAr = "مشجع ومرشد",
        labelEn = "Encouraging & Mentor-like",
        descriptionAr = "أسلوب مشجع وداعم مثل المرشد",
        descriptionEn = "Encouraging and supportive like a mentor"
    ),
    PLAYFUL(
        labelAr = "مرح",
        labelEn = "Playful",
        descriptionAr = "أسلوب مرح مع لمسات من الفكاهة",
        descriptionEn = "Playful with touches of humor"
    )
}

/**
 * Communication style settings
 */
enum class CommunicationStyle(
    val labelAr: String,
    val labelEn: String,
    val descriptionAr: String,
    val descriptionEn: String
) {
    CONCISE(
        labelAr = "مختصر",
        labelEn = "Concise",
        descriptionAr = "إجابات قصيرة ومباشرة",
        descriptionEn = "Short and direct answers"
    ),
    BALANCED(
        labelAr = "متوازن",
        labelEn = "Balanced",
        descriptionAr = "توازن بين الإيجاز والتفصيل",
        descriptionEn = "Balance between brevity and detail"
    ),
    DETAILED(
        labelAr = "مفصل",
        labelEn = "Detailed",
        descriptionAr = "إجابات مفصلة وشاملة",
        descriptionEn = "Detailed and comprehensive answers"
    ),
    DEEP_EDUCATIONAL(
        labelAr = "تعليمي عميق",
        labelEn = "Deep Educational",
        descriptionAr = "شرح عميق مع مفاهيم أساسية",
        descriptionEn = "Deep explanations with fundamental concepts"
    ),
    STEP_BY_STEP(
        labelAr = "خطوة بخطوة",
        labelEn = "Step-by-Step",
        descriptionAr = "شرحات منظمة خطوة بخطوة",
        descriptionEn = "Organized step-by-step explanations"
    ),
    SOCRATIC(
        labelAr = "سقراطي",
        labelEn = "Socratic",
        descriptionAr = "أسئلة استفهامية لتوجيه التفكير",
        descriptionEn = "Thought-provoking questions to guide thinking"
    )
}

/**
 * Communication language settings
 */
enum class CommunicationLanguage(
    val code: String,
    val labelAr: String,
    val labelEn: String
) {
    MODERN_STANDARD_ARABIC(
        code = "ar-MSA",
        labelAr = "العربية الفصحى",
        labelEn = "Modern Standard Arabic"
    ),
    EGYPTIAN_ARABIC(
        code = "ar-EG",
        labelAr = "العربية المصرية",
        labelEn = "Egyptian Arabic"
    ),
    ENGLISH(
        code = "en",
        labelAr = "الإنجليزية",
        labelEn = "English"
    ),
    MIXED_ARABIC_ENGLISH(
        code = "ar-en",
        labelAr = "مختلطة عربي-إنجليزية",
        labelEn = "Mixed Arabic-English"
    )
}

/**
 * Humor frequency settings
 */
enum class HumorFrequency(
    val value: Int,
    val labelAr: String,
    val labelEn: String
) {
    NONE(0, "بدون", "None"),
    RARE(1, "نادر", "Rare"),
    OCCASIONAL(2, "من وقت لآخر", "Occasional"),
    FREQUENT(3, "متكرر", "Frequent")
}

/**
 * Emoji frequency settings
 */
enum class EmojiFrequency(
    val value: Int,
    val labelAr: String,
    val labelEn: String
) {
    NONE(0, "بدون", "None"),
    RARE(1, "نادر", "Rare"),
    MODERATE(2, "متوسط", "Moderate"),
    FREQUENT(3, "متكرر", "Frequent")
}

/**
 * Energy level settings
 */
enum class EnergyLevel(
    val value: Int,
    val labelAr: String,
    val labelEn: String
) {
    LOW(0, "منخفض", "Low"),
    MODERATE(1, "متوسط", "Moderate"),
    HIGH(2, "مرتفع", "High")
}

/**
 * Formality level settings
 */
enum class FormalityLevel(
    val value: Int,
    val labelAr: String,
    val labelEn: String
) {
    CASUAL(0, "غير رسمي", "Casual"),
    MODERATE(1, "متوسط", "Moderate"),
    FORMAL(2, "رسمي", "Formal"),
    VERY_FORMAL(3, "رسمي جدا", "Very Formal")
}

/**
 * Elaboration level settings
 */
enum class ElaborationLevel(
    val value: Int,
    val labelAr: String,
    val labelEn: String
) {
    MINIMAL(0, "أدنى", "Minimal"),
    MODERATE(1, "متوسط", "Moderate"),
    DETAILED(2, "مفصل", "Detailed"),
    EXHAUSTIVE(3, "شامل", "Exhaustive")
}

/**
 * Islamic reference depth settings
 */
enum class IslamicReferenceDepth(
    val value: Int,
    val labelAr: String,
    val labelEn: String,
    val descriptionAr: String,
    val descriptionEn: String
) {
    MINIMAL(
        0,
        "أدنى",
        "Minimal",
        "ذكر المصدر الرئيسي فقط",
        "Mention main source only"
    ),
    BALANCED(
        1,
        "متوازن",
        "Balanced",
        "ذكر المصدر مع شرح مختصر",
        "Mention source with brief explanation"
    ),
    DETAILED(
        2,
        "مفصل",
        "Detailed",
        "ذكر المصدر مع شرح مفصل وتفسير",
        "Mention source with detailed explanation and interpretation"
    ),
    SCHOLARLY(
        3,
        "علمي",
        "Scholarly",
        "ذكر المصدر مع أدلة ووجهات نظر مختلفة",
        "Mention source with evidence and different viewpoints"
    )
}

/**
 * Complete personality preferences model
 * 
 * This data class represents all configurable aspects of Zahra's personality.
 * It is versioned to allow for backward-compatible changes.
 */
data class PersonalityPreferences(
    /** Schema version for backward compatibility */
    val version: Int = 1,
    
    // ===== TONE =====
    /** Overall communication tone */
    val tone: CommunicationTone = CommunicationTone.FRIENDLY_WARM,
    
    // ===== STYLE =====
    /** Communication style */
    val style: CommunicationStyle = CommunicationStyle.BALANCED,
    
    // ===== LANGUAGE =====
    /** Primary communication language */
    val language: CommunicationLanguage = CommunicationLanguage.MODERN_STANDARD_ARABIC,
    
    // ===== EXPRESSIVENESS =====
    /** How often to use humor */
    val humorFrequency: HumorFrequency = HumorFrequency.OCCASIONAL,
    
    /** How often to use emojis */
    val emojiFrequency: EmojiFrequency = EmojiFrequency.MODERATE,
    
    /** Overall energy level */
    val energyLevel: EnergyLevel = EnergyLevel.MODERATE,
    
    // ===== CONTENT PREFERENCES =====
    /** Whether to use examples in explanations */
    val useExamples: Boolean = true,
    
    /** Whether to use analogies in explanations */
    val useAnalogies: Boolean = true,
    
    /** Whether to use storytelling */
    val useStorytelling: Boolean = true,
    
    // ===== FORMALITY =====
    /** Level of formality */
    val formality: FormalityLevel = FormalityLevel.MODERATE,
    
    // ===== ELABORATION =====
    /** Level of detail in responses */
    val elaboration: ElaborationLevel = ElaborationLevel.MODERATE,
    
    // ===== ISLAMIC GROUNDING =====
    /** Depth of Islamic references in religious answers */
    val islamicReferenceDepth: IslamicReferenceDepth = IslamicReferenceDepth.BALANCED,
    
    // ===== METADATA =====
    /** When these preferences were created */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** When these preferences were last updated */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** Whether these are the default preferences */
    val isDefault: Boolean = false
) {
    /**
     * Create a copy with updated timestamp
     */
    fun withUpdate(): PersonalityPreferences {
        return this.copy(
            updatedAt = System.currentTimeMillis(),
            isDefault = false
        )
    }
    
    /**
     * Check if these preferences are different from another set
     */
    fun isDifferentFrom(other: PersonalityPreferences): Boolean {
        return this != other
    }
    
    /**
     * Get a summary of these preferences for display
     */
    fun getSummary(): String {
        return buildString {
            append("${tone.labelAr}/${tone.labelEn}")
            append(", ${style.labelAr}/${style.labelEn}")
            append(", ${language.labelAr}/${language.labelEn}")
        }
    }
    
    companion object {
        /**
         * Default personality preferences that match Zahra's current behavior
         */
        fun getDefault(): PersonalityPreferences {
            return PersonalityPreferences(
                version = 1,
                tone = CommunicationTone.FRIENDLY_WARM,
                style = CommunicationStyle.BALANCED,
                language = CommunicationLanguage.MODERN_STANDARD_ARABIC,
                humorFrequency = HumorFrequency.OCCASIONAL,
                emojiFrequency = EmojiFrequency.MODERATE,
                energyLevel = EnergyLevel.MODERATE,
                useExamples = true,
                useAnalogies = true,
                useStorytelling = true,
                formality = FormalityLevel.MODERATE,
                elaboration = ElaborationLevel.MODERATE,
                islamicReferenceDepth = IslamicReferenceDepth.BALANCED,
                isDefault = true
            )
        }
        
        /**
         * Create personality from natural language description
         */
        fun fromNaturalLanguage(description: String): PersonalityPreferences {
            val default = getDefault()
            val lowerDesc = description.lowercase()
            
            return default.copy(
                tone = when {
                    lowerDesc.contains("serious") || lowerDesc.contains("جاد") -> CommunicationTone.SERIOUS_DIRECT
                    lowerDesc.contains("calm") || lowerDesc.contains("هادئ") -> CommunicationTone.CALM_THOUGHTFUL
                    lowerDesc.contains("professional") || lowerDesc.contains("مهني") -> CommunicationTone.PROFESSIONAL
                    lowerDesc.contains("mentor") || lowerDesc.contains("مرشد") -> CommunicationTone.ENCOURAGING_MENTOR
                    lowerDesc.contains("playful") || lowerDesc.contains("مرح") -> CommunicationTone.PLAYFUL
                    else -> default.tone
                },
                style = when {
                    lowerDesc.contains("concise") || lowerDesc.contains("مختصر") -> CommunicationStyle.CONCISE
                    lowerDesc.contains("detailed") || lowerDesc.contains("مفصل") -> CommunicationStyle.DETAILED
                    lowerDesc.contains("step by step") || lowerDesc.contains("خطوة") -> CommunicationStyle.STEP_BY_STEP
                    lowerDesc.contains("educational") || lowerDesc.contains("تعليمي") -> CommunicationStyle.DEEP_EDUCATIONAL
                    lowerDesc.contains("socratic") || lowerDesc.contains("سقراطي") -> CommunicationStyle.SOCRATIC
                    else -> default.style
                },
                language = when {
                    lowerDesc.contains("egyptian") || lowerDesc.contains("مصرية") -> CommunicationLanguage.EGYPTIAN_ARABIC
                    lowerDesc.contains("english") -> CommunicationLanguage.ENGLISH
                    lowerDesc.contains("mixed") || lowerDesc.contains("مختلطة") -> CommunicationLanguage.MIXED_ARABIC_ENGLISH
                    else -> default.language
                },
                humorFrequency = when {
                    lowerDesc.contains("funny") || lowerDesc.contains("مرح") || lowerDesc.contains("more humor") -> HumorFrequency.FREQUENT
                    lowerDesc.contains("no humor") || lowerDesc.contains("بدون فكاهة") -> HumorFrequency.NONE
                    lowerDesc.contains("less humor") || lowerDesc.contains("أقل فكاهة") -> HumorFrequency.RARE
                    else -> default.humorFrequency
                },
                emojiFrequency = when {
                    lowerDesc.contains("more emoji") || lowerDesc.contains("أكثر رموز") -> EmojiFrequency.FREQUENT
                    lowerDesc.contains("no emoji") || lowerDesc.contains("بدون رموز") -> EmojiFrequency.NONE
                    lowerDesc.contains("fewer emoji") || lowerDesc.contains("أقل رموز") -> EmojiFrequency.RARE
                    else -> default.emojiFrequency
                }
            ).withUpdate()
        }
    }
}

/**
 * Result of parsing a personality change request
 */
sealed class PersonalityChangeResult {
    data class Success(
        val updatedPreferences: PersonalityPreferences,
        val changedFields: List<String>,
        val confirmationMessage: String
    ) : PersonalityChangeResult()
    
    data class Partial(
        val updatedPreferences: PersonalityPreferences,
        val recognizedFields: List<String>,
        val unrecognizedFields: List<String>,
        val confirmationMessage: String
    ) : PersonalityChangeResult()
    
    data class Invalid(
        val errorMessage: String
    ) : PersonalityChangeResult()
    
    data class NoChange(
        val message: String
    ) : PersonalityChangeResult()
}

/**
 * Command types for personality changes
 */
sealed class PersonalityCommand {
    data class SetTone(val tone: CommunicationTone) : PersonalityCommand()
    data class SetStyle(val style: CommunicationStyle) : PersonalityCommand()
    data class SetLanguage(val language: CommunicationLanguage) : PersonalityCommand()
    data class SetHumor(val frequency: HumorFrequency) : PersonalityCommand()
    data class SetEmoji(val frequency: EmojiFrequency) : PersonalityCommand()
    data class SetEnergy(val level: EnergyLevel) : PersonalityCommand()
    data class SetFormality(val level: FormalityLevel) : PersonalityCommand()
    data class SetElaboration(val level: ElaborationLevel) : PersonalityCommand()
    data class SetIslamicDepth(val depth: IslamicReferenceDepth) : PersonalityCommand()
    data class ToggleExamples(val enable: Boolean) : PersonalityCommand()
    data class ToggleAnalogies(val enable: Boolean) : PersonalityCommand()
    data class ToggleStorytelling(val enable: Boolean) : PersonalityCommand()
    object ResetToDefault : PersonalityCommand()
    data class NaturalLanguage(val description: String) : PersonalityCommand()
}
