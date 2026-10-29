package com.example.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات مرجعية للإحصاءات (تعمل بلا Robolectric — أسرع حلقة تغذية راجعة).
 * نفس القيم المرجعية موجودة في scripts/test_stats.py، وأي اختلاف بين التنفيذين = فشل.
 */
class EvalStatsTest {

  @Test
  fun wilsonMatchesPublishedTable() {
    // 60/100 -> [0.502, 0.691] ، 0/20 -> [0.000, 0.161] ، 20/20 -> [0.839, 1.000]
    val (lo1, hi1) = EvalStats.wilson(60, 100)
    assertEquals(0.502, lo1, 0.002)
    assertEquals(0.691, hi1, 0.002)

    val (lo2, hi2) = EvalStats.wilson(0, 20)
    assertEquals(0.0, lo2, 1e-9)
    assertEquals(0.161, hi2, 0.002)

    val (lo3, hi3) = EvalStats.wilson(20, 20)
    assertEquals(1.0, hi3, 1e-9)
    assertEquals(0.839, lo3, 0.002)
  }

  @Test
  fun wilsonIsSymmetricAndBounded() {
    val (lo, hi) = EvalStats.wilson(3, 10)
    val (loMirror, hiMirror) = EvalStats.wilson(7, 10)
    assertEquals(1 - hi, loMirror, 1e-9)
    assertEquals(1 - lo, hiMirror, 1e-9)
    assertTrue(lo >= 0.0 && hi <= 1.0)
  }

  @Test
  fun bootstrapIsDeterministicForFixedSeed() {
    val values = List(30) { 1.0 } + List(70) { 0.0 }
    val a = EvalStats.bootstrapMeanCi(values, iters = 500, seed = 7L)
    val b = EvalStats.bootstrapMeanCi(values, iters = 500, seed = 7L)
    assertEquals(a.first, b.first, 0.0)
    assertEquals(a.second, b.second, 0.0)
    assertTrue("الفاصل يجب أن يحتوي المتوسط", a.first <= 0.30 && a.second >= 0.30)
  }

  @Test
  fun mcnemarMatchesExactBinomial() {
    assertEquals(0.0625, EvalStats.mcnemarExact(0, 5), 1e-9)
    assertEquals(1.0, EvalStats.mcnemarExact(3, 3), 1e-9)
    assertEquals(1.0, EvalStats.mcnemarExact(0, 0), 1e-9)
    val expected = 2 * (1 + 10) / 1024.0
    assertEquals(expected, EvalStats.mcnemarExact(1, 9), 1e-9)
  }

  @Test
  fun percentagesAreStable() {
    assertEquals("86.0%", EvalStats.pct(0.860, 1))
    assertEquals("0.0%", EvalStats.pct(0.0, 1))
  }
}
