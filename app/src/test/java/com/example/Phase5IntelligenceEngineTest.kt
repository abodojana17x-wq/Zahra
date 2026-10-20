package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.intelligence.classifier.CalibratedClassifier
import com.example.zahra.intelligence.features.NlpFeatureExtractor
import com.example.zahra.intelligence.planner.*
import com.example.zahra.intelligence.execution.PlanExecutor
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraToolPayload
import com.example.zahra.security.ZahraCrypto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase5IntelligenceEngineTest {

  private lateinit var context: Context
  private lateinit var router: ZahraRouter

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    ZahraCrypto.setTestKeyForTestingOnly(ZahraCrypto.generateTestKey())
    router = ZahraRouter(context)
  }

  @After
  fun tearDown() {
    ZahraCrypto.setTestKeyForTestingOnly(null)
  }

  // =========================================================================
  // 1. NLP FEATURE EXTRACTION TESTS
  // =========================================================================

  @Test
  fun testFeatureExtractor_extractsTokensAndBigrams() {
    val feat = NlpFeatureExtractor.extract("احسب مساحة الدائرة")
    assertTrue(feat.tokens.contains("احسب"))
    assertTrue(feat.tokens.contains("مساحه") || feat.tokens.contains("مساحة"))
    assertTrue(feat.bigrams.isNotEmpty())
  }

  @Test
  fun testFeatureExtractor_extractsCharNgrams() {
    val feat = NlpFeatureExtractor.extract("خوارزمية")
    assertTrue("Char n-grams should be non-empty", feat.charNgrams.isNotEmpty())
    assertTrue("Should contain 3-gram or 4-gram prefixes", feat.charNgrams.any { it.startsWith("c3_") || it.startsWith("c4_") })
  }

  @Test
  fun testFeatureExtractor_extractsMorphologicalAffixes() {
    val feat = NlpFeatureExtractor.extract("الملاحظات وتدوينهم")
    assertTrue("Should extract definite article prefix", feat.affixFeatures.contains("pfx_ال"))
    assertTrue("Should extract pronominal suffix", feat.affixFeatures.contains("sfx_هم") || feat.affixFeatures.contains("sfx_ات"))
  }

  @Test
  fun testFeatureExtractor_detectsMathAndDateCues() {
    val mathFeat = NlpFeatureExtractor.extract("احسب 50 * 4")
    assertEquals(1.0, mathFeat.lexicalCues["cue_has_digits"] ?: 0.0, 0.001)
    assertEquals(1.0, mathFeat.lexicalCues["cue_has_math_op"] ?: 0.0, 0.001)

    val dateFeat = NlpFeatureExtractor.extract("فكرني بكرة بموعد الطبيب")
    assertEquals(1.0, dateFeat.lexicalCues["cue_has_date"] ?: 0.0, 0.001)
    assertEquals(1.0, dateFeat.lexicalCues["cue_has_imperative"] ?: 0.0, 0.001)
  }

  // =========================================================================
  // 2. CALIBRATED CLASSIFIER & UNCERTAINTY TESTS
  // =========================================================================

  @Test
  fun testCalibratedClassifier_trainsAndClassifiesCorpus() {
    val classifier = CalibratedClassifier()
    val corpus = mapOf(
      "calculate" to listOf("احسب 25 * 4", "كم حاصل جمع 5 و 10", "ضرب وقسمة"),
      "create_task" to listOf("أضف مهمة جديدة", "سجل مهمة للغد", "مهمة إرسال التقرير"),
      "translate" to listOf("ترجم هذه الكلمة", "ترجم إلى الإنجليزية", "translate this")
    )
    classifier.train(corpus)

    val res = classifier.classify("احسب 100 + 20")
    assertEquals("calculate", res.topIntent)
    assertTrue("Top probability must be positive", res.topProbability > 0.40)
    assertTrue("Margin must be positive", res.confidenceMargin >= 0.0)
    assertTrue("Shannon entropy must be between 0 and 1", res.shannonEntropy in 0.0..1.0)
  }

  @Test
  fun testCalibratedClassifier_abstainsOnEmptyOrNonsenseInput() {
    val classifier = CalibratedClassifier()
    val corpus = mapOf(
      "greet" to listOf("السلام عليكم", "مرحبا", "أهلا بك"),
      "farewell" to listOf("مع السلامة", "إلى اللقاء")
    )
    classifier.train(corpus)

    val emptyRes = classifier.classify("   ")
    assertTrue("Must recommend abstention on blank input", emptyRes.isAbstentionRecommended)

    val nonsenseRes = classifier.classify("xyzqwer123456789")
    assertTrue("Must recommend abstention on totally out-of-domain words", nonsenseRes.isAbstentionRecommended)
  }

  @Test
  fun testCalibratedClassifier_evaluatesPredictionEntropy() {
    val classifier = CalibratedClassifier()
    val corpus = mapOf(
      "action_a" to listOf("كلمة مشتركة عامة"),
      "action_b" to listOf("كلمة مشتركة عامة ثانية")
    )
    classifier.train(corpus)

    val res = classifier.classify("كلمة مشتركة")
    // When inputs have high overlap across classes, entropy should be elevated
    assertTrue("Entropy should be elevated under high ambiguity", res.shannonEntropy > 0.40)
  }

  // =========================================================================
  // 3. COMPOUND REQUEST DECOMPOSITION TESTS
  // =========================================================================

  @Test
  fun testCompoundAnalyzer_splitsArabicConjunctions() {
    val candidates1 = CompoundRequestAnalyzer.analyze("احسب 50 * 4 ثم احفظ الناتج في ملاحظة")
    assertEquals(2, candidates1.size)
    assertEquals(1, candidates1[0].order)
    assertEquals(2, candidates1[1].order)
    assertTrue(candidates1[1].refersToPreviousOutput)

    val candidates2 = CompoundRequestAnalyzer.analyze("لخص هذا المقال وبعدين أضف مهمة لمراجعته")
    assertEquals(2, candidates2.size)
  }

  @Test
  fun testCompoundAnalyzer_splitsCoordinatedActionVerbs() {
    val candidates = CompoundRequestAnalyzer.analyze("احسب 25 * 4 واضف الناتج في مهمة")
    assertEquals(2, candidates.size)
    assertTrue(candidates[1].rawClause.contains("مهمة"))
  }

  @Test
  fun testCompoundAnalyzer_splitsEnglishThenConjunctions() {
    val candidates = CompoundRequestAnalyzer.analyze("calculate 20 * 5 and then add task to review")
    assertEquals(2, candidates.size)
  }

  @Test
  fun testCompoundAnalyzer_preservesQuotedContentWithoutSplitting() {
    val candidates = CompoundRequestAnalyzer.analyze("سجل ملاحظة بعنوان \"ملاحظة ثم فكرة جديدة\" ثم ذكرني غداً")
    assertEquals(2, candidates.size)
    assertTrue("First candidate must preserve inner quoted conjunction", candidates[0].rawClause.contains("ملاحظة ثم فكرة جديدة"))
  }

  // =========================================================================
  // 4. STRUCTURED TASK PLANNER TESTS
  // =========================================================================

  @Test
  fun testStructuredTaskPlanner_generatesMultiStepPlanWithBindings() {
    val plan = StructuredTaskPlanner.plan("احسب 50 * 4 ثم احفظ الناتج في ملاحظة")
    assertEquals(2, plan.steps.size)

    val step1 = plan.steps[0]
    val step2 = plan.steps[1]

    assertEquals(PlanActionType.CALCULATE, step1.actionType)
    assertEquals(PlanActionType.CREATE_NOTE, step2.actionType)

    assertTrue("Step 2 must depend on Step 1", step2.dependsOnStepIds.contains(step1.id))
    assertTrue("Step 2 must have an argument binding to step 1", step2.argumentBindings.any { it.sourceStepId == step1.id })
  }

  @Test
  fun testStructuredTaskPlanner_handlesSingleStepRequestsWithoutDecomposition() {
    val isCompound = StructuredTaskPlanner.isCompoundRequest("احسب 25 * 4")
    assertFalse("Single request should not be compound", isCompound)

    val plan = StructuredTaskPlanner.plan("احسب 25 * 4")
    assertEquals(1, plan.steps.size)
    assertEquals(PlanActionType.CALCULATE, plan.steps[0].actionType)
  }

  // =========================================================================
  // 5. TRANSACTIONAL PLAN EXECUTOR & ROLLBACK TESTS
  // =========================================================================

  @Test
  fun testPlanExecutor_executesTwoStepCalculateAndNotePlan() {
    val plan = StructuredTaskPlanner.plan("احسب 50 * 4 ثم احفظ الناتج في ملاحظة")
    val executedPlan = router.planExecutor.execute(plan)

    assertTrue("Plan must be completed", executedPlan.isCompleted)
    assertFalse("Plan must have no failures", executedPlan.hasFailures)

    val step1 = executedPlan.steps[0]
    val step2 = executedPlan.steps[1]

    assertEquals(StepExecutionStatus.SUCCESS, step1.status)
    assertEquals(StepExecutionStatus.SUCCESS, step2.status)
    assertTrue("Step 1 output must contain 200", step1.output?.contains("200") == true)
    assertTrue("Step 2 output must confirm note created", step2.output?.contains("الملاحظة") == true)

    // Verify note actually exists in NoteEngine
    val notes = router.noteEngine.getNotes()
    assertTrue("Note containing 200 must be in storage", notes.any { it.content.contains("200") || it.title.contains("200") })
  }

  @Test
  fun testPlanExecutor_executesTwoStepCalculateAndTaskPlan() {
    val plan = StructuredTaskPlanner.plan("احسب 12 * 12 واضف الناتج في مهمة")
    val executedPlan = router.planExecutor.execute(plan)

    assertTrue(executedPlan.isCompleted)
    assertEquals(StepExecutionStatus.SUCCESS, executedPlan.steps[0].status)
    assertEquals(StepExecutionStatus.SUCCESS, executedPlan.steps[1].status)
    assertTrue(executedPlan.steps[0].output?.contains("144") == true)

    val tasks = router.taskEngine.getTasks()
    assertTrue("Task containing 144 must exist in storage", tasks.any { it.title.contains("144") })
  }

  @Test
  fun testPlanExecutor_rollsBackOnStepFailure() {
    // Construct a plan where step 1 succeeds (create a task) but step 2 fails (division by zero)
    val step1 = PlannedStep(
      id = "step1",
      stepNumber = 1,
      title = "إنشاء مهمة قابلة للتراجع",
      actionType = PlanActionType.CREATE_TASK,
      rawCommand = "مهمة اختبار التراجع المعياري",
      parameters = mapOf("title" to "مهمة اختبار التراجع المعياري"),
      isRollbackAvailable = true
    )
    val step2 = PlannedStep(
      id = "step2",
      stepNumber = 2,
      title = "حساب مستحيل",
      actionType = PlanActionType.CALCULATE,
      rawCommand = "احسب 100 / 0",
      parameters = mapOf("expression" to "100 / 0")
    )
    val plan = ExecutionPlan(
      originalRequest = "مهمة مع حساب مستحيل",
      title = "خطة اختبار التراجع",
      steps = listOf(step1, step2),
      overallConfidence = 0.90
    )

    val executed = router.planExecutor.execute(plan)
    assertTrue("Plan must report failures", executed.hasFailures)
    assertFalse("Plan is not fully completed", executed.isCompleted)

    assertEquals(StepExecutionStatus.ROLLED_BACK, executed.steps[0].status)
    assertEquals(StepExecutionStatus.FAILED, executed.steps[1].status)

    // Verify task was rolled back and deleted from taskEngine
    val tasks = router.taskEngine.getTasks()
    assertFalse("Task should be removed by transactional rollback", tasks.any { it.title.contains("مهمة اختبار التراجع المعياري") })
  }

  // =========================================================================
  // 6. END-TO-END ROUTER INTEGRATION TESTS
  // =========================================================================

  @Test
  fun testRouter_processesCompoundCalculateAndNoteRequest() {
    val resp = router.process("احسب 30 * 5 ثم احفظ الناتج في ملاحظة")
    assertEquals("compound_plan", resp.intent)
    assertEquals(OutcomeState.SUPPORTED, resp.outcomeState)
    assertTrue("Response must contain success summary", resp.text.contains("150"))
    assertTrue("Tool payload must be PlanExecutionPayload", resp.toolPayload is ZahraToolPayload.PlanExecutionPayload)

    val payload = resp.toolPayload as ZahraToolPayload.PlanExecutionPayload
    assertEquals(2, payload.plan.steps.size)
    assertTrue(payload.plan.isCompleted)
  }

  @Test
  fun testRouter_processesCompoundCalculateAndTaskRequest() {
    val resp = router.process("احسب 500 / 5 واضف الناتج في مهمة")
    assertEquals("compound_plan", resp.intent)
    assertEquals(OutcomeState.SUPPORTED, resp.outcomeState)
    assertTrue(resp.text.contains("100"))
  }

  @Test
  fun testRouter_singleActionBackwardsCompatibilityPreserved() {
    // Single calculation must still route to "calculate"
    val calcResp = router.process("احسب 15 * 3")
    assertEquals("calculate", calcResp.intent)
    assertTrue(calcResp.text.contains("45"))

    // Single unit conversion must route to "convert_unit"
    val unitResp = router.process("حول 10 كم إلى ميل")
    assertEquals("convert_unit", unitResp.intent)
    assertTrue(unitResp.text.contains("ميل") || unitResp.text.contains("6.21"))

    // Single knowledge query
    val kResp = router.process("ما هي سرعة الضوء")
    assertTrue(kResp.intent == "query_knowledge" || kResp.intent == "define_word")
  }
}
