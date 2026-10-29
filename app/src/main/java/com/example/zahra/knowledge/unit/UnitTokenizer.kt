package com.example.zahra.knowledge.unit

import com.example.zahra.core.ZahraNormalizer

/**
 * مُقطِّع ونظام تطبيع خاص بفهرس وحدات المعرفة (المرحلة 3).
 *
 * لماذا ملف جديد بدل `ArabicKnowledgeNormalizer`؟
 * - `ArabicKnowledgeNormalizer` يخدم السجلات القديمة (`KnowledgeRecord`) بسلوكه الخاص؛ لا نغيّره حتى لا نكسر
 *   `KnowledgeEngine` القائم.
 * - هنا نعيد استخدام التطبيع الموحّد من المرحلة 1 ([ZahraNormalizer]) حرفيًا (عربي + مصري + فرانكو + تجذيع خفيف)
 *   حتى تكون كل المسارات على نفس التعريف.
 *
 * ⚠️ هذا الملف هو **مرجع الحقيقة** للتقطيع: نسخة البناء (`tools/build_index.py`) تُطابق توائم هذا الملف
 * مقابل `knowledge/unit/tokenizer-fixtures.json`؛ أي اختلاف يُفشل البناء (D24).
 */
object UnitTokenizer {

  /** كلمات وقف زائدة عن قائمة [ZahraNormalizer.ARABIC_STOPWORDS] — كلمات سؤال لا تحمل موضوعًا. */
  val EXTRA_STOPWORDS: Set<String> = setOf(
    // استفهام وصيغ طلب عربي/مصري
    "ايه", "إيه", "يعني", "ازاي", "إزاي", "ازاى", "مين", "امتى", "فين", "ليه", "ليش", "ايش",
    "قولي", "قولى", "قول", "اشرح", "اشرحلي", "وضح", "وضحلي", "عرفني", "عرفنى", "عايز", "عاوز",
    "عايزه", "محتاج", "ممكن", "لو", "سمحت", "فضلك", "باختصار", "بسرعة", "كده", "ده", "دي", "دول",
    "تعريف", "معنى", "شرح", "مثال", "امثلة", "أمثلة", "فرق", "الفرق", "بين", "ولا",
    // أدوات تعريف سائبة: تظهر وحدها عند «الـ index» ولا تحمل موضوعًا
    "ال", "el", "الـ",
    // إنجليزية
    "what", "whats", "is", "are", "was", "the", "a", "an", "of", "to", "how", "why", "do", "does",
    "did", "i", "you", "me", "my", "we", "in", "on", "for", "and", "or", "it", "its", "that", "this",
    "with", "explain", "define", "meaning", "tell", "about", "please", "can", "should", "when", "where",
    "which", "who", "difference", "between", "vs", "versus", "example"
  )

  /**
   * كل كلمات الوقف المستخدمة في التقطيع **بصيغتها المطبَّعة**.
   *
   * لماذا التطبيع؟ لأن الرمزات تُقارن بعد [ZahraNormalizer.normalize] (ى→ي، إزالة التشكيل، توحيد الهمزات)،
   * فكلمة مثل «على» تصبح «علي» ولا تطابق مدخل القائمة الأصلي. بدون هذا التطبيع تتسرّب كلمات وقف إلى
   * المطابقة (شوهد فعليًا: «علي» تُحسب دليلًا لصالح وحدة لا علاقة لها بالسؤال).
   */
  val STOPWORDS: Set<String> = (ZahraNormalizer.ARABIC_STOPWORDS + EXTRA_STOPWORDS)
    .flatMap { listOf(it, ZahraNormalizer.normalize(it).lowercase()) }
    .toSet()

  /** الحد الأدنى لطول الرمز المقبول (نفس سياسة بقية التطبيق: حرفان على الأقل). */
  const val MIN_TOKEN_LENGTH = 2

  /** حجم بصمة الحروف (3-gram في الخطة). */
  const val GRAM_SIZE = 3

  private val WORD_REGEX = Regex("[\\p{L}\\p{N}]+")

  /** يحوّل النص إلى رمزات مطبَّعة (بلا تجذيع) مع إسقاط كلمات الوقف. */
  fun tokenize(text: String): List<String> {
    val normalized = ZahraNormalizer.normalize(text).lowercase()
    val words = WORD_REGEX.findAll(normalized)
      .map { it.value }
      .filter { it.length >= MIN_TOKEN_LENGTH }
      .toList()
    if (words.isEmpty()) return emptyList()
    val filtered = words.filterNot { STOPWORDS.contains(it) }
    // إن كان كل النص كلمات وقف (مثل «ما هو ده») لا نُعيد قائمة فارغة حتى يظل الاستعلام قابلًا للتفسير.
    return if (filtered.isEmpty()) words else filtered
  }

  /** تجذيع خفيف محافظ لكل رمز (نفس `stemLight` في المرحلة 1). */
  fun stem(token: String): String = ZahraNormalizer.stemLight(token)

  /** رمزات مجذّعة جاهزة للفهرسة/الاستعلام. */
  fun indexedTokens(text: String): List<String> = tokenize(text).map { stem(it) }

  /** هل النص يحتوي أي رمز جوهري (بعد إسقاط كلمات الوقف)؟ */
  fun hasSubstantiveTokens(text: String): Boolean = tokenize(text).any { !STOPWORDS.contains(it) }

  /**
   * بصمة حروف (3-gram) لكل رمز مع حدود كلمة `^` و`$`، تُستخدم لمطابقة الأخطاء الإملائية والفرانكو.
   *
   * تُبنى من الرمزات **بعد إسقاط كلمات الوقف** حتى لا تتشابه كل الأسئلة في «ما هو / ازاي».
   * Jaccard محسوب على المجموعات (لا التكرار) — وهذا مقصود: الغرض تحمّل الإملاء لا ترتيب الأهمية.
   */
  fun charGrams(text: String): Set<String> {
    val tokens = tokenize(text)
    if (tokens.isEmpty()) return emptySet()
    val grams = HashSet<String>(tokens.size * 8)
    for (token in tokens) {
      val padded = "^$token$"
      if (padded.length <= GRAM_SIZE) {
        grams.add(padded)
        continue
      }
      for (i in 0..(padded.length - GRAM_SIZE)) {
        grams.add(padded.substring(i, i + GRAM_SIZE))
      }
    }
    return grams
  }

  /** Jaccard بين مجموعتين من البصمات (0.0 عند غياب أي تقاطع، و0.0 لمجموعة فارغة). */
  fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    var intersection = 0
    // نمرّ على الأصغر لتقليل العمل
    val (small, large) = if (a.size <= b.size) a to b else b to a
    for (gram in small) {
      if (large.contains(gram)) intersection++
    }
    val union = a.size + b.size - intersection
    return if (union <= 0) 0.0 else intersection.toDouble() / union
  }

  /**
   * توائم التقطيع المعلنة للتحقق المتقاطع مع سكربت البناء.
   * تُستخدم لكتابة `knowledge/unit/tokenizer-fixtures.json` وفحصها في `tools/build_index.py`.
   */
  data class Fixture(val text: String, val tokens: List<String>, val stems: List<String>)

  fun fixtureFor(text: String): Fixture {
    val tokens = tokenize(text)
    return Fixture(text = text, tokens = tokens, stems = tokens.map { stem(it) })
  }
}
