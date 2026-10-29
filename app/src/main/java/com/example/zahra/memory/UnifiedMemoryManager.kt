package com.example.zahra.memory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import com.example.zahra.security.ZahraCrypto

/**
 * Unified Memory Manager — مدير الذاكرة الموحّد لزهرة
 * 
 * Manages 3 tiers of memory:
 * 
 * 1. 🧠 Conversation Memory (ذاكرة المحادثة)
 *    - Short-term, current session only
 *    - Ring buffer of recent turns (20 turns max)
 *    - Tracks entities, context, corrections
 *    
 * 2. 📚 Topic Memory (ذاكرة المواضيع)
 *    - Cross-session topic tracking
 *    - Remembers what was discussed and when
 *    - Persists between app restarts
 *    
 * 3. 👤 User Profile Memory (ذاكرة المستخدم)
 *    - Structured profile: name, age, gender, likes, dislikes
 *    - Auto-extracted from conversation
 *    - Persists between app restarts
 * 
 * All persistent data is stored encrypted locally.
 * Pure rule-based, zero-LLM, zero-network.
 */
class UnifiedMemoryManager(private val context: Context? = null) {

    // ========== Tier 1: Conversation Memory ==========
    private val conversationTurns = mutableListOf<ConversationTurn>()
    private val maxTurns = 20
    
    data class ConversationTurn(
        val userMessage: String,
        val zahraResponse: String,
        val intent: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun recordTurn(userMsg: String, zahraResp: String, intent: String? = null) {
        conversationTurns.add(ConversationTurn(userMsg, zahraResp, intent))
        if (conversationTurns.size > maxTurns) {
            conversationTurns.removeAt(0)
        }
        
        // Only explicit names/gender are durable: "أنا فرحان" is not a name.
        val extracted = profileExtractor.extract(userMsg)
        val nameIsExplicit = Regex("(?:اسمي|ناديني|my name is|call me)\\s+", RegexOption.IGNORE_CASE).containsMatchIn(userMsg)
        val genderIsExplicit = Regex("(?:انا|أنا)\\s+(?:ولد|بنت|رجل|راجل|امرأة|ست)\\b|i am (?:male|female)", RegexOption.IGNORE_CASE).containsMatchIn(userMsg)
        val negatedLike = Regex("(?:مش|لا|ما)\\s*(?:بحب|احب|أحب)").containsMatchIn(userMsg)
        val extraction = extracted.copy(
            name = extracted.name.takeIf { nameIsExplicit },
            gender = extracted.gender.takeIf { genderIsExplicit },
            likes = if (negatedLike) emptyList() else extracted.likes
        )
        val hasSafeExtraction = extraction.name != null || extraction.age != null || extraction.gender != null ||
            extraction.likes.isNotEmpty() || extraction.dislikes.isNotEmpty() || extraction.hobbies.isNotEmpty() ||
            extraction.location != null || extraction.work != null || extraction.education != null
        if (hasSafeExtraction) {
            val before = userProfile
            try {
                mergeExtractionIntoProfile(extraction)
                lastPersistenceError = null
            } catch (e: SecurityException) {
                // Fail closed without turning an already-executed tool into a reported failure.
                userProfile = before
                lastPersistenceError = e.message ?: "تعذر حفظ الملف الشخصي مشفرًا"
            }
        }
        
        // Detect topics
        topicMemory.detectTopics(userMsg)
        
        // Update message count
        userProfile = userProfile.copy(
            totalMessages = userProfile.totalMessages + 1,
            lastInteraction = System.currentTimeMillis()
        )
    }

    fun getRecentTurns(n: Int = 5): List<ConversationTurn> {
        return conversationTurns.takeLast(n)
    }

    fun clearConversation() {
        conversationTurns.clear()
    }

    fun getConversationLength(): Int = conversationTurns.size

    // ========== Tier 2: Topic Memory ==========
    val topicMemory = TopicMemory()

    fun startNewSession() {
        topicMemory.startNewSession()
        conversationTurns.clear()
        userProfile = userProfile.copy(
            totalSessions = userProfile.totalSessions + 1,
            relationshipLevel = calculateRelationshipLevel()
        )
    }

    fun getPreviousTopics(): List<TopicMemory.TopicEntry> {
        return topicMemory.getPreviousSessionTopics()
    }

    fun getTopicSummary(): String = topicMemory.getTopicSummary()

    // ========== Tier 3: User Profile Memory ==========
    private val profileExtractor = UserProfileExtractor()
    var lastPersistenceError: String? = null
        private set

    /**
     * تقرير صحة القراءة: التلف أو الترحيل المنقطع لا يُخفى في صمت.
     * فشل فك التشفير لا يُسجّل هنا لأنه يفشل مغلقًا برفع `SecurityException`.
     */
    data class LoadIssue(val area: String, val kind: String, val detail: String)

    private val loadIssues = mutableListOf<LoadIssue>()
    val lastLoadIssues: List<LoadIssue> get() = loadIssues.toList()

    private fun noteIssue(area: String, kind: String, detail: String) {
        loadIssues.add(LoadIssue(area, kind, detail.take(160)))
    }

    fun clearLoadIssues() = loadIssues.clear()
    var userProfile = UserProfile()
        private set

    /**
     * Merge auto-extracted data into the user profile
     */
    private fun mergeExtractionIntoProfile(extraction: UserProfileExtractor.ExtractionResult) {
        userProfile = userProfile.copy(
            name = extraction.name ?: userProfile.name,
            age = extraction.age ?: userProfile.age,
            gender = extraction.gender ?: userProfile.gender,
            likes = if (extraction.likes.isNotEmpty()) {
                (userProfile.likes + extraction.likes).distinct().take(20)
            } else userProfile.likes,
            dislikes = if (extraction.dislikes.isNotEmpty()) {
                (userProfile.dislikes + extraction.dislikes).distinct().take(20)
            } else userProfile.dislikes,
            hobbies = if (extraction.hobbies.isNotEmpty()) {
                (userProfile.hobbies + extraction.hobbies).distinct().take(15)
            } else userProfile.hobbies,
            location = extraction.location ?: userProfile.location,
            work = extraction.work ?: userProfile.work,
            education = extraction.education ?: userProfile.education,
            usesDialect = userProfile.usesDialect || extraction.dialectHints.isNotEmpty(),
            dialectType = extraction.dialectHints.firstOrNull() ?: userProfile.dialectType,
            lastInteraction = System.currentTimeMillis(),
            relationshipLevel = calculateRelationshipLevel()
        )
        
        // Persist profile
        saveProfile()
    }

    /**
     * Get a friendly summary of what Zahra knows about the user
     */
    fun getUserProfileSummary(): String = userProfile.toFriendlySummary()

    /**
     * Get user's name (or null if unknown)
     */
    fun getUserName(): String? = userProfile.name

    /**
     * Get user's gender
     */
    fun getUserGender(): UserProfile.Gender? = userProfile.gender

    /**
     * Check if this is a first-time user
     */
    fun isFirstTimeUser(): Boolean = userProfile.totalSessions == 0 && userProfile.name == null

    /**
     * Check if user is returning (has previous sessions)
     */
    fun isReturningUser(): Boolean = userProfile.totalSessions > 1

    private fun calculateRelationshipLevel(): UserProfile.RelationshipLevel {
        return when {
            userProfile.totalSessions >= 30 -> UserProfile.RelationshipLevel.CLOSE
            userProfile.totalSessions >= 10 -> UserProfile.RelationshipLevel.FAMILIAR
            userProfile.totalSessions >= 3 -> UserProfile.RelationshipLevel.ACQUAINTANCE
            else -> UserProfile.RelationshipLevel.NEW
        }
    }

    // ========== Persistence ==========

    private val prefs: SharedPreferences? = context?.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE)

