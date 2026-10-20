package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.intelligence.classifier.CalibratedClassifier
import com.example.zahra.intelligence.features.NlpFeatureExtractor
import com.example.zahra.intelligence.planner.StructuredTaskPlanner
import com.example.zahra.security.ZahraCrypto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
class Phase5PerformanceBenchmarkTest {

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

  @Test
  fun benchmarkFeatureExtractionLatency() {
    val sampleText = "احسب 250 * 4 ثم احفظ الناتج في ملاحظة هامة للغد"
    // Warmup
    repeat(10) { NlpFeatureExtractor.extract(sampleText) }

    // Benchmark 100 iterations
    val iterations = 100
    val totalNanos = measureNanoTime {
      repeat(iterations) {
        val f = NlpFeatureExtractor.extract(sampleText)
        assertNotNull(f)
      }
    }
    val avgMillis = (totalNanos / 1_000_000.0) / iterations
    println("Benchmark: Average Feature Extraction Latency = ${avgMillis} ms")
    assertTrue("Feature extraction should be fast (< 5ms on JVM)", avgMillis < 5.0)
  }

  @Test
  fun benchmarkCompoundPlanningAndExecutionLatency() {
    val compoundInput = "احسب 40 * 5 ثم احفظ الناتج في ملاحظة"
    // Warmup
    val pWarm = StructuredTaskPlanner.plan(compoundInput)
    router.planExecutor.execute(pWarm)

    // Benchmark 50 executions
    val iterations = 50
    val totalNanos = measureNanoTime {
      repeat(iterations) {
        val plan = StructuredTaskPlanner.plan(compoundInput)
        val res = router.planExecutor.execute(plan)
        assertTrue(res.isCompleted)
      }
    }
    val avgMillis = (totalNanos / 1_000_000.0) / iterations
    println("Benchmark: Average Compound Plan Execution Latency = ${avgMillis} ms")
    assertTrue("Compound execution should be under 20ms", avgMillis < 20.0)
  }

  @Test
  fun benchmarkCalibratedClassifierInferenceLatency() {
    val classifier = CalibratedClassifier()
    val corpus = mapOf(
      "calculate" to listOf("احسب 25 * 4", "كم حاصل جمع 5 و 10", "ضرب وقسمة"),
      "create_task" to listOf("أضف مهمة جديدة", "سجل مهمة للغد", "مهمة إرسال التقرير"),
      "translate" to listOf("ترجم هذه الكلمة", "ترجم إلى الإنجليزية", "translate this")
    )
    classifier.train(corpus)

    // Warmup
    repeat(10) { classifier.classify("احسب 100 + 20") }

    val iterations = 100
    val totalNanos = measureNanoTime {
      repeat(iterations) {
        val res = classifier.classify("احسب 100 + 20")
        assertNotNull(res)
      }
    }
    val avgMillis = (totalNanos / 1_000_000.0) / iterations
    println("Benchmark: Average Calibrated Inference Latency = ${avgMillis} ms")
    assertTrue("Calibrated classification inference should be < 3ms", avgMillis < 3.0)
  }
}
