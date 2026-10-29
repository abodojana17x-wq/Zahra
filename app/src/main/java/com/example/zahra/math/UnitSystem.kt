package com.example.zahra.math

/** تصنيفات الوحدات المدعومة. التحويل مسموح داخل التصنيف فقط. */
enum class UnitCategory(val arabicLabel: String) {
  LENGTH("الطول"),
  MASS("الكتلة"),
  TEMPERATURE("الحرارة"),
  VOLUME("الحجم"),
  AREA("المساحة"),
  TIME("الزمن"),
  SPEED("السرعة"),
  DATA("البيانات"),
  CURRENCY("العملة")
}

/**
 * تعريف وحدة قياس.
 *
 * التحويل عام: `base = value * factorToBase + offsetToBase` ثم `target = (base - offset) / factor`.
 * هذا يجعل التحويل بين C/F/K تحويلًا **أفينيًا** لا ضربًا بسيطًا (خطأ شائع في المحوّلات اليدوية).
 *
 * @param approximate `true` يعني أن القيمة متفق عليها عرفيًا لكنها ليست تعريفًا دوليًا دقيقًا
 * (مثل الفدان المصري، والبايت المضاعف 1024) — تُذكر الملاحظة في نص الرد ولا تُخفى.
 */
data class UnitDefinition(
  val id: String,
  val category: UnitCategory,
  val factorToBase: Double,
  val offsetToBase: Double = 0.0,
  val arabicName: String,
  val symbol: String,
  val aliases: List<String>,
  val approximate: Boolean = false,
  val note: String? = null
) {
  fun toBase(value: Double): Double = value * factorToBase + offsetToBase
  fun fromBase(base: Double): Double = (base - offsetToBase) / factorToBase
}

/** نتيجة التحويل — لا تُعاد قيمة وهمية عند الفشل، بل سبب صريح. */
sealed class UnitConversionResult {
  data class Success(
    val value: Double,
    val text: String,
    val sourceName: String,
    val confidence: Double = 1.0,
    val isApproximate: Boolean = false
  ) : UnitConversionResult()

  data class Unsupported(val message: String, val unknownUnit: String? = null) : UnitConversionResult()

  data class CrossCategory(val message: String) : UnitConversionResult()

  data class RequiresLiveSource(val message: String) : UnitConversionResult()
}

/**
 * نظام وحدات عام بجدول عوامل (بدل سلسلة `when` بعشرة فروع).
 *
 * التغطية: 9 تصنيفات، وأي زوج داخل التصنيف يعمل (طول↔طول، مساحة↔مساحة، حرارة↔حرارة...).
 * أسماء عربية وإنجليزية ولهجية، مع وحدات مصرية تقليدية (فدان، قيراط، سهم).
 *
 * ⚠️ العملات: لا تُحوَّل هنا إطلاقًا. الأسعار متغيّرة، وأي رقم ثابت مخزّن في الكود
 * سيكون كذبًا واثقًا. إما مصدر حي بتاريخ، وإما امتناع صريح.
 */
object UnitSystem {

  private const val SOURCE_SI = "النظام الدولي للوحدات (SI) — المكتب الدولي للأوزان والمقاييس BIPM"
  private const val SOURCE_EGYPT = "الوحدات المصرية التقليدية (1 فدان = 4200.833 م²، 24 قيراط، 24 سهم)"
  private const val SOURCE_BINARY_DATA = "تعريفات البيانات الشائعة في أنظمة التشغيل (1 KB = 1024 B)"

