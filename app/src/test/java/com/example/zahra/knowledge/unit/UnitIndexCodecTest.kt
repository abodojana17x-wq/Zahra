package com.example.zahra.knowledge.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * توصيف عقد `knowledge_index.json` ([UnitIndexCodec]): ما يُقرأ، وما يُرفض صراحةً.
 *
 * ⚠️ مكتوبة ولم تُشغَّل هنا. لا ادعاء نجاح قبل تشغيل فعلي.
 */
class UnitIndexCodecTest {

  private val minimal = """
    {
      "schemaVersion": 1,
      "builtAt": "2026-10-29T00:00:00Z",
      "builderVersion": "1.0.0",
      "contentHash": "abc123",
      "params": {"k1": 1.2, "b": 0.75, "delta": 1.0, "gramN": 3,
                 "weights": {"bm25": 0.4, "char": 0.3, "tfidf": 0.3}},
      "stats": {"units": 1, "avgDocLength": 5.0, "bm25ref": 2.5},
      "sourcePacks": [{"pack": "seed-x", "version": "1.0.0", "units": 1, "license": "CC-BY-4.0"}],
      "df": {"alpha": 1},
      "units": [{
        "id": "u-1", "pack": "seed-x", "title": "عنوان", "intent": "query_knowledge",
        "q": ["سؤال أول", "سؤال ثانٍ"], "a": ["إجابة كافية للاختبار."], "tags": ["وسم"],
        "source": {"name": "مصدر", "url": "https://example.org", "license": "CC-BY-4.0", "location": "ص 1"},
        "authored": true, "author": "مؤلف", "verifiedAt": "2026-10-29", "safety": "none",
        "weight": 1.0, "docLength": 3, "terms": {"alpha": 2.0}
      }]
    }
  """.trimIndent()

  @Test
  fun decode_readsContractFields() {
    val index = UnitIndexCodec.decode(minimal)
    assertEquals(1, index.schemaVersion)
    assertEquals("abc123", index.contentHash)
    assertEquals(2.5, index.stats.bm25Ref, 1e-9)
    assertEquals(1, index.documentFrequency["alpha"])
    assertEquals(1, index.sourcePacks.size)
    assertEquals("CC-BY-4.0", index.sourcePacks.first().license)

    val unit = index.units.single()
    assertEquals("u-1", unit.id)
    assertEquals(listOf("سؤال أول", "سؤال ثانٍ"), unit.questions)
    assertEquals("سؤال أول", unit.primaryQuestion)
    assertEquals(2.0, unit.terms["alpha"]!!, 1e-9)
    assertEquals(3, unit.docLength)
    assertTrue(unit.authored)
    assertEquals("ص 1", unit.source!!.location)
    assertTrue(unit.source!!.citation().contains("مصدر"))
  }

  @Test
  fun decode_acceptsLegacyLongFieldNames() {
    // الأسماء الطويلة كانت مستخدمة قبل توحيد العقد على q/a؛ القراءة تتقبّلها للتوافق.
    val legacy = minimal
      .replace("\"q\": [", "\"questions\": [")
      .replace("\"a\": [", "\"answers\": [")
    val unit = UnitIndexCodec.decode(legacy).units.single()
    assertEquals(listOf("سؤال أول", "سؤال ثانٍ"), unit.questions)
  }

  @Test
  fun decode_usesApplicationDefaultWeightsWhenAbsent() {
    val withoutWeights = minimal.replace(
      "\"weights\": {\"bm25\": 0.4, \"char\": 0.3, \"tfidf\": 0.3}",
      "\"gramN\": 3"
    )
    val index = UnitIndexCodec.decode(withoutWeights)
    assertEquals(HybridWeights().bm25, index.params.weights.bm25, 1e-9)
    assertEquals(HybridWeights().char, index.params.weights.char, 1e-9)
    assertEquals(HybridWeights().tfidf, index.params.weights.tfidf, 1e-9)
  }

  @Test
  fun decode_rejectsUnsupportedSchemaVersion() {
    val unsupported = minimal.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
    assertThrows(UnitIndexFormatException::class.java) { UnitIndexCodec.decode(unsupported) }
  }

  @Test
  fun decode_rejectsMissingUnitsArray() {
    val truncated = minimal.substringBefore("\"units\": [[") + "\"df\": {}}"
    assertThrows(UnitIndexFormatException::class.java) { UnitIndexCodec.decode(truncated) }
  }

  @Test
  fun decode_rejectsUnitWithoutId() {
    val noId = minimal.replace("\"id\": \"u-1\", ", "")
    assertThrows(UnitIndexFormatException::class.java) { UnitIndexCodec.decode(noId) }
  }

  @Test
  fun decode_rejectsInvalidJson() {
    assertThrows(UnitIndexFormatException::class.java) { UnitIndexCodec.decode("{ ليس JSON") }
  }

  @Test
  fun decode_treatsNullSourceAsAbsent() {
    val noSource = minimal.replace(
      "\"source\": {\"name\": \"مصدر\", \"url\": \"https://example.org\", \"license\": \"CC-BY-4.0\", \"location\": \"ص 1\"}",
      "\"source\": null"
    )
    val index = UnitIndexCodec.decode(noSource)
    assertNull(index.units.single().source)
    assertFalse(index.units.single().source?.isPresent ?: false)
  }
}
