package com.example.zahra.knowledge.unit

import com.example.zahra.tools.JsonParseException
import com.example.zahra.tools.JsonValue
import com.example.zahra.tools.MiniJson

/**
 * مخطط «وحدة المعرفة» (المرحلة 3 · المسار A) — عقد مشترك مع المرحلة 5 (الحزم).
 *
 * كل وحدة = سؤال أساسي + تنويعات (`questions`) → إجابة مركّبة من بلوكات (`answers`)، مع مصدر أو `authored:true`.
 * الحقول هنا **وصفية فقط**؛ حساب الفهرس (df/idf/أطوال/درجة مرجعية) يتم زمن البناء عبر `tools/build_index.py`
 * ويُشحن في `app/src/main/assets/knowledge_index.json` (D24).
 */
data class UnitSource(
  val name: String,
  val url: String = "",
  val license: String = "",
  val location: String = ""
) {
  val isPresent: Boolean get() = name.isNotBlank() || url.isNotBlank() || location.isNotBlank()

  fun citation(): String {
    val where = location.ifBlank { url }
    return when {
      name.isNotBlank() && where.isNotBlank() -> "$name — $where"
      name.isNotBlank() -> name
      else -> where
    }
  }
}

/** حالات السلامة المعلنة للوحدة (لا يُستنتج منها حكم شرعي أو طبي). */
object UnitSafety {
  const val NONE = "none"
  const val MEDICAL_DISCLAIMER = "medical_disclaimer"
  const val RELIGIOUS_REFERENCE = "religious_reference"
  const val NO_FATWA = "no_fatwa"
}

/**
 * وحدة معرفة مشحونة من الفهرس.
 *
 * @property terms أوزان TF المرجّحة لكل رمز **محسوبة زمن البناء**:
 *   سؤال×3، عنوان×2، وسوم×2، إجابة×1 (D24).
 * @property docLength طول المستند المرجّح (نفس الأوزان) المستخدم في تطبيع BM25.
 */
data class KnowledgeUnit(
  val id: String,
  val pack: String,
  val title: String,
  val intent: String,
  val questions: List<String>,
  val answers: List<String>,
  val tags: List<String>,
  val source: UnitSource?,
  val authored: Boolean,
  val author: String,
  val verifiedAt: String,
  val safety: String,
  val weight: Double,
  val docLength: Int,
  val terms: Map<String, Double>
) {
  val primaryQuestion: String get() = questions.firstOrNull() ?: title

  val answerText: String get() = answers.joinToString("\n\n")

  /** نص كامل يُستخدم لإشارة TF-IDF (المحرك القديم يعمل على نص واحد لكل مستند). */
  fun tfIdfText(): String = buildString {
    append(title)
    for (q in questions) {
      append('\n').append(q)
    }
    if (tags.isNotEmpty()) {
      append('\n').append(tags.joinToString(" "))
    }
    for (a in answers) {
      append('\n').append(a)
    }
  }
}

/** معاملات BM25+ المثبَّتة في الفهرس (تُقرأ ولا تُعاد اختراعها في وقت التشغيل). */
data class IndexParams(
  val k1: Double = 1.2,
  val b: Double = 0.75,
  val delta: Double = 1.0,
  val gramSize: Int = 3,
  val weights: HybridWeights = HybridWeights()
)

data class IndexStats(
  val unitCount: Int,
  val avgDocLength: Double,
  /** درجة BM25+ الخام لمطابقة الوحدة لسؤالها الأساسي (وسيط على الفهرس) — مقياس التطبيع (D24). */
  val bm25Ref: Double
)

data class PackInfo(val pack: String, val version: String, val units: Int, val license: String)

/** الفهرس الكامل المشحون في assets. */
data class KnowledgeUnitIndex(
  val schemaVersion: Int,
  val builtAt: String,
  val builderVersion: String,
  val params: IndexParams,
  val stats: IndexStats,
  val documentFrequency: Map<String, Int>,
  val units: List<KnowledgeUnit>,
  val sourcePacks: List<PackInfo> = emptyList(),
  val contentHash: String = ""
) {
  companion object {
    const val SUPPORTED_SCHEMA_VERSION = 1
  }
}

class UnitIndexFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * قارئ فهرس الوحدات (JSON → نماذج) بلا أي اعتماد أندرويد، عبر [MiniJson] النقي من المرحلة 2.
 * يجعل منطق الاسترجاع قابلًا للاختبار الكامل على JVM بلا Robolectric.
 */
object UnitIndexCodec {

