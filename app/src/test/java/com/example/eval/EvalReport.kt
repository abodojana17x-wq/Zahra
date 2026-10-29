package com.example.eval

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * كتابة تقرير القياس وحراسة الانحدار — المرحلة 0.
 *
 * المخرجات (AC-0.4/AC-0.3):
 *   <build>/reports/zahra-eval/results.json    — كل الحالات وكل التكرارات
 *   <build>/reports/zahra-eval/SUMMARY.md      — جدول لكل مجال + فاصل ويلسون + قائمة الفشل
 *   eval/BASELINE.md                           — خط الأساس المجمّد (يُكتب مرة واحدة ثم يُراجع يدويًا)
 */
object EvalReport {

  private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

  fun outDir(): File {
    val fromProp = System.getProperty("zahra.eval.outDir")
    return if (!fromProp.isNullOrBlank()) File(fromProp) else File("build/reports/zahra-eval")
  }

  fun baselineFile(): File {
    val fromProp = System.getProperty("zahra.eval.baseline")
    return if (!fromProp.isNullOrBlank()) File(fromProp) else File(EvalDataset.dir(), "BASELINE.md")
  }

  fun writeAll(report: EvalHarness.SuiteReport) {
    val dir = outDir()
    dir.mkdirs()
    File(dir, "results.json").writeText(resultsJson(report), Charsets.UTF_8)
    File(dir, "SUMMARY.md").writeText(summaryMarkdown(report), Charsets.UTF_8)
  }

  // ------------------------------------------------------------------------------------
  // results.json
  // ------------------------------------------------------------------------------------
  fun resultsJson(report: EvalHarness.SuiteReport): String {
    val root = JsonObject()

    val meta = JsonObject()
    meta.addProperty("tool", "Zahra EvalHarness")
    meta.addProperty("phase", "00")
    meta.addProperty("started_at_utc", report.startedAtUtc)
    meta.addProperty("commit", report.codeCommit)
    meta.addProperty("cases_sha256", report.casesSha256)
    meta.addProperty("repeats", report.repeats)
    meta.addProperty("seed", report.seed)
    meta.addProperty("wall_ms", report.wallMs)
    meta.addProperty("total_counted", report.total)
    meta.addProperty("passed", report.passed)
    meta.addProperty("failed", report.failed)
    meta.addProperty("skipped", report.skipped)
    root.add("meta", meta)

    val domains = JsonArray()
    for (d in domainStats(report)) {
      val obj = JsonObject()
      obj.addProperty("domain", d.domain)
      obj.addProperty("passed", d.passed)
      obj.addProperty("total", d.total)
      obj.addProperty("rate", d.rate)
      obj.addProperty("wilson_low", d.low)
      obj.addProperty("wilson_high", d.high)
      domains.add(obj)
    }
    root.add("domains", domains)

    val cases = JsonArray()
    for (run in report.runs) {
      val obj = JsonObject()
      obj.addProperty("id", run.id)
      obj.addProperty("domain", run.domain)
      obj.addProperty("question", run.question)
      obj.addProperty("status", run.status)
      obj.addProperty("passed", run.passed)
      obj.addProperty("distinct_outputs", run.distinctOutputs)
      obj.addProperty("expected_intent", run.expectedIntent)
      val actualIntents = run.attempts.map { it.intent }.distinct()
      obj.addProperty("actual_intents", actualIntents.joinToString("|"))
      obj.addProperty("latency_ms_median", run.medianLatencyMs)
      if (run.note != null) obj.addProperty("note", run.note)
      val reasons = JsonArray()
      run.failureReasons.forEach { reasons.add(it) }
      obj.add("failure_reasons", reasons)
      val attempts = JsonArray()
      for (a in run.attempts) {
        val ao = JsonObject()
        ao.addProperty("repetition", a.repetition)
        ao.addProperty("passed", a.passed)
        ao.addProperty("intent", a.intent)
        ao.addProperty("confidence", a.confidence)
        ao.addProperty("confidence_tier", a.confidenceTier)
        ao.addProperty("outcome_state", a.outcomeState)
        ao.addProperty("source", a.source)
        ao.addProperty("latency_ms", a.latencyMs)
        ao.addProperty("text", a.text)
        if (a.failureReason != null) ao.addProperty("failure_reason", a.failureReason)
        attempts.add(ao)
      }
      obj.add("attempts", attempts)
      cases.add(obj)
    }
    root.add("cases", cases)

    return gson.toJson(root)
  }