    /**
     * Save all persistent memory to local storage
     */
    fun saveAll() {
        saveProfile()
        saveTopics()
    }

    /**
     * Load all persistent memory from local storage
     */
    fun loadAll() {
        loadIssues.clear()
        loadProfile()
        loadTopics()
    }

    private fun saveProfile() {
        val prefs = prefs ?: return
        val json = JSONObject().apply {
            put("name", userProfile.name ?: JSONObject.NULL)
            put("age", userProfile.age ?: JSONObject.NULL)
            put("gender", userProfile.gender?.name ?: JSONObject.NULL)
            put("likes", org.json.JSONArray(userProfile.likes))
            put("dislikes", org.json.JSONArray(userProfile.dislikes))
            put("hobbies", org.json.JSONArray(userProfile.hobbies))
            put("favoriteTopics", org.json.JSONArray(userProfile.favoriteTopics))
            put("location", userProfile.location ?: JSONObject.NULL)
            put("work", userProfile.work ?: JSONObject.NULL)
            put("education", userProfile.education ?: JSONObject.NULL)
            put("preferredTone", userProfile.preferredTone ?: JSONObject.NULL)
            put("preferredLength", userProfile.preferredLength ?: JSONObject.NULL)
            put("usesDialect", userProfile.usesDialect)
            put("dialectType", userProfile.dialectType ?: JSONObject.NULL)
            put("firstInteraction", userProfile.firstInteraction)
            put("lastInteraction", userProfile.lastInteraction)
            put("totalSessions", userProfile.totalSessions)
            put("totalMessages", userProfile.totalMessages)
            put("relationshipLevel", userProfile.relationshipLevel.name)
        }
        writeEncrypted("user_profile_v3", json.toString())
    }

