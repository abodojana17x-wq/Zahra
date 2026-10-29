package com.example.zahra.knowledge.unit

import android.content.Context

/**
 * تحميل فهرس وحدات المعرفة المشحون في assets (`knowledge_index.json`).
 *
 * الفهرس يُبنى زمن البناء بـ `tools/build_index.py` (D24)، فالتحميل هنا **قراءة فقط** بلا حساب صرفي
 * للوثائق. أي خطأ (ملف مفقود/تالف/إصدار غير مدعوم) يعود `null` صراحةً، ويُسجَّل السبب — لا استثناء يصل
 * للمستخدم ولا صمت يُخفي العطل.
 */
object UnitIndexAndroidLoader {

  const val ASSET_NAME = "knowledge_index.json"

  class LoadOutcome(val index: KnowledgeUnitIndex?, val error: String?)

  fun loadOrNull(context: Context, assetName: String = ASSET_NAME): LoadOutcome {
    return try {
      val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
      LoadOutcome(UnitIndexCodec.decode(text), null)
    } catch (e: Exception) {
      LoadOutcome(null, "تعذّر تحميل فهرس وحدات المعرفة ($assetName): ${e.message}")
    }
  }

  /** خدمة جاهزة أو `null` مع السبب. */
  fun serviceOrNull(context: Context, assetName: String = ASSET_NAME): Pair<UnitKnowledgeService?, String?> {
    val outcome = loadOrNull(context, assetName)
    val index = outcome.index ?: return null to outcome.error
    return UnitKnowledgeService(index) to null
  }
}
