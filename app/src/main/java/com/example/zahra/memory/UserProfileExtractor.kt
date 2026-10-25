package com.example.zahra.memory

import com.example.zahra.core.ZahraNormalizer

/**
 * User Profile Extractor — مستخرج بيانات المستخدم التلقائي
 * 
 * Automatically extracts user profile information from natural conversation.
 * Pure rule-based, zero-LLM, zero-network.
 * 
 * Extracts:
 * - Name (اسم): "أنا أحمد", "اسمي سارة", "انا محمد"
 * - Age (عمر): "عمري 25", "أنا عندي 30 سنة", "I'm 20 years old"
 * - Gender (جنس): inferred from language patterns, explicit statements
 * - Likes (يحب): "بحب القراءة", "أنا بحب القهوة", "I like programming"
 * - Dislikes (يكره): "بكره الزحمة", "مش بحب الحر"
 * - Hobbies (هوايات): "هوايتي الرسم", "بحب ألعب كورة"
 * - Location (مكان): "أنا من مصر", "ساكن في الإسكندرية", "من السعودية"
 * - Work (عمل): "أنا مهندس", "بشتغل في البرمجة", "طالب في الجامعة"
 */
class UserProfileExtractor {

    data class ExtractionResult(
        val name: String? = null,
        val age: Int? = null,
        val gender: UserProfile.Gender? = null,
        val likes: List<String> = emptyList(),
        val dislikes: List<String> = emptyList(),
        val hobbies: List<String> = emptyList(),
        val location: String? = null,
        val work: String? = null,
        val education: String? = null,
        val dialectHints: List<String> = emptyList(),
        val hasExtraction: Boolean = false
    ) {
        fun isEmpty() = !hasExtraction
    }

    /**
     * Extract profile data from user input
     */
    fun extract(input: String): ExtractionResult {
        val normalized = ZahraNormalizer.normalize(input).lowercase()
        val clean = input.trim()
        
        val name = extractName(clean, normalized)
        val age = extractAge(clean, normalized)
        val gender = extractGender(clean, normalized)
        val likes = extractLikes(clean, normalized)
        val dislikes = extractDislikes(clean, normalized)
        val hobbies = extractHobbies(clean, normalized)
        val location = extractLocation(clean, normalized)
        val work = extractWork(clean, normalized)
        val education = extractEducation(clean, normalized)
        val dialect = detectDialect(clean, normalized)
        
        val hasExtraction = name != null || age != null || gender != null ||
            likes.isNotEmpty() || dislikes.isNotEmpty() || hobbies.isNotEmpty() ||
            location != null || work != null || education != null
        
        return ExtractionResult(
            name = name,
            age = age,
            gender = gender,
            likes = likes,
            dislikes = dislikes,
            hobbies = hobbies,
            location = location,
            work = work,
            education = education,
            dialectHints = dialect,
            hasExtraction = hasExtraction
        )
    }

