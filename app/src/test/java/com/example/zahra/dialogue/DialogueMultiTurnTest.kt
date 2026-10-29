package com.example.zahra.dialogue

import org.junit.Assert.*
import org.junit.Test

/** Independent three-turn scenarios, with eight deterministic presentation variations. */
class DialogueMultiTurnTest {
    private data class Case(val first: String, val city: String?, val topic: String?, val second: String, val kind: String)
    @Test fun twentyConversationsDoNotGuess() {
        val cases = listOf(
            Case("معلومات عن مصر", null, "مصر", "عاصمتها؟", "query"),
            Case("معلومات عن فرنسا", null, "فرنسا", "وإيه عاصمتها؟", "query"),
            Case("معلومات عن اليابان", null, "اليابان", "عاصمتها", "query"),
            Case("معلومات عن تونس", null, "تونس", "عاصمتها؟", "query"),
            Case("معلومات عن المغرب", null, "المغرب", "عاصمتها؟", "query"),
            Case("معلومات عن الجزائر", null, "الجزائر", "عاصمتها؟", "query"),
            Case("الجو في القاهرة", "القاهرة", null, "وبكرة؟", "abstain"),
            Case("الطقس في أسوان", "أسوان", null, "بكره", "abstain"),
            Case("الجو في دبي", "دبي", null, "غداً؟", "abstain"),
            Case("الطقس في الرياض", "الرياض", null, "طب بكرة؟", "abstain"),
            Case("الجو في القاهرة", "القاهرة", null, "الطقس؟", "query"),
            Case("الطقس في أسوان", "أسوان", null, "الجو ايه؟", "query"),
            Case("الجو في دبي", "دبي", null, "درجة الحرارة؟", "query"),
            Case("مرحبا", null, null, "عاصمتها؟", "ask"),
            Case("مرحبا", null, null, "عنه؟", "ask"),
            Case("مرحبا", null, null, "عنها؟", "ask"),
            Case("مرحبا", null, null, "عنهم؟", "ask"),
            Case("مرحبا", null, null, "كمان؟", "ask"),
            Case("مرحبا", null, null, "نفس السؤال بس", "ask"),
            Case("مرحبا", null, null, "طب بالجنيه؟", "abstain")
        )
        assertEquals(20, cases.size)
        cases.forEach { case -> repeat(8) { repeat ->
            val state = DialogueState(); val resolver = ReferenceResolver()
            state.record(case.first, "رد", if (case.city != null) "weather" else "query_knowledge", case.city, case.topic)
            val result = ClarificationPolicy.resolve(resolver.resolve(case.second, state, null), state, case.second)
            when (case.kind) {
                "query" -> assertTrue(result is Resolution.Query)
                "ask" -> assertTrue(result is Resolution.Ask)
                "abstain" -> assertTrue(result is Resolution.Abstain)
            }
            val core = when (result) {
                is Resolution.Query -> result.text
                is Resolution.Ask -> result.question
                is Resolution.Abstain -> result.text
                Resolution.Unchanged -> "غير معروف"
            }
            val composed = ResponseComposer.compose(ResponseBlock(core, BlockSource.CLARIFICATION),
                ResponseBlock(listOf("أهلاً", "حسنًا", "تمام", "طبعًا").random(kotlin.random.Random(repeat)), BlockSource.LOCAL))
            assertTrue(composed.text.contains(core))
            state.lastWasClarification = result is Resolution.Ask
            state.record(case.second, composed.text, if (result is Resolution.Ask) "clarification" else "unknown")
            val third = ClarificationPolicy.resolve(resolver.resolve("عنه؟", state, null), state, "عنه؟")
            if (result is Resolution.Ask) assertTrue(third is Resolution.Abstain)
        } }
    }

    @Test fun boundedStateRetainsCityProvenance() {
        val state = DialogueState()
        state.record("القاهرة", "", "weather", city = "القاهرة")
        repeat(100) { state.record("دورة $it", "", "unknown") }
        assertTrue(state.turns.size <= 21)
        val city = state.slots[Slot.CITY]!!
        assertTrue(state.turns.any { it.id == city.turn && it.user.contains("القاهرة") })
        state.record("دبي", "", "weather", city = "دبي")
        assertTrue(state.turns.size <= 21)
        assertEquals("دبي", state.slots[Slot.CITY]?.value)
    }

    @Test fun ambiguousChoiceIsConsumedAndCanResolveCapital() {
        val state = DialogueState(); val resolver = ReferenceResolver()
        state.record("مصر وفرنسا", "", "knowledge", entities = listOf("مصر", "فرنسا"))
        val ask = ClarificationPolicy.resolve(resolver.resolve("عاصمتها؟", state, null), state, "عاصمتها؟")
        assertTrue(ask is Resolution.Ask)
        assertEquals("ما عاصمة مصر؟", (resolver.resolve("مصر", state, null) as Resolution.Query).text)
        assertNull(state.pendingClarification)
    }
}
