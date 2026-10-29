package com.example.zahra.knowledge.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * توصيف [UnitTokenizer] (المرحلة 3): التطبيع، إسقاط كلمات الوقف **المطبَّعة**، التجذيع الخفيف،
 * ومجموعات الحروف المستخدمة في تحمّل الإملاء والفرانكو.
 *
 * ⚠️ لم تُشغَّل هذه الاختبارات في هذه الحجرة (لا JDK ولا Android SDK). لا تُوصف بأنها ناجحة قبل تشغيل فعلي.
 */
class UnitTokenizerTest {

  @Test
  fun tokenize_dropsStopwordsAndNormalizes() {
    assertEquals(listOf("closure"), UnitTokenizer.tokenize("يعني ايه closure"))
    // «فايدة» و«البيانات» محتوى لا وقف؛ التجذيع يحدث في indexedTokens لا في tokenize.
    assertEquals(listOf("فايده", "index", "قاعده", "البيانات"),
      UnitTokenizer.tokenize("ايه فايدة index في قاعدة البيانات"))
  }

  @Test
  fun tokenize_normalizesDefiniteArticleAlone() {
    // «الـ index» تُقطَّع إلى «ال» + «index»؛ «ال» وحدها لا تحمل موضوعًا فتُسقط.
    assertFalse(UnitTokenizer.tokenize("الـ index").contains("ال"))
  }

  @Test
  fun stopwords_areStoredInNormalizedForm() {
    // «على» تُطبَّع إلى «علي»؛ لو بقيت القائمة بصيغتها الأصلية لتسرّبت «علي» كدليل مطابقة.
    assertTrue(UnitTokenizer.STOPWORDS.contains("علي"))
    assertFalse(UnitTokenizer.tokenize("المصادقة على العقد").contains("علي"))
  }

  @Test
  fun tokenize_convertsFrancoDigitsToArabicLetters() {
    val tokens = UnitTokenizer.tokenize("3ayez a3raf el index")
    assertTrue("المتوقع احتواء «اعرف» لكن كانت: $tokens", tokens.contains("اعرف"))
    assertFalse(tokens.contains("a3raf"))
  }

  @Test
  fun hasSubstantiveTokens_rejectsPureStopwords() {
    assertFalse(UnitTokenizer.hasSubstantiveTokens("ما هو ده"))
    assertTrue(UnitTokenizer.hasSubstantiveTokens("يعني ايه index"))
  }

  @Test
  fun stem_keepsNegationAndTechnicalTerms() {
    assertEquals("مش", UnitTokenizer.stem("مش"))
    assertEquals("كود", UnitTokenizer.stem("كود"))
    // تجذيع خفيف: سوابق (و/ب/ك/ل) تُزال عندما يبقى أصل معتبر
    assertEquals(UnitTokenizer.stem("كتاب"), UnitTokenizer.stem("وكتاب"))
  }

  @Test
  fun charGrams_areRepeatInsensitiveAndBoundaryAware() {
    val a = UnitTokenizer.charGrams("closure")
    val b = UnitTokenizer.charGrams("closure closure")
    assertEquals("التكرار لا يغيّر المجموعة", a, b)
    assertTrue("حدود الكلمة ممثلة", a.any { it.startsWith("^") })
  }

  @Test
  fun jaccard_separatesTypoFromUnrelatedWord() {
    val typo = UnitTokenizer.jaccard(UnitTokenizer.charGrams("closure"), UnitTokenizer.charGrams("closur"))
    val unrelated = UnitTokenizer.jaccard(UnitTokenizer.charGrams("closure"), UnitTokenizer.charGrams("قاعدة"))
    assertTrue("خطأ حرف واحد يجب أن يبقى فوق 0.5 (فعلي=$typo)", typo >= 0.5)
    assertTrue("كلمة غير ذات صلة يجب أن تقترب من الصفر (فعلي=$unrelated)", unrelated < 0.1)
    assertNotNull(unitGramsSample())
  }

  private fun unitGramsSample(): Any = UnitTokenizer.charGrams("الـ Closure في JavaScript")
}