    private fun loadProfile() {
        val prefs = prefs ?: return
        val jsonStr = readEncrypted("user_profile_v3") ?: return
        try {
            val json = JSONObject(jsonStr)
            userProfile = UserProfile(
                name = json.optString("name", null),
                age = if (json.has("age") && !json.isNull("age")) json.getInt("age") else null,
                gender = try { 
                    json.optString("gender", null)?.let { UserProfile.Gender.valueOf(it) }
                } catch (e: Exception) { null },
                likes = jsonArrayToList(json.optJSONArray("likes")),
                dislikes = jsonArrayToList(json.optJSONArray("dislikes")),
                hobbies = jsonArrayToList(json.optJSONArray("hobbies")),
                favoriteTopics = jsonArrayToList(json.optJSONArray("favoriteTopics")),
                location = json.optString("location", null),
                work = json.optString("work", null),
                education = json.optString("education", null),
                preferredTone = json.optString("preferredTone", null),
                preferredLength = json.optString("preferredLength", null),
                usesDialect = json.optBoolean("usesDialect", false),
                dialectType = json.optString("dialectType", null),
                firstInteraction = json.optLong("firstInteraction", System.currentTimeMillis()),
                lastInteraction = json.optLong("lastInteraction", System.currentTimeMillis()),
                totalSessions = json.optInt("totalSessions", 0),
                totalMessages = json.optInt("totalMessages", 0),
                relationshipLevel = try {
                    UserProfile.RelationshipLevel.valueOf(json.optString("relationshipLevel", "NEW"))
                } catch (e: Exception) { UserProfile.RelationshipLevel.NEW }
            )
        } catch (e: SecurityException) {
            // فشل فك التشفير/التحقق: يفشل مغلقًا ولا يُستبدل بنص واضح ولا يُبتلع.
            throw e
        } catch (e: Exception) {
            // بيانات تالفة: نبدأ بحالة نظيفة، لكن نُبلّغ الواجهة بدل الكتمان.
            noteIssue("الملف الشخصي", "تلف بيانات", e.message ?: e.javaClass.simpleName)
        }
    }

    private fun saveTopics() {
        val prefs = prefs ?: return
        writeEncrypted("topics_v3", topicMemory.toJsonString())
    }

    private fun loadTopics() {
        val prefs = prefs ?: return
        val topicsJson = readEncrypted("topics_v3") ?: return
        if (!topicMemory.loadFromJson(topicsJson))
            noteIssue("المواضيع", "تلف بيانات", "تعذر تحليل بيانات المواضيع المحفوظة")
    }

    private fun writeEncrypted(key: String, plain: String) {
        val storage = prefs ?: return
        // Two stages: commit and verify ciphertext before removing a legacy plaintext key.
        val encrypted = ZahraCrypto.encrypt(plain)
        if (!storage.edit().putString(key + "_encrypted", encrypted).commit() ||
            storage.getString(key + "_encrypted", null) != encrypted)
            throw SecurityException("تعذر حفظ الذاكرة المشفرة أو التحقق منها")
        removeLegacy(storage, key)
    }

    private fun removeLegacy(storage: SharedPreferences, key: String) {
        if (storage.contains(key) && (!storage.edit().remove(key).commit() || storage.contains(key)))
            throw SecurityException("تعذر إزالة نسخة الذاكرة القديمة")
    }

    private fun readEncrypted(key: String): String? {
        val storage = prefs ?: return null
        val encrypted = storage.getString(key + "_encrypted", null)
        if (encrypted != null) {
            val plain = ZahraCrypto.decrypt(encrypted)
            // Complete an interrupted migration only after authenticated decryption.
            removeLegacy(storage, key)
            return plain
        }
        val legacy = storage.getString(key, null) ?: return null
        writeEncrypted(key, legacy)
        return legacy
    }

    private fun jsonArrayToList(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { 
            try { array.getString(it) } catch (e: Exception) { null }
        }
    }

    // ========== Clear / Reset ==========

    /**
     * Clear all memory (user requested)
     */
    fun clearAll(): Boolean {
        if (prefs != null && !prefs.edit().clear().commit()) return false
        conversationTurns.clear()
        topicMemory.clear()
        userProfile = UserProfile()
        lastPersistenceError = null
        loadIssues.clear()
        return true
    }

    /**
     * Get a full memory dump for debugging
     */
    fun getMemoryDump(): String {
        val sb = StringBuilder()
        sb.appendLine("=== 🧠 زهرة Memory Dump ===")
        sb.appendLine()
        sb.appendLine("📌 Conversation Turns: ${conversationTurns.size}/$maxTurns")
        conversationTurns.takeLast(3).forEach {
            sb.appendLine("  User: ${it.userMessage.take(50)}")
            sb.appendLine("  Zahra: ${it.zahraResponse.take(50)}")
        }
        sb.appendLine()
        sb.appendLine("📚 Topics: ${topicMemory.getAllTopics().size}")
        topicMemory.getTopTopics(5).forEach {
            sb.appendLine("  ${it.topic} (${it.mentionCount}x)")
        }
        sb.appendLine()
        sb.appendLine("👤 Profile:")
        sb.appendLine("  Name: ${userProfile.name}")
        sb.appendLine("  Age: ${userProfile.age}")
        sb.appendLine("  Gender: ${userProfile.gender?.labelAr}")
        sb.appendLine("  Sessions: ${userProfile.totalSessions}")
        sb.appendLine("  Messages: ${userProfile.totalMessages}")
        sb.appendLine("  Likes: ${userProfile.likes.take(5)}")
        sb.appendLine("  Relationship: ${userProfile.relationshipLevel.labelAr}")
        return sb.toString()
    }
}
