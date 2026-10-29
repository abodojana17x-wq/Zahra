package com.example.zahra.math

/**
 * محلّل نحوي تنازلي (Pratt parser) آمن لتعبيرات رياضية.
 *
 * الأولويات: `+/-` = 10، `* / %` والضرب الضمني = 20، السالب الأحادي والدوال البادئة = 35،
 * `^` = 40 (يمينية الترابط)، `!` = 50 (لاحقة). النتيجة: `-2^2 = -4` و`2^3^2 = 512`.
 *
 * الضرب الضمني مدعوم: `2(3+4)` و`3pi` و`2sqrt(9)`.
 * أي معرّف غير موجود في [MathFunctionRegistry] يُرفض ولا يُنفَّذ.
 */
class MathParser(private val tokens: List<MathToken>) {

  private var index = 0

  private companion object {
    const val ADDITIVE_BP = 10
    const val MULTIPLICATIVE_BP = 20
    const val UNARY_BP = 35
    const val POWER_BP = 40
    const val FACTORIAL_BP = 50
  }

  fun parse(): MathExpr {
    if (peek().type == MathTokenType.END) {
      throw MathSyntaxException("لا توجد حدود رياضية للمعالجة.")
    }
    val expression = parseExpression(0)
    val trailing = peek()
    if (trailing.type != MathTokenType.END) {
      throw when (trailing.type) {
        MathTokenType.RIGHT_PAREN -> MathSyntaxException("أقواس غير متطابقة: قوس مغلق بلا فتح مقابل.")
        MathTokenType.OPERATOR -> MathSyntaxException("عامل تشغيل في غير موضعه: \"${trailing.text}\".")
        else -> MathSyntaxException("جزء غير مفهوم بعد التعبير: \"${trailing.text}\".")
      }
    }
    return expression
  }

  private fun parseExpression(minBindingPower: Int): MathExpr {
    var left = parsePrefix()

    while (true) {
      val token = peek()

      // المضروب اللاحق
      if (token.type == MathTokenType.FACTORIAL) {
        if (FACTORIAL_BP <= minBindingPower) break
        advance()
        left = UnaryExpr(UnaryOperator.FACTORIAL, left)
        continue
      }

      // معامل ثنائي صريح
      if (token.type == MathTokenType.OPERATOR) {
        val bindingPower = bindingPowerOf(token.text) ?: throw MathSyntaxException("عامل تشغيل غير معروف: \"${token.text}\".")
        if (bindingPower <= minBindingPower) break
        advance()
        val rightBindingPower = if (token.text == "^") bindingPower - 1 else bindingPower
        val right = parseExpression(rightBindingPower)
        left = BinaryExpr(operatorOf(token.text), left, right)
        continue
      }

      // ضرب ضمني: 2(3+4) / 3pi / 2sqrt(9)
      if (startsOperand(token)) {
        if (MULTIPLICATIVE_BP <= minBindingPower) break
        val right = parseExpression(MULTIPLICATIVE_BP)
        left = BinaryExpr(BinaryOperator.MULTIPLY, left, right)
        continue
      }

      break
    }

    return left
  }

