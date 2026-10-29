package com.example.zahra.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.truncate

/**
 * سجلّ الدوال والثوابت المدعومة — لا يوجد أي `eval` أو تنفيذ ديناميكي.
 *
 * كل اسم غير موجود هنا يُرفض صراحةً عند التحليل (`MathSyntaxException`)، ولذلك
 * لا يمكن حقن `process.exit` أو `alert(...)` أو أي استدعاء خارجي.
 *
 * ملاحظة صدق: الدوال المثلثية تعمل بالراديان (مطابق للمعيار الرياضي و`java.lang.Math`)،
 * والدالتان [MathFunctionSpec.name] = `deg`/`rad` موجودتان للتحويل الصريح.
 */
data class MathFunctionSpec(
  val name: String,
  val minArguments: Int,
  val maxArguments: Int, // -1 = بلا حد أعلى
  val aliases: List<String>,
  val description: String,
  val invoke: (List<Double>) -> Double
)

object MathFunctionRegistry {

  /** الثوابت الرياضية المدعومة (المفتاح هو الاسم القياسي). */
  val constants: Map<String, Double> = linkedMapOf(
    "pi" to Math.PI,
    "e" to Math.E,
    "phi" to 1.6180339887498949,
    "tau" to 2.0 * Math.PI
  )

  /** أسماء عربية للثوابت. */
  val constantAliases: Map<String, String> = mapOf(
    "باي" to "pi",
    "بي" to "pi",
    "فاي" to "phi",
    "تاو" to "tau"
  )

  private fun requireFiniteResult(value: Double, functionName: String): Double {
    if (value.isNaN()) {
      throw MathDomainException("الدالة $functionName خارج نطاقها المعرّف لهذه القيم.")
    }
    if (value.isInfinite()) {
      throw MathOverflowException("نتيجة $functionName تجاوزت النطاق المسموح به.")
    }
    return value
  }

  private fun single(value: Double, name: String, body: (Double) -> Double): Double =
    requireFiniteResult(body(value), name)

  fun factorialOf(value: Double): Double {
    if (value < 0 || abs(value - round(value)) > 1e-9) {
      throw MathDomainException("المضروب معرّف للأعداد الصحيحة غير السالبة فقط، والمدخل هو ${MathFormatting.formatNumber(value)}.")
    }
    val n = value.toLong()
    if (n > 170) {
      throw MathOverflowException("المضروب !170 هو أقصى ما يمكن تمثيله بدقة في هذا المحرّك.")
    }
    var acc = 1.0
    for (i in 2..n) acc *= i.toDouble()
    return acc
  }

  private fun gcdOf(a: Double, b: Double): Double {
    if (abs(a - round(a)) > 1e-9 || abs(b - round(b)) > 1e-9) {
      throw MathDomainException("القاسم المشترك الأكبر يحتاج عددين صحيحين.")
    }
    var x = abs(a).toLong()
    var y = abs(b).toLong()
    if (x == 0L && y == 0L) return 0.0
    while (y != 0L) {
      val t = y
      y = x % y
      x = t
    }
    return x.toDouble()
  }

