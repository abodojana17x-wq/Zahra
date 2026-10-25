package com.example.zahra.intelligence.mood

import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraResponse
import com.example.zahra.personality.CommunicationTone
import com.example.zahra.personality.PersonalityPreferences

/**
 * Mood-Adaptive Composed Response Builder
 * نظام الردود المركّبة المتكيفة مع المزاج
 * 
 * Builds responses from multiple layers, each adapting to the user's mood:
 *   Layer 1: Opening (mood-aware greeting/acknowledgment)
 *   Layer 2: Empathy Bridge (mirrors feelings + positive bias)
 *   Layer 3: Core Answer (the actual answer/content — UNCHANGED by mood)
 *   Layer 4: Enrichment (context-dependent: dua, ayah, tip, follow-up)
 *   Layer 5: Closing (warm sign-off + next-step suggestion)
 * 
 * Key Invariant: Core Answer (Layer 3) is NEVER changed by mood.
 * Only presentation layers (1,2,4,5) adapt.
 */
class MoodAdaptiveResponseBuilder {

    private val moodAnalyzer = MoodAnalyzer()

    // ========== Empathy Templates by Mood ==========

    private val empathyOpeners = mapOf(
        MoodAnalyzer.Mood.SAD to listOf(
            "حاسّة بيك 🤍",
            "معلش، الأيام الصعبة بتعدي 🌸",
            "أنا هنا معاك 💜",
            "ربنا يفرّحك ويشرح صدرك 🤍",
            "كلنا بنمُر بأوقات صعبة، ومفيش حاجة غلط إنك تحس كده",
            "خد نفس عميق… أنا جنباك 💜"
        ),
        MoodAnalyzer.Mood.LONELY to listOf(
            "أنا هنا معاك، مش لوحدك 🤍",
            "محدش يستاهل يحس بالوحدة، وأنا مبسوطة إنك كلمتني 💜",
            "ربنا قريب منك، وأنا كمان هنا 🌸",
            "حتى لو محدش جنبك دلوقتي، فاكر إن في ناس بتحبك 🤍"
        ),
        MoodAnalyzer.Mood.TIRED to listOf(
            "خد راحتك، التعب حق 🤍",
            "جسمك محتاج راحة، اسمع كلامه 💜",
            "ربنا يعطيك عافية 🌸",
            "مش لازم تعمل كل حاجة النهارده… خطوة خطوة 💪"
        ),
        MoodAnalyzer.Mood.ANXIOUS to listOf(
            "خد نفس عميق… كل حاجة هتبقى تمام 🤍",
            "القلق صعب بس مؤقت، وأنا هنا أساعدك 💜",
            "ربنا لا يكلّف نفساً إلا وسعها 🌸",
            "خلينا نفكر سوا بهدوء… خطوة خطوة 🤍"
        ),
        MoodAnalyzer.Mood.CONFUSED to listOf(
            "طبيعي تحس بالحيرة، خليني أوضّحلك 🤍",
            "مفيش مشكلة، هنحلها سوا 💜",
            "خلّيني أبسّطلك الموضوع… 🌸",
            "سؤال كويس! خليني أجاوبك بوضوح 💡"
        ),
        MoodAnalyzer.Mood.ANGRY to listOf(
            "فاهمة إنك مضغوط… خد وقتك 🤍",
            "حقك تزعل، بس خليني أساعدك 💜",
            "خلينا نحل الموضوع بهدوء 🌸",
            "أنا سامعاك… عايز تقولي أكتر؟ 🤍"
        ),
        MoodAnalyzer.Mood.HAPPY to listOf(
            "يا فرحتك! 🎉",
            "ده خبر حلو أوي! 😊",
            "ربنا يديمها عليك يا رب! ✨",
            "مبسوطة أوي عشانك! 🌟",
            "الحمدلله! ده شيء جميل ✨"
        ),
        MoodAnalyzer.Mood.GRATEFUL to listOf(
            "العفو! ده واجبي 🤍",
            "مبسوطة إني قدرت أساعدك 😊",
            "ربنا يبارك فيك! 🌸",
            "ده أقل واجب 💜"
        ),
        MoodAnalyzer.Mood.MOTIVATED to listOf(
            "يلا بينا! 💪",
            "حماسك حلو أوي! 🔥",
            "ماشاء الله عليك! ✨",
            "أنا معاك خطوة بخطوة! 🌟"
        ),
        MoodAnalyzer.Mood.CURIOUS to listOf(
            "سؤال ممتاز! 🤔",
            "خليني أشرحلك… 💡",
            "سؤال ذكي! 🌟",
            "أنا مبسوطة إنك بتسأل، خليني أقولك… 📚"
        ),
        MoodAnalyzer.Mood.URGENT to listOf(
            "تمام، هجاوبك بسرعة ⚡",
            "خليني أساعدك دلوقتي فوراً ⚡",
            "حاضر، بسرعة ⚡"
        ),
        MoodAnalyzer.Mood.NEUTRAL to listOf(
            "أهلاً! 😊",
            "حاضر 🤍",
            "تحت أمرك ✨",
            "أنا هنا 💜"
        )
    )

