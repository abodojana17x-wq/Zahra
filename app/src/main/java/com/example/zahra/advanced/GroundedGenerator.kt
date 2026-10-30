package com.example.zahra.advanced

import com.example.zahra.dialogue.DialogueResponseValidator
import com.example.zahra.dialogue.MemoryClaimGuard
import com.example.zahra.knowledge.unit.HybridRetriever
import com.example.zahra.knowledge.unit.KnowledgeUnit
import com.example.zahra.knowledge.unit.KnowledgeUnitIndex
import com.example.zahra.knowledge.unit.UnitMatch
import com.example.zahra.knowledge.unit.UnitSafety

/**
 * المولّد المُؤسَّس (Grounded Generator) — المرحلة 7 · المسار C.
 *
 * المبدأ (C.1–C.5): «لا توليد بلا سند». النموذج لا يُستدعى إلا لمهام توليدية صرفة
 * (تلخيص/إعادة صياغة/كتابة/شرح)، وبعد استرجاع أعلى 3 وحدات من فهرس المرحلة 3 وحقنها في الـ prompt
 * مع مصدرها ورخصتها. كل رد يمرّ بفاحص بعد التوليد: [DialogueResponseValidator] + فحص تناقض +
 * كشف «ذاكرة وهمية» ([MemoryClaimGuard]) + منع الفتوى/الوصفات الطبية + حذف/رفض الأرقام غير المسندة.
 * أي فشل/بطء/هلوسة ⇒ العودة الفورية للنواة الحتمية (0–6) بلا ادعاء.
 *
 * ⚠️ لا تعتمد على أندرويد (قابل للاختبار على JVM). تستهلك [HybridRetriever] والبوابات القائمة
 * دون تعديلها؛ النواة تبقى سليمة ومُقاسة بدون هذا الوضع.
 */

/** مصدر تأسيسي مُستخرج من وحدة معرفة مسترجعة. */
data class GroundingSource(
  val id: String,
  val title: String,
  val sourceUrl: String,
  val license: String
)

/** نتيجة توليد مؤسَّسة ناجحة. */
data class GroundedGenerationResult(
  val text: String,
  val usedModel: Boolean,
  val sources: List<GroundingSource>,
  val confidence: Double,
  val transparencyLabel: String,
  val diagnostics: Map<String, String>
)

/** مخرج المولّد: إما رد مؤسَّس، أو «استخدم النواة» (مع سبب للتدهور الرشيق). */
sealed interface GroundedOutcome {
  data class UseCore(val reason: String, val code: String) : GroundedOutcome
  data class Answered(val result: GroundedGenerationResult) : GroundedOutcome
}

