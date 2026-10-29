package com.example.zahra.knowledge.unit

import com.example.zahra.routing.ConfidenceGatePolicy
import com.example.zahra.routing.GateOutcome

/**
 * قرار بوابة الاسترجاع الهجين (المرحلة 3 · المسار C.5).
 *
 * المبدأ (D25): **لا عتبات استرجاع جديدة.** كل الأرقام تأتي من [ConfidenceGatePolicy]:
 * - أرضية القبول: [ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE] (legacy معلنة، غير معايَرة).
 * - هامش التقارب: [ConfidenceGatePolicy.KNOWLEDGE_CLOSE_CALL_MARGIN] (نفس قيمة
 *   `KnowledgeEngine.CONFLICT_SCORE_DELTA`).
 *
 * المخرج واحد من ثلاثة فقط — نفس [GateOutcome] الموحّد من المرحلة 1، حتى لا تتفرّع قرارات الصدق.
 */
data class UnitRetrievalDecision(
  val outcome: GateOutcome,
  /** سبب مقروء للآلة وللمستخدم (`low_score`، `no_match`، `close_call`…). */
  val reasonCode: String,
  val reason: String,
  val best: UnitMatch?,
  val alternatives: List<UnitMatch>,
  val clarifyOptions: List<String>,
  val diagnostics: Map<String, String> = emptyMap()
) {
  val answered: Boolean get() = outcome == GateOutcome.ANSWER && best != null
}

class UnitRetrievalGate(
  private val minScore: Double = ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE,
  private val closeCallMargin: Double = ConfidenceGatePolicy.KNOWLEDGE_CLOSE_CALL_MARGIN
) {

  /**
   * يقرّر مصير نتيجة استرجاع واحدة.
   *
   * @param query نص المستخدم الخام (يُستخدم للتأكد من وجود موضوع فعلي).
   * @param result نتيجة [HybridRetriever.search].
   */
  fun decide(query: String, result: HybridSearchResult): UnitRetrievalDecision {
    if (query.isBlank()) {
      return abstain("empty_query", "الاستعلام فارغ؛ لا يمكن الاسترجاع من قاعدة المعرفة المحلية.")
    }
    if (!UnitTokenizer.hasSubstantiveTokens(query)) {
      return abstain(
        "no_substantive_tokens",
        "السؤال لا يحتوي موضوعًا محددًا يمكن البحث عنه في المعرفة الموثقة."
      )
    }
    val best = result.best
      ?: return abstain("no_match", "لا توجد وحدة معرفة مطابقة في القاعدة المحلية؛ لا تخمين.")
    if (best.score < minScore) {
      return abstain(
        "below_threshold",
        "أعلى تطابق في المعرفة المحلية ${pct(best.score)} وهو أقل من أرضية القبول ${pct(minScore)} " +
          "(عتبة قديمة غير معايَرة — D4). الامتناع أصدق من إجابة ضعيفة السند."
      ).copy(alternatives = result.matches.take(3))
    }
    if (!best.hasLexicalSupport) {
      return abstain(
        "no_lexical_support",
        "التطابق الأعلى مبني على تشابه عام دون كلمة مشتركة موثقة؛ لا يُعتمد كسند."
      ).copy(alternatives = result.matches.take(3))
    }

    val second = result.matches.getOrNull(1)
    if (second != null &&
      (best.score - second.score) <= closeCallMargin &&
      second.unit.pack != best.unit.pack
    ) {
      val options = result.matches.take(3).map { it.unit.title }
      return UnitRetrievalDecision(
        outcome = GateOutcome.CLARIFY,
        reasonCode = "close_call",
        reason = "هناك أكثر من وحدة متقاربة من حزم مختلفة (فرق ${pct(best.score - second.score)}). " +
          "التوضيح أفضل من اختيار عشوائي.",
        best = best,
        alternatives = result.matches.take(3),
        clarifyOptions = options,
        diagnostics = diagnosticsOf(best, second)
      )
    }

    return UnitRetrievalDecision(
      outcome = GateOutcome.ANSWER,
      reasonCode = "answered",
      reason = "تطابق موثّق بدرجة ${pct(best.score)} (أرضية ${pct(minScore)}).",
      best = best,
      alternatives = result.matches.drop(1).take(2),
      clarifyOptions = emptyList(),
      diagnostics = diagnosticsOf(best, second)
    )
  }

  private fun abstain(code: String, reason: String): UnitRetrievalDecision = UnitRetrievalDecision(
    outcome = GateOutcome.ABSTAIN,
    reasonCode = code,
    reason = reason,
    best = null,
    alternatives = emptyList(),
    clarifyOptions = emptyList()
  )

  private fun diagnosticsOf(best: UnitMatch, second: UnitMatch?): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    map["floor"] = String.format(java.util.Locale.US, "%.3f", minScore)
    map["best"] = best.unit.id
    map["bestScore"] = String.format(java.util.Locale.US, "%.3f", best.score)
    map["breakdown"] = best.breakdown.explain()
    map["matchedTerms"] = best.matchedTerms.joinToString(",")
    if (best.fuzzyMatches.isNotEmpty()) map["fuzzy"] = best.fuzzyMatches.joinToString(",")
    if (second != null) {
      map["runnerUp"] = second.unit.id
      map["margin"] = String.format(java.util.Locale.US, "%.3f", best.score - second.score)
    }
    return map
  }

  private fun pct(value: Double): String = String.format(java.util.Locale.US, "%.0f%%", value * 100)
}