  val units: List<UnitDefinition> = listOf(
    // ——— الطول (الأساس: متر) ———
    UnitDefinition("mm", UnitCategory.LENGTH, 0.001, arabicName = "مليمتر", symbol = "mm",
      aliases = listOf("مم", "مليمتر", "ملليمتر", "مليمترات", "المليمتر", "millimeter", "millimeters")),
    UnitDefinition("cm", UnitCategory.LENGTH, 0.01, arabicName = "سنتيمتر", symbol = "cm",
      aliases = listOf("سم", "سنتيمتر", "سنتيمترات", "السنتيمتر", "سنتي", "centimeter", "centimeters", "cm")),
    UnitDefinition("m", UnitCategory.LENGTH, 1.0, arabicName = "متر", symbol = "m",
      aliases = listOf("متر", "أمتار", "امتار", "المتر", "مترا", "meter", "meters", "metre", "metres")),
    UnitDefinition("km", UnitCategory.LENGTH, 1000.0, arabicName = "كيلومتر", symbol = "km",
      aliases = listOf("كلم", "كيلومتر", "كيلومترات", "كيلو متر", "الكيلومتر", "كيلو", "kilometer", "kilometers", "kilometre", "km")),
    UnitDefinition("inch", UnitCategory.LENGTH, 0.0254, arabicName = "بوصة", symbol = "in",
      aliases = listOf("بوصة", "بوصه", "بوصات", "إنش", "انش", "الإنش", "inch", "inches", "in")),
    UnitDefinition("ft", UnitCategory.LENGTH, 0.3048, arabicName = "قدم", symbol = "ft",
      aliases = listOf("قدم", "أقدام", "اقدام", "قدمين", "الأقدام", "foot", "feet", "ft")),
    UnitDefinition("yd", UnitCategory.LENGTH, 0.9144, arabicName = "يارد", symbol = "yd",
      aliases = listOf("يارد", "ياردة", "ياردات", "اليارد", "yard", "yards", "yd")),
    UnitDefinition("mi", UnitCategory.LENGTH, 1609.344, arabicName = "ميل", symbol = "mi",
      aliases = listOf("ميل", "أميال", "اميال", "الأميال", "mile", "miles", "mi")),

    // ——— الكتلة (الأساس: كيلوجرام) ———
    UnitDefinition("mg", UnitCategory.MASS, 1e-6, arabicName = "مليجرام", symbol = "mg",
      aliases = listOf("مليجرام", "ملليجرام", "مجم", "milligram", "milligrams", "mg")),
    UnitDefinition("g", UnitCategory.MASS, 0.001, arabicName = "جرام", symbol = "g",
      aliases = listOf("جرام", "غرام", "جم", "جرامات", "الجرام", "gram", "grams", "g")),
    UnitDefinition("kg", UnitCategory.MASS, 1.0, arabicName = "كيلوجرام", symbol = "kg",
      aliases = listOf("كجم", "كيلوجرام", "كيلوغرام", "كيلو جرام", "كيلو", "الكيلو", "كيلوجرامات", "kilogram", "kilograms", "kilo", "kg")),
    UnitDefinition("ton", UnitCategory.MASS, 1000.0, arabicName = "طن", symbol = "t",
      aliases = listOf("طن", "أطنان", "اطنان", "الطن", "ton", "tons", "tonne", "tonnes")),
    UnitDefinition("oz", UnitCategory.MASS, 0.028349523125, arabicName = "أونصة", symbol = "oz",
      aliases = listOf("أونصة", "اونصة", "أونصات", "ounce", "ounces", "oz")),
    UnitDefinition("lb", UnitCategory.MASS, 0.45359237, arabicName = "باوند", symbol = "lb",
      aliases = listOf("باوند", "رطل", "أرطال", "ارطال", "باوندات", "الرطل", "pound", "pounds", "lb", "lbs")),

    // ——— الحرارة (الأساس: كلفن — تحويل أفيني) ———
    UnitDefinition("c", UnitCategory.TEMPERATURE, 1.0, offsetToBase = 273.15, arabicName = "درجة مئوية", symbol = "°C",
      aliases = listOf("مئوية", "مئويه", "سيليزيوس", "سيلسيوس", "درجة مئوية", "درجه مئويه", "c", "celsius", "centigrade", "°c")),
    UnitDefinition("f", UnitCategory.TEMPERATURE, 5.0 / 9.0, offsetToBase = 459.67 * 5.0 / 9.0, arabicName = "درجة فهرنهايت", symbol = "°F",
      aliases = listOf("فهرنهايت", "فهرنهايتية", "درجة فهرنهايت", "درجه فهرنهايت", "f", "fahrenheit", "°f")),
    UnitDefinition("k", UnitCategory.TEMPERATURE, 1.0, arabicName = "كلفن", symbol = "K",
      aliases = listOf("كلفن", "كيلفن", "kelvin", "k")),

    // ——— الحجم (الأساس: لتر) ———
    UnitDefinition("ml", UnitCategory.VOLUME, 0.001, arabicName = "ملليلتر", symbol = "ml",
      aliases = listOf("مل", "ملليلتر", "مليلتر", "مللي لتر", "مليلترات", "milliliter", "millilitre", "ml")),
    UnitDefinition("l", UnitCategory.VOLUME, 1.0, arabicName = "لتر", symbol = "L",
      aliases = listOf("لتر", "ليتر", "لترات", "اللتر", "liter", "liters", "litre", "litres")),
    UnitDefinition("gal", UnitCategory.VOLUME, 3.785411784, arabicName = "جالون", symbol = "gal",
      aliases = listOf("جالون", "جالونات", "الجالون", "gallon", "gallons", "gal")),
    UnitDefinition("cup", UnitCategory.VOLUME, 0.2365882365, arabicName = "كوب", symbol = "cup",
      aliases = listOf("كوب", "أكواب", "اكواب", "cup", "cups")),

    // ——— المساحة (الأساس: متر مربع) ———
    UnitDefinition("cm2", UnitCategory.AREA, 0.0001, arabicName = "سنتيمتر مربع", symbol = "cm²",
      aliases = listOf("سم مربع", "سنتيمتر مربع", "سم2", "cm2", "cm²")),
    UnitDefinition("m2", UnitCategory.AREA, 1.0, arabicName = "متر مربع", symbol = "m²",
      aliases = listOf("متر مربع", "مترمربع", "م2", "م²", "متر مربعا", "square meter", "square meters", "m2", "m²")),
    UnitDefinition("km2", UnitCategory.AREA, 1_000_000.0, arabicName = "كيلومتر مربع", symbol = "km²",
      aliases = listOf("كيلومتر مربع", "كيلو متر مربع", "كم مربع", "كم2", "km2", "km²", "square kilometer", "square kilometers")),
    UnitDefinition("feddan", UnitCategory.AREA, 4200.833, arabicName = "فدان", symbol = "فدان",
      aliases = listOf("فدان", "أفدنة", "افدنه", "فدادين", "الفدان", "feddan", "feddanS"),
      approximate = true, note = "الفدان المصري = 4200.833 م² (اتفاق عرفي/قانوني مصري)"),
    UnitDefinition("qirat", UnitCategory.AREA, 4200.833 / 24.0, arabicName = "قيراط", symbol = "قيراط",
      aliases = listOf("قيراط", "قراريط", "القيراط", "kiraat", "qirat"),
      approximate = true, note = "القيراط = 1/24 فدان"),
    UnitDefinition("sahm", UnitCategory.AREA, 4200.833 / 24.0 / 24.0, arabicName = "سهم", symbol = "سهم",
      aliases = listOf("سهم", "أسهم", "اسهم", "الأسهم", "sahm"),
      approximate = true, note = "السهم = 1/24 قيراط"),
    UnitDefinition("hectare", UnitCategory.AREA, 10_000.0, arabicName = "هكتار", symbol = "ha",
      aliases = listOf("هكتار", "هكتارات", "الهكتار", "hectare", "hectares", "ha")),
    UnitDefinition("acre", UnitCategory.AREA, 4046.8564224, arabicName = "أيكر", symbol = "ac",
      aliases = listOf("أيكر", "ايكر", "أكر", "acres", "acre", "ac")),

    // ——— الزمن (الأساس: ثانية) ———
    UnitDefinition("ms", UnitCategory.TIME, 0.001, arabicName = "مللي ثانية", symbol = "ms",
      aliases = listOf("مللي ثانية", "ملي ثانية", "مللي ثانيه", "millisecond", "milliseconds", "ms")),
    UnitDefinition("s", UnitCategory.TIME, 1.0, arabicName = "ثانية", symbol = "s",
      aliases = listOf("ثانية", "ثانيه", "ثواني", "ثوان", "الثانية", "second", "seconds", "sec", "s")),
    UnitDefinition("min", UnitCategory.TIME, 60.0, arabicName = "دقيقة", symbol = "min",
      aliases = listOf("دقيقة", "دقيقه", "دقائق", "دقايق", "الدقيقة", "minute", "minutes", "min")),
    UnitDefinition("h", UnitCategory.TIME, 3600.0, arabicName = "ساعة", symbol = "h",
      aliases = listOf("ساعة", "ساعه", "ساعات", "الساعة", "hour", "hours", "hr", "h")),
    UnitDefinition("day", UnitCategory.TIME, 86_400.0, arabicName = "يوم", symbol = "day",
      aliases = listOf("يوم", "أيام", "ايام", "اليوم", "day", "days")),
    UnitDefinition("week", UnitCategory.TIME, 604_800.0, arabicName = "أسبوع", symbol = "wk",
      aliases = listOf("أسبوع", "اسبوع", "أسابيع", "اسابيع", "week", "weeks")),

    // ——— السرعة (الأساس: متر/ثانية) ———
    UnitDefinition("kmh", UnitCategory.SPEED, 1.0 / 3.6, arabicName = "كيلومتر/ساعة", symbol = "km/h",
      aliases = listOf("كم/س", "كم/ساعة", "كيلومتر في الساعة", "كيلومتر/ساعة", "km/h", "kmh", "kph")),
    UnitDefinition("mps", UnitCategory.SPEED, 1.0, arabicName = "متر/ثانية", symbol = "m/s",
      aliases = listOf("م/ث", "متر في الثانية", "متر/ثانية", "m/s", "mps")),
    UnitDefinition("mph", UnitCategory.SPEED, 0.44704, arabicName = "ميل/ساعة", symbol = "mph",
      aliases = listOf("ميل في الساعة", "ميل/ساعة", "mph")),
    UnitDefinition("knot", UnitCategory.SPEED, 0.5144444444444445, arabicName = "عقدة", symbol = "kn",
      aliases = listOf("عقدة", "عقد", "knot", "knots", "kn")),

    // ——— البيانات (الأساس: بايت) ———
    UnitDefinition("bit", UnitCategory.DATA, 0.125, arabicName = "بت", symbol = "bit",
      aliases = listOf("بت", "bits", "bit")),
    UnitDefinition("byte", UnitCategory.DATA, 1.0, arabicName = "بايت", symbol = "B",
      aliases = listOf("بايت", "بايتس", "byte", "bytes")),
    UnitDefinition("kb", UnitCategory.DATA, 1024.0, arabicName = "كيلوبايت", symbol = "KB",
      aliases = listOf("كيلوبايت", "كيلو بايت", "ك ب", "kilobyte", "kilobytes", "kb")),
    UnitDefinition("mb", UnitCategory.DATA, 1024.0 * 1024, arabicName = "ميجابايت", symbol = "MB",
      aliases = listOf("ميجابايت", "ميجا بايت", "ميغابايت", "megabyte", "megabytes", "mb")),
    UnitDefinition("gb", UnitCategory.DATA, 1024.0 * 1024 * 1024, arabicName = "جيجابايت", symbol = "GB",
      aliases = listOf("جيجابايت", "جيجا بايت", "غيغابايت", "gigabyte", "gigabytes", "gb")),
    UnitDefinition("tb", UnitCategory.DATA, 1024.0 * 1024 * 1024 * 1024, arabicName = "تيرابايت", symbol = "TB",
      aliases = listOf("تيرابايت", "تيرا بايت", "terabyte", "terabytes", "tb")),

    // ——— العملة (لا تحويل رقمي — تحتاج مصدرًا حيًا) ———
    UnitDefinition("egp", UnitCategory.CURRENCY, 0.0, arabicName = "جنيه مصري", symbol = "EGP",
      aliases = listOf("جنيه", "جنية", "جنيه مصري", "egp", "junaih")),
    UnitDefinition("usd", UnitCategory.CURRENCY, 0.0, arabicName = "دولار", symbol = "USD",
      aliases = listOf("دولار", "دولارات", "usd", "dollar", "dollars")),
    UnitDefinition("sar", UnitCategory.CURRENCY, 0.0, arabicName = "ريال", symbol = "SAR",
      aliases = listOf("ريال", "ريالات", "sar", "riyal", "riyals")),
    UnitDefinition("aed", UnitCategory.CURRENCY, 0.0, arabicName = "درهم", symbol = "AED",
      aliases = listOf("درهم", "دراهم", "aed", "dirham", "dirhams")),
    UnitDefinition("kwd", UnitCategory.CURRENCY, 0.0, arabicName = "دينار", symbol = "KWD",
      aliases = listOf("دينار", "دنانير", "kwd", "dinar", "dinars")),
    UnitDefinition("eur", UnitCategory.CURRENCY, 0.0, arabicName = "يورو", symbol = "EUR",
      aliases = listOf("يورو", "اورو", "eur", "euro", "euros"))
  )

