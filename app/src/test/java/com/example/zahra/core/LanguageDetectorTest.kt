package com.example.zahra.core

import org.junit.Assert.*
import org.junit.Test

/**
 * توصيف سلوك [LanguageDetector]: كشف اللغة بدرجة ثقة (ليست Boolean فقط).
 * لم تُشغّل بعد (لا JDK/Android SDK في بيئة هذه الحجرة).
 */
class LanguageDetectorTest {

  @Test
  fun detect_arabicText_returnsARWithHighConfidence() {
    val res = LanguageDetector.detect("كيف حالك اليوم؟")
    assertEquals(DetectedLang.AR, res.language)
    assertEquals("Arabic", res.script)
    assertTrue(res.confidence >= 0.7 && res.confidence <= 1.0)
  }

  @Test
  fun detect_englishText_returnsEN() {
    val res = LanguageDetector.detect("how are you today")
    assertEquals(DetectedLang.EN, res.language)
    assertTrue(res.confidence > 0.0)
  }

  @Test
  fun detect_frenchText_returnsFR() {
    val res = LanguageDetector.detect("c'est la vie")
    assertEquals(DetectedLang.FR, res.language)
  }

  @Test
  fun detect_francoWithDigits_returnsFrancoWithConfidence() {
    val res = LanguageDetector.detect("ana 3ayez a3raf")
    assertEquals(DetectedLang.FRANCO, res.language)
    assertEquals("Franco-Arabic", res.script)
    assertTrue(res.confidence > 0.0, "الثقة يجب أن تكون موجبة للفرانكو")
    assertTrue(res.confidence <= 1.0)
  }

  @Test
  fun detect_francoDigitsAdjacent_returnsFranco() {
    val res = LanguageDetector.detect("a2ol lak")
    assertEquals(DetectedLang.FRANCO, res.language)
    assertTrue(res.confidence > 0.0)
  }

  @Test
  fun detect_mixedArabicLatin_returnsMixed() {
    val res = LanguageDetector.detect("كيف are you اليوم؟")
    assertEquals(DetectedLang.MIXED, res.language)
    assertEquals("Mixed", res.script)
    assertTrue(res.confidence > 0.0)
  }

  @Test
  fun detect_emptyOrSymbols_returnsUnknown() {
    val empty = LanguageDetector.detect("")
    assertEquals(DetectedLang.UNKNOWN, empty.language)
    assertEquals(0.0, empty.confidence, 0.001)

    val symbols = LanguageDetector.detect("123 !!!")
    assertEquals(DetectedLang.UNKNOWN, symbols.language)
  }

  @Test
  fun detect_confidenceIsNeverBooleanOnly() {
    // كل نتيجة تحمل درجة في المدى [0, 1] regardless of the class
    val cases = listOf(
      "كيف حالك",
      "how are you",
      "ezayak",
      "ciao",
      "123"
    )
    cases.forEach { c ->
      val res = LanguageDetector.detect(c)
      assertTrue("نص: $c → ثقة ${res.confidence}", res.confidence in 0.0..1.0)
    }
  }
}
