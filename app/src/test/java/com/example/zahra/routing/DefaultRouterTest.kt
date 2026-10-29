package com.example.zahra.routing

import com.example.zahra.core.DetectedLang
import com.example.zahra.core.LangResult
import com.example.zahra.understanding.AmbiguityAndClarificationDetector
import com.example.zahra.understanding.AmbiguityCheckResult
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * توصيف سلوك [DefaultRouter] (المسار ج — المرحلة 1).
 * اختبارات JVM خفيفة لا تحتاج Android Context.
 * لم تُشغّل بعد (لا JDK في بيئة هذه الحجرة).
 */
class DefaultRouterTest {

  private val ar = LangResult(DetectedLang.AR, "Arabic", 0.9)

  private fun evidence(
    intentLabel: String = Intent.UNKNOWN,
    confidence: Double = 0.5,
    scores: Map<String, Double> = emptyMap(),
    toolVerdict: ToolVerdict? = null,
    knowledgeState: KnowledgeState? = null,
    isImpermissible: Boolean = false,
    ambiguity: AmbiguityCheckResult? = null,
    isSystemCommand: Boolean = false
  ): RoutingEvidence = RoutingEvidence(
    raw = "raw", normalized = "norm", lang = ar,
    intentLabel = intentLabel, classifierConfidence = confidence, scores = scores,
    toolVerdict = toolVerdict, knowledgeState = knowledgeState,
    isImpermissible = isImpermissible, ambiguity = ambiguity, isSystemCommand = isSystemCommand
  )