  private val byKey: Map<String, UnitDefinition> = buildMap {
    for (unit in units) {
      put(normalizeKey(unit.id), unit)
      put(normalizeKey(unit.symbol), unit)
      put(normalizeKey(unit.arabicName), unit)
      put(normalizeKey(unit.arabicName.replace("ال", "", ignoreCase = false)), unit)
      for (alias in unit.aliases) put(normalizeKey(alias), unit)
    }
  }

  /** أطول الأسماء أولًا حتى تُطابق «متر مربع» قبل «متر». */
  val aliasesByLength: List<Pair<String, UnitDefinition>> =
    byKey.entries
      .filter { it.key.isNotBlank() }
      .sortedByDescending { it.key.length }
      .map { it.key to it.value }

  /**
   * تطبيع اسم وحدة/كلمة عربية للمطابقة: إزالة التشكيل والتطويل، توحيد الهمزات
   * والتاء المربوطة والياء، وحذف أداة التعريف «ال» من بداية الكلمة.
   */
  fun normalizeKey(raw: String): String {
    val builder = StringBuilder()
    for (ch in raw.trim().lowercase()) {
      when (ch) {
        'أ', 'إ', 'آ', 'ٱ' -> builder.append('ا')
        'ة' -> builder.append('ه')
        'ى' -> builder.append('ي')
        'ـ' -> Unit
        in '\u064B'..'\u065F' -> Unit // تشكيل
        '٬', ',' -> builder.append(' ')
        '٫' -> builder.append('.')
        in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
        in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
        else -> builder.append(ch)
      }
    }
    val collapsed = builder.toString().replace(Regex("\\s+"), " ").trim()
    // حذف «ال» التعريف من بداية كل كلمة
    return collapsed.split(' ').joinToString(" ") { word ->
      // «ال» التعريف تُحذف فقط إذا بقي منها جذر واضح (نتجنب تحويل «إلى» إلى «ي»)
      if (word.length > 3 && word.startsWith("ال")) word.substring(2) else word
    }
  }

