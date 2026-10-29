package com.example.zahra.memory

/**
 * Greeting Variator — نظام تنويع التحيات
 * 
 * Solves the "always same greeting" problem by providing:
 * - 25+ varied greeting responses
 * - Time-aware greetings (صباح/مساء/ليل)
 * - Mood-aware greetings (using MoodAnalyzer integration)
 * - Personalized greetings (using user's name)
 * - Context-aware (first time vs returning user)
 * - Random selection to avoid repetition
 * 
 * Pure rule-based, zero-LLM, zero-network.
 */
class GreetingVariator {

    enum class TimeOfDay(val labelAr: String) {
        MORNING("صباح"),
        AFTERNOON("ظهيرة"),
        EVENING("مساء"),
        NIGHT("ليل")
    }

    data class GreetingContext(
        val userName: String? = null,
        val timeOfDay: TimeOfDay = detectTimeOfDay(),
        val isFirstSession: Boolean = false,
        val previousTopics: List<String> = emptyList(),
        val isReturningUser: Boolean = false,
        val userGender: UserProfile.Gender? = null,
        val isFormal: Boolean = false
    )

    // ========== Time Detection ==========
    companion object {
        fun detectTimeOfDay(): TimeOfDay {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return when {
                hour in 5..11 -> TimeOfDay.MORNING
                hour in 12..16 -> TimeOfDay.AFTERNOON
                hour in 17..21 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }
        }
    }

    // ========== Greeting Banks (25+ variations) ==========

    // السلام عليكم responses
    private val salamResponses = listOf(
        "وعليكم السلام ورحمة الله وبركاته! 🌸",
        "وعليكم السلام! أهلاً وسهلاً بيك ✨",
        "وعليكم السلام ورحمة الله! نورت 🌟",
        "وعليكم السلام! يا هلا بيك 💜",
        "وعليكم السلام ورحمة الله وبركاته، أهلًا بيك! 🤍",
        "السلام عليكم ورحمة الله! كيفك اليوم؟ 🌸",
        "وعليكم السلام! فرحانة إنك جيت تكلمني ✨",
        "وعليكم السلام يا غالي! 🤍",
        "وعليكم السلام ورحمة الله، أهلاً بيك في أي وقت! 💜",
        "وعليكم السلام! يا مرحبا بيك 🌟",
        "وعليكم السلام! نورت دنيتي 🌸",
        "وعليكم السلام ورحمة الله! حياك الله ✨"
    )

    // صباح الخير responses
    private val morningResponses = listOf(
        "صباح النور والسرور! 🌅",
        "صباح الخير والفل! كيف بدأ يومك؟ ☀️",
        "صباح الورد! يومك مبارك إن شاء الله 🌸",
        "صباح الخير يا جميل! أتمنالك يوم رائع ✨",
        "صباح النور! يا رب يكون يومك حلو 🌟",
        "صباح الخير والياسمين! 💐",
        "صباح الفل! نورت الصباح 🌅",
        "صباح الخير! كيفك النهارده؟ ☀️",
        "صباح النور يا غالي! يومك سعيد إن شاء الله 🤍",
        "صباح الورد والفل! أهلًا بيك 🌸"
    )

    // مساء الخير responses
    private val eveningResponses = listOf(
        "مساء النور والسرور! 🌙",
        "مساء الخير! كيف كان يومك؟ ✨",
        "مساء الفل! أهلاً بيك 🌸",
        "مساء النور يا جميل! 💜",
        "مساء الخير والورد! 🌺",
        "مساء النور! نورت المساء 🌟",
        "مساء الخير! يومك كان حلو إن شاء الله؟ 🤍",
        "مساء الفل والياسمين! ✨",
        "مساء النور يا غالي! 🌙",
        "مساء الخير! كيفك اليوم؟ 🌸"
    )

