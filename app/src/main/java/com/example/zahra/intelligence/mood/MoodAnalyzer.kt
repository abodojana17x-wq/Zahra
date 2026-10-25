package com.example.zahra.intelligence.mood

import com.example.zahra.core.ZahraNormalizer

/**
 * Mood Analyzer — نظام تحليل المشاعر والمزاج
 * 
 * Pure rule-based, zero-LLM, zero-network Arabic sentiment and mood detection.
 * Uses a curated Arabic lexicon + contextual rules + emoji analysis.
 * 
 * Supports:
 * - 7 mood categories with confidence scores
 * - Dialect-aware keyword matching (MSA + Egyptian + Gulf + Levantine)
 * - Emoji sentiment scoring
 * - Context modifiers (negation, intensifiers, questions)
 * - Urgency detection
 * - Engagement level estimation
 * 
 * Design Principle: Honest uncertainty — if mood is ambiguous, report it as MIXED/NEUTRAL.
 */
class MoodAnalyzer {

    // ========== Mood Categories ==========
    enum class Mood(val labelAr: String, val emoji: String) {
        HAPPY("سعيد", "😊"),
        SAD("حزين", "😔"),
        ANGRY("غاضب", "😤"),
        ANXIOUS("قلق", "😰"),
        CURIOUS("فضولي", "🤔"),
        URGENT("مستعجل", "⚡"),
        NEUTRAL("محايد", "😐"),
        TIRED("متعب", "😴"),
        GRATEFUL("ممتن", "🤍"),
        LONELY("وحيد", "💔"),
        MOTIVATED("متحمس", "💪"),
        CONFUSED("مشوش", "😵")
    }

    data class MoodResult(
        val primaryMood: Mood,
        val confidence: Double,           // 0.0 - 1.0
        val secondaryMood: Mood? = null,
        val sentimentScore: Double,       // -1.0 (very negative) to +1.0 (very positive)
        val urgencyLevel: UrgencyLevel,
        val engagementLevel: EngagementLevel,
        val detectedKeywords: List<String> = emptyList(),
        val detectedEmojis: List<String> = emptyList(),
        val hasNegation: Boolean = false,
        val hasIntensifier: Boolean = false,
        val suggestedResponseStrategy: ResponseStrategy
    )

    enum class UrgencyLevel(val labelAr: String) {
        LOW("عادي"),
        NORMAL("عادي"),
        HIGH("مستعجل"),
        CRITICAL("طارئ")
    }

    enum class EngagementLevel(val labelAr: String) {
        LOW("مختصر"),       // one-word, minimal
        NORMAL("طبيعي"),    // normal conversation
        HIGH("متفاعل"),     // detailed, emotional
        DEEP("عميق")        // deep personal sharing
    }

    enum class ResponseStrategy(val labelAr: String) {
        WARM_SUPPORT("دعم دافئ"),           // sad, lonely, tired
        CALM_REASSURANCE("طمأنينة هادئة"),  // anxious, confused
        QUICK_DIRECT("سريع ومباشر"),        // urgent
        CELEBRATE("احتفال ومشاركة"),        // happy, grateful, motivated
        EDUCATE("شرح وتعليم"),              // curious
        DEESCALATE("تهدئة"),                // angry
        NORMAL("عادي")                      // neutral
    }

    // ========== Arabic Sentiment Lexicon ==========
    
