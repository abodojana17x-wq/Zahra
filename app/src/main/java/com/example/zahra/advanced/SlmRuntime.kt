package com.example.zahra.advanced

/**
 * طبقة التشغيل الموحّدة خلف وقت التشغيل المختار — المرحلة 7 · المسار B (D9/D10/D11).
 *
 * واجهة واحدة خلف أي خلفية (llama.cpp/GGUF، MediaPipe/.task، ONNX GenAI، ExecuTorch/.pte)،
 * حتى تبقى النواة والواجهة معزولين عن تفاصيل الاستدلال (D9: الوضع المتقدّم معزول).
 *
 * ⚠️ لا تعتمد هذه الواجهة على أي صنف أندرويد، لتظل قابلة للاختبار الكامل على JVM بلا Robolectric.
 *
 * ⚠️ لا توجد حاليًا خلفية تشغيل حقيقية مدمجة في الاعتماديات (لا llama.cpp/MLC/MediaPipe في build).
 * لذلك الوضع الإنتاجي الافتراضي هو [DisabledSlmRuntime] الذي يعيد «غير متاح» فيعيد زهرة فورًا
 * للنواة الحتمية (تدهور رشيق). تُضاف الخلفيات الحقيقية لاحقًا خلف هذه الواجهة بقرار مالك (D9/D10/D11).
 */

/** قيود الإخراج: قواعد GBNF أو JSON schema مضمّنة، ونقاط توقّف، وحرارة. */
data class GenerationConstraints(
  val grammar: String? = null,
  val jsonSchema: String? = null,
  val stopSequences: List<String> = emptyList(),
  val temperature: Double = 0.7
)

/** نتيجة توليد واحدة من وقت التشغيل. */
data class SlmResult(
  val text: String,
  val finished: Boolean,
  val tokenCount: Int,
  val elapsedMs: Long,
  /** هل كان وقت التشغيل جاهزًا فعلًا (نموذج محمّل)؟ false ⇒ تدهور رشيق للنواة. */
  val available: Boolean,
  val cancelled: Boolean = false,
  val timedOut: Boolean = false,
  val error: String? = null
)

/** قدرات وقت التشغيل المُعلنة (لا ادعاء دعم دون خلفية حقيقية). */
data class RuntimeCapabilities(
  val supported: Boolean,
  val kinds: Set<SlmRuntimeKind> = emptySet(),
  val maxContextTokens: Int = 0,
  val supportsGrammar: Boolean = false,
  val supportsStreaming: Boolean = false
) {
  companion object {
    val NONE = RuntimeCapabilities(supported = false)
  }
}

interface SlmRuntime {
  /** يحمّل نموذجًا (أو يعيد false إن تعذّر). onProgress من 0..1. */
  fun load(model: SlmModelEntry, onProgress: (Float) -> Unit = {}): Boolean

  fun isLoaded(): Boolean

  /**
   * يولّد نصًا ضمن ميزانية صارمة.
   * @param maxTokens افتراضي 256 (المسار B.2).
   * @param budgetMs مهلة زمنية قابلة للضبط؛ عند تجاوزها يعود [SlmResult.timedOut]=true.
   */
  fun generate(
    prompt: String,
    constraints: GenerationConstraints = GenerationConstraints(),
    maxTokens: Int = 256,
    budgetMs: Long = 8000
  ): SlmResult

  /** إلغاء فوري (مغادرة المستخدم للشاشة — تجنّب استنزاف البطارية). */
  fun cancel()

  /** تفريغ النموذج من الذاكرة. */
  fun unload()

  fun capabilities(): RuntimeCapabilities
}

/**
 * الخلفية الافتراضية في الإنتاج: معطّلة دومًا. أي محاولة توليد تعود «غير متاح»
 * فيعيد [GroundedGenerator] التوجيه للنواة الحتمية (0–6) فورًا.
 *
 * هذا ليس فشلًا — هو السلوك الآمن المطلوب حتى يُضيف المالك خلفية تشغيل حقيقية (D9/D10/D11)
 * وينزّل نموذجًا مكمّمًا عبر [com.example.zahra.advanced.ModelDelivery].
 */
object DisabledSlmRuntime : SlmRuntime {
  override fun load(model: SlmModelEntry, onProgress: (Float) -> Unit): Boolean = false

  override fun isLoaded(): Boolean = false

  override fun generate(
    prompt: String,
    constraints: GenerationConstraints,
    maxTokens: Int,
    budgetMs: Long
  ): SlmResult = SlmResult(
    text = "",
    finished = false,
    tokenCount = 0,
    elapsedMs = 0,
    available = false,
    error = "وضع التوليد المتقدم معطّل: لا توجد خلفية تشغيل مدمجة ولا نموذج محمّل."
  )

  override fun cancel() = Unit

  override fun unload() = Unit

  override fun capabilities(): RuntimeCapabilities = RuntimeCapabilities.NONE
}
