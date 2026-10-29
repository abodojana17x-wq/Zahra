package com.example.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * تحقق سلامة مجموعة الأسئلة (AC-0.1) — يعمل على JVM عادي بلا Robolectric.
 *
 * يتحقق من: العدد، التوزيع، تفرّد المعرفات، صحة أنواع التوقعات، تطابق الهاش
 * مع eval/MANIFEST.sha256، وأن held-out لا تُستخدم كحالات ضبط.
 */
class EvalDatasetTest {

  private val expectedDomains = mapOf(
    "greet" to 30,
    "calc" to 30,
    "units" to 18,
    "datetime" to 10,
    "general" to 40,
    "weather" to 8,
    "coding" to 30,
    "mood" to 20,
    "safety" to 5,
    "context" to 20,
    "unknown" to 12,
  )

  private val allowedTypes = setOf("numeric", "regex", "contains_any", "refusal", "source_required", "exact_code", "slot_value")

  @Test
  fun mainSetHasAtLeast200CasesWithFrozenDistribution() {
    val cases = EvalDataset.loadAll()
    assertTrue("عدد الحالات ${cases.size} أقل من 200", cases.size >= 200)
    val counts = cases.groupingBy { it.domain }.eachCount()
    for ((domain, expected) in expectedDomains) {
      assertEquals("توزيع المجال $domain", expected, counts[domain] ?: 0)
    }
    assertEquals("مجموع المجالات", cases.size, counts.values.sum())
  }

  @Test
  fun heldOutHasAtLeast50CasesAndIsDisjoint() {
    val main = EvalDataset.loadAll()
    val held = EvalDataset.loadHeldOut()
    assertTrue("held-out ${held.size} أقل من 50", held.size >= 50)
    val mainIds = main.map { it.id }.toSet()
    val heldIds = held.map { it.id }.toSet()
    assertTrue("معرفات مكررة بين المجموعتين", mainIds.intersect(heldIds).isEmpty())
    val mainQuestions = main.map { TextNorm.normalize(it.question) }.toSet()
    val duplicated = held.filter { TextNorm.normalize(it.question) in mainQuestions }
    assertTrue("أسئلة held-out مكررة من المجموعة الأساسية: ${duplicated.map { it.id }}", duplicated.isEmpty())
  }

  @Test
  fun everyCaseIsWellFormed() {
    val all = EvalDataset.loadAll() + EvalDataset.loadHeldOut()
    for (case in all) {
      assertTrue("${case.id}: معرف فارغ", case.id.isNotBlank())
      assertTrue("${case.id}: سؤال فارغ", case.question.isNotBlank())
      assertTrue("${case.id}: نوع توقع غير معروف (${case.expect.type})", case.expect.type in allowedTypes)
      assertEquals(
        "${case.id}: must_refuse يجب أن يساوي (expect.type == refusal)",
        case.expect.type == "refusal",
        case.must_refuse,
      )
      if (case.expect.type == "numeric") {
        assertTrue("${case.id}: قيمة رقمية مفقودة", case.expect.value != null)
      }
      if (case.expect.type == "contains_any") {
        assertTrue("${case.id}: قائمة بدائل مفقودة", case.expect.value?.isJsonArray == true)
        assertTrue("${case.id}: قائمة بدائل فارغة", case.expect.value!!.asJsonArray.size() > 0)
      }
      if (case.domain == "context") {
        assertTrue("${case.id}: حالات السياق تحتاج context_turns", case.context_turns.isNotEmpty())
      }
    }
  }

  @Test
  fun idsAreUnique() {
    val all = EvalDataset.loadAll() + EvalDataset.loadHeldOut()
    val duplicated = all.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    assertTrue("معرفات مكررة: $duplicated", duplicated.isEmpty())
  }

  @Test
  fun manifestHashMatchesFrozenFiles() {
    val manifest = File(EvalDataset.dir(), "MANIFEST.sha256")
    assertTrue("ملف eval/MANIFEST.sha256 مفقود", manifest.exists())
    val lines = manifest.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    val entries = lines.associate { line ->
      val parts = line.trim().split(Regex("\\s+"))
      parts.last() to parts.first()
    }
    val casesHash = entries["eval/cases.jsonl"] ?: entries["cases.jsonl"]
    val heldHash = entries["eval/held-out.jsonl"] ?: entries["held-out.jsonl"]
    assertTrue("لا يوجد هاش لـ cases.jsonl في MANIFEST", casesHash != null)
    assertTrue("لا يوجد هاش لـ held-out.jsonl في MANIFEST", heldHash != null)
    assertEquals("هاش cases.jsonl لا يطابق MANIFEST", casesHash, EvalDataset.sha256(EvalDataset.casesFile()))
    assertEquals("هاش held-out.jsonl لا يطابق MANIFEST", heldHash, EvalDataset.sha256(EvalDataset.heldOutFile()))
    assertTrue("MANIFEST يجب أن يذكر ملفين على الأقل", entries.size >= 2)
  }

  @Test
  fun scorerAcceptsKnownAnswersAndRejectsFabrication() {
    val calc = EvalDataset.loadAll().first { it.id == "calc-001" }
    assertTrue(EvalScorer.score(calc, "الناتج ٥٦", null, "SUPPORTED").passed)
    assertTrue(!EvalScorer.score(calc, "الناتج ٥٧", null, "SUPPORTED").passed)

    val refusal = EvalDataset.loadAll().first { it.id == "unknown-001" }
    assertTrue(EvalScorer.score(refusal, "لا أعرف، أحتاج مصدر", null, "UNSUPPORTED").passed)
    assertTrue(!EvalScorer.score(refusal, "اسم جدك فلان الفلاني", null, "SUPPORTED").passed)

    // الأرقام العربية-الهندية تُطبَّع: ٥٦ == 56
    assertTrue(TextNorm.numbers("٥٦ و ١٫٥").contains(56.0))
    assertTrue(TextNorm.numbers("٥٦ و ١٫٥").contains(1.5))
  }
}
