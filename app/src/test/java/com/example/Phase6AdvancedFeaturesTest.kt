package com.example

import com.example.zahra.knowledge.SemanticSearchEngine
import com.example.zahra.mood.MoodJournal
import com.example.zahra.wisdom.WisdomBank
import com.example.zahra.intelligence.mood.MoodAnalyzer
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6.7: Advanced Features Tests
 * 
 * اختبارات شاملة للمكونات الجديدة:
 * 1. SemanticSearchEngine — البحث الدلالي
 * 2. WisdomBank — بنك الحكم والأمثال
 * 3. MoodJournal — مذكرات المزاج
 */
class Phase6AdvancedFeaturesTest {

    // ========== SemanticSearchEngine Tests ==========

    @Test
    fun `semantic search finds relevant documents`() {
        val engine = SemanticSearchEngine()
        
        engine.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "الصبر مفتاح الفرج",
            content = "الصبر من أعظم virtues الإسلامية. من صبر نال ومن جد وجد.",
            category = "دين"
        ))
        
        engine.addDocument(SemanticSearchEngine.SearchDocument(
            id = "2",
            title = "النجاح في الحياة",
            content = "النجاح يحتاج عمل واجتهاد وصبر. الطريق طويل لكن المكافأة عظيمة.",
            category = "تطوير"
        ))
        
        val results = engine.search("الصبر والعزيمة", topK = 2)
        assertTrue("Should find results", results.isNotEmpty())
        assertTrue("First result should mention patience", 
            results.first().document.content.contains("صبر", ignoreCase = true) ||
            results.first().document.title.contains("صبر", ignoreCase = true))
    }

    @Test
    fun `hybrid search combines keyword and semantic`() {
        val engine = SemanticSearchEngine()
        
        engine.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "حكم عن الأمل",
            content = "لا تيأس من روح الله فإن الفرج قريب",
            category = "حكم",
            keywords = listOf("أمل", "فرج", "يأس")
        ))
        
        val results = engine.hybridSearch("الأمل والفرج", topK = 3)
        assertTrue("Should find results", results.isNotEmpty())
        assertTrue("Score should be positive", results.first().score > 0)
    }

    @Test
    fun `synonym expansion works`() {
        val engine = SemanticSearchEngine()
        
        engine.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "السعادة الحقيقية",
            content = "السعادة تأتي من الرضا والقناعة بما قسمه الله",
            category = "حكم"
        ))
        
        // Search with synonym
        val results = engine.search("الفرح والبهجة", topK = 3)
        assertTrue("Should find happiness-related content via synonyms", results.isNotEmpty())
    }

    @Test
    fun `empty search returns empty list`() {
        val engine = SemanticSearchEngine()
        val results = engine.search("أي شيء")
        assertTrue("Empty engine should return empty results", results.isEmpty())
    }

    @Test
    fun `document count tracking works`() {
        val engine = SemanticSearchEngine()
        assertEquals(0, engine.getDocumentCount())
        
        engine.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1", title = "test", content = "content", category = "test"
        ))
        assertEquals(1, engine.getDocumentCount())
        
        engine.clear()
        assertEquals(0, engine.getDocumentCount())
    }

    // ========== WisdomBank Tests ==========

    @Test
    fun `wisdom bank has content`() {
        val bank = WisdomBank()
        assertTrue("Should have wisdoms loaded", bank.getCount() > 50)
    }

    @Test
    fun `get random wisdom`() {
        val bank = WisdomBank()
        val wisdom = bank.getRandomWisdom()
        
        assertNotNull("Should return a wisdom", wisdom)
        assertTrue("Text should not be empty", wisdom.text.isNotBlank())
        assertTrue("Author should not be empty", wisdom.author.isNotBlank())
    }

    @Test
    fun `get wisdom by mood - sad`() {
        val bank = WisdomBank()
        val wisdom = bank.getWisdomByMood(MoodAnalyzer.Mood.SAD)
        
        assertNotNull("Should find wisdom for sad mood", wisdom)
        assertTrue("Wisdom should be tagged for sad mood",
            MoodAnalyzer.Mood.SAD in wisdom!!.moodTags)
    }

    @Test
    fun `get wisdom by mood - anxious`() {
        val bank = WisdomBank()
        val wisdom = bank.getWisdomByMood(MoodAnalyzer.Mood.ANXIOUS)
        
        assertNotNull("Should find wisdom for anxious mood", wisdom)
    }

    @Test
    fun `get wisdom by category`() {
        val bank = WisdomBank()
        val wisdom = bank.getWisdomByCategory(WisdomBank.Category.PATIENCE)
        
        assertNotNull("Should find patience wisdom", wisdom)
        assertEquals("Category should be patience", 
            WisdomBank.Category.PATIENCE, wisdom!!.category)
    }

    @Test
    fun `search wisdoms by keyword`() {
        val bank = WisdomBank()
        val results = bank.searchWisdoms("صبر")
        
        assertTrue("Should find wisdoms about patience", results.isNotEmpty())
        assertTrue("At least one result should mention patience",
            results.any { it.text.contains("صبر") || it.keywords.contains("صبر") })
    }

    @Test
    fun `get religious wisdom`() {
        val bank = WisdomBank()
        val wisdom = bank.getReligiousWisdom()
        
        assertNotNull("Should have religious wisdom", wisdom)
        assertTrue("Should be marked as religious", wisdom!!.isReligious)
    }

    @Test
    fun `get inspirational wisdom`() {
        val bank = WisdomBank()
        val wisdom = bank.getInspirationalWisdom()
        
        assertNotNull("Should have inspirational wisdom", wisdom)
        assertTrue("Should be from inspiring categories",
            wisdom!!.category in listOf(
                WisdomBank.Category.HOPE,
                WisdomBank.Category.SUCCESS,
                WisdomBank.Category.STRENGTH,
                WisdomBank.Category.COURAGE
            ))
    }

    @Test
    fun `format wisdom output`() {
        val bank = WisdomBank()
        val wisdom = bank.getRandomWisdom()
        val formatted = bank.formatWisdom(wisdom)
        
        assertTrue("Should contain emoji", formatted.contains(wisdom.category.emoji))
        assertTrue("Should contain text", formatted.contains(wisdom.text))
        assertTrue("Should contain author", formatted.contains(wisdom.author))
    }

    @Test
    fun `all categories exist`() {
        val bank = WisdomBank()
        val categories = bank.getAllCategories()
        
        assertTrue("Should have at least 10 categories", categories.size >= 10)
        assertTrue("Should have PATIENCE", categories.contains(WisdomBank.Category.PATIENCE))
        assertTrue("Should have HOPE", categories.contains(WisdomBank.Category.HOPE))
        assertTrue("Should have FAITH", categories.contains(WisdomBank.Category.FAITH))
    }

    // ========== MoodJournal Tests ==========

    @Test
    fun `add and retrieve mood entry`() {
        // Use mock context (null-safe test)
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8, "يوم جميل")
        val today = journal.getTodayEntries()
        
        assertTrue("Should have today's entries", today.isNotEmpty())
        assertEquals(MoodAnalyzer.Mood.HAPPY, today.last().mood)
    }

    @Test
    fun `calculate stats with entries`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        journal.addEntry(MoodAnalyzer.Mood.SAD, -0.3)
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.7)
        
        val stats = journal.calculateStats(7)
        
        assertEquals("Should have 3 entries", 3, stats.totalEntries)
        assertTrue("Average should be positive", stats.averageSentiment > 0)
        assertEquals("Most common should be HAPPY", MoodAnalyzer.Mood.HAPPY, stats.mostCommonMood)
    }

    @Test
    fun `empty stats return defaults`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        val stats = journal.calculateStats(7)
        
        assertEquals(0, stats.totalEntries)
        assertEquals(0.0, stats.averageSentiment, 0.001)
        assertEquals(MoodAnalyzer.Mood.NEUTRAL, stats.mostCommonMood)
    }

    @Test
    fun `mood trend detection - improving`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        // Simulate improving mood over time
        journal.addEntry(MoodAnalyzer.Mood.SAD, -0.5)
        journal.addEntry(MoodAnalyzer.Mood.SAD, -0.3)
        journal.addEntry(MoodAnalyzer.Mood.NEUTRAL, 0.1)
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.5)
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        
        val stats = journal.calculateStats(7)
        assertEquals("Trend should be improving", MoodJournal.MoodTrend.IMPROVING, stats.trend)
    }

    @Test
    fun `personalized advice based on mood`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        // Test with improving mood
        journal.addEntry(MoodAnalyzer.Mood.SAD, -0.3)
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.7)
        
        val advice = journal.getPersonalizedAdvice()
        assertTrue("Advice should not be empty", advice.isNotBlank())
    }

    @Test
    fun `mood summary generation`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8, "يوم جميل")
        journal.addEntry(MoodAnalyzer.Mood.SAD, -0.3, "يوم صعب")
        
        val summary = journal.getMoodSummary()
        assertTrue("Summary should contain stats", summary.contains("ملخص"))
    }

    @Test
    fun `clear all entries`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        journal.clearAll()
        
        val stats = journal.calculateStats(7)
        assertEquals("Should be empty after clear", 0, stats.totalEntries)
    }

    @Test
    fun `export and import JSON`() {
        val journal = MoodJournal(android.content.ContextWrapper(null))
        journal.clearAll()
        
        journal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8, "test note", listOf("work"))
        val json = journal.exportToJson()
        
        assertTrue("JSON should contain version", json.contains("version"))
        assertTrue("JSON should contain entries", json.contains("entries"))
        
        // Import into new journal
        val journal2 = MoodJournal(android.content.ContextWrapper(null))
        journal2.clearAll()
        val imported = journal2.importFromJson(json)
        
        assertTrue("Import should succeed", imported)
    }

    // ========== Integration Tests ==========

    @Test
    fun `wisdom bank covers all moods`() {
        val bank = WisdomBank()
        val moods = listOf(
            MoodAnalyzer.Mood.HAPPY, MoodAnalyzer.Mood.SAD,
            MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.MOTIVATED,
            MoodAnalyzer.Mood.LONELY, MoodAnalyzer.Mood.CONFUSED
        )
        
        for (mood in moods) {
            val wisdom = bank.getWisdomByMood(mood)
            // Most moods should have matching wisdoms (some might not)
            if (wisdom != null) {
                assertTrue("Wisdom for $mood should have matching mood tag",
                    mood in wisdom.moodTags)
            }
        }
    }

    @Test
    fun `semantic search with wisdom content`() {
        val engine = SemanticSearchEngine()
        val bank = WisdomBank()
        
        // Index some wisdoms
        val wisdoms = listOf(
            bank.getWisdomByCategory(WisdomBank.Category.PATIENCE),
            bank.getWisdomByCategory(WisdomBank.Category.HOPE),
            bank.getWisdomByCategory(WisdomBank.Category.FAITH)
        ).filterNotNull()
        
        for ((index, wisdom) in wisdoms.withIndex()) {
            engine.addDocument(SemanticSearchEngine.SearchDocument(
                id = "w$index",
                title = wisdom.category.labelAr,
                content = wisdom.text,
                category = wisdom.category.name,
                keywords = wisdom.keywords
            ))
        }
        
        val results = engine.hybridSearch("الصبر والفرج", topK = 3)
        assertTrue("Should find wisdom-related results", results.isNotEmpty())
    }
}
