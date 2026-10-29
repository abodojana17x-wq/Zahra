package com.example.zahra.knowledge.unit

/**
 * أوزان الدمج المرجّح للإشارات الثلاث (BM25+ / بصمات الحروف / TF-IDF cosine).
 *
 * ⚠️ **ليست أوزانًا معايَرة إحصائيًا** (D4/D20). القيم الافتراضية هي نتيجة `tools/phase03/tune_weights.py`
 * المحفوظة في `evidence/phase-03/weights.json`:
 *  1. البحث الشبكي على `split=train` فقط في `eval/phase03/paraphrase-100.jsonl`،
 *  2. نقطة انطلاق مستمدة من أوزان المنافس المعلنة في `reference/raqim-2.0.html` (0.58 / 0.24 / 0.18)،
 *  3. عند تعادل المقاييس (شائع: الإشارات مترابطة) تُفضَّل التوليفة التي تُبقي كل إشارة فاعلة — وإلا صار
 *     «الدمج الهجين» زينة في التفسير دون أثر.
 *
 * تُقاس على `split=heldout` وتُنشر كما هي؛ لا يوجد رقم قبول مستهدف.
 */
data class HybridWeights(
  val bm25: Double = 0.40,
  val char: Double = 0.30,
  val tfidf: Double = 0.30
) {
  init {
    require(bm25 >= 0.0 && char >= 0.0 && tfidf >= 0.0) { "أوزان الدمج لا يمكن أن تكون سالبة" }
  }

  val sum: Double get() = bm25 + char + tfidf

  /** يعيد توزيع الأوزان على مجموع 1؛ يُستخدم قبل الدمج حتى تُقارن الدرجات بثبات. */
  fun normalized(): HybridWeights {
    if (sum <= 0.0) return HybridWeights(1.0, 0.0, 0.0)
    if (kotlin.math.abs(sum - 1.0) < 1e-9) return this
    return HybridWeights(bm25 / sum, char / sum, tfidf / sum)
  }

  fun describe(): String =
    "bm25=${fmt(bm25)};char=${fmt(char)};tfidf=${fmt(tfidf)}"

  private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
}
