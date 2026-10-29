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
        val future = Regex("(بكر[هة]|غدا|بعد غد|الاسبوع القادم|الأسبوع القادم|الاسبوع الجاي|الأسبوع الجاي|\\d{4}-\\d{2}-\\d{2})").containsMatchIn(text)
        val shortFollowup = Regex("^(?:و|طب\\s*)?(?:بعد\\s+)?(?:بكر[هة]|غدا)[؟?]?$ ".trim()).matches(text)
        if (future && (weather || state.lastRoute == "weather" && shortFollowup))
            return Resolution.Abstain("لا أملك توقعًا موثوقًا لطقس الغد أو تاريخ مستقبلي؛ المتاح هنا قراءة الطقس الحالية فقط.")
        if (weather && city == null && state.slots[Slot.CITY] == null)
            return Resolution.Ask("الطقس في أي مدينة؟ القاهرة أم الإسكندرية؟", listOf("القاهرة", "الإسكندرية"), Slot.CITY)
        if (weather && city == null && state.slots[Slot.CITY] != null)
            return Resolution.Query("$text في ${state.slots[Slot.CITY]!!.value}")
        if (Regex("^(?:طب\\s*)?(?:و)?بالجنيه[؟?]?$ ".trim()).matches(text))
            return Resolution.Abstain("لا أملك سعر صرف موثوقًا لتحويل المبلغ إلى جنيه دون مصدر محدث.")
        val isReference = Regex("(عاصمتها|عنه|عنها|عنهم|حل[هّ]|نفس السؤال|^كمان[؟?]?$|^طب بالجنيه)").containsMatchIn(text)
        if (!isReference) return Resolution.Unchanged
        val candidates = state.turns.takeLast(3).flatMap { it.entities }.distinct().takeLast(2)
        if (candidates.size == 1 && Regex("^(?:و|طب\\s*)?(?:احكيلي |حدثني |معلومات )?عن(?:ه|ها|هم)[؟?]?$ ".trim()).matches(text))
            return Resolution.Query("معلومات عن ${candidates.single()}")
        // Only read-only questions have an unambiguous rewrite; never rewrite an action.
        if (text.contains("عاصمتها") && candidates.size == 1 &&
            state.slots[Slot.TOPIC]?.value == candidates.single())
            return Resolution.Query("ما عاصمة ${candidates.single()}؟")
        val suffix = if (candidates.isEmpty()) "" else " هل تقصد ${candidates.joinToString(" أم ")}؟"
        return Resolution.Ask("تقصد إيه بالمرجع في سؤالك؟$suffix", candidates)
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
        if (state.lastWasClarification) return Resolution.Abstain("لم أستطع تحديد المقصود، لذلك لا أستطيع الإجابة دون تخمين.")
        state.pendingClarification = Clarification(original, result.slot, result.options)
        return result
    }
}
