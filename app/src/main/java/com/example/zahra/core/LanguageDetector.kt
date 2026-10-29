package com.example.zahra.core

enum class DetectedLang {
  AR,
  EN,
  FR,
  FRANCO,
  MIXED,
  UNKNOWN
}

data class LangResult(
  val language: DetectedLang,
  val script: String,
  val confidence: Double
)

object LanguageDetector {
  private val ARABIC_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
  private val LATIN_REGEX = Regex("[a-zA-Z\\u00C0-\\u00FF]")
  private val FRENCH_ACCENTS = Regex("[éèêëàâçîïôûùüœæ]", RegexOption.IGNORE_CASE)
  private val FRENCH_STOPWORDS = Regex("\\b(le|la|les|un|une|des|du|de|et|est|en|que|qui|dans)\\b", RegexOption.IGNORE_CASE)

  /**
   * كشف لغة النص مع درجة ثقة موثقة المصدر، لا قرار Boolean فقط.
   *
   * مصادر الدرجات:
   * - نسبة الحروف لكل خليط → المدى [0.7, 1.0] (نفس منطق الإصدار الحالي).
   * - فرانكو: أنماط الأرقام-الحروف (2=ء, 3=ع, 5=خ, 7=ح, 8=غ, 9=ق) ومطابقات قاموس الفرانكو
   *   (`ZahraNormalizer.FRANCO_LEXICON` / `FRANCO_PHRASES`، راجع `evidence/phase-01/research.md` المصدر 5).
   *   الدرجة ترتفع مع عدد المطابقات حتى سقف 0.97؛ لا تُدعى الاحتمالية معايَرة.
   */
  fun detect(text: String?): LangResult {
    if (text.isNullOrBlank()) {
      return LangResult(DetectedLang.UNKNOWN, "None", 0.0)
    }

    val arCount = ARABIC_REGEX.findAll(text).count()
    val latinCount = LATIN_REGEX.findAll(text).count()
    val total = arCount + latinCount

    if (total == 0) {
      return LangResult(DetectedLang.UNKNOWN, "NumericOrSymbol", 0.5)
    }

    val arRatio = arCount.toDouble() / total
    val latinRatio = latinCount.toDouble() / total

    //schrift عربية غالبة
    if (arRatio > 0.75) {
      return LangResult(DetectedLang.AR, "Arabic", (0.7 + arRatio * 0.3).coerceAtMost(1.0))
    }

    //schrift لاتينية غالبة
    if (latinRatio > 0.75) {
      val signals = ZahraNormalizer.francoSignals(text)
      if (signals.isFranco) {
        return LangResult(
          DetectedLang.FRANCO,
          "Franco-Arabic",
          signals.confidence.coerceAtMost(1.0)
        )
      }

      val isFrench = FRENCH_ACCENTS.containsMatchIn(text) || FRENCH_STOPWORDS.containsMatchIn(text)
      return LangResult(
        if (isFrench) DetectedLang.FR else DetectedLang.EN,
        "Latin",
        (0.7 + latinRatio * 0.3).coerceAtMost(1.0)
      )
    }

    // خليط عربي + لاتيني
    val signals = ZahraNormalizer.francoSignals(text)
    val francoBoost = if (signals.isFranco) 0.1 else 0.0
    return LangResult(
      DetectedLang.MIXED,
      "Mixed",
      (0.6 + francoBoost).coerceAtMost(0.85)
    )
  }
}
