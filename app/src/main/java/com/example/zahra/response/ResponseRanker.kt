package com.example.zahra.response

import com.example.zahra.context.ConversationState
import com.example.zahra.personality.PersonalityPreferences

/**
 * Response Ranker
 * 
 * Ranks response candidates based on:
 * - Relevance to the request
 * - Context appropriateness
 * - Personality match
 * - Confidence scores
 */
class ResponseRanker {
    
    /**
     * Rank a list of response templates
     * 
     * @param templates List of candidate templates
     * @param request The user's request
     * @param context Current conversation context
     * @return List of templates sorted by rank (highest first)
     */
    fun rankTemplates(
        templates: List<ResponseTemplate>,
        request: String,
        context: ConversationState
    ): List<ResponseTemplate> {
        val normalizedRequest = request.lowercase()
        
        return templates.sortedByDescending { template ->
            calculateTemplateScore(template, normalizedRequest, context)
        }
    }
    
    /**
     * Calculate a score for a template
     */
    private fun calculateTemplateScore(
        template: ResponseTemplate,
        request: String,
        context: ConversationState
    ): Double {
        var score = template.priority * 10.0 // Base score from priority
        
        // Add relevance score
        score += calculateRelevanceScore(template, request) * 5.0
        
        // Add context score
        score += calculateContextScore(template, context) * 3.0
        
        // Add style match score
        // Note: Personality is not passed here, so we use a default
        score += 2.0 // Default style match
        
        return score
    }
    
    /**
     * Calculate how relevant a template is to the request
     */
    private fun calculateRelevanceScore(
        template: ResponseTemplate,
        request: String
    ): Double {
        val templateLower = template.template.lowercase()
        val requestWords = request.split("\\s+".toRegex())
        
        // Count matching words
        val matchingWords = requestWords.count { word ->
            word.length > 3 && templateLower.contains(word)
        }
        
        // Normalize by template length
        val templateWordCount = templateLower.split("\\s+".toRegex()).size
        
        return if (templateWordCount > 0) {
            matchingWords.toDouble() / templateWordCount.coerceAtLeast(1)
        } else {
            0.0
        }
    }
    
    /**
     * Calculate how well a template matches the context
     */
    private fun calculateContextScore(
        template: ResponseTemplate,
        context: ConversationState
    ): Double {
        var score = 0.0
        
        // If we have a current topic, check if template mentions it
        context.currentTopic?.let { topic ->
            if (template.template.contains(topic, ignoreCase = true)) {
                score += 0.5
            }
        }
        
        // Check if template references previous turns
        context.getPreviousTurn()?.let { previousTurn ->
            if (template.template.contains("{previous}", ignoreCase = true) ||
                template.template.contains("{that}", ignoreCase = true)) {
                score += 0.3
            }
        }
        
        // Longer conversations prefer more concise responses
        if (context.turns.size > 15) {
            when (template.style) {
                ResponseStyle.CONCISE -> score += 0.2
                ResponseStyle.NEUTRAL -> score += 0.1
                else -> {}
            }
        }
        
        // Shorter conversations can handle more detailed responses
        if (context.turns.size <= 5) {
            when (template.style) {
                ResponseStyle.DETAILED -> score += 0.2
                ResponseStyle.EDUCATIONAL -> score += 0.15
                else -> {}
            }
        }
        
        return score.coerceAtMost(1.0)
    }
    
    /**
     * Rank a list of knowledge records
     */
    fun rankKnowledgeRecords(
        records: List<com.example.zahra.knowledge.model.KnowledgeRecord>,
        query: String,
        personality: PersonalityPreferences
    ): List<com.example.zahra.knowledge.model.KnowledgeRecord> {
        val normalizedQuery = query.lowercase()
        
        return records.sortedByDescending { record ->
            val relevanceScore = calculateKnowledgeRelevance(record, normalizedQuery)
            val personalityScore = calculateKnowledgePersonalityMatch(record, personality)
            
            // Combine scores with weights
            relevanceScore * 0.7 + personalityScore * 0.3
        }
    }
    
    /**
     * Calculate relevance of a knowledge record to a query
     */
    private fun calculateKnowledgeRelevance(
        record: com.example.zahra.knowledge.model.KnowledgeRecord,
        query: String
    ): Double {
        val titleLower = record.title.lowercase()
        val contentLower = record.canonicalContent.lowercase()
        val queryWords = query.split("\\s+".toRegex())
        
        // Count matching words in title (higher weight)
        val titleMatches = queryWords.count { word ->
            word.length > 2 && titleLower.contains(word)
        }
        
        // Count matching words in content
        val contentMatches = queryWords.count { word ->
            word.length > 2 && contentLower.contains(word)
        }
        
        // Normalize by query length
        val queryWordCount = queryWords.size.coerceAtLeast(1)
        
        // Title matches are more important
        val score = (titleMatches * 2.0 + contentMatches) / queryWordCount
        
        return score.coerceAtMost(1.0)
    }
    
    /**
     * Calculate how well a knowledge record matches personality preferences
     */
    private fun calculateKnowledgePersonalityMatch(
        record: com.example.zahra.knowledge.model.KnowledgeRecord,
        personality: PersonalityPreferences
    ): Double {
        var score = 0.0
        
        // Prefer records in the user's language
        if (record.language == personality.language.toString()) {
            score += 0.3
        }
        
        // Prefer verified records for serious personalities
        if (record.verificationStatus.isVerified && 
            personality.tone == com.example.zahra.personality.CommunicationTone.SERIOUS_DIRECT) {
            score += 0.2
        }
        
        // Prefer more detailed content for detailed personalities
        if (personality.communicationStyle == com.example.zahra.personality.CommunicationStyle.DETAILED &&
            record.canonicalContent.length > 200) {
            score += 0.1
        }
        
        return score.coerceAtMost(1.0)
    }
}
