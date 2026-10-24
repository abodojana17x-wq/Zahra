package com.example.zahra.response

import com.example.zahra.context.ConversationState
import com.example.zahra.personality.PersonalityPreferences

/**
 * Template Selector
 * 
 * Selects appropriate response templates based on:
 * - User intent
 * - Extracted entities
 * - Conversation context
 * - Personality preferences
 */
class TemplateSelector {
    
    /**
     * Select templates for a given request
     * 
     * @param intent The detected intent
     * @param entities Extracted entities
     * @param context Current conversation context
     * @param personality Current personality preferences
     * @return List of candidate templates, ordered by suitability
     */
    fun selectTemplates(
        intent: String,
        entities: Map<String, Any>,
        context: ConversationState,
        personality: PersonalityPreferences
    ): List<ResponseTemplate> {
        // Get templates for this intent
        val intentTemplates = getTemplatesForIntent(intent, personality)
        
        // Filter based on context
        val contextFiltered = filterByContext(intentTemplates, context, personality)
        
        // Filter based on entities
        val entityFiltered = filterByEntities(contextFiltered, entities, personality)
        
        // Sort by priority
        return entityFiltered.sortedByDescending { it.priority }
    }
    
    /**
     * Get templates for a specific intent
     */
    private fun getTemplatesForIntent(
        intent: String,
        personality: PersonalityPreferences
    ): List<ResponseTemplate> {
        return when (intent) {
            "greet" -> listOf(
                ResponseTemplate(
                    id = "greet_formal",
                    template = "Hello! How can I assist you today?",
                    intent = "greet",
                    priority = 10,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.FORMAL
                ),
                ResponseTemplate(
                    id = "greet_friendly",
                    template = "Hey there! What's up?",
                    intent = "greet",
                    priority = 8,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.CASUAL
                ),
                ResponseTemplate(
                    id = "greet_islamic",
                    template = "As-salamu alaykum! How may I help you?",
                    intent = "greet",
                    priority = 9,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.ISLAMIC
                )
            )
            
            "farewell" -> listOf(
                ResponseTemplate(
                    id = "farewell_formal",
                    template = "Goodbye! Have a great day.",
                    intent = "farewell",
                    priority = 10,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.FORMAL
                ),
                ResponseTemplate(
                    id = "farewell_friendly",
                    template = "See you later! Take care!",
                    intent = "farewell",
                    priority = 8,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.CASUAL
                ),
                ResponseTemplate(
                    id = "farewell_islamic",
                    template = "Ma'a salama! May Allah bless you.",
                    intent = "farewell",
                    priority = 9,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.ISLAMIC
                )
            )
            
            "thank" -> listOf(
                ResponseTemplate(
                    id = "thank_response_formal",
                    template = "You're welcome! I'm happy to help.",
                    intent = "thank",
                    priority = 10,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.FORMAL
                ),
                ResponseTemplate(
                    id = "thank_response_friendly",
                    template = "No problem! Happy to assist!",
                    intent = "thank",
                    priority = 8,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.CASUAL
                ),
                ResponseTemplate(
                    id = "thank_response_islamic",
                    template = "You're welcome! May Allah reward you for your gratitude.",
                    intent = "thank",
                    priority = 9,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.ISLAMIC
                )
            )
            
            "help" -> listOf(
                ResponseTemplate(
                    id = "help_general",
                    template = "I can help you with various tasks like scheduling, reminders, calculations, translations, and answering questions. What do you need help with?",
                    intent = "help",
                    priority = 10,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.FORMAL
                ),
                ResponseTemplate(
                    id = "help_concise",
                    template = "I can help with schedules, tasks, math, translations, and questions. Ask me anything!",
                    intent = "help",
                    priority = 8,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.CONCISE
                )
            )
            
            "unknown" -> listOf(
                ResponseTemplate(
                    id = "unknown_general",
                    template = "I'm not sure I understand. Could you please rephrase or ask a different question?",
                    intent = "unknown",
                    priority = 10,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.FORMAL
                ),
                ResponseTemplate(
                    id = "unknown_friendly",
                    template = "I'm not sure what you mean. Could you try saying it differently?",
                    intent = "unknown",
                    priority = 8,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.CASUAL
                )
            )
            
            else -> listOf(
                ResponseTemplate(
                    id = "generic_response",
                    template = "I understand. Let me help you with that.",
                    intent = intent,
                    priority = 5,
                    requiredEntities = emptyList(),
                    style = ResponseStyle.NEUTRAL
                )
            )
        }.filter { template ->
            // Filter by personality style
            matchesPersonalityStyle(template, personality)
        }
    }
    
