package com.example.zahra.dialogue

import org.junit.Assert.*
import org.junit.Test

class DialogueStateTest {
    @Test fun slotsExpireAndCityPersists() {
        val s = DialogueState()
        s.record("طقس القاهرة بكرة", "", "weather", "القاهرة", "مصر", "بكرة")
        s.record("سؤال", "", "unknown")
        assertNotNull(s.slots[Slot.DATE])
        s.record("سؤال آخر", "", "unknown")
        assertNull(s.slots[Slot.DATE])
        s.record("سؤال ثالث", "", "unknown")
        s.record("سؤال رابع", "", "unknown")
        assertNull(s.slots[Slot.TOPIC])
        assertEquals("القاهرة", s.slots[Slot.CITY]?.value)
        assertEquals(5, s.clearConversation())
        assertTrue(s.slots.isEmpty())
    }

    @Test fun missingReferenceNeverGuessesAndRepairIsBounded() {
        val s = DialogueState(); val r = ReferenceResolver()
        val first = ClarificationPolicy.resolve(r.resolve("وإيه عاصمتها؟", s, null), s, "وإيه عاصمتها؟")
        assertTrue(first is Resolution.Ask)
        s.lastWasClarification = true
        val second = ClarificationPolicy.resolve(r.resolve("عنه؟", s, null), s, "عنه؟")
        assertTrue(second is Resolution.Abstain)
        assertNull(s.pendingClarification)
    }

    @Test fun pendingCityIsConsumedWithoutHijackingACommand() {
        val s = DialogueState(); val r = ReferenceResolver()
        ClarificationPolicy.resolve(r.resolve("الطقس إيه؟", s, null), s, "الطقس إيه؟")
        assertTrue(r.resolve("فكرني بالقاهرة", s, "القاهرة") is Resolution.Unchanged)
        assertNull(s.pendingClarification)
        ClarificationPolicy.resolve(r.resolve("الطقس إيه؟", s, null), s, "الطقس إيه؟")
        assertEquals("الطقس إيه؟ في القاهرة", (r.resolve("القاهرة", s, "القاهرة") as Resolution.Query).text)
    }

    @Test fun tomorrowIsNotCurrentWeatherOrReminder() {
        val s = DialogueState(); val r = ReferenceResolver()
        s.record("الطقس في القاهرة", "غير متاح", "weather", "القاهرة")
        assertTrue(r.resolve("وبكرة؟", s, null) is Resolution.Abstain)
        assertTrue(r.resolve("فكرني بكرة", s, null) is Resolution.Unchanged)
        assertTrue(r.resolve("طقس القاهرة بكرة", s, "القاهرة") is Resolution.Abstain)
        assertTrue(r.resolve("الطقس في القاهرة الأسبوع القادم", s, "القاهرة") is Resolution.Abstain)
    }

    @Test fun capitalRewritingNeedsOneActualRecentCountry() {
        val s = DialogueState(); val r = ReferenceResolver()
        s.record("معلومات عن مصر", "", "query_knowledge", topic = "مصر")
        assertEquals("ما عاصمة مصر؟", (r.resolve("وإيه عاصمتها؟", s, null) as Resolution.Query).text)
        s.record("معلومات عن فرنسا", "", "query_knowledge", topic = "فرنسا")
        assertTrue(r.resolve("وإيه عاصمتها؟", s, null) is Resolution.Ask)
        s.clearConversation()
        assertTrue(r.resolve("وإيه عاصمتها؟", s, null) is Resolution.Ask)
    }

    @Test fun presentationKeepsCoreAndSource() {
        val core = "لا أستطيع التأكد: 12.50 https://example.com/a"
        repeat(8) {
            val out = ResponseComposer.compose(ResponseBlock(core, BlockSource.ABSTAIN), ResponseBlock("أهلاً", BlockSource.LOCAL))
            assertTrue(out.text.contains(core))
            assertEquals(BlockSource.ABSTAIN, out.blocks.last().source)
            assertTrue(out.traceId.isNotBlank())
        }
    }

    @Test fun emotionalAnswersAreSpecificAndAvoidNegationAndDanger() {
        val inputs = listOf("مش قادر اذاكر", "حاسس اني وحيد", "اتخانقت مع صاحبي", "انا فرحان النهارده", "انا زهقان", "هل تحبني")
        val keywords = listOf("المذاكرة", "الوحدة", "صاحبك", "فرحان", "الزهق", "مشاعر")
        inputs.zip(keywords).forEach { (input, expected) -> assertTrue(EmotionalSupport.answer(input)?.contains(expected) == true) }
        assertNull(EmotionalSupport.answer("مش حاسس اني وحيد"))
        assertNull(EmotionalSupport.answer("حاسس اني وحيد وعايز انتحر"))
        assertNotNull(EmotionalSupport.crisisAnswer("حاسس اني وحيد وعايز انتحر"))
        assertNull(EmotionalSupport.crisisAnswer("بحث عن الانتحار"))
    }
    @Test fun memoryClaimsAreNotJustifiedByAnUnrelatedTurn() {
        assertTrue(MemoryClaimGuard.containsUnverifiableClaim("كما ذكرت سابقًا اسمك علي"))
        assertTrue(MemoryClaimGuard.containsUnverifiableClaim("بالمناسبة حديثك السابق"))
        assertFalse(MemoryClaimGuard.containsUnverifiableClaim("لا توجد معلومات محفوظة حالياً"))
    }

    @Test fun moodAndIntentForSixExamples() {
        val analyzer = com.example.zahra.intelligence.mood.MoodAnalyzer()
        val cases = listOf(
            "مش قادر اذاكر" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.CONFUSED,
            "حاسس اني وحيد" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.LONELY,
            "اتخانقت مع صاحبي" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.ANGRY,
            "انا فرحان النهارده" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.HAPPY,
            "انا زهقان" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.BORED,
            "هل تحبني" to com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.CURIOUS)
        cases.forEach { (input, expected) ->
            assertEquals(expected, analyzer.analyze(input).primaryMood)
            assertNotNull(EmotionalSupport.answer(input))
        }
        assertEquals(com.example.zahra.intelligence.mood.MoodAnalyzer.Mood.NEUTRAL,
            analyzer.analyze("مش حاسس اني وحيد").primaryMood)
    }

    @Test fun emotionalResponsesAreMostlySpecificRatherThanGeneric() {
        // Word stems plus manual equivalence for "وحيد"/"الوحدة"; a small
        // deterministic test, not a general semantic relatedness metric.
        val cases = listOf(
            "مش قادر اذاكر" to listOf("ذاكر"),
            "حاسس اني وحيد" to listOf("وحد"),
            "اتخانقت مع صاحبي" to listOf("صاحب", "خناق"),
            "انا فرحان النهارده" to listOf("فرح"),
            "انا زهقان" to listOf("زهق"),
            "هل تحبني" to listOf("حب", "مشاعر")
        )
        cases.forEach { (input, anchors) ->
            val answer = EmotionalSupport.answer(input)!!
            val sentences = answer.split(Regex("[.!؟?]+" )).map { it.trim() }.filter { it.isNotEmpty() }
            val generic = sentences.filter { sentence -> anchors.none { sentence.contains(it) } }.sumOf { it.length }
            assertTrue(generic.toDouble() / sentences.sumOf { it.length } <= 0.4)
        }
    }

}
