package com.example.zahra.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات محلّل التعبيرات الجديد (المرحلة 2 / المسار A).
 *
 * الاختبارات مكتوبة ومراجعة، ولم تُشغَّل عبر Gradle في بيئة التنفيذ (لا JDK/SDK).
 * ما شُغّل فعليًا هو `tools/offline-typecheck/phase02/SelfCheck.kt` بنفس الحالات.
 */
class MathExpressionEngineTest {

  // ——— الأولويات والترابط ———
  @Test
  fun precedence_multiplicationBeforeAddition() {
    assertEquals(14.0, value("2 + 3 * 4"), 1e-9)
    assertEquals(20.0, value("(2 + 3) * 4"), 1e-9)
  }

  @Test
  fun exponentiation_isRightAssociative_andBindsTighterThanUnaryMinus() {
    assertEquals(512.0, value("2 ^ 3 ^ 2"), 1e-9)
    assertEquals(512.0, value("2 ** 3 ** 2"), 1e-9)
    assertEquals(-4.0, value("-2 ^ 2"), 1e-9)
    assertEquals(4.0, value("(-2) ^ 2"), 1e-9)
    assertEquals(0.125, value("2^-3"), 1e-9)
  }

  @Test
  fun implicitMultiplication_isSupported() {
    assertEquals(14.0, value("2(3+4)"), 1e-9)
    assertEquals(6.0, value("2sqrt(9)"), 1e-9)
    assertEquals(9.42477796076938, value("3pi"), 1e-9)
  }

  @Test
  fun factorial_isSupported_andDoubleFactorialIsRejected() {
    assertEquals(120.0, value("5!"), 1e-9)
    assertEquals(120.0, value("fact(5)"), 1e-9)
    expectSyntax { MathExpressionEngine.evaluate("3!!") }
  }

  // ——— الدوال ———
  @Test
  fun functions_registryCoversRequiredSet() {
    val required = listOf(
      "sqrt", "abs", "sin", "cos", "tan", "asin", "acos", "atan", "log", "log2", "ln", "exp",
      "floor", "ceil", "round", "trunc", "sign", "cbrt", "pow", "min", "max", "sum", "avg",
      "gcd", "lcm", "fact"
    )
    for (name in required) {
      assertTrue("الدالة $name مفقودة من السجل", MathFunctionRegistry.findFunction(name) != null)
    }
    assertTrue(MathFunctionRegistry.findConstant("pi") != null)
    assertTrue(MathFunctionRegistry.findConstant("e") != null)
    assertTrue(MathFunctionRegistry.findConstant("phi") != null)
  }

  @Test
  fun functions_multiArgumentAndVariadic() {
    assertEquals(1.0, value("min(3, 5, 1)"), 1e-9)
    assertEquals(7.333333333333333, value("avg(4, 8, 10)"), 1e-9)
    assertEquals(18.0, value("sum(4, 8, 6)"), 1e-9)
    assertEquals(6.0, value("gcd(12, 18)"), 1e-9)
    assertEquals(12.0, value("lcm(4, 6)"), 1e-9)
    assertEquals(1024.0, value("pow(2, 10)"), 1e-9)
  }

  @Test
  fun arabicFunctionAliases_work() {
    assertEquals(12.0, value("جذر 144"), 1e-9)
    assertEquals(5.0, value("مطلق(-5)"), 1e-9)
    assertEquals(2.0, value("لوغاريتم(100)"), 1e-9)
    assertEquals(3.141592653589793, value("باي"), 1e-9)
  }

  @Test
  fun indianAndPersianDigits_areNormalized() {
    assertEquals(110.0, value("25 * 4 + 10"), 1e-9)
    assertEquals(56.0, value("٥٦"), 1e-9)
    assertEquals(7.0, value("۷"), 1e-9)
    assertEquals(1.5, value("١.٥"), 1e-9)
  }

  @Test
  fun superscriptExponent_isUnderstood() {
    assertEquals(25.0, value("5²"), 1e-9)
  }

  // ——— حالات حدية ———
  @Test
  fun divisionByZero_isExplicit() {
    expectDivisionByZero("5 / 0")
    expectDivisionByZero("0 / 0")
    expectDivisionByZero("10 / (3 - 3)")
    expectDivisionByZero("15 % 0")
  }

  @Test
  fun negativeSquareRoot_isDomainError() {
    try {
      MathExpressionEngine.evaluate("sqrt(-16)")
      fail("كان يجب رفض الجذر السالب")
    } catch (e: MathDomainException) {
      assertTrue(e.message!!.contains("سالب"))
    }
  }

  @Test
  fun logarithmOfZero_isDomainError() {
    try {
      MathExpressionEngine.evaluate("ln(0)")
      fail("كان يجب رفض لوغاريتم الصفر")
    } catch (_: MathDomainException) {
    }
  }

  @Test
  fun overflow_isDetected() {
    try {
      MathExpressionEngine.evaluate("1E308 * 10")
      fail("كان يجب اكتشاف الطفحان")
    } catch (_: MathOverflowException) {
    }
  }

  @Test
  fun malformedExpressions_areSyntaxErrors() {
    val malformed = listOf("", "2 +", "* 5", "2 * / 3", "(2 + 3", "2 + 3)", "()", "2 + ")
    for (expression in malformed) {
      expectSyntax { MathExpressionEngine.evaluate(expression) }
    }
  }

  @Test
  fun unknownIdentifiersAndInjectionAttempts_areRejected() {
    val attacks = listOf(
      "process.exit(1)",
      "alert(1)",
      "Runtime.getRuntime()",
      "2 + abc * 3",
      "System.exit(0)",
      "eval(\"1+1\")",
      "this.constructor"
    )
    for (attack in attacks) {
      expectSyntax { MathExpressionEngine.evaluate(attack) }
    }
  }

  @Test
  fun operationTree_isProducedForAuditing() {
    val evaluation = MathExpressionEngine.evaluate("2 + 3 * 4")
    assertEquals("(+ 2 (* 3 4))", evaluation.operationTree)
  }

  // ——— أدوات مساعدة ———
  private fun value(expression: String): Double = MathExpressionEngine.evaluate(expression).value

  private fun expectSyntax(block: () -> Unit) {
    try {
      block()
      fail("كان يجب رفض التعبير")
    } catch (_: MathSyntaxException) {
    }
  }

  private fun expectDivisionByZero(expression: String) {
    try {
      MathExpressionEngine.evaluate(expression)
      fail("كان يجب رفض القسمة على صفر: $expression")
    } catch (_: MathDivisionByZeroException) {
    }
  }
}
