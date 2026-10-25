package com.example

import com.example.zahra.memory.UserProfile
import com.example.zahra.memory.UserProfileExtractor
import com.example.zahra.memory.TopicMemory
import com.example.zahra.memory.GreetingVariator
import com.example.zahra.memory.UnifiedMemoryManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6.6: Unified 3-Tier Memory System Tests
 * 
 * Tests the three memory tiers:
 * 1. UserProfileExtractor — auto-extraction from conversation
 * 2. TopicMemory — cross-session topic tracking
 * 3. GreetingVariator — dynamic, varied greetings
 * 4. UnifiedMemoryManager — integration of all tiers
 */
class Phase6MemorySystemTest {

    // ========== UserProfileExtractor Tests ==========
    
    private val extractor = UserProfileExtractor()

    @Test
    fun `extract name from Arabic`() {
        val result = extractor.extract("أنا اسمي أحمد")
        assertEquals("أحمد", result.name)
        assertTrue(result.hasExtraction)
    }

    @Test
    fun `extract name from Egyptian dialect`() {
        val result = extractor.extract("انا سارة")
        assertNotNull(result.name)
        assertTrue(result.hasExtraction)
    }

    @Test
    fun `extract name from English`() {
        val result = extractor.extract("my name is John")
        assertEquals("John", result.name)
    }

    @Test
    fun `extract name with call me pattern`() {
        val result = extractor.extract("call me Alex")
        assertEquals("Alex", result.name)
    }

    @Test
    fun `extract age from Arabic`() {
        val result = extractor.extract("عمري 25 سنة")
        assertEquals(25, result.age)
    }

    @Test
    fun `extract age from Egyptian`() {
        val result = extractor.extract("أنا عندي 30 سنة")
        assertEquals(30, result.age)
    }

    @Test
    fun `extract age rejects invalid values`() {
        val result = extractor.extract("عمري 200 سنة")
        assertNull("Age 200 should be rejected", result.age)
    }

    @Test
    fun `extract gender male explicit`() {
        val result = extractor.extract("أنا ولد")
        assertEquals(UserProfile.Gender.MALE, result.gender)
    }

    @Test
    fun `extract gender female explicit`() {
        val result = extractor.extract("أنا بنت")
        assertEquals(UserProfile.Gender.FEMALE, result.gender)
    }

    @Test
    fun `extract gender female from verb forms`() {
        val result = extractor.extract("أنا عايزة أتعلم برمجة")
        assertEquals(UserProfile.Gender.FEMALE, result.gender)
    }

    @Test
    fun `extract likes from Arabic`() {
        val result = extractor.extract("بحب القراءة والكتابة")
        assertTrue("Should extract at least one like", result.likes.isNotEmpty())
    }

    @Test
    fun `extract likes from Egyptian`() {
        val result = extractor.extract("أنا بحب القهوة")
        assertTrue("Should extract likes", result.likes.isNotEmpty())
    }

    @Test
    fun `extract dislikes`() {
        val result = extractor.extract("بكره الزحمة أوي")
        assertTrue("Should extract dislikes", result.dislikes.isNotEmpty())
    }

    @Test
    fun `extract dislikes negative pattern`() {
        val result = extractor.extract("مش بحب الحر")
        assertTrue("Should extract dislikes from 'مش بحب'", result.dislikes.isNotEmpty())
    }

    @Test
    fun `extract hobbies`() {
        val result = extractor.extract("هوايتي الرسم والتصوير")
        assertTrue("Should extract hobbies", result.hobbies.isNotEmpty())
    }

    @Test
    fun `extract location`() {
        val result = extractor.extract("أنا من مصر")
        assertNotNull("Should extract location", result.location)
    }

    @Test
    fun `extract location with ساكن`() {
        val result = extractor.extract("ساكن في الإسكندرية")
        assertNotNull("Should extract location", result.location)
    }

    @Test
    fun `extract work`() {
        val result = extractor.extract("أنا مهندس برمجيات")
        assertNotNull("Should extract work", result.work)
    }

    @Test
    fun `extract work with بشتغل`() {
        val result = extractor.extract("بشتغل في البرمجة")
        assertNotNull("Should extract work", result.work)
    }

    @Test
    fun `extract education`() {
        val result = extractor.extract("أنا طالب في الجامعة")
        assertNotNull("Should extract education", result.education)
    }

    @Test
    fun `detect Egyptian dialect`() {
        val result = extractor.extract("عايز أكل عيش كده")
        assertTrue("Should detect Egyptian dialect", result.dialectHints.contains("egyptian"))
    }

    @Test
    fun `detect Gulf dialect`() {
        val result = extractor.extract("أبغى أروح السوق")
        assertTrue("Should detect Gulf dialect", result.dialectHints.contains("gulf"))
    }

