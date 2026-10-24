package com.example.zahra.islamic

import android.content.Context
import com.example.zahra.knowledge.engine.KnowledgeEngine
import com.example.zahra.knowledge.model.KnowledgeRecord
import com.example.zahra.knowledge.model.KnowledgeRetrievalResult
import com.example.zahra.knowledge.model.KnowledgeRetrievalState
import com.example.zahra.model.ZahraResponse
import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.EvidenceType
import com.example.zahra.model.GroundedFact
import com.example.zahra.reasoning.EpistemicGroundingPipeline

/**
 * Islamic Knowledge Engine
 * 
 * Provides specialized handling for Islamic knowledge queries with:
 * - Source hierarchy (Qur'an > Authentic Hadith > Scholarly Consensus > Individual Opinions)
 * - Authenticity verification
 * - Proper citation and attribution
 * - Uncertainty handling for disputed matters
 * 
 * Core Principle: Never fabricate religious rulings or sources.
 * Always distinguish between established teachings and scholarly disagreement.
 */
class IslamicKnowledgeEngine(private val context: Context) {
    
    private val knowledgeEngine = KnowledgeEngine(context)
    
    // Source hierarchy levels
    enum class SourceLevel {
        QURAN,                    // Highest authority
        AUTHENTIC_HADITH,        // Sahih/Bukhari/Muslim
        STRONG_HADITH,           // Hasan
        WEAKE_HADITH,            // Da'eef (must be clearly marked)
        SCHOLARLY_CONSENSUS,     // Ijma
        MAJORITY_OPINION,       // Majority of scholars
        SCHOLARLY_OPINION,       // Individual scholar opinion
        DISPUTED,                // Known disagreement
        UNVERIFIED               // Not authenticated
    }
    
    /**
     * Query Islamic knowledge with proper source handling
     */
    fun queryIslamicKnowledge(
        query: String,
        language: String = "ar"
    ): IslamicResponse {
        // First, check for impermissible requests
        val impermissible = checkImpermissibleRequest(query)
        if (impermissible != null) {
            return IslamicResponse(
                text = impermissible.text,
                intent = "ask_religion",
                confidence = impermissible.confidence,
                confidenceTier = ConfidenceTier.DIRECT,
                outcomeState = impermissible.outcomeState,
                source = impermissible.source,
                sourceLevel = SourceLevel.QURAN,
                isAuthentic = true,
                isDisputed = false,
                requiresScholar = false,
                followups = impermissible.followups
            )
        }
        
        // Query the knowledge engine for Islamic content
        val result = knowledgeEngine.query(query)
        
        return when (result.state) {
            KnowledgeRetrievalState.VERIFIED_MATCH -> {
                val record = result.bestMatch?.record ?: return buildNoKnowledgeResponse(query)
                buildIslamicResponse(record, result.bestMatch?.relevanceScore ?: 0.0)
            }
            KnowledgeRetrievalState.TRACEABLE_CANDIDATE -> {
                val record = result.bestMatch?.record ?: return buildNoKnowledgeResponse(query)
                buildIslamicResponse(record, result.bestMatch?.relevanceScore ?: 0.0, isVerified = false)
            }
            else -> {
                buildNoKnowledgeResponse(query)
            }
        }
    }
    
    /**
     * Build an Islamic response from a knowledge record
     */
    private fun buildIslamicResponse(
        record: KnowledgeRecord,
        relevanceScore: Double,
        isVerified: Boolean = true
    ): IslamicResponse {
        val sourceLevel = determineSourceLevel(record)
        val (isAuthentic, isDisputed, requiresScholar) = analyzeSourceReliability(record, sourceLevel)
        
        val confidenceTier = when {
            relevanceScore >= 0.75 -> ConfidenceTier.DIRECT
            relevanceScore >= 0.40 -> ConfidenceTier.QUALIFIED
            else -> ConfidenceTier.UNCERTAIN
        }
        
        val outcomeState = if (isAuthentic) OutcomeState.SUPPORTED else OutcomeState.AMBIGUOUS
        
        // Build the response text with proper attribution
        val responseText = buildIslamicResponseText(record, sourceLevel, isAuthentic, isDisputed)
        
        // Build source citation
        val sourceCitation = buildSourceCitation(record, sourceLevel)
        
        // Build follow-up questions
        val followups = buildFollowUpQuestions(record, isDisputed, requiresScholar)
        
        // Build evidence trace
        val trace = buildEvidenceTrace(record, sourceLevel, isAuthentic, isDisputed)
        
        return IslamicResponse(
            text = responseText,
            intent = "ask_religion",
            confidence = relevanceScore,
            confidenceTier = confidenceTier,
            outcomeState = outcomeState,
            source = sourceCitation,
            sourceLevel = sourceLevel,
            isAuthentic = isAuthentic,
            isDisputed = isDisputed,
            requiresScholar = requiresScholar,
            originalArabic = record.originalSourceText.takeIf { record.language == "ar" },
            verificationStatus = record.verificationStatus.labelAr,
            followups = followups,
            evidenceTrace = trace
        )
    }
    
