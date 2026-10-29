package com.example.zahra.context

import com.example.zahra.model.Sender
import com.example.zahra.model.ZahraMessage
import com.example.zahra.model.ZahraResponse
import java.util.UUID

/**
 * Conversational Context System
 * 
 * Maintains the state of the current conversation, including:
 * - Conversation history (turns)
 * - Current topic and entities
 * - Unresolved references
 * - Active tasks and goals
 * 
 * This enables multi-turn conversation understanding and follow-up handling.
 */

/**
 * Maximum number of turns to keep in memory
 */
const val DEFAULT_CONVERSATION_LENGTH = 20

/**
 * Represents a single turn in a conversation
 */
data class ConversationTurn(
    /** Unique identifier for this turn */
    val id: String = UUID.randomUUID().toString(),
    
    /** Who spoke (USER or ZAHRA) */
    val speaker: Sender,
    
    /** The raw text that was spoken */
    val text: String,
    
    /** Normalized version of the text */
    val normalizedText: String,
    
    /** Detected intent (if any) */
    val intent: String? = null,
    
    /** Extracted entities */
    val entities: Map<String, Any> = emptyMap(),
    
    /** Timestamp of when this turn occurred */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** The response generated (if speaker was USER) */
    val response: ZahraResponse? = null,
    
    /** Metadata about this turn */
    val metadata: TurnMetadata = TurnMetadata()
) {
    /**
     * Check if this turn contains a question
     */
    fun isQuestion(): Boolean {
        val questionPatterns = listOf("؟", "?", "what", "how", "why", "when", "where", "who", "which")
        return questionPatterns.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * Check if this turn is a follow-up to a previous turn
     */
    fun isFollowUp(): Boolean {
        val followUpPatterns = listOf(
            "and", "also", "then", "next", "after that", "additionally",
            "\u0648", "\u0627\u0644\u062c\u0644", "\u0645\u0627\u0630\u0627", "\u0648\u0645\u0627\u0630\u0627",
            "then", "after that", "next"
        )
        return followUpPatterns.any { 
            normalizedText.startsWith(it, ignoreCase = true) || 
            normalizedText.contains(" $it ", ignoreCase = true)
        }
    }
    
    /**
     * Get the main topic or subject of this turn
     */
    fun getTopic(): String? {
        // Extract topic from entities or intent
        return entities["topic"] as? String
            ?: entities["subject"] as? String
            ?: intent
    }
}

/**
 * Metadata about a conversation turn
 */
data class TurnMetadata(
    /** Whether this turn was part of a multi-step plan */
    val isPartOfPlan: Boolean = false,
    
    /** The plan ID if this is part of a plan */
    val planId: String? = null,
    
    /** Step number in the plan */
    val stepNumber: Int? = null,
    
    /** Whether this turn contained a correction */
    val isCorrection: Boolean = false,
    
    /** Whether this turn was a clarification request */
    val isClarification: Boolean = false,
    
    /** Confidence score for this turn's processing */
    val confidence: Double? = null
)

/**
 * Represents the current conversation state
 */
data class ConversationState(
    /** Unique identifier for this conversation */
    val conversationId: String = UUID.randomUUID().toString(),
    
    /** List of turns in this conversation (oldest first) */
    val turns: MutableList<ConversationTurn> = mutableListOf(),
    
    /** Current topic or main subject */
    var currentTopic: String? = null,
    
    /** Active entities being discussed */
    val activeEntities: MutableMap<String, EntityInfo> = mutableMapOf(),
    
    /** Unresolved references (pronouns, etc.) */
    val unresolvedReferences: MutableList<UnresolvedReference> = mutableListOf(),
    
    /** Metadata about the conversation */
    val metadata: ConversationMetadata = ConversationMetadata(),
    
    /** Maximum number of turns to keep */
    val maxTurns: Int = DEFAULT_CONVERSATION_LENGTH
) {
    /**
     * Add a new turn to the conversation
     */
    fun addTurn(turn: ConversationTurn) {
        turns.add(turn)
        
        // Trim if we exceed max turns
        while (turns.size > maxTurns) {
            val removed = turns.removeAt(0)
            // Clean up references to removed turn
            unresolvedReferences.removeAll { it.referenceTurnId == removed.id }
        }
        
        // Update current topic
        turn.getTopic()?.let { topic ->
            currentTopic = topic
        }
        
        // Update metadata
        metadata.lastActivityTime = System.currentTimeMillis()
        if (turn.speaker == Sender.USER) {
            metadata.userTurnCount++
        } else {
            metadata.zahraTurnCount++
        }
    }
    
    /**
     * Get the most recent turn
     */
    fun getLastTurn(): ConversationTurn? {
        return turns.lastOrNull()
    }
    
    /**
     * Get the previous turn (before the last one)
     */
    fun getPreviousTurn(): ConversationTurn? {
        return if (turns.size >= 2) turns[turns.size - 2] else null
    }
    
    /**
     * Get turns from the user
     */
    fun getUserTurns(): List<ConversationTurn> {
        return turns.filter { it.speaker == Sender.USER }
    }
    
    /**
     * Get turns from Zahra
     */
    fun getZahraTurns(): List<ConversationTurn> {
        return turns.filter { it.speaker == Sender.ZAHRA }
    }
    
    /**
     * Get all turns related to a specific topic
     */
    fun getTurnsByTopic(topic: String): List<ConversationTurn> {
        return turns.filter { it.getTopic() == topic }
    }
    
    /**
     * Get all turns that are questions
     */
    fun getQuestionTurns(): List<ConversationTurn> {
        return turns.filter { it.isQuestion() }
    }
    
    /**
     * Get all unresolved questions
     */
    fun getUnresolvedQuestions(): List<ConversationTurn> {
        return getQuestionTurns().filter { turn ->
            // A question is unresolved if there's no follow-up answer
            val index = turns.indexOf(turn)
            val nextTurns = turns.subList(index + 1, turns.size)
            nextTurns.none { it.response?.intent == "answer_question" }
        }
    }
    
    /**
     * Clear the conversation
     */
    fun clear() {
        turns.clear()
        currentTopic = null
        activeEntities.clear()
        unresolvedReferences.clear()
        metadata.reset()
    }
    
    /**
     * Get a summary of the conversation
     */
    fun getSummary(): ConversationSummary {
        val topics = turns
            .mapNotNull { it.getTopic() }
            .distinct()
            .take(5)
        
        val keyPoints = turns
            .filter { it.speaker == Sender.ZAHRA && it.response?.outcomeState == com.example.zahra.model.OutcomeState.SUPPORTED }
            .mapNotNull { it.response?.text?.take(100) }
            .take(5)
        
        val unresolved = unresolvedReferences
            .map { it.referenceText }
            .distinct()
            .take(3)
        
        val actionItems = turns
            .filter { it.intent in listOf("create_task", "set_reminder", "create_schedule") }
            .mapNotNull { it.intent }
            .take(3)
        
        return ConversationSummary(
            conversationId = conversationId,
            turnCount = turns.size,
            topics = topics,
            keyPoints = keyPoints,
            unresolvedItems = unresolved,
            actionItems = actionItems,
            startTime = metadata.startTime,
            lastActivityTime = metadata.lastActivityTime
        )
    }
}

/**
 * Metadata about a conversation
 */
data class ConversationMetadata(
    /** When the conversation started */
    val startTime: Long = System.currentTimeMillis(),
    
    /** When the conversation was last active */
    var lastActivityTime: Long = System.currentTimeMillis(),
    
    /** Number of user turns */
    var userTurnCount: Int = 0,
    
    /** Number of Zahra turns */
    var zahraTurnCount: Int = 0,
    
    /** Active tasks in this conversation */
    val activeTasks: MutableList<String> = mutableListOf(),
    
    /** Active schedules in this conversation */
    val activeSchedules: MutableList<String> = mutableListOf()
) {
    fun reset() {
        userTurnCount = 0
        zahraTurnCount = 0
        activeTasks.clear()
        activeSchedules.clear()
    }
}

/**
 * Summary of a conversation for display
 */
data class ConversationSummary(
    val conversationId: String,
    val turnCount: Int,
    val topics: List<String>,
    val keyPoints: List<String>,
    val unresolvedItems: List<String>,
    val actionItems: List<String>,
    val startTime: Long,
    val lastActivityTime: Long
)

/**
 * Information about an entity in the conversation
 */
data class EntityInfo(
    /** The entity value */
    val value: Any,
    
    /** The type of entity */
    val type: String,
    
    /** When this entity was introduced */
    val introducedAt: Long = System.currentTimeMillis(),
    
    /** The turn ID where this entity was introduced */
    val introducedInTurn: String,
    
    /** How many times this entity has been referenced */
    var referenceCount: Int = 1,
    
    /** Additional metadata */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents an unresolved reference (pronoun, etc.)
 */
data class UnresolvedReference(
    /** The reference text (e.g., "it", "that", "هذا") */
    val referenceText: String,
    
    /** The turn ID where this reference was made */
    val referenceTurnId: String,
    
    /** The position in the text */
    val position: Int,
    
    /** Possible entities this might refer to */
    val possibleReferents: List<String> = emptyList(),
    
    /** Confidence in the reference resolution */
    val confidence: Double? = null
)

/**
 * Manager for conversation context
 */
class ContextManager {
    
    // Current conversation state
    private var currentConversation: ConversationState = ConversationState()
    
    // Conversation history (for accessing previous conversations)
    private val conversationHistory: MutableList<ConversationState> = mutableListOf()
    
    // Maximum number of conversations to keep in history
    private val maxHistorySize: Int = 10

    // المرحلة 4: عند الربط، لا يحتفظ هذا المدير بسجل مستقل؛ ينسخ الدورات
    // المسلَّمة من المصدر الوحيد (`DialogueState`).
    private var dialogueProvider: (() -> com.example.zahra.dialogue.DialogueHistorySource?)? = null

    /**
     * يربط المدير بالمصدر الوحيد. في الوضع `PROJECTED` يعيد المزوّد مصدرًا،
     * وفي وضع الرجوع `MIRRORED` يعيد `null` فيبقى السجل المحلي كما كان.
     */
    fun bindDialogueSource(provider: (() -> com.example.zahra.dialogue.DialogueHistorySource?)?) {
        dialogueProvider = provider
        if (provider?.invoke() != null) currentConversation = ConversationState()
    }

    /**true حين يقرأ هذا المدير من مصدر الحالة الوحيد. */
    fun isProjected(): Boolean = dialogueProvider?.invoke() != null

    /**
     * يعيد بناء المحادثة الحالية من السجل المسلَّم. لا يُخترع دور غير موجود،
     * ولا تُحفظ نسخة ثانية قابلة للانحراف.
     * @return عدد الدورات المنسوخة (0 في وضع MIRRORED).
     */
    fun syncFromDialogue(): Int {
        val source = dialogueProvider?.invoke() ?: return 0
        val rebuilt = ConversationState()
        source.turns().forEach { turn ->
            rebuilt.addTurn(
                ConversationTurn(
                    speaker = Sender.USER,
                    text = turn.user,
                    normalizedText = turn.user,
                    intent = turn.intent.takeIf { it.isNotBlank() }
                )
            )
            rebuilt.addTurn(
                ConversationTurn(
                    speaker = Sender.ZAHRA,
                    text = turn.response,
                    normalizedText = turn.response,
                    intent = turn.intent.takeIf { it.isNotBlank() }
                )
            )
        }
        currentConversation = rebuilt
        return source.turnCount()
    }
    
    /**
     * Get the current conversation
     */
    fun getCurrentConversation(): ConversationState {
        return currentConversation
    }
    
    /**
     * Record a new turn in the conversation
     */
    fun recordTurn(
        text: String,
        normalizedText: String,
        speaker: Sender,
        intent: String? = null,
        entities: Map<String, Any> = emptyMap(),
        response: ZahraResponse? = null
    ): ConversationTurn {
        val turn = ConversationTurn(
            speaker = speaker,
            text = text,
            normalizedText = normalizedText,
            intent = intent,
            entities = entities,
            response = response
        )

        // في الوضع المُسقَط، التسجيل مرة واحدة في DialogueState ثم syncFromDialogue().
        if (isProjected()) return turn
        
        currentConversation.addTurn(turn)
        
        // Update active entities
        updateActiveEntities(turn)
        
        return turn
    }
    
    /**
     * Start a new conversation
     */
    fun startNewConversation(): ConversationState {
        // Save current conversation to history
        if (currentConversation.turns.isNotEmpty()) {
            conversationHistory.add(currentConversation)
            
            // Trim history if needed
            while (conversationHistory.size > maxHistorySize) {
                conversationHistory.removeAt(0)
            }
        }
        
        // Start fresh
        currentConversation = ConversationState()
        return currentConversation
    }
    
    /**
     * Switch to a specific conversation
     */
    fun switchToConversation(conversationId: String): ConversationState? {
        val conversation = conversationHistory.find { it.conversationId == conversationId }
        if (conversation != null) {
            // Save current to history
            if (currentConversation.turns.isNotEmpty()) {
                conversationHistory.add(currentConversation)
            }
            
            // Remove from history
            conversationHistory.remove(conversation)
            
            // Set as current
            currentConversation = conversation
            return conversation
        }
        return null
    }
    
    /**
     * Get conversation history
     */
    fun getConversationHistory(): List<ConversationSummary> {
        return conversationHistory.map { it.getSummary() }
    }
    
    /**
     * Clear the current conversation
     */
    fun clearCurrentConversation() {
        currentConversation.clear()
    }
    
    /**
     * Clear all conversation history
     */
    fun clearAllConversations() {
        currentConversation.clear()
        conversationHistory.clear()
    }
    
    /**
     * Update active entities based on a new turn
     */
    private fun updateActiveEntities(turn: ConversationTurn) {
        // Add new entities from this turn
        for ((key, value) in turn.entities) {
            if (!currentConversation.activeEntities.containsKey(key)) {
                currentConversation.activeEntities[key] = EntityInfo(
                    value = value,
                    type = inferEntityType(key, value),
                    introducedInTurn = turn.id
                )
            } else {
                // Update reference count
                currentConversation.activeEntities[key]?.let { entityInfo ->
                    entityInfo.referenceCount++
                }
            }
        }
        
        // Remove entities that haven't been referenced in a while
        val recentTurns = currentConversation.turns.takeLast(10)
        val referencedEntityIds = recentTurns.flatMap { it.entities.keys }.toSet()
        
        currentConversation.activeEntities.entries.removeAll { (key, _) ->
            key !in referencedEntityIds && !recentTurns.any { turn ->
                turn.entities.containsKey(key)
            }
        }
    }
    
    /**
     * Infer the type of an entity based on its key and value
     */
    private fun inferEntityType(key: String, value: Any): String {
        return when {
            key.contains("number", ignoreCase = true) -> "NUMBER"
            key.contains("date", ignoreCase = true) -> "DATE"
            key.contains("time", ignoreCase = true) -> "TIME"
            key.contains("person", ignoreCase = true) -> "PERSON"
            key.contains("location", ignoreCase = true) -> "LOCATION"
            key.contains("topic", ignoreCase = true) -> "TOPIC"
            key.contains("intent", ignoreCase = true) -> "INTENT"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Resolve a reference to an entity
     */
    fun resolveReference(referenceText: String): EntityInfo? {
        // Try to find the most recently mentioned entity that matches
        val normalizedRef = referenceText.lowercase()
        
        // Check active entities
        for ((key, entity) in currentConversation.activeEntities) {
            val entityString = entity.value.toString().lowercase()
            if (entityString.contains(normalizedRef) || 
                key.lowercase().contains(normalizedRef)) {
                return entity
            }
        }
        
        // Check recent turns for entities
        val recentTurns = currentConversation.turns.takeLast(5)
        for (turn in recentTurns) {
            for ((key, value) in turn.entities) {
                val valueString = value.toString().lowercase()
                if (valueString.contains(normalizedRef) || 
                    key.lowercase().contains(normalizedRef)) {
                    return EntityInfo(
                        value = value,
                        type = inferEntityType(key, value),
                        introducedInTurn = turn.id
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * Get the current topic of conversation
     */
    fun getCurrentTopic(): String? {
        return currentConversation.currentTopic
    }
    
    /**
     * Set the current topic
     */
    fun setCurrentTopic(topic: String) {
        currentConversation.currentTopic = topic
    }
    
    /**
     * Get entities related to the current topic
     */
    fun getTopicEntities(topic: String): List<EntityInfo> {
        return currentConversation.activeEntities.values
            .filter { it.metadata["topic"] == topic }
            .toList()
    }
    
    /**
     * Check if the user is asking a follow-up question
     */
    fun isFollowUpQuestion(text: String): Boolean {
        val normalized = text.lowercase()
        
        // Check for follow-up indicators
        val followUpIndicators = listOf(
            "and", "also", "then", "next", "after that", "additionally",
            "what about", "tell me more", "explain more", "how about",
            "\u0648", "\u0627\u0644\u062c\u0644", "\u0645\u0627\u0630\u0627", "\u0648\u0645\u0627\u0630\u0627",
            "\u0645\u0627 \u0628\u0627\u0644\u0646\u0633\u0628\u0629", "\u0623\u062d\u0628\u0631\u0646\u064a",
            "\u0627\u0636\u0627\u0641\u0644", "\u0627\u0633\u062a\u0645\u0644"
        )
        
        return followUpIndicators.any { indicator ->
            normalized.startsWith(indicator) || 
            normalized.contains(" $indicator ")
        }
    }
    
    /**
     * Get the most likely referent for a pronoun
     */
    fun resolvePronoun(pronoun: String): EntityInfo? {
        // Map pronouns to likely referents
        val pronounToReferent = mapOf(
            "it" to "last_mentioned",
            "this" to "last_mentioned",
            "that" to "previously_mentioned",
            "he" to "last_person",
            "she" to "last_person",
            "they" to "last_plural",
            "\u0627\u0644\u0646\u0627\u062a\u062c" to "last_mentioned", // "that" in Arabic
            "\u0627\u0644\u062c\u0644" to "last_mentioned", // "this" in Arabic
            "\u0627\u0644\u062d\u0644" to "last_person", // "he" in Arabic
            "\u0647\u0644" to "last_person", // "she" in Arabic
            "\u0647\u0645" to "last_plural" // "they" in Arabic
        )
        
        val referentType = pronounToReferent[pronoun.lowercase()]
        
        return when (referentType) {
            "last_mentioned" -> {
                currentConversation.turns.lastOrNull()?.entities?.entries?.lastOrNull()?.let { (key, value) ->
                    EntityInfo(
                        value = value,
                        type = inferEntityType(key, value),
                        introducedInTurn = currentConversation.turns.last().id
                    )
                }
            }
            "previously_mentioned" -> {
                if (currentConversation.turns.size >= 2) {
                    currentConversation.turns[currentConversation.turns.size - 2].entities.entries.lastOrNull()?.let { (key, value) ->
                        EntityInfo(
                            value = value,
                            type = inferEntityType(key, value),
                            introducedInTurn = currentConversation.turns[currentConversation.turns.size - 2].id
                        )
                    }
                } else null
            }
            "last_person" -> {
                currentConversation.activeEntities.values
                    .lastOrNull { it.type == "PERSON" }
            }
            "last_plural" -> {
                currentConversation.activeEntities.values
                    .lastOrNull { 
                        it.value.toString().contains(" ") ||
                        it.value.toString().endsWith("s") ||
                        it.value.toString().endsWith("\u0646") || // Arabic plural
                        it.value.toString().endsWith("\u0648\u0646")
                    }
            }
            else -> null
        }
    }
}
