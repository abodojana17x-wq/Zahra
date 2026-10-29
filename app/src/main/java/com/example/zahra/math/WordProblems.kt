package com.example.zahra.math

/** نتيجة حل مسألة كلامية. */
sealed class WordProblemResult {
  /**
   * مسألة محلولة بقاعدة حتمية.
   * @param expression التعبير الحسابي المكافئ (يُعرض للمستخدم للتحقق)
   * @param explanation خطوات الحل بلغة بسيطة
   */
  data class Solved(
    val templateId: String,
    val expression: String,
    val value: Double,
    val unitLabel: String?,
    val explanation: String,
    val confidence: Double = 1.0
  ) : WordProblemResult()

  /** ليست من قوالب المسائل المعروفة ⇒ تُترك للمسار العادي (لا تخمين). */
  data class NotApplicable(val reason: String) : WordProblemResult()
}

/**
 * قوالب المسائل الكلامية المصرية — كل قالب حتمي وله حالة سلبية.
 *
 * القاعدة: إن لم تُستخرج كل الأرقام اللازمة فلا حساب. الهدف هو **عدم** إنتاج
 * رقم واثق من جملة لم تُفهم (نفس مبدأ المنافس في «مفيش تخمين»).
 *
 * القوالب:
 * 1. `discount_value` — «500 وخصم 20%» ⇒ 400
 * 2. `discount_basket` — «اشتريت 3 حاجات بـ25 وخصم 10%» ⇒ 67.5
 * 3. `percent_of` — «20% من 500» ⇒ 100
 * 4. `percent_increase` — «1000 وبعد زيادة 15%» ⇒ 1150
 * 5. `average` — «متوسط 4 و8 و10» ⇒ 7.3333
 * 6. `circle_area_radius` — «مساحة دائرة نصف قطرها 5» ⇒ 78.5398
 * 7. `circle_area_diameter` — «مساحة دائرة قطرها 5» ⇒ 19.635
 * 8. `rectangle_area` — «مساحة مستطيل طوله 4 وعرضه 6» ⇒ 24
 * 9. `remaining_money` — «معايا 1000 وصرفت 350» ⇒ 650
 */
object WordProblems {

  private val NUMBER = "(\\d+(?:\\.\\d+)?)"

  fun looksLikeWordProblem(rawInput: String): Boolean {
    val text = normalize(rawInput)
    return KEYWORDS.any { text.contains(it) }
  }

  private val KEYWORDS = listOf(
    "خصم", "متوسط", "مساحة", "نسبة", "في الميه", "في المية", "زيادة", "صرفت",
    "معايا", "معي", "عندي", "كام يفضل", "يفضل", "%"
  )

  fun solve(rawInput: String): WordProblemResult {
    val text = normalize(rawInput)
    if (text.isBlank()) return WordProblemResult.NotApplicable("نص فارغ")

    solveCircleArea(text)?.let { return it }
    solveRectangleArea(text)?.let { return it }
    solveDiscountBasket(text)?.let { return it }
    solveDiscountValue(text)?.let { return it }
    solvePercentOf(text)?.let { return it }
    solvePercentIncrease(text)?.let { return it }
    solveAverage(text)?.let { return it }
    solveRemainingMoney(text)?.let { return it }

    return WordProblemResult.NotApplicable("الجملة لا تطابق أي قالب مسألة معروف")
  }

  // ——— 1) نسبة من عدد ———
  private fun solvePercentOf(text: String): WordProblemResult? {
    val match = Regex("(?:نسبة|نسبه|كم|كام)? *$NUMBER *% *من *$NUMBER").find(text)
      ?: Regex("$NUMBER *% *من *$NUMBER").find(text)
      ?: return null
    val percent = match.groupValues[1].toDoubleOrNull() ?: return null
    val total = match.groupValues[2].toDoubleOrNull() ?: return null
    val value = total * percent / 100.0
    return WordProblemResult.Solved(
      templateId = "percent_of",
      expression = "${MathFormatting.formatNumber(total)} × ${MathFormatting.formatNumber(percent)} ÷ 100",
      value = value,
      unitLabel = null,
      explanation = "النسبة $percent% من $total = $total × $percent ÷ 100 = ${MathFormatting.formatNumber(value)}"
    )
  }

