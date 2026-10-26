package com.example.eval

import android.content.Context
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.model.ZahraResponse

/**
 * هارنس القياس — المرحلة 0 (Eval & Evidence).
 *
 * العقد المنفّذ هنا (plans/00-measurement.md · المسار B):
 *  - يُبنى `ZahraRouter` لكل حالة عبر سياق Robolectric (الأصول والـ SharedPreferences حقيقية).
 *  - عزل الذاكرة بين التكرارات: clearAll + محادثة جديدة + مسح التفضيلات المعروفة.
 *  - **8 تكرارات لكل حالة** (سلوك المنافس) وأي فشل في أي تكرار = فشل الحالة.
 *  - يُسجَّل لكل تكرار: النية، الثقة، الحالة المعرفية، النص، الزمن، سبب الفشل.
 *  - يُسجَّل `distinct_outputs`: نص مختلف لكل حالة — مقياس صريح للعشوائية (TemplateSelector/GreetingVariator).
 *
 * القيود المُعلنة (تُنشر في SUMMARY.md ولا تُخفى):
 *  - لا حقن لقطات (fixtures) في المحرّك بعد ⇒ حالات `network=fixture` تُعلَّم `skipped`.
 *  - تفضيلات SharedPreferences تُمسح بالقائمة المعروفة فقط؛ أي مفتاح جديد لم يُضف هنا يبقى بين الحالات.
 */
object EvalHarness {

  const val DEFAULT_REPEATS = 8
  const val DEFAULT_SEED = 20261026L

  private val KNOWN_PREFS = listOf(
    "zahra_unified_memory",
    "zahra_tasks_prefs",
    "zahra_notes_prefs",
    "zahra_schedules_prefs",
    "zahra_mood_journal",
    "zahra_chat_archive",
    "zahra_ui_preferences",
    "ZahraPersonalityPrefs",
    "ZahraPersonalityStorage",
    "zahra_knowledge_prefs",
    "zahra_translation_prefs",
    "zahra_religion_prefs",
  )

  data class Attempt(
    val repetition: Int,
    val passed: Boolean,
    val intent: String,
    val confidence: Double,
    val confidenceTier: String,
    val outcomeState: String,
    val text: String,
    val source: String?,
    val latencyMs: Double,
    val failureReason: String?,
  )

  data class CaseRun(
    val id: String,
    val domain: String,
    val question: String,
    val attempts: List<Attempt>,
    val status: String, // passed | failed | skipped | crashed
    val expectedIntent: String? = null,
    val distinctOutputs: Int,
    val note: String? = null,
  ) {
    val passed: Boolean get() = status == "passed"
    val counted: Boolean get() = status != "skipped"
    val medianLatencyMs: Double
      get() {
        if (attempts.isEmpty()) return 0.0
        val sorted = attempts.map { it.latencyMs }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
      }
    val failureReasons: List<String>
      get() = attempts.mapNotNull { it.failureReason }.distinct()
  }

  data class SuiteReport(
    val runs: List<CaseRun>,
    val repeats: Int,
    val seed: Long,
    val wallMs: Long,
    val codeCommit: String,
    val casesSha256: String,
    val startedAtUtc: String,
  ) {
    val counted get() = runs.filter { it.counted }
    val passed get() = counted.count { it.passed }
    val failed get() = counted.count { it.status == "failed" || it.status == "crashed" }
    val skipped get() = runs.count { !it.counted }
    val total get() = counted.size
  }

