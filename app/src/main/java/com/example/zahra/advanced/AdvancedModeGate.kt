package com.example.zahra.advanced

/**
 * بوابة تفعيل «الوضع المتقدّم» — تقرّر متى يُستخدم النموذج التوليدي المحلي ومتى يُعاد التوجيه للنواة.
 *
 * التدفّق (من خطة المرحلة 7): المستخدم يفعّل Opt-in → [AdvancedModeGate] يسأل: هل النموذج محمّل؟
 * هل الجهاز يكفي؟ هل الطلب من النوع التوليدي؟ → إن نعم يمرّر لـ [GroundedGenerator]، وإلا يعيد
 * للنواة الحتمية (0–6).
 *
 * لا تعتمد على أندرويد (قابل للاختبار على JVM).
 */

/** تفضيلات المستخدم لتفعيل الوضع المتقدّم. */
data class AdvancedModeSettings(
  /** Opt-in صريح من المستخدم (افتراضي false = النواة فقط). */
  val enabledByUser: Boolean,
  /** السماح بالتشغيل على أجهزة برام منخفض جدًا (يسمح بتجربة أضعف). */
  val allowOnLowRam: Boolean = false
)

/** ملف الجهاز لفحص كفاية الموارد. */
data class DeviceProfile(
  val totalRamMB: Int,
  val availableRamMB: Int = totalRamMB
)

/** تصنيف نوع الطلب قبل التوجيه. */
enum class RequestKind {
  /** حساب/تحويل/موعد/تذكير/طقس — النواة الحتمية أدق. */
  DETERMINISTIC,
  /** ديني/طبي — يُعالَج بالنواة الآمنة المزوّدة بمدقّقات سلامة. */
  SENSITIVE,
  /** سؤال حقيقي يُجاب من النواة الموثّقة (استرجاع + بوابة الثقة). */
  FACTUAL,
  /** تلخيص/إعادة صياغة/كتابة/شرح كود — النموذج التوليدي مع تأسيس بالمصادر. */
  GENERATIVE
}

sealed class AdvancedDecision {
  data class UseAdvanced(val reason: String) : AdvancedDecision()
  data class UseCore(val reason: String, val code: String) : AdvancedDecision()
}

object AdvancedModeGate {
  /**
   * أقل رام مريح لتشغيل SLM صغير على جهاز متوسط (تقديري — لا قياس؛ وثّق كحد أدنى لا كمعيار أداء).
   * يتجاوزه المستخدم عبر [AdvancedModeSettings.allowOnLowRam].
   */
  const val MIN_RAM_TO_ENABLE_MB = 1536

  fun decide(
    settings: AdvancedModeSettings,
    device: DeviceProfile,
    runtime: SlmRuntime,
    kind: RequestKind
  ): AdvancedDecision {
    if (!settings.enabledByUser) {
      return AdvancedDecision.UseCore("الوضع المتقدّم متوقّف (Opt-in مطلوب)", "disabled")
    }
    // المهام التي النواة الحتمية أفضل/أكثر أمانًا بها لا تمرّ عبر النموذج إطلاقًا.
    when (kind) {
      RequestKind.DETERMINISTIC -> return AdvancedDecision.UseCore("مهمة حتمية يتولّاها النواة بدقة", "deterministic")
      RequestKind.SENSITIVE -> return AdvancedDecision.UseCore("مجال حسّاس يُعالَج بالنواة الآمنة", "sensitive")
      RequestKind.FACTUAL -> return AdvancedDecision.UseCore("سؤال حقيقي يُجاب من النواة الموثّقة", "factual")
      RequestKind.GENERATIVE -> Unit // يكمل أدناه
    }
    if (!runtime.capabilities().supported || !runtime.isLoaded()) {
      return AdvancedDecision.UseCore("النموذج غير محمّل/غير متاح", "no_model")
    }
    if (device.availableRamMB < MIN_RAM_TO_ENABLE_MB && !settings.allowOnLowRam) {
      return AdvancedDecision.UseCore("الرام غير كافٍ لتشغيل النموذج على الجهاز", "low_ram")
    }
    return AdvancedDecision.UseAdvanced("نموذج جاهز وطلب توليدي صرف")
  }
}
