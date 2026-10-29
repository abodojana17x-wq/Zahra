package com.example.zahra.dialogue

import com.example.zahra.context.ContextManager
import com.example.zahra.understanding.ContextAndCorrectionTracker
import com.example.zahra.understanding.CorrectionResolution
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * المرحلة 4 — البند المعماري المتبقي: توحيد مستهلكي السياق القديم.
 *
 * كل السيناريوهات هنا تعمل على منطق Kotlin خالص (لا Android)، وتثبت أن:
 * 1) الوضع الافتراضي `PROJECTED` لا يحتفظ بسجل ثانٍ.
 * 2) وضع الرجوع `MIRRORED` يُنتج نفس الدورات (تكافؤ)، لا سلوكًا مختلفًا.
 * 3) لا يُستشهد بدورة غير موجودة في المصدر الوحيد.
 */
class DialogueContextUnificationTest {

    @After fun resetMode() {
        LegacyContextPolicy.setModeForTesting(LegacyContextMode.PROJECTED)
    }

    private class Harness {
        val state = DialogueState()
        val history = DialogueStateHistory(state)
        val tracker = ContextAndCorrectionTracker { LegacyContextPolicy.projectedSource(history) }
        val context = ContextManager().apply {
            bindDialogueSource { LegacyContextPolicy.projectedSource(history) }
        }
        val mirror = mutableListOf<DialogueContextEquivalence.MirroredTurn>()

        /** يحاكي حدّ الموجّه: تسجيل واحد في الحالة ثم إسقاط أو مرآة. */
        fun turn(user: String, response: String, intent: String) {
            state.record(user, response, intent)
            tracker.recordTurn(user, com.example.zahra.model.ZahraResponse(
                text = response, intent = intent, confidence = 1.0,
                confidenceTier = com.example.zahra.model.ConfidenceTier.DIRECT
            ))
            if (context.isProjected()) context.syncFromDialogue()
            else context.recordTurn(user, user, com.example.zahra.model.Sender.USER, intent)
            mirror.add(DialogueContextEquivalence.MirroredTurn(user, response, intent))
        }
    }

    private fun scenario(): Harness {
        val h = Harness()
        h.turn("الطقس في القاهرة", "٢٥ درجة", "weather")
        h.turn("وبكرة؟", "لا أملك توقعًا موثوقًا", "dialogue_abstain")
        h.turn("معلومات عن مصر", "مصر دولة", "query_knowledge")
        return h
    }

    @Test fun projectedModeHasNoSecondHistory() {
        val h = scenario()
        assertTrue(h.context.isProjected())
        assertTrue(h.tracker.isProjected())
        assertEquals(3, h.state.turns.size)
        assertEquals("معلومات عن مصر", h.tracker.getLastUserQuery())
        assertEquals("مصر دولة", h.tracker.getLastZahraResponse())
        // الكائن القديم الكامل غير متاح في الوضع المُسقَط (لا نخترع حقولًا مفقودة).
        assertNull(h.tracker.getLastTurn())
        assertEquals(emptyList<String>(), DialogueContextEquivalence.drift(h.history, h.mirror))
        assertEquals(h.state.turns.map { it.user },
            h.context.getCurrentConversation().turns.filter {
                it.speaker == com.example.zahra.model.Sender.USER
            }.map { it.text })
    }

    @Test fun mirroredModeIsEquivalentForTheSameTurns() {
        LegacyContextPolicy.setModeForTesting(LegacyContextMode.MIRRORED)
        val h = scenario()
        assertFalse(h.context.isProjected())
        assertFalse(h.tracker.isProjected())
        assertEquals("معلومات عن مصر", h.tracker.getLastUserQuery())
        assertEquals("مصر دولة", h.tracker.getLastZahraResponse())
        assertEquals(emptyList<String>(), DialogueContextEquivalence.drift(h.history, h.mirror))
        val mirroredUsers = h.context.getCurrentConversation().turns
            .filter { it.speaker == com.example.zahra.model.Sender.USER }.map { it.text }
        assertEquals(h.state.turns.map { it.user }, mirroredUsers)
        // بعد الرجوع للوضع الافتراضي يعود المصدر الوحيد فورًا.
        LegacyContextPolicy.setModeForTesting(LegacyContextMode.PROJECTED)
        assertTrue(h.tracker.isProjected())
    }

    @Test fun correctionsNeverUseATurnThatWasNotDelivered() {
        val state = DialogueState()
        val history = DialogueStateHistory(state)
        val tracker = ContextAndCorrectionTracker { LegacyContextPolicy.projectedSource(history) }
        // لا دورات: طلب التراجع لا يخترع وصفًا لإجراء سابق غير موجود،
        // والتلخيص/المتابعة لا تُبنى على ذاكرة متخيّلة.
        val undo = tracker.detectCorrectionOrFollowup("تراجع")
        assertTrue(undo is CorrectionResolution.UndoAction)
        assertEquals("الإجراء الأخير", (undo as CorrectionResolution.UndoAction).actionDescription)
        assertEquals(CorrectionResolution.None, tracker.detectCorrectionOrFollowup("لخصه"))
        assertEquals(CorrectionResolution.None, tracker.detectCorrectionOrFollowup("وماذا عن فرنسا؟"))
        state.record("معلومات عن مصر", "رد", "query_knowledge", topic = "مصر")
        val follow = tracker.detectCorrectionOrFollowup("وماذا عن فرنسا")
        assertTrue(follow is CorrectionResolution.RefinedQuery)
        assertTrue((follow as CorrectionResolution.RefinedQuery).reformulatedQuery.contains("معلومات عن فرنسا"))
    }

    @Test fun sameQuestionRuleNeedsADeliveredQuestion() {
        val h = Harness()
        assertEquals(CorrectionResolution.None, h.tracker.detectCorrectionOrFollowup("نفس السؤال بس في فرنسا"))
        h.turn("الطقس في القاهرة", "٢٥", "weather")
        val r = h.tracker.detectCorrectionOrFollowup("نفس السؤال بس في أسوان")
        assertTrue(r is CorrectionResolution.RefinedQuery)
        assertTrue((r as CorrectionResolution.RefinedQuery).reformulatedQuery.contains("الطقس في القاهرة"))
        assertTrue(r.reformulatedQuery.contains("أسوان"))
    }

    @Test fun claimsWithoutATurnAreDetected() {
        val h = scenario()
        assertTrue(DialogueContextEquivalence.claimHasNoTurn(h.history, "موضوع لم نتكلم عنه إطلاقًا"))
        assertFalse(DialogueContextEquivalence.claimHasNoTurn(h.history, "الطقس في القاهرة"))
        assertFalse(DialogueContextEquivalence.claimHasNoTurn(h.history, "  "))
    }

    @Test fun equivalenceDetectsRealDrift() {
        val h = scenario()
        val tampered = h.mirror.toMutableList()
        tampered[0] = tampered[0].copy(response = "٣٠ درجة")
        assertFalse(DialogueContextEquivalence.drift(h.history, tampered).isEmpty())
        assertFalse(DialogueContextEquivalence.drift(h.history, tampered.drop(1)).isEmpty())
    }
}
