package com.example.zahra.diagnostics

import com.example.zahra.tools.JsonParseException
import com.example.zahra.tools.MiniJson

/**
 * معلومات الإصدار والقيود المعروفة — المرحلة 6.
 *
 * المصدر الوحيد هو `release/limitations.json`، ويُولَّد منه أصل التطبيق
 * `app/src/main/assets/release_info.json` عبر `tools/phase06/release_audit.py`.
 * لا تُكتب القيود هنا يدويًا، ولا تُحذف قيود معروفة لتجميل الواجهة.
 *
 * هذا الملف نقيّ (Pure Kotlin) ولا يستورد Android، حتى يُختبر على JVM بلا
 * Robolectric. قراءة الأصل تتم في الواجهة/الطبقة التي تملك `Context`.
 */
data class KnownLimitation(
  val id: String,
  val categoryAr: String,
  val severity: String,
  val severityAr: String,
  val titleAr: String,
  val detailAr: String,
) {
  val rank: Int
    get() = when (severity) {
      "critical" -> 0
      "major" -> 1
      "minor" -> 2
      else -> 3
    }
}

data class ReleaseInfo(
  val app: String,
  val versionName: String,
  val versionCode: Int,
  val channel: String,
  val channelAr: String,
  val measurementStatus: String,
  val measurementAr: String,
  val updatedAt: String,
  val limitations: List<KnownLimitation>,
) {
  /** تجميع العرض مع الحفاظ على ترتيب الفئات كما وردت في الأصل. */
  fun groupedByCategory(): List<Pair<String, List<KnownLimitation>>> {
    val order = LinkedHashMap<String, MutableList<KnownLimitation>>()
    for (item in limitations) {
      order.getOrPut(item.categoryAr) { mutableListOf() }.add(item)
    }
    return order.map { (category, items) ->
      category to items.sortedWith(compareBy({ it.rank }, { it.id }))
    }
  }

  val criticalCount: Int get() = limitations.count { it.severity == "critical" }
}

object ReleaseInfoCatalog {
  const val ASSET_NAME = "release_info.json"
  private const val SUPPORTED_SCHEMA = 1.0
  private val SEVERITIES = setOf("critical", "major", "minor", "info")

  /**
   * يقرأ الأصل بدقة، ويرفض أي شكل غير متوقع بدل عرض معلومات ناقصة كأنها كاملة.
   * أي فشل في الواجهة يجب أن يظهر كخطأ صريح، لا كقائمة قيود فارغة.
   */
  fun parse(text: String): ReleaseInfo {
    val root = try {
      MiniJson.parse(text)
    } catch (error: JsonParseException) {
      throw IllegalArgumentException("ملف معلومات الإصدار ليس JSON صالحًا: ${error.message}")
    }
    val schema = root.numberOrNull("schema")
      ?: throw IllegalArgumentException("حقل schema مفقود")
    if (schema != SUPPORTED_SCHEMA) {
      throw IllegalArgumentException("إصدار مخطط معلومات الإصدار غير مدعوم: $schema")
    }
    fun required(key: String): String {
      val value = root.stringOrNull(key)?.trim().orEmpty()
      if (value.isEmpty()) throw IllegalArgumentException("حقل $key مفقود أو فارغ")
      return value
    }

    val versionCode = root.numberOrNull("versionCode")?.toInt()
      ?: throw IllegalArgumentException("حقل versionCode مفقود")
    val items = root.arrayOrNull("limitations")
      ?: throw IllegalArgumentException("حقل limitations مفقود")
    val limitations = items.mapIndexed { index, value ->
      fun field(key: String): String {
        val text = value.stringOrNull(key)?.trim().orEmpty()
        if (text.isEmpty()) throw IllegalArgumentException("القيد #${index + 1}: $key مفقود")
        return text
      }
      val severity = field("severity")
      if (severity !in SEVERITIES) {
        throw IllegalArgumentException("القيد #${index + 1}: خطورة غير معروفة ($severity)")
      }
      KnownLimitation(
        id = field("id"),
        categoryAr = field("categoryAr"),
        severity = severity,
        severityAr = field("severityAr"),
        titleAr = field("titleAr"),
        detailAr = field("detailAr"),
      )
    }
    if (limitations.isEmpty()) throw IllegalArgumentException("قائمة القيود فارغة")
    val duplicates = limitations.groupBy { it.id }.filterValues { it.size > 1 }.keys
    if (duplicates.isNotEmpty()) throw IllegalArgumentException("معرّفات قيود مكررة: $duplicates")

    return ReleaseInfo(
      app = required("app"),
      versionName = required("versionName"),
      versionCode = versionCode,
      channel = required("channel"),
      channelAr = required("channelAr"),
      measurementStatus = required("measurementStatus"),
      measurementAr = required("measurementAr"),
      updatedAt = required("updatedAt"),
      limitations = limitations,
    )
  }
}
