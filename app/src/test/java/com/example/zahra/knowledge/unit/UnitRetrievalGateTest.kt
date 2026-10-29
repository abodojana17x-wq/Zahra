package com.example.zahra.knowledge.unit

import com.example.zahra.routing.ConfidenceGatePolicy
import com.example.zahra.routing.GateOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * توصيف [UnitRetrievalGate]: لا عتبات جديدة (كل الأرقام من [ConfidenceGatePolicy])، ومخرج واحد من ثلاثة.
 *
 * ⚠️ مكتوبة ولم تُشغَّل هنا. لا ادعاء نجاح قبل تشغيل فعلي.
 */
class UnitRetrievalGateTest {

  private val weights = HybridWeights(1.0, 0.0, 0.0)

  private fun closeIndex(samePacks: Boolean): KnowledgeUnitIndex {
    val packA = "packA"
    val packB = if (samePacks) "packA" else "packB"
    return testIndex(
      listOf(
        testUnit("a-unit", mapOf("alpha" to 3.0), docLength = 3, questions = listOf("alpha"), pack = packA),
        testUnit("b-unit", mapOf("alpha" to 2.9), docLength = 3, questions = listOf("alpha"), pack = packB)
      ),
      bm25Ref = 1.0,
      weights = weights
    )
  }

  @Test
  fun blankQuery_abstains() {
    val index = closeIndex(samePacks = true)
    val decision = UnitRetrievalGate().decide("", HybridRetriever(index, weights, NoTfIdfSignal).search(""))
    assertEquals(GateOutcome.ABSTAIN, decision.outcome)
    assertEquals("empty_query", decision.reasonCode)
    assertFalse(decision.answered)
  }

  @Test
  fun stopwordsOnly_abstainsWithExplicitReason() {
    val index = closeIndex(samePacks = true)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    val decision = UnitRetrievalGate().decide("ما هو ده", retriever.search("ما هو ده"))
    assertEquals(GateOutcome.ABSTAIN, decision.outcome)
    assertEquals("no_substantive_tokens", decision.reasonCode)
  }

  @Test
  fun noMatches_abstainsWithoutGuessing() {
    val index = closeIndex(samePacks = true)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    val decision = UnitRetrievalGate().decide("زقزقة النورس", retriever.search("زقزقة النورس"))
    assertEquals(GateOutcome.ABSTAIN, decision.outcome)
    assertTrue(decision.reasonCode in setOf("no_match", "no_substantive_tokens"))
  }

  @Test
  fun belowFloor_abstainsAndReportsThreshold() {
    val index = closeIndex(samePacks = true)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    // استعلام من رمزين: تغطية نصف ⇒ 0.675، أقل من أرضية 0.999
    val decision = UnitRetrievalGate(minScore = 0.999).decide("alpha beta", retriever.search("alpha beta"))
    assertEquals(GateOutcome.ABSTAIN, decision.outcome)
    assertEquals("below_threshold", decision.reasonCode)
    assertTrue("السبب يذكر العتبة", decision.reason.contains("%"))
  }

  @Test
  fun closeCallAcrossPacks_asksForClarification() {
    val index = closeIndex(samePacks = false)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    val decision = UnitRetrievalGate(minScore = 0.0).decide("alpha", retriever.search("alpha"))
    assertEquals(GateOutcome.CLARIFY, decision.outcome)
    assertEquals("close_call", decision.reasonCode)
    assertEquals(2, decision.clarifyOptions.size)
    assertFalse(decision.answered)
  }

  @Test
  fun closeCallWithinSamePack_answers() {
    val index = closeIndex(samePacks = true)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    val decision = UnitRetrievalGate(minScore = 0.0).decide("alpha", retriever.search("alpha"))
    assertEquals(GateOutcome.ANSWER, decision.outcome)
    assertTrue(decision.answered)
  }

  @Test
  fun answeredDecision_carriesFullDiagnostics() {
    val index = closeIndex(samePacks = true)
    val retriever = HybridRetriever(index, weights, NoTfIdfSignal)
    val decision = UnitRetrievalGate(minScore = 0.0).decide("alpha", retriever.search("alpha"))
    assertNotNull(decision.best)
    assertTrue(decision.diagnostics.containsKey("breakdown"))
    assertTrue(decision.diagnostics.containsKey("bestScore"))
    assertTrue(decision.diagnostics["breakdown"]!!.contains("bm25+"))
  }

  @Test
  fun thresholdUsed_isTheSharedPolicyValue() {
    // لا ثابت جديد: القيمة الافتراضية هي نفس عتبة السياسة الموحّدة.
    val decision = UnitRetrievalGate().decide("alpha", HybridSearchResult("alpha", listOf("alpha"), emptyList()))
    assertEquals("no_match", decision.reasonCode)
    assertEquals(0.50, ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE, 1e-9)
  }
}
