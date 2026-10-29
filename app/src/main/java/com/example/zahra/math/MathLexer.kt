package com.example.zahra.math

/** أنواع الرموز التي ينتجها [MathLexer]. */
enum class MathTokenType { NUMBER, IDENTIFIER, OPERATOR, LEFT_PAREN, RIGHT_PAREN, COMMA, FACTORIAL, END }

data class MathToken(
  val type: MathTokenType,
  val text: String,
  val position: Int,
  val numericValue: Double = 0.0
)

/**
 * محلّل معجمي (Lexer) حتمي لتعبيرات رياضية عربية/إنجليزية.
 *
 * ما يدعمه:
 * - أرقام هندية (٠-٩) وفارسية (۰-۹) ولاتينية، وفاصلة عشرية `٫` أو `.` أو `,`.
 * - علامات عربية: × ÷ − √ π، ونجوم متتالية `**` تُفهم كقوة.
 * - أسس مكتوبة بحروف علوية: `5²` ⇒ `5^2`.
 * - ضرب ضمني: `2(3+4)`، `3pi`، `2sqrt(9)` (يُدار في [MathParser]).
 *
 * ما يرفضه صراحةً (أمان + صدق):
 * - أي رمز غريب مثل `$`, `@`, `[`, `]`, `{`, `}`, `.` خارج رقم ⇒ [MathSyntaxException].
 * - مضروب مزدوج `!!` ⇒ [MathSyntaxException] برسالة صريحة.
 * - أي معرّف غير معروف (يُرفض في [MathParser] لأن المعجم لا يعرف السجلّ).
 */
object MathLexer {

  private const val EASTERN_ZERO = '٠' // ٠
  private const val PERSIAN_ZERO = '۰' // ۰
  private val SUPERSCRIPT_DIGITS = mapOf(
    '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
    '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9'
  )