  fun run(
    context: Context,
    cases: List<EvalCase>,
    repeats: Int = DEFAULT_REPEATS,
    seed: Long = DEFAULT_SEED,
  ): SuiteReport {
    val startedAt = java.time.Instant.now().toString()
    val t0 = System.nanoTime()
    val runs = ArrayList<CaseRun>(cases.size)

    for (case in cases) {
      if (case.network == "fixture") {
        runs.add(
          CaseRun(
            id = case.id,
            domain = case.domain,
            question = case.question,
            attempts = emptyList(),
            status = "skipped",
            expectedIntent = case.expected_intent,
            distinctOutputs = 0,
            note = "fixture_not_injectable_yet",
          )
        )
        continue
      }
      runs.add(runCase(context, case, repeats, seed))
    }

    val wallMs = (System.nanoTime() - t0) / 1_000_000
    return SuiteReport(
      runs = runs,
      repeats = repeats,
      seed = seed,
      wallMs = wallMs,
      codeCommit = System.getenv("GITHUB_SHA") ?: gitCommit(),
      casesSha256 = try {
        EvalDataset.sha256(EvalDataset.casesFile())
      } catch (e: Exception) {
        "unavailable"
      },
      startedAtUtc = startedAt,
    )
  }

  private fun runCase(context: Context, case: EvalCase, repeats: Int, seed: Long): CaseRun {
    val attempts = ArrayList<Attempt>(repeats)
    var fatal: String? = null

    for (rep in 0 until repeats) {
      var router: ZahraRouter? = null
      try {
        router = ZahraRouter(context)
        resetState(router, context)
        // تغذية السياق (لحالات متعددة الأدوار) قبل قياس الدور الأخير
        for (turn in case.context_turns) {
          try {
            router.process(turn)
          } catch (e: Throwable) {
            // فشل دور سابق لا يُسقط الحالة: يُسجَّل كملاحظة عبر نص الاستجابة اللاحق
          }
        }
        val start = System.nanoTime()
        val response: ZahraResponse = router.process(case.question)
        val latencyMs = (System.nanoTime() - start) / 1_000_000.0
        val outcome = EvalScorer.score(
          case = case,
          text = response.text,
          source = response.source,
          outcomeState = response.outcomeState.name,
        )
        attempts.add(
          Attempt(
            repetition = rep + 1,
            passed = outcome.passed,
            intent = response.intent,
            confidence = response.confidence,
            confidenceTier = response.confidenceTier.name,
            outcomeState = response.outcomeState.name,
            text = response.text.take(600),
            source = response.source,
            latencyMs = latencyMs,
            failureReason = if (outcome.passed) null else outcome.reason,
          )
        )
      } catch (t: Throwable) {
        fatal = "${t.javaClass.simpleName}: ${t.message}"
        attempts.add(
          Attempt(
            repetition = rep + 1,
            passed = false,
            intent = "ERROR",
            confidence = 0.0,
            confidenceTier = "UNCERTAIN",
            outcomeState = "EXECUTION_ERROR",
            text = "",
            source = null,
            latencyMs = 0.0,
            failureReason = "exception: $fatal",
          )
        )
      } finally {
        try {
          router?.release()
        } catch (ignored: Throwable) {
          // إطلاق الموارد لا يجب أن يُسقط القياس
        }
      }
    }

    val status = when {
      fatal != null -> "crashed"
      attempts.all { it.passed } -> "passed"
      else -> "failed"
    }
    return CaseRun(
      id = case.id,
      domain = case.domain,
      question = case.question,
      attempts = attempts,
      status = status,
      expectedIntent = case.expected_intent,
      distinctOutputs = attempts.map { it.text }.distinct().size,
      note = if (fatal != null) fatal else null,
    )
  }

  /** عزل الحالة بين التكرارات — انظر قيود العزل في تعليق الكلاس. */
  fun resetState(router: ZahraRouter, context: Context) {
    runCatching { router.unifiedMemory.clearAll() }
    runCatching { router.contextManager.clearAllConversations() }
    runCatching { router.contextManager.startNewConversation() }
    runCatching { router.contextTracker.clear() }
    runCatching { router.memoryEngine.store.clearAll() }
    for (name in KNOWN_PREFS) {
      runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
  }

  private fun gitCommit(): String {
    val fromEnv = System.getenv("GITHUB_SHA")
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return runCatching {
      val root = System.getProperty("zahra.eval.root")
      val process = ProcessBuilder("git", "rev-parse", "HEAD")
        .directory(if (root != null) java.io.File(root) else null)
        .redirectErrorStream(true)
        .start()
      process.inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("unknown")
  }
}
