package com.example.zahra.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * اختبارات أدوات الوقت/الهجري بساعة ثابتة (لا اعتماد على وقت التشغيل).
 *
 * الساعة المرجعية: 2026-10-29 15:07 بتوقيت القاهرة (UTC+2 في هذا التاريخ).
 */
class TimeToolsTest {

  private val zone: TimeZone = TimeZone.getTimeZone("Africa/Cairo")
  private val referenceNow: Long = fixedEpoch(2026, Calendar.OCTOBER, 29, 15, 7)

  private fun fixedEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance(zone).apply {
      clear()
      set(year, month, day, hour, minute, 0)
    }.timeInMillis

  private fun answer(question: String): TimeAnswer? =
    TimeTools.answer(question, nowMs = referenceNow, zone = zone)

  @Test
  fun clockQuestion_returnsFormattedTime() {
    val result = answer("الساعة كام دلوقتي؟")
    assertNotNull(result)
    assertEquals("ask_time", result!!.intent)
    assertTrue("يجب أن يحتوي الوقت بصيغة HH:mm — النص: ${result.text}", Regex("\\d{1,2}[:.]\\d{2}").containsMatchIn(result.text))
    assertTrue(result.text.contains("03:07") || result.text.contains("15:07"))
  }

  @Test
  fun dateQuestion_returnsGregorianDate() {
    val result = answer("النهاردة كام؟")
    assertNotNull(result)
    assertEquals("ask_date", result!!.intent)
    assertTrue(result.text.contains("29/10/2026"))
    assertTrue(result.text.contains("الخميس"))
  }

  @Test
  fun weekdayQuestion_answersDayName() {
    val result = answer("النهاردة يوم إيه؟")
    assertNotNull(result)
    assertTrue(result!!.text.contains("الخميس"))
  }

  @Test
  fun tomorrowAndYesterday_areRelativeToInjectedClock() {
    val tomorrow = answer("بكرة يوافق كام؟")!!
    assertTrue(tomorrow.text.contains("30/10/2026"))

    val yesterday = answer("امبارح كان يوم إيه؟")!!
    assertTrue(yesterday.text.contains("الأربعاء"))

    val dayAfter = answer("بعد بكرة تاريخه كام؟")!!
    assertTrue(dayAfter.text.contains("31/10/2026"))
  }

  @Test
  fun hijriDate_containsHijriMonthName() {
    val result = answer("إيه التاريخ الهجري النهاردة؟")
    assertNotNull(result)
    assertTrue("يجب ذكر الشهر الهجري — النص: ${result!!.text}", Regex("(محرم|صفر|ربيع|جمادى|رجب|شعبان|رمضان|شوال|ذو)").containsMatchIn(result.text))
    assertTrue(result.text.contains("هـ"))
  }

  @Test
  fun hijriMonth_questionReturnsCurrentMonth() {
    val result = answer("الشهر الهجري الحالي إيه؟")
    assertNotNull(result)
    assertTrue(result!!.text.contains("الشهر الهجري"))
  }

  @Test
  fun countdownToFriday_isComputed() {
    val result = answer("كام يوم للجمعة؟")
    assertNotNull(result)
    assertTrue("باقي يوم واحد من الخميس للجمعة — النص: ${result!!.text}", result.text.contains("1"))
    assertTrue(result.text.contains("الجمعة"))
  }

  @Test
  fun schedulingCommands_areNeverHijacked() {
    assertNull(answer("فكرني بكرة الساعة 8 بمراجعة البحث"))
    assertNull(answer("ذكرني الساعة 5 مساءً بموعد الطبيب"))
    assertNull(answer("جدول لي النهاردة مهمة"))
  }

  @Test
  fun devotionalTimes_areNotHijacked() {
    assertNull(answer("مواقيت الصلاة النهاردة"))
  }

  @Test
  fun unrelatedText_returnsNull() {
    assertNull(answer("مرحبا يا زهرة"))
    assertNull(answer("احسب 25 * 4"))
  }

  @Test
  fun ummAlQuraTable_matchesKnownAnchors() {
    val calculator = UmmAlQuraHijriCalculator()
    val utc = TimeZone.getTimeZone("UTC")
    val epoch2018 = fixedEpochUtc(2018, Calendar.SEPTEMBER, 11, 0, 0)
    val anchor = calculator.fromEpochMillis(epoch2018, utc)
    assertEquals(1440, anchor.year)
    assertEquals(1, anchor.month)
    assertEquals(1, anchor.day)

    val today = calculator.fromEpochMillis(referenceNow, zone)
    assertEquals(1448, today.year)
    assertEquals(5, today.month)
    assertEquals(18, today.day)
    assertEquals("جمادى الأولى", today.monthNameAr)
  }

  @Test
  fun ummAlQuraTable_stepsOneDayAtATime() {
    val calculator = UmmAlQuraHijriCalculator()
    var previous = 0L
    for (offset in 0..400) {
      val date = calculator.fromEpochMillis(referenceNow + offset * 86_400_000L, zone)
      val serial = date.year * 12L * 30L + date.month * 30L + date.day
      assertTrue("يوم مفقود أو مكرر عند الإزاحة $offset", serial == previous + 1L || previous == 0L)
      previous = serial
      assertTrue(date.day in 1..30)
    }
  }

  private fun fixedEpochUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(year, month, day, hour, minute, 0)
    }.timeInMillis

  @Test
  fun tabularHijri_isMonotonicAcrossDays() {
    val calculator = TabularHijriCalculator()
    var previous = 0L
    for (offset in 0..30) {
      val epoch = referenceNow + offset * 86_400_000L
      val hijri = calculator.fromEpochMillis(epoch, zone)
      val serial = hijri.year * 12L * 30L + hijri.month * 30L + hijri.day
      assertTrue("التاريخ الهجري يجب أن يتقدم", serial > previous)
      previous = serial
      assertTrue(hijri.month in 1..12)
      assertTrue(hijri.day in 1..30)
    }
  }
}
