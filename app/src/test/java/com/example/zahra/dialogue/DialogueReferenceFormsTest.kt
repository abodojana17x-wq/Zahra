package com.example.zahra.dialogue

import org.junit.Assert.*
import org.junit.Test

/**
 * المرحلة 4 — صيغ الإحالة والمتابعة الموسَّعة + حد التوضيح على كل المسارات.
 * قواعد صريحة فقط؛ أي غموض يؤول إلى سؤال أو امتناع، لا إلى تخمين.
 */
class DialogueReferenceFormsTest {

    private fun stateWith(user: String, response: String = "رد", intent: String = "query_knowledge",
                         city: String? = null, topic: String? = null,
                         entities: List<String> = emptyList()): DialogueState = DialogueState().apply {
        record(user, response, intent, city, topic, entities = entities)
    }

    @Test fun sameQuestionRepeatsOnlyRepeatableIntents() {
        val resolver = ReferenceResolver()
        val weather = stateWith("الطقس في القاهرة", intent = "weather", city = "القاهرة")
        assertEquals("الطقس في القاهرة", (resolver.resolve("نفس السؤال", weather, null) as Resolution.Query).text)
        assertEquals("الطقس في القاهرة في أسوان",
            (resolver.resolve("نفس السؤال بس في أسوان", weather, null) as Resolution.Query).text)

        val smallTalk = stateWith("مرحبا")
        assertTrue(resolver.resolve("نفس السؤال بس", smallTalk, null) is Resolution.Ask)
        val empty = DialogueState()
        assertTrue(resolver.resolve("نفس السؤال بس في فرنسا", empty, null) is Resolution.Ask)
    }

    @Test fun sameCityUsesThePersistedCityOrAsks() {
        val resolver = ReferenceResolver()
        val withCity = stateWith("الطقس في القاهرة", intent = "weather", city = "القاهرة")
        assertEquals("الطقس في القاهرة", (resolver.resolve("نفس المدينة؟", withCity, null) as Resolution.Query).text)
        val without = stateWith("الطقس إيه؟", intent = "weather")
        assertTrue(resolver.resolve("نفس المدينة؟", without, null) is Resolution.Ask)
    }

    @Test fun solutionReferenceNeedsExactlyOneCandidate() {
        val resolver = ReferenceResolver()
        val single = stateWith("مشكلة في المعادلة", intent = "math", entities = listOf("2x+3=7"))
        assertEquals("ما حل 2x+3=7؟", (resolver.resolve("طب وإيه حلّه؟", single, null) as Resolution.Query).text)
        val ambiguous = stateWith("مشكلة في المعادلتين", intent = "math", entities = listOf("2x+3=7", "x-1=0"))
        assertTrue(resolver.resolve("وإيه حلّه؟", ambiguous, null) is Resolution.Ask)
        val none = stateWith("مرحبا")
        assertTrue(resolver.resolve("وإيه حلّه؟", none, null) is Resolution.Ask)
    }

    @Test fun anyCurrencyConversionAbstainsWithoutARate() {
        val resolver = ReferenceResolver()
        listOf("طب بالجنيه؟", "وبالدولار؟", "بالريال", "كم باليورو؟", "بكام بالجنيه؟").forEach {
            val result = resolver.resolve(it, DialogueState(), null)
            assertTrue("$it should abstain", result is Resolution.Abstain)
            assertTrue((result as Resolution.Abstain).text.contains("سعر صرف"))
        }
    }

    @Test fun futureReferencesNeverBecomeCurrentWeather() {
        val resolver = ReferenceResolver()
        listOf("وبكرة؟", "وبعده؟", "وبعد بكرة؟", "وبعد غد؟", "والأسبوع الجاي؟", "يوم الجمعة؟").forEach {
            val noContext = resolver.resolve(it, DialogueState(), null)
            assertTrue("$it must not be answered as current weather",
                noContext is Resolution.Unchanged || noContext is Resolution.Abstain)
        }
        val weather = stateWith("الطقس في القاهرة", intent = "weather", city = "القاهرة")
        assertTrue(resolver.resolve("وبكرة؟", weather, null) is Resolution.Abstain)
        assertTrue(resolver.resolve("وبعد بكرة؟", weather, null) is Resolution.Abstain)
    }