    // ========== Duas & Comfort Phrases by Mood ==========

    private val comfortDuas = mapOf(
        MoodAnalyzer.Mood.SAD to listOf(
            "💡 دعاء الهمّ والحزن:\n\"اللهم إني أعوذ بك من الهمّ والحزن، والعجز والكسل، والجبن والبخل، وضلع الدين وغلبة الرجال\"",
            "🤍 \"إن مع العسر يسراً\" — الشرح: 6",
            "🌸 \"لا تحزن إن الله معنا\" — التوبة: 40",
            "✨ تذكّر: كل ضيقة ولها فرجة، وربنا ما بيبتلينا إلا بيحبنا"
        ),
        MoodAnalyzer.Mood.ANXIOUS to listOf(
            "💡 دعاء القلق:\n\"اللهم إني أسألك نفساً مطمئنة، تؤمن بلقائك، وترضى بقضائك، وتقنع بعطائك\"",
            "🤍 \"ألا بذكر الله تطمئن القلوب\" — الرعد: 28",
            "🌸 خد نفس عميق وقل: \"حسبي الله ونعم الوكيل\"",
            "✨ \"ومن يتق الله يجعل له مخرجاً ويرزقه من حيث لا يحتسب\" — الطلاق: 2-3"
        ),
        MoodAnalyzer.Mood.ANGRY to listOf(
            "💡 النبي ﷺ قال: \"لا تغضب\" — رواه البخاري",
            "🤍 استعذ بالله من الشيطان الرجيم، وتوضأ لو تقدر",
            "🌸 \"والكاظمين الغيظ والعافين عن الناس والله يحب المحسنين\" — آل عمران: 134",
            "✨ جرّب: خد نفس عميق 5 مرات، واشرب مية"
        ),
        MoodAnalyzer.Mood.LONELY to listOf(
            "🤍 \"وإذا سألك عبادي عني فإني قريب\" — البقرة: 186",
            "💡 النبي ﷺ قال: \"ما من مسلم يغرس غرساً إلا كان له صدقة\" — رواه مسلم",
            "🌸 تذكّر: ربنا أقرب ليك من أي حد تاني",
            "✨ جرّب: اكتب 3 حاجات حلوة حصلتلك النهارده — حتى لو صغيرة"
        ),
        MoodAnalyzer.Mood.TIRED to listOf(
            "🤍 \"لا يكلف الله نفساً إلا وسعها\" — البقرة: 286",
            "💡 النبي ﷺ قال: \"إن لبدنك عليك حقاً\" — رواه البخاري",
            "🌸 خد قسط من الراحة — ربنا يحب العبد الرفيق بنفسه"
        ),
        MoodAnalyzer.Mood.CONFUSED to listOf(
            "💡 \"ربّ اشرح لي صدري ويسّر لي أمري\" — طه: 25-26",
            "🤍 استخر الله واسأل أهل العلم في الحاجات المهمة"
        )
    )

    // ========== Closing Suggestions by Mood ==========