  fun findUnit(rawName: String): UnitDefinition? {
    val key = normalizeKey(rawName)
    if (key.isBlank()) return null
    byKey[key]?.let { return it }
    // «كيلو متر» مقابل «كيلومتر»
    val compact = key.replace(" ", "")
    return byKey[compact] ?: byKey.entries.firstOrNull { it.key.replace(" ", "") == compact }?.value
  }

  fun convert(value: Double, fromRaw: String, toRaw: String): UnitConversionResult {
    val from = findUnit(fromRaw)
      ?: return UnitConversionResult.Unsupported(unsupportedMessage(fromRaw), fromRaw)
    val to = findUnit(toRaw)
      ?: return UnitConversionResult.Unsupported(unsupportedMessage(toRaw), toRaw)
    return convert(value, from, to)
  }

  fun convert(request: UnitRequest): UnitConversionResult = convert(request.value, request.from, request.to)

  fun convert(value: Double, from: UnitDefinition, to: UnitDefinition): UnitConversionResult {
    if (from.category == UnitCategory.CURRENCY || to.category == UnitCategory.CURRENCY) {
      return UnitConversionResult.RequiresLiveSource(
        "أسعار العملات مش ثابتة، ومش هألف لك سعرًا من ذاكرتي. فعّل «الوضع المتصل» ليُجلب السعر من مصدر حي بتاريخه، " +
          "أو استخدم سعرًا أنت أدخلته بنفسك."
      )
    }
    if (from.category != to.category) {
      return UnitConversionResult.CrossCategory(
        "مفيش تحويل مباشر بين ${from.category.arabicLabel} و${to.category.arabicLabel}. " +
          "التحويل يكون داخل التصنيف نفسه (مثال: كيلومتر ➔ ميل، أو فدان ➔ متر مربع)."
      )
    }
    if (!value.isFinite()) {
      return UnitConversionResult.Unsupported("القيمة المدخلة غير صالحة للتحويل.")
    }

    val result = to.fromBase(from.toBase(value))
    if (!result.isFinite() || result.isNaN()) {
      return UnitConversionResult.Unsupported("نتيجة التحويل تجاوزت النطاق المسموح به.")
    }

    val formattedValue = MathFormatting.formatNumber(value)
    val formattedResult = MathFormatting.formatNumber(result)
    val textBuilder = StringBuilder()
    textBuilder.append("$formattedValue ${from.arabicName} = $formattedResult ${to.arabicName}")

    val notes = mutableListOf<String>()
    if (from.approximate || to.approximate) {
      notes.add(from.note ?: to.note ?: "قيمة عرفية متفق عليها وليست تعريفًا دوليًا دقيقًا")
    }
    if (from.category == UnitCategory.DATA) {
      notes.add("البيانات محسوبة بـ 1024 (تعريف أنظمة التشغيل)، وتختلف عن التعريف العشري SI (1000)")
    }
    if (from.category == UnitCategory.TEMPERATURE) {
      notes.add("تحويل الحرارة أفيني (له إزاحة) وليس ضربًا بمعامل فقط")
    }
    if (notes.isNotEmpty()) {
      textBuilder.append("\n• ").append(notes.joinToString("\n• "))
    }

    val source = when {
      from.category == UnitCategory.CURRENCY -> SOURCE_SI
      from.category == UnitCategory.AREA && (from.id == "feddan" || to.id == "feddan" || from.id == "qirat" || to.id == "qirat" || from.id == "sahm" || to.id == "sahm") -> SOURCE_EGYPT
      from.category == UnitCategory.DATA -> SOURCE_BINARY_DATA
      else -> SOURCE_SI
    }

    return UnitConversionResult.Success(
      value = result,
      text = textBuilder.toString(),
      sourceName = source,
      confidence = 1.0,
      isApproximate = from.approximate || to.approximate
    )
  }

  fun unsupportedMessage(rawName: String): String =
    "الوحدة «${rawName.trim()}» غير مدعومة في محوّل زهرة. المدعوم: الطول، الكتلة، الحرارة، الحجم، " +
      "المساحة (ومنها الفدان والقيراط)، الزمن، السرعة، البيانات. العملات تحتاج مصدرًا حيًا."

  /** قائمة مختصرة للتصنيفات والوحدات (للتعليم، لا للتزيين). */
  fun supportedUnitsSummary(): String =
    UnitCategory.values().joinToString("\n") { category ->
      val ids = units.filter { it.category == category }.joinToString("، ") { it.arabicName }
      "• ${category.arabicLabel}: $ids"
    }
}
