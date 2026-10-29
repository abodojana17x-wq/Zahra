package com.example.zahra.dialogue

sealed class Resolution {
    data class Query(val text: String) : Resolution()
    data class Ask(val question: String, val options: List<String> = emptyList(), val slot: Slot = Slot.TOPIC) : Resolution()
    data class Abstain(val text: String) : Resolution()
    object Unchanged : Resolution()
}

/** Narrow rules: never turn an ambiguous reference into an executable user command. */
class ReferenceResolver {
    fun resolve(input: String, state: DialogueState, city: String?): Resolution {
        val text = input.trim().replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        val pending = state.pendingClarification
        state.pendingClarification = null // consumed even when the answer changes subject
        if (pending?.slot == Slot.CITY && city != null && isCityOnly(text, city)) {
            return Resolution.Query("${pending.original} في $city")
        }
        if (pending?.slot == Slot.TOPIC && pending.options.isNotEmpty()) {
            val choice = pending.options.singleOrNull { sameChoice(text, it) }
            if (choice != null && pending.original.contains("عاصمتها"))
                return Resolution.Query("ما عاصمة $choice؟")
            if (choice != null && Regex("^(?:و|طب\\s*)?(?:احكيلي |حدثني |معلومات )?عن(?:ه|ها|هم)[؟?]?$ ".trim()).matches(pending.original.trim()))
                return Resolution.Query("معلومات عن $choice")
        }
        if (Regex("(فكرني|ذكرني|نبهني|جدول|موعد|تذكير|منبه|احفظ|اضف|أضف|احذف)").containsMatchIn(text)) return Resolution.Unchanged
        val weather = Regex("(طقس|الجو|حرار[هة]|مطر)").containsMatchIn(text)
        val future = Regex("(بكر[هة]|غدا|بعد غد|بعد بكر[هة]|الاسبوع القادم|الأسبوع القادم|الاسبوع الجاي|الأسبوع الجاي|الشهر القادم|يوم الجمعه|يوم الجمعة|\\d{4}-\\d{2}-\\d{2})").containsMatchIn(text)
        val shortFollowup = Regex("^(?:و|طب\\s*)?(?:بعد\\s+)?(?:بكر[هة]|غدا)[؟?]?$ ".trim()).matches(text)
        if (future && (weather || state.lastRoute == "weather" && shortFollowup))
            return Resolution.Abstain("لا أملك توقعًا موثوقًا لطقس الغد أو تاريخ مستقبلي؛ المتاح هنا قراءة الطقس الحالية فقط.")
        if (weather && city == null && state.slots[Slot.CITY] == null)
            return Resolution.Ask("الطقس في أي مدينة؟ القاهرة أم الإسكندرية؟", listOf("القاهرة", "الإسكندرية"), Slot.CITY)
        if (weather && city == null && state.slots[Slot.CITY] != null)
            return Resolution.Query("$text في ${state.slots[Slot.CITY]!!.value}")
        // أي تحويل عملة يحتاج مصدر صرف محدّثًا لا نملكه؛ لا يُخترع سعر.
        val currencyFollowup = Regex("^(?:و|طب|طيب|كم)?\\s*ب(?:الجنيه|الدولار|اليورو|الريال|الدرهم)[؟?]?$ ".trim()).matches(text) ||
            Regex("^بكام\\s+ب(?:الجنيه|الدولار|اليورو|الريال)[؟?]?$ ".trim()).matches(text)
        if (currencyFollowup)
            return Resolution.Abstain("لا أملك سعر صرف موثوقًا لتحويل المبلغ دون مصدر محدث.")
        // طلب صريح لتكرار السؤال نفسه لا يحتاج مرجعًا جديدًا، لكن فقط للأسئلة القابلة
        // للتكرار (طقس/أدوات/حساب)؛ لا يُعاد سؤال ترحيب أو محادثة عامة.
        val sameQuestion = Regex("^(?:و|طب\\s*)?نفس\\s+السؤال(?:\\s+بس)?\\s*(.*)$".trim()).find(text)
        if (sameQuestion != null) {
            val lastQuery = state.turns.lastOrNull()?.user?.takeIf { !it.contains("نفس السؤال") }
            val suffix = sameQuestion.groupValues[1].trim()
            if (lastQuery != null && suffix.isNotBlank())
                return Resolution.Query("$lastQuery $suffix")
            if (lastQuery != null && suffix.isBlank() && repeatableIntent(state.lastRoute))
                return Resolution.Query(lastQuery)
            // غير قابل للتكرار أو لا يوجد سؤال سابق: لا تُخمّن.
            return Resolution.Ask("تقصد إيه بـ«نفس السؤال»؟ اسألني السؤال من جديد لو تحب.", emptyList())
        }
        if (Regex("^(?:و|طب\\s*)?(?:في\\s+)?نفس\\s+المدين[هة][؟?]?$ ".trim()).matches(text)) {
            val savedCity = state.slots[Slot.CITY]?.value
                ?: return Resolution.Ask("أنهي مدينة تقصد؟", listOf("القاهرة", "الإسكندرية"), Slot.CITY)
            return Resolution.Query("الطقس في $savedCity")
        }
        val isCapitalReference = Regex("(عاصمتها|عاصمته|عاصمتهم|العاصمه|العاصمة)").containsMatchIn(text)
        val isAboutReference = Regex("(^|\\s)عن(?:ه|ها|هم)(\\s|[؟?،,.]|$)").containsMatchIn(text)
        val isSolutionReference = Regex("حل(?:ه|ها|هم)").containsMatchIn(text)
        val isBareContinue = Regex("^(?:و|طب\\s*)?كمان[؟?]?$ ".trim()).matches(text)
        val isReference = isCapitalReference || isAboutReference || isSolutionReference || isBareContinue
        if (!isReference) return Resolution.Unchanged
        val candidates = state.turns.takeLast(3).flatMap { it.entities }.distinct().takeLast(2)
        if (candidates.size == 1 && isAboutReference && Regex("^(?:و|طب\\s*)?(?:احكيلي |حدثني |معلومات )?عن(?:ه|ها|هم)[؟?]?$ ".trim()).matches(text))
            return Resolution.Query("معلومات عن ${candidates.single()}")
        // Only read-only questions have an unambiguous rewrite; never rewrite an action.
        if (isCapitalReference && candidates.size == 1 &&
            state.slots[Slot.TOPIC]?.value == candidates.single())
            return Resolution.Query("ما عاصمة ${candidates.single()}؟")
        if (isSolutionReference && candidates.size == 1)
            return Resolution.Query("ما حل ${candidates.single()}؟")
        if (isBareContinue && candidates.size == 1)
            return Resolution.Query("معلومات عن ${candidates.single()}")
        val suffix = if (candidates.isEmpty()) "" else " هل تقصد ${candidates.joinToString(" أم ")}؟"
        return Resolution.Ask("تقصد إيه بالمرجع في سؤالك؟$suffix", candidates)
    }