    private val closingSuggestions = mapOf(
        MoodAnalyzer.Mood.SAD to listOf(
            "\n\nتحب أساعدك في حاجة تانية؟ أنا هنا 💜",
            "\n\nلو حابب أقولك دعاء تاني أو أحكيلك قصة حلوة، قولي 🤍",
            "\n\nخد وقتك… وأنا هنا لما تحتاجني 🌸"
        ),
        MoodAnalyzer.Mood.LONELY to listOf(
            "\n\nتحب نتكلم أكتر؟ أنا مبسوطة بيك 🤍",
            "\n\nلو حابب أقولك حكمة أو قصة، قولي 💜"
        ),
        MoodAnalyzer.Mood.ANXIOUS to listOf(
            "\n\nلو في حاجة معينة قلقانك، قولي نفكر فيها سوا 🤍",
            "\n\nتحب أعملك تذكير بشيء يريحك؟ 💜"
        ),
        MoodAnalyzer.Mood.ANGRY to listOf(
            "\n\nتحب تكمل كلام؟ أنا سامعاك 🤍",
            "\n\nلو حابب نحل المشكلة خطوة خطوة، أنا معاك 💜"
        ),
        MoodAnalyzer.Mood.HAPPY to listOf(
            "\n\nفي حاجة تانية أقدر أعملهالك؟ 😊",
            "\n\nربنا يديمها فرحة! تحب نعمل حاجة تانية؟ ✨"
        ),
        MoodAnalyzer.Mood.CURIOUS to listOf(
            "\n\nعايز تعرف أكتر عن الموضوع ده؟ 🤔",
            "\n\nفي سؤال تاني؟ أنا مستمتع بالأسئلة الحلوة دي 📚"
        ),
        MoodAnalyzer.Mood.MOTIVATED to listOf(
            "\n\nيلا نبدأ! عايز أعملك خطة أو أضيف مهمة؟ 💪",
            "\n\nتحب أنظمّلك أهدافك؟ ✨"
        ),
        MoodAnalyzer.Mood.URGENT to listOf(
            "\n\nفي حاجة تانية مستعجلة؟ ⚡",
            "\n\nتحت أمرك 🤍"
        ),
        MoodAnalyzer.Mood.NEUTRAL to listOf(
            "\n\nفي حاجة تانية أقدر أساعدك بيها؟ 😊",
            "\n\nتحت أمرك في أي وقت ✨"
        ),
        MoodAnalyzer.Mood.GRATEFUL to listOf(
            "\n\nأي حاجة تانية؟ أنا سعيدة بمساعدتك 😊",
            "\n\nفي أي وقت تحتاجني، أنا هنا 🤍"
        ),
        MoodAnalyzer.Mood.TIRED to listOf(
            "\n\nخد راحتك… وأنا هنا لما تحتاجني 🤍",
            "\n\nتحب أعملك تذكير تنام بدري؟ 😴"
        ),
        MoodAnalyzer.Mood.CONFUSED to listOf(
            "\n\nوضحّتلك؟ لو في حاجة مش واضحة قولي 🤍",
            "\n\nتحب أشرحلِك بطريقة تانية؟ 💡"
        )
    )

    // ========== Main Build Function ==========

    /**
     * Build a mood-adaptive composed response
     * 
     * @param coreResponse The original ZahraResponse (core content — NEVER modified)
     * @param userInput The raw user input
     * @param personality User's personality preferences
     * @return A new ZahraResponse with mood-adapted presentation layers
     */
    fun buildAdaptiveResponse(
        coreResponse: ZahraResponse,
        userInput: String,
        personality: PersonalityPreferences
    ): ZahraResponse {
        // Don't modify tool payloads, clarifications, or plans — they need their original structure
        if (coreResponse.toolPayload != null || coreResponse.clarification != null || 
            coreResponse.executionPlan != null || coreResponse.deepThinking != null) {
            // Just prepend a brief mood-aware opener to tool responses
            val mood = moodAnalyzer.analyze(userInput)
            val briefOpener = getBriefMoodOpener(mood.primaryMood, personality)
            
            val adaptedText = if (briefOpener.isNotEmpty() && coreResponse.text.isNotBlank()) {
                "$briefOpener\n\n${coreResponse.text}"
            } else {
                coreResponse.text
            }
            
            return coreResponse.copy(text = adaptedText)
        }
        
        val mood = moodAnalyzer.analyze(userInput)
        
        // Build composed response layers
        val layers = mutableListOf<String>()
        
        // Layer 1: Mood-aware opening (skip for urgent)
        if (mood.urgencyLevel != MoodAnalyzer.UrgencyLevel.CRITICAL) {
            val opener = getEmpathyOpener(mood.primaryMood, personality)
            if (opener.isNotEmpty()) layers.add(opener)
        }
        
        // Layer 2: Empathy bridge (only for emotional moods)
        if (mood.primaryMood in setOf(
            MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.LONELY, 
            MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.ANGRY,
            MoodAnalyzer.Mood.TIRED
        )) {
            val bridge = getEmpathyBridge(mood, personality)
            if (bridge.isNotEmpty()) layers.add(bridge)
        }
        
        // Layer 3: Core answer (UNCHANGED)
        if (coreResponse.text.isNotBlank()) {
            layers.add(coreResponse.text)
        }
        
        // Layer 4: Enrichment (dua/tip for emotional moods, follow-up for curious)
        val enrichment = getEnrichment(mood, coreResponse)
        if (enrichment.isNotEmpty() && mood.urgencyLevel == MoodAnalyzer.UrgencyLevel.LOW) {
            layers.add(enrichment)
        }
        
        // Layer 5: Closing suggestion
        val closing = getClosing(mood)
        if (closing.isNotEmpty() && mood.urgencyLevel != MoodAnalyzer.UrgencyLevel.CRITICAL) {
            layers.add(closing)
        }
        
        val composedText = layers.joinToString("\n\n")
        
        return coreResponse.copy(text = composedText)
    }