    // ========== Name Extraction ==========
    private fun extractName(original: String, normalized: String): String? {
        val namePatterns = listOf(
            // Arabic patterns
            Regex("""(?:اسمي|أنا|انا)\s+([^\s،,.!؟?]{2,20})"""),
            Regex("""(?:أنا|انا)\s+(?:اسمي)\s+([^\s،,.!؟?]{2,20})"""),
            Regex("""اسمي\s+([^\s،,.!؟?]{2,20})"""),
            Regex("""ناديني\s+([^\s،,.!؟?]{2,20})"""),
            Regex("""انا\s+([أ-ي]{2,15})\s"""),
            // English patterns
            Regex("""(?:my name is|i'm|i am|call me)\s+([a-zA-Z]{2,20})""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in namePatterns) {
            val match = pattern.find(original) ?: pattern.find(normalized)
            if (match != null) {
                val name = match.groupValues[1].trim()
                // Filter out common words that aren't names
                val nonNames = setOf("زهرة", "طالب", "مهندس", "دكتور", "من", "في", "عايز", 
                    "بحب", "بكره", "عاوز", "مش", "كده", "جداً", "طيب", "كويس")
                if (name.isNotBlank() && name !in nonNames && name.length >= 2) {
                    // Capitalize first letter
                    return name.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return null
    }

    // ========== Age Extraction ==========
    private fun extractAge(original: String, normalized: String): Int? {
        val agePatterns = listOf(
            Regex("""(?:عمري|عندي|سنّي|seny)\s*(\d{1,3})\s*(?:سنة|سنوات|سنين)?"""),
            Regex("""(?:أنا|انا)\s*(?:عمري|عندي)\s*(\d{1,3})"""),
            Regex("""(?:i'm|i am)\s*(\d{1,3})\s*(?:years?\s*old)?""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,3})\s*(?:سنة|سنوات|سنين)\s*(?:عمري|عندي)""")
        )
        
        for (pattern in agePatterns) {
            val match = pattern.find(original) ?: pattern.find(normalized)
            if (match != null) {
                val age = match.groupValues[1].toIntOrNull()
                if (age != null && age in 5..120) return age
            }
        }
        return null
    }

    // ========== Gender Extraction ==========
    private fun extractGender(original: String, normalized: String): UserProfile.Gender? {
        // Explicit statements
        val maleExplicit = listOf("أنا ولد", "انا ولد", "أنا رجل", "انا راجل", "i'm male", "i'm a boy", "i'm a man")
        val femaleExplicit = listOf("أنا بنت", "انا بنت", "أنا امرأة", "انا ست", "i'm female", "i'm a girl", "i'm a woman")
        
        val lowerOrig = original.lowercase()
        if (maleExplicit.any { lowerOrig.contains(it) }) return UserProfile.Gender.MALE
        if (femaleExplicit.any { lowerOrig.contains(it) }) return UserProfile.Gender.FEMALE
        
        // Infer from Arabic verb/adjective endings
        // Female patterns in Arabic: ـة, ـتى, ـى in self-referencing
        val femaleSelfPatterns = listOf(
            Regex("""(?:أنا|انا)\s+\w+[ةى]"""),
            Regex("""(?:بحب|بكره|عايزة|عايزه|مشتاقة|تع bana|زعلانة|فرحانة|مبسوطة|تعبانة)""")
        )
        val maleSelfPatterns = listOf(
            Regex("""(?:عايز|مشتاق|زعلان|فرحان|مبسوط|تعبان|بحب|بكره)""")
        )
        
        // Count female vs male indicators
        val femaleCount = femaleSelfPatterns.count { it.containsMatchIn(original) || it.containsMatchIn(normalized) }
        val maleCount = maleSelfPatterns.count { it.containsMatchIn(original) || it.containsMatchIn(normalized) }
        
        return when {
            femaleCount > maleCount + 1 -> UserProfile.Gender.FEMALE
            maleCount > femaleCount + 1 -> UserProfile.Gender.MALE
            else -> null // Ambiguous, don't guess
        }
    }

    // ========== Likes Extraction ==========
    private fun extractLikes(original: String, normalized: String): List<String> {
        val likes = mutableListOf<String>()
        val likePatterns = listOf(
            Regex("""(?:بحب|بحب|أحب|احب|بحبّ|بعشق|بشغل|بعرف|بحب اوي|بحب جداً)\s+(.{2,40}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:أنا|انا)\s+(?:بحب|بحب|أحب|احب)\s+(.{2,40}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:i like|i love|i enjoy)\s+(.{2,40}?)(?:[.,!?\n]|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in likePatterns) {
            val matches = pattern.findAll(original) + pattern.findAll(normalized)
            for (match in matches) {
                val thing = match.groupValues[1].trim()
                    .removePrefix("ال").removePrefix("ا")
                    .trim()
                if (thing.isNotBlank() && thing.length in 2..30) {
                    likes.add(thing)
                }
            }
        }
        return likes.distinct().take(5)
    }

    // ========== Dislikes Extraction ==========
    private fun extractDislikes(original: String, normalized: String): List<String> {
        val dislikes = mutableListOf<String>()
        val dislikePatterns = listOf(
            Regex("""(?:بكره|بكرهه|أكره|اكره|بعشقش|مش بحب|مبحبش|مش طايق|مش طايقه|مش عايز)\s+(.{2,40}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:أنا|انا)\s+(?:بكره|بكرهه|أكره|اكره|مش بحب)\s+(.{2,40}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:i hate|i dislike|i don't like)\s+(.{2,40}?)(?:[.,!?\n]|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in dislikePatterns) {
            val matches = pattern.findAll(original) + pattern.findAll(normalized)
            for (match in matches) {
                val thing = match.groupValues[1].trim()
                    .removePrefix("ال").removePrefix("ا")
                    .trim()
                if (thing.isNotBlank() && thing.length in 2..30) {
                    dislikes.add(thing)
                }
            }
        }
        return dislikes.distinct().take(5)
    }

    // ========== Hobbies Extraction ==========
    private fun extractHobbies(original: String, normalized: String): List<String> {
        val hobbies = mutableListOf<String>()
        val hobbyPatterns = listOf(
            Regex("""(?:هوايتي|هواياتي)\s+(.{2,40}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:بحب|بحب)\s+(?:ألعب|العب|أقرأ|اقرا|أرسم|ارسم|أكتب|اكتب|أسبح|اسبح|أطبخ|اطبخ)\s*(.{0,30}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""في وقت فراغي\s+(.{2,40}?)(?:[،,.!؟?\n]|$)""")
        )
        
        for (pattern in hobbyPatterns) {
            val matches = pattern.findAll(original) + pattern.findAll(normalized)
            for (match in matches) {
                val hobby = match.groupValues[1].trim()
                if (hobby.isNotBlank() && hobby.length in 2..30) {
                    hobbies.add(hobby)
                }
            }
        }
        return hobbies.distinct().take(5)
    }

    // ========== Location Extraction ==========
    private fun extractLocation(original: String, normalized: String): String? {
        val locationPatterns = listOf(
            Regex("""(?:أنا من|انا من|من|ساكن في|ساكن ب|عايش في|عايش ب|من بلد|من مدينة)\s+([أ-يa-zA-Z\s]{2,25}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:i'm from|i live in)\s+([a-zA-Z\s]{2,25}?)(?:[.,!?\n]|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in locationPatterns) {
            val match = pattern.find(original) ?: pattern.find(normalized)
            if (match != null) {
                val loc = match.groupValues[1].trim()
                val nonLocations = setOf("هنا", "هناك", "البيت", "الشغل", "المدرسة", "here", "there", "home")
                if (loc.isNotBlank() && loc !in nonLocations) return loc
            }
        }
        return null
    }

    // ========== Work Extraction ==========
    private fun extractWork(original: String, normalized: String): String? {
        val workPatterns = listOf(
            Regex("""(?:أنا|انا)\s+(?:مهندس|مهندسة|دكتور|دكتورة|طبيب|طبيبة|مدرس|مدرسة|طالب|طالبة|مبرمج|مبرمجة|محاسب|محاسبة|صحفي|صحفية|كاتب|كاتبة|فنان|فنانة)"""),
            Regex("""(?:بشتغل|اشتغل|شغال|شغالة|أشتغل)\s+(?:في|بـ?)?\s*([أ-يa-zA-Z\s]{2,30}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:i work as|i'm a|i am a)\s+([a-zA-Z\s]{2,25}?)(?:[.,!?\n]|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in workPatterns) {
            val match = pattern.find(original) ?: pattern.find(normalized)
            if (match != null) {
                val work = if (match.groupValues.size > 1) match.groupValues[1].trim() else match.value.trim()
                if (work.isNotBlank()) return work
            }
        }
        return null
    }

    // ========== Education Extraction ==========
    private fun extractEducation(original: String, normalized: String): String? {
        val eduPatterns = listOf(
            Regex("""(?:أنا|انا)\s+(?:طالب|طالبة)\s+(?:في)\s+([أ-يa-zA-Z\s]{2,30}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:بدرس|ادرس|أدرس)\s+(?:في)?\s*([أ-يa-zA-Z\s]{2,30}?)(?:[،,.!؟?\n]|$)"""),
            Regex("""(?:خريج|خريجة)\s+([أ-يa-zA-Z\s]{2,30}?)(?:[،,.!؟?\n]|$)""")
        )
        
        for (pattern in eduPatterns) {
            val match = pattern.find(original) ?: pattern.find(normalized)
            if (match != null) {
                val edu = match.groupValues[1].trim()
                if (edu.isNotBlank()) return edu
            }
        }
        return null
    }

    // ========== Dialect Detection ==========
    private fun detectDialect(original: String, normalized: String): List<String> {
        val hints = mutableListOf<String>()
        
        // Egyptian dialect markers
        val egyptian = listOf("عايز", "عايزة", "إيه", "ايه", "أوي", "اوي", "جامد", "بص", "يا جماعة", "كده", "خلاص")
        // Gulf dialect markers
        val gulf = listOf("وش", "أبغى", "ابي", "شلون", "ليش", "زين", "هلا", "كيفك", "بعد")
        // Levantine dialect markers
        val levantine = listOf("بدّي", "بدي", "شو", "ليش", "هلق", "كيف", "منيح", "كتير")
        
        val lowerOrig = original.lowercase()
        if (egyptian.any { lowerOrig.contains(it) }) hints.add("egyptian")
        if (gulf.any { lowerOrig.contains(it) }) hints.add("gulf")
        if (levantine.any { lowerOrig.contains(it) }) hints.add("levantine")
        
        return hints
    }
}
