package com.example.zahra.advanced

/**
 * سجل نماذج اللغة الصغيرة (SLM) المرشّحة للوضع المتقدّم — المرحلة 7 · المسار A (D9/D10/D11).
 *
 * ⚠️ لا يُضمّن هذا الملف أي وزن نموذجي. هذه بيانات وصفية فقط تُستخدم لاتخاذ قرار التنزيل
 * (D11: تنزيل عند الطلب + تحقق sha256 + تخزين مشفّر + زر حذف). كل الأوزان تُنزَّل خارج APK
 * الأساسي وبموافقة صريحة (Opt-in).
 *
 * قاعدة سوداء إلزامية: أي نموذج يتطلب اتصالًا بالسحابة وقت الاستدلال يُرفض (ينتهك Zero-Network/D1).
 *
 * القبول لا يتم إلا بعد تحقق: رخصة تسمح بالتوزيع + حجم مكمّم ≤ ميزانية + RAM ≤ 2GB + درجة عربية/مصرية
 * مقاسة فعليًا. الدرجات (arabicScore/egyptianScore) تُملأ من قياس على `eval/frontier-set.jsonl`؛
 * القيمة `-1.0` تعني «غير مقاسة بعد» — ولا نختلق درجة.
 *
 * المصادر (بحث R0 · 2026):
 *  - On-device LLM inference on Android (Grokipedia, 2026-03): MediaPipe tasks-genai، Gemma-3 1B 4-bit، GGUF/llama.cpp.
 *  - androidexperto (2026-05): جدول أوقات التشغيل، minSdk 26، نماذج 1B–3B مكمّمة.
 *  - RightNow-Arabic-0.5B-Turbo (arXiv:2605.28827, 2026-04): أصغر نموذج عربي متخصص، ~398MB Q4.
 *  - Best Arabic Local LLMs 2026 (promptquorum, 2026-08): Jais/ALLaM/AceGPT/Fanar، أحجام ورخص.
 */

/** نوع وقت التشغيل المطلوب لكل صيغة نموذج. */
enum class SlmRuntimeKind { LLAMA_CPP_GGUF, MEDIAPIPE_TASK, ONNX_GENAI, EXECUTORCH_PTE, UNKNOWN }

/**
 * مرشّح نموذج واحد.
 *
 * @property arabicScore  درجة عربية مقاسة على `frontier-set` في [0,1]، أو `-1.0` إن لم تُقاس.
 * @property egyptianScore درجة مصري مقاسة، أو `-1.0` إن لم تُقاس.
 * @property requiresNetworkForInference إن كان يطلب سحابة وقت الاستدلال ⇒ مرفوض دائمًا (القاعدة السوداء).
 */
data class SlmModelEntry(
  val id: String,
  val family: String,
  val params: String,
  val quant: String,
  val sizeMB: Int,
  val license: String,
  val licenseUrl: String,
  val licenseAllowsCommercial: Boolean,
  val licenseAllowsRedistribution: Boolean,
  val minRamMB: Int,
  val sourceUrl: String,
  val runtime: SlmRuntimeKind,
  val hasArabicSupport: Boolean,
  val arabicScore: Double = -1.0,
  val egyptianScore: Double = -1.0,
  val requiresNetworkForInference: Boolean = false,
  val notes: String = ""
)

/** حالة قبول مرشّح بعد تطبيق معايير المسار A. */
enum class ModelAcceptance {
  /** مستوفٍ كل الشروط + درجات مقاسة ≥ العتبة. */
  ELIGIBLE,
  /** يتطلب سحابة وقت الاستدلال — مرفوض (Zero-Network). */
  BLOCKED_CLOUD,
  /** رخصة لا تسمح بالتوزيع داخل التطبيق. */
  BLOCKED_LICENSE,
  /** حجم مكمّم يتجاوز الميزانية. */
  BLOCKED_SIZE,
  /** RAM المطلوبة تتجاوز السقف أو جهاز المستخدم. */
  BLOCKED_RAM,
  /** رخصة وحجم ورام مقبولة، لكن الدرجات لم تُقاس بعد ⇒ لا قبول تلقائي. */
  PENDING_BENCHMARK
}

