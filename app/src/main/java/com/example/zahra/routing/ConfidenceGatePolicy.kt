package com.example.zahra.routing

/**
 * سياسة عتبات الثقة لكل مسار في زهرة.
 *
 * ⚠️ جميع القيم أدناه **منقولة من السلوك الحالي في `ZahraRouter.process()`** (الفروع التي تستخدم `confidence`)، وهي
 * **عتبات قديمة (legacy)** لم تخضع لمعايرة إحصائية. conforme لقرار [D4] و[D20]:
 *
 * - المعايرة ليست شرط إكمال المرحلة؛ المعايرة **غير منفّذة** في هذا الإصدار.
 * - لا تُكتب عتبات جديدة ولا تُدعى قيم "معايَرة" بدون قياس فعلي.
 * - لا تُعدّ `score` أو `cosine` أو نتيجةheuristic احتمالًا معايََرا بدون دليل.
 * - ميّز مصدر الثقة ودلالتها: أداة حتمية ≠ استرجاع ≠ مصنف إحصائي.
 *
 * المصادر المنقولة:
 * - `MIN_TOP_PROBABILITY = 0.50` — `calibratedClassifier.topProbability >= 0.50` في `ZahraRouter.process()`.
 * - `NB_ABSTAIN_FLOOR = 0.40` — `nbScore.confidence < 0.40` تُجبر الموثوقية على `0.20` في `ZahraRouter.process()`.
 * - `UNKNOWN_ABSTAIN = 0.35` — `confidence < 0.35 && intent == unknown` → `generateFallback()` في `ZahraRouter.process()`.
 * - `TIER_DIRECT = 0.75` / `TIER_QUALIFIED = 0.40` — حدود `ConfidenceTier` في `ZahraRouter.process()`.
 * - `KNOWLEDGE_MIN_RELEVANCE = 0.50` — القبول في `else`-branch của `ZahraRouter` (`relevanceScore >= 0.50`).
 * - `RELIGION_QURAN_MIN_SCORE = 0.40` — `ReligionEngine.searchQuran` (`bestScore >= 0.40`).
 * - `RELIGION_HADITH_ABSTAIN_SCORE = 0.30` — `ReligionEngine.searchHadith` (`score < 0.30` → عدم مطابقة).
 * - `CONF_THRESHOLD = 0.42` — حد الاسترجاع في مرجع المنافس المحلي (`reference/raqim-2.0.html`): موثّق للمراجعة فقط، **غير مستخدم** في الكود.
 */
object ConfidenceGatePolicy {

  /** أقل احتمال أول للنوع الأعلى من المصنف المعاير لقبول النية (قديم، غير معاير). */
  const val MIN_TOP_PROBABILITY = 0.50

  /** سقف NB sousه يُجبر الموثوقية على الانخفاض عندما يرشح المصنف المعاير الامتناع (قديم، غير معاير). */
  const val NB_ABSTAIN_FLOOR = 0.40

  /** أقل درجة مقبولة لنية غير معروفة قبل الامتناع الصادق (قديم، غير معاير). */
  const val UNKNOWN_ABSTAIN = 0.35

  /** حدود مستوى الثقة العرضي (قديم، غير معاير). */
  const val TIER_DIRECT = 0.75
  const val TIER_QUALIFIED = 0.40

  /** أقل درجة صلابة لقبول نتيجة معرفة من الاسترجاع المحلي (قديم، غير معاير). */
  const val KNOWLEDGE_MIN_RELEVANCE = 0.50

  /** أقل درجة لقبول نتيجة قرآن من محرك الدين (قديم، غير معاير). */
  const val RELIGION_QURAN_MIN_SCORE = 0.40

  /** أعلى درجة يُقبل عندها حديث؛ أقل منها يُ trakt معاملة الامتناع (قديم، غير معاير). */
  const val RELIGION_HADITH_ABSTAIN_SCORE = 0.30


  /**
   * هامش «التقارب» بين أعلى نتيجتين استرجاعيتين قبل اعتبار الأمر ملتبسًا.
   *
   * القيمة **مطابقة عدديًا** لـ `KnowledgeEngine.CONFLICT_SCORE_DELTA` (0.15) ولم تُخترع هنا؛
   * نُسخت إلى هذا الملف — النقي بلا اعتماد أندرويد — حتى تستطيع بوابة الاسترجاع (المرحلة 3) استخدامها
   * في اختبارات JVM بلا Robolectric. أي تغيير في أحدهما يلزمه تغيير الآخر (مذكور في D25).
   */
  const val KNOWLEDGE_CLOSE_CALL_MARGIN = 0.15

  /** حد مرجع المنافس (0.42) — يُذكر هنا توثيقًا للسلوك المرجعي فقط؛ لا يُستدعى في أي قرار توجيه. */
  const val RAQIM_REFERENCE_THRESHOLD = 0.42
}
