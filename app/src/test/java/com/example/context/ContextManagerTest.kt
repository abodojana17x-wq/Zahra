package com.example.context

import com.example.zahra.model.Sender
import com.example.zahra.model.ZahraResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the Context Manager
 */
class ContextManagerTest {
    
    private lateinit var contextManager: ContextManager
    
    @Before
    fun setUp() {
        contextManager = ContextManager()
    }
    
    // ==================== ConversationState Tests ====================
    
    @Test
    fun testAddTurn() {
        val turn = ConversationTurn(
            speaker = Sender.USER,
            text = "Hello",
            normalizedText = "hello",
            intent = "greet"
        )
        
        contextManager.recordTurn(
            text = "Hello",
            normalizedText = "hello",
            speaker = Sender.USER,
            intent = "greet"
        )
        
        val conversation = contextManager.getCurrentConversation()
        assertEquals(1, conversation.turns.size)
        assertEquals("Hello", conversation.turns[0].text)
    }
    
    @Test
    fun testAddMultipleTurns() {
        contextManager.recordTurn(
            text = "Hello",
            normalizedText = "hello",
            speaker = Sender.USER,
            intent = "greet"
        )
        
        contextManager.recordTurn(
            text = "Hi there!",
            normalizedText = "hi there",
            speaker = Sender.ZAHRA,
            intent = "greet"
        )
        
        val conversation = contextManager.getCurrentConversation()
        assertEquals(2, conversation.turns.size)
    }
    
    @Test
    fun testMaxTurnsLimit() {
        // Add more turns than the default max
        for (i in 0..25) {
            contextManager.recordTurn(
                text = "Turn $i",
                normalizedText = "turn $i",
                speaker = if (i % 2 == 0) Sender.USER else Sender.ZAHRA,
                intent = "test"
            )
        }
        
        val conversation = contextManager.getCurrentConversation()
        // Should be capped at DEFAULT_CONVERSATION_LENGTH (20)
        assertTrue(conversation.turns.size <= DEFAULT_CONVERSATION_LENGTH)
    }
    
    @Test
    fun testGetLastTurn() {
        contextManager.recordTurn(
            text = "First",
            normalizedText = "first",
            speaker = Sender.USER
        )
        
        contextManager.recordTurn(
            text = "Second",
            normalizedText = "second",
            speaker = Sender.ZAHRA
        )
        
        val lastTurn = contextManager.getCurrentConversation().getLastTurn()
        assertNotNull(lastTurn)
        assertEquals("Second", lastTurn?.text)
    }
    
    @Test
    fun testGetPreviousTurn() {
        contextManager.recordTurn(
            text = "First",
            normalizedText = "first",
            speaker = Sender.USER
        )
        
        contextManager.recordTurn(
            text = "Second",
            normalizedText = "second",
            speaker = Sender.ZAHRA
        )
        
        val previousTurn = contextManager.getCurrentConversation().getPreviousTurn()
        assertNotNull(previousTurn)
        assertEquals("First", previousTurn?.text)
    }
    
    @Test
    fun testGetUserTurns() {
        contextManager.recordTurn(
            text = "User 1",
            normalizedText = "user 1",
            speaker = Sender.USER
        )
        
        contextManager.recordTurn(
            text = "Zahra 1",
            normalizedText = "zahra 1",
            speaker = Sender.ZAHRA
        )
        
        contextManager.recordTurn(
            text = "User 2",
            normalizedText = "user 2",
            speaker = Sender.USER
        )
        
        val userTurns = contextManager.getCurrentConversation().getUserTurns()
        assertEquals(2, userTurns.size)
        assertTrue(userTurns.all { it.speaker == Sender.USER })
    }
    