object ModelRegistry {
  /**
   * ميزانيات معلنة.
   * - النواة (المراحل 0–6) ≤ 60MB وفق D7.
   * - النموذج الإضافي يُقاس «بوضوح لكل حزمة/نموذج» وفق D7؛ السقف الافتراضي أدناه للوضع المتقدّم
   *   (انظر القرار D35 في DECISIONS.md)، ولا يُضمّن أي وزن في APK الأساسي (D11).
   */
  const val MAX_MODEL_SIZE_MB = 1500
  const val MAX_MODEL_RAM_MB = 2048
  /** عتبة القبول المعلنة للدرجة المقاسة (لا تُطبَّق دون قياس فعلي). */
  const val ACCEPT_ARABIC_SCORE = 0.50

  /**
   * قائمة المرشّحين المجمّعة من بحث R0 (2026). الدرجات `-1.0` عمدًا: لم تُقاس بعد،
   * فكل المرشّحين يبقون `PENDING_BENCHMARK` حتى يُشغَّل قياس على `frontier-set`.
   */
  val candidates: List<SlmModelEntry> = listOf(
    SlmModelEntry(
      id = "rightnow-arabic-0.5b-turbo-q4",
      family = "RightNow-Arabic",
      params = "0.5B",
      quant = "Q4_K_M",
      sizeMB = 398,
      license = "Apache-2.0 (تحقّق من شروط توزيع الأوزان المبنية على Qwen2.5-0.5B)",
      licenseUrl = "https://arxiv.org/abs/2605.28827",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 700,
      sourceUrl = "https://huggingface.co/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "الأصغر المتخصص عربيًا (518M)؛ ~398MB Q4 عبر llama.cpp؛ مرشّح أول لـ minSdk 24 ورام منخفض."
    ),
    SlmModelEntry(
      id = "qwen2.5-1.5b-instruct-q4",
      family = "Qwen",
      params = "1.5B",
      quant = "Q4_K_M",
      sizeMB = 1100,
      license = "Apache-2.0",
      licenseUrl = "https://huggingface.co/Qwen/",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 1200,
      sourceUrl = "https://huggingface.co/Qwen/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "متعدد اللغات بأداء عربي قوي؛ حجم معتدل ضمن الميزانية."
    ),
    SlmModelEntry(
      id = "gemma-3-1b-it-task",
      family = "Gemma",
      params = "1B",
      quant = "4-bit",
      sizeMB = 550,
      license = "Gemma Terms of Use (تجاري مسموح بحدود؛ قيود على تحسين نماذج أخرى)",
      licenseUrl = "https://ai.google.dev/gemma/terms",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 900,
      sourceUrl = "https://huggingface.co/google/",
      runtime = SlmRuntimeKind.MEDIAPIPE_TASK,
      hasArabicSupport = true,
      notes = "يدعمه MediaPipe LLM Inference API (com.google.mediapipe:tasks-genai) بصيغة .task."
    ),
    SlmModelEntry(
      id = "gemma-2-2b-it-task",
      family = "Gemma",
      params = "2B",
      quant = "4-bit",
      sizeMB = 1400,
      license = "Gemma Terms of Use",
      licenseUrl = "https://ai.google.dev/gemma/terms",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 1600,
      sourceUrl = "https://huggingface.co/google/",
      runtime = SlmRuntimeKind.MEDIAPIPE_TASK,
      hasArabicSupport = true,
      notes = "Gemma 2 2B .task ~1.4GB؛ ضمن الميزانية لكنه أثقل من 1B."
    ),
    SlmModelEntry(
      id = "acegpt-7b-q4",
      family = "AceGPT",
      params = "7B",
      quant = "Q4_K_M",
      sizeMB = 4500,
      license = "Apache-2.0",
      licenseUrl = "https://huggingface.co/cerebras/",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 4000,
      sourceUrl = "https://huggingface.co/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "عربي متخصص لكنه 7B ⇒ يتجاوز ميزانية الجوال (BLOCKED_SIZE)."
    ),
    SlmModelEntry(
      id = "jais-13b-chat-q4",
      family = "Jais",
      params = "13B",
      quant = "Q4_K_M",
      sizeMB = 8000,
      license = "Apache-2.0",
      licenseUrl = "https://huggingface.co/inceptionai/",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 7000,
      sourceUrl = "https://huggingface.co/inceptionai/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "عربي/إنجليزي ممتاز لكن 13B يتجاوز ميزانية الجوال (BLOCKED_SIZE)."
    ),
    SlmModelEntry(
      id = "allam-7b-instruct-q4",
      family = "ALLaM",
      params = "7B",
      quant = "Q4_K_M",
      sizeMB = 4500,
      license = "Apache-2.0",
      licenseUrl = "https://huggingface.co/humain-ai/",
      licenseAllowsCommercial = true,
      licenseAllowsRedistribution = true,
      minRamMB = 4000,
      sourceUrl = "https://huggingface.co/humain-ai/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "عربي قوي لكن 7B يتجاوز ميزانية الحجم (BLOCKED_SIZE)."
    ),
    SlmModelEntry(
      id = "fanar-2-q4",
      family = "Fanar",
      params = "27B",
      quant = "Q4_K_M",
      sizeMB = 16000,
      license = "Proprietary (OALL)",
      licenseUrl = "https://www.qcri.org/",
      licenseAllowsCommercial = false,
      licenseAllowsRedistribution = false,
      minRamMB = 12000,
      sourceUrl = "https://huggingface.co/QCRI/",
      runtime = SlmRuntimeKind.LLAMA_CPP_GGUF,
      hasArabicSupport = true,
      notes = "رخصة مغلقة ⇒ مرفوض التوزيع (BLOCKED_LICENSE) بغض النظر عن الحجم."
    ),
    SlmModelEntry(
      id = "hypothetical-cloud-llm-api",
      family = "Cloud-API",
      params = "unknown",
      quant = "n/a",
      sizeMB = 0,
      license = "Proprietary",
      licenseUrl = "",
      licenseAllowsCommercial = false,
      licenseAllowsRedistribution = false,
      minRamMB = 0,
      sourceUrl = "",
      runtime = SlmRuntimeKind.UNKNOWN,
      hasArabicSupport = true,
      requiresNetworkForInference = true,
      notes = "نموذج وهمي للتوضيح: أي نموذج يطلب سحابة وقت الاستدلال يُرفض تلقائيًا (القاعدة السوداء / Zero-Network)."
    )
  )

