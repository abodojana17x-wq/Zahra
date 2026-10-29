package com.example.zahra.tools

import com.example.zahra.model.OutcomeState

/**
 * سياسة الشبكة — القرار D1: النواة أوفلاين دائمًا، والشبكة Opt-in صريحة.
 *
 * كل أداة تُعلن [NetworkRequirement]. البوابة [NetworkPolicy.gate] تمنع التنفيذ في
 * الوضع الخطأ وتُعيد رسالة صريحة بدل نتيجة مخترعة أو اتصال صامت.
 */
enum class NetworkRequirement { OFFLINE_ONLY, OPT_IN }

enum class NetworkMode {
  /** الوضع الافتراضي: لا يوجد أي طلب شبكة. */
  OFFLINE_ONLY,

  /** يفعّله المستخدم بنفسه من الإعدادات، ويظهر مؤشره في كل رد يستخدم مصدرًا حيًا. */
  OPT_IN_CONNECTED
}

sealed class NetworkDecision {
  data object Allowed : NetworkDecision()

  data class Blocked(val message: String, val outcomeState: OutcomeState = OutcomeState.UNSUPPORTED) : NetworkDecision()
}

/**
 * بوابة الشبكة المشتركة بين كل أدوات البيانات.
 *
 * @param mode الوضع الحالي (يُقرأ من تفضيلات المستخدم عند البناء).
 */
class NetworkPolicy(var mode: NetworkMode = NetworkMode.OFFLINE_ONLY) {

  fun isNetworkEnabled(): Boolean = mode == NetworkMode.OPT_IN_CONNECTED

  fun requiresOptInFor(requirement: NetworkRequirement): Boolean =
    requirement == NetworkRequirement.OPT_IN && !isNetworkEnabled()

  fun gate(toolName: String, requirement: NetworkRequirement): NetworkDecision {
    if (!requiresOptInFor(requirement)) return NetworkDecision.Allowed
    return NetworkDecision.Blocked(
      "«$toolName» محتاج اتصال بالإنترنت، والوضع المتصل مقفول حاليًا (زهرة أوفلاين افتراضيًا).\n" +
        "• فعّل «الوضع المتصل» من الإعدادات لو موافق على طلب شبكة صريح،\n" +
        "• أو استخدم الحزمة المحلية المتاحة في الجهاز.\n" +
        "مش هأدّعي معلومة حيّة من غير مصدر."
    )
  }

  companion object {
    /** يحوّل قيمة التفضيلات المحفوظة إلى وضع، مع الافتراضي الآمن. */
    fun fromPersistedValue(value: String?): NetworkMode =
      if (value == NetworkMode.OPT_IN_CONNECTED.name) NetworkMode.OPT_IN_CONNECTED else NetworkMode.OFFLINE_ONLY
  }
}
