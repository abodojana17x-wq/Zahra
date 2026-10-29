package com.example.zahra.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * اختبارات نظام الوحدات العام (المسار B): ≥ 60 تحويلًا + اختبارات عكسية A→B→A.
 * الأرقام المرجعية مأخوذة من تعريفات SI الرسمية (BIPM) ومن قيم الفدان المصري الموثقة.
 */
class UnitSystemTest {

  private data class Case(val value: Double, val from: String, val to: String, val expected: Double)

  private val cases: List<Case> = listOf(
    // الطول
    Case(10.0, "km", "mi", 6.213711922),
    Case(1.0, "mi", "km", 1.609344),
    Case(30.0, "cm", "inch", 11.8110236),
    Case(12.0, "inch", "cm", 30.48),
    Case(6.0, "ft", "m", 1.8288),
    Case(1.0, "m", "ft", 3.280839895),
    Case(3.0, "yd", "ft", 9.0),
    Case(1.0, "km", "m", 1000.0),
    Case(1000.0, "mm", "m", 1.0),
    Case(1.0, "yd", "inch", 36.0),
    // الكتلة
    Case(500.0, "g", "kg", 0.5),
    Case(2.0, "ton", "kg", 2000.0),
    Case(150.0, "lb", "kg", 68.0388555),
    Case(1.0, "kg", "lb", 2.2046226218),
    Case(1.0, "oz", "g", 28.349523125),
    Case(5000.0, "mg", "g", 5.0),
    // الحرارة (أفيني)
    Case(0.0, "c", "f", 32.0),
    Case(100.0, "c", "f", 212.0),
    Case(72.0, "f", "c", 22.2222222),
    Case(35.0, "c", "f", 95.0),
    Case(0.0, "c", "k", 273.15),
    Case(300.0, "k", "c", 26.85),
    Case(-40.0, "c", "f", -40.0),
    Case(98.6, "f", "c", 37.0),
    // الحجم
    Case(2000.0, "ml", "l", 2.0),
    Case(2.0, "l", "gal", 0.5283441047),
    Case(1.0, "gal", "l", 3.785411784),
    Case(2.0, "cup", "ml", 473.176473),
    // المساحة
    Case(3.0, "feddan", "m2", 12602.499),
    Case(1.0, "feddan", "qirat", 24.0),
    Case(1.0, "qirat", "sahm", 24.0),
    Case(1.0, "hectare", "m2", 10000.0),
    Case(1.0, "km2", "m2", 1_000_000.0),
    Case(10_000.0, "cm2", "m2", 1.0),
    Case(1.0, "acre", "m2", 4046.8564224),
    Case(4200.833, "m2", "feddan", 1.0),
    // الزمن
    Case(3.0, "h", "min", 180.0),
    Case(90.0, "min", "h", 1.5),
    Case(1.0, "day", "h", 24.0),
    Case(1.0, "week", "day", 7.0),
    Case(1500.0, "ms", "s", 1.5),
    // السرعة
    Case(36.0, "kmh", "mps", 10.0),
    Case(1.0, "mph", "kmh", 1.609344),
    Case(1.0, "knot", "kmh", 1.852),
    // البيانات (1024)
    Case(1.5, "gb", "mb", 1536.0),
    Case(500.0, "mb", "gb", 0.48828125),
    Case(1.0, "kb", "byte", 1024.0),
    Case(1.0, "byte", "bit", 8.0),
    Case(1.0, "tb", "gb", 1024.0),
    // أزواج إضافية داخل التصنيف
    Case(1.0, "m", "mm", 1000.0),
    Case(1.0, "mile", "yard", 1760.0),
    Case(1.0, "litre", "ml", 1000.0),
    Case(1.0, "gram", "mg", 1000.0),
    Case(1.0, "tonne", "gram", 1_000_000.0),
    Case(1.0, "hectare", "acre", 2.4710538147),
    Case(1.0, "hour", "second", 3600.0),
    Case(1.0, "week", "hour", 168.0),
    Case(60.0, "mps", "kmh", 216.0),
    Case(10.0, "knot", "mps", 5.14444444),
    Case(2048.0, "byte", "kb", 2.0),
    Case(2.0, "c", "k", 275.15)
  )