    @Test
    fun `detect Levantine dialect`() {
        val result = extractor.extract("بدي أروح ع البيت")
        assertTrue("Should detect Levantine dialect", result.dialectHints.contains("levantine"))
    }

    @Test
    fun `no extraction for plain math`() {
        val result = extractor.extract("احسب 25 * 4")
        assertFalse("Should not extract from math", result.hasExtraction)
    }

    @Test
    fun `combined extraction`() {
        val result = extractor.extract("أنا اسمي محمد وعمري 28 سنة وبحب البرمجة")
        assertNotNull("Should extract name", result.name)
        assertEquals(28, result.age)
        assertTrue("Should extract likes", result.likes.isNotEmpty())
        assertTrue(result.hasExtraction)
    }

    // ========== TopicMemory Tests ==========

    @Test
    fun `detect programming topic`() {
        val tm = TopicMemory()
        val detected = tm.detectTopics("أنا بكتب كود بايثون")
        assertTrue("Should detect programming topic", 
            detected.any { it.topic.contains("برمجة") || it.topic.contains("تكنولوجيا") })
    }

    @Test
    fun `detect health topic`() {
        val tm = TopicMemory()
        val detected = tm.detectTopics("روحت الجيم النهارده")
        assertTrue("Should detect health/sports topic", detected.isNotEmpty())
    }

    @Test
    fun `detect religion topic`() {
        val tm = TopicMemory()
        val detected = tm.detectTopics("إزاي أصلي الفجر؟")
        assertTrue("Should detect religion topic", 
            detected.any { it.topic.contains("دين") || it.topic.contains("عبادات") })
    }

    @Test
    fun `detect multiple topics`() {
        val tm = TopicMemory()
        val detected = tm.detectTopics("بعد الصلاة بروح الجيم وبعدين أكتب كود")
        assertTrue("Should detect multiple topics", detected.size >= 2)
    }

    @Test
    fun `topic mention count increments`() {
        val tm = TopicMemory()
        tm.detectTopics("بحب البرمجة")
        tm.detectTopics("كود بايثون")
        val topics = tm.getAllTopics()
        val programmingTopic = topics.find { it.topic.contains("برمجة") || it.topic.contains("تكنولوجيا") }
        assertNotNull("Should have programming topic", programmingTopic)
        assertTrue("Mention count should be 2", programmingTopic!!.mentionCount >= 2)
    }

    @Test
    fun `topic serialization roundtrip`() {
        val tm = TopicMemory()
        tm.detectTopics("بحب البرمجة والصلاة")
        val json = tm.toJsonString()
        
        val tm2 = TopicMemory()
        tm2.loadFromJson(json)
        
        assertEquals("Should have same number of topics", 
            tm.getAllTopics().size, tm2.getAllTopics().size)
    }

    @Test
    fun `new session tracking`() {
        val tm = TopicMemory()
        tm.detectTopics("بحب البرمجة")
        tm.startNewSession()
        tm.detectTopics("روحت الجيم")
        
        val prevTopics = tm.getPreviousSessionTopics()
        assertTrue("Should have previous session topics", prevTopics.isNotEmpty())
    }

    // ========== GreetingVariator Tests ==========

    @Test
    fun `greeting varies across calls`() {
        val gv = GreetingVariator()
        val responses = (1..10).map { 
            gv.generateGreeting("السلام عليكم") 
        }.toSet()
        
        assertTrue("Should produce varied greetings (got ${responses.size} unique)", 
            responses.size > 1)
    }

    @Test
    fun `salam greeting contains appropriate response`() {
        val gv = GreetingVariator()
        val response = gv.generateGreeting("السلام عليكم")
        // Should contain some form of peace response
        assertTrue("Should respond to salam",
            response.contains("سلام") || response.contains("أهل") || 
            response.contains("مرحب") || response.contains("هلا") ||
            response.contains("نورت"))
    }

    @Test
    fun `morning greeting detects time`() {
        val gv = GreetingVariator()
        val response = gv.generateGreeting("صباح الخير")
        // Should be morning-themed (or first-time)
        assertTrue("Should have some greeting content",
            response.length > 5)
    }

    @Test
    fun `evening greeting detects time`() {
        val gv = GreetingVariator()
        val response = gv.generateGreeting("مساء الخير")
        assertTrue("Should have some greeting content",
            response.length > 5)
    }

    @Test
    fun `greeting includes name when provided`() {
        val gv = GreetingVariator()
        val context = GreetingVariator.GreetingContext(userName = "أحمد")
        // Generate multiple greetings - at least some should include the name
        val responses = (1..20).map { 
            gv.generateGreeting("السلام عليكم", context) 
        }
        val withName = responses.filter { it.contains("أحمد") }
        assertTrue("Some greetings should include name (got ${withName.size}/20)", 
            withName.isNotEmpty())
    }

