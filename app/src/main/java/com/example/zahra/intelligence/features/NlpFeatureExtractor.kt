package com.example.zahra.intelligence.features

import com.example.zahra.core.ZahraNormalizer
import kotlin.math.sqrt

data class ExtractedFeatures(
  val tokens: List<String>,
  val bigrams: List<String>,
  val charNgrams: List<String>,
  val affixFeatures: List<String>,
  val lexicalCues: Map<String, Double>,
  val featureVector: Map<String, Double>
)

object NlpFeatureExtractor {

  private val ARABIC_PREFIXES = listOf("ال", "لل", "بال", "فال", "كال", "وال", "و", "ف", "ب", "ل", "ك", "س")
  private val ARABIC_SUFFIXES = listOf("هما", "هم", "هن", "ها", "كم", "كن", "نا", "ني", "ي", "ات", "ون", "ين", "ان", "تا", "ه")

  private val MATH_OPERATOR_PATTERN = Regex("""[+\-*/×÷^%=]|(?<![\\p{L}\\p{N}])(?:ضرب|قسمه|قسمة|جمع|طرح|زائد|ناقص|جذر|نسبه|نسبة|بالمئه|بالمئة|calc|math)(?![\\p{L}\\p{N}])""")
  private val DATE_CUE_PATTERN = Regex("""(?<![\\p{L}\\p{N}])(?:بكره|بكرة|غدا|اليوم|امس|ساعه|ساعة|دقائق|دقيقه|دقيقة|صباحا|مساء|عصر|فجر|ظهر|مغرب|عشاء|جدول|تذكير|منبه|remind|schedule|tomorrow|today)(?![\\p{L}\\p{N}])""")
  private val QUESTION_CUE_PATTERN = Regex("""(?<![\\p{L}\\p{N}])(?:ما|ماذا|هل|اين|أين|كيف|متى|من|كم|لماذا|عرف|شرح|what|how|when|where|who|why|define)(?![\\p{L}\\p{N}])""")
  private val IMPERATIVE_CUE_PATTERN = Regex("""(?<![\\p{L}\\p{N}])(?:احسب|لخص|ترجم|سجل|اضف|أضف|فكرني|ذكرني|حول|صمم|ارسم|اعرض|calc|summarize|translate|add|save|remind|convert)(?![\\p{L}\\p{N}])""")

  /**
   * Extract comprehensive lexical, morphological, n-gram, and structural features.
   */
  fun extract(rawText: String): ExtractedFeatures {
    val clean = rawText.trim()
    val normalized = ZahraNormalizer.normalize(clean)
    val tokens = ZahraNormalizer.extractWords(normalized)

    // 1. Bigrams
    val bigrams = mutableListOf<String>()
    for (i in 0 until tokens.size - 1) {
      bigrams.add("${tokens[i]}_${tokens[i + 1]}")
    }

    // 2. Character n-grams (3-grams and 4-grams)
    val charNgrams = mutableListOf<String>()
    for (token in tokens) {
      if (token.length >= 3) {
        for (i in 0..token.length - 3) {
          charNgrams.add("c3_" + token.substring(i, i + 3))
        }
      }
      if (token.length >= 4) {
        for (i in 0..token.length - 4) {
          charNgrams.add("c4_" + token.substring(i, i + 4))
        }
      }
    }

    // 3. Morphological Affixes
    val affixes = mutableListOf<String>()
    for (token in tokens) {
      if (token.length >= 4) {
        for (p in ARABIC_PREFIXES) {
          if (token.startsWith(p) && token.length - p.length >= 3) {
            affixes.add("pfx_$p")
            break
          }
        }
        for (s in ARABIC_SUFFIXES) {
          if (token.endsWith(s) && token.length - s.length >= 3) {
            affixes.add("sfx_$s")
            break
          }
        }
      }
    }

    // 4. Lexical and Structural Cues
    val lexicalCues = mutableMapOf<String, Double>()
    lexicalCues["cue_has_digits"] = if (Regex("""[0-9]""").containsMatchIn(normalized)) 1.0 else 0.0
    lexicalCues["cue_has_math_op"] = if (MATH_OPERATOR_PATTERN.containsMatchIn(normalized)) 1.0 else 0.0
    lexicalCues["cue_has_date"] = if (DATE_CUE_PATTERN.containsMatchIn(normalized)) 1.0 else 0.0
    lexicalCues["cue_has_question"] = if (QUESTION_CUE_PATTERN.containsMatchIn(normalized)) 1.0 else 0.0
    lexicalCues["cue_has_imperative"] = if (IMPERATIVE_CUE_PATTERN.containsMatchIn(normalized)) 1.0 else 0.0
    lexicalCues["cue_token_count"] = tokens.size.toDouble()

    var arChars = 0
    var latChars = 0
    var numChars = 0
    for (ch in clean) {
      when {
        ch in '\u0600'..'\u06FF' -> arChars++
        ch in 'a'..'z' || ch in 'A'..'Z' -> latChars++
        ch.isDigit() -> numChars++
      }
    }
    val totalChars = clean.length.coerceAtLeast(1).toDouble()
    lexicalCues["ratio_arabic"] = arChars / totalChars
    lexicalCues["ratio_latin"] = latChars / totalChars
    lexicalCues["ratio_digits"] = numChars / totalChars

    // 5. Construct Sparse Feature Vector
    val featureCounts = mutableMapOf<String, Double>()
    // Word tokens (weight 1.5)
    for (w in tokens) {
      featureCounts["w_$w"] = (featureCounts["w_$w"] ?: 0.0) + 1.5
    }
    // Bigrams (weight 2.0)
    for (bg in bigrams) {
      featureCounts["bg_$bg"] = (featureCounts["bg_$bg"] ?: 0.0) + 2.0
    }
    // Char n-grams (weight 0.4)
    for (cg in charNgrams) {
      featureCounts[cg] = (featureCounts[cg] ?: 0.0) + 0.4
    }
    // Affixes (weight 0.6)
    for (af in affixes) {
      featureCounts[af] = (featureCounts[af] ?: 0.0) + 0.6
    }
    // Lexical cues (weight 1.5 for active binary cues)
    for ((cue, v) in lexicalCues) {
      if (cue.startsWith("cue_has_") && v > 0.0) {
        featureCounts[cue] = v * 1.5
      }
    }

    // L2 Normalization
    var sumSq = 0.0
    for (v in featureCounts.values) {
      sumSq += v * v
    }
    val norm = sqrt(sumSq).coerceAtLeast(1e-9)
    val normalizedVector = mutableMapOf<String, Double>()
    for ((k, v) in featureCounts) {
      normalizedVector[k] = v / norm
    }

    return ExtractedFeatures(
      tokens = tokens,
      bigrams = bigrams,
      charNgrams = charNgrams,
      affixFeatures = affixes,
      lexicalCues = lexicalCues,
      featureVector = normalizedVector
    )
  }
}
