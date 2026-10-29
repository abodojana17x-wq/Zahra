package com.example.zahra.tools

import java.util.Calendar
import java.util.TimeZone

/** تاريخ هجري بسيط (الشهر 1..12). */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
  val monthNameAr: String
    get() = HijriMonths.nameOf(month)

  fun formatAr(): String = "$day $monthNameAr $year هـ"
}

object HijriMonths {
  val names: List<String> = listOf(
    "محرم",
    "صفر",
    "ربيع الأول",
    "ربيع الآخر",
    "جمادى الأولى",
    "جمادى الآخرة",
    "رجب",
    "شعبان",
    "رمضان",
    "شوال",
    "ذو القعدة",
    "ذو الحجة"
  )

  fun nameOf(month: Int): String = names.getOrElse(month - 1) { "شهر ${month}" }
}

/**
 * واجهة تحويل ميلادي ➔ هجري.
 *
 * فصلها عن [TimeTools] يسمح باستخدام تقويم الجهاز (Umm al-Qura) على أندرويد،
 * وبتقويم حسابي حتمي في الاختبارات وفي الأجهزة التي يفشل فيها `android.icu`.
 */
interface HijriCalculator {
  fun fromEpochMillis(epochMs: Long, timeZone: TimeZone): HijriDate
}

/** حساب رقم اليوم اليولياني من تاريخ ميلادي (خوارزمية معيارية). */
object JulianDayCalculator {
  fun of(year0: Int, month0: Int, day: Int): Long {
    var year = year0
    var month = month0
    if (month <= 2) {
      year -= 1
      month += 12
    }
    val a = year / 100
    val b = 2 - a + a / 4
    return (365.25 * (year + 4716)).toLong() +
      (30.6001 * (month + 1)).toLong() +
      day + b - 1524
  }
}

/**
 * تقويم هجري حسابي (الخوارزمية الكويتية/الجداولية المدنية).
 *
 * ⚠️ صدق عند الحدود: التقويم المدني الجدولي **ليس** تقويم أم القرى المعتمد على الرؤية،
 * وقد يختلف عنه بيوم (أحيانًا يومين) في بعض الأشهر. لذلك:
 * - على أندرويد يُستخدم `android.icu.util.IslamicCalendar` بنوع `ISLAMIC_UMALQURA` أولًا.
 * - هذا الحساب بديل عند فشل ICU وفي الاختبارات، ويُعلَن للمستخدم أن التاريخ «حسابي» عند استخدامه.
 */
class TabularHijriCalculator : HijriCalculator {

  override fun fromEpochMillis(epochMs: Long, timeZone: TimeZone): HijriDate {
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
    return fromGregorian(
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH) + 1,
      calendar.get(Calendar.DAY_OF_MONTH)
    )
  }

  fun fromGregorian(year: Int, month: Int, day: Int): HijriDate {
    val julianDay = julianDay(year, month, day)
    return fromJulianDay(julianDay)
  }

  private fun julianDay(year: Int, month: Int, day: Int): Long = JulianDayCalculator.of(year, month, day)

  private fun fromJulianDay(julianDay: Long): HijriDate {
    var l = julianDay - 1948440 + 10632
    val n = (l - 1) / 10631
    l = l - 10631 * n + 354
    val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((l % 5670) / 15238)
    l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 29) + 29
    val month = (24 * l / 709).toInt()
    val day = (l - (709L * month / 24)).toInt()
    val year = (30 * n + j - 30).toInt()
    return HijriDate(year = year, month = month.coerceIn(1, 12), day = day.coerceIn(1, 30))
  }
}

/**
 * تقويم أم القرى عبر **جدول أطوال الأشهر** المدمج (1440–1500هـ).
 *
 * لماذا جدول ولماذا ليس حسابًا فلكيًا؟ لأن طول الشهر الهجري يُعلن ولا يُستنتج:
 * الشهر 29 أو 30 يومًا حسب الرؤية/التقويم المعتمد. أي حساب خاص بنا سيخالف
 * تقويم الجهاز الرسمي في أشهر كاملة. هذا الجدول مُشتق من جداول أم القرى الرسمية
 * المتاحة في `java.time.chrono.HijrahDate` (umalqura)، ويغطي 61 سنة هجرية
 * (2018-09-11 إلى 2077-10-17 ميلاديًا تقريبًا) كافية لعمر التطبيق العملي.
 *
 * - داخل النطاق: مطابقة تامة للجدول (بلا شبكة، بلا مكتبة إضافية).
 * - خارج النطاق (قبل 1440هـ أو بعد 1500هـ): تحويل حسابي تقديري عبر [TabularHijriCalculator]
 *   مع إعلان أن التاريخ تقديري.
 */
class UmmAlQuraHijriCalculator(
  private val fallback: HijriCalculator = TabularHijriCalculator()
) : HijriCalculator {

  companion object {
    const val START_YEAR = 1440
    const val END_YEAR = 1500
    const val EPOCH_JULIAN_DAY = 2458373L

    /** 732 بت (61 سنة × 12 شهرًا)، كل بت = 1 إذا كان الشهر 30 يومًا. */
    private const val MONTH_TABLE_HEX =
      "5d4ada55aaab595749764baa5b52b6a56e4db25b52b6a5ad2ae92f49764b6a56acad655d49da4dd16d955aa5b52da95b4ad5956ca6e4aea4f52b6956aaab54bd25d92ea96d4ada95b4aba55b29b54d6a97547693749b5556aa6b52b"

    private val monthLengths: IntArray by lazy {
      val lengths = IntArray((END_YEAR - START_YEAR + 1) * 12)
      var index = 0
      for (hexChar in MONTH_TABLE_HEX) {
        val value = hexChar.digitToInt(16)
        for (bit in 3 downTo 0) {
          if (index >= lengths.size) break
          lengths[index] = if ((value shr bit) and 1 == 1) 30 else 29
          index++
        }
      }
      lengths
    }

    private val yearStartOffsets: IntArray by lazy {
      val years = END_YEAR - START_YEAR + 1
      val offsets = IntArray(years + 1)
      for (yearIndex in 0 until years) {
        var length = 0
        for (month in 0 until 12) length += monthLengths[yearIndex * 12 + month]
        offsets[yearIndex + 1] = offsets[yearIndex] + length
      }
      offsets
    }
  }

  /** يعيد `null` إذا كان التاريخ خارج نطاق الجدول. */
  fun fromJulianDay(julianDay: Long): HijriDate? {
    var delta = julianDay - EPOCH_JULIAN_DAY
    if (delta < 0 || delta >= yearStartOffsets.last()) return null
    var yearIndex = 0
    while (yearIndex < yearStartOffsets.size - 1 && delta >= yearStartOffsets[yearIndex + 1]) {
      yearIndex++
    }
    var dayOfYear = delta - yearStartOffsets[yearIndex]
    var monthIndex = 0
    while (monthIndex < 12 && dayOfYear >= monthLengths[yearIndex * 12 + monthIndex]) {
      dayOfYear -= monthLengths[yearIndex * 12 + monthIndex]
      monthIndex++
    }
    if (monthIndex >= 12) return null
    return HijriDate(
      year = START_YEAR + yearIndex,
      month = monthIndex + 1,
      day = dayOfYear.toInt() + 1
    )
  }

  override fun fromEpochMillis(epochMs: Long, timeZone: TimeZone): HijriDate {
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
    val julianDay = JulianDayCalculator.of(
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH) + 1,
      calendar.get(Calendar.DAY_OF_MONTH)
    )
    return fromJulianDay(julianDay) ?: fallback.fromEpochMillis(epochMs, timeZone)
  }
}