    @Test fun reminderCommandsAreNeverRewrittenIntoWeather() {
        val resolver = ReferenceResolver()
        val weather = stateWith("الطقس في القاهرة", intent = "weather", city = "القاهرة")
        listOf("فكرني بكرة", "ذكرني بكرة الصبح", "جدول موعد بكرة").forEach {
            assertTrue(resolver.resolve(it, weather, null) is Resolution.Unchanged)
        }
    }

    @Test fun clarificationIsBoundedOnceAndConsumedEvenOnTopicChange() {
        val state = DialogueState()
        val resolver = ReferenceResolver()
        val first = ClarificationPolicy.resolve(resolver.resolve("وإيه عاصمتها؟", state, null), state, "وإيه عاصمتها؟")
        assertTrue(first is Resolution.Ask)
        assertEquals(Slot.TOPIC, state.pendingClarification?.slot)
        // الإجابة تغيّر الموضوع: الطلب المعلّق يُستهلك ولا يبقى للدورة التالية.
        val changed = ClarificationPolicy.resolve(resolver.resolve("فكرني بالقاهرة", state, "القاهرة"), state, "فكرني بالقاهرة")
        assertTrue(changed is Resolution.Unchanged)
        assertNull(state.pendingClarification)
    }

    @Test fun legacyAmbiguousPathsAlsoEnterTheBound() {
        // كل مسار قديم (كائن توضيح، خيارات، أو وسم ambiguous) يدخل الحد.
        assertTrue(ClarificationBudget.asksForClarification(
            intent = "schedule_task", outcomeStateName = "AMBIGUOUS",
            hasClarificationPayload = true, hasQuestionMark = false, hasOptions = false))
        assertTrue(ClarificationBudget.asksForClarification(
            intent = "unknown", outcomeStateName = "AMBIGUOUS",
            hasClarificationPayload = false, hasQuestionMark = false, hasOptions = true))
        assertTrue(ClarificationBudget.asksForClarification(
            intent = "clarification", outcomeStateName = "SUPPORTED",
            hasClarificationPayload = false, hasQuestionMark = false, hasOptions = false))
        assertFalse(ClarificationBudget.asksForClarification(
            intent = "weather", outcomeStateName = "SUPPORTED",
            hasClarificationPayload = false, hasQuestionMark = true, hasOptions = false))
        assertTrue(ClarificationBudget.mustAbstain(true, true, false))
        assertFalse(ClarificationBudget.mustAbstain(true, true, true)) // إجراء نُفّذ لا يُلغى
        assertFalse(ClarificationBudget.mustAbstain(false, true, false))
        assertFalse(ClarificationBudget.mustAbstain(true, false, false))
    }

    @Test fun threeTurnsWithVariedFollowUpsNeverInventAReferent() {
        val conversations = listOf(
            Triple("معلومات عن مصر", "وإيه عاصمتها؟", "طب وإيه حلّه؟"),
            Triple("الجو في القاهرة", "وبكرة؟", "نفس المدينة؟"),
            Triple("مرحبا", "كمان؟", "عنهم؟"),
            Triple("معلومات عن فرنسا", "نفس السؤال بس في إيطاليا", "عاصمتها؟")
        )
        conversations.forEach { (first, second, third) ->
            repeat(8) {
                val state = DialogueState()
                val resolver = ReferenceResolver()
                state.record(first, "رد", if (first.contains("الجو")) "weather" else "query_knowledge",
                    if (first.contains("القاهرة")) "القاهرة" else null,
                    first.substringAfter("عن ", "").takeIf { !it.contains("الجو") }?.trim())
                val secondResult = ClarificationPolicy.resolve(resolver.resolve(second, state, null), state, second)
                state.lastWasClarification = secondResult is Resolution.Ask
                state.record(second, "رد ثانٍ", if (secondResult is Resolution.Ask) "clarification" else "unknown")
                val thirdResult = ClarificationPolicy.resolve(resolver.resolve(third, state, null), state, third)
                if (secondResult is Resolution.Ask) {
                    // لا سؤال توضيح ثانٍ متتالٍ: امتناع صريح.
                    assertTrue(thirdResult is Resolution.Abstain)
                } else {
                    assertTrue(thirdResult !is Resolution.Ask || (thirdResult is Resolution.Ask && state.lastWasClarification.not()))
                }
            }
        }
    }
}