    @Test
    fun testGetZahraTurns() {
        contextManager.recordTurn(
            text = "User 1",
            normalizedText = "user 1",
            speaker = Sender.USER
        )
        
        contextManager.recordTurn(
            text = "Zahra 1",
            normalizedText = "zahra 1",
            speaker = Sender.ZAHRA
        )
        
        contextManager.recordTurn(
            text = "User 2",
            normalizedText = "user 2",
            speaker = Sender.USER
        )
        
        val zahraTurns = contextManager.getCurrentConversation().getZahraTurns()
        assertEquals(1, zahraTurns.size)
        assertTrue(zahraTurns.all { it.speaker == Sender.ZAHRA })
    }
    
    @Test
    fun testClearConversation() {
        contextManager.recordTurn(
            text = "Test",
            normalizedText = "test",
            speaker = Sender.USER
        )
        
        contextManager.clearCurrentConversation()
        
        val conversation = contextManager.getCurrentConversation()
        assertEquals(0, conversation.turns.size)
    }
    
    // ==================== ConversationTurn Tests ====================
    
    @Test
    fun testIsQuestion() {
        val questionTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "What is the capital of Egypt?",
            normalizedText = "what is the capital of egypt"
        )
        
        assertTrue(questionTurn.isQuestion())
        
        val statementTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "The capital of Egypt is Cairo.",
            normalizedText = "the capital of egypt is cairo"
        )
        
        assertFalse(statementTurn.isQuestion())
    }
    
    @Test
    fun testIsQuestion_arabic() {
        val questionTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "ما هي عاصمة مصر؟",
            normalizedText = "ما هي عاصمة مصر"
        )
        
        assertTrue(questionTurn.isQuestion())
    }
    
    @Test
    fun testIsFollowUp() {
        val followUpTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "and what about France?",
            normalizedText = "and what about france"
        )
        
        assertTrue(followUpTurn.isFollowUp())
        
        val regularTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "What is the capital of Egypt?",
            normalizedText = "what is the capital of egypt"
        )
        
        assertFalse(regularTurn.isFollowUp())
    }
    
    @Test
    fun testIsFollowUp_arabic() {
        val followUpTurn = ConversationTurn(
            speaker = Sender.USER,
            text = "وماذا عن فرنسا؟",
            normalizedText = "وماذا عن فرنسا"
        )
        
        assertTrue(followUpTurn.isFollowUp())
    }
    
    @Test
    fun testGetTopic() {
        val turnWithTopic = ConversationTurn(
            speaker = Sender.USER,
            text = "Tell me about Egypt",
            normalizedText = "tell me about egypt",
            entities = mapOf("topic" to "Egypt")
        )
        
        assertEquals("Egypt", turnWithTopic.getTopic())
        
        val turnWithoutTopic = ConversationTurn(
            speaker = Sender.USER,
            text = "Hello",
            normalizedText = "hello"
        )
        
        assertNull(turnWithoutTopic.getTopic())
    }
    
    // ==================== ContextManager Tests ====================
    
    @Test
    fun testGetCurrentTopic() {
        assertNull(contextManager.getCurrentTopic())
        
        contextManager.setCurrentTopic("Egypt")
        assertEquals("Egypt", contextManager.getCurrentTopic())
    }
    
    @Test
    fun testStartNewConversation() {
        contextManager.recordTurn(
            text = "First conversation",
            normalizedText = "first conversation",
            speaker = Sender.USER
        )
        
        val oldConversation = contextManager.getCurrentConversation()
        assertEquals(1, oldConversation.turns.size)
        
        contextManager.startNewConversation()
        
        val newConversation = contextManager.getCurrentConversation()
        assertEquals(0, newConversation.turns.size)
    }
    
    @Test
    fun testConversationSummary() {
        contextManager.recordTurn(
            text = "What is the capital of Egypt?",
            normalizedText = "what is the capital of egypt",
            speaker = Sender.USER,
            intent = "ask_knowledge",
            entities = mapOf("topic" to "Egypt", "question" to "capital")
        )
        
        contextManager.recordTurn(
            text = "The capital of Egypt is Cairo.",
            normalizedText = "the capital of egypt is cairo",
            speaker = Sender.ZAHRA,
            intent = "answer_knowledge",
            response = ZahraResponse(
                text = "The capital of Egypt is Cairo.",
                intent = "answer_knowledge"
            )
        )
        
        val summary = contextManager.getCurrentConversation().getSummary()
        
        assertNotNull(summary)
        assertTrue(summary.turnCount >= 2)
        assertTrue(summary.topics.isNotEmpty())
    }
    
    @Test
    fun testIsFollowUpQuestion() {
        contextManager.recordTurn(
            text = "What is the capital of Egypt?",
            normalizedText = "what is the capital of egypt",
            speaker = Sender.USER
        )
        
        assertTrue(contextManager.isFollowUpQuestion("and what about France?"))
        assertTrue(contextManager.isFollowUpQuestion("Also, tell me about..."))
        assertTrue(contextManager.isFollowUpQuestion("وماذا عن..."))
        
        assertFalse(contextManager.isFollowUpQuestion("What is the capital of Egypt?"))
    }
    
    // ==================== Entity Tracking Tests ====================
    
    @Test
    fun testActiveEntitiesTracking() {
        contextManager.recordTurn(
            text = "Tell me about Egypt",
            normalizedText = "tell me about egypt",
            speaker = Sender.USER,
            entities = mapOf("topic" to "Egypt", "type" to "country")
        )
        
        val conversation = contextManager.getCurrentConversation()
        assertEquals(2, conversation.activeEntities.size)
        assertTrue(conversation.activeEntities.containsKey("topic"))
        assertTrue(conversation.activeEntities.containsKey("type"))
    }
    
    @Test
    fun testEntityReferenceCount() {
        contextManager.recordTurn(
            text = "Tell me about Egypt",
            normalizedText = "tell me about egypt",
            speaker = Sender.USER,
            entities = mapOf("topic" to "Egypt")
        )
        
        contextManager.recordTurn(
            text = "What about Egypt's capital?",
            normalizedText = "what about egypt's capital",
            speaker = Sender.USER,
            entities = mapOf("topic" to "Egypt")
        )
        
        val conversation = contextManager.getCurrentConversation()
        val egyptEntity = conversation.activeEntities["topic"]
        
        assertNotNull(egyptEntity)
        assertTrue(egyptEntity!!.referenceCount >= 2)
    }
    
    // ==================== Reference Resolution Tests ====================
    
    @Test
    fun testResolveReference() {
        contextManager.recordTurn(
            text = "Egypt is a country in Africa",
            normalizedText = "egypt is a country in africa",
            speaker = Sender.USER,
            entities = mapOf("subject" to "Egypt", "type" to "country")
        )
        
        val resolved = contextManager.resolveReference("it")
        assertNotNull(resolved)
        assertEquals("Egypt", resolved?.value)
    }
    
    @Test
    fun testResolvePronoun_he() {
        contextManager.recordTurn(
            text = "Ahmed is a student",
            normalizedText = "ahmed is a student",
            speaker = Sender.USER,
            entities = mapOf("person" to "Ahmed", "role" to "student")
        )
        
        val resolved = contextManager.resolvePronoun("he")
        assertNotNull(resolved)
        assertEquals("Ahmed", resolved?.value)
    }
    
    @Test
    fun testResolvePronoun_this() {
        contextManager.recordTurn(
            text = "This is a book",
            normalizedText = "this is a book",
            speaker = Sender.USER,
            entities = mapOf("object" to "book")
        )
        
        val resolved = contextManager.resolvePronoun("this")
        assertNotNull(resolved)
        assertEquals("book", resolved?.value)
    }
    
    @Test
    fun testResolvePronoun_arabic() {
        contextManager.recordTurn(
            text = "هذا كتاب",
            normalizedText = "هذا كتاب",
            speaker = Sender.USER,
            entities = mapOf("object" to "كتاب")
        )
        
        val resolved = contextManager.resolvePronoun("هذا")
        assertNotNull(resolved)
        assertEquals("كتاب", resolved?.value)
    }
    
    @Test
    fun testResolvePronoun_notFound() {
        val resolved = contextManager.resolvePronoun("it")
        assertNull(resolved)
    }
}
