package com.example.eval

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive

/**
 * تطبيع النص العربي ومُقيّم التوقعات — المرحلة 0.
 *
 * القاعدة: التقييم يجب أن يكون **حتميًا ومعجميًا** (لا رأي ولا محكّم نموذج)،
 * لأن الهدف قياس قابل لإعادة التشغيل. أي تساهل هنا يُسجَّل صراحةً في
 * evidence/phase-00/research.md (بند: كيف نمنع التقييم المتساهل).
 */
object TextNorm {
  private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

  fun normalize(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
      val mapped = when (ch) {
        in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
        in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ة' -> 'ه'
        'ى' -> 'ي'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        '٫' -> '.'
        '٬', '\u060C' -> ','
        else -> ch
      }
      sb.append(mapped)
    }
    return DIACRITICS.replace(sb.toString(), "")
      .lowercase()
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private val NUM = Regex("""\d+(?:[.,]\d+)?""")
  private val THOUSANDS = Regex("""(\d),(\d{3})(?!\d)""")

  /** كل الأرقام في النص (يشمل الأرقام العربية-الهندية بعد التطبيع، وعلامة الآلاف). */
  fun numbers(text: String): List<Double> {
    val norm = normalize(text)
    val noThousands = THOUSANDS.replace(norm) { m -> m.groupValues[1] + m.groupValues[2] }
    return NUM.findAll(norm).map { it.value }.plus(NUM.findAll(noThousands).map { it.value })
      .mapNotNull { it.replace(',', '.').toDoubleOrNull() }
      .toList()
  }
}

data class ScoreOutcome(val passed: Boolean, val reason: String)

object EvalScorer {

  private val REFUSAL_MARKERS = listOf(
    "لا استطيع", "لا اقدر", "لا اعرف", "مش عارف", "مش متاكد", "مش متأكد",
    "معلومه غير", "معلومه مش متوفره", "لا توجد", "لا توجد لدي", "غير متوفر",
    "معنديش", "ما عندي", "لا املك", "محتاج مصادر", "مصدر موثوق", "لا يمكنني",
    "محتاج توضيح", "أحتاج توضيح", "معلوماتي لا", "لا اتوفر", "لا تتوفر",
    "اسف", "آسف", "مش هيطلع", "مش هينفع", "مش مسموح", "لا يجوز ان اساعد",
  )

  private val ABSTAIN_STATES = setOf("UNSUPPORTED", "AMBIGUOUS")

  fun isRefusal(normalizedText: String, outcomeState: String): Boolean {
    if (outcomeState == "UNSUPPORTED") return true
    if (REFUSAL_MARKERS.any { normalizedText.contains(it) }) return true
    return false
  }

  fun score(case: EvalCase, text: String, source: String?, outcomeState: String): ScoreOutcome {
    val norm = TextNorm.normalize(text)
    val refused = isRefusal(norm, outcomeState)
    val type = case.expect.type
    val expected = case.expect.value

    if (type == "refusal") {
      return if (refused) {
        ScoreOutcome(true, "refused")
      } else {
        ScoreOutcome(false, "expected_explicit_refusal_but_answered")
      }
    }
    if (refused) {
      return ScoreOutcome(false, "unexpected_refusal(${outcomeState})")
    }

    return when (type) {
      "numeric" -> {
        val want = expected?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        val wantValue = when {
          want == null -> null
          want.isNumber -> want.asDouble
          want.isString -> want.asString.replace(',', '.').toDoubleOrNull()
          else -> null
        } ?: return ScoreOutcome(false, "bad_expect_value")
        val tol = case.expect.tolerance ?: 0.01
        val found = TextNorm.numbers(text)
        val hit = found.firstOrNull { kotlin.math.abs(it - wantValue) <= tol }
        if (hit != null) {
          ScoreOutcome(true, "numeric_ok($wantValue)")
        } else {
          ScoreOutcome(false, "numeric_mismatch(want=$wantValue,found=${found.take(6)})")
        }
      }

      "contains_any" -> {
        val terms = termsOf(expected)
        if (terms.isEmpty()) return ScoreOutcome(false, "bad_expect_value")
        val hit = terms.firstOrNull { norm.contains(TextNorm.normalize(it)) }
        if (hit != null) ScoreOutcome(true, "contains_any_ok($hit)")
        else ScoreOutcome(false, "none_of_terms_present(${terms.take(4)})")
      }

      "regex" -> {
        val pattern = expected?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString
          ?: return ScoreOutcome(false, "bad_expect_value")
        val rx = try {
          Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
          return ScoreOutcome(false, "bad_regex($pattern)")
        }
        if (rx.containsMatchIn(text) || rx.containsMatchIn(norm)) {
          ScoreOutcome(true, "regex_ok")
        } else {
          ScoreOutcome(false, "regex_no_match($pattern)")
        }
      }

      "source_required" -> {
        if (!source.isNullOrBlank()) ScoreOutcome(true, "source_present")
        else ScoreOutcome(false, "source_missing")
      }

      "exact_code" -> {
        val want = expected?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString ?: ""
        val squeeze = { s: String -> s.replace(Regex("\\s+"), "") }
        if (want.isNotEmpty() && squeeze(text).contains(squeeze(want))) {
          ScoreOutcome(true, "exact_code_ok")
        } else {
          ScoreOutcome(false, "exact_code_missing($want)")
        }
      }

      "slot_value" -> {
        val terms = termsOf(expected)
        val hit = terms.firstOrNull { norm.contains(TextNorm.normalize(it)) }
        if (hit != null) ScoreOutcome(true, "slot_ok($hit)")
        else ScoreOutcome(false, "slot_missing(${terms.take(4)})")
      }

      else -> ScoreOutcome(false, "unknown_expect_type($type)")
    }
  }

  fun abstained(outcomeState: String): Boolean = ABSTAIN_STATES.contains(outcomeState)

  private fun termsOf(element: com.google.gson.JsonElement?): List<String> {
    if (element == null) return emptyList()
    if (element.isJsonArray) {
      val array: JsonArray = element.asJsonArray
      return array.mapNotNull { item: com.google.gson.JsonElement ->
        if (item.isJsonPrimitive) {
          val prim: JsonPrimitive = item.asJsonPrimitive
          if (prim.isString) prim.asString else prim.toString()
        } else {
          null
        }
      }
    }
    if (element.isJsonPrimitive) {
      val prim = element.asJsonPrimitive
      return listOf(if (prim.isString) prim.asString else prim.toString())
    }
    return emptyList()
  }
}