  private fun parsePrefix(): MathExpr {
    val token = advance()
    return when (token.type) {
      MathTokenType.NUMBER -> NumberExpr(token.numericValue)

      MathTokenType.IDENTIFIER -> parseIdentifier(token)

      MathTokenType.LEFT_PAREN -> {
        if (peek().type == MathTokenType.RIGHT_PAREN) {
          throw MathSyntaxException("أقواس فارغة لا تحتوي تعبيرًا.")
        }
        val inner = parseExpression(0)
        val closing = advance()
        if (closing.type != MathTokenType.RIGHT_PAREN) {
          throw MathSyntaxException("أقواس غير متطابقة: قوس مفتوح غير مغلق.")
        }
        inner
      }

      MathTokenType.OPERATOR -> when (token.text) {
        "-" -> UnaryExpr(UnaryOperator.NEGATE, parseExpression(UNARY_BP))
        "+" -> parseExpression(UNARY_BP)
        else -> throw MathSyntaxException("عامل التشغيل \"${token.text}\" يحتاج معاملًا قبله.")
      }

      MathTokenType.RIGHT_PAREN -> throw MathSyntaxException("قوس مغلق في غير موضعه أو معامل ناقص.")
      MathTokenType.COMMA -> throw MathSyntaxException("فاصلة في غير موضعها داخل التعبير.")
      MathTokenType.FACTORIAL -> throw MathSyntaxException("المضروب (!) يحتاج عددًا قبله.")
      MathTokenType.END -> throw MathSyntaxException("التعبير ينتهي بعامل تشغيل دون معامل أخير.")
    }
  }

  private fun parseIdentifier(token: MathToken): MathExpr {
    val name = token.text

    // استدعاء بدوال بين قوسين: min(3, 5)
    if (peek().type == MathTokenType.LEFT_PAREN) {
      val spec = MathFunctionRegistry.findFunction(name)
        ?: throw MathSyntaxException("معرّف غير معروف: \"$name\". المحرّك لا ينفّذ إلا الدوال المعرّفة.")
      advance() // (
      val arguments = mutableListOf<MathExpr>()
      if (peek().type != MathTokenType.RIGHT_PAREN) {
        while (true) {
          arguments.add(parseExpression(0))
          if (peek().type == MathTokenType.COMMA) {
            advance()
            continue
          }
          break
        }
      }
      val closing = advance()
      if (closing.type != MathTokenType.RIGHT_PAREN) {
        throw MathSyntaxException("قوس غير مغلق بعد وسائط الدالة $name.")
      }
      validateArity(spec, arguments.size)
      return CallExpr(spec.name, arguments)
    }

    val constant = MathFunctionRegistry.findConstant(name)
    if (constant != null) return ConstantExpr(name, constant)

    val spec = MathFunctionRegistry.findFunction(name)
      ?: throw MathSyntaxException("معرّف غير معروف: \"$name\". المحرّك لا ينفّذ إلا الدوال والثوابت المعرّفة.")
    if (spec.maxArguments != 1 && spec.minArguments > 1) {
      throw MathSyntaxException("الدالة ${spec.name} تحتاج ${spec.minArguments} وسائط وتُكتب بين قوسين.")
    }
    val operand = parseExpression(UNARY_BP)
    return CallExpr(spec.name, listOf(operand))
  }

  private fun validateArity(spec: MathFunctionSpec, argumentCount: Int) {
    if (argumentCount < spec.minArguments) {
      throw MathSyntaxException("الدالة ${spec.name} تحتاج ${spec.minArguments} وسيطًا على الأقل، ووُجد $argumentCount.")
    }
    if (spec.maxArguments >= 0 && argumentCount > spec.maxArguments) {
      throw MathSyntaxException("الدالة ${spec.name} تقبل ${spec.maxArguments} وسيطًا كحد أقصى، ووُجد $argumentCount.")
    }
  }

  private fun startsOperand(token: MathToken): Boolean = when (token.type) {
    MathTokenType.NUMBER, MathTokenType.IDENTIFIER, MathTokenType.LEFT_PAREN -> true
    else -> false
  }

  private fun bindingPowerOf(operatorText: String): Int? = when (operatorText) {
    "+", "-" -> ADDITIVE_BP
    "*", "/", "%" -> MULTIPLICATIVE_BP
    "^" -> POWER_BP
    else -> null
  }

  private fun operatorOf(operatorText: String): BinaryOperator = when (operatorText) {
    "+" -> BinaryOperator.ADD
    "-" -> BinaryOperator.SUBTRACT
    "*" -> BinaryOperator.MULTIPLY
    "/" -> BinaryOperator.DIVIDE
    "%" -> BinaryOperator.MODULO
    "^" -> BinaryOperator.POWER
    else -> throw MathSyntaxException("عامل تشغيل غير معروف: \"$operatorText\".")
  }

