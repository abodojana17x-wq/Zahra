package com.example.zahra.routing

import com.example.zahra.core.DetectedLang

/**
 * قرار التوجيه الموحد لطلب مستخدم واحد في زهرة.
 *
 * يُنتجه [Router] من تتابع unambiguous:
 * `normalized → intent → confidence → route → gate → outcome`.
 * يُنفّذه [com.example.zahra.engines.ZahraRouter.process] بعد ذلك دون إعادة حساب القرار.
 *
 * @property raw المدخلة الخام من المستخدم (كما وردت، franco/عربي/إنجليزي/مixed).
 * @property normalized المخرج بعد التطبيع (`ZahraNormalizer.normalize`).
 * @property detectedLang لغة/kشف النص حسب [com.example.zahra.core.LanguageDetector].
 * @property intent النية المصنّفة (وسم نصي يُغلّف في [Intent]).
 * @property scores الدرجات لكل نية من المصنف (مثل `rankedProbabilities` من `CalibratedClassifier`) — ليست احتمالات معايَرة.
 * @property confidence درجة الثقة النهائية للقرار؛ مصدرها موثق (أداة / استرجاع / مصنف / ثابت قديم).
 * @property route المسار المختار לפי أولوية التوجيه.
 * @property gate مخرج بوابة الثقة: [GateOutcome.ANSWER] / [GateOutcome.CLARIFY] / [GateOutcome.ABSTAIN].
 * @property clarifyOptions خيارات التوضيح cuando [gate] == [GateOutcome.CLARIFY] — مأخوذة من أعلى النيات الفعلية فقط.
 * @property traceId معرّف تتبع فريد للقرار لتشخيص المراحل اللاحقة (لا يُشترط إنتاج تقارير قياس).
 */
data class RoutingDecision(
  val raw: String,
  val normalized: String,
  val detectedLang: DetectedLang,
  val intent: Intent,
  val scores: Map<String, Double>,
  val confidence: Double,
  val route: Route,
  val gate: GateOutcome,
  val clarifyOptions: List<String>,
  val traceId: String
)