class GroundedGenerator(
  private val index: KnowledgeUnitIndex,
  private val runtime: SlmRuntime,
  private val settings: AdvancedModeSettings,
  private val device: DeviceProfile,
  private val responseValidator: DialogueResponseValidator = DialogueResponseValidator,
  private val topK: Int = 3,
  private val maxTokens: Int = 256,
  private val budgetMs: Long = 8000
) {
  private val retriever = HybridRetriever(index)

  /**
   * نقطة الدخول الرئيسية.
   * تصنّف الطلب، تستعلم بوابة الوضع المتقدّم، ثم إما تولّد مؤسَّسًا أو تعيد التوجيه للنواة.
   */
  fun generate(userRequest: String): GroundedOutcome {
    val kind = classifyRequest(userRequest)
    val decision = AdvancedModeGate.decide(settings, device, runtime, kind)
    if (decision is AdvancedDecision.UseCore) {
      return GroundedOutcome.UseCore(decision.reason, decision.code)
    }
    return runGrounded(userRequest, kind)
  }

  private fun runGrounded(request: String, kind: RequestKind): GroundedOutcome {
    val result = retriever.search(request, topK)
    val matches = result.matches.take(topK)
    val sources = matches.mapNotNull { m -> toSource(m.unit) }

    val prompt = buildPrompt(request, matches)
    val gen = runtime.generate(prompt, GenerationConstraints(), maxTokens, budgetMs)

    // المسار B.4 / C.5: أي عطل/مهلة/إلغاء/نص فارغ ⇒ تدهور رشيق للنواة.
    if (!gen.available || gen.cancelled || gen.timedOut || gen.text.isBlank()) {
      return GroundedOutcome.UseCore(
        "النموذج غير متاح/انتهت مهلته/أُلغي ⇒ إعادة للنواة الحتمية",
        "runtime_fallback"
      )
    }

    // فاحص ما بعد التوليد (C.4/C.5).
    val postProblem = postCheck(gen.text, request, matches, kind)
    if (postProblem != null) {
      return GroundedOutcome.UseCore(postProblem, "post_check")
    }

    val label = if (sources.isNotEmpty()) {
      "مولّد محليًا • مدعوم بالمصادر: " + sources.joinToString("، ") { it.title }
    } else {
      "مولّد محليًا • بلا مصادر خارجية (لا ادعاءات موثّقة)"
    }
    // الثقة مبنية على درجة الاسترجاع إن وُجدت مصدر، وإلا قيمة متواضعة معلَنة كغير معايَرة.
    val confidence = if (sources.isNotEmpty()) (result.best?.score ?: 0.6) else 0.5

    return GroundedOutcome.Answered(
      GroundedGenerationResult(
        text = gen.text,
        usedModel = true,
        sources = sources,
        confidence = confidence,
        transparencyLabel = label,
        diagnostics = mapOf(
          "tokens" to gen.tokenCount.toString(),
          "elapsedMs" to gen.elapsedMs.toString(),
          "retrieved" to sources.size.toString(),
          "kind" to kind.name
        )
      )
    )
  }

  private fun toSource(unit: KnowledgeUnit): GroundingSource? {
    val s = unit.source
    return GroundingSource(
      id = unit.id,
      title = unit.title,
      sourceUrl = s?.url ?: "",
      license = s?.license ?: ""
    )
  }

  /**
   * فاحص ما بعد التوليد. يُعيد نص سبب (غير null) يستوجب العودة للنواة، أو null إذا سلم الرد.
   */
  private fun postCheck(
    text: String,
    request: String,
    matches: List<UnitMatch>,
    kind: RequestKind
  ): String? {
    // 1) ذاكرة وهمية (المرحلة 4): ادعاء إشارة لسياق سابق غير موجود.
    if (MemoryClaimGuard.containsUnverifiableClaim(text)) return "ادعاء ذاكرة بلا مرجع قابل للتحقق"

    // 2) فحوص عامة لرد الحوار (تناقض/وعد طبي/ادعاء ديني/ضمان غير مسند).
    val problem = responseValidator.problem(text, hasGroundedSource = matches.isNotEmpty())
    if (problem != null && problem in HARD_REJECT_PROBLEMS) return problem

    // 3) أرقام غير مسندة: أي رقم في الرد لا يوجد في الطلب ولا في المصادر المسترجعة = هلوسة رقمية.
    val sourceTexts = matches.map { it.unit.answerText }
    val unexplained = responseValidator.unexplainedNumbers(text, request, sourceTexts)
    if (unexplained.isNotEmpty()) {
      // حتى التوليد الصرف: رقم غير مسند يكسر وعد الصدق ⇒ نعيد للنواة.
      return "رقم غير مسند: ${unexplained.joinToString()}"
    }

    // 4) سلامة: لا فتوى/وصفة طبية غير مسندة (المرحلة 4). إن لم يأتِ الرد من وحدة ذات سلامة صريحة،
    //    وأنتج النموذج ادعاءً دينيًا/طبيًا قاطعًا ⇒ رفض للنواة الآمنة.
    val groundedWithSafety = matches.any {
      it.unit.safety == UnitSafety.NO_FATWA || it.unit.safety == UnitSafety.MEDICAL_DISCLAIMER
    }
    if (!groundedWithSafety && hasUnsourcedReligiousOrMedicalClaim(text)) {
      return "ادعاء ديني/طبي بلا سند"
    }

    return null
  }

  /** أنماط ادعاء ديني/طبي قاطع غير مسند — تستوجب الرفض للنواة الآمنة. */
  private fun hasUnsourcedReligiousOrMedicalClaim(text: String): Boolean {
    val t = text.lowercase()
    return UNSOURCED_RISK.any { t.contains(it) }
  }

  /**
   * يبني الـ prompt المُؤسَّس: تعليمة صارمة بعدم اختلاق حقائق + حقن المصادر مع روابطها ورخصها.
   */
  private fun buildPrompt(request: String, matches: List<UnitMatch>): String {
    val sb = StringBuilder()
    sb.appendLine("أنت مساعد زهرة المحلي على الجهاز. ممنوع تمامًا اختلاق حقائق أو أرقام أو فتاوى أو وصفات طبية.")
    sb.appendLine("في المهام التوليدية (تلخيص/إعادة صياغة/كتابة/شرح) قدّم إجابة موجزة بالعربية أو العامية المصرية حسب سياق المستخدم.")
    if (matches.isNotEmpty()) {
      sb.appendLine("استخدم فقط المعلومات التالية كمرجع عند الحاجة، واذكر أنها من المصادر المذكورة:")
      matches.forEachIndexed { i, m ->
        val src = m.unit.source
        val cite = if (src != null && (src.url.isNotBlank() || src.name.isNotBlank())) {
          " [المصدر: ${src.citation()} — الرخصة: ${src.license}]"
        } else ""
        sb.appendLine("(${i + 1}) ${m.unit.title}: ${m.unit.answerText}$cite")
      }
    } else {
      sb.appendLine("لا توجد مصادر محلية لهذا الطلب؛ لا تخترع معلومات موثّقة، واكتفِ بالمهمة التوليدية المطلوبة فقط.")
    }
    sb.appendLine("الطلب: $request")
    return sb.toString()
  }

  /**
   * مُصنِّف نوع الطلب (محافِظ). يعتمد على كلمات مفتاحية؛ هو مجرد توجيه أوّلي،
   * والمسارات النهائية تُحسم بالنواة. التصنيف المتحفّظ يفضّل تمرير الطلبات الحسّاسة/الحتمية للنواة.
   */
  private fun classifyRequest(text: String): RequestKind {
    val t = text.lowercase()
    if (containsAny(t, DETERMINISTIC_HINTS)) return RequestKind.DETERMINISTIC
    if (containsAny(t, SENSITIVE_HINTS)) return RequestKind.SENSITIVE
    if (containsAny(t, GENERATIVE_HINTS)) return RequestKind.GENERATIVE
    return RequestKind.FACTUAL
  }

  private fun containsAny(text: String, hints: List<String>): Boolean =
    hints.any { text.contains(it, ignoreCase = true) }

  companion object {
    private val HARD_REJECT_PROBLEMS = setOf(
      "تناقض صريح",
      "ذاكرة بلا مرجع قابل للتحقق",
      "وعد طبي غير موثق",
      "ادعاء ديني بلا مصدر",
      "ضمان غير مسند",
      "رقم غير مسند في سياق ادعاء",
      "رد فارغ"
    )

    private val DETERMINISTIC_HINTS = listOf(
      "احسب", "حساب", "كم ", "حوّل", "تحويل", "موعد", "تذكير", "أذكّر", "تاريخ اليوم",
      "التاريخ", "مواقيت الصلاة", "توقعات الطقس", "الطقس", "كم باقي", "ضرب", "اقسم"
    )

    private val SENSITIVE_HINTS = listOf(
      "فتوى", "حكم شرعي", "حكم ديني", "دواء", "علاج", "جرعة", "مرض", "تشخيص",
      "صلّي على", "حكم الله", "رأي ديني"
    )

    private val GENERATIVE_HINTS = listOf(
      "لخّص", "لخص", "اختصر", "أعد صياغة", "صيغ", "اكتب", "ألف", "قصيدة", "قصة",
      "رواية", "اشرح الكود", "اشرح هذا الكود", "ترجم", "نموذج", "بريد", "رسالة",
      "نصيحة أسلوب", "مقدمة", "خاتمة"
    )

    private val UNSOURCED_RISK = listOf(
      "فتوى", "هذا حرام", "هذا حلال", "يجب أن تأخذ", "جرعة من", "يعالج مرض",
      "دواء مضمون", "علاج مؤكد", "حكم الله أن"
    )
  }
}
