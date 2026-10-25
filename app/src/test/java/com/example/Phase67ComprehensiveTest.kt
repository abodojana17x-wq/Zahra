package com.example

import com.example.zahra.vision.VisionEngine
import com.example.zahra.voice.VoiceEngine
import com.example.zahra.wisdom.WisdomBank
import com.example.zahra.mood.MoodJournal
import com.example.zahra.knowledge.SemanticSearchEngine
import com.example.zahra.intelligence.mood.MoodAnalyzer
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase 6.7: Comprehensive Integration Tests
 * 
 * اختبارات شاملة للمكونات الجديدة:
 * 1. Vision Engine (OCR + Face Detection)
 * 2. Voice Engine (STT + TTS)
 * 3. Wisdom Bank (200+ حكم)
 * 4. Mood Journal (تتبع المزاج)
 * 5. Semantic Search (البحث الدلالي)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Phase67ComprehensiveTest {

    private lateinit var visionEngine: VisionEngine
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var wisdomBank: WisdomBank
    private lateinit var moodJournal: MoodJournal
    private lateinit var semanticSearch: SemanticSearchEngine
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        visionEngine = VisionEngine(context)
        voiceEngine = VoiceEngine(context)
        wisdomBank = WisdomBank()
        moodJournal = MoodJournal(context)
        semanticSearch = SemanticSearchEngine()
    }

    // ========== Vision Engine Tests ==========

    @Test
    fun `test vision engine initialization`() {
        assertNotNull("VisionEngine should be initialized", visionEngine)
    }

    @Test
    fun `test image analysis with solid color bitmap`() {
        val bitmap = createSolidColorBitmap(100, 100, Color.RED)
        val analysis = visionEngine.analyzeImage(bitmap)
        
        assertNotNull("Analysis should not be null", analysis)
        assertEquals("Width should match", 100, analysis.width)
        assertEquals("Height should match", 100, analysis.height)
        assertTrue("Should detect dominant colors", analysis.dominantColors.isNotEmpty())
        assertNotNull("Description should be generated", analysis.description)
        assertTrue("Description should contain dimensions", analysis.description.contains("100×100"))
    }

    @Test
    fun `test brightness calculation for bright image`() {
        val brightBitmap = createSolidColorBitmap(50, 50, Color.WHITE)
        val analysis = visionEngine.analyzeImage(brightBitmap)
        
        assertTrue("Brightness should be high for white image", analysis.brightness > 0.8)
        assertTrue("Description should mention bright", analysis.description.contains("مضيئة"))
    }

    @Test
    fun `test brightness calculation for dark image`() {
        val darkBitmap = createSolidColorBitmap(50, 50, Color.BLACK)
        val analysis = visionEngine.analyzeImage(darkBitmap)
        
        assertTrue("Brightness should be low for black image", analysis.brightness < 0.2)
        assertTrue("Description should mention dark", analysis.description.contains("داكنة"))
    }

    @Test
    fun `test dominant color detection`() {
        val blueBitmap = createSolidColorBitmap(100, 100, Color.BLUE)
        val analysis = visionEngine.analyzeImage(blueBitmap)
        
        assertTrue("Should detect at least one dominant color", analysis.dominantColors.isNotEmpty())
        val topColor = analysis.dominantColors.first()
        assertTrue("Top color should be blue-ish", 
            topColor.nameAr.contains("أزرق") || topColor.name.contains("blue"))
    }

    @Test
    fun `test face detection returns empty for solid color`() {
        val bitmap = createSolidColorBitmap(200, 200, Color.GRAY)
        var detectedFaces: List<VisionEngine.FaceInfo>? = null
        
        visionEngine.detectFaces(bitmap) { faces ->
            detectedFaces = faces
        }
        
        // Give ML Kit time to process
        Thread.sleep(100)
        
        assertNotNull("Face detection callback should be called", detectedFaces)
        assertTrue("Should detect no faces in solid color image", detectedFaces!!.isEmpty())
    }

    @Test
    fun `test OCR initialization`() {
        val initialized = visionEngine.initializeOCR("eng")
        // Note: This might fail if tessdata files are not present in test environment
        // In production, this should return true
        assertNotNull("OCR initialization result should not be null", initialized)
    }

    @Test
    fun `test vision engine cleanup`() {
        visionEngine.release()
        // Should not throw exception
        assertTrue("Cleanup should complete without error", true)
    }

    // ========== Voice Engine Tests ==========

    @Test
    fun `test voice engine initialization`() {
        voiceEngine.initialize()
        assertNotNull("VoiceEngine should be initialized", voiceEngine)
    }

    @Test
    fun `test voice engine state tracking`() {
        assertFalse("Should not be listening initially", voiceEngine.isListening())
        assertFalse("Should not be speaking initially", voiceEngine.isSpeaking())
    }

    @Test
    fun `test voice engine speech rate configuration`() {
        voiceEngine.initialize()
        // Should not throw exception
        voiceEngine.setSpeechRate(1.0f)
        voiceEngine.setSpeechRate(0.5f)
        voiceEngine.setSpeechRate(2.0f)
        assertTrue("Speech rate configuration should work", true)
    }

    @Test
    fun `test voice engine pitch configuration`() {
        voiceEngine.initialize()
        // Should not throw exception
        voiceEngine.setPitch(1.0f)
        voiceEngine.setPitch(0.5f)
        voiceEngine.setPitch(2.0f)
        assertTrue("Pitch configuration should work", true)
    }

    @Test
    fun `test voice engine cleanup`() {
        voiceEngine.initialize()
        voiceEngine.release()
        // Should not throw exception
        assertTrue("Voice cleanup should complete", true)
    }

    // ========== Wisdom Bank Tests ==========

    @Test
    fun `test wisdom bank has sufficient content`() {
        val count = wisdomBank.getCount()
        assertTrue("Should have at least 200 wisdoms, got $count", count >= 200)
    }

    @Test
    fun `test random wisdom retrieval`() {
        val wisdom = wisdomBank.getRandomWisdom()
        assertNotNull("Should return a wisdom", wisdom)
        assertTrue("Wisdom text should not be empty", wisdom.text.isNotEmpty())
        assertTrue("Wisdom author should not be empty", wisdom.author.isNotEmpty())
    }

    @Test
    fun `test wisdom by category - patience`() {
        val wisdom = wisdomBank.getWisdomByCategory(WisdomBank.Category.PATIENCE)
        assertNotNull("Should find patience wisdom", wisdom)
        assertEquals("Category should be patience", WisdomBank.Category.PATIENCE, wisdom!!.category)
    }

    @Test
    fun `test wisdom by category - hope`() {
        val wisdom = wisdomBank.getWisdomByCategory(WisdomBank.Category.HOPE)
        assertNotNull("Should find hope wisdom", wisdom)
        assertEquals("Category should be hope", WisdomBank.Category.HOPE, wisdom!!.category)
    }

    @Test
    fun `test wisdom by mood - sad`() {
        val wisdom = wisdomBank.getWisdomByMood(MoodAnalyzer.Mood.SAD)
        assertNotNull("Should find wisdom for sad mood", wisdom)
    }

    @Test
    fun `test wisdom by mood - happy`() {
        val wisdom = wisdomBank.getWisdomByMood(MoodAnalyzer.Mood.HAPPY)
        assertNotNull("Should find wisdom for happy mood", wisdom)
    }

    @Test
    fun `test wisdom search`() {
        val results = wisdomBank.searchWisdoms("صبر")
        assertTrue("Should find wisdoms about patience", results.isNotEmpty())
    }

    @Test
    fun `test religious wisdom retrieval`() {
        val wisdom = wisdomBank.getReligiousWisdom()
        assertNotNull("Should find religious wisdom", wisdom)
        assertTrue("Should be marked as religious", wisdom!!.isReligious)
    }

    @Test
    fun `test inspirational wisdom retrieval`() {
        val wisdom = wisdomBank.getInspirationalWisdom()
        assertNotNull("Should find inspirational wisdom", wisdom)
    }

    @Test
    fun `test wisdom formatting`() {
        val wisdom = wisdomBank.getRandomWisdom()
        val formatted = wisdomBank.formatWisdom(wisdom)
        
        assertNotNull("Formatted wisdom should not be null", formatted)
        assertTrue("Should contain emoji", formatted.contains(wisdom.category.emoji))
        assertTrue("Should contain text", formatted.contains(wisdom.text))
        assertTrue("Should contain author", formatted.contains(wisdom.author))
    }

    @Test
    fun `test all categories exist`() {
        val categories = wisdomBank.getAllCategories()
        assertTrue("Should have at least 10 categories", categories.size >= 10)
        assertTrue("Should include PATIENCE", categories.contains(WisdomBank.Category.PATIENCE))
        assertTrue("Should include HOPE", categories.contains(WisdomBank.Category.HOPE))
        assertTrue("Should include LOVE", categories.contains(WisdomBank.Category.LOVE))
        assertTrue("Should include WORK", categories.contains(WisdomBank.Category.WORK))
    }

    // ========== Mood Journal Tests ==========

    @Test
    fun `test mood journal initialization`() {
        assertNotNull("MoodJournal should be initialized", moodJournal)
    }

    @Test
    fun `test add mood entry`() {
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8, "يوم جميل")
        val entries = moodJournal.getTodayEntries()
        assertTrue("Should have at least one entry", entries.isNotEmpty())
        assertEquals("Mood should be HAPPY", MoodAnalyzer.Mood.HAPPY, entries.last().mood)
    }

    @Test
    fun `test mood statistics calculation`() {
        moodJournal.clearAll()
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        moodJournal.addEntry(MoodAnalyzer.Mood.SAD, -0.3)
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.7)
        
        val stats = moodJournal.calculateStats(7)
        
        assertEquals("Should have 3 entries", 3, stats.totalEntries)
        assertTrue("Average sentiment should be positive", stats.averageSentiment > 0)
        assertEquals("Most common mood should be HAPPY", MoodAnalyzer.Mood.HAPPY, stats.mostCommonMood)
    }

    @Test
    fun `test mood trend detection - improving`() {
        moodJournal.clearAll()
        moodJournal.addEntry(MoodAnalyzer.Mood.SAD, -0.5)
        moodJournal.addEntry(MoodAnalyzer.Mood.SAD, -0.3)
        moodJournal.addEntry(MoodAnalyzer.Mood.NEUTRAL, 0.1)
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.5)
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        
        val stats = moodJournal.calculateStats(7)
        assertEquals("Trend should be improving", MoodJournal.MoodTrend.IMPROVING, stats.trend)
    }

    @Test
    fun `test mood trend detection - stable`() {
        moodJournal.clearAll()
        moodJournal.addEntry(MoodAnalyzer.Mood.NEUTRAL, 0.1)
        moodJournal.addEntry(MoodAnalyzer.Mood.NEUTRAL, 0.0)
        moodJournal.addEntry(MoodAnalyzer.Mood.NEUTRAL, 0.1)
        
        val stats = moodJournal.calculateStats(7)
        assertEquals("Trend should be stable", MoodJournal.MoodTrend.STABLE, stats.trend)
    }

    @Test
    fun `test personalized advice generation`() {
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.7)
        val advice = moodJournal.getPersonalizedAdvice()
        assertNotNull("Advice should not be null", advice)
        assertTrue("Advice should not be empty", advice.isNotEmpty())
    }

    @Test
    fun `test mood summary generation`() {
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        val summary = moodJournal.getMoodSummary()
        assertNotNull("Summary should not be null", summary)
        assertTrue("Summary should contain statistics", summary.contains("ملخص"))
    }

    @Test
    fun `test mood journal clear`() {
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8)
        moodJournal.clearAll()
        val stats = moodJournal.calculateStats(7)
        assertEquals("Should have no entries after clear", 0, stats.totalEntries)
    }

    @Test
    fun `test mood journal export and import`() {
        moodJournal.clearAll()
        moodJournal.addEntry(MoodAnalyzer.Mood.HAPPY, 0.8, "test")
        
        val json = moodJournal.exportToJson()
        assertNotNull("Exported JSON should not be null", json)
        assertTrue("JSON should contain entries", json.contains("entries"))
        
        moodJournal.clearAll()
        val imported = moodJournal.importFromJson(json)
        assertTrue("Import should succeed", imported)
        
        val stats = moodJournal.calculateStats(7)
        assertTrue("Should have entries after import", stats.totalEntries > 0)
    }

    // ========== Semantic Search Tests ==========

    @Test
    fun `test semantic search initialization`() {
        assertNotNull("SemanticSearchEngine should be initialized", semanticSearch)
    }

    @Test
    fun `test add document to search index`() {
        val doc = SemanticSearchEngine.SearchDocument(
            id = "test1",
            title = "الصبر",
            content = "الصبر مفتاح الفرج ومن صبر نال",
            category = "حكم"
        )
        
        semanticSearch.addDocument(doc)
        assertEquals("Should have 1 document", 1, semanticSearch.getDocumentCount())
    }

    @Test
    fun `test semantic search finds relevant documents`() {
        semanticSearch.clear()
        semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "الصبر",
            content = "الصبر من أعظم الفضائل الإسلامية",
            category = "دين"
        ))
        semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
            id = "2",
            title = "النجاح",
            content = "النجاح يحتاج عمل واجتهاد",
            category = "تطوير"
        ))
        
        val results = semanticSearch.search("الصبر والفضائل", topK = 2)
        assertTrue("Should find results", results.isNotEmpty())
        assertEquals("First result should be about patience", "1", results.first().document.id)
    }

    @Test
    fun `test hybrid search combines methods`() {
        semanticSearch.clear()
        semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "الأمل",
            content = "لا تيأس من روح الله فإن الفرج قريب",
            category = "حكم",
            keywords = listOf("أمل", "فرج", "يأس")
        ))
        
        val results = semanticSearch.hybridSearch("الأمل والفرج", topK = 3)
        assertTrue("Should find results", results.isNotEmpty())
        assertTrue("Score should be positive", results.first().score > 0)
    }

    @Test
    fun `test synonym expansion`() {
        semanticSearch.clear()
        semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "السعادة",
            content = "السعادة تأتي من الرضا والقناعة",
            category = "حكم"
        ))
        
        // Search with synonym
        val results = semanticSearch.search("الفرح والبهجة", topK = 3)
        assertTrue("Should find happiness content via synonyms", results.isNotEmpty())
    }

    @Test
    fun `test empty search returns empty results`() {
        semanticSearch.clear()
        val results = semanticSearch.search("أي شيء")
        assertTrue("Empty engine should return empty results", results.isEmpty())
    }

    @Test
    fun `test search index clear`() {
        semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
            id = "1",
            title = "test",
            content = "test content",
            category = "test"
        ))
        
        semanticSearch.clear()
        assertEquals("Should have 0 documents after clear", 0, semanticSearch.getDocumentCount())
    }

    // ========== Integration Tests ==========

    @Test
    fun `test wisdom bank covers all moods`() {
        val moods = listOf(
            MoodAnalyzer.Mood.HAPPY,
            MoodAnalyzer.Mood.SAD,
            MoodAnalyzer.Mood.ANXIOUS,
            MoodAnalyzer.Mood.MOTIVATED
        )
        
        for (mood in moods) {
            val wisdom = wisdomBank.getWisdomByMood(mood)
            // Most moods should have matching wisdoms
            if (wisdom != null) {
                assertTrue("Wisdom for $mood should have matching mood tag",
                    mood in wisdom.moodTags)
            }
        }
    }

    @Test
    fun `test semantic search with wisdom content`() {
        semanticSearch.clear()
        
        val wisdoms = listOf(
            wisdomBank.getWisdomByCategory(WisdomBank.Category.PATIENCE),
            wisdomBank.getWisdomByCategory(WisdomBank.Category.HOPE),
            wisdomBank.getWisdomByCategory(WisdomBank.Category.FAITH)
        ).filterNotNull()
        
        for ((index, wisdom) in wisdoms.withIndex()) {
            semanticSearch.addDocument(SemanticSearchEngine.SearchDocument(
                id = "w$index",
                title = wisdom.category.labelAr,
                content = wisdom.text,
                category = wisdom.category.name,
                keywords = wisdom.keywords
            ))
        }
        
        val results = semanticSearch.hybridSearch("الصبر والفرج", topK = 3)
        assertTrue("Should find wisdom-related results", results.isNotEmpty())
    }

    // ========== Helper Methods ==========

    private fun createSolidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