    // Positive words with weights (MSA + dialects)
    private val positiveLexicon: Map<String, Double> = buildMap {
        // MSA positive
        put("سعيد", 0.8); put("سعيده", 0.8); put("سعداء", 0.85)
        put("فرح", 0.8); put("فرحان", 0.8); put("فرحانه", 0.8)
        put("مبسوط", 0.75); put("مبسوطة", 0.75)
        put("حسن", 0.5); put("حسنًا", 0.5); put("حسنا", 0.5)
        put("جميل", 0.6); put("جميلة", 0.6); put("جميله", 0.6)
        put("رائع", 0.75); put("رائعة", 0.75); put("روعة", 0.8)
        put("ممتاز", 0.8); put("ممتازة", 0.8)
        put("شكر", 0.6); put("شكرًا", 0.65); put("شكرا", 0.65); put("شكور", 0.6)
        put("حمدلله", 0.6); put("الحمدلله", 0.65); put("الحمد لله", 0.65)
        put("ماشاء الله", 0.7); put("ماشاءالله", 0.7)
        put("بارك الله", 0.7); put("بارك الله فيك", 0.75)
        put("جزاك الله", 0.7); put("جزاك الله خير", 0.75)
        put("أحب", 0.7); put("احب", 0.7); put("حبيبي", 0.6); put("حبيبتي", 0.6)
        put("نجاح", 0.75); put("نجح", 0.75); put("نجحت", 0.75); put("نجحنا", 0.75)
        put("فخور", 0.7); put("فخورة", 0.7); put("فخورين", 0.7)
        put("أمل", 0.5); put("امل", 0.5); put("متفائل", 0.65)
        put("حب", 0.6); put("حبا", 0.6); put("محب", 0.6)
        put("خير", 0.5); put("خيرًا", 0.5)
        put("تمام", 0.4); put("كويس", 0.5); put("كويسة", 0.5)
        put("حلو", 0.5); put("حلوة", 0.5); put("حلوين", 0.5)
        put("مريح", 0.5); put("راحة", 0.5); put("مرتاحة", 0.5)
        put("اطمئنيت", 0.6); put("مطمئن", 0.6)
        put("الله يبارك", 0.7); put("الله يعطيك العافية", 0.65)
        put("يسلمو", 0.5); put("يسلموا", 0.5)
        put("تسلم", 0.5); put("تسلمي", 0.5)
        put("عاشت الايادي", 0.65); put("عاشت ايديك", 0.65)
        put("مبدع", 0.7); put("مبدعة", 0.7)
        put("أحسنت", 0.7); put("احسنت", 0.7)
        put("برافو", 0.65)
        // Grateful
        put("ممتن", 0.7); put("ممتنة", 0.7); put("ممنون", 0.6); put("ممنونة", 0.6)
        put("شاكر", 0.6); put("شاكرا", 0.6); put("شاكرة", 0.6)
        // Motivated
        put("يلا", 0.4); put("يللا", 0.4); put("هيا", 0.3)
        put("متحمس", 0.7); put("متحمسة", 0.7); put("حماس", 0.65)
        put("جاهز", 0.4); put("جاهزة", 0.4)
        put("قادر", 0.5); put("اقدر", 0.5)
        put("سأنجح", 0.6); put("هنجح", 0.6)
        put("بكرة احلى", 0.55); put("بكره احلى", 0.55)
    }

    // Negative words with weights (negative = negative score)
    private val negativeLexicon: Map<String, Double> = buildMap {
        // Sad
        put("حزين", 0.75); put("حزينة", 0.75); put("حزن", 0.7)
        put("زعلان", 0.7); put("زعلانة", 0.7); put("زعل", 0.65)
        put("مكتئب", 0.8); put("اكتئاب", 0.8)
        put("ضيق", 0.6); put("ضايق", 0.6); put("ضايقه", 0.6)
        put("مهموم", 0.7); put("هم", 0.55); put("هموم", 0.65)
        put("مقهور", 0.75); put("قهر", 0.7)
        put("بكيت", 0.75); put("بكي", 0.7); put("بكاء", 0.7)
        put("وحشتني", 0.55); put("وحشة", 0.55); put("وحشني", 0.55)
        put("اشتقت", 0.5); put("اشتقتلك", 0.55)
        put("مضغوط", 0.6); put("مضغوطة", 0.6); put("ضغط", 0.5)
        put("تعبان", 0.55); put("تعبانة", 0.55); put("تعب", 0.5)
        put("مرهق", 0.55); put("مرهقة", 0.55); put("ارهاق", 0.55)
        put("ملل", 0.4); put("مللت", 0.5); put("زهقان", 0.5); put("زهقانة", 0.5)
        put("ملل", 0.4); put("ممل", 0.4)
        put("خايب", 0.6); put("خيبة", 0.6); put("خايبين", 0.6)
        put("فاشل", 0.7); put("فاشلة", 0.7); put("فشل", 0.65)
        put("مش قادر", 0.55); put("مش قادرة", 0.55)
        put("مش عارف", 0.35); put("مش عارفة", 0.35)
        put("مش لاقي", 0.5); put("مش لاقيه", 0.5)
        // Angry
        put("غضبان", 0.8); put("غضبانة", 0.8); put("غضب", 0.75)
        put("معصب", 0.75); put("عصبي", 0.65); put("عصبية", 0.65)
        put("مجنن", 0.65); put("مجنني", 0.65); put("جنني", 0.7)
        put("كراهية", 0.85); put("أكره", 0.8); put("اكره", 0.8)
        put("زفت", 0.7); put("خرى", 0.75); put("كلب", 0.5)
        put("مش طايق", 0.7); put("مش طايقة", 0.7)
        put("زهقت", 0.55); put("مللت", 0.5)
        put("حسبي الله", 0.5) // frustration
        put("كفاية", 0.5); put("خلاص", 0.3)
        // Anxious
        put("خايف", 0.7); put("خايفة", 0.7); put("خوف", 0.65)
        put("قلقان", 0.7); put("قلقانة", 0.7); put("قلق", 0.6)
        put("متوتر", 0.65); put("متوترة", 0.65); put("توتر", 0.6)
        put("مرعوب", 0.8); put("رعب", 0.75)
        put("خوفاني", 0.65); put("مرعوب", 0.75)
        put("مش مرتاح", 0.5); put("مش مرتاحة", 0.5)
        put("همي", 0.55); put("همي همي", 0.6)
        put("مستني", 0.3); put("منتظر", 0.25)
        put("محتار", 0.5); put("محتارة", 0.5); put("حيرة", 0.5)
        put("مش فاهم", 0.4); put("مش فاهمة", 0.4)
        put("تايه", 0.55); put("تايهة", 0.55); put("ضائع", 0.55)
        // Lonely
        put("لوحدي", 0.6); put("لوحديا", 0.6); put("وحيد", 0.65); put("وحيدة", 0.65)
        put("محدش", 0.45); put("محدش جنبي", 0.65); put("مفيش حد", 0.55)
        put("عزلة", 0.65); put("معزول", 0.6)
        put("ماحدش سأل", 0.6); put("ماحدش بيسأل", 0.6)
        put("محدش فاهمني", 0.65); put("محدش حاسس بيا", 0.7)
    }