  fun decode(json: String): KnowledgeUnitIndex {
    val root = try {
      MiniJson.parse(json)
    } catch (e: JsonParseException) {
      throw UnitIndexFormatException("فهرس وحدات المعرفة ليس JSON صالحًا: ${e.message}", e)
    }
    if (root !is JsonValue.Obj) throw UnitIndexFormatException("جذر الفهرس يجب أن يكون كائنًا")

    val schemaVersion = root.numberOrNull("schemaVersion")?.toInt()
      ?: throw UnitIndexFormatException("schemaVersion مفقود")
    if (schemaVersion != KnowledgeUnitIndex.SUPPORTED_SCHEMA_VERSION) {
      throw UnitIndexFormatException(
        "إصدار مخطط الفهرس $schemaVersion غير مدعوم (المدعوم ${KnowledgeUnitIndex.SUPPORTED_SCHEMA_VERSION})"
      )
    }

    val paramsNode = root.field("params") as? JsonValue.Obj
    val weightsNode = paramsNode?.field("weights") as? JsonValue.Obj
    val params = IndexParams(
      k1 = paramsNode?.numberOrNull("k1") ?: 1.2,
      b = paramsNode?.numberOrNull("b") ?: 0.75,
      delta = paramsNode?.numberOrNull("delta") ?: 1.0,
      gramSize = paramsNode?.numberOrNull("gramN")?.toInt() ?: 3,
      weights = HybridWeights(
        bm25 = weightsNode?.numberOrNull("bm25") ?: HybridWeights().bm25,
        char = weightsNode?.numberOrNull("char") ?: HybridWeights().char,
        tfidf = weightsNode?.numberOrNull("tfidf") ?: HybridWeights().tfidf
      )
    )

    val statsNode = root.field("stats") as? JsonValue.Obj
    val unitsArray = root.arrayOrNull("units")
      ?: throw UnitIndexFormatException("حقل units مفقود")

    val units = unitsArray.mapIndexed { i, node ->
      val obj = node as? JsonValue.Obj
        ?: throw UnitIndexFormatException("الوحدة رقم $i ليست كائنًا")
      val id = obj.stringOrNull("id")?.takeIf { it.isNotBlank() }
        ?: throw UnitIndexFormatException("الوحدة رقم $i بلا معرّف")

      val sourceNode = obj.field("source") as? JsonValue.Obj
      val source = sourceNode?.let {
        UnitSource(
          name = it.stringOrNull("name") ?: "",
          url = it.stringOrNull("url") ?: "",
          license = it.stringOrNull("license") ?: "",
          location = it.stringOrNull("location") ?: ""
        )
      }

      val termsNode = obj.field("terms") as? JsonValue.Obj
      val terms = LinkedHashMap<String, Double>()
      termsNode?.fields?.forEach { (term, value) ->
        if (value is JsonValue.Num) terms[term] = value.value
      }

      KnowledgeUnit(
        id = id,
        pack = obj.stringOrNull("pack") ?: "unknown",
        title = obj.stringOrNull("title") ?: "",
        intent = obj.stringOrNull("intent") ?: "query_knowledge",
        questions = obj.stringList("q").ifEmpty { obj.stringList("questions") },
        answers = obj.stringList("a").ifEmpty { obj.stringList("answers") },
        tags = obj.stringList("tags"),
        source = source,
        authored = (obj.field("authored") as? JsonValue.Bool)?.value ?: false,
        author = obj.stringOrNull("author") ?: "",
        verifiedAt = obj.stringOrNull("verifiedAt") ?: "",
        safety = obj.stringOrNull("safety") ?: UnitSafety.NONE,
        weight = obj.numberOrNull("weight") ?: 1.0,
        docLength = obj.numberOrNull("docLength")?.toInt() ?: 1,
        terms = terms
      )
    }

    val df = LinkedHashMap<String, Int>()
    (root.field("df") as? JsonValue.Obj)?.fields?.forEach { (term, value) ->
      if (value is JsonValue.Num) df[term] = value.value.toInt()
    }

    val packs = root.arrayOrNull("sourcePacks")?.mapNotNull { node ->
      val obj = node as? JsonValue.Obj ?: return@mapNotNull null
      PackInfo(
        pack = obj.stringOrNull("pack") ?: "",
        version = obj.stringOrNull("version") ?: "",
        units = obj.numberOrNull("units")?.toInt() ?: 0,
        license = obj.stringOrNull("license") ?: ""
      )
    } ?: emptyList()

    return KnowledgeUnitIndex(
      schemaVersion = schemaVersion,
      builtAt = root.stringOrNull("builtAt") ?: "",
      builderVersion = root.stringOrNull("builderVersion") ?: "",
      params = params,
      stats = IndexStats(
        unitCount = statsNode?.numberOrNull("units")?.toInt() ?: units.size,
        avgDocLength = statsNode?.numberOrNull("avgDocLength") ?: 1.0,
        bm25Ref = statsNode?.numberOrNull("bm25ref") ?: 1.0
      ),
      documentFrequency = df,
      units = units,
      sourcePacks = packs,
      contentHash = root.stringOrNull("contentHash") ?: ""
    )
  }

  /** قائمة نصوص من مصفوفة JSON؛ يرفض أي عنصر غير نصي بدل تجاهله صامتًا. */
  private fun JsonValue.Obj.stringList(key: String): List<String> =
    arrayOrNull(key)?.map { item ->
      (item as? JsonValue.Str)?.value
        ?: throw UnitIndexFormatException("الحقل $key يحتوي عنصرًا غير نصي")
    } ?: emptyList()
}
