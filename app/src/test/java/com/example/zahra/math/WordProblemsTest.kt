package com.example.zahra.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات المسائل الكلامية (المسار C): كل قالب له حالة موجبة وحالة سلبية. */
class WordProblemsTest {

  private fun solved(input: String): WordProblemResult.Solved {
    val result = WordProblems.solve(input)
    assertTrue("كان يجب حل: $input — النتيجة: $result", result is WordProblemResult.Solved)
    return result as WordProblemResult.Solved
  }

  private fun unsolved(input: String) {
    assertTrue("كان يجب ألا يُحسب: $input", WordProblems.solve(input) is WordProblemResult.NotApplicable)
  }

  @Test
  fun discountValue() {
    assertEquals(400.0, solved("500 وخصم 20%").value, 1e-9)
    assertEquals("discount_value", solved("500 وخصم 20%").templateId)
  }

  @Test
  fun discountBasket() {
    val result = solved("اشتريت 3 حاجات بـ 25 جنيه وخصم 10%")
    assertEquals(67.5, result.value, 1e-9)
    assertEquals("discount_basket", result.templateId)
  }

  @Test
  fun percentOf() {
    assertEquals(100.0, solved("20% من 500").value, 1e-9)
    assertEquals(150.0, solved("نسبة 30% من 500").value, 1e-9)
  }

  @Test
  fun percentIncrease() {
    assertEquals(1150.0, solved("1000 وبعد زيادة 15%").value, 1e-9)
  }

  @Test
  fun average() {
    assertEquals(7.333333333333333, solved("متوسط 4 و8 و10").value, 1e-9)
  }

  @Test
  fun circleArea_radiusVersusDiameter() {
    val radius = solved("مساحة دائرة نصف قطرها 5")
    assertEquals(Math.PI * 25, radius.value, 1e-9)
    assertEquals("circle_area_radius", radius.templateId)

    val diameter = solved("مساحة دائرة قطرها 5")
    assertEquals(Math.PI * 6.25, diameter.value, 1e-9)
    assertEquals("circle_area_diameter", diameter.templateId)
    // ملاحظة موثّقة: الرقم 19.635 في نص الخطة يكافئ دائرة قُطرها 5 (نصف قطرها 2.5)
    assertEquals(19.6349540849, diameter.value, 1e-6)
  }

  @Test
  fun rectangleArea() {
    assertEquals(24.0, solved("مساحة مستطيل طوله 4 وعرضه 6").value, 1e-9)
  }

  @Test
  fun remainingMoney() {
    assertEquals(650.0, solved("لو معايا 1000 وصرفت 350 كام يفضل").value, 1e-9)
  }

  @Test
  fun arabicIndicDigits_areHandled() {
    assertEquals(400.0, solved("٥٠٠ وخصم ٢٠%").value, 1e-9)
  }

  // ——— حالات سلبية: لا حساب بالغلط ———
  @Test
  fun negativeCases_areNotCalculated() {
    unsolved("الخصم في المحل 20%")          // نسبة بلا قيمة أصلية
    unsolved("مساحة الدائرة")                // لا نصف قطر ولا قطر
    unsolved("متوسط")                        // لا أرقام
    unsolved("ازاي اتعلم البرمجة")            // ليست مسألة
    unsolved("عايز أشتري تليفون بخصم")        // خصم بلا أرقام
  }

  @Test
  fun discountAboveHundred_isRejected() {
    unsolved("500 وخصم 120%")
  }

  @Test
  fun lookLikeWordProblem_detection() {
    assertTrue(WordProblems.looksLikeWordProblem("500 وخصم 20%"))
    assertFalse(WordProblems.looksLikeWordProblem("مرحبا يا زهرة"))
  }
}