    // Urgency keywords
    private val urgencyKeywords: Map<String, UrgencyLevel> = buildMap {
        put("ضروري", UrgencyLevel.HIGH)
        put("ضروري جدا", UrgencyLevel.CRITICAL)
        put("الآن", UrgencyLevel.HIGH)
        put("دلوقتي", UrgencyLevel.HIGH)
        put("فورًا", UrgencyLevel.CRITICAL)
        put("فورا", UrgencyLevel.CRITICAL)
        put("بسرعة", UrgencyLevel.HIGH)
        put("سريع", UrgencyLevel.HIGH)
        put("طارئ", UrgencyLevel.CRITICAL)
        put("طوارئ", UrgencyLevel.CRITICAL)
        put("استعجال", UrgencyLevel.HIGH)
        put("مستعجل", UrgencyLevel.HIGH)
        put("عاجل", UrgencyLevel.HIGH)
        put("الحقني", UrgencyLevel.CRITICAL)
        put("انقذني", UrgencyLevel.CRITICAL)
        put("اسعفيني", UrgencyLevel.CRITICAL)
        put("تكفَى", UrgencyLevel.HIGH)
        put("تكفى", UrgencyLevel.HIGH)
        put("ارجوك", UrgencyLevel.HIGH)
        put("ارجوكي", UrgencyLevel.HIGH)
        put("يا رب", UrgencyLevel.NORMAL) // can be urgent depending on context
        put("لازم", UrgencyLevel.HIGH)
        put("محتاج", UrgencyLevel.NORMAL)
        put("محتاجة", UrgencyLevel.NORMAL)
    }

    // Negation words (flip sentiment)
    private val negationWords = setOf(
        "مش", "مو", "ما", "لا", "ليس", "ليست", "ليسوا", "لم", "لن",
        "غير", "بلا", "دون", "محدش", "مفيش", "مش", "مين",
        "no", "not", "dont", "don't", "never", "nope"
    )

    // Intensifiers (amplify sentiment)
    private val intensifiers = setOf(
        "جدا", "جداً", "جدًا", "كتير", "كتيرًا", "كثير", "كثيرًا",
        "أوي", "اوي", "جامد", "جامدا", "مرة", "مره", "رهيب",
        "very", "so", "extremely", "really", "absolutely",
        "أبدا", "ابدا", "تمامًا", "تماما", "فعلاً", "فعلا",
        "بجد", "جد", "والله", "بالله"
    )

    // Question markers (curiosity indicator)
    private val questionMarkers = setOf(
        "إيه", "ايه", "ماذا", "ما", "كيف", "ازاي", "ليه", "لماذا", "ليش",
        "فين", "أين", "متى", "امتى", "مين", "من", "كام", "كم",
        "هل", "what", "how", "why", "where", "when", "who", "?"
    )