    // ========== Layer Builders ==========

    private fun getEmpathyOpener(mood: MoodAnalyzer.Mood, personality: PersonalityPreferences): String {
        val openers = empathyOpeners[mood] ?: empathyOpeners[MoodAnalyzer.Mood.NEUTRAL]!!
        
        // Adjust by personality tone
        val filtered = when (personality.tone) {
            CommunicationTone.SERIOUS_DIRECT -> {
                // Remove emoji-heavy openers for serious tone
                openers.filter { it.count { c -> c.code > 0x1F000 } <= 1 }
                    .ifEmpty { openers }
            }
            CommunicationTone.PROFESSIONAL -> {
                openers.filter { !it.contains("🤍") && !it.contains("💜") }
                    .ifEmpty { openers }
            }
            CommunicationTone.PLAYFUL -> openers // Keep all
            else -> openers // Keep all
        }
        
        return filtered.random()
    }

    private fun getBriefMoodOpener(mood: MoodAnalyzer.Mood, personality: PersonalityPreferences): String {
        return when (mood) {
            MoodAnalyzer.Mood.SAD -> "🤍"
            MoodAnalyzer.Mood.HAPPY -> "😊"
            MoodAnalyzer.Mood.URGENT -> "⚡"
            MoodAnalyzer.Mood.ANXIOUS -> "🤍"
            MoodAnalyzer.Mood.ANGRY -> "🤍"
            MoodAnalyzer.Mood.CURIOUS -> "💡"
            MoodAnalyzer.Mood.MOTIVATED -> "💪"
            else -> ""
        }
    }

    private fun getEmpathyBridge(mood: MoodAnalyzer.MoodResult, personality: PersonalityPreferences): String {
        // Positive bias: add +0.2 above user's sentiment level
        // But only for emotional moods, and only when engagement is deep
        if (mood.engagementLevel == MoodAnalyzer.EngagementLevel.DEEP || 
            mood.engagementLevel == MoodAnalyzer.EngagementLevel.HIGH) {
            
            return when (mood.primaryMood) {
                MoodAnalyzer.Mood.SAD -> "مشاعر زي دي طبيعية جداً، والاعتراف بيها شجاعة مش ضعف."
                MoodAnalyzer.Mood.LONELY -> "الوحدة صعبة، بس تذكر إن في ناس بتهتم بيك حتى لو مش حاسس بيهم دلوقتي."
                MoodAnalyzer.Mood.ANXIOUS -> "القلق بيدل إنك إنسان بيهتم — وده شيء كويس في الأساس."
                MoodAnalyzer.Mood.ANGRY -> "حقك تحس كده. المهم إننا نلاقي حل."
                MoodAnalyzer.Mood.TIRED -> "جسمك بيقولك حاجة مهمة — اسمعه."
                else -> ""
            }
        }
        return ""
    }

    private fun getEnrichment(mood: MoodAnalyzer.MoodResult, coreResponse: ZahraResponse): String {
        // Only add duas/comfort for emotional moods with high engagement
        if (mood.engagementLevel.ordinal >= MoodAnalyzer.EngagementLevel.NORMAL.ordinal) {
            val duas = comfortDuas[mood.primaryMood]
            if (duas != null) {
                return duas.random()
            }
        }
        
        // For curious mood, offer related knowledge
        if (mood.primaryMood == MoodAnalyzer.Mood.CURIOUS) {
            return "💡 لو حابب تتعمق أكتر في الموضوع، قولي!"
        }
        
        return ""
    }

    private fun getClosing(mood: MoodAnalyzer.MoodResult): String {
        val suggestions = closingSuggestions[mood.primaryMood] ?: closingSuggestions[MoodAnalyzer.Mood.NEUTRAL]!!
        return suggestions.random()
    }

    // ========== Utility: Quick Mood Check ==========

    /**
     * Quick mood analysis without building a full response.
     * Useful for pre-processing or analytics.
     */
    fun quickAnalyze(input: String): MoodAnalyzer.MoodResult {
        return moodAnalyzer.analyze(input)
    }
}
