package com.example.zahra.tools.android

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import com.example.zahra.tools.HijriCalculator
import com.example.zahra.tools.HijriDate
import com.example.zahra.tools.UmmAlQuraHijriCalculator
import java.util.Date
import java.util.TimeZone

/**
 * تقويم هجري عبر `android.icu.util.IslamicCalendar` (متاح من API 24 بلا desugaring).
 *
 * القرارات المُثبَّتة هنا (بناءً على بحث ما قبل التنفيذ):
 * - النوع مثبّت صراحةً على `ISLAMIC_UMALQURA` ولا يُستنتج من لغة الجهاز، لأن ICU
 *   غيّر ربط `@calendar=islamic` بين الإصدارات، فكان يمكن أن يتحرّك التاريخ مع تحديث النظام.
 * - الشهر في ICU صفري الأساس ⇒ يُحوَّل إلى 1..12 عند الحدود فقط.
 * - أي `Throwable` (اختلاف OEM أو غياب ICU) يسقط إلى [TabularHijriCalculator]
 *   بدل إظهار تاريخ مخترع؛ ويُعلن للمستخدم أنه تقويم حسابي.
 */
class AndroidHijriCalculator(
  private val fallback: HijriCalculator = UmmAlQuraHijriCalculator()
) : HijriCalculator {

  override fun fromEpochMillis(epochMs: Long, timeZone: TimeZone): HijriDate =
    readFromDeviceCalendar(epochMs, timeZone) ?: fallback.fromEpochMillis(epochMs, timeZone)

  /** يعيد `null` إذا لم يعمل تقويم الجهاز — عندها يُستخدم البديل الحسابي. */
  fun readFromDeviceCalendar(epochMs: Long, timeZone: TimeZone): HijriDate? = try {
    val calendar = IslamicCalendar(ULocale.ROOT)
    calendar.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
    calendar.timeZone = android.icu.util.TimeZone.getTimeZone(timeZone.id)
    calendar.time = Date(epochMs)
    HijriDate(
      year = calendar.get(android.icu.util.Calendar.YEAR),
      month = calendar.get(android.icu.util.Calendar.MONTH) + 1,
      day = calendar.get(android.icu.util.Calendar.DATE)
    )
  } catch (_: Throwable) {
    null
  }

  /** هل تقويم الجهاز يعمل فعلًا الآن؟ (يُستخدم لاختيار نص المصدر بدقة). */
  fun isDeviceCalendarUsable(nowMs: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Boolean =
    readFromDeviceCalendar(nowMs, timeZone) != null
}