  // ------------------------------------------------------------------------------------
  // SUMMARY.md
  // ------------------------------------------------------------------------------------
  data class DomainStat(val domain: String, val passed: Int, val total: Int, val rate: Double, val low: Double, val high: Double)

  fun domainStats(report: EvalHarness.SuiteReport): List<DomainStat> {
    return report.counted
      .groupBy { it.domain }
      .toSortedMap()
      .map { (domain, runs) ->
        val passed = runs.count { it.passed }
        val total = runs.size
        val (low, high) = EvalStats.wilson(passed, total)
        DomainStat(domain, passed, total, passed.toDouble() / total, low, high)
      }
  }

  fun summaryMarkdown(report: EvalHarness.SuiteReport): String {
    val sb = StringBuilder()
    sb.appendLine("# تقرير قياس زهرة — المرحلة 0")
    sb.appendLine()
    sb.appendLine("- التاريخ (UTC): ${Instant.now()}")
    sb.appendLine("- الـ commit: `${report.codeCommit}`")
    sb.appendLine("- هاش مجموعة الأسئلة (cases.jsonl): `${report.casesSha256}`")
    sb.appendLine("- التكرارات لكل حالة: **${report.repeats}** · البذرة: ${report.seed} · زمن التشغيل: ${report.wallMs}ms")
    sb.appendLine("- الحالات: **${report.total}** محسوبة · ${report.passed} ناجحة · ${report.failed} فاشلة · ${report.skipped} معلَّمة كـskipped")
    sb.appendLine()
    sb.appendLine("## جدول المجالات (فاصل ويلسون 95%)")
    sb.appendLine()
    sb.appendLine("| المجال | ناجح/الكل | النسبة | فاصل 95% |")
    sb.appendLine("|---|---|---|---|")
    for (d in domainStats(report)) {
      sb.appendLine("| ${d.domain} | ${d.passed}/${d.total} | ${EvalStats.pct(d.rate)} | ${EvalStats.pct(d.low)}–${EvalStats.pct(d.high)} |")
    }
    val overallRate = if (report.total == 0) 0.0 else report.passed.toDouble() / report.total
    sb.appendLine("| **الإجمالي** | **${report.passed}/${report.total}** | **${EvalStats.pct(overallRate)}** | **${EvalStats.intervalText(report.passed, report.total)}** |")
    sb.appendLine()

    val latencies = report.counted.map { it.medianLatencyMs }.filter { it > 0 }
    if (latencies.isNotEmpty()) {
      val (lo, hi) = EvalStats.bootstrapMeanCi(latencies)
      // دقة توجيه النية (Phase 1 ستضبطها؛ هنا نُثبّت خط الأساس)
    val intentCases = report.counted.filter { !it.expectedIntent.isNullOrBlank() }
    if (intentCases.isNotEmpty()) {
      val matched = intentCases.count { run ->
        run.attempts.isNotEmpty() && run.attempts.all { it.intent == run.expectedIntent }
      }
      sb.appendLine("## دقة توجيه النية (intent accuracy)")
      sb.appendLine()
      sb.appendLine("- مطابقة كاملة: **$matched/${intentCases.size}** (${EvalStats.pct(matched.toDouble() / intentCases.size)}) · فاصل 95%: ${EvalStats.intervalText(matched, intentCases.size)}")
      sb.appendLine("- المنهج: كل تكرار يجب أن يطابق `expected_intent`؛ الحالة تُحسب مطابقة فقط لو طابقت كل التكرارات.")
      sb.appendLine()
    }

    sb.appendLine("## الزمن")
      sb.appendLine()
      sb.appendLine("- متوسط زمن الرد الوسيط: ${String.format(Locale.US, "%.1f", latencies.average())}ms")
      sb.appendLine("- bootstrap 95% للمتوسط: ${String.format(Locale.US, "%.1f", lo)}–${String.format(Locale.US, "%.1f", hi)}ms")
      sb.appendLine()
    }

    sb.appendLine("## أكثر أسباب الفشل تكرارًا")
    sb.appendLine()
    val reasons = report.counted
      .flatMap { run -> run.failureReasons.map { it.substringBefore('(').substringBefore(':') to run.id } }
      .groupBy({ it.first }, { it.second })
      .toList()
      .sortedByDescending { it.second.size }
      .take(15)
    if (reasons.isEmpty()) {
      sb.appendLine("لا فشل — (غير متوقع في المرحلة 0؛ راجع التقييم قبل الثقة).")
    } else {
      sb.appendLine("| السبب | العدد | أمثلة |")
      sb.appendLine("|---|---|---|")
      for ((reason, ids) in reasons) {
        sb.appendLine("| `$reason` | ${ids.size} | ${ids.take(4).joinToString(", ")} |")
      }
    }
    sb.appendLine()

    sb.appendLine("## حالات التباين (نص مختلف لنفس السؤال)")
    sb.appendLine()
    val variable = report.counted.filter { it.distinctOutputs > 1 }.sortedByDescending { it.distinctOutputs }.take(15)
    if (variable.isEmpty()) {
      sb.appendLine("لا يوجد تباين في النصوص داخل هذا التشغيل.")
    } else {
      sb.appendLine("| الحالة | عدد النصوص المختلفة | الحالة النهائية |")
      sb.appendLine("|---|---|---|")
      for (run in variable) sb.appendLine("| ${run.id} | ${run.distinctOutputs}/${report.repeats} | ${run.status} |")
    }
    sb.appendLine()

    val failedIds = report.counted.filter { !it.passed }.map { "${it.id}(${it.domain})" }
    sb.appendLine("## الحالات الفاشلة (${failedIds.size})")
    sb.appendLine()
    if (failedIds.isEmpty()) {
      sb.appendLine("لا يوجد.")
    } else {
      sb.appendLine(failedIds.joinToString("، "))
    }
    sb.appendLine()

    val skippedIds = report.runs.filter { !it.counted }.map { "${it.id}${it.note?.let { n -> " ($n)" } ?: ""}" }
    if (skippedIds.isNotEmpty()) {
      sb.appendLine("## حالات skipped (غير محسوبة في النسبة)")
      sb.appendLine()
      sb.appendLine(skippedIds.joinToString("، "))
      sb.appendLine()
    }

    sb.appendLine("## قيود مُعلنة (لا تُخفى)")
    sb.appendLine()
    sb.appendLine("- حالات الطقس من نوع `network=fixture` معلَّمة `skipped` لأن المحرّك لا يقبل حقن لقطات بعد (المرحلة 2/5).")
    sb.appendLine("- التقييم معجمي/رقمي حتمي: لا محكّم نموذجي، والتطبيع العربي موثّق في `EvalScorer.kt`.")
    sb.appendLine("- قياس الوحدات يقبل أي رقم في النص يطابق القيمة المتوقعة داخل التفاوت — تساهل مقصود ومُعلن.")
    sb.appendLine("- العزل بين الحالات: `clearAll` + محادثة جديدة + مسح تفضيلات معروفة؛ المفاتيح غير المسجّلة تبقى.")
    return sb.toString()
  }

