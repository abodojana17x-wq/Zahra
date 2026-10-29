package com.example.zahra.knowledge.unit

/**
 * مُصنِّعات وحدات/فهارس اصطناعية لاختبارات المرحلة 3 (ليست اختبارات بذاتها).
 *
 * كل الأرقام هنا معلنة داخل الاختبار حتى تُحسب الدرجات يدويًا ويُقارن الناتج بها.
 */
internal fun testUnit(
  id: String,
  terms: Map<String, Double>,
  docLength: Int,
  questions: List<String> = listOf("سؤال $id"),
  title: String = "عنوان $id",
  tags: List<String> = emptyList(),
  pack: String = "packA",
  weight: Double = 1.0,
  safety: String = UnitSafety.NONE,
  source: UnitSource? = null,
  authored: Boolean = true
): KnowledgeUnit = KnowledgeUnit(
  id = id,
  pack = pack,
  title = title,
  intent = "query_knowledge",
  questions = questions,
  answers = listOf("إجابة الوحدة $id — نص كافٍ للاختبار."),
  tags = tags,
  source = source,
  authored = authored,
  author = "اختبار",
  verifiedAt = "2026-10-29",
  safety = safety,
  weight = weight,
  docLength = docLength,
  terms = terms
)

internal fun testIndex(
  units: List<KnowledgeUnit>,
  avgDocLength: Double = units.map { it.docLength }.average().takeIf { units.isNotEmpty() } ?: 1.0,
  bm25Ref: Double = 1.0,
  df: Map<String, Int> = buildDf(units),
  weights: HybridWeights = HybridWeights()
): KnowledgeUnitIndex = KnowledgeUnitIndex(
  schemaVersion = KnowledgeUnitIndex.SUPPORTED_SCHEMA_VERSION,
  builtAt = "2026-10-29",
  builderVersion = "test",
  params = IndexParams(weights = weights),
  stats = IndexStats(units.size, avgDocLength, bm25Ref),
  documentFrequency = df,
  units = units
)

internal fun buildDf(units: List<KnowledgeUnit>): Map<String, Int> {
  val df = LinkedHashMap<String, Int>()
  for (unit in units) {
    for (term in unit.terms.keys) df[term] = (df[term] ?: 0) + 1
  }
  return df
}

/**
 * الفهرس المرجعي لحساب BM25+ يدويًا (نفس أرقام `syntheticBm25Checks` في `tools/offline-typecheck/phase03`):
 * N=3، avgDocLength=53/3، df(alpha)=2، df(beta)=1، bm25ref=1.0.
 */
internal fun bm25ReferenceIndex(weights: HybridWeights = HybridWeights(1.0, 0.0, 0.0)): KnowledgeUnitIndex {
  val units = listOf(
    testUnit("u1", mapOf("alpha" to 3.0), docLength = 3, questions = listOf("alpha")),
    testUnit("u2", mapOf("alpha" to 1.0), docLength = 30, questions = listOf("alpha")),
    testUnit("u3", mapOf("beta" to 2.0), docLength = 20, questions = listOf("beta"))
  )
  return testIndex(units, bm25Ref = 1.0, weights = weights)
}
