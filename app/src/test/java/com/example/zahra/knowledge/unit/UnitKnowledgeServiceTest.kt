package com.example.zahra.knowledge.unit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * توصيف [UnitKnowledgeService]: ربط استرجاع ← بوابة ← نص إجابة بسند ظاهر، وامتناع صادق بلا نص.
 *
 * ⚠️ مكتوبة ولم تُشغَّل هنا. لا ادعاء نجاح قبل تشغيل فعلي.
 */
class UnitKnowledgeServiceTest {

  private fun service(units: List<KnowledgeUnit>): UnitKnowledgeService =
    UnitKnowledgeService(
      index = testIndex(units, bm25Ref = 1.0, weights = HybridWeights(1.0, 0.0, 0.0)),
      weights = HybridWeights(1.0, 0.0, 0.0),
      tfIdfSignal = NoTfIdfSignal
    )

  private fun closureUnit(safety: String = UnitSafety.NONE, source: UnitSource? = null): KnowledgeUnit =
    testUnit(
      id = "prog-closure",
      terms = mapOf("closure" to 3.0, "جافاسكربت" to 2.0),
      docLength = 5,
      questions = listOf("يعني ايه closure في جافاسكربت", "what is closure in javascript"),
      title = "الـ Closure في JavaScript",
      pack = "seed-programming",
      safety = safety,
      source = source
    )

  @Test
  fun answered_returnsTextWithCitation() {
    val unit = closureUnit(source = UnitSource(name = "MDN", url = "https://developer.mozilla.org", license = "CC-BY-SA"))
    val answer = service(listOf(unit)).answer("closure في جافاسكربت")
    assertTrue(answer.answered)
    val text = answer.text!!
    assertTrue("نص الإجابة موجود", text.contains("إجابة الوحدة"))
    assertTrue("السند ظاهر", text.contains("[المصدر: MDN"))
    assertTrue("الرخصة ظاهرة", text.contains("CC-BY-SA"))
    assertNotNull(answer.sourceLabel)
  }

  @Test
  fun abstained_returnsNullTextAndReason() {
    val answer = service(listOf(closureUnit())).answer("زقزقة النورس عند الفجر")
    assertFalse(answer.answered)
    assertNull(answer.text)
    assertTrue(answer.decision.reason.isNotBlank())
  }

  @Test
  fun authoredUnit_isDeclaredAsAuthored() {
    val answer = service(listOf(closureUnit())).answer("closure في جافاسكربت")
    assertTrue(answer.answered)
    assertTrue(answer.text!!.contains("مُؤلّفة داخليًا"))
  }

  @Test
  fun medicalSafety_appendsDisclaimer() {
    val unit = closureUnit(safety = UnitSafety.MEDICAL_DISCLAIMER).copy(
      pack = "seed-legacy",
      title = "احتياج النوم اليومي للبالغين"
    )
    val composed = service(listOf(unit)).compose(
      UnitMatch(unit, 1.0, listOf("closure"), emptyList(), 0,
        ScoreBreakdown(1.0, 0.5, 0.0, 0.0, 0.0, 0.5, 1.0, 1.0, 1.0, 1.0))
    )
    assertTrue("تنبيه طبي ظاهر", composed.contains("⚠️"))
    assertTrue(composed.contains("طبيب"))
  }

  @Test
  fun noFatwaSafety_appendsReligiousBoundary() {
    val unit = closureUnit(safety = UnitSafety.NO_FATWA)
    val composed = service(listOf(unit)).compose(
      UnitMatch(unit, 1.0, listOf("closure"), emptyList(), 0,
        ScoreBreakdown(1.0, 0.5, 0.0, 0.0, 0.0, 0.5, 1.0, 1.0, 1.0, 1.0))
    )
    assertTrue(composed.contains("فتوى"))
  }
}
