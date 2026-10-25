package com.example

import com.example.zahra.islamic.IslamicKnowledgeBase
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Islamic Knowledge Base Tests
 * 
 * اختبارات شاملة للقاعدة الإسلامية الأساسية:
 * - فقه يسير
 * - سيرة يسير
 * - عقيدة يسير
 * - تزكية يسيرة
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IslamicKnowledgeBaseTest {

    private lateinit var islamicKnowledge: IslamicKnowledgeBase

    @Before
    fun setup() {
        islamicKnowledge = IslamicKnowledgeBase()
    }

    // ========== General Tests ==========

    @Test
    fun `test islamic knowledge base initialization`() {
        assertNotNull("IslamicKnowledgeBase should be initialized", islamicKnowledge)
    }

    @Test
    fun `test islamic knowledge base has content`() {
        val count = islamicKnowledge.getLessonCount()
        assertTrue("Should have at least 20 lessons, got $count", count >= 20)
    }

    @Test
    fun `test all categories exist`() {
        val categories = islamicKnowledge.getAllCategories()
        assertEquals("Should have 4 categories", 4, categories.size)
        assertTrue("Should include FIQH", categories.contains(IslamicKnowledgeBase.Category.FIQH))
        assertTrue("Should include SEERAH", categories.contains(IslamicKnowledgeBase.Category.SEERAH))
        assertTrue("Should include AQEEDAH", categories.contains(IslamicKnowledgeBase.Category.AQEEDAH))
        assertTrue("Should include TAZKIYAH", categories.contains(IslamicKnowledgeBase.Category.TAZKIYAH))
    }

    // ========== Random Lesson Tests ==========

    @Test
    fun `test random lesson retrieval`() {
        val lesson = islamicKnowledge.getRandomLesson()
        assertNotNull("Should return a lesson", lesson)
        assertTrue("Lesson title should not be empty", lesson.title.isNotEmpty())
        assertTrue("Lesson content should not be empty", lesson.content.isNotEmpty())
        assertTrue("Lesson source should not be empty", lesson.source.isNotEmpty())
    }

    @Test
    fun `test random lesson has valid category`() {
        val lesson = islamicKnowledge.getRandomLesson()
        assertNotNull("Lesson category should not be null", lesson.category)
        assertTrue("Lesson category should be valid", 
            islamicKnowledge.getAllCategories().contains(lesson.category))
    }

    // ========== Category-Based Tests ==========

    @Test
    fun `test fiqh lessons exist`() {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.FIQH)
        assertNotNull("Should find fiqh lesson", lesson)
        assertEquals("Category should be FIQH", IslamicKnowledgeBase.Category.FIQH, lesson!!.category)
    }

    @Test
    fun `test seerah lessons exist`() {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.SEERAH)
        assertNotNull("Should find seerah lesson", lesson)
        assertEquals("Category should be SEERAH", IslamicKnowledgeBase.Category.SEERAH, lesson!!.category)
    }

    @Test
    fun `test aqeedah lessons exist`() {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.AQEEDAH)
        assertNotNull("Should find aqeedah lesson", lesson)
        assertEquals("Category should be AQEEDAH", IslamicKnowledgeBase.Category.AQEEDAH, lesson!!.category)
    }

    @Test
    fun `test tazkiyah lessons exist`() {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.TAZKIYAH)
        assertNotNull("Should find tazkiyah lesson", lesson)
        assertEquals("Category should be TAZKIYAH", IslamicKnowledgeBase.Category.TAZKIYAH, lesson!!.category)
    }

    @Test
    fun `test get all lessons in fiqh category`() {
        val lessons = islamicKnowledge.getAllLessonsInCategory(IslamicKnowledgeBase.Category.FIQH)
        assertTrue("Should have at least 3 fiqh lessons", lessons.size >= 3)
        lessons.forEach { lesson ->
            assertEquals("All lessons should be FIQH", IslamicKnowledgeBase.Category.FIQH, lesson.category)
        }
    }

    @Test
    fun `test get all lessons in seerah category`() {
        val lessons = islamicKnowledge.getAllLessonsInCategory(IslamicKnowledgeBase.Category.SEERAH)
        assertTrue("Should have at least 3 seerah lessons", lessons.size >= 3)
        lessons.forEach { lesson ->
            assertEquals("All lessons should be SEERAH", IslamicKnowledgeBase.Category.SEERAH, lesson.category)
        }
    }

    @Test
    fun `test get all lessons in aqeedah category`() {
        val lessons = islamicKnowledge.getAllLessonsInCategory(IslamicKnowledgeBase.Category.AQEEDAH)
        assertTrue("Should have at least 3 aqeedah lessons", lessons.size >= 3)
        lessons.forEach { lesson ->
            assertEquals("All lessons should be AQEEDAH", IslamicKnowledgeBase.Category.AQEEDAH, lesson.category)
        }
    }

    @Test
    fun `test get all lessons in tazkiyah category`() {
        val lessons = islamicKnowledge.getAllLessonsInCategory(IslamicKnowledgeBase.Category.TAZKIYAH)
        assertTrue("Should have at least 5 tazkiyah lessons", lessons.size >= 5)
        lessons.forEach { lesson ->
            assertEquals("All lessons should be TAZKIYAH", IslamicKnowledgeBase.Category.TAZKIYAH, lesson.category)
        }
    }

    // ========== Search Tests ==========

    @Test
    fun `test search for prayer lessons`() {
        val results = islamicKnowledge.searchLessons("الصلاة")
        assertTrue("Should find prayer lessons", results.isNotEmpty())
        assertTrue("Should find at least one fiqh lesson", 
            results.any { it.category == IslamicKnowledgeBase.Category.FIQH })
    }

    @Test
    fun `test search for prophet lessons`() {
        val results = islamicKnowledge.searchLessons("النبي")
        assertTrue("Should find prophet lessons", results.isNotEmpty())
        assertTrue("Should find at least one seerah lesson", 
            results.any { it.category == IslamicKnowledgeBase.Category.SEERAH })
    }

    @Test
    fun `test search for tawhid lessons`() {
        val results = islamicKnowledge.searchLessons("التوحيد")
        assertTrue("Should find tawhid lessons", results.isNotEmpty())
        assertTrue("Should find at least one aqeedah lesson", 
            results.any { it.category == IslamicKnowledgeBase.Category.AQEEDAH })
    }

    @Test
    fun `test search for self purification lessons`() {
        val results = islamicKnowledge.searchLessons("النفس")
        assertTrue("Should find self purification lessons", results.isNotEmpty())
        assertTrue("Should find at least one tazkiyah lesson", 
            results.any { it.category == IslamicKnowledgeBase.Category.TAZKIYAH })
    }

    @Test
    fun `test search for wudu lessons`() {
        val results = islamicKnowledge.searchLessons("الوضوء")
        assertTrue("Should find wudu lessons", results.isNotEmpty())
        assertTrue("First result should be about wudu", 
            results.first().title.contains("وضوء", ignoreCase = true) ||
            results.first().content.contains("وضوء", ignoreCase = true))
    }

    @Test
    fun `test search for fasting lessons`() {
        val results = islamicKnowledge.searchLessons("الصيام")
        assertTrue("Should find fasting lessons", results.isNotEmpty())
    }

    @Test
    fun `test search returns ranked results`() {
        val results = islamicKnowledge.searchLessons("الصلاة الزكاة")
        assertTrue("Should find multiple lessons", results.size >= 2)
        // Results should be sorted by relevance
        val firstScore = calculateRelevanceScore(results.first(), listOf("الصلاة", "الزكاة"))
        val lastScore = calculateRelevanceScore(results.last(), listOf("الصلاة", "الزكاة"))
        assertTrue("Results should be sorted by relevance", firstScore >= lastScore)
    }

    private fun calculateRelevanceScore(lesson: IslamicKnowledgeBase.Lesson, keywords: List<String>): Int {
        var score = 0
        keywords.forEach { keyword ->
            if (lesson.title.contains(keyword, ignoreCase = true)) score += 3
            if (lesson.content.contains(keyword, ignoreCase = true)) score += 2
            if (lesson.keywords.any { it.contains(keyword, ignoreCase = true) }) score += 1
        }
        return score
    }

    @Test
    fun `test search with no results`() {
        val results = islamicKnowledge.searchLessons("كلمة_غير_موجودة_في_القاعدة")
        assertTrue("Should return empty list for non-existent term", results.isEmpty())
    }

    // ========== Lesson Content Validation Tests ==========

    @Test
    fun `test all lessons have titles`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        allLessons.forEach { lesson ->
            assertTrue("Lesson ${lesson.id} should have title", lesson.title.isNotEmpty())
        }
    }

    @Test
    fun `test all lessons have content`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        allLessons.forEach { lesson ->
            assertTrue("Lesson ${lesson.id} should have content", lesson.content.isNotEmpty())
            assertTrue("Lesson ${lesson.id} content should be substantial", lesson.content.length > 100)
        }
    }

    @Test
    fun `test all lessons have sources`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        allLessons.forEach { lesson ->
            assertTrue("Lesson ${lesson.id} should have source", lesson.source.isNotEmpty())
        }
    }

    @Test
    fun `test lessons have keywords`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val lessonsWithKeywords = allLessons.count { it.keywords.isNotEmpty() }
        assertTrue("At least 80% of lessons should have keywords", 
            lessonsWithKeywords.toDouble() / allLessons.size >= 0.8)
    }

    @Test
    fun `test some lessons have quran references`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val lessonsWithQuran = allLessons.count { it.quranRef != null }
        assertTrue("At least 50% of lessons should have Quran references", 
            lessonsWithQuran.toDouble() / allLessons.size >= 0.5)
    }

    @Test
    fun `test some lessons have hadith references`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val lessonsWithHadith = allLessons.count { it.hadithRef != null }
        assertTrue("At least 30% of lessons should have Hadith references", 
            lessonsWithHadith.toDouble() / allLessons.size >= 0.3)
    }

    // ========== Formatting Tests ==========

    @Test
    fun `test lesson formatting`() {
        val lesson = islamicKnowledge.getRandomLesson()
        val formatted = islamicKnowledge.formatLesson(lesson)
        
        assertNotNull("Formatted lesson should not be null", formatted)
        assertTrue("Formatted lesson should contain emoji", 
            formatted.contains(lesson.category.emoji))
        assertTrue("Formatted lesson should contain title", 
            formatted.contains(lesson.title))
        assertTrue("Formatted lesson should contain content", 
            formatted.contains(lesson.content))
        assertTrue("Formatted lesson should contain source", 
            formatted.contains(lesson.source))
    }

    @Test
    fun `test lesson formatting with quran reference`() {
        val lessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val lessonWithQuran = lessons.find { it.quranRef != null }
        assertNotNull("Should find lesson with Quran reference", lessonWithQuran)
        
        val formatted = islamicKnowledge.formatLesson(lessonWithQuran!!)
        assertTrue("Formatted lesson should contain Quran reference", 
            formatted.contains("📖"))
        assertTrue("Formatted lesson should contain Quran text", 
            formatted.contains(lessonWithQuran.quranRef!!))
    }

    @Test
    fun `test lesson formatting with hadith reference`() {
        val lessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val lessonWithHadith = lessons.find { it.hadithRef != null }
        assertNotNull("Should find lesson with Hadith reference", lessonWithHadith)
        
        val formatted = islamicKnowledge.formatLesson(lessonWithHadith!!)
        assertTrue("Formatted lesson should contain Hadith reference", 
            formatted.contains("📜"))
        assertTrue("Formatted lesson should contain Hadith text", 
            formatted.contains(lessonWithHadith.hadithRef!!))
    }

    // ========== Specific Lesson Tests ==========

    @Test
    fun `test wudu lesson exists and is correct`() {
        val lesson = islamicKnowledge.getLessonById("fiqh_wudu")
        assertNotNull("Wudu lesson should exist", lesson)
        assertEquals("Should be in FIQH category", IslamicKnowledgeBase.Category.FIQH, lesson!!.category)
        assertTrue("Should mention wudu in title", lesson.title.contains("وضوء", ignoreCase = true))
        assertTrue("Should mention wudu steps", lesson.content.contains("غسل الوجه"))
        assertTrue("Should mention wudu nullifiers", lesson.content.contains("نواقض"))
    }

    @Test
    fun `test prayer lesson exists and is correct`() {
        val lesson = islamicKnowledge.getLessonById("fiqh_prayer")
        assertNotNull("Prayer lesson should exist", lesson)
        assertEquals("Should be in FIQH category", IslamicKnowledgeBase.Category.FIQH, lesson!!.category)
        assertTrue("Should mention prayer in title", lesson.title.contains("الصلاة"))
        assertTrue("Should mention prayer pillars", lesson.content.contains("أركان"))
        assertTrue("Should mention rakats", lesson.content.contains("ركعات"))
    }

    @Test
    fun `test prophet birth lesson exists`() {
        val lesson = islamicKnowledge.getLessonById("seerah_birth")
        assertNotNull("Prophet birth lesson should exist", lesson)
        assertEquals("Should be in SEERAH category", IslamicKnowledgeBase.Category.SEERAH, lesson!!.category)
        assertTrue("Should mention birth", lesson.title.contains("مولد") || lesson.title.contains("ميلاد"))
    }

    @Test
    fun `test hijrah lesson exists`() {
        val lesson = islamicKnowledge.getLessonById("seerah_hijrah")
        assertNotNull("Hijrah lesson should exist", lesson)
        assertEquals("Should be in SEERAH category", IslamicKnowledgeBase.Category.SEERAH, lesson!!.category)
        assertTrue("Should mention hijrah", lesson.title.contains("هجرة"))
    }

    @Test
    fun `test tawhid lesson exists`() {
        val lesson = islamicKnowledge.getLessonById("aqeedah_tawhid")
        assertNotNull("Tawhid lesson should exist", lesson)
        assertEquals("Should be in AQEEDAH category", IslamicKnowledgeBase.Category.AQEEDAH, lesson!!.category)
        assertTrue("Should mention tawhid", lesson.title.contains("توحيد"))
        assertTrue("Should mention types of tawhid", 
            lesson.content.contains("ربوبية") || lesson.content.contains("ألوهية"))
    }

    @Test
    fun `test pillars of faith lesson exists`() {
        val lesson = islamicKnowledge.getLessonById("aqeedah_pillars")
        assertNotNull("Pillars of faith lesson should exist", lesson)
        assertEquals("Should be in AQEEDAH category", IslamicKnowledgeBase.Category.AQEEDAH, lesson!!.category)
        assertTrue("Should mention pillars", lesson.title.contains("أركان") || lesson.title.contains("إيمان"))
    }

    @Test
    fun `test change lesson exists (from book)`() {
        val lesson = islamicKnowledge.getLessonById("tazkiyah_change")
        assertNotNull("Change lesson should exist", lesson)
        assertEquals("Should be in TAZKIYAH category", IslamicKnowledgeBase.Category.TAZKIYAH, lesson!!.category)
        assertTrue("Should mention change", lesson.title.contains("تغيير"))
        assertTrue("Should reference the book", lesson.source.contains("كيف نغير ما بأنفسنا"))
    }

    @Test
    fun `test three axes lesson exists (from book)`() {
        val lesson = islamicKnowledge.getLessonById("tazkiyah_three_axes")
        assertNotNull("Three axes lesson should exist", lesson)
        assertEquals("Should be in TAZKIYAH category", IslamicKnowledgeBase.Category.TAZKIYAH, lesson!!.category)
        assertTrue("Should mention three axes", lesson.title.contains("ثلاثة") || lesson.title.contains("محاور"))
        assertTrue("Should mention mind", lesson.content.contains("عقل"))
        assertTrue("Should mention heart", lesson.content.contains("قلب"))
        assertTrue("Should mention self", lesson.content.contains("نفس"))
    }

    @Test
    fun `test quran as medicine lesson exists (from book)`() {
        val lesson = islamicKnowledge.getLessonById("tazkiyah_quran")
        assertNotNull("Quran medicine lesson should exist", lesson)
        assertEquals("Should be in TAZKIYAH category", IslamicKnowledgeBase.Category.TAZKIYAH, lesson!!.category)
        assertTrue("Should mention Quran", lesson.title.contains("قرآن"))
        assertTrue("Should mention medicine", lesson.title.contains("دواء") || lesson.content.contains("دواء"))
        assertTrue("Should reference the book", lesson.source.contains("كيف نغير ما بأنفسنا"))
    }

    // ========== Integration Tests ==========

    @Test
    fun `test all categories have balanced content`() {
        val categories = islamicKnowledge.getAllCategories()
        
        categories.forEach { category ->
            val lessons = islamicKnowledge.getAllLessonsInCategory(category)
            assertTrue("Category ${category.labelAr} should have at least 3 lessons", lessons.size >= 3)
        }
    }

    @Test
    fun `test lesson IDs are unique`() {
        val allLessons = islamicKnowledge.getAllCategories().flatMap { category ->
            islamicKnowledge.getAllLessonsInCategory(category)
        }
        
        val ids = allLessons.map { it.id }
        val uniqueIds = ids.toSet()
        
        assertEquals("All lesson IDs should be unique", ids.size, uniqueIds.size)
    }

    @Test
    fun `test search returns relevant results across categories`() {
        val searchTerms = listOf("الصلاة", "النبي", "الإيمان", "النفس", "القرآن")
        
        searchTerms.forEach { term ->
            val results = islamicKnowledge.searchLessons(term)
            assertTrue("Search for '$term' should return results", results.isNotEmpty())
            assertTrue("Search for '$term' should return at least one relevant result",
                results.any { lesson ->
                    lesson.title.contains(term, ignoreCase = true) ||
                    lesson.content.contains(term, ignoreCase = true) ||
                    lesson.keywords.any { it.contains(term, ignoreCase = true) }
                })
        }
    }
}