  // ——— 2) خصم على قيمة ———
  private fun solveDiscountValue(text: String): WordProblemResult? {
    if (!text.contains("خصم")) return null
    // «اشتريت 3 حاجات بـ25» له قالب مستقل
    if (BASKET_HINT.containsMatchIn(text)) return null
    val match = Regex("$NUMBER *.*?خصم *$NUMBER *%").find(text) ?: return null
    val original = match.groupValues[1].toDoubleOrNull() ?: return null
    val percent = match.groupValues[2].toDoubleOrNull() ?: return null
    if (percent > 100) return null
    val value = original * (1 - percent / 100.0)
    return WordProblemResult.Solved(
      templateId = "discount_value",
      expression = "${MathFormatting.formatNumber(original)} × (1 − ${MathFormatting.formatNumber(percent)} ÷ 100)",
      value = value,
      unitLabel = null,
      explanation = "السعر بعد خصم $percent%: $original × (1 − $percent ÷ 100) = ${MathFormatting.formatNumber(value)}"
    )
  }

  private val BASKET_HINT = Regex("(?:حاجات|حاجه|حاجة|قطع|منتجات|كتب|اقلام|أقلام)")

  // ——— 3) سلة مشتريات + خصم ———
  private fun solveDiscountBasket(text: String): WordProblemResult? {
    if (!text.contains("خصم")) return null
    val match = Regex(
      "(?:اشتريت|شريت|اشتري|جبت) *$NUMBER *" + "(?:حاجات|حاجه|حاجة|قطع|منتجات|كتب|اقلام|أقلام)? *" +
        "(?:بـ|ب|بسعر|سعر *الواحده?)? *$NUMBER *" + "(?:جنيه|جنيها|جنية|ج)? *.*?خصم *$NUMBER *%"
    ).find(text) ?: return null
    val count = match.groupValues[1].toDoubleOrNull() ?: return null
    val unitPrice = match.groupValues[2].toDoubleOrNull() ?: return null
    val percent = match.groupValues[3].toDoubleOrNull() ?: return null
    if (percent > 100) return null
    val value = count * unitPrice * (1 - percent / 100.0)
    return WordProblemResult.Solved(
      templateId = "discount_basket",
      expression = "${MathFormatting.formatNumber(count)} × ${MathFormatting.formatNumber(unitPrice)} × (1 − ${MathFormatting.formatNumber(percent)} ÷ 100)",
      value = value,
      unitLabel = "جنيه",
      explanation = "الإجمالي قبل الخصم $count × $unitPrice = ${MathFormatting.formatNumber(count * unitPrice)}، " +
        "وبعد خصم $percent% = ${MathFormatting.formatNumber(value)}"
    )
  }

  // ——— 4) زيادة نسبة ———
  private fun solvePercentIncrease(text: String): WordProblemResult? {
    if (!text.contains("زياده") && !text.contains("زيادة") && !text.contains("ارتفاع")) return null
    val match = Regex("$NUMBER *.*?(?:زياده|زيادة|ارتفاع|زاد) *(?:بنسبة)? *$NUMBER *%").find(text) ?: return null
    val original = match.groupValues[1].toDoubleOrNull() ?: return null
    val percent = match.groupValues[2].toDoubleOrNull() ?: return null
    val value = original * (1 + percent / 100.0)
    return WordProblemResult.Solved(
      templateId = "percent_increase",
      expression = "${MathFormatting.formatNumber(original)} × (1 + ${MathFormatting.formatNumber(percent)} ÷ 100)",
      value = value,
      unitLabel = null,
      explanation = "القيمة بعد زيادة $percent%: $original × (1 + $percent ÷ 100) = ${MathFormatting.formatNumber(value)}"
    )
  }

  // ——— 5) متوسط ———
  private fun solveAverage(text: String): WordProblemResult? {
    if (!text.contains("متوسط")) return null
    val tail = text.substringAfter("متوسط")
    val numbers = Regex(NUMBER).findAll(tail).mapNotNull { it.value.toDoubleOrNull() }.toList()
    if (numbers.size < 2) return null
    val value = numbers.sum() / numbers.size
    return WordProblemResult.Solved(
      templateId = "average",
      expression = "(${numbers.joinToString(" + ") { MathFormatting.formatNumber(it) }}) ÷ ${numbers.size}",
      value = value,
      unitLabel = null,
      explanation = "المتوسط = مجموع ${numbers.size} قيم ÷ عددها = ${MathFormatting.formatNumber(value)}"
    )
  }