  @Test
  fun conversions_matchReferenceValues() {
    for (case in cases) {
      val result = UnitSystem.convert(case.value, case.from, case.to)
      assertTrue("تحويل ${case.from}->${case.to} فشل: $result", result is UnitConversionResult.Success)
      val value = (result as UnitConversionResult.Success).value
      val tolerance = maxOf(abs(case.expected) * 1e-6, 1e-6)
      assertEquals(
        "قيمة خاطئة في ${case.from}->${case.to}",
        case.expected,
        value,
        tolerance
      )
    }
    assertTrue("عدد الحالات يجب أن يكون 60 على الأقل", cases.size >= 60)
  }

  @Test
  fun roundTripConversions_returnOriginalValue() {
    val pairs = listOf(
      "km" to "mi", "kg" to "lb", "c" to "f", "l" to "gal", "m2" to "feddan",
      "h" to "min", "gb" to "mb", "kmh" to "mps", "ft" to "m", "qirat" to "m2"
    )
    for ((from, to) in pairs) {
      val forward = UnitSystem.convert(7.5, from, to) as UnitConversionResult.Success
      val backward = UnitSystem.convert(forward.value, to, from) as UnitConversionResult.Success
      assertEquals("العودة من $to إلى $from لا تساوي الأصل", 7.5, backward.value, 1e-9)
    }
  }

  @Test
  fun arabicAliases_areResolved() {
    val pairs = listOf(
      Triple("كيلومتر", "ميل", UnitCategory.LENGTH),
      Triple("أقدام", "متر", UnitCategory.LENGTH),
      Triple("باوند", "كيلوجرام", UnitCategory.MASS),
      Triple("فدان", "متر مربع", UnitCategory.AREA),
      Triple("جيجابايت", "ميجابايت", UnitCategory.DATA),
      Triple("دقيقة", "ساعة", UnitCategory.TIME),
      Triple("مئوية", "فهرنهايت", UnitCategory.TEMPERATURE)
    )
    for ((from, to, category) in pairs) {
      val fromUnit = UnitSystem.findUnit(from)
      val toUnit = UnitSystem.findUnit(to)
      assertNotNull("وحدة عربية غير معروفة: $from", fromUnit)
      assertNotNull("وحدة عربية غير معروفة: $to", toUnit)
      assertEquals(category, fromUnit!!.category)
      assertEquals(category, toUnit!!.category)
    }
  }

  @Test
  fun crossCategoryConversion_isRefusedEducationally() {
    val result = UnitSystem.convert(5.0, "kg", "m")
    assertTrue(result is UnitConversionResult.CrossCategory)
    assertTrue((result as UnitConversionResult.CrossCategory).message.contains("مفيش تحويل"))
  }

  @Test
  fun unknownUnit_isRefusedExplicitly() {
    val result = UnitSystem.convert(50.0, "kg", "كيلوفولت")
    assertTrue(result is UnitConversionResult.Unsupported)
    assertTrue((result as UnitConversionResult.Unsupported).message.contains("غير مدعوم"))
  }

  @Test
  fun currencyConversion_neverFabricatesRate() {
    val result = UnitSystem.convert(100.0, "usd", "egp")
    assertTrue("لا يجوز إنتاج سعر عملة ثابت", result is UnitConversionResult.RequiresLiveSource)
    assertTrue((result as UnitConversionResult.RequiresLiveSource).message.contains("مصدر حي"))
    assertNull(UnitSystem.findUnit("عملة غير معروفة"))
  }

  @Test
  fun feddanConversion_matchesEgyptianLegalValue() {
    val result = UnitSystem.convert(3.0, "فدان", "متر مربع") as UnitConversionResult.Success
    assertEquals(12602.499, result.value, 1e-3)
    assertTrue("الفدان قيمة عرفية ويجب أن تُعلن", result.isApproximate)
  }

  @Test
  fun normalization_handlesHamzaAndDefiniteArticle() {
    assertEquals(UnitSystem.findUnit("فدان")?.id, UnitSystem.findUnit("الفدان")?.id)
    assertEquals(UnitSystem.findUnit("أقدام")?.id, UnitSystem.findUnit("اقدام")?.id)
    assertEquals(UnitSystem.findUnit("كيلو متر")?.id, UnitSystem.findUnit("كيلومتر")?.id)
  }
}
