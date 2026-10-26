package com.example.eval

import java.util.Locale
import java.util.Random

/**
 * إحصاءات العيّنة الصغيرة — المرحلة 0.
 *
 * 223 حالة + 50 held-out = «عيّنة صغيرة» بالأرقام، ولذلك لا ننشر نسبة مئوية
 * بدون فاصل ثقة (هذا هو التحفظ المسجّل على تقرير المنافس: 143 حالة ونسبة واحدة).
 *
 * التنفيذ مطابق لدوال Python في scripts/stats.py، والاختبارات المرجعية في
 * EvalStatsTest.kt وscripts/test_stats.py تتشارك نفس القيم المرجعية.
 */
object EvalStats {
  const val Z95: Double = 1.959963984540054

  /** فاصل ويلسون للنسبة — لا يخرج أبدًا خارج [0, 1] (Wilson, 1927). */
  fun wilson(successes: Int, total: Int, z: Double = Z95): Pair<Double, Double> {
    require(total > 0) { "total must be > 0" }
    require(successes in 0..total) { "successes must be within [0, total]" }
    val p = successes.toDouble() / total
    val denom = 1.0 + z * z / total
    val center = (p + z * z / (2.0 * total)) / denom
    val half = z * Math.sqrt(p * (1 - p) / total + z * z / (4.0 * total * total)) / denom
    return Pair(maxOf(0.0, center - half), minOf(1.0, center + half))
  }

  /** فاصل ثقة bootstrap للمتوسط ببذرة ثابتة (قابل للتكرار بالبايت). */
  fun bootstrapMeanCi(values: List<Double>, iters: Int = 2000, alpha: Double = 0.05, seed: Long = 20261026L): Pair<Double, Double> {
    require(values.isNotEmpty()) { "values must not be empty" }
    val rng = Random(seed)
    val n = values.size
    val means = DoubleArray(iters)
    for (i in 0 until iters) {
      var sum = 0.0
      for (j in 0 until n) sum += values[rng.nextInt(n)]
      means[i] = sum / n
    }
    means.sort()
    val lo = means[Math.floor(alpha / 2.0 * iters).toInt().coerceIn(0, iters - 1)]
    val hi = means[(Math.ceil((1 - alpha / 2.0) * iters).toInt() - 1).coerceIn(0, iters - 1)]
    return Pair(lo, hi)
  }

  /** اختبار ماكنيمار الدقيق (ذيلين) — b: نجح في B فقط، c: نجح في A فقط. */
  fun mcnemarExact(b: Int, c: Int): Double {
    require(b >= 0 && c >= 0) { "counts must be non-negative" }
    val n = b + c
    if (n == 0) return 1.0
    val k = minOf(b, c)
    var tail = 0.0
    for (i in 0..k) tail += comb(n, i)
    tail /= Math.pow(2.0, n.toDouble())
    return minOf(1.0, 2.0 * tail)
  }

  private fun comb(n: Int, k: Int): Double {
    var result = 1.0
    for (i in 1..k) result = result * (n - k + i) / i
    return result
  }

  fun pct(value: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f%%", value * 100)

  fun intervalText(successes: Int, total: Int): String {
    if (total <= 0) return "—"
    val (lo, hi) = wilson(successes, total)
    return "${pct(lo)}–${pct(hi)}"
  }
}
