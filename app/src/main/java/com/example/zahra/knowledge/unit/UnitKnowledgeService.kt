package com.example.zahra.knowledge.unit

import com.example.zahra.routing.GateOutcome

/**
 * خدمة الإجابة من فهرس وحدات المعرفة (المرحلة 3).
 *
 * تربط: استرجاع هجين → بوابة موحّدة → نص إجابة **بسند ظاهر**. لا توليد ولا اختلاق:
 * إن لم تجتز البوابة، يعود [UnitAnswer.text] مساويًا `null` ومعه سبب امتناع جاهز للعرض.
 */
data class UnitAnswer(
  val decision: UnitRetrievalDecision,
  val text: String?,
  val sourceLabel: String?,
  val confidence: Double,
  val warnings: List<String> = emptyList()
) {
  val answered: Boolean get() = decision.outcome == GateOutcome.ANSWER && text != null
}

class UnitKnowledgeService(
  private val index: KnowledgeUnitIndex,
  weights: HybridWeights = index.params.weights.normalized(),
  tfIdfSignal: TfIdfSignal = SemanticEngineSignal()
) {

  val retriever: HybridRetriever = HybridRetriever(index, weights, tfIdfSignal)
  val gate: UnitRetrievalGate = UnitRetrievalGate()

  fun answer(query: String, maxResults: Int = 5): UnitAnswer {
    val result = retriever.search(query, maxResults)
    val decision = gate.decide(query, result)
    val best = decision.best
    if (decision.outcome != GateOutcome.ANSWER || best == null) {
      return UnitAnswer(decision, null, null, best?.score ?: 0.0, result.warnings)
    }
    val warnings = result.warnings.toMutableList()
    if (best.fuzzyMatches.isNotEmpty()) {
      warnings.add("تمت مطابقة تقريبية: ${best.fuzzyMatches.joinToString(", ")}")
    }
    return UnitAnswer(
      decision = decision,
      text = compose(best),
      sourceLabel = sourceLabel(best.unit),
      confidence = best.score,
      warnings = warnings
    )
  }

  /** تركيب نص الإجابة من بلوكات الوحدة + سطر السند + تنبيهات السلامة. */
  fun compose(match: UnitMatch): String {
    val unit = match.unit
    val sb = StringBuilder()
    sb.append(unit.answerText.trim())
    val citation = unit.source?.citation().orEmpty()
    if (citation.isNotBlank()) {
      sb.append("\n\n[المصدر: ").append(citation)
      val license = unit.source?.license.orEmpty()
      if (license.isNotBlank()) sb.append(" · الرخصة: ").append(license)
      sb.append(']')
    } else if (unit.authored) {
      sb.append("\n\n[وحدة مُؤلّفة داخليًا")
      if (unit.author.isNotBlank()) sb.append(" — ").append(unit.author)
      sb.append(']')
    }
    when (unit.safety) {
      UnitSafety.MEDICAL_DISCLAIMER ->
        sb.append("\n\n⚠️ معلومات عامة للتوعية فقط وليست تشخيصًا؛ راجع طبيبًا مختصًا.")
      UnitSafety.NO_FATWA ->
        sb.append("\n\n⚠️ هذه مسألة فقهية تحتاج فتوى من أهل العلم المختصين؛ زهرة تنقل المرجع ولا تفتي.")
      UnitSafety.RELIGIOUS_REFERENCE ->
        sb.append("\n\n📖 المرجع مذكور أعلاه؛ يُنصح بمراجعة المصدر الأصلي.")
    }
    return sb.toString()
  }

  private fun sourceLabel(unit: KnowledgeUnit): String {
    val citation = unit.source?.citation().orEmpty()
    return when {
      citation.isNotBlank() -> citation
      unit.authored && unit.author.isNotBlank() -> "وحدة مُؤلّفة — ${unit.author}"
      unit.authored -> "وحدة مُؤلّفة داخليًا"
      else -> "المعرفة المحلية"
    }
  }
}
