package com.example.zahra.memory

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Topic Memory — ذاكرة المواضيع بين المحادثات
 * 
 * Remembers what topics were discussed across sessions, enabling Zahra to:
 * - Reference previous conversations ("كلمتني قبل كده عن البرمجة...")
 * - Detect returning topics ("لسة اتكلمنا عن ده!")
 * - Track user's evolving interests
 * - Provide continuity between sessions
 * 
 * Pure rule-based, zero-LLM, zero-network.
 * Stored encrypted locally alongside user facts.
 */
class TopicMemory {

    data class TopicEntry(
        val id: String = UUID.randomUUID().toString(),
        val topic: String,              // e.g., "البرمجة", "الصحة", "الدين"
        val keywords: List<String>,     // Related keywords
        val firstMentioned: Long = System.currentTimeMillis(),
        val lastMentioned: Long = System.currentTimeMillis(),
        val mentionCount: Int = 1,
        val sessionId: String = "",
        val summary: String = ""        // Brief summary of what was discussed
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("topic", topic)
                put("keywords", JSONArray(keywords))
                put("firstMentioned", firstMentioned)
                put("lastMentioned", lastMentioned)
                put("mentionCount", mentionCount)
                put("sessionId", sessionId)
                put("summary", summary)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): TopicEntry {
                val kwArray = json.optJSONArray("keywords")
                val keywords = if (kwArray != null) {
                    (0 until kwArray.length()).map { kwArray.getString(it) }
                } else emptyList()
                
                return TopicEntry(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    topic = json.getString("topic"),
                    keywords = keywords,
                    firstMentioned = json.optLong("firstMentioned", System.currentTimeMillis()),
                    lastMentioned = json.optLong("lastMentioned", System.currentTimeMillis()),
                    mentionCount = json.optInt("mentionCount", 1),
                    sessionId = json.optString("sessionId", ""),
                    summary = json.optString("summary", "")
                )
            }
        }
    }

    // In-memory topic store
    private val topics = mutableListOf<TopicEntry>()
    
    // Current session tracking
    private var currentSessionId: String = UUID.randomUUID().toString()
    private val sessionTopics = mutableSetOf<String>()

    /**
     * Topic categories and their keyword patterns
     */
    private val topicCategories = mapOf(
        "البرمجة والتكنولوجيا" to listOf(
            "برمجة", "كود", "بايثون", "جافا", "تطبيق", "موقع", "ويب", "أندرويد", "ios",
            "programming", "code", "python", "java", "javascript", "app", "developer", "مبرمج",
            "ذكاء اصطناعي", "ai", "تعلم آلة", "machine learning", "خوارزمية"
        ),
        "الصحة والرياضة" to listOf(
            "رياضة", "جيم", "تمارين", "صحة", "طعام", "أكل", "دايت", "رجيم", "نوم",
            "جري", "مشي", "سباحة", "كرة", "fitness", "gym", "health", "diet", "وزن"
        ),
        "الدين والعبادات" to listOf(
            "صلاة", "قرآن", "صيام", "زكاة", "حج", "دعاء", "سنة", "حديث", "فقه",
            "مسجد", "عبادة", "توبة", "استغفار", "أذكار", "تفسير", "سورة"
        ),
        "الدراسة والتعليم" to listOf(
            "دراسة", "امتحان", "جامعة", "مدرسة", "كتاب", "تعلم", "كورس", "دورة",
            "شهادة", "تخرج", "واجب", "بحث", "محاضرة", "study", "exam", "university"
        ),
        "العمل والمهنة" to listOf(
            "شغل", "عمل", "وظيفة", "مرتب", "ترقية", "مدير", "زميل", "مشروع",
            "meeting", "work", "job", "career", "office", "مقابلة"
        ),
        "السفر والسياحة" to listOf(
            "سفر", "سياحة", "فندق", "طيارة", "بلد", "رحلة", "visit", "travel",
            "trip", "flight", "hotel", "فيزا", "جواز"
        ),
        "العلاقات والأسرة" to listOf(
            "أهل", "عيلة", "أم", "أب", "أخ", "أخت", "صديق", "حبيب", "خطوبة",
            "زواج", "family", "friend", "relationship", "أمي", "أبوي"
        ),
        "الطبخ والأكل" to listOf(
            "طبخ", "أكلة", "وصفة", "مطبخ", "حلو", "كيك", "عشاء", "غداء", "فطور",
            "cook", "recipe", "food", "restaurant", "مطعم"
        ),
        "الترفيه والفن" to listOf(
            "فيلم", "مسلسل", "أغنية", "موسيقى", "لعبة", "game", "movie", "series",
            "netflix", "يوتيوب", "أنمي", "رسم", "تصوير", "فن"
        ),
        "الأخبار والأحداث" to listOf(
            "أخبار", "حدث", "سياسة", "اقتصاد", "عالم", "news", "event",
            "حرب", "انتخابات", "بورصة", "سوق"
        )
    )

    /**
     * Detect and record topics from user input
     */
    fun detectTopics(input: String): List<TopicEntry> {
        val normalized = input.lowercase()
        val detectedTopics = mutableListOf<TopicEntry>()
        
        for ((category, keywords) in topicCategories) {
            val matchedKeywords = keywords.filter { normalized.contains(it.lowercase()) }
            if (matchedKeywords.isNotEmpty()) {
                val existing = topics.find { it.topic == category }
                if (existing != null) {
                    // Update existing topic
                    val updated = existing.copy(
                        lastMentioned = System.currentTimeMillis(),
                        mentionCount = existing.mentionCount + 1,
                        keywords = (existing.keywords + matchedKeywords).distinct().take(20),
                        sessionId = currentSessionId
                    )
                    topics.remove(existing)
                    topics.add(updated)
                    detectedTopics.add(updated)
                } else {
                    // New topic
                    val newTopic = TopicEntry(
                        topic = category,
                        keywords = matchedKeywords.distinct(),
                        sessionId = currentSessionId
                    )
                    topics.add(newTopic)
                    detectedTopics.add(newTopic)
                }
                sessionTopics.add(category)
            }
        }
        
        return detectedTopics
    }

    /**
     * Get topics discussed in previous sessions (not current)
     */
    fun getPreviousSessionTopics(): List<TopicEntry> {
        return topics
            .filter { it.sessionId != currentSessionId && it.mentionCount > 0 }
            .sortedByDescending { it.lastMentioned }
    }

    /**
     * Get all topics sorted by recency
     */
    fun getAllTopics(): List<TopicEntry> {
        return topics.sortedByDescending { it.lastMentioned }
    }

    /**
     * Get top N most discussed topics
     */
    fun getTopTopics(n: Int = 5): List<TopicEntry> {
        return topics.sortedByDescending { it.mentionCount }.take(n)
    }

    /**
     * Check if a topic was discussed before (in previous sessions)
     */
    fun wasTopicDiscussedBefore(topic: String): TopicEntry? {
        return topics.find { 
            it.topic == topic && it.sessionId != currentSessionId 
        }
    }

    /**
     * Start a new session
     */
    fun clear() {
        topics.clear()
        sessionTopics.clear()
        currentSessionId = UUID.randomUUID().toString()
    }

    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        sessionTopics.clear()
    }

    /**
     * Get current session topics
     */
    fun getCurrentSessionTopics(): Set<String> = sessionTopics.toSet()

    /**
     * Serialize to JSON for storage
     */
    fun toJsonString(): String {
        val array = JSONArray()
        topics.forEach { array.put(it.toJson()) }
        return JSONObject().apply {
            put("version", 1)
            put("topics", array)
            put("currentSessionId", currentSessionId)
        }.toString()
    }

    /**
     * تحميل المواضيع من JSON. يُبنى في قائمة مؤقتة ثم يُستبدل، فلا تبقى حالة
     * نصف محمّلة عند بيانات تالفة. @return false إذا كانت البيانات غير قابلة للقراءة.
     */
    fun loadFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("topics")
            val parsed = mutableListOf<TopicEntry>()
            if (array != null) {
                for (i in 0 until array.length()) parsed.add(TopicEntry.fromJson(array.getJSONObject(i)))
            }
            topics.clear()
            topics.addAll(parsed)
            true
        } catch (e: Exception) {
            // لا نُبقي مزيجًا من قديم وجديد، ونُعيد فشلًا صريحًا للمستدعي ليُبلّغ الواجهة.
            topics.clear()
            false
        }
    }

    /**
     * Get a friendly summary of topic history
     */
    fun getTopicSummary(): String {
        if (topics.isEmpty()) {
            return "لسه ما اتكلمناش عن مواضيع معينة. أنا هنا عشان أتكلم معاك في أي حاجة! 🌸"
        }

        val topTopics = getTopTopics(5)
        val prevTopics = getPreviousSessionTopics()
        
        val sb = StringBuilder()
        sb.append("📚 المواضيع اللي اتكلمنا فيها:\n\n")
        
        for (topic in topTopics) {
            val timesLabel = when {
                topic.mentionCount == 1 -> "مرة"
                topic.mentionCount == 2 -> "مرتين"
                topic.mentionCount in 3..10 -> "${topic.mentionCount} مرات"
                else -> "${topic.mentionCount} مرة"
            }
            sb.append("• ${topic.topic} ($timesLabel)\n")
        }
        
        if (prevTopics.isNotEmpty()) {
            sb.append("\n🔄 مواضيع من محادثات سابقة:\n")
            prevTopics.take(3).forEach {
                sb.append("• ${it.topic}\n")
            }
        }
        
        return sb.toString().trimEnd()
    }
}
