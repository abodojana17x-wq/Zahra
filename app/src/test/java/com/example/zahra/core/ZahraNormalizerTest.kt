package com.example.zahra.core

import org.junit.Assert.*
import org.junit.Test

/**
 * توصيف سلوك [ZahraNormalizer] قبل وبعد التوسع (المسار ب — المرحلة 1).
 *
 * ملاحظة البيئة: لا توجد JDK ولا Android SDK في هذه الحجرة، هذه الاختبارات
 * مكتوبة وثائقية ولم تُشغّل بعد. لا يُصف أي اختبار بأنه ناجح قبل تشغيل فعلي.
 */
class ZahraNormalizerTest {

 // --- توحيد الحروف (همزة، مربوطة، مقصورة، تشكيل، أرقام) ---

 @Test
 fun normalize_unifiesAlefVariants() {
  assertEquals("احمد", ZahraNormalizer.normalize("أحمد"))
  assertEquals("اسلام", ZahraNormalizer.normalize("إسلام"))
  assertEquals("اية", ZahraNormalizer.normalize("آية"))
 }

 @Test
 fun normalize_unifiesYaaAndTaMarbuta() {
  assertEquals("مدرسه", ZahraNormalizer.normalize("مدرسة"))
  assertEquals("صحراي", ZahraNormalizer.normalize("صحراء"))
  assertEquals("مستشفى", ZahraNormalizer.normalize("مستشفى")) // ه unchanged
 }

 @Test
 fun normalize_removesTashkeel() {
  assertEquals("كتب", ZahraNormalizer.normalize("كُتِبَ"))
  assertEquals("السلام عليكم", ZahraNormalizer.normalize("السَّلَامُ عَلَيْكُمْ"))
 }

 @Test
 fun normalize_unifiesIndicDigits() {
  assertEquals("2026", ZahraNormalizer.normalize("٢٠٢٦"))
  assertEquals("123", ZahraNormalizer.normalize("۱۲۳")) //astern arabic-indic
  assertEquals("الساعة ٥", ZahraNormalizer.normalize("الساعة ٥"))
 }

 @Test
 fun normalize_colloquialCanonicalization() {
  assertEquals("الان", ZahraNormalizer.normalize("دلوقتي"))
  assertEquals("غدا", ZahraNormalizer.normalize("بكره"))
  assertEquals("امس", ZahraNormalizer.normalize("امبارح"))
 }

 // --- فرانكو: عبارات قبل مفردات ---

 @Test
 fun normalize_francoPhraseBeforeWords() {
  assertEquals("كيف حالك", ZahraNormalizer.normalize("how are you"))
  assertEquals("عامل ايه", ZahraNormalizer.normalize("3amel eh"))
  assertEquals("صباح الخير", ZahraNormalizer.normalize("saba7 el kher"))
 }

 @Test
 fun normalize_francoLexiconEntries() {
  assertEquals("ازيك", ZahraNormalizer.normalize("ezayak"))
  assertEquals("عايز", ZahraNormalizer.normalize("3ayez"))
  assertEquals("كده", ZahraNormalizer.normalize("keda"))
  assertEquals("مش", ZahraNormalizer.normalize("mish"))
  assertEquals("مفيش", ZahraNormalizer.normalize("mafeesh"))
  assertEquals("دلوقتي", ZahraNormalizer.normalize("delwa2ty")) // luego colloquial -> الان
  assertEquals("الان", ZahraNormalizer.normalize("delwa2ty")) // دلوقتي -> الان
 }

 @Test
 fun normalize_francoDigitLetters() {
  // 2 = ء محاذٍ للحروف اللاتينية
  assertTrue(ZahraNormalizer.normalize("a2ol").contains("ء"))
  assertTrue(ZahraNormalizer.francoSignals("a2ol lak").isFranco)
  assertTrue(ZahraNormalizer.francoSignals("a2ol lak").digitPatterns > 0)
 }

 // --- طول القاموس وفائدته (لا حشو) ---

 @Test
 fun francoLexiconHasAtLeast300UsefulEntries() {
  assertTrue("القاموس يجب أن يحوي على الأقل 300 مدخل فريد", ZahraNormalizer.FRANCO_LEXICON.size >= 300)
  // المدخلات غير فارغة ومفيدة
  ZahraNormalizer.FRANCO_LEXICON.forEach { (k, v) ->
   assertTrue("مفتاح فارغ: $k", k.isNotBlank())
   assertTrue("قيمة فارغة للمفتاح: $k", v.isNotBlank())
  }
 }