    // ========== Emoji Analysis ==========
    
    private val positiveEmojis = mapOf(
        "😊" to 0.6, "😄" to 0.7, "😀" to 0.6, "🥰" to 0.8, "😍" to 0.7,
        "✨" to 0.4, "🌟" to 0.5, "💫" to 0.5, "⭐" to 0.4,
        "🎉" to 0.75, "🎊" to 0.75, "🥳" to 0.8, "👏" to 0.6,
        "💪" to 0.5, "👍" to 0.5, "🙌" to 0.65,
        "❤️" to 0.7, "💜" to 0.6, "🤍" to 0.55, "💚" to 0.55, "💙" to 0.55,
        "🌸" to 0.4, "🌺" to 0.4, "🌹" to 0.5,
        "🤗" to 0.65, "😇" to 0.55, "🙂" to 0.35,
        "😂" to 0.5, "🤣" to 0.55,
        "🔥" to 0.5, "💯" to 0.6, "✅" to 0.4,
        "🤲" to 0.4, "🙏" to 0.45
    )

    private val negativeEmojis = mapOf(
        "😔" to 0.6, "😢" to 0.7, "😭" to 0.85, "😞" to 0.6, "😟" to 0.55,
        "😤" to 0.7, "😡" to 0.85, "🤬" to 0.9,
        "😰" to 0.65, "😨" to 0.7, "😱" to 0.8, "😥" to 0.55,
        "💔" to 0.75, "😫" to 0.65, "😩" to 0.6,
        "😴" to 0.3, "🥱" to 0.35, "😪" to 0.4,
        "😵" to 0.5, "🤯" to 0.5,
        "😒" to 0.5, "🙄" to 0.4, "😑" to 0.3,
        "😠" to 0.65, "👎" to 0.5,
        "🥺" to 0.55, "😿" to 0.65
    )

    // ========== Main Analysis Function ==========

    /**
     * Analyze the mood/sentiment of user input
     * 
     * @param input Raw user input text
     * @return MoodResult with primary mood, confidence, strategy, etc.
     */
    fun analyze(input: String): MoodResult {
        val normalized = ZahraNormalizer.normalize(input).lowercase()
        val words = normalized.split(Regex("[\\s،,.!?،؛]+")).filter { it.isNotBlank() }
        
        // 1. Emoji analysis
        val emojiScores = analyzeEmojis(input)
        
        // 2. Lexicon analysis
        var positiveScore = 0.0
        var negativeScore = 0.0
        val detectedKeywords = mutableListOf<String>()
        
        for (word in words) {
            val posScore = positiveLexicon[word]
            val negScore = negativeLexicon[word]
            
            if (posScore != null) {
                positiveScore += posScore
                detectedKeywords.add(word)
            }
            if (negScore != null) {
                negativeScore += negScore
                detectedKeywords.add(word)
            }
        }
        
        // Check multi-word phrases
        val phrases = extractPhrases(normalized)
        for (phrase in phrases) {
            val posScore = positiveLexicon[phrase]
            val negScore = negativeLexicon[phrase]
            if (posScore != null) {
                positiveScore += posScore
                detectedKeywords.add(phrase)
            }
            if (negScore != null) {
                negativeScore += negScore
                detectedKeywords.add(phrase)
            }
        }
        
        // Add emoji scores
        positiveScore += emojiScores.first
        negativeScore += emojiScores.second
        
        // 3. Negation detection
        val hasNegation = words.any { it in negationWords }
        if (hasNegation) {
            // Flip: negation near positive word makes it less positive
            // Simple heuristic: reduce the dominant score
            if (positiveScore > negativeScore) {
                positiveScore *= 0.3
                negativeScore += 0.2
            } else if (negativeScore > positiveScore) {
                negativeScore *= 0.5  // double negation can soften
            }
        }
        
        // 4. Intensifier detection
        val hasIntensifier = words.any { it in intensifiers }
        if (hasIntensifier) {
            val dominant = maxOf(positiveScore, negativeScore)
            if (positiveScore >= negativeScore) {
                positiveScore = dominant * 1.4
            } else {
                negativeScore = dominant * 1.4
            }
        }
        
        // 5. Question detection (curiosity)
        val isQuestion = words.any { it in questionMarkers } || input.contains("?") || input.contains("؟")
        
        // 6. Urgency detection
        val urgency = detectUrgency(normalized, words)
        
        // 7. Engagement level
        val engagement = detectEngagement(input, words)
        
        // 8. Compute sentiment score (-1.0 to +1.0)
        val totalScore = positiveScore + negativeScore
        val sentimentScore = if (totalScore > 0) {
            ((positiveScore - negativeScore) / totalScore).coerceIn(-1.0, 1.0)
        } else {
            0.0
        }
        
        // 9. Determine primary mood
        val (primaryMood, secondaryMood, moodConfidence) = determineMood(
            positiveScore, negativeScore, isQuestion, urgency, words, normalized
        )
        
        // 10. Determine response strategy
        val strategy = determineStrategy(primaryMood, urgency, sentimentScore, engagement)
        
        return MoodResult(
            primaryMood = primaryMood,
            confidence = moodConfidence,
            secondaryMood = secondaryMood,
            sentimentScore = sentimentScore,
            urgencyLevel = urgency,
            engagementLevel = engagement,
            detectedKeywords = detectedKeywords,
            detectedEmojis = extractEmojiList(input),
            hasNegation = hasNegation,
            hasIntensifier = hasIntensifier,
            suggestedResponseStrategy = strategy
        )
    }