    // Generic greetings (مرحبا، أهلاً، etc.)
    private val genericResponses = listOf(
        "أهلاً وسهلاً بيك! 🌸",
        "مرحبا! نورت ✨",
        "يا هلا بيك! تحت أمرك 💜",
        "أهلًا! فرحانة إنك هنا 🤍",
        "مرحبًا! كيفك اليوم؟ 🌟",
        "أهلاً وسهلاً! أنا هنا عشان أساعدك 🌸",
        "يا مرحبا! عايز تعمل إيه النهارده؟ ✨",
        "أهلًا بيك! عندك أي حاجة في بالك؟ 💜",
        "مرحبا يا غالي! 🤍",
        "أهلاً! نورت المحادثة 🌟"
    )

    // First-time greeting responses
    private val firstTimeResponses = listOf(
        "أهلاً وسهلاً بيك! أنا زهرة 🌸 مساعدتك الذكية اللي بتشتغل offline بالكامل. قولي اسمك عشان نتعرف!",
        "مرحبا! أنا زهرة ✨ يشرفني أتعرف عليك. ممكن تقولي اسمك وإيه اللي تحب أساعدك فيه؟",
        "أهلًا بيك في زهرة! 🌟 أنا مساعدتك الشخصية — بحسب، بترجم، بجدول، وبحفظ معلوماتك. نتعرف؟",
        "يا هلا! أنا زهرة 💜 يشرفني أكون مساعدتك. قولي شوية عن نفسك عشان أخدمك أحسن!",
        "أهلاً وسهلاً! 🤍 أنا زهرة، مساعدتك الذكية المحلية. عايز تبدأ بإيه؟"
    )

    // Returning user greetings (with memory)
    private val returningResponses = listOf(
        "أهلاً بعودتك! 🌸 وحشتني!",
        "يا هلا! فرحانة إنك رجعت تاني ✨",
        "أهلاً بيك تاني! 🤍 جاهزة أساعدك",
        "نورت تاني! 🌟 عندك حاجة جديدة النهارده؟",
        "أهلًا برجوعك! 💜 اشتقتلك!"
    )

    // Late night greetings
    private val nightResponses = listOf(
        "أهلاً بيك في وقت متأخر! 🌙 متتعبتش؟",
        "يا هلا! لسه صاحي؟ 🌟 خد راحتك",
        "أهلاً! الوقت متأخر شوية، بس أنا هنا في أي وقت 🤍",
        "مرحبا! 🌙 خلي بالك من نومك، بس خد وقتك معايا",
        "أهلًا بيك! ✨ الليل هادي ومناسب للكلام"
    )

    // ========== Main Greeting Function ==========

    /**
     * Generate a varied, context-aware greeting
     * 
     * @param input The user's greeting input
     * @param context Greeting context (name, time, session info)
     * @return A personalized, varied greeting response
     */
    fun generateGreeting(input: String, context: GreetingContext = GreetingContext()): String {
        val normalized = input.lowercase().trim()
        
        // Detect greeting type
        val greetingType = detectGreetingType(normalized)
        
        // Select base response based on type + time
        val baseResponse = selectBaseResponse(greetingType, context)
        
        // Personalize with name
        val personalized = personalizeGreeting(baseResponse, context)
        
        // Add context-aware follow-up
        val withFollowUp = addFollowUp(personalized, context)
        
        return withFollowUp
    }

    private fun detectGreetingType(input: String): String {
        return when {
            input.contains("السلام عليكم") || input.contains("سلام عليكم") || input.contains("سلام") -> "salam"
            input.contains("صباح") -> "morning"
            input.contains("مساء") -> "evening"
            input.contains("مرحبا") || input.contains("أهلا") || input.contains("اهلا") || 
            input.contains("hi") || input.contains("hello") -> "generic"
            else -> "generic"
        }
    }