  val functions: List<MathFunctionSpec> = listOf(
    MathFunctionSpec("sqrt", 1, 1, listOf("جذر", "الجذر", "جزر"), "الجذر التربيعي") { args ->
      val v = args[0]
      if (v < 0) throw MathDomainException("خطأ رياضي: لا يمكن حساب الجذر التربيعي لعدد سالب (${MathFormatting.formatNumber(v)}).")
      sqrt(v)
    },
    MathFunctionSpec("cbrt", 1, 1, listOf("جذر تكعيبي"), "الجذر التكعيبي") { args -> cbrt(args[0]) },
    MathFunctionSpec("abs", 1, 1, listOf("مطلق", "القيمة المطلقة"), "القيمة المطلقة") { args -> abs(args[0]) },
    MathFunctionSpec("sin", 1, 1, listOf("جيب", "جا"), "الجيب (بالراديان)") { args -> single(args[0], "sin") { sin(it) } },
    MathFunctionSpec("cos", 1, 1, listOf("جتا", "جيب تمام"), "جيب التمام (بالراديان)") { args -> single(args[0], "cos") { cos(it) } },
    MathFunctionSpec("tan", 1, 1, listOf("ظل", "ظا"), "الظل (بالراديان)") { args -> single(args[0], "tan") { tan(it) } },
    MathFunctionSpec("asin", 1, 1, listOf("قوس الجيب"), "قوس الجيب") { args ->
      if (args[0] < -1 || args[0] > 1) throw MathDomainException("قوس الجيب معرّف للقيم بين -1 و1 فقط.")
      asin(args[0])
    },
    MathFunctionSpec("acos", 1, 1, listOf("قوس جيب التمام"), "قوس جيب التمام") { args ->
      if (args[0] < -1 || args[0] > 1) throw MathDomainException("قوس جيب التمام معرّف للقيم بين -1 و1 فقط.")
      acos(args[0])
    },
    MathFunctionSpec("atan", 1, 1, listOf("قوس الظل"), "قوس الظل") { args -> atan(args[0]) },
    MathFunctionSpec("log", 1, 1, listOf("لوغاريتم", "لو"), "لوغاريتم أساس 10") { args ->
      if (args[0] <= 0) throw MathDomainException("اللوغاريتم معرّف للأعداد الأكبر من صفر فقط.")
      single(args[0], "log") { log10(it) }
    },
    MathFunctionSpec("log2", 1, 1, listOf("لوغاريتم ثنائي"), "لوغاريتم أساس 2") { args ->
      if (args[0] <= 0) throw MathDomainException("اللوغاريتم معرّف للأعداد الأكبر من صفر فقط.")
      single(args[0], "log2") { log2(it) }
    },
    MathFunctionSpec("ln", 1, 1, listOf("لن", "لوغاريتم طبيعي"), "اللوغاريتم الطبيعي") { args ->
      if (args[0] <= 0) throw MathDomainException("اللوغاريتم الطبيعي معرّف للأعداد الأكبر من صفر فقط.")
      single(args[0], "ln") { ln(it) }
    },
    MathFunctionSpec("exp", 1, 1, listOf("أس هـ"), "دالة الأس") { args -> single(args[0], "exp") { exp(it) } },
    MathFunctionSpec("floor", 1, 1, listOf("أرضية"), "أكبر عدد صحيح أقل من أو يساوي") { args -> floor(args[0]) },
    MathFunctionSpec("ceil", 1, 1, listOf("سقف"), "أصغر عدد صحيح أكبر من أو يساوي") { args -> ceil(args[0]) },
    MathFunctionSpec("round", 1, 1, listOf("تقريب"), "تقريب لأقرب عدد صحيح") { args -> round(args[0]) },
    MathFunctionSpec("trunc", 1, 1, listOf("اقتطاع"), "حذف الجزء الكسري") { args -> truncate(args[0]) },
    MathFunctionSpec("sign", 1, 1, listOf("إشارة"), "إشارة العدد (-1 أو 0 أو 1)") { args -> sign(args[0]) },
    MathFunctionSpec("fact", 1, 1, listOf("مضروب", "عاملي"), "المضروب n!") { args -> factorialOf(args[0]) },
    MathFunctionSpec("pow", 2, 2, listOf("قوة", "أس"), "الرفع لقوة: pow(الأساس، الأس)") { args ->
      val base = args[0]
      val exponent = args[1]
      if (base < 0 && abs(exponent - round(exponent)) > 1e-9) {
        throw MathDomainException("رفع عدد سالب لأس كسري غير معرّف في الأعداد الحقيقية.")
      }
      requireFiniteResult(base.pow(exponent), "pow")
    },
    MathFunctionSpec("min", 1, -1, listOf("أصغر", "الأصغر"), "أصغر قيمة") { args -> args.min() },
    MathFunctionSpec("max", 1, -1, listOf("أكبر", "الأكبر"), "أكبر قيمة") { args -> args.max() },
    MathFunctionSpec("sum", 1, -1, listOf("مجموع"), "مجموع القيم") { args -> args.sum() },
    MathFunctionSpec("avg", 1, -1, listOf("متوسط"), "المتوسط الحسابي") { args -> args.sum() / args.size },
    MathFunctionSpec("gcd", 2, 2, listOf("قاسم مشترك أكبر"), "القاسم المشترك الأكبر") { args -> gcdOf(args[0], args[1]) },
    MathFunctionSpec("lcm", 2, 2, listOf("مضاعف مشترك أصغر"), "المضاعف المشترك الأصغر") { args ->
      val g = gcdOf(args[0], args[1])
      if (g == 0.0) 0.0 else requireFiniteResult(abs(args[0] * args[1]) / g, "lcm")
    },
    MathFunctionSpec("deg", 1, 1, listOf("درجات"), "تحويل من راديان إلى درجات") { args -> Math.toDegrees(args[0]) },
    MathFunctionSpec("rad", 1, 1, listOf("راديان"), "تحويل من درجات إلى راديان") { args -> Math.toRadians(args[0]) }
  )

  private val byName: Map<String, MathFunctionSpec> = buildMap {
    for (spec in functions) {
      put(spec.name.lowercase(), spec)
      for (alias in spec.aliases) put(alias.lowercase(), spec)
    }
  }

  private val constantsByName: Map<String, Double> = buildMap {
    putAll(constants)
    for ((alias, canonical) in constantAliases) put(alias.lowercase(), constants.getValue(canonical))
  }

  fun findFunction(name: String): MathFunctionSpec? = byName[name.trim().lowercase()]

  fun findConstant(name: String): Double? = constantsByName[name.trim().lowercase()]

  fun isKnownIdentifier(name: String): Boolean =
    findFunction(name) != null || findConstant(name) != null
}