    // ========== Helper Functions ==========

    private fun analyzeEmojis(input: String): Pair<Double, Double> {
        var positive = 0.0
        var negative = 0.0
        for ((emoji, score) in positiveEmojis) {
            if (input.contains(emoji)) positive += score
        }
        for ((emoji, score) in negativeEmojis) {
            if (input.contains(emoji)) negative += score
        }
        return Pair(positive, negative)
    }

    private fun extractEmojiList(input: String): List<String> {
        val found = mutableListOf<String>()
        for (emoji in positiveEmojis.keys + negativeEmojis.keys) {
            if (input.contains(emoji)) found.add(emoji)
        }
        return found
    }

    private fun extractPhrases(text: String): List<String> {
        val phrases = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        for (i in 0 until words.size - 1) {
            phrases.add("${words[i]} ${words[i+1]}")
            if (i < words.size - 2) {
                phrases.add("${words[i]} ${words[i+1]} ${words[i+2]}")
            }
        }
        return phrases
    }

    private fun detectUrgency(normalized: String, words: List<String>): UrgencyLevel {
        var maxUrgency = UrgencyLevel.LOW
        for ((keyword, level) in urgencyKeywords) {
            if (normalized.contains(keyword)) {
                if (level.ordinal > maxUrgency.ordinal) {
                    maxUrgency = level
                }
            }
        }
        // Exclamation marks add urgency
        val exclamationCount = normalized.count { it == '!' }
        if (exclamationCount >= 3) {
            maxUrgency = UrgencyLevel.CRITICAL
        } else if (exclamationCount >= 2 && maxUrgency.ordinal < UrgencyLevel.HIGH.ordinal) {
            maxUrgency = UrgencyLevel.HIGH
        }
        return maxUrgency
    }

    private fun detectEngagement(input: String, words: List<String>): EngagementLevel {
        val wordCount = words.size
        val hasPersonalPronouns = words.any { it in setOf("أنا", "انا", "نفسي", "حياتي", "قلبي") }
        val hasDeepSharing = input.contains("بحس") || input.contains("بشعر") || 
                            input.contains("اشعر") || input.contains("أشعر") ||
                            input.contains("قلبي") || input.contains("نفسي")
        
        return when {
            hasDeepSharing && wordCount > 10 -> EngagementLevel.DEEP
            wordCount > 20 -> EngagementLevel.HIGH
            wordCount > 8 -> EngagementLevel.NORMAL
            wordCount <= 3 -> EngagementLevel.LOW
            else -> EngagementLevel.NORMAL
        }
    }

