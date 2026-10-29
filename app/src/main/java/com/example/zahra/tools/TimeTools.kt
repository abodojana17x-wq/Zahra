package com.example.zahra.tools

import com.example.zahra.model.OutcomeState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** رد أدوات الوقت/التاريخ (حتمي بالكامل — لا شبكة ولا نموذج). */
data class TimeAnswer(
  val intent: String,
  val text: String,
  val source: String,
  val confidence: Double,
  val outcomeState: OutcomeState = OutcomeState.SUPPORTED,
  val followups: List<String> = emptyList()
)

/**
 * أدوات الوقت والتاريخ والهجري — حتمية بالكامل وقابلة للاختبار.
 *
 * - لا `System.currentTimeMillis()` داخل المنطق: [nowMs] قابل للحقن، وكذلك [zone]
 *   و[HijriCalculator] (اختبار بلا ساعة حقيقية).
 * - لا تتعامل مع أوامر الجدولة: أي جملة فيها «فكرني/ذكرني/نبهني/جدول/تذكير»
 *   تُترك للمسار الطبيعي حتى لا تُسرق من محرّك الجدولة.
 * - كل الردود تحمل مصدرًا معلنًا: تقويم الجهاز أو الحساب الجدولي.
 */
object TimeTools {

  private const val SOURCE_DEVICE_HIJRI = "تقويم الجهاز (أم القرى) — android.icu"
  private const val SOURCE_TABLE_HIJRI = "تقويم أم القرى (جدول أطوال الأشهر المدمج 1440–1500هـ)"
  private const val SOURCE_CLOCK = "ساعة الجهاز والمنطقة الزمنية المحلية"

  private val SCHEDULING_WORDS = listOf("فكرني", "ذكرني", "نبهني", "جدول", "موعد", "تذكير", "منبه", "schedule")

  private val WEEKDAYS = arrayOf(
    "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
  )

  private val WEEKDAY_ALIASES = mapOf(
    "الاحد" to Calendar.SUNDAY,
    "الأحد" to Calendar.SUNDAY,
    "الاثنين" to Calendar.MONDAY,
    "الإثنين" to Calendar.MONDAY,
    "الاتنين" to Calendar.MONDAY,
    "الثلاثاء" to Calendar.TUESDAY,
    "الاربعاء" to Calendar.WEDNESDAY,
    "الأربعاء" to Calendar.WEDNESDAY,
    "الخميس" to Calendar.THURSDAY,
    "الجمعه" to Calendar.FRIDAY,
    "الجمعة" to Calendar.FRIDAY,
    "السبت" to Calendar.SATURDAY
  )

  /**
   * يحاول الإجابة على سؤال وقت/تاريخ. يعيد `null` إذا لم تكن الجملة سؤالًا زمنيًا،
   * وحينها يكمل المسار الطبيعي (لا تخمين ولا استيلاء على نوايا أخرى).
   */
  fun answer(
    rawInput: String,
    nowMs: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
    hijri: HijriCalculator = UmmAlQuraHijriCalculator(),
    hijriIsDeviceAccurate: Boolean = false,
    hijriSourceLabel: String? = null
  ): TimeAnswer? {
    val text = normalize(rawInput)
    if (text.isBlank()) return null
    if (SCHEDULING_WORDS.any { text.contains(it) }) return null

    countdownToWeekday(text, nowMs, zone)?.let { return it }
    val resolvedLabel = hijriSourceLabel
      ?: if (hijriIsDeviceAccurate) SOURCE_DEVICE_HIJRI else SOURCE_TABLE_HIJRI
    if (containsHijriWord(text)) return answerHijri(text, nowMs, zone, hijri, resolvedLabel)
    if (isTimeQuestion(text)) return answerClock(nowMs, zone)
    if (isDateQuestion(text)) return answerDate(text, nowMs, zone, hijri, resolvedLabel)
    return null
  }

