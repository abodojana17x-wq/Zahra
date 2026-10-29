package com.example.eval

import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.File
import java.security.MessageDigest

/**
 * نماذج بيانات مجموعة القياس (المرحلة 0).
 *
 * المخطط موثّق في plans/00-measurement.md (القسم «المسار A») والملفات المولّدة
 * بواسطة scripts/gen_eval_cases.py ومُجمّدة بهاش في eval/MANIFEST.sha256.
 */
data class EvalExpect(
  val type: String = "",
  val value: JsonElement? = null,
  val tolerance: Double? = null,
)

data class EvalCase(
  val id: String = "",
  val domain: String = "",
  val question: String = "",
  val dialect: String = "",
  val expect: EvalExpect = EvalExpect(),
  val expected_intent: String? = null,
  val must_refuse: Boolean = false,
  val needs_source: Boolean = false,
  val network: String = "offline",
  val difficulty: String = "",
  val tags: List<String> = emptyList(),
  val context_turns: List<String> = emptyList(),
  val fixture: Map<String, Any>? = null,
)

object EvalDataset {
  private val gson = Gson()

  /** المسار الافتراضي: مجلد eval/ في جذر المستودع (يُمرَّر من Gradle كـ system property). */
  fun dir(): File {
    val fromProp = System.getProperty("zahra.eval.dir")
    if (!fromProp.isNullOrBlank()) return File(fromProp)
    // احتياطي: ابحث لأعلى عن eval/cases.jsonl (يعمل من app/ أو من الجذر)
    var current: File? = File("").absoluteFile
    while (current != null) {
      val candidate = File(current, "eval/cases.jsonl")
      if (candidate.exists()) return File(current, "eval")
      current = current.parentFile
    }
    return File("eval")
  }

  fun casesFile(): File = File(dir(), "cases.jsonl")

  fun heldOutFile(): File = File(dir(), "held-out.jsonl")

  fun load(file: File): List<EvalCase> {
    require(file.exists()) { "ملف مجموعة القياس غير موجود: ${file.absolutePath}" }
    return file.readLines(Charsets.UTF_8)
      .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
      .mapIndexed { index, line ->
        try {
          gson.fromJson(line, EvalCase::class.java)
            ?: error("سطر ${index + 1}: JSON فارغ")
        } catch (e: Exception) {
          throw IllegalStateException("سطر ${index + 1} غير صالح في ${file.name}: ${e.message}", e)
        }
      }
  }

  fun loadAll(): List<EvalCase> = load(casesFile())

  fun loadHeldOut(): List<EvalCase> = load(heldOutFile())

  fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(file.readBytes())
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