  fun tokenize(input: String): List<MathToken> {
    val tokens = mutableListOf<MathToken>()
    var i = 0
    val length = input.length

    while (i < length) {
      val c = input[i]

      // مسافات وتطويل وعلامات اتجاه
      if (c.isWhitespace() || c == '\u200f' || c == '\u200e' || c == '\u0640') {
        i++
        continue
      }

      // الأرقام (لاتينية/هندية/فارسية)
      if (isDigitChar(c) || (c == '.' || c == '٫') && i + 1 < length && isDigitChar(input[i + 1])) {
        val start = i
        val builder = StringBuilder()
        var hasDot = false
        while (i < length) {
          val ch = input[i]
          if (isDigitChar(ch)) {
            builder.append(toAsciiDigit(ch))
            i++
          } else if ((ch == '.' || ch == '٫') && !hasDot && i + 1 < length && isDigitChar(input[i + 1])) {
            hasDot = true
            builder.append('.')
            i++
          } else if (ch == '٬') {
            // فاصل آلاف أو فاصلة بعد الفاصلة العشرية ⇒ تجاهل
            i++
          } else {
            break
          }
        }
        // الصيغة العلمية: 1e308 / 2.5E-3
        if (i < length && (input[i] == 'e' || input[i] == 'E')) {
          var lookahead = i + 1
          if (lookahead < length && (input[lookahead] == '+' || input[lookahead] == '-')) lookahead++
          var hasExponentDigits = false
          while (lookahead < length && input[lookahead].isDigit()) {
            hasExponentDigits = true
            lookahead++
          }
          if (hasExponentDigits) {
            builder.append('e')
            i++
            if (input[i] == '+' || input[i] == '-') {
              builder.append(input[i])
              i++
            }
            while (i < length && input[i].isDigit()) {
              builder.append(input[i])
              i++
            }
          }
        }
        val text = builder.toString()
        val value = text.toDoubleOrNull()
          ?: throw MathSyntaxException("رقم غير صالح في الموضع $start: \"$text\"")
        tokens.add(MathToken(MathTokenType.NUMBER, text, start, value))
        continue
      }

      // الحروف العلوية كأسس: 5²
      if (SUPERSCRIPT_DIGITS.containsKey(c)) {
        val start = i
        val digits = StringBuilder()
        while (i < length && SUPERSCRIPT_DIGITS.containsKey(input[i])) {
          digits.append(SUPERSCRIPT_DIGITS.getValue(input[i]))
          i++
        }
        tokens.add(MathToken(MathTokenType.OPERATOR, "^", start))
        tokens.add(MathToken(MathTokenType.NUMBER, digits.toString(), start, digits.toString().toDouble()))
        continue
      }

      // معاملات مكافئة
      when (c) {
        '×', '·', '⋅', '✕', '⨯' -> {
          tokens.add(MathToken(MathTokenType.OPERATOR, "*", i)); i++; continue
        }
        '÷', '∕', '⁄' -> {
          tokens.add(MathToken(MathTokenType.OPERATOR, "/", i)); i++; continue
        }
        '−', '–', '—' -> {
          tokens.add(MathToken(MathTokenType.OPERATOR, "-", i)); i++; continue
        }
        '√' -> {
          tokens.add(MathToken(MathTokenType.IDENTIFIER, "sqrt", i)); i++; continue
        }
        'π' -> {
          tokens.add(MathToken(MathTokenType.IDENTIFIER, "pi", i)); i++; continue
        }
        '*' -> {
          val start = i
          if (i + 1 < length && input[i + 1] == '*') {
            tokens.add(MathToken(MathTokenType.OPERATOR, "^", start))
            i += 2
          } else {
            tokens.add(MathToken(MathTokenType.OPERATOR, "*", start))
            i++
          }
          continue
        }
        '+', '-', '/', '^', '%' -> {
          tokens.add(MathToken(MathTokenType.OPERATOR, c.toString(), i)); i++; continue
        }
        '(' -> {
          tokens.add(MathToken(MathTokenType.LEFT_PAREN, "(", i)); i++; continue
        }
        ')' -> {
          tokens.add(MathToken(MathTokenType.RIGHT_PAREN, ")", i)); i++; continue
        }
        ',' -> {
          tokens.add(MathToken(MathTokenType.COMMA, ",", i)); i++; continue
        }
        '=' -> {
          // علامة يساوي في نهاية تعبير عربي (احسب 2+2 =) تُتجاهل
          i++
          continue
        }
        '?' , '؟' , '!' -> {
          if (c == '!') {
            if (i + 1 < length && input[i + 1] == '!') {
              throw MathSyntaxException("المضروب المزدوج (!!) غير مدعوم. استخدم fact(n) أو عاملي واحدًا فقط.")
            }
            tokens.add(MathToken(MathTokenType.FACTORIAL, "!", i)); i++; continue
          }
          // علامات استفهام عربية/إنجليزية في نهاية التعبير تُتجاهل
          i++
          continue
        }
      }

      // المعرّفات (دوال/ثوابت عربية وإنجليزية)
      if (c.isLetter() || c == '_') {
        val start = i
        while (i < length && (input[i].isLetter() || input[i] == '_' || input[i].isDigit())) i++
        tokens.add(MathToken(MathTokenType.IDENTIFIER, input.substring(start, i), start))
        continue
      }

      throw MathSyntaxException("رمز غير مدعوم: '$c' في الموضع $i")
    }

    tokens.add(MathToken(MathTokenType.END, "", length))
    return tokens
  }

  fun normalizeDigits(input: String): String {
    val builder = StringBuilder(input.length)
    for (ch in input) {
      builder.append(
        when {
          ch in '٠'..'٩' -> ('0' + (ch - '٠'))
          ch in '۰'..'۹' -> ('0' + (ch - '۰'))
          else -> ch
        }
      )
    }
    return builder.toString()
  }

  private fun isDigitChar(c: Char): Boolean =
    c in '0'..'9' || c in '٠'..'٩' || c in '۰'..'۹'

  private fun toAsciiDigit(c: Char): Char = when {
    c in '٠'..'٩' -> ('0' + (c - '٠'))
    c in '۰'..'۹' -> ('0' + (c - '۰'))
    else -> c
  }
}
