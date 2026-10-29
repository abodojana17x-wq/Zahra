package com.example.zahra.routing

import com.example.zahra.core.DetectedLang
import com.example.zahra.core.LangResult
import com.example.zahra.understanding.AmbiguityCheckResult
import com.example.zahra.understanding.AmbiguityAndClarificationDetector
import java.util.UUID

/**
 * نتيجة تنفيذ أداة حتمية يُغذي بها الموجه قبل اتخاذ القرار.
 */
enum class ToolVerdict {
  /** الأداة نفّدت العملية بنجاح والمدخلات كاملة. */
  OK,
  /** الأداة تحتاج معلمة ناقصة (مثل وحدة التحويل المستهدفة أو وقت التذكير). */
  AMBIGUOUS,
  /** الأداة رفضت التنفيذ (تحويل غير مدعوم، عملية غير صالحة). */
  FAILED,
  /** الأداة غير متاحة لهذا الطلب. */
  UNSUPPORTED
}

/**
 * حالة استرجاع المعرفة بحسب محرك المعرفة المحلي (`KnowledgeEngine`).
 */
enum class KnowledgeState {
  /** مطابقة مؤكّدة من مصدر موثق. */
  VERIFIED,
  /** مرشح قابل للتعقب من المعرفة المحلية. */
  CANDIDATE,
  /** أدلة متعارضة حول المسألة. */
  CONFLICT,
  /** 증거 غير كافية / لا مطابقة. */
  INSUFFICIENT,
  /** خطأ في محرك الاسترجاع. */
  ERROR
}

/**
 * الأدلة التي يبني عليها [Router] قرار التوجيه.
 *
 * لا يحتوي هذا الكلاس على أي منطق قرار؛ هو فقط حاوية 증거ات تُمرّر إلى [Router.route].
 */
data class RoutingEvidence(
  /** المدخلة الخام من المستخدم. */
  val raw: String,
  /** النص بعد التطبيع (`ZahraNormalizer.normalize`). */
  val normalized: String,
  /** نتيجة كشف اللغة (تحتوي على `language: DetectedLang` و `confidence`). */
  val lang: LangResult,
  /** وسم النية من المطابقة الحالية (regex / NB / اللهجة). */
  val intentLabel: String,
  /** درجة الثقة المصاحبة للنية حسب المسار الحالي (ليست معايَرة). */
  val classifierConfidence: Double,
  /** درجات جميع النيات من المصنف (مثل `rankedProbabilities` من `CalibratedClassifier`). */
  val scores: Map<String, Double>,
  /** نتيجة الأداة الحتمية إذا كان الطلب أداة (حساب / وحدات / وقت …)؛ محايد إذا لم يُنفذ الأداة بعد. */
  val toolVerdict: ToolVerdict? = null,
  /** حالة استرجاع المعرفة إذا كان الطلب معرفيًا. */
  val knowledgeState: KnowledgeState? = null,
  /** صلة أفضل نتيجة معرفة (عند وجودها). */
  val knowledgeRelevance: Double? = null,
  /** هل الطلب محظور دينيًا/أمانيًا حسب `ReligionEngine.checkImpermissibleRequest`. */
  val isImpermissible: Boolean = false,
  /** نتيجة كاشف الغموض الحتمي إذا تم استدعاؤه. */
  val ambiguity: AmbiguityCheckResult? = null,
  /** هل الطلب من أوامر النظام (مهمة / ملاحظة / تذكير / تصحيح حقيقة / شخصية …). */
  val isSystemCommand: Boolean = false
)

/**
 * موجّه زهرة: يفصّل قرار التوجيه عن التنفيذ.
 *
 * واجهة [Router] لا تبني ردًا للمستخدم؛ تُعيد [RoutingDecision] فقط، כדי أن يظل منطق القرار
 * قابلًا للاختبار على JVM خفيفة، وتنفيذه يقتصر على [com.example.zahra.engines.ZahraRouter.process].
 *
 * أولوية التوجيه الموثقة (من الأعلى إلى الأقل):
 * 1. أمان/رفض [Route.REFUSE]
 * 2. أوامر النظام [Route.SYSTEM] (مهام، ملاحظات، جدولة، تصحيح، شخصية، تراجع)
 * 3. أدوات حتمية [Route.CALC] / [Route.UNIT] / [Route.TIME] (حساب، تحويل وحدات، وقت)
 * 4. حقائق بمصدر [Route.FACT] (قاعدة المعرفة المحلية)
 * 5. برمجة [Route.CODE] (توليد محلي: صور، بطاقات…)
 * 6. دين [Route.RELIGION] (قرآن، حديث، فقه، مواقيت)
 * 7. مشاعر/حوار [Route.SOCIAL] / [Route.EMOTION] (تحيات، وداع، شكر، مساعدة، حكمة)
 * 8. امتناع [Route.UNKNOWN] + [GateOutcome.ABSTAIN]
 */