  // ——— 6) مساحة الدائرة ———
  private fun solveCircleArea(text: String): WordProblemResult? {
    if (!text.contains("مساحه") && !text.contains("مساحة")) return null
    if (!text.contains("دايره") && !text.contains("دائرة") && !text.contains("دائره")) return null

    val radiusMatch = Regex("(?:نصف *القطر|نصف *قطرها|نص *قطرها|نصف *قطر|نص *قطر) *(?:=|هو)? *$NUMBER").find(text)
    if (radiusMatch != null) {
      val radius = radiusMatch.groupValues[1].toDoubleOrNull() ?: return null
      val value = Math.PI * radius * radius
      return WordProblemResult.Solved(
        templateId = "circle_area_radius",
        expression = "π × $radius²",
        value = value,
        unitLabel = "مربع وحدة الطول",
        explanation = "مساحة الدائرة = π × نصف القطر² = π × ${MathFormatting.formatNumber(radius)}² = ${MathFormatting.formatNumber(value)}"
      )
    }

    val diameterMatch = Regex("(?:القطر|قطرها|قطر) *(?:=|هو)? *$NUMBER").find(text)
    if (diameterMatch != null) {
      val diameter = diameterMatch.groupValues[1].toDoubleOrNull() ?: return null
      val radius = diameter / 2.0
      val value = Math.PI * radius * radius
      return WordProblemResult.Solved(
        templateId = "circle_area_diameter",
        expression = "π × ($diameter ÷ 2)²",
        value = value,
        unitLabel = "مربع وحدة الطول",
        explanation = "نصف القطر = $diameter ÷ 2 = ${MathFormatting.formatNumber(radius)}، " +
          "والمساحة = π × ${MathFormatting.formatNumber(radius)}² = ${MathFormatting.formatNumber(value)}"
      )
    }
    return null
  }

  // ——— 7) مساحة المستطيل ———
  private fun solveRectangleArea(text: String): WordProblemResult? {
    if (!text.contains("مساحه") && !text.contains("مساحة")) return null
    if (!text.contains("مستطيل")) return null
    val length = Regex("(?:طول|الطول) *$NUMBER").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val width = Regex("(?:عرض|العرض) *$NUMBER").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val pair = if (length != null && width != null) {
      length to width
    } else {
      val two = Regex(NUMBER).findAll(text).mapNotNull { it.value.toDoubleOrNull() }.take(2).toList()
      if (two.size == 2) two[0] to two[1] else null
    } ?: return null
    val value = pair.first * pair.second
    return WordProblemResult.Solved(
      templateId = "rectangle_area",
      expression = "${MathFormatting.formatNumber(pair.first)} × ${MathFormatting.formatNumber(pair.second)}",
      value = value,
      unitLabel = "مربع وحدة الطول",
      explanation = "مساحة المستطيل = الطول × العرض = ${MathFormatting.formatNumber(value)}"
    )
  }

  // ——— 8) الباقي من المال ———
  private fun solveRemainingMoney(text: String): WordProblemResult? {
    if (!Regex("(?:معايا|معي|عندي|رصيدي|رصيدى)").containsMatchIn(text)) return null
    if (!Regex("(?:صرفت|صرف|دفعت|استهلكت|خصمت)").containsMatchIn(text)) return null
    val numbers = Regex(NUMBER).findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
    if (numbers.size < 2) return null
    val available = numbers[0]
    val spent = numbers[1]
    val value = available - spent
    return WordProblemResult.Solved(
      templateId = "remaining_money",
      expression = "${MathFormatting.formatNumber(available)} − ${MathFormatting.formatNumber(spent)}",
      value = value,
      unitLabel = "جنيه",
      explanation = "المتبقي = ${MathFormatting.formatNumber(available)} − ${MathFormatting.formatNumber(spent)} = ${MathFormatting.formatNumber(value)}"
    )
  }

  private fun normalize(rawInput: String): String {
    val builder = StringBuilder()
    for (ch in rawInput.lowercase()) {
      when (ch) {
        'أ', 'إ', 'آ' -> builder.append('ا')
        'ة' -> builder.append('ه')
        'ى' -> builder.append('ي')
        '٪' -> builder.append('%')
        in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
        in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
        in '\u064B'..'\u065F', 'ـ' -> Unit
        else -> builder.append(ch)
      }
    }
    // نُبقي الأرقام والرموز، ونوحّد المسافات
    return builder.toString().replace(Regex("\\s+"), " ").trim()
  }
}