    /** نوايا يمكن تكرار سؤالها حرفيًا دون أن ينتج رد بلا معنى. */
    private fun repeatableIntent(intent: String?): Boolean = when (intent) {
        "weather", "math", "calculate", "math_query", "time", "date", "tool", "word_problem" -> true
        else -> false
    }
    private fun sameChoice(text: String, option: String): Boolean =
        text.trim().removeSuffix("؟").removeSuffix("?").trim().replace('أ', 'ا').replace('إ', 'ا') ==
            option.trim().replace('أ', 'ا').replace('إ', 'ا')

    private fun isCityOnly(text: String, city: String): Boolean {
        fun norm(s: String) = s.replace('إ', 'ا').replace('أ', 'ا').replace('ة', 'ه').trim()
        val reply = norm(text).removePrefix("في ").removeSuffix("؟").removeSuffix("?").trim()
        return reply == norm(city)
    }
}

object ClarificationPolicy {
    fun resolve(result: Resolution, state: DialogueState, original: String): Resolution {
        if (result !is Resolution.Ask) return result
        if (state.lastWasClarification) return Resolution.Abstain(ClarificationBudget.ABSTENTION)
        state.pendingClarification = Clarification(original, result.slot, result.options)
        return result
    }
}

/**
 * حد أسئلة التوضيح (المرحلة 4، المسار C). سؤال توضيح واحد متتالٍ فقط،
 * ويُطبَّق الحد على كل مسارات الموجّه القديمة لا على النتائج الموسومة وحدها.
 */
object ClarificationBudget {
    const val MAX_CONSECUTIVE = 1
    const val ABSTENTION = "لم أستطع تحديد المقصود، لذلك لا أستطيع الإجابة دون تخمين."

    /** هل هذا الرد طلب توضيح؟ يشمل المسارات القديمة التي لم توسم صراحةً. */
    fun asksForClarification(
        intent: String,
        outcomeStateName: String,
        hasClarificationPayload: Boolean,
        hasQuestionMark: Boolean,
        hasOptions: Boolean
    ): Boolean = intent == "clarification" ||
        hasClarificationPayload ||
        (outcomeStateName == "AMBIGUOUS" && (hasQuestionMark || hasOptions))

    /** هل يجب تحويل السؤال الثاني إلى امتناع صريح؟ */
    fun mustAbstain(previousWasClarification: Boolean, asksNow: Boolean, hasActionResult: Boolean): Boolean =
        previousWasClarification && asksNow && !hasActionResult
}
