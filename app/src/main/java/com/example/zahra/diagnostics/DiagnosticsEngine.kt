package com.example.zahra.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import com.example.zahra.engines.MathEngine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * زر «تشخيص» — المرحلة 0 (plans/00-measurement.md · المسار D.3).
 *
 * يعرض داخل التطبيق **ما يمكن إثباته على الجهاز نفسه** بلا شبكة:
 *  1) فحوصات محرك حقيقية تُنفَّذ الآن (حساب، وحدات، فك أصول، ذاكرة، صلاحيات).
 *  2) عدد الوحدات في كل قاعدة معرفة (إصدار الفهرس الحقيقي).
 *  3) حالة الشبكة من الـ Manifest نفسه: غياب إذن INTERNET = Zero-Network مضمون لا مُدَّعى.
 *  4) آخر نتيجة قياس في CI — تُضمَّن في الـ APK كأصل `eval_summary.json` وقت البناء
 *     (ينشئه scripts/export_eval_summary.py من results.json). غيابه يعني بناء محلي بلا قياس.
 *
 * لا يحتوي هذا الملف أي ادعاء غير مُقاس: كل سطر معروض مشتق من نتيجة تنفيذ.
 */
data class DiagnosticCheck(
  val id: String,
  val labelAr: String,
  val passed: Boolean,
  val detail: String,
)

data class DiagnosticsReport(
  val ranAt: String,
  val checks: List<DiagnosticCheck>,
  val assetCounts: List<Pair<String, Int>>,
  val networkMode: String,
  val embeddedEval: String,
  val appVersion: String,
) {
  val passedCount: Int get() = checks.count { it.passed }
  val totalCount: Int get() = checks.size
  val allPassed: Boolean get() = checks.all { it.passed }
}

object DiagnosticsEngine {

  private const val PREFS = "zahra_diagnostics"
  private const val KEY_LAST_RUN = "last_run_utc"

  fun run(context: Context): DiagnosticsReport {
    val checks = mutableListOf<DiagnosticCheck>()
    val assets = mutableListOf<Pair<String, Int>>()

    fun check(id: String, label: String, block: () -> Pair<Boolean, String>) {
      val result = try {
        block()
      } catch (e: Throwable) {
        false to "استثناء: ${e.javaClass.simpleName}"
      }
      checks.add(DiagnosticCheck(id, label, result.first, result.second))
    }

    check("calc_basic", "حساب: 7 × 8 = 56") {
      val value = MathEngine.evaluate("7*8").numericResult
      (value != null && abs(value - 56.0) < 1e-6) to "الناتج=$value"
    }
    check("calc_arabic", "حساب بأرقام عربية-هندية: ٧ × ٨") {
      val value = MathEngine.evaluate("٧ × ٨").numericResult
      (value != null && abs(value - 56.0) < 1e-6) to "الناتج=$value"
    }
    check("calc_sqrt", "جذر ١٤٤ = 12") {
      val value = MathEngine.evaluate("جذر 144").numericResult
      (value != null && abs(value - 12.0) < 1e-6) to "الناتج=$value"
    }
    check("calc_power", "أس: ٢^١٠ = 1024") {
      val value = MathEngine.evaluate("٢^١٠").numericResult
      (value != null && abs(value - 1024.0) < 1e-6) to "الناتج=$value"
    }
    check("unit_km_mi", "وحدات: ١٠ كم ≈ 6.2137 ميل") {
      val value = MathEngine.convertUnit(10.0, "km", "mi").numericResult
      (value != null && abs(value - 6.21371) < 0.01) to "الناتج=$value"
    }

    for (name in listOf("general_kb.json", "religion_kb.json", "translation_kb.json")) {
      val count = runCatching { countLeaves(JSONObject(readAsset(context, name))) }.getOrDefault(0)
      assets.add(name to count)
      checks.add(
        DiagnosticCheck(
          id = "asset_$name",
          labelAr = "أصل $name يحتوي وحدات",
          passed = count > 0,
          detail = "$count وحدة",
        )
      )
    }

    check("intent_corpus", "مجموعة النوايا (≥ 20 نية)") {
      val root = JSONObject(readAsset(context, "intent_corpus.json"))
      val intents = root.optJSONObject("intents")
      val count = intents?.length() ?: 0
      var samples = 0
      val keys = intents?.keys()
      while (keys != null && keys.hasNext()) {
        val arr = intents.optJSONArray(keys.next())
        samples += arr?.length() ?: 0
      }
      (count >= 20) to "$count نية / $samples عبارة"
    }

    check("zero_network", "صفر شبكة: لا يوجد إذن INTERNET") {
      val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS
      )
      val permissions = info.requestedPermissions?.toList() ?: emptyList()
      val hasInternet = permissions.any {
        it == android.Manifest.permission.INTERNET || it == android.Manifest.permission.ACCESS_NETWORK_STATE
      }
      (!hasInternet) to if (hasInternet) "الإذن موجود في الـ Manifest" else "لا إذن شبكة — تحقق من المانيفست"
    }

    check("memory_prefs", "الذاكرة المحلية قابلة للكتابة والقراءة") {
      val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      prefs.edit().putString("selftest", "zahra").commit()
      val read = prefs.getString("selftest", null)
      (read == "zahra") to "قراءة/كتابة ناجحة"
    }

    val ranAt = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).format(Date())
    runCatching {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_RUN, ranAt).apply()
    }

    return DiagnosticsReport(
      ranAt = ranAt,
      checks = checks,
      assetCounts = assets,
      networkMode = if (checks.firstOrNull { it.id == "zero_network" }?.passed == true) {
        "بلا إذن شبكة (فحص المانيفست)"
      } else {
        "إذن شبكة موجود في الـ Manifest"
      },
      embeddedEval = readEmbeddedEval(context),
      appVersion = versionText(context),
    )
  }

  fun lastRunAt(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_RUN, null)

  private fun readEmbeddedEval(context: Context): String {
    return runCatching {
      val json = JSONObject(readAsset(context, "eval_summary.json"))
      val passed = json.optInt("passed", -1)
      val total = json.optInt("total", -1)
      if (passed < 0 || total <= 0) return@runCatching "تقرير غير صالح"
      val rate = json.optDouble("rate", Double.NaN)
      val commit = json.optString("commit", "?")
      val wilson = json.optString("wilson", "?")
      val at = json.optString("generated_at_utc", "?")
      "المجموعة v${json.optString("dataset_version", "?")} · $passed/$total " +
        "(${if (rate.isNaN()) "?" else String.format(Locale.US, "%.1f%%", rate * 100)}) · " +
        "فاصل ويلسون $wilson · commit ${commit.take(7)} · $at"
    }.getOrElse { "لا يوجد تقرير مضمَّن (بناء محلي بلا CI) — انظر build/reports/zahra-eval/SUMMARY.md" }
  }

  private fun versionText(context: Context): String {
    return runCatching {
      val info = context.packageManager.getPackageInfo(context.packageName, 0)
      val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
      "${info.versionName} ($code)"
    }.getOrDefault("?")
  }

  private fun readAsset(context: Context, name: String): String =
    context.assets.open(name).bufferedReader().use { it.readText() }

  private fun countLeaves(value: Any?): Int = when (value) {
    null, JSONObject.NULL -> 0
    is JSONObject -> {
      var total = 0
      val keys = value.keys()
      while (keys.hasNext()) total += countLeaves(value.opt(keys.next()))
      total
    }
    is JSONArray -> (0 until value.length()).sumOf { countLeaves(value.opt(it)) }
    is String -> 1
    else -> 0
  }
}
