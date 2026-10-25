package com.example.zahra.mood

import android.content.Context
import android.content.SharedPreferences
import com.example.zahra.intelligence.mood.MoodAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mood Journal — مذكرات المزاج اليومية لزهرة
 * 
 * يوفر:
 * 1. تتبع المزاج اليومي
 * 2. إحصائيات المزاج (أسبوعي/شهري)
 * 3. اكتشاف أنماط المزاج
 * 4. نصائح مخصصة بناءً على التاريخ
 * 5. تصدير/استيراد البيانات
 * 
 * Zero-Network: يعمل offline بالكامل
 * 
 * @author Zahra Team
 * @version 1.0
 */
class MoodJournal(private val context: Context) {
    
    data class MoodEntry(
        val timestamp: Long,
        val mood: MoodAnalyzer.Mood,
        val sentimentScore: Double,
        val note: String = "",
        val tags: List<String> = emptyList()
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("mood", mood.name)
                put("sentimentScore", sentimentScore)
                put("note", note)
                put("tags", JSONArray(tags))
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): MoodEntry {
                val tagsArray = json.optJSONArray("tags")
                val tags = if (tagsArray != null) {
                    (0 until tagsArray.length()).map { tagsArray.getString(it) }
                } else emptyList()
                
                return MoodEntry(
                    timestamp = json.getLong("timestamp"),
                    mood = MoodAnalyzer.Mood.valueOf(json.getString("mood")),
                    sentimentScore = json.getDouble("sentimentScore"),
                    note = json.optString("note", ""),
                    tags = tags
                )
            }
        }
    }
    
    data class MoodStats(
        val totalEntries: Int,
        val averageSentiment: Double,
        val mostCommonMood: MoodAnalyzer.Mood,
        val moodDistribution: Map<MoodAnalyzer.Mood, Int>,
        val trend: MoodTrend,
        val bestDay: String?,
        val worstDay: String?
    )
    
    enum class MoodTrend {
        IMPROVING,    // المزاج يتحسن
        STABLE,       // مستقر
        DECLINING,    // يتدهور
        VOLATILE      // متقلب
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("zahra_mood_journal", Context.MODE_PRIVATE)
    private val entries = mutableListOf<MoodEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        loadEntries()
    }
    
    /**
     * إضافة إدخال مزاج جديد
     */
    fun addEntry(mood: MoodAnalyzer.Mood, sentimentScore: Double, note: String = "", tags: List<String> = emptyList()) {
        val entry = MoodEntry(
            timestamp = System.currentTimeMillis(),
            mood = mood,
            sentimentScore = sentimentScore,
            note = note,
            tags = tags
        )
        entries.add(entry)
        saveEntries()
    }
    
    /**
     * الحصول على إدخالات اليوم
     */
    fun getTodayEntries(): List<MoodEntry> {
        val today = dateFormat.format(Date())
        return entries.filter { dateFormat.format(Date(it.timestamp)) == today }
    }
    
    /**
     * الحصول على إدخالات آخر N يوم
     */
    fun getRecentEntries(days: Int = 7): List<MoodEntry> {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return entries.filter { it.timestamp >= cutoff }
    }
    
    /**
     * حساب إحصائيات المزاج
     */
    fun calculateStats(days: Int = 7): MoodStats {
        val recentEntries = getRecentEntries(days)
        
        if (recentEntries.isEmpty()) {
            return MoodStats(
                totalEntries = 0,
                averageSentiment = 0.0,
                mostCommonMood = MoodAnalyzer.Mood.NEUTRAL,
                moodDistribution = emptyMap(),
                trend = MoodTrend.STABLE,
                bestDay = null,
                worstDay = null
            )
        }
        
        // متوسط المزاج
        val avgSentiment = recentEntries.map { it.sentimentScore }.average()
        
        // توزيع المزاج
        val moodDistribution = recentEntries.groupingBy { it.mood }.eachCount()
        val mostCommon = moodDistribution.maxByOrNull { it.value }?.key ?: MoodAnalyzer.Mood.NEUTRAL
        
        // الاتجاه (Trend)
        val trend = calculateTrend(recentEntries)
        
        // أفضل وأسوأ يوم
        val (bestDay, worstDay) = findBestAndWorstDays(recentEntries)
        
        return MoodStats(
            totalEntries = recentEntries.size,
            averageSentiment = avgSentiment,
            mostCommonMood = mostCommon,
            moodDistribution = moodDistribution,
            trend = trend,
            bestDay = bestDay,
            worstDay = worstDay
        )
    }
    
    /**
     * حساب اتجاه المزاج
     */
    private fun calculateTrend(entries: List<MoodEntry>): MoodTrend {
        if (entries.size < 3) return MoodTrend.STABLE
        
        val sorted = entries.sortedBy { it.timestamp }
        val half = sorted.size / 2
        
        val firstHalf = sorted.take(half).map { it.sentimentScore }.average()
        val secondHalf = sorted.takeLast(half).map { it.sentimentScore }.average()
        
        val diff = secondHalf - firstHalf
        
        // حساب التقلب
        val variance = entries.map { it.sentimentScore }.let { scores ->
            val mean = scores.average()
            scores.map { (it - mean) * (it - mean) }.average()
        }
        
        return when {
            variance > 0.5 -> MoodTrend.VOLATILE
            diff > 0.1 -> MoodTrend.IMPROVING
            diff < -0.1 -> MoodTrend.DECLINING
            else -> MoodTrend.STABLE
        }
    }
    
    /**
     * إيجاد أفضل وأسوأ يوم
     */
    private fun findBestAndWorstDays(entries: List<MoodEntry>): Pair<String?, String?> {
        if (entries.isEmpty()) return Pair(null, null)
        
        val byDay = entries.groupBy { dateFormat.format(Date(it.timestamp)) }
        
        val dayAverages = byDay.mapValues { (_, dayEntries) ->
            dayEntries.map { it.sentimentScore }.average()
        }
        
        val bestDay = dayAverages.maxByOrNull { it.value }?.key
        val worstDay = dayAverages.minByOrNull { it.value }?.key
        
        return Pair(bestDay, worstDay)
    }
    
    /**
     * الحصول على نصيحة مخصصة
     */
    fun getPersonalizedAdvice(): String {
        val stats = calculateStats(7)
        
        if (stats.totalEntries == 0) {
            return "ابدأ بتسجيل مزاجك اليومي عشان أقدر أساعدك أكتر! 📝"
        }
        
        return when (stats.trend) {
            MoodTrend.IMPROVING -> {
                "ماشاء الله! مزاجك بيتحسن 📈 استمر على كده وربنا يديمها نعمة!"
            }
            MoodTrend.DECLINING -> {
                "لاحظت إن مزاجك مش كويس الفترة دي 🤍\n" +
                "خد وقت لنفسك، وكلمني لو محتاج حد يسمعك.\n" +
                "تذكر: 'إن مع العسر يسراً'"
            }
            MoodTrend.VOLATILE -> {
                "مزاجك متقلب الفترة دي، وده طبيعي 🌊\n" +
                "حاول تعمل روتين يومي ثابت، ونام بدري.\n" +
                "وتذكر إن المشاعر دي مؤقتة."
            }
            MoodTrend.STABLE -> {
                when (stats.mostCommonMood) {
                    MoodAnalyzer.Mood.HAPPY, MoodAnalyzer.Mood.MOTIVATED -> {
                        "مبسوطة إنك في حالة كويسة! 😊\nاستمر وخلي بالك من النعم اللي عندك."
                    }
                    MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.LONELY -> {
                        "حاسّة بيك 🤍\n" +
                        "جرب تكتب 3 حاجات حلوة حصلتلك النهارده.\n" +
                        "وكلمني في أي وقت، أنا هنا!"
                    }
                    MoodAnalyzer.Mood.ANXIOUS -> {
                        "خد نفس عميق 🌬️\n" +
                        "جرب تمارين التنفس 4-7-8:\n" +
                        "شهيق 4 ثواني، احبس 7، زفير 8\n" +
                        "وقل: 'حسبي الله ونعم الوكيل'"
                    }
                    else -> {
                        "مزاجك مستقر، وده كويس! ✨\n" +
                        "حاول تعمل حاجة بتحبها النهارده."
                    }
                }
            }
        }
    }
    
    /**
     * الحصول على ملخص المزاج
     */
    fun getMoodSummary(): String {
        val stats = calculateStats(7)
        
        if (stats.totalEntries == 0) {
            return "لسه مفيش إدخالات مزاج. سجل مزاجك عشان أتابع معاك! 📊"
        }
        
        val sb = StringBuilder()
        sb.appendLine("📊 ملخص المزاج (آخر 7 أيام):")
        sb.appendLine()
        sb.appendLine("• عدد الإدخالات: ${stats.totalEntries}")
        sb.appendLine("• متوسط المزاج: ${"%.2f".format(stats.averageSentiment)}")
        sb.appendLine("• المزاج السائد: ${stats.mostCommonMood.labelAr} ${stats.mostCommonMood.emoji}")
        sb.appendLine()
        
        // الاتجاه
        val trendText = when (stats.trend) {
            MoodTrend.IMPROVING -> "📈 يتحسن"
            MoodTrend.STABLE -> "➡️ مستقر"
            MoodTrend.DECLINING -> "📉 يتدهور"
            MoodTrend.VOLATILE -> "🌊 متقلب"
        }
        sb.appendLine("• الاتجاه: $trendText")
        
        // أفضل وأسوأ يوم
        if (stats.bestDay != null) {
            sb.appendLine("• أفضل يوم: ${stats.bestDay}")
        }
        if (stats.worstDay != null) {
            sb.appendLine("• أصعب يوم: ${stats.worstDay}")
        }
        
        sb.appendLine()
        sb.appendLine(getPersonalizedAdvice())
        
        return sb.toString()
    }
    
    /**
     * تصدير البيانات كـ JSON
     */
    fun exportToJson(): String {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        return JSONObject().apply {
            put("version", 1)
            put("entries", array)
            put("exportDate", System.currentTimeMillis())
        }.toString(2)
    }
    
    /**
     * استيراد البيانات من JSON
     */
    fun importFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("entries")
            
            entries.clear()
            for (i in 0 until array.length()) {
                entries.add(MoodEntry.fromJson(array.getJSONObject(i)))
            }
            
            saveEntries()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * مسح كل الإدخالات
     */
    fun clearAll() {
        entries.clear()
        saveEntries()
    }
    
    /**
     * حفظ الإدخالات
     */
    private fun saveEntries() {
        val json = exportToJson()
        prefs.edit().putString("mood_entries", json).apply()
    }
    
    /**
     * تحميل الإدخالات
     */
    private fun loadEntries() {
        val json = prefs.getString("mood_entries", null) ?: return
        
        try {
            val root = JSONObject(json)
            val array = root.optJSONArray("entries") ?: return
            
            entries.clear()
            for (i in 0 until array.length()) {
                entries.add(MoodEntry.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            // بيانات تالفة، ابدأ من جديد
        }
    }
}