  // ------------------------------------------------------------------------------------
  // BASELINE.md + حراسة الانحدار
  // ------------------------------------------------------------------------------------
  fun baselineJson(report: EvalHarness.SuiteReport, mode: String): JsonObject {
    val obj = JsonObject()
    obj.addProperty("version", "v1")
    obj.addProperty("mode", mode)
    obj.addProperty("cases_sha256", report.casesSha256)
    obj.addProperty("repeats", report.repeats)
    obj.addProperty("seed", report.seed)
    obj.addProperty("total", report.total)
    obj.addProperty("passed", report.passed)
    obj.addProperty("failed", report.failed)
    obj.addProperty("skipped", report.skipped)
    obj.addProperty("commit", report.codeCommit)
    obj.addProperty("recorded_at_utc", report.startedAtUtc)
    return obj
  }

  fun baselineMarkdown(report: EvalHarness.SuiteReport, mode: String): String {
    val sb = StringBuilder()
    sb.appendLine("# خط أساس زهرة — المرحلة 0")
    sb.appendLine()
    sb.appendLine("> ⚠️ هذا الملف يُكتب تلقائيًا مرة واحدة من أول تشغيل حقيقي، ثم **يُجمَّد** (لا يُعدَّل إلا بإصدار جديد للمجموعة).")
    sb.appendLine()
    sb.appendLine("- mode: `$mode` (`recorded` = خط أساس مجمّد يُحرس؛ `pending` = لم يُشغَّل بعد)")
    sb.appendLine("- هاش المجموعة: `${report.casesSha256}`")
    sb.appendLine("- التكرارات: ${report.repeats} · البذرة: ${report.seed}")
    sb.appendLine("- النتيجة: **${report.passed}/${report.total}** (${EvalStats.pct(if (report.total == 0) 0.0 else report.passed.toDouble() / report.total)})")
    sb.appendLine("- فاصل ويلسون 95%: ${EvalStats.intervalText(report.passed, report.total)}")
    sb.appendLine("- الـ commit: `${report.codeCommit}` · التاريخ: ${report.startedAtUtc}")
    sb.appendLine()
    sb.appendLine("## الكتلة الآلية (لا تُعدَّل يدويًا)")
    sb.appendLine()
    sb.appendLine("```json")
    sb.appendLine(gson.toJson(baselineJson(report, mode)))
    sb.appendLine("```")
    sb.appendLine()
    sb.appendLine("## الطريقة")
    sb.appendLine()
    sb.appendLine("```bash")
    sb.appendLine("./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest'")
    sb.appendLine("```")
    return sb.toString()
  }