  @Test
  fun route_impermissibleHasHighestPriority() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.CREATE_TASK,
      confidence = 0.99,
      isImpermissible = true
    ))
    assertEquals(Route.REFUSE, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
    assertNotNull(decision.traceId)
  }

  @Test
  fun route_systemCommandClearAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.CREATE_TASK, confidence = 0.9, isSystemCommand = true
    ))
    assertEquals(Route.SYSTEM, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
    assertTrue(decision.clarifyOptions.isEmpty())
  }

  @Test
  fun route_systemCommandAmbiguousClarifiesWithDetectorOptions() {
    val router = DefaultRouter()
    val ambiguity = AmbiguityCheckResult.Ambiguous(
      com.example.zahra.model.ClarificationPayload(
        question = "أي وقت؟",
        options = emptyList(),
        originalQuery = "فكرني",
        reason = "وقت غير محدد"
      )
    )
    val decision = router.route(evidence(
      intentLabel = Intent.CREATE_SCHEDULE, confidence = 0.9,
      isSystemCommand = true, ambiguity = ambiguity
    ))
    assertEquals(Route.SYSTEM, decision.route)
    assertEquals(GateOutcome.CLARIFY, decision.gate)
  }

  @Test
  fun route_calculateToolOkAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.CALCULATE, confidence = 0.98, toolVerdict = ToolVerdict.OK
    ))
    assertEquals(Route.CALC, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
  }

  @Test
  fun route_calculateToolAmbiguousClarifies() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.CALCULATE, confidence = 0.98, toolVerdict = ToolVerdict.AMBIGUOUS
    ))
    assertEquals(Route.CALC, decision.route)
    assertEquals(GateOutcome.CLARIFY, decision.gate)
  }

  @Test
  fun route_calculateToolFailedAbstains() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.CALCULATE, confidence = 0.98, toolVerdict = ToolVerdict.FAILED
    ))
    assertEquals(Route.CALC, decision.route)
    assertEquals(GateOutcome.ABSTAIN, decision.gate)
  }

  @Test
  fun route_knowledgeVerifiedAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.QUERY_KNOWLEDGE, confidence = 0.6,
      knowledgeState = KnowledgeState.VERIFIED
    ))
    assertEquals(Route.FACT, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
  }

  @Test
  fun route_knowledgeCandidateAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.QUERY_KNOWLEDGE, confidence = 0.5,
      knowledgeState = KnowledgeState.CANDIDATE
    ))
    assertEquals(Route.FACT, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
  }

  @Test
  fun route_knowledgeInsufficientAbstains() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.QUERY_KNOWLEDGE, confidence = 0.5,
      knowledgeState = KnowledgeState.INSUFFICIENT
    ))
    assertEquals(Route.FACT, decision.route)
    assertEquals(GateOutcome.ABSTAIN, decision.gate)
  }

  @Test
  fun route_religionHighConfidenceAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.ASK_QURAN, confidence = 0.45
    ))
    assertEquals(Route.RELIGION, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
  }

  @Test
  fun route_religionMediumConfidenceClarifies() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.ASK_HADITH, confidence = 0.35
    ))
    assertEquals(Route.RELIGION, decision.route)
    assertEquals(GateOutcome.CLARIFY, decision.gate)
  }

  @Test
  fun route_religionLowConfidenceAbstains() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.ASK_HADITH, confidence = 0.25
    ))
    assertEquals(Route.RELIGION, decision.route)
    assertEquals(GateOutcome.ABSTAIN, decision.gate)
  }

  @Test
  fun route_greetSocialAnswers() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.GREET, confidence = 0.95
    ))
    assertEquals(Route.SOCIAL, decision.route)
    assertEquals(GateOutcome.ANSWER, decision.gate)
  }

  @Test
  fun route_unknownBelowAbstainThresholdAbstains() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.UNKNOWN, confidence = 0.20,
      scores = mapOf(Intent.UNKNOWN to 0.20)
    ))
    assertEquals(Route.UNKNOWN, decision.route)
    assertEquals(GateOutcome.ABSTAIN, decision.gate)
    assertTrue(decision.clarifyOptions.isEmpty())
  }

  @Test
  fun route_unknownMediumWithAlternativesClarifiesFromScoresOnly() {
    val router = DefaultRouter()
    val scores = mapOf(
      Intent.UNKNOWN to 0.40,
      Intent.CALCULATE to 0.30,
      Intent.GREET to 0.20,
      Intent.ASK_RELIGION to 0.10
    )
    val decision = router.route(evidence(
      intentLabel = Intent.UNKNOWN, confidence = 0.40, scores = scores
    ))
    assertEquals(GateOutcome.CLARIFY, decision.gate)
    // البدائل من أعلى النيات الفعلية فقط، ولا تحتوي على النية الحالية أو "unknown"
    assertFalse(decision.clarifyOptions.contains(Intent.UNKNOWN))
    assertFalse(decision.clarifyOptions.contains(Intent.UNKNOWN))
    assertTrue(decision.clarifyOptions.contains(Intent.CALCULATE))
    assertTrue(decision.clarifyOptions.size <= 3)
    assertEquals(listOf(Intent.CALCULATE, Intent.GREET, Intent.ASK_RELIGION), decision.clarifyOptions)
  }

  @Test
  fun route_unknownWithoutEnoughAlternativesAbstains() {
    val router = DefaultRouter()
    val decision = router.route(evidence(
      intentLabel = Intent.UNKNOWN, confidence = 0.40,
      scores = mapOf(Intent.UNKNOWN to 0.40, Intent.CALCULATE to 0.30)
    ))
    assertEquals(GateOutcome.ABSTAIN, decision.gate) // بديل واحد فقط < 2
    assertTrue(decision.clarifyOptions.isEmpty())
  }

  @Test
  fun route_traceIdIsUniqueUuid() {
    val router = DefaultRouter()
    val d1 = router.route(evidence(intentLabel = Intent.GREET, confidence = 0.9))
    val d2 = router.route(evidence(intentLabel = Intent.GREET, confidence = 0.9))
    assertTrue(UUID.fromString(d1.traceId) != null)
    assertTrue(UUID.fromString(d2.traceId) != null)
    assertNotEquals(d1.traceId, d2.traceId)
  }

  @Test
  fun route_prioritySystemBeforeFact() {
    val router = DefaultRouter()
    // أمر نظام مع معرف مؤكد: النظام يسبق المعرفة في الأولوية
    val decision = router.route(evidence(
      intentLabel = Intent.CREATE_TASK, confidence = 0.9,
      knowledgeState = KnowledgeState.VERIFIED, isSystemCommand = true
    ))
    assertEquals(Route.SYSTEM, decision.route)
  }
}
