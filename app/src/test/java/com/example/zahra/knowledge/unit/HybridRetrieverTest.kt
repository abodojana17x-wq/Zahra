package com.example.zahra.knowledge.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * توصيف [HybridRetriever]: معادلة BM25+ محسوبة يدويًا، تطبيع بمقياس مشتق، دمج مرجّح،
 * تقريب إملائي على مستوى الرمز، وإخراج قابل للتفسير.
 *
 * ⚠️ مكتوبة ولم تُشغَّل هنا (لا JDK ولا Android SDK). لا ادعاء نجاح قبل تشغيل فعلي.
 */
class HybridRetrieverTest {

  private val weights = HybridWeights(1.0, 0.0, 0.0)

  @Test
  fun bm25Plus_matchesHandComputedScores() {
    // N=3، avgDocLength=53/3، df(alpha)=2، df(beta)=1، k1=1.2، b=0.75، δ=1.0
    // u1: tf=3, dl=3  ⇒ 1.3684034   ·   u2: tf=1, dl=30 ⇒ 0.8355968   ·   u3(beta) ⇒ 2.2811668
    val retriever = HybridRetriever(bm25ReferenceIndex(), weights, NoTfIdfSignal)
    val result = retriever.search("alpha", maxResults = 5)
    val u1 = result.matches.first { it.unit.id == "u1" }
    val u2 = result.matches.first { it.unit.id == "u2" }
    assertEquals(1.3684034, u1.breakdown.bm25Raw, 1e-6)
    assertEquals(0.8355968, u2.breakdown.bm25Raw, 1e-6)

    val beta = retriever.search("beta").matches.first { it.unit.id == "u3" }
    assertEquals(2.2811668, beta.breakdown.bm25Raw, 1e-6)
  }

  @Test
  fun score_isRatioToBestCandidate_withinUnitInterval() {
    val retriever = HybridRetriever(bm25ReferenceIndex(), weights, NoTfIdfSignal)
    val result = retriever.search("alpha")
    assertEquals("أفضل مرشح = 1.0 بعد النسبة", 1.0, result.best!!.score, 1e-9)
    assertTrue(result.matches.all { it.score in 0.0..1.0 })
    // bm25Norm = raw/(raw+ref) حيث ref مشتق من الفهرس (1.0 في هذا الفهرس الاصطناعي)
    assertEquals(1.3684034 / 2.3684034, result.best!!.breakdown.bm25Normalized, 1e-6)
  }

  @Test
  fun results_areSortedByScoreDescending() {
    val index = testIndex(
      listOf(
        testUnit("strong", mapOf("debounce" to 4.0), docLength = 4, questions = listOf("debounce")),
        testUnit("weak", mapOf("debounce" to 1.0), docLength = 40, questions = listOf("debounce")),
        testUnit("other", mapOf("pomodoro" to 3.0), docLength = 3, questions = listOf("pomodoro"))
      ),
      bm25Ref = 1.0,
      weights = weights
    )
    val matches = HybridRetriever(index, weights, NoTfIdfSignal).search("debounce").matches
    assertEquals(listOf("strong", "weak"), matches.map { it.unit.id })
  }

  @Test
  fun matchedTermsAndBreakdown_areExplainable() {
    val retriever = HybridRetriever(bm25ReferenceIndex(), weights, NoTfIdfSignal)
    val match = retriever.search("alpha").best!!
    assertEquals(listOf("alpha"), match.matchedTerms)
    assertTrue(match.hasLexicalSupport)
    val explain = match.breakdown.explain()
    assertTrue("التفسير يذكر BM25: $explain", explain.contains("bm25+"))
    assertTrue("التفسير يذكر التغطية: $explain", explain.contains("تغطية"))
    assertNotNull(match.breakdown.relative)
  }

  @Test
  fun typoInKeyword_isRescuedByCharacterGrams() {
    val index = testIndex(
      listOf(
        testUnit(
          "closure", mapOf("closure" to 3.0), docLength = 3,
          title = "closure", questions = listOf("closure")
        )
      ),
      bm25Ref = 1.0,
      weights = HybridWeights(0.40, 0.30, 0.30)
    )
    val result = HybridRetriever(index, HybridWeights(0.40, 0.30, 0.30), NoTfIdfSignal).search("closur")
    val best = result.best
    assertNotNull("خطأ حرف واحد لا يجب أن يُفقد الوحدة", best)
    assertTrue("يجب أن يُشرح التقريب: ${best!!.fuzzyMatches}", best.fuzzyMatches.isNotEmpty())
    assertTrue(best.breakdown.charJaccard > 0.5)
  }

  @Test
  fun blankQuery_returnsWarningAndNoMatches() {
    val retriever = HybridRetriever(bm25ReferenceIndex(), weights, NoTfIdfSignal)
    val result = retriever.search("   ")
    assertNull(result.best)
    assertTrue(result.warnings.isNotEmpty())
  }

  @Test
  fun emptyIndex_returnsWarningInsteadOfThrowing() {
    val retriever = HybridRetriever(testIndex(emptyList(), avgDocLength = 1.0), weights, NoTfIdfSignal)
    val result = retriever.search("alpha")
    assertNull(result.best)
    assertTrue(result.warnings.isNotEmpty())
  }

  @Test
  fun overlyLongQuery_isTruncatedWithExplicitWarning() {
    val retriever = HybridRetriever(bm25ReferenceIndex(), weights, NoTfIdfSignal)
    val long = "alpha " + "ز".repeat(HybridRetriever.MAX_QUERY_LENGTH)
    val result = retriever.search(long)
    assertTrue(result.warnings.any { it.contains("اقتطاع") })
  }

  @Test
  fun weights_areNormalizedBeforeFusion() {
    val retriever = HybridRetriever(bm25ReferenceIndex(), HybridWeights(2.0, 1.0, 1.0), NoTfIdfSignal)
    assertEquals(1.0, retriever.usedWeights.normalized().sum, 1e-9)
    assertEquals(0.5, retriever.usedWeights.normalized().bm25, 1e-9)
  }

  @Test
  fun tfIdfSignal_isInjectableAndExplained() {
    val signal = TfIdfSignal { _, _ -> mapOf("u2" to 0.9) }
    val index = bm25ReferenceIndex(HybridWeights(0.5, 0.0, 0.5))
    val retriever = HybridRetriever(index, HybridWeights(0.5, 0.0, 0.5), signal)
    val match = retriever.search("alpha").matches.first { it.unit.id == "u2" }
    assertEquals(0.9, match.breakdown.tfidfCosine, 1e-9)
    assertFalse(match.breakdown.explain().contains("tfidf=0.000"))
  }
}