 // --- حماية المعنى: النفي، الأرقام، التعبيرات الحسابية، المتغيرات، المصطلحات البرمجية ---

 @Test
 fun normalize_preservesNegation() {
  assertEquals("مش كده", ZahraNormalizer.normalize("مش كده"))
  assertEquals("ما رحتش", ZahraNormalizer.normalize("ما رحتش"))
  assertEquals("لا أعرف", ZahraNormalizer.normalize("لا أعرف"))
  // مفيش لا تُصرَّف إلى شيء آخر
  assertEquals("مفيش فلوس", ZahraNormalizer.normalize("مفيش فلوس"))
 }

 @Test
 fun normalize_preservesArithmeticAndNumbers() {
  assertEquals("احسب 50 * 4 + 2", ZahraNormalizer.normalize("احسب 50 * 4 + 2"))
  assertEquals("احسب (100 + 20) / 4", ZahraNormalizer.normalize("احسب (100 + 20) / 4"))
  assertEquals("نتيجة 250 = 1000", ZahraNormalizer.normalize("نتيجة 250 = 1000"))
 }

 @Test
 fun normalize_preservesVariableAndCodeTerms() {
  assertEquals("x = 5 + y", ZahraNormalizer.normalize("x = 5 + y"))
  assertEquals("userName", ZahraNormalizer.normalize("userName"))
  assertEquals("count++", ZahraNormalizer.normalize("count++"))
  assertEquals("calc 25 * 4", ZahraNormalizer.normalize("calc 25 * 4"))
 }

 @Test
 fun normalize_mixedSentenceArabicEnglishFrancoDigits() {
  val out = ZahraNormalizer.normalize("إزاي حالك اليوم؟ how are you 123")
  assertTrue(out.contains("إزاي"))
  assertTrue(out.contains("اليوم"))
  assertTrue(out.contains("123"))
  // العبارة المركبة تُطبق قبل المفردات: "how are you" تُترجم كوحدة واحدة
  assertTrue(out.contains("كيف حالك"))
  assertFalse(out.contains("how are you"))
 }

 // --- تجذيع خفيف مع الاستثناءات ---

 @Test
 fun stemLight_removesArticleAndSuffixesConservatively() {
  assertEquals("مدرس", ZahraNormalizer.stemLight("المدرسون"))
  assertEquals("كاتب", ZahraNormalizer.stemLight("الكتابين")) // كتاب + ين -> كتاب
 }

 @Test
 fun stemLight_protectsExceptions() {
  assertEquals("مش", ZahraNormalizer.stemLight("مش"))
  assertEquals("مفيش", ZahraNormalizer.stemLight("مفيش"))
  assertEquals("لا", ZahraNormalizer.stemLight("لا"))
  assertEquals("الله", ZahraNormalizer.stemLight("الله"))
  assertEquals("القرآن", ZahraNormalizer.stemLight("القرآن"))
  assertEquals("كود", ZahraNormalizer.stemLight("كود"))
  assertEquals("برمجة", ZahraNormalizer.stemLight("برمجة"))
  assertEquals("متغير", ZahraNormalizer.stemLight("متغير"))
 }

 @Test
 fun stemLight_protectsLatinAndDigits() {
  assertEquals("userName", ZahraNormalizer.stemLight("userName"))
  assertEquals("x = 5 + y", ZahraNormalizer.stemLight("x = 5 + y"))
 }

 @Test
 fun normalizeStems_appliesStemmingPerWord() {
  assertEquals("مدرس يدرس", ZahraNormalizer.normalizeStems("المدرسون يدرسون"))
 }

 @Test
 fun extractWords_filtersStopwordsAndShortTokens() {
  val words = ZahraNormalizer.extractWords("هذا الكتاب مفيد جداً في هذا المجال")
  assertFalse(words.contains("هذا"))
  assertFalse(words.contains("في"))
  // التجزيع لا يُطبّق في extractWords، لذلك تُحتفظ بالبادئة "ال"
  assertTrue(words.contains("الكتاب"))
  assertTrue(words.contains("مفيد"))
  assertTrue(words.contains("المجال"))
  assertTrue(words.size <= 6)
 }
}
