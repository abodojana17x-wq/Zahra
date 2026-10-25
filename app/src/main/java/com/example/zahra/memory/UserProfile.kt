package com.example.zahra.memory

import com.example.zahra.model.FactCategory

/**
 * Structured User Profile — بروفايل المستخدم المُنظّم
 * 
 * Represents everything Zahra knows about the user in a structured format.
 * Auto-extracted from conversation + manually stored facts.
 * 
 * Categories:
 * - Identity: name, age, gender
 * - Preferences: likes, dislikes, communication style
 * - Context: location, work, education
 * - Personality traits observed
 */
data class UserProfile(
    // Identity
    val name: String? = null,
    val age: Int? = null,
    val gender: Gender? = null,
    
    // Preferences
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val hobbies: List<String> = emptyList(),
    val favoriteTopics: List<String> = emptyList(),
    
    // Context
    val location: String? = null,
    val work: String? = null,
    val education: String? = null,
    
    // Communication preferences (learned over time)
    val preferredTone: String? = null,  // "formal", "casual", "friendly"
    val preferredLength: String? = null, // "brief", "detailed"
    val usesDialect: Boolean = false,
    val dialectType: String? = null,    // "egyptian", "gulf", "levantine", etc.
    
    // Metadata
    val firstInteraction: Long = System.currentTimeMillis(),
    val lastInteraction: Long = System.currentTimeMillis(),
    val totalSessions: Int = 0,
    val totalMessages: Int = 0,
    
    // Relationship
    val relationshipLevel: RelationshipLevel = RelationshipLevel.NEW
) {
    enum class Gender(val labelAr: String) {
        MALE("ذكر"),
        FEMALE("أنثى"),
        UNKNOWN("غير محدد")
    }
    
    enum class RelationshipLevel(val labelAr: String, val minSessions: Int) {
        NEW("جديد", 0),
        ACQUAINTANCE("معرفة", 3),
        FAMILIAR("مألوف", 10),
        CLOSE("قريب", 30)
    }
    
    /**
     * Build a friendly summary of what Zahra knows about the user
     */
    fun toFriendlySummary(): String {
        val parts = mutableListOf<String>()
        
        if (name != null) parts.add("اسمك: $name")
        if (age != null) parts.add("عمرك: $age")
        if (gender != null && gender != Gender.UNKNOWN) parts.add("الجنس: ${gender.labelAr}")
        if (location != null) parts.add("المكان: $location")
        if (work != null) parts.add("الشغل: $work")
        if (likes.isNotEmpty()) parts.add("بتحب: ${likes.take(3).joinToString("، ")}")
        if (dislikes.isNotEmpty()) parts.add("مش بتحب: ${dislikes.take(3).joinToString("، ")}")
        if (hobbies.isNotEmpty()) parts.add("هواياتك: ${hobbies.take(3).joinToString("، ")}")
        
        return if (parts.isEmpty()) {
            "لسه مش عارفة عنك حاجة كتير. قولي اسمك وعمرك وهواياتك عشان نتعرف أكتر! 😊"
        } else {
            "🌸 اللي أعرفه عنك:\n" + parts.joinToString("\n") { "• $it" }
        }
    }
    
    /**
     * Convert to map for storage
     */
    fun toFactMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        name?.let { map["الاسم"] = it }
        age?.let { map["العمر"] = it.toString() }
        gender?.let { if (it != Gender.UNKNOWN) map["الجنس"] = it.labelAr }
        location?.let { map["المكان"] = it }
        work?.let { map["العمل"] = it }
        education?.let { map["التعليم"] = it }
        if (likes.isNotEmpty()) map["يحب"] = likes.joinToString("، ")
        if (dislikes.isNotEmpty()) map["يكره"] = dislikes.joinToString("، ")
        if (hobbies.isNotEmpty()) map["الهوايات"] = hobbies.joinToString("، ")
        if (favoriteTopics.isNotEmpty()) map["المواضيع المفضلة"] = favoriteTopics.joinToString("، ")
        map["عدد الجلسات"] = totalSessions.toString()
        map["عدد الرسائل"] = totalMessages.toString()
        return map
    }
}
