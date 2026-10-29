package com.example.zahra.dialogue

import com.example.zahra.core.ZahraNormalizer
import com.example.zahra.memory.UserProfileExtractor
import com.example.zahra.understanding.ContextAndCorrectionTracker
import com.example.zahra.understanding.CorrectionResolution
import org.junit.Assert.*
import org.junit.Test

class CorrectionSafetyTest {
    @Test fun negatedFeelingsAreNeverNameCorrections() {
        val tracker = ContextAndCorrectionTracker()
        listOf("مش حاسس اني وحيد", "مش قادر اذاكر", "لا مش كده", "مش اسمي محمد").forEach {
            assertFalse(tracker.detectCorrectionOrFollowup(it) is CorrectionResolution.UpdateFact)
        }
    }

    @Test fun explicitCorrectionExtractsTheNewName() {
        val tracker = ContextAndCorrectionTracker()
        val r = tracker.detectCorrectionOrFollowup("اسمي أحمد مش محمد") as CorrectionResolution.UpdateFact
        assertEquals(ZahraNormalizer.normalize("أحمد"), r.newValue)
        assertEquals("أحمد", UserProfileExtractor().extract("أنا اسمي أحمد").name)
        assertEquals("احمد", SlotExtractor.extract("اسمي هو أحمد")[Slot.PERSON])
    }

    @Test fun validatorRejectsUnsupportedClaimsButKeepsOrdinaryToolText() {
        assertNotNull(DialogueResponseValidator.problem("هذا علاج مؤكد", false))
        assertNotNull(DialogueResponseValidator.problem("قال النبي كلاماً", false))
        assertNotNull(DialogueResponseValidator.problem("كما ذكرت سابقا", true))
        assertNull(DialogueResponseValidator.problem("النتيجة 12.50 https://example.com", false))
    }
}