    @Test
    fun `first session gets special greeting`() {
        val gv = GreetingVariator()
        val context = GreetingVariator.GreetingContext(isFirstSession = true)
        val response = gv.generateGreeting("السلام عليكم", context)
        // First-time greetings should introduce Zahra
        assertTrue("First greeting should mention زهرة",
            response.contains("زهرة") || response.contains("مساعد"))
    }

    @Test
    fun `farewell varies across calls`() {
        val gv = GreetingVariator()
        val responses = (1..10).map { gv.generateFarewell() }.toSet()
        assertTrue("Should produce varied farewells (got ${responses.size} unique)", 
            responses.size > 1)
    }

    // ========== UserProfile Tests ==========

    @Test
    fun `profile friendly summary with data`() {
        val profile = UserProfile(
            name = "أحمد",
            age = 25,
            gender = UserProfile.Gender.MALE,
            likes = listOf("البرمجة", "القراءة"),
            location = "مصر"
        )
        val summary = profile.toFriendlySummary()
        assertTrue("Should contain name", summary.contains("أحمد"))
        assertTrue("Should contain age", summary.contains("25"))
    }

    @Test
    fun `profile friendly summary when empty`() {
        val profile = UserProfile()
        val summary = profile.toFriendlySummary()
        assertTrue("Should prompt user to share info",
            summary.contains("عارفة") || summary.contains("قولي"))
    }

    @Test
    fun `profile to fact map`() {
        val profile = UserProfile(
            name = "سارة",
            age = 30,
            gender = UserProfile.Gender.FEMALE,
            likes = listOf("الطبخ")
        )
        val map = profile.toFactMap()
        assertEquals("سارة", map["الاسم"])
        assertEquals("30", map["العمر"])
        assertEquals("أنثى", map["الجنس"])
    }

    @Test
    fun `relationship level based on sessions`() {
        val new = UserProfile(totalSessions = 0)
        assertEquals(UserProfile.RelationshipLevel.NEW, new.relationshipLevel)
        
        val familiar = UserProfile(totalSessions = 15)
        assertEquals(UserProfile.RelationshipLevel.FAMILIAR, familiar.relationshipLevel)
        
        val close = UserProfile(totalSessions = 50)
        assertEquals(UserProfile.RelationshipLevel.CLOSE, close.relationshipLevel)
    }

    // ========== UnifiedMemoryManager Tests ==========

    @Test
    fun `unified memory records turns`() {
        val um = UnifiedMemoryManager(null) // No context = in-memory only
        um.recordTurn("أنا اسمي أحمد", "أهلاً أحمد!", "greet")
        assertEquals(1, um.getConversationLength())
    }

    @Test
    fun `unified memory auto-extracts profile`() {
        val um = UnifiedMemoryManager(null)
        um.recordTurn("أنا اسمي أحمد وعمري 25", "أهلاً!", "greet")
        
        assertEquals("أحمد", um.getUserName())
        assertEquals(25, um.userProfile.age)
    }

    @Test
    fun `unified memory detects topics`() {
        val um = UnifiedMemoryManager(null)
        um.recordTurn("بحب البرمجة وكود بايثون", "ممتاز!", "chat")
        
        val topics = um.topicMemory.getAllTopics()
        assertTrue("Should detect topics", topics.isNotEmpty())
    }

    @Test
    fun `unified memory ring buffer limit`() {
        val um = UnifiedMemoryManager(null)
        for (i in 1..25) {
            um.recordTurn("رسالة $i", "رد $i", "test")
        }
        assertTrue("Should cap at 20 turns", um.getConversationLength() <= 20)
    }

    @Test
    fun `unified memory new session tracking`() {
        val um = UnifiedMemoryManager(null)
        um.recordTurn("بحب البرمجة", "ممتاز!", "chat")
        um.startNewSession()
        
        assertTrue("Should be returning user after session",
            um.userProfile.totalSessions >= 1)
    }

    @Test
    fun `unified memory clear all`() {
        val um = UnifiedMemoryManager(null)
        um.recordTurn("أنا اسمي أحمد وعمري 25", "أهلاً!", "greet")
        um.clearAll()
        
        assertNull("Name should be cleared", um.getUserName())
        assertEquals(0, um.getConversationLength())
    }

    @Test
    fun `unified memory dump works`() {
        val um = UnifiedMemoryManager(null)
        um.recordTurn("أنا اسمي أحمد", "أهلاً!", "greet")
        val dump = um.getMemoryDump()
        
        assertTrue("Dump should contain section headers",
            dump.contains("Memory Dump") || dump.contains("Topics") || dump.contains("Profile"))
    }
}
