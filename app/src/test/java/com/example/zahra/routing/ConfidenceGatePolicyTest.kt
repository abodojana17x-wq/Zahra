package com.example.zahra.routing

import org.junit.Assert.*
import org.junit.Test

/**
 * توثيق العتبات القديمة المنقولة من `ZahraRouter.process()`.
 *
 * conforme لـ D4/D20: هذه القيم غير معايَرة، والمعايرة غير منفّذة في هذه المرحلة.
 * لا يُderived أي احتمالية معايَرة منها.
 */
class ConfidenceGatePolicyTest {

  @Test
  fun thresholdsAreDocumentedLegacyValues() {
    assertEquals(0.50, ConfidenceGatePolicy.MIN_TOP_PROBABILITY, 0.001)
    assertEquals(0.40, ConfidenceGatePolicy.NB_ABSTAIN_FLOOR, 0.001)
    assertEquals(0.35, ConfidenceGatePolicy.UNKNOWN_ABSTAIN, 0.001)
    assertEquals(0.75, ConfidenceGatePolicy.TIER_DIRECT, 0.001)
    assertEquals(0.40, ConfidenceGatePolicy.TIER_QUALIFIED, 0.001)
    assertEquals(0.50, ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE, 0.001)
    assertEquals(0.40, ConfidenceGatePolicy.RELIGION_QURAN_MIN_SCORE, 0.001)
    assertEquals(0.30, ConfidenceGatePolicy.RELIGION_HADITH_ABSTAIN_SCORE, 0.001)
    // حد مرجع المنافس موثّق للمراجعة فقط، غير مستخدم في الكود
    assertEquals(0.42, ConfidenceGatePolicy.RAQIM_REFERENCE_THRESHOLD, 0.001)
  }
}