  /**
   * يحسب حالة القبول دون ادعاء قياس.
   *
   * @param deviceRamMB رام الجهاز المتاحة بـ MB؛ مرّر [Int.MAX_VALUE] لتجاهل فحص الجهاز.
   */
  fun assess(entry: SlmModelEntry, deviceRamMB: Int = Int.MAX_VALUE): ModelAcceptance {
    if (entry.requiresNetworkForInference) return ModelAcceptance.BLOCKED_CLOUD
    if (!entry.licenseAllowsRedistribution) return ModelAcceptance.BLOCKED_LICENSE
    if (entry.sizeMB > MAX_MODEL_SIZE_MB) return ModelAcceptance.BLOCKED_SIZE
    if (entry.minRamMB > MAX_MODEL_RAM_MB) return ModelAcceptance.BLOCKED_RAM
    if (deviceRamMB != Int.MAX_VALUE && entry.minRamMB > deviceRamMB) return ModelAcceptance.BLOCKED_RAM
    // لا قبول تلقائي قبل قياس الدرجات فعليًا (لا نختلق أرقامًا).
    if (entry.arabicScore < 0.0 || entry.egyptianScore < 0.0) return ModelAcceptance.PENDING_BENCHMARK
    if (entry.arabicScore < ACCEPT_ARABIC_SCORE || entry.egyptianScore < ACCEPT_ARABIC_SCORE) {
      return ModelAcceptance.PENDING_BENCHMARK
    }
    return ModelAcceptance.ELIGIBLE
  }

  /** المرشّحون المستوفون لكل الشروط + درجات مقاسة. (فارغ حتى يُشغَّل القياس.) */
  fun eligible(deviceRamMB: Int = Int.MAX_VALUE): List<SlmModelEntry> =
    candidates.filter { assess(it, deviceRamMB) == ModelAcceptance.ELIGIBLE }

  /** المرشّحون المرفوضون لأنهم يطلبون السحابة وقت الاستدلال. */
  fun blockedCloud(): List<SlmModelEntry> = candidates.filter { it.requiresNetworkForInference }

  fun byId(id: String): SlmModelEntry? = candidates.firstOrNull { it.id == id }
}
