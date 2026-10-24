package com.example.zahra.response

import com.example.zahra.context.ContextManager
import com.example.zahra.engines.KnowledgeEngine
import com.example.zahra.knowledge.engine.KnowledgeEngine as KnowledgeEngineImpl
import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.EvidenceGroundingTrace
import com.example.zahra.model.EvidenceType
import com.example.zahra.model.GroundedFact
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraResponse
import com.example.zahra.model.ZahraToolPayload
import com.example.zahra.personality.PersonalityManager
import com.example.zahra.personality.PersonalityPreferences
import com.example.zahra.reasoning.EpistemicGroundingPipeline

/**
 * Response Builder
 * 
 * Main orchestrator for building responses based on:
 * - User request
 * - Conversation context
 * - Personality preferences
 * - Available knowledge
 * 
 * This component provides a unified interface for response generation,
 * combining template-based, retrieval-augmented, and (future) generated responses.
 */
class ResponseBuilder(
    private val personalityManager: PersonalityManager,
    private val contextManager: ContextManager,
    private val knowledgeEngine: KnowledgeEngineImpl,
    private val templateSelector: TemplateSelector = TemplateSelector(),
    private val templateExpander: TemplateExpander = TemplateExpander(),
    private val responseRanker: ResponseRanker = ResponseRanker(),
    private val responseValidator: ResponseValidator = ResponseValidator()
) {
    
    companion object {
        // Minimum confidence threshold for direct responses
        private const val DIRECT_CONFIDENCE_THRESHOLD = 0.75
        
        // Minimum confidence threshold for qualified responses
        private const val QUALIFIED_CONFIDENCE_THRESHOLD = 0.40
    }
    
    /**
     * Build a response for a user request
     * 
     * @param input The user's raw input
     * @param normalizedInput The normalized version of the input
     * @param intent The detected intent
     * @param entities Extracted entities
     * @param confidence The confidence score
     * @return A ZahraResponse or null if response generation failed
     */
    fun buildResponse(
        input: String,
        normalizedInput: String = input,
        intent: String? = null,
        entities: Map<String, Any> = emptyMap(),
        confidence: Double = 0.0,
        outcomeState: OutcomeState = OutcomeState.UNSUPPORTED,
        source: String? = null
    ): ZahraResponse? {
        val personality = personalityManager.getCurrentPersonality()
        val conversationContext = contextManager.getCurrentConversation()
        
        // Try to build a response based on the available information
        val responseResult = tryBuildResponse(
            input = input,
            normalizedInput = normalizedInput,
            intent = intent,
            entities = entities,
            confidence = confidence,
            outcomeState = outcomeState,
            source = source,
            personality = personality,
            conversationContext = conversationContext
        )
        
        return responseResult?.let { result ->
            // Record the turn in context
            contextManager.recordTurn(
                text = input,
                normalizedText = normalizedInput,
                speaker = com.example.zahra.model.Sender.USER,
                intent = intent,
                entities = entities
            )
            
            // Apply personality styling
            val styledResponse = applyPersonalityToResponse(result, personality)
            
            // Validate the response
            if (responseValidator.validate(styledResponse, personality)) {
                styledResponse
            } else {
                // Fallback to simple response if validation fails
                ZahraResponse(
                    text = "I'm sorry, I couldn't generate a valid response.",
                    intent = intent ?: "unknown",
                    confidence = 0.1,
                    confidenceTier = ConfidenceTier.UNCERTAIN,
                    outcomeState = OutcomeState.UNSUPPORTED,
                    source = "ResponseBuilder fallback"
                )
            }
        }
    }
    
    /**
     * Try to build a response
     */
    private fun tryBuildResponse(
        input: String,
        normalizedInput: String,
        intent: String?,
        entities: Map<String, Any>,
        confidence: Double,
        outcomeState: OutcomeState,
        source: String?,
        personality: PersonalityPreferences,
        conversationContext: com.example.zahra.context.ConversationState
    ): ZahraResponse? {
        // Determine the response type based on intent and confidence
        return when {
            // High confidence with known intent - use template
            intent != null && confidence >= DIRECT_CONFIDENCE_THRESHOLD -> {
                buildTemplateResponse(
                    input = input,
                    intent = intent,
                    entities = entities,
                    confidence = confidence,
                    personality = personality,
                    conversationContext = conversationContext
                )
            }
            
            // Knowledge query - try to retrieve and format
            intent == "query_knowledge" || intent == "ask_religion" || intent == "ask_quran" || intent == "ask_hadith" -> {
                buildKnowledgeResponse(
                    input = input,
                    intent = intent,
                    entities = entities,
                    confidence = confidence,
                    personality = personality
                )
            }
            
            // Math or calculation - use existing engine
            intent == "calculate" || intent == "convert_unit" -> {
                buildCalculationResponse(
                    input = input,
                    intent = intent,
                    entities = entities,
                    confidence = confidence,
                    personality = personality
                )
            }
            
            // Greeting or farewell
            intent == "greet" -> {
                buildGreetingResponse(personality)
            }
            
            intent == "farewell" -> {
                buildFarewellResponse(personality)
            }
            
            // Help
            intent == "help" -> {
                buildHelpResponse(personality)
            }
            
            // Fallback for unknown or low confidence
            else -> {
                buildFallbackResponse(
                    input = input,
                    intent = intent,
                    confidence = confidence,
                    outcomeState = outcomeState,
                    personality = personality
                )
            }
        }
    }
    
    /**
     * Build a template-based response
     */
    private fun buildTemplateResponse(
        input: String,
        intent: String,
        entities: Map<String, Any>,
        confidence: Double,
        personality: PersonalityPreferences,
        conversationContext: com.example.zahra.context.ConversationState
    ): ZahraResponse? {
        // Select appropriate templates for this intent
        val templates = templateSelector.selectTemplates(
            intent = intent,
            entities = entities,
            context = conversationContext,
            personality = personality
        )
        
        if (templates.isEmpty()) {
            return null
        }
        
        // Rank templates
        val rankedTemplates = responseRanker.rankTemplates(
            templates = templates,
            request = input,
            context = conversationContext
        )
        
        // Select the best template
        val bestTemplate = rankedTemplates.firstOrNull() ?: return null
        
        // Fill template slots
        val slots = extractSlotsForTemplate(bestTemplate, entities, conversationContext)
        val expandedText = templateExpander.expandTemplate(
            template = bestTemplate,
            slots = slots,
            context = conversationContext,
            personality = personality
        )
        
        // Build the response
        return ZahraResponse(
            text = expandedText,
            intent = intent,
            confidence = confidence,
            confidenceTier = when {
                confidence >= DIRECT_CONFIDENCE_THRESHOLD -> ConfidenceTier.DIRECT
                confidence >= QUALIFIED_CONFIDENCE_THRESHOLD -> ConfidenceTier.QUALIFIED
                else -> ConfidenceTier.UNCERTAIN
            },
            outcomeState = OutcomeState.SUPPORTED,
            source = "Template: ${bestTemplate.id}",
            evidenceTrace = buildEvidenceTrace(
                input = input,
                intent = intent,
                entities = entities,
                evidenceType = EvidenceType.DEDUCTION,
                confidence = confidence
            )
        )
    }
    
    /**
     * Build a knowledge-based response
     */
    private fun buildKnowledgeResponse(
        input: String,
        intent: String,
        entities: Map<String, Any>,
        confidence: Double,
        personality: PersonalityPreferences
    ): ZahraResponse? {
        // Query the knowledge engine
        val query = entities["query"] as? String ?: input
        val knowledgeResult = knowledgeEngine.query(query)
        
        return when (knowledgeResult.state) {
            com.example.zahra.knowledge.model.KnowledgeRetrievalState.VERIFIED_MATCH -> {
                val best = knowledgeResult.bestMatch ?: return null
                
                // Build response with knowledge content
                val responseText = buildKnowledgeText(
                    knowledgeRecord = best.record,
                    personality = personality
                )
                
                ZahraResponse(
                    text = responseText,
                    intent = intent,
                    confidence = best.relevanceScore,
                    confidenceTier = when {
                        best.relevanceScore >= DIRECT_CONFIDENCE_THRESHOLD -> ConfidenceTier.DIRECT
                        best.relevanceScore >= QUALIFIED_CONFIDENCE_THRESHOLD -> ConfidenceTier.QUALIFIED
                        else -> ConfidenceTier.UNCERTAIN
                    },
                    outcomeState = OutcomeState.SUPPORTED,
                    source = "[${best.record.provenance.sourceTitle} - ${best.record.provenance.sourceLocation}]",
                    toolPayload = ZahraToolPayload.KnowledgePayload(
                        title = best.record.title,
                        category = best.record.category.labelAr,
                        content = best.record.canonicalContent,
                        originalSourceText = best.record.originalSourceText,
                        source = best.record.provenance.sourceTitle,
                        sourceLocation = best.record.provenance.sourceLocation,
                        verificationStatusText = best.record.verificationStatus.labelAr,
                        isVerified = best.record.verificationStatus.isVerified,
                        matchMethodText = best.matchMethod.labelAr,
                        relevancePercent = (best.relevanceScore * 100).toInt(),
                        recordId = best.record.id,
                        warning = best.warning
                    ),
                    evidenceTrace = buildEvidenceTrace(
                        input = input,
                        intent = intent,
                        entities = entities,
                        evidenceType = EvidenceType.LOCAL_CURATED,
                        confidence = best.relevanceScore,
                        source = best.record.provenance.sourceTitle
                    )
                )
            }
            com.example.zahra.knowledge.model.KnowledgeRetrievalState.TRACEABLE_CANDIDATE -> {
                val best = knowledgeResult.bestMatch ?: return null
                
                // Build response but indicate it's from a candidate source
                val responseText = buildKnowledgeText(
                    knowledgeRecord = best.record,
                    personality = personality,
                    isVerified = false
                )
                
                ZahraResponse(
                    text = responseText,
                    intent = intent,
                    confidence = best.relevanceScore * 0.8, // Slightly lower confidence
                    confidenceTier = ConfidenceTier.QUALIFIED,
                    outcomeState = OutcomeState.SUPPORTED,
                    source = "[${best.record.provenance.sourceTitle} - ${best.record.provenance.sourceLocation}]",
                    toolPayload = ZahraToolPayload.KnowledgePayload(
                        title = best.record.title,
                        category = best.record.category.labelAr,
                        content = best.record.canonicalContent,
                        originalSourceText = best.record.originalSourceText,
                        source = best.record.provenance.sourceTitle,
                        sourceLocation = best.record.provenance.sourceLocation,
                        verificationStatusText = best.record.verificationStatus.labelAr,
                        isVerified = false,
                        matchMethodText = best.matchMethod.labelAr,
                        relevancePercent = (best.relevanceScore * 100).toInt(),
                        recordId = best.record.id,
                        warning = "This information is from a traceable candidate source"
                    )
                )
            }
            else -> {
                // No knowledge found - build a response indicating that
                buildNoKnowledgeResponse(input, intent, personality)
            }
        }
    }
    
    /**
     * Build a calculation response
     */
    private fun buildCalculationResponse(
        input: String,
        intent: String,
        entities: Map<String, Any>,
        confidence: Double,
        personality: PersonalityPreferences
    ): ZahraResponse {
        // For now, we'll let the existing MathEngine handle the calculation
        // This method is a placeholder for future enhancement
        
        return ZahraResponse(
            text = "I'll calculate that for you.",
            intent = intent,
            confidence = confidence,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "Calculation pending"
        )
    }
    
    /**
     * Build a greeting response
     */
    private fun buildGreetingResponse(
        personality: PersonalityPreferences
    ): ZahraResponse {
        val greeting = com.example.zahra.personality.StyleApplier.getGreeting(personality)
        
        return ZahraResponse(
            text = greeting,
            intent = "greet",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "Greeting template",
            followups = listOf(
                "How can I help you today?",
                "What would you like to know?",
                "What can I do for you?"
            )
        )
    }
    
    /**
     * Build a farewell response
     */
    private fun buildFarewellResponse(
        personality: PersonalityPreferences
    ): ZahraResponse {
        val farewell = com.example.zahra.personality.StyleApplier.getFarewell(personality)
        
        return ZahraResponse(
            text = farewell,
            intent = "farewell",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "Farewell template"
        )
    }
    
    /**
     * Build a help response
     */
    private fun buildHelpResponse(
        personality: PersonalityPreferences
    ): ZahraResponse {
        val helpText = when (personality.language) {
            com.example.zahra.personality.CommunicationLanguage.MODERN_STANDARD_ARABIC,
            com.example.zahra.personality.CommunicationLanguage.EGYPTIAN_ARABIC -> {
                """
                | أنا زهرة، مساعدك الذكي. يمكنني مساعدتك في:
                | • الإجابة على أسئلتك
                | • حل المسائل الرياضية
                | • ترجمة النصوص
                | • تلخيص المقالات
                | • إدارة المهام والتذكيرات
                | • تقديم معلومات دينية موثوقة
                | 
                | ببساطة أخبرني بما تريد معرفته!
                """.trimMargin()
            }
            else -> {
                """
                | I am Zahra, your intelligent assistant. I can help you with:
                | • Answering your questions
                | • Solving math problems
                | • Translating text
                | • Summarizing articles
                | • Managing tasks and reminders
                | • Providing reliable religious information
                | 
                | Just tell me what you'd like to know!
                """.trimMargin()
            }
        }
        
        return ZahraResponse(
            text = helpText,
            intent = "help",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "Help template"
        )
    }
    
    /**
     * Build a fallback response
     */
    private fun buildFallbackResponse(
        input: String,
        intent: String?,
        confidence: Double,
        outcomeState: OutcomeState,
        personality: PersonalityPreferences
    ): ZahraResponse {
        val fallbackText = when (personality.language) {
            com.example.zahra.personality.CommunicationLanguage.MODERN_STANDARD_ARABIC,
            com.example.zahra.personality.CommunicationLanguage.EGYPTIAN_ARABIC -> {
                """
                | آسفة، لا يمكنني العثور على معلومات كافية للإجابة على سؤالك.
                | قد تكون:
                | • المعلومات غير متاحة في قاعدة معارفي الحالية
                | • السؤال غير واضح بما يكفي
                | • هناك خطأ في فهم السؤال
                | 
                | يمكنك محاولة reformulate سؤالك أو سؤال شيء آخر.
                """.trimMargin()
            }
            else -> {
                """
                | I'm sorry, I couldn't find enough information to answer your question.
                | This might be because:
                | • The information is not available in my current knowledge base
                | • The question is not clear enough
                | • There was an error in understanding your question
                | 
                | You can try rephrasing your question or asking something else.
                """.trimMargin()
            }
        }
        
        return ZahraResponse(
            text = fallbackText,
            intent = intent ?: "unknown",
            confidence = confidence,
            confidenceTier = ConfidenceTier.UNCERTAIN,
            outcomeState = outcomeState,
            source = "Fallback template",
            followups = listOf(
                "Try rephrasing your question",
                "Ask something else",
                "Check the help menu"
            )
        )
    }
    
    /**
     * Build knowledge text based on personality
     */
    private fun buildKnowledgeText(
        knowledgeRecord: com.example.zahra.knowledge.model.KnowledgeRecord,
        personality: PersonalityPreferences,
        isVerified: Boolean = true
    ): String {
        val baseText = knowledgeRecord.canonicalContent
        
        // Add source citation based on reference depth
        val sourceText = when (personality.islamicReferenceDepth) {
            com.example.zahra.personality.IslamicReferenceDepth.MINIMAL -> {
                ""
            }
            com.example.zahra.personality.IslamicReferenceDepth.BALANCED -> {
                "\n\n[${knowledgeRecord.provenance.sourceTitle}]"
            }
            com.example.zahra.personality.IslamicReferenceDepth.DETAILED -> {
                "\n\n[${knowledgeRecord.provenance.sourceTitle} - ${knowledgeRecord.provenance.sourceLocation}]"
            }
            com.example.zahra.personality.IslamicReferenceDepth.SCHOLARLY -> {
                "\n\n[${knowledgeRecord.provenance.sourceTitle} - ${knowledgeRecord.provenance.sourceLocation}]\n" +
                "Source: ${knowledgeRecord.provenance.origin}"
            }
        }
        
        // Add verification warning if not verified
        val warningText = if (!isVerified) {
            when (personality.language) {
                com.example.zahra.personality.CommunicationLanguage.MODERN_STANDARD_ARABIC,
                com.example.zahra.personality.CommunicationLanguage.EGYPTIAN_ARABIC -> {
                    "\n\n⚠️ هذه المعلومات من مصدر قابل للتتبع ولكنها غير مؤكدة"
                }
                else -> {
                    "\n\n⚠️ This information is from a traceable source but is not verified"
                }
            }
        } else {
            ""
        }
        
        return "$baseText$sourceText$warningText"
    }
    
    /**
     * Build no knowledge response
     */
    private fun buildNoKnowledgeResponse(
        input: String,
        intent: String?,
        personality: PersonalityPreferences
    ): ZahraResponse {
        val text = when (personality.language) {
            com.example.zahra.personality.CommunicationLanguage.MODERN_STANDARD_ARABIC,
            com.example.zahra.personality.CommunicationLanguage.EGYPTIAN_ARABIC -> {
                "آسف، لا أملك معلومات كافية حول هذا الموضوع."
            }
            else -> {
                "I'm sorry, I don't have enough information about this topic."
            }
        }
        
        return ZahraResponse(
            text = text,
            intent = intent ?: "unknown",
            confidence = 0.2,
            confidenceTier = ConfidenceTier.UNCERTAIN,
            outcomeState = OutcomeState.UNSUPPORTED,
            source = "No knowledge available",
            followups = listOf(
                "Try a different question",
                "Ask about something else"
            )
        )
    }
    
    /**
     * Extract slots for a template
     */
    private fun extractSlotsForTemplate(
        template: ResponseTemplate,
        entities: Map<String, Any>,
        conversationContext: com.example.zahra.context.ConversationState
    ): Map<String, Any> {
        val slots = mutableMapOf<String, Any>()
        
        for (slotDef in template.slots) {
            when (slotDef.source) {
                SlotSource.ENTITY -> {
                    entities[slotDef.name]?.let { value ->
                        slots[slotDef.name] = value
                    }
                }
                SlotSource.CONTEXT -> {
                    // Try to get from conversation context
                    val contextValue = conversationContext.activeEntities[slotDef.name]?.value
                    contextValue?.let { value ->
                        slots[slotDef.name] = value
                    }
                }
                else -> {
                    // Use default if available
                    slotDef.default?.let { default ->
                        slots[slotDef.name] = default
                    }
                }
            }
        }
        
        return slots
    }
    
    /**
     * Apply personality to a response
     */
    private fun applyPersonalityToResponse(
        response: ZahraResponse,
        personality: PersonalityPreferences
    ): ZahraResponse {
        // Apply style to the text
        val styledText = com.example.zahra.personality.StyleApplier.applyToText(
            response.text,
            personality
        )
        
        return response.copy(
            text = styledText
        )
    }
    
    /**
     * Build evidence trace
     */
    private fun buildEvidenceTrace(
        input: String,
        intent: String?,
        entities: Map<String, Any>,
        evidenceType: EvidenceType,
        confidence: Double,
        source: String? = null
    ): EvidenceGroundingTrace {
        val facts = mutableListOf<GroundedFact>()
        
        // Add intent fact if available
        intent?.let { i ->
            facts.add(GroundedFact(
                claim = "Detected intent: $i",
                evidenceType = EvidenceType.USER_SUPPLIED,
                source = "Intent classifier",
                confidence = confidence
            ))
        }
        
        // Add entity facts
        entities.forEach { (key, value) ->
            facts.add(GroundedFact(
                claim = "$key: $value",
                evidenceType = EvidenceType.USER_SUPPLIED,
                source = "Entity extractor",
                confidence = 0.9
            ))
        }
        
        return EpistemicGroundingPipeline.buildTrace(
            rawRequest = input,
            intent = intent ?: "unknown",
            entities = entities,
            appliedRules = listOf("response_generation"),
            facts = facts,
            uncertaintyNotes = if (confidence < QUALIFIED_CONFIDENCE_THRESHOLD) {
                listOf("Low confidence in response")
            } else {
                emptyList()
            },
            outcomeState = if (confidence >= QUALIFIED_CONFIDENCE_THRESHOLD) {
                OutcomeState.SUPPORTED
            } else {
                OutcomeState.AMBIGUOUS
            },
            sourceDesc = source ?: "ResponseBuilder"
        )
    }
}
