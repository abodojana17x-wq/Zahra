package com.example.zahra.math

/**
 * شجرة عمليات رياضية بسيطة لا تعتمد على أي مكتبة خارجية ولا على `eval`.
 *
 * الشجرة تُبنى من [MathParser] وتُقيَّم بواسطة [MathEvaluator]. تُستخدم أيضًا
 * لتوليد `executionTrace` قابل للتدقيق (تمثيل بادئي/prefix) في ردود المحرّك.
 */
sealed class MathExpr {
  /** تمثيل بادئي مقروء: `(+ 2 (* 3 4))` — يفيد في تتبّع القرار وتصحيح الأخطاء. */
  abstract fun toPrefixString(): String

  /** تمثيل داخلي (infix) لإعادة بناء التعبير بشكل قياسي. */
  abstract fun toInfixString(): String
}

data class NumberExpr(val value: Double) : MathExpr() {
  override fun toPrefixString(): String = MathFormatting.formatNumber(value)
  override fun toInfixString(): String = MathFormatting.formatNumber(value)
}

/** ثابت رياضي مسمّى مثل `pi` أو `e` أو `phi`. */
data class ConstantExpr(val name: String, val value: Double) : MathExpr() {
  override fun toPrefixString(): String = name
  override fun toInfixString(): String = name
}

enum class UnaryOperator { NEGATE, FACTORIAL }

data class UnaryExpr(val operator: UnaryOperator, val operand: MathExpr) : MathExpr() {
  override fun toPrefixString(): String = when (operator) {
    UnaryOperator.NEGATE -> "(- ${operand.toPrefixString()})"
    UnaryOperator.FACTORIAL -> "(! ${operand.toPrefixString()})"
  }

  override fun toInfixString(): String = when (operator) {
    UnaryOperator.NEGATE -> "-(${operand.toInfixString()})"
    UnaryOperator.FACTORIAL -> "(${operand.toInfixString()})!"
  }
}

enum class BinaryOperator(val symbol: String) {
  ADD("+"),
  SUBTRACT("-"),
  MULTIPLY("*"),
  DIVIDE("/"),
  MODULO("%"),
  POWER("^")
}

data class BinaryExpr(
  val operator: BinaryOperator,
  val left: MathExpr,
  val right: MathExpr
) : MathExpr() {
  override fun toPrefixString(): String =
    "(${operator.symbol} ${left.toPrefixString()} ${right.toPrefixString()})"

  override fun toInfixString(): String =
    "(${left.toInfixString()} ${operator.symbol} ${right.toInfixString()})"
}

data class CallExpr(
  val name: String,
  val arguments: List<MathExpr>
) : MathExpr() {
  override fun toPrefixString(): String =
    "($name ${arguments.joinToString(" ") { it.toPrefixString() }})"

  override fun toInfixString(): String =
    "$name(${arguments.joinToString(", ") { it.toInfixString() }})"
}

/** نتيجة تقييم تعبير كاملة مع أثر قابل للتدقيق. */
data class MathEvaluation(
  val value: Double,
  val canonicalExpression: String,
  val operationTree: String
)

/** تنسيق موحّد للأرقام في كل أدوات المرحلة الثانية. */
object MathFormatting {
  private val DECIMAL = java.text.DecimalFormat("#.####", java.text.DecimalFormatSymbols(java.util.Locale.US)).apply {
    isGroupingUsed = false
  }

  private val DECIMAL_6 = java.text.DecimalFormat("#.######", java.text.DecimalFormatSymbols(java.util.Locale.US)).apply {
    isGroupingUsed = false
  }

  /** عدد صحيح ⇒ بدون كسور؛ غير ذلك بحد أقصى 4 منازل (أو 6 للأرقام الدقيقة جدًا). */
  private val SCIENTIFIC = java.text.DecimalFormat("0.####E0", java.text.DecimalFormatSymbols(java.util.Locale.US)).apply {
    isGroupingUsed = false
  }

  fun formatNumber(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
    val magnitude = kotlin.math.abs(value)
    // أرقام ضخمة أو دقيقة جدًا تُكتب بالصيغة العلمية بدل سلسلة أصفار مضللة
    if (magnitude != 0.0 && (magnitude >= 1e15 || magnitude < 1e-6)) {
      return SCIENTIFIC.format(java.math.BigDecimal.valueOf(value))
    }
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    val fourDecimals = DECIMAL.format(java.math.BigDecimal.valueOf(value))
    if (fourDecimals.replace("-", "").replace(".", "").trim('0').isEmpty()) {
      return DECIMAL_6.format(java.math.BigDecimal.valueOf(value))
    }
    return fourDecimals
  }
}