interface Router {

  /**
   * تبني قرار التوجيه من [evidence] دون تنفيذ أي إجراء جانبي.
   *
   * @return [RoutingDecision] piena بكلّ أبعاد القرار بما في ذلك [traceId] للتتبع.
   */
  fun route(evidence: RoutingEvidence): RoutingDecision
}

/**
 * التنفيذ الافتراضي والموحد للموجّه.
 *
 * جميع العتبات مستمدة من [ConfidenceGatePolicy] (عتبات قديمة منقولة من `ZahraRouter.process()`؛
 * المعايرة غير منفّذة — راجع توثيقها). لا يُخترع أي رقم هنا.
 */
class DefaultRouter(
  private val policy: ConfidenceGatePolicy = ConfidenceGatePolicy,
  private val ambiguityDetector: AmbiguityAndClarificationDetector =
    AmbiguityAndClarificationDetector
) : Router {

  private val SYSTEM_INTENTS = setOf(
    Intent.CREATE_TASK, Intent.LIST_TASKS, Intent.COMPLETE_TASK,
    Intent.REMEMBER_NOTE, Intent.LIST_NOTES, Intent.DELETE_NOTE,
    Intent.CREATE_SCHEDULE, Intent.LIST_SCHEDULES, Intent.CANCEL_SCHEDULE,
    Intent.QUERY_MEMORY, Intent.FORGET_FACT, Intent.REMEMBER_FACT,
    Intent.UPDATE_FACT, Intent.UNDO_ACTION, Intent.CHANGE_PERSONALITY,
    Intent.COMPOUND_PLAN
  )

  private val DETERMINISTIC_TOOL_INTENTS = mapOf(
    Intent.CALCULATE to Route.CALC,
    Intent.CONVERT_UNIT to Route.UNIT,
    Intent.TRANSLATE to Route.CALC,   // أداة تحويل نصي حتمية (محرك الترجمة المحلي)
    Intent.SUMMARIZE to Route.CALC,   // أداة تلخيص حتمية (TextRank)
    Intent.GENERATE_IMAGE to Route.CODE // توليد محلي إجرائي
  )

  private val RELIGION_INTENTS = setOf(
    Intent.ASK_RELIGION, Intent.ASK_PRAYER_TIME, Intent.ASK_QURAN, Intent.ASK_HADITH,
    Intent.ASK_ISLAMIC, Intent.ASK_FIQH, Intent.ASK_SEERAH, Intent.ASK_AQEEDAH, Intent.ASK_TAZKIYAH
  )

  private val SOCIAL_INTENTS = setOf(
    Intent.GREET, Intent.FAREWELL, Intent.HELP, Intent.THANK,
    Intent.ASK_WISDOM, Intent.MOOD_JOURNAL, Intent.SEMANTIC_SEARCH,
    Intent.VOICE_INPUT, Intent.SPEAK_RESPONSE
  )

  override fun route(evidence: RoutingEvidence): RoutingDecision {
    val traceId = UUID.randomUUID().toString()
    val intent = Intent(evidence.intentLabel)
    val scores = evidence.scores

    // 1) أمان/رفض أولوية مطلقة.
    if (evidence.isImpermissible) {
      return RoutingDecision(
        raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
        intent = intent, scores = scores, confidence = evidence.classifierConfidence,
        route = Route.REFUSE, gate = GateOutcome.ANSWER, clarifyOptions = emptyList(), traceId = traceId
      )
    }

    // 2) أوامر النظام.
    if (evidence.isSystemCommand || SYSTEM_INTENTS.contains(intent)) {
      val gate = gateForSystem(evidence)
      val options = clarifyOptionsFrom(evidence)
      return RoutingDecision(
        raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
        intent = intent, scores = scores, confidence = evidence.classifierConfidence,
        route = Route.SYSTEM, gate = gate, clarifyOptions = options, traceId = traceId
      )
    }

    // 3) أدوات حتمية.
    val toolRoute = DETERMINISTIC_TOOL_INTENTS[intent]
    if (toolRoute != null) {
      return toolGate(evidence, toolRoute, traceId, scores, intent)
    }

    // 4) حقائق بمصدر (أknowledge أولًا لأنها متلقاة بأي نية عند عدم المطابقة).
    val knowledgeGate = gateForKnowledge(evidence)
    if (knowledgeGate != null) {
      return RoutingDecision(
        raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
        intent = intent, scores = scores, confidence = evidence.classifierConfidence,
        route = Route.FACT, gate = knowledgeGate, clarifyOptions = emptyList(), traceId = traceId
      )
    }

    // 5) برمجة (ما بعد الأدوات الحتمية).
    if (intent == Intent.CODE || intent == Intent.ANALYZE_IMAGE || intent == Intent.EXTRACT_TEXT ||
        intent == Intent.DETECT_FACES) {
      return verdictToGate(evidence, Route.CODE, traceId, scores)
    }

    // 6) دين.
    if (RELIGION_INTENTS.contains(intent)) {
      return religionGate(evidence, traceId, scores)
    }

    // 7) مشاعر / حوار.
    if (SOCIAL_INTENTS.contains(intent)) {
      return RoutingDecision(
        raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
        intent = intent, scores = scores, confidence = coerceConfidence(evidence.classifierConfidence),
        route = Route.SOCIAL, gate = GateOutcome.ANSWER, clarifyOptions = emptyList(), traceId = traceId
      )
    }

    // 8) امتناع / توضيح للنية غير المعروفة أو منخفضة الثقة.
    return unknownGate(evidence, traceId, scores)
  }

  /** إذا وجد كاشف الغموض غموضًا في أمر نظام → CLARIFY بخياراته الفعلية، else ANSWER. */
  private fun gateForSystem(evidence: RoutingEvidence): GateOutcome {
    return if (evidence.ambiguity is AmbiguityCheckResult.Ambiguous) GateOutcome.CLARIFY
    else GateOutcome.ANSWER
  }

  /** بوابة الأداة الحتمية حسب حكم التنفيذ. */
  private fun toolGate(
    evidence: RoutingEvidence,
    route: Route,
    traceId: String,
    scores: Map<String, Double>,
    intent: Intent
  ): RoutingDecision {
    val (gate, options) = when (evidence.toolVerdict) {
      null -> GateOutcome.ANSWER to emptyList() // لم تُنفذ الأداة بعد؛ قرار التصنيف.current speaker يسري
      ToolVerdict.OK -> GateOutcome.ANSWER to emptyList()
      ToolVerdict.AMBIGUOUS -> GateOutcome.CLARIFY to clarifyOptionsFrom(evidence)
      ToolVerdict.FAILED, ToolVerdict.UNSUPPORTED -> GateOutcome.ABSTAIN to emptyList()
    }
    return RoutingDecision(
      raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
      intent = intent, scores = scores,
      confidence = toolVerdictConfidence(evidence.toolVerdict, evidence.classifierConfidence),
      route = route, gate = gate, clarifyOptions = options, traceId = traceId
    )
  }

  /** بوابة المعرفة: فقط المطابقة المؤكّدة/القابلة للتعقب تُجيب؛ 나머ج امتناع. */
  private fun gateForKnowledge(evidence: RoutingEvidence): GateOutcome? {
    val state = evidence.knowledgeState ?: return null
    return when (state) {
      KnowledgeState.VERIFIED, KnowledgeState.CANDIDATE -> GateOutcome.ANSWER
      KnowledgeState.CONFLICT -> GateOutcome.ANSWER // تُنفذ الطبقة التنفيذية الرد مع تحذير النزاع
      KnowledgeState.INSUFFICIENT, KnowledgeState.ERROR -> GateOutcome.ABSTAIN
    }
  }

  /** بوابة الدين حسب درجة الموثوقية القديمة من محرك الدين. */
  private fun religionGate(
    evidence: RoutingEvidence,
    traceId: String,
    scores: Map<String, Double>
  ): RoutingDecision {
    val conf = evidence.classifierConfidence
    val gate = when {
      conf >= policy.RELIGION_QURAN_MIN_SCORE -> GateOutcome.ANSWER
      conf >= policy.RELIGION_HADITH_ABSTAIN_SCORE -> GateOutcome.CLARIFY
      else -> GateOutcome.ABSTAIN
    }
    return RoutingDecision(
      raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
      intent = Intent(evidence.intentLabel), scores = scores, confidence = conf,
      route = Route.RELIGION, gate = gate,
      clarifyOptions = if (gate == GateOutcome.CLARIFY) clarifyOptionsFrom(evidence) else emptyList(),
      traceId = traceId
    )
  }

  /** بوابة النية غير المعروفة: Amتناع إذا دون العتبة، توضيح إذا وجدت بدائل فعلية، وإلا امتناع. */
  private fun unknownGate(
    evidence: RoutingEvidence,
    traceId: String,
    scores: Map<String, Double>
  ): RoutingDecision {
    val conf = evidence.classifierConfidence
    val intent = Intent(evidence.intentLabel)

    if (conf < policy.UNKNOWN_ABSTAIN) {
      return RoutingDecision(
        raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
        intent = intent, scores = scores, confidence = conf, route = Route.UNKNOWN,
        gate = GateOutcome.ABSTAIN, clarifyOptions = emptyList(), traceId = traceId
      )
    }

    val options = topIntentOptions(scores, current = intent.label, limit = 3)
    val gate = if (options.size >= 2) GateOutcome.CLARIFY else GateOutcome.ABSTAIN
    return RoutingDecision(
      raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
      intent = intent, scores = scores, confidence = conf,
      route = Route.UNKNOWN, gate = gate, clarifyOptions = options, traceId = traceId
    )
  }

  /** قرار عام بناءً على حكم الأداة. */
  private fun verdictToGate(
    evidence: RoutingEvidence,
    route: Route,
    traceId: String,
    scores: Map<String, Double>
  ): RoutingDecision {
    val gate = when (evidence.toolVerdict) {
      null, ToolVerdict.OK -> GateOutcome.ANSWER
      ToolVerdict.AMBIGUOUS -> GateOutcome.CLARIFY
      ToolVerdict.FAILED, ToolVerdict.UNSUPPORTED -> GateOutcome.ABSTAIN
    }
    return RoutingDecision(
      raw = evidence.raw, normalized = evidence.normalized, detectedLang = evidence.lang.language,
      intent = Intent(evidence.intentLabel), scores = scores,
      confidence = evidence.classifierConfidence.coerceIn(0.0, 1.0),
      route = route, gate = gate,
      clarifyOptions = if (gate == GateOutcome.CLARIFY) clarifyOptionsFrom(evidence) else emptyList(),
      traceId = traceId
    )
  }

  /** بدائل التوضيح: من أعلى النيات في [scores] فقط (حالي وunknown م exclu)، لا كلمات عشوائية من المدخلة. */
  private fun clarifyOptionsFrom(evidence: RoutingEvidence): List<String> {
    return topIntentOptions(evidence.scores, current = evidence.intentLabel, limit = 3)
  }

  /**
   * أعلى [limit] نيات من [scores] باستثناء [current] و [Intent.UNKNOWN]، مرتبة تنازليًا حسب الدرجة.
   * تُستخدم حصريًا كخيارات [GateOutcome.CLARIFY] حتى لا تبدو البدائل عشوائية.
   */
  private fun topIntentOptions(
    scores: Map<String, Double>,
    current: String,
    limit: Int
  ): List<String> {
    return scores.entries
      .filter { it.key != current && it.key != Intent.UNKNOWN && it.value > 0.0 }
      .sortedByDescending { it.value }
      .take(limit)
      .map { it.key }
  }

  /**convert حكم الأداة إلى درجة ثقة معقولة (غير مخترعة؛ أقل من درجة المصنف عندما Fail). */
  private fun toolVerdictConfidence(verdict: ToolVerdict?, current: Double): Double {
    return when (verdict) {
      null -> current.coerceIn(0.0, 1.0)
      ToolVerdict.OK -> current.coerceIn(0.0, 1.0)
      ToolVerdict.AMBIGUOUS -> (current * 0.6).coerceIn(0.0, 1.0)
      ToolVerdict.FAILED, ToolVerdict.UNSUPPORTED -> (current * 0.25).coerceIn(0.0, 1.0)
    }
  }

  /** تصفيف درجات الثقة إلى المدى الشرعي [0, 1] (لا عتبات جديدة). */
  private fun coerceConfidence(value: Double): Double = value.coerceIn(0.0, 1.0)

  companion object {
    fun newTraceId(): String = UUID.randomUUID().toString()
  }
}