    /**
     * Filter templates by conversation context
     */
    private fun filterByContext(
        templates: List<ResponseTemplate>,
        context: ConversationState,
        personality: PersonalityPreferences
    ): List<ResponseTemplate> {
        // If we have a current topic, prefer templates that reference it
        context.currentTopic?.let { topic ->
            val topicTemplates = templates.filter { 
                it.template.contains(topic, ignoreCase = true) 
            }
            if (topicTemplates.isNotEmpty()) {
                return topicTemplates
            }
        }
        
        // If conversation has many turns, prefer more concise templates
        if (context.turns.size > 10 && personality.elaborationLevel == com.example.zahra.personality.ElaborationLevel.CONCISE) {
            return templates.filter { 
                it.style == ResponseStyle.CONCISE || it.style == ResponseStyle.NEUTRAL
            }.ifEmpty { templates }
        }
        
        return templates
    }
    
    /**
     * Filter templates by entities
     */
    private fun filterByEntities(
        templates: List<ResponseTemplate>,
        entities: Map<String, Any>,
        personality: PersonalityPreferences
    ): List<ResponseTemplate> {
        // Check if templates require specific entities
        val entityKeys = entities.keys
        
        return templates.filter { template ->
            // If template requires entities, make sure we have them
            if (template.requiredEntities.isNotEmpty()) {
                template.requiredEntities.all { entityKeys.contains(it) }
            } else {
                true
            }
        }
    }
    
    /**
     * Check if template matches personality style
     */
    private fun matchesPersonalityStyle(
        template: ResponseTemplate,
        personality: PersonalityPreferences
    ): Boolean {
        // Map personality style to template style
        val personalityStyle = when (personality.communicationStyle) {
            com.example.zahra.personality.CommunicationStyle.CONCISE -> ResponseStyle.CONCISE
            com.example.zahra.personality.CommunicationStyle.DETAILED -> ResponseStyle.DETAILED
            com.example.zahra.personality.CommunicationStyle.DEEP_EDUCATIONAL -> ResponseStyle.EDUCATIONAL
            com.example.zahra.personality.CommunicationStyle.STEP_BY_STEP -> ResponseStyle.EDUCATIONAL
            com.example.zahra.personality.CommunicationStyle.SOCRATIC -> ResponseStyle.QUESTIONING
            else -> ResponseStyle.NEUTRAL
        }
        
        // For now, accept all templates that match or are neutral
        return template.style == personalityStyle || 
               template.style == ResponseStyle.NEUTRAL ||
               template.style == ResponseStyle.FORMAL
    }
}

/**
 * Response template data class
 */
data class ResponseTemplate(
    /** Unique identifier for this template */
    val id: String,
    
    /** The template text (may contain slots like {name}, {topic}, etc.) */
    val template: String,
    
    /** The intent this template is for */
    val intent: String,
    
    /** Priority (higher = more preferred) */
    val priority: Int = 5,
    
    /** Required entities for this template */
    val requiredEntities: List<String> = emptyList(),
    
    /** Style of this template */
    val style: ResponseStyle = ResponseStyle.NEUTRAL,
    
    /** Minimum personality formality level */
    val minFormality: Int = 0,
    
    /** Maximum personality formality level */
    val maxFormality: Int = 10
)

/**
 * Response style enum
 */
enum class ResponseStyle {
    FORMAL,
    CASUAL,
    NEUTRAL,
    ISLAMIC,
    EDUCATIONAL,
    DETAILED,
    CONCISE,
    QUESTIONING
}