    /**
     * Determine the source level of a knowledge record
     */
    private fun determineSourceLevel(record: KnowledgeRecord): SourceLevel {
        val sourceTitle = record.provenance.sourceTitle.lowercase()
        val sourceLocation = record.provenance.sourceLocation.lowercase()
        
        return when {
            // Qur'an
            sourceTitle.contains("quran") || sourceTitle.contains("قرآن") ||
            sourceTitle.contains("koran") || sourceLocation.contains("quran") -> {
                SourceLevel.QURAN
            }
            // Authentic Hadith (Sahih)
            sourceTitle.contains("sahih") || sourceTitle.contains("بخاري") ||
            sourceTitle.contains("muslim") || sourceTitle.contains("أبو داود") ||
            sourceTitle.contains("tirmidhi") || sourceTitle.contains("نسائي") ||
            sourceTitle.contains("ibn majah") || sourceLocation.contains("sahih") -> {
                SourceLevel.AUTHENTIC_HADITH
            }
            // Strong Hadith (Hasan)
            sourceTitle.contains("hasan") || sourceLocation.contains("hasan") -> {
                SourceLevel.STRONG_HADITH
            }
            // Weak Hadith
            sourceTitle.contains("daeef") || sourceTitle.contains("weak") ||
            sourceLocation.contains("daeef") || sourceLocation.contains("weak") -> {
                SourceLevel.WEAKE_HADITH
            }
            // Scholarly Consensus
            sourceTitle.contains("ijma") || sourceTitle.contains("إجماع") ||
            sourceTitle.contains("consensus") -> {
                SourceLevel.SCHOLARLY_CONSENSUS
            }
            // Majority opinion or individual scholar
            sourceTitle.contains("majority") || sourceTitle.contains("أكثر") ||
            sourceTitle.contains("scholar") || sourceTitle.contains("عالم") -> {
                SourceLevel.MAJORITY_OPINION
            }
            // Default to scholarly opinion
            else -> {
                SourceLevel.SCHOLARLY_OPINION
            }
        }
    }
    
    /**
     * Analyze source reliability
     */
    private fun analyzeSourceReliability(
        record: KnowledgeRecord,
        sourceLevel: SourceLevel
    ): Triple<Boolean, Boolean, Boolean> {
        val isVerified = record.verificationStatus.isVerified
        
        return when (sourceLevel) {
            SourceLevel.QURAN -> {
                Triple(true, false, false) // Authentic, not disputed, no scholar needed
            }
            SourceLevel.AUTHENTIC_HADITH -> {
                Triple(true, false, false)
            }
            SourceLevel.STRONG_HADITH -> {
                Triple(true, false, false)
            }
            SourceLevel.WEAKE_HADITH -> {
                Triple(false, true, true) // Not authentic, disputed, requires scholar
            }
            SourceLevel.SCHOLARLY_CONSENSUS -> {
                Triple(true, false, false)
            }
            SourceLevel.MAJORITY_OPINION -> {
                Triple(true, false, false)
            }
            SourceLevel.SCHOLARLY_OPINION -> {
                Triple(isVerified, true, true) // May be disputed, requires scholar
            }
            SourceLevel.DISPUTED -> {
                Triple(false, true, true)
            }
            SourceLevel.UNVERIFIED -> {
                Triple(false, true, true)
            }
        }
    }
    
    /**
     * Build response text with proper attribution
     */
    private fun buildIslamicResponseText(
        record: KnowledgeRecord,
        sourceLevel: SourceLevel,
        isAuthentic: Boolean,
        isDisputed: Boolean
    ): String {
        val title = record.title
        val content = record.canonicalContent
        val sourceTitle = record.provenance.sourceTitle
        val sourceLocation = record.provenance.sourceLocation
        
        // Start with the content
        var text = content
        
        // Add source attribution
        val attribution = when (sourceLevel) {
            SourceLevel.QURAN -> {
                "\n\n[: ${sourceTitle} - ${sourceLocation}]"
            }
            SourceLevel.AUTHENTIC_HADITH -> {
                "\n\n[ : ${sourceTitle} - ${sourceLocation}]"
            }
            SourceLevel.STRONG_HADITH -> {
                "\n\n[  (Hasan): ${sourceTitle} - ${sourceLocation}]"
            }
            SourceLevel.WEAKE_HADITH -> {
                "\n\n[  (Da'eef - Weak): ${sourceTitle} - ${sourceLocation}]\n\n:  "
            }
            else -> {
                "\n\n[Source: ${sourceTitle} - ${sourceLocation}]"
            }
        }
        
        text += attribution
        
        // Add uncertainty disclaimer if needed
        if (isDisputed || !isAuthentic) {
            text += "\n\n"
            if (isDisputed) {
                text += "\u000f\u000f: \u000f  "
            }
            if (!isAuthentic) {
                text += "\n\u000f\u000f: \u000f"
            }
        }
        
        return text
    }
    