    private fun determineMood(
        posScore: Double,
        negScore: Double,
        isQuestion: Boolean,
        urgency: UrgencyLevel,
        words: List<String>,
        normalized: String
    ): Triple<Mood, Mood?, Double> {
        val totalScore = posScore + negScore
        
        // Special cases first
        if (urgency >= UrgencyLevel.CRITICAL) {
            return Triple(Mood.URGENT, null, 0.85)
        }
        
        // Detect specific moods from keyword patterns
        val sadKeywords = setOf("حزين", "حزينة", "زعلان", "زعلانة", "بكيت", "بكي", "مكتئب", "ضايق", "ضايقه", "مقهور")
        val angryKeywords = setOf("غضبان", "غضبانة", "معصب", "أكره", "اكره", "كراهية", "مش طايق", "مش طايقة", "مجنني", "جنني")
        val anxiousKeywords = setOf("خايف", "خايفة", "قلقان", "قلقانة", "متوتر", "متوترة", "مرعوب", "محتار", "محتارة")
        val lonelyKeywords = setOf("لوحدي", "وحيد", "وحيدة", "محدش", "عزلة", "محدش فاهمني", "محدش حاسس بيا")
        val tiredKeywords = setOf("تعبان", "تعبانة", "مرهق", "مرهقة", "نعسان", "ارهاق")
        val gratefulKeywords = setOf("شكرًا", "شكرا", "ممتن", "ممتنة", "ممنون", "جزاك الله", "الحمدلله", "الحمد لله")
        val motivatedKeywords = setOf("متحمس", "متحمسة", "حماس", "هيا", "يلا", "سأنجح", "هنجح", "قادر")
        val confusedKeywords = setOf("مش فاهم", "مش فاهمة", "محتار", "محتارة", "تايه", "تايهة", "مش عارف")
        
        val wordSet = words.toSet()
        val normalizedContains = { terms: Set<String> -> terms.any { normalized.contains(it) } }
        
        // Score each mood
        val moodScores = mutableMapOf<Mood, Double>()
        
        if (normalizedContains(sadKeywords)) moodScores[Mood.SAD] = negScore * 1.2 + 0.3
        if (normalizedContains(angryKeywords)) moodScores[Mood.ANGRY] = negScore * 1.3 + 0.3
        if (normalizedContains(anxiousKeywords)) moodScores[Mood.ANXIOUS] = negScore * 1.1 + 0.25
        if (normalizedContains(lonelyKeywords)) moodScores[Mood.LONELY] = negScore * 1.15 + 0.3
        if (normalizedContains(tiredKeywords)) moodScores[Mood.TIRED] = negScore * 0.9 + 0.25
        if (normalizedContains(gratefulKeywords)) moodScores[Mood.GRATEFUL] = posScore * 1.1 + 0.3
        if (normalizedContains(motivatedKeywords)) moodScores[Mood.MOTIVATED] = posScore * 1.1 + 0.25
        if (normalizedContains(confusedKeywords)) moodScores[Mood.CONFUSED] = 0.5
        
        if (posScore > 0.3 && !moodScores.containsKey(Mood.GRATEFUL) && !moodScores.containsKey(Mood.MOTIVATED)) {
            moodScores[Mood.HAPPY] = posScore
        }
        if (isQuestion && totalScore < 0.3) {
            moodScores[Mood.CURIOUS] = 0.45
        }
        if (urgency == UrgencyLevel.HIGH) {
            moodScores[Mood.URGENT] = 0.6
        }
        
        if (moodScores.isEmpty()) {
            // Default: neutral
            return Triple(Mood.NEUTRAL, null, 0.5)
        }
        
        // Sort by score
        val sorted = moodScores.entries.sortedByDescending { it.value }
        val primary = sorted.first()
        val secondary = if (sorted.size > 1 && sorted[1].value > primary.value * 0.5) sorted[1].key else null
        
        val confidence = (primary.value / (totalScore + 0.5)).coerceIn(0.3, 0.95)
        
        return Triple(primary.key, secondary, confidence)
    }

    private fun determineStrategy(
        mood: Mood,
        urgency: UrgencyLevel,
        sentimentScore: Double,
        engagement: EngagementLevel
    ): ResponseStrategy {
        if (urgency >= UrgencyLevel.HIGH) return ResponseStrategy.QUICK_DIRECT
        
        return when (mood) {
            Mood.SAD -> ResponseStrategy.WARM_SUPPORT
            Mood.LONELY -> ResponseStrategy.WARM_SUPPORT
            Mood.TIRED -> ResponseStrategy.WARM_SUPPORT
            Mood.ANXIOUS -> ResponseStrategy.CALM_REASSURANCE
            Mood.CONFUSED -> ResponseStrategy.CALM_REASSURANCE
            Mood.ANGRY -> ResponseStrategy.DEESCALATE
            Mood.HAPPY -> ResponseStrategy.CELEBRATE
            Mood.GRATEFUL -> ResponseStrategy.CELEBRATE
            Mood.MOTIVATED -> ResponseStrategy.CELEBRATE
            Mood.CURIOUS -> ResponseStrategy.EDUCATE
            Mood.URGENT -> ResponseStrategy.QUICK_DIRECT
            Mood.NEUTRAL -> ResponseStrategy.NORMAL
        }
    }
}
