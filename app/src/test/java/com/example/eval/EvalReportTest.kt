package com.example.eval

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * تشغيل القياس الكامل داخل Robolectric (AC-0.4 + AC-0.3).
 *
 * الأمر:
 *   ./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest'
 *
 * المخرجات: build/reports/zahra-eval/{results.json,SUMMARY.md}
 * أول تشغيل ينشئ eval/BASELINE.md بوضع `pending` (لا يُفشل البناء)؛ تحويله إلى
 * `recorded` قرار بشري موثّق في evidence/phase-00/ — بعدها أي انحدار يُفشل الاختبار.
 */
@RunWith(RobolectricTestRunner::class)
class EvalReportTest {

  @Test
  fun runFullSuite() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val cases = EvalDataset.loadAll()
    assertTrue("ملف الأسئلة فارغ — راجع eval/cases.jsonl", cases.size >= 200)

    val repeats = System.getProperty("zahra.eval.repeats")?.toIntOrNull() ?: EvalHarness.DEFAULT_REPEATS
    val seed = System.getProperty("zahra.eval.seed")?.toLongOrNull() ?: EvalHarness.DEFAULT_SEED

    println("زهرة/قياس: ${cases.size} حالة × $repeats تكرار (seed=$seed, commit=${System.getenv("GITHUB_SHA") ?: "local"})")
    val report = EvalHarness.run(context, cases, repeats, seed)
    EvalReport.writeAll(report)

    println(
      "زهرة/قياس: ${report.passed}/${report.total} ناجحة · ${report.failed} فاشلة · ${report.skipped} skipped · ${report.wallMs}ms"
    )
    println("زهرة/قياس: التقرير في ${EvalReport.outDir().absolutePath}")

    val baseline = EvalReport.baselineFile()
    if (!baseline.exists()) {
      baseline.parentFile?.mkdirs()
      baseline.writeText(EvalReport.baselineMarkdown(report, "pending"), Charsets.UTF_8)
      println("زهرة/قياس: أُنشئ خط أساس مبدئي (mode=pending) في ${baseline.absolutePath}")
    }

    EvalReport.regressionMessage(report)?.let { message -> fail(message) }
    assertTrue("يجب أن تُقاس 200 حالة على الأقل، والمقيس الآن ${report.total}", report.total >= 200)
  }
}