    /**
     * Build source citation
     */
    private fun buildSourceCitation(
        record: KnowledgeRecord,
        sourceLevel: SourceLevel
    ): String {
        val sourceTitle = record.provenance.sourceTitle
        val sourceLocation = record.provenance.sourceLocation
        
        return when (sourceLevel) {
            SourceLevel.QURAN -> "[: ${sourceTitle} - ${sourceLocation}]"
            SourceLevel.AUTHENTIC_HADITH -> "[ : ${sourceTitle} - ${sourceLocation}]"
            SourceLevel.STRONG_HADITH -> "[  (Hasan): ${sourceTitle} - ${sourceLocation}]"
            SourceLevel.WEAKE_HADITH -> "[  (Da'eef): ${sourceTitle} - ${sourceLocation}]"
            else -> "[${sourceTitle} - ${sourceLocation}]"
        }
    }
    
    /**
     * Build follow-up questions
     */
    private fun buildFollowUpQuestions(
        record: KnowledgeRecord,
        isDisputed: Boolean,
        requiresScholar: Boolean
    ): List<String> {
        val followups = mutableListOf<String>()
        
        // Always suggest asking a scholar for religious matters
        followups.add(" ")
        
        // If disputed, suggest seeking clarification
        if (isDisputed) {
            followups.add(" ")
        }
        
        // Add topic-specific follow-ups
        val title = record.title.lowercase()
        if (title.contains("prayer") || title.contains("salah") || title.contains("صلاة")) {
            followups.add(" ")
        }
        
        return followups
    }
    
    /**
     * Build evidence trace
     */
    private fun buildEvidenceTrace(
        record: KnowledgeRecord,
        sourceLevel: SourceLevel,
        isAuthentic: Boolean,
        isDisputed: Boolean
    ): com.example.zahra.model.EvidenceGroundingTrace {
        val claim = record.canonicalContent.take(200)
        val evidenceType = if (isAuthentic) EvidenceType.VERIFIED else EvidenceType.LOCAL_CURATED
        val confidence = if (isAuthentic) 0.95 else 0.70
        
        val facts = mutableListOf<GroundedFact>()
        facts.add(
            GroundedFact(
                claim = claim,
                evidenceType = evidenceType,
                source = "${record.provenance.sourceTitle} - ${record.provenance.sourceLocation}",
                confidence = confidence
            )
        )
        
        val uncertainty = mutableListOf<String>()
        if (isDisputed) {
            uncertainty.add("This matter has scholarly disagreement")
        }
        if (!isAuthentic) {
            uncertainty.add("Source authenticity requires verification")
        }
        
        val outcomeState = if (isAuthentic) OutcomeState.SUPPORTED else OutcomeState.AMBIGUOUS
        
        return EpistemicGroundingPipeline.buildTrace(
            rawRequest = "",
            intent = "ask_religion",
            entities = emptyMap(),
            appliedRules = listOf("rule_islamic_sourcing"),
            facts = facts,
            uncertainty = uncertainty,
            outcomeState = outcomeState,
            sourceDesc = "Islamic Knowledge Engine"
        )
    }
    
    /**
     * Build no-knowledge response
     */
    private fun buildNoKnowledgeResponse(query: String): IslamicResponse {
        val trace = EpistemicGroundingPipeline.buildTrace(
            rawRequest = query,
            intent = "ask_religion",
            entities = emptyMap(),
            appliedRules = listOf("rule_epistemic_abstention"),
            facts = emptyList(),
            uncertainty = listOf("No authenticated Islamic knowledge found for this query"),
            outcomeState = OutcomeState.UNSUPPORTED,
            sourceDesc = "Islamic Knowledge Engine"
        )
        
        return IslamicResponse(
            text = "   .      ",
            intent = "ask_religion",
            confidence = 0.1,
            confidenceTier = ConfidenceTier.UNCERTAIN,
            outcomeState = OutcomeState.UNSUPPORTED,
            source = "Islamic Knowledge Engine",
            sourceLevel = SourceLevel.UNVERIFIED,
            isAuthentic = false,
            isDisputed = false,
            requiresScholar = true,
            followups = listOf(" "),
            evidenceTrace = trace
        )
    }
    
    /**
     * Check for impermissible requests
     */
    private fun checkImpermissibleRequest(query: String): ZahraResponse? {
        // This should be handled by ReligionEngine
        // For now, return null to use the existing implementation
        return null
    }
}

/**
 * Islamic Response data class
 */
data class IslamicResponse(
    val text: String,
    val intent: String,
    val confidence: Double,
    val confidenceTier: ConfidenceTier,
    val outcomeState: OutcomeState,
    val source: String,
    val sourceLevel: SourceLevel,
    val isAuthentic: Boolean,
    val isDisputed: Boolean,
    val requiresScholar: Boolean,
    val originalArabic: String? = null,
    val verificationStatus: String? = null,
    val followups: List<String> = emptyList(),
    val evidenceTrace: com.example.zahra.model.EvidenceGroundingTrace? = null
)