  // ——— الساعة ———
  private fun isTimeQuestion(text: String): Boolean {
    val mentionsTime = text.contains("الساعه") || text.contains("الساعة") || text.contains("الوقت") ||
      text.contains("التوقيت") || text.contains("ساعه") || text.contains("كم الساعه")
    if (!mentionsTime) return false
    if (text.contains("تاريخ")) return false
    // «الساعة كام» / «الوقت إيه» / «كم الساعة الآن»
    return text.contains("كام") || text.contains("كم") || text.contains("ايه") ||
      text.contains("إيه") || text.contains("دلوقتي") || text.contains("الان") || text.contains("الآن")
  }

  private fun answerClock(nowMs: Long, zone: TimeZone): TimeAnswer {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val hour12 = when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
    val period = if (hour24 < 12) "ص" else "م"
    val display = String.format(Locale.US, "%02d:%02d", hour12, minute)
    val display24 = String.format(Locale.US, "%02d:%02d", hour24, minute)
    val weekday = weekdayName(calendar)
    val gregorian = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = zone }.format(Date(nowMs))

    return TimeAnswer(
      intent = "ask_time",
      text = "الساعة دلوقتي **$display $period** ($display24) — $weekday $gregorian.\n" +
        "• المنطقة الزمنية: ${zone.id}\n" +
        "• المصدر: $SOURCE_CLOCK",
      source = SOURCE_CLOCK,
      confidence = 1.0,
      followups = listOf("إيه تاريخ النهاردة؟", "إيه التاريخ الهجري النهاردة؟", "كام يوم للجمعة؟")
    )
  }

  // ——— التاريخ ———
  private fun isDateQuestion(text: String): Boolean {
    val mentionsDate = DATE_WORDS.any { text.contains(it) } || text.contains("التاريخ")
    if (!mentionsDate) return false
    // «فكرني بكرة» مستثناة أصلًا؛ «النهاردة كام» / «بكرة يوافق كام» / «تاريخ النهاردة»
    return text.contains("كام") || text.contains("كم") || text.contains("ايه") || text.contains("إيه") ||
      text.contains("تاريخ") || text.contains("يوافق") || text.contains("يوم") || text.contains("تاريخه")
  }

  private val DATE_WORDS = listOf(
    "النهارده", "النهاردة", "اليوم", "بكره", "بكرة", "غدا", "غداً", "بعد بكره", "بعد بكرة",
    "امبارح", "امبارحه", "امس", "أمس", "التاريخ"
  )

  private fun answerDate(
    text: String,
    nowMs: Long,
    zone: TimeZone,
    hijri: HijriCalculator,
    hijriSourceLabel: String
  ): TimeAnswer {
    val offset = dayOffsetOf(text)
    val calendar = Calendar.getInstance(zone).apply {
      timeInMillis = nowMs
      add(Calendar.DAY_OF_YEAR, offset)
    }
    val dayLabel = when (offset) {
      2 -> "بعد بكرة"
      1 -> "بكرة"
      -1 -> "امبارح"
      else -> "النهاردة"
    }
    val weekday = weekdayName(calendar)
    val gregorian = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = zone }.format(calendar.time)
    val hijriDate = hijri.fromEpochMillis(calendar.timeInMillis, zone)
    val asksWeekdayOnly = text.contains("يوم") && !text.contains("تاريخ") && !text.contains("كام") && !text.contains("يوافق")

    val body = if (asksWeekdayOnly) {
      "$dayLabel يوم **$weekday** — $gregorian ميلادي."
    } else {
      "$dayLabel **$weekday $gregorian** ميلادي.\n• هجري: **${hijriDate.formatAr()}**"
    }

    return TimeAnswer(
      intent = "ask_date",
      text = body + "\n• المصدر: $hijriSourceLabel",
      source = hijriSourceLabel,
      confidence = 1.0,
      followups = listOf("الساعة كام دلوقتي؟", "إيه الشهر الهجري الحالي؟", "كام يوم للجمعة؟")
    )
  }

  // ——— الهجري ———
  private fun containsHijriWord(text: String): Boolean =
    text.contains("هجري") || text.contains("هجريه") || text.contains("اسلامي")

  private fun answerHijri(
    text: String,
    nowMs: Long,
    zone: TimeZone,
    hijri: HijriCalculator,
    hijriSourceLabel: String
  ): TimeAnswer {
    val offset = dayOffsetOf(text)
    val calendar = Calendar.getInstance(zone).apply {
      timeInMillis = nowMs
      add(Calendar.DAY_OF_YEAR, offset)
    }
    val hijriDate = hijri.fromEpochMillis(calendar.timeInMillis, zone)
    val gregorian = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = zone }.format(calendar.time)
    val asksMonth = text.contains("شهر")

    val body = if (asksMonth) {
      "الشهر الهجري الحالي: **${hijriDate.monthNameAr} ${hijriDate.year} هـ** (${hijriDate.day} ${hijriDate.monthNameAr}).\n" +
        "• الميلادي المقابل: $gregorian"
    } else {
      "التاريخ الهجري: **${hijriDate.formatAr()}**\n• الميلادي المقابل: $gregorian ميلادي"
    }

    return TimeAnswer(
      intent = "ask_date",
      text = body + "\n• المصدر: $hijriSourceLabel",
      source = hijriSourceLabel,
      confidence = 1.0,
      followups = listOf("إيه تاريخ النهاردة؟", "الساعة كام دلوقتي؟", "كام يوم لرمضان؟")
    )
  }

  // ——— العدّ لليوم ———
  private fun countdownToWeekday(text: String, nowMs: Long, zone: TimeZone): TimeAnswer? {
    if (!Regex("(كام|كم) *يوم").containsMatchIn(text)) return null
    val targetDay = WEEKDAY_ALIASES.entries.firstOrNull { entry ->
      val key = normalize(entry.key)
      // «للجمعة» و«الجمعة» و«جمعة» كلها تشير لنفس اليوم
      text.contains(key) || text.contains(key.removePrefix("ال"))
    }?.value ?: return null
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    val today = calendar.get(Calendar.DAY_OF_WEEK)
    var diff = targetDay - today
    if (diff <= 0) diff += 7
    val dayName = WEEKDAYS[targetDay - 1]
    val targetDate = Calendar.getInstance(zone).apply {
      timeInMillis = nowMs
      add(Calendar.DAY_OF_YEAR, diff)
    }
    val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = zone }.format(targetDate.time)
    return TimeAnswer(
      intent = "ask_date",
      text = "باقي **$diff ${if (diff == 1) "يوم" else "أيام"}** على $dayName ($formatted).",
      source = SOURCE_CLOCK,
      confidence = 1.0,
      followups = listOf("إيه تاريخ النهاردة؟", "الساعة كام دلوقتي؟")
    )
  }

  private fun dayOffsetOf(text: String): Int = when {
    text.contains("بعد بكره") || text.contains("بعد بكرة") -> 2
    text.contains("بكره") || text.contains("بكرة") || text.contains("غدا") || text.contains("غداً") -> 1
    text.contains("امبارح") || text.contains("امس") || text.contains("أمس") -> -1
    else -> 0
  }

  private fun weekdayName(calendar: Calendar): String {
    val index = calendar.get(Calendar.DAY_OF_WEEK) - 1
    return WEEKDAYS.getOrElse(index) { "يوم غير معروف" }
  }

  /** تطبيع خفيف خاص بالأدوات: أرقام + همزات + تشكيل + مسافات. */
  private fun normalize(rawInput: String): String {
    val builder = StringBuilder()
    for (ch in rawInput.lowercase()) {
      when (ch) {
        'أ', 'إ', 'آ' -> builder.append('ا')
        '٪' -> builder.append('%')
        in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
        in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
        in '\u064B'..'\u065F', 'ـ', '\u200f', '\u200e' -> Unit
        else -> builder.append(ch)
      }
    }
    return builder.toString().replace(Regex("\\s+"), " ").trim()
  }
}