  private fun peek(): MathToken = tokens[index]

  private fun advance(): MathToken {
    val token = tokens[index]
    if (index < tokens.size - 1) index++
    return token
  }
}

/** تقييم شجرة العمليات بعد التحقق من النطاقات (لا يفترض أي مدخل صالح). */
object MathEvaluator {

  private const val ZERO_TOLERANCE = 1e-15

  fun evaluate(expression: MathExpr): Double {
    val value = when (expression) {
      is NumberExpr -> expression.value
      is ConstantExpr -> expression.value
      is UnaryExpr -> when (expression.operator) {
        UnaryOperator.NEGATE -> -evaluate(expression.operand)
        UnaryOperator.FACTORIAL -> MathFunctionRegistry.factorialOf(evaluate(expression.operand))
      }
      is BinaryExpr -> evaluateBinary(expression)
      is CallExpr -> {
        val spec = MathFunctionRegistry.findFunction(expression.name)
          ?: throw MathSyntaxException("معرّف غير معروف: \"${expression.name}\".")
        val args = expression.arguments.map { evaluate(it) }
        spec.invoke(args)
      }
    }
    if (value.isNaN()) {
      throw MathDomainException("نتيجة غير معرّفة (NaN) — تأكد من نطاق الدوال المستخدمة.")
    }
    return value
  }

  private fun evaluateBinary(expression: BinaryExpr): Double {
    val left = evaluate(expression.left)
    val right = evaluate(expression.right)
    val value = when (expression.operator) {
      BinaryOperator.ADD -> left + right
      BinaryOperator.SUBTRACT -> left - right
      BinaryOperator.MULTIPLY -> left * right
      BinaryOperator.DIVIDE -> {
        if (kotlin.math.abs(right) < ZERO_TOLERANCE) {
          throw MathDivisionByZeroException("خطأ رياضي: القسمة على صفر غير معرفة (Division by zero).")
        }
        left / right
      }
      BinaryOperator.MODULO -> {
        if (kotlin.math.abs(right) < ZERO_TOLERANCE) {
          throw MathDivisionByZeroException("خطأ رياضي: حساب باقي القسمة على صفر غير معرف.")
        }
        left % right
      }
      BinaryOperator.POWER -> {
        if (left < 0 && kotlin.math.abs(right - kotlin.math.round(right)) > 1e-9) {
          throw MathDomainException("رفع عدد سالب لأس كسري غير معرّف في الأعداد الحقيقية.")
        }
        Math.pow(left, right)
      }
    }
    if (value.isInfinite()) {
      throw MathOverflowException("نتيجة العملية تجاوزت نطاق الأعداد المسموح به.")
    }
    return value
  }
}

/**
 * الواجهة العامة لمحرّك التعبيرات: تحليل + تقييم + أثر.
 * لا يستخدم أي `eval` أو `ScriptEngine` أو انعكاس.
 */
object MathExpressionEngine {

  fun evaluate(input: String): MathEvaluation {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
      throw MathSyntaxException("لا توجد حدود رياضية للمعالجة.")
    }
    val tokens = MathLexer.tokenize(trimmed)
    val tree = MathParser(tokens).parse()
    val value = MathEvaluator.evaluate(tree)
    if (value.isInfinite()) {
      throw MathOverflowException("نتيجة التعبير تجاوزت نطاق الأعداد المسموح به.")
    }
    return MathEvaluation(
      value = value,
      canonicalExpression = tree.toInfixString(),
      operationTree = tree.toPrefixString()
    )
  }

  /** يعيد شجرة العمليات فقط (للتصحيح والقياس) أو يرمي استثناءً نحويًا. */
  fun operationTreeOf(input: String): String =
    MathParser(MathLexer.tokenize(input.trim())).parse().toPrefixString()
}