  /** يقرأ كتلة json من BASELINE.md — يرجع null لو الملف غير موجود أو بلا كتلة. */
  fun readBaselineText(file: File): JsonObject? {
    if (!file.exists()) return null
    val content = file.readText(Charsets.UTF_8)
    val match = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").find(content) ?: return null
    return runCatching { JsonParser.parseString(match.groupValues[1]).asJsonObject }.getOrNull()
  }

  /**
   * يرجع رسالة فشل لو النتيجة أقل من خط الأساس، وnull لو لا يوجد خط أساس مجمّد.
   * (الحراسة تُنفّذ أيضًا في CI عبر scripts/check_regression.py للتحقق المزدوج.)
   */
  fun regressionMessage(report: EvalHarness.SuiteReport): String? {
    val baseline = readBaselineText(baselineFile()) ?: return null
    val mode = baseline.get("mode")?.asString ?: "pending"
    if (mode != "recorded") return null
    val baselineCases = baseline.get("cases_sha256")?.asString
    if (baselineCases != null && baselineCases != report.casesSha256) {
      return "خط الأساس محسوب على مجموعة مختلفة ($baselineCases ≠ ${report.casesSha256}) — أعد القياس أو أنشئ إصدار v2 من المجموعة."
    }
    val passed = baseline.get("passed")?.asInt ?: return null
    if (report.passed < passed) {
      return "انحدار: ${report.passed}/${report.total} أقل من خط الأساس $passed/${baseline.get("total")?.asInt ?: report.total}. راجع SUMMARY.md."
    }
    return null
  }
}