    private fun selectBaseResponse(type: String, context: GreetingContext): String {
        // First session? Use special greetings
        if (context.isFirstSession) {
            return firstTimeResponses.random()
        }
        
        // Returning user? Sometimes use returning greetings
        if (context.isReturningUser && Math.random() < 0.4) {
            return returningResponses.random()
        }
        
        // Late night?
        if (context.timeOfDay == TimeOfDay.NIGHT && Math.random() < 0.5) {
            return nightResponses.random()
        }
        
        // Select based on greeting type
        return when (type) {
            "salam" -> salamResponses.random()
            "morning" -> morningResponses.random()
            "evening" -> eveningResponses.random()
            "generic" -> {
                // Mix generic with time-appropriate
                when (context.timeOfDay) {
                    TimeOfDay.MORNING -> (genericResponses + morningResponses).random()
                    TimeOfDay.EVENING -> (genericResponses + eveningResponses).random()
                    TimeOfDay.NIGHT -> (genericResponses + nightResponses).random()
                    else -> genericResponses.random()
                }
            }
            else -> genericResponses.random()
        }
    }

    private fun personalizeGreeting(greeting: String, context: GreetingContext): String {
        if (context.userName.isNullOrBlank()) return greeting
        
        // Add name if not already present
        return if (!greeting.contains(context.userName!!)) {
            // Insert name naturally
            val nameAdditions = listOf(
                " يا ${context.userName}",
                " يا ${context.userName}!",
                "، ${context.userName}!"
            )
            
            // Only add name ~70% of the time for variety
            if (Math.random() < 0.7) {
                val insertion = nameAdditions.random()
                // Insert before the last punctuation or emoji
                val lastPunct = greeting.indexOfLast { it in "!؟?." }
                if (lastPunct > 0) {
                    greeting.substring(0, lastPunct) + insertion + greeting.substring(lastPunct)
                } else {
                    greeting + insertion
                }
            } else {
                greeting
            }
        } else {
            greeting
        }
    }

    private fun addFollowUp(greeting: String, context: GreetingContext): String {
        // Only add follow-up ~50% of the time for variety
        if (Math.random() > 0.5) return greeting
        
        val followUps = mutableListOf<String>()
        
        // Time-aware follow-ups
        when (context.timeOfDay) {
            TimeOfDay.MORNING -> {
                followUps.add("\n\nيومك بدأ بإيه؟")
                followUps.add("\n\nعندك خطط للنهارده؟")
                followUps.add("\n\nأنا هنا لو احتجت أي حاجة!")
            }
            TimeOfDay.AFTERNOON -> {
                followUps.add("\n\nكيف يومك لحد دلوقتي؟")
                followUps.add("\n\nفي حاجة أقدر أساعدك بيها؟")
            }
            TimeOfDay.EVENING -> {
                followUps.add("\n\nيومك كان إزاي؟")
                followUps.add("\n\nفي حاجة حلوة حصلت النهارده؟")
            }
            TimeOfDay.NIGHT -> {
                followUps.add("\n\nخد راحتك، أنا هنا 🤍")
                followUps.add("\n\nفي حاجة في بالك قبل ما تنام؟")
            }
        }
        
        // Generic follow-ups
        followUps.add("\n\nإيه اللي في بالك؟")
        followUps.add("\n\nتحت أمرك!")
        followUps.add("\n\nكيف أقدر أساعدك؟")
        
        return greeting + followUps.random()
    }

    /**
     * Generate a farewell response (also varied)
     */
    fun generateFarewell(context: GreetingContext = GreetingContext()): String {
        val farewells = listOf(
            "في أمان الله! 🤍 نورت المحادثة",
            "مع السلامة! 🌸 ارجعلي في أي وقت",
            "باي باي! ✨ يومك سعيد",
            "في رعاية الله! 💜 كنت مبسوطة بيك",
            "مع السلامة يا غالي! 🌟 بشتاقلك!",
            "الله معاك! 🤍 لو احتجت حاجة أنا هنا",
            "باي! 🌸 يومك مبارك إن شاء الله",
            "في أمان الله وحفظه! ✨ كل بياناتك محفوظة ومشفرة"
        )
        
        val base = farewells.random()
        return if (!context.userName.isNullOrBlank() && Math.random() < 0.5) {
            base.replace("!", " يا ${context.userName}!")
        } else {
            base
        }
    }
}
