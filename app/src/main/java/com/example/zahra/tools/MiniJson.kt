package com.example.zahra.tools

/**
 * قارئ JSON بسيط بلا اعتماديات (بديل خفيف عن مكتبة كاملة داخل أدوات البيانات).
 *
 * لماذا؟ الأدوات الشبكية (طقس/ويكيبيديا/ويكيداتا) تحتاج قراءة استجابات صغيرة فقط،
 * والاعتماد الكامل على `org.json` يجعل منطق القراءة غير قابل للاختبار خارج أندرويد.
 * هذا القارئ نقيّ (Pure Kotlin)، يُختبر محليًا، ولا يستخدم `eval` ولا انعكاسًا.
 *
 * القيود المعلنة: يقبل JSON صحيحًا فقط (RFC 8259) بلا تعليقات ولا فواصل زائدة،
 * ويحوّل كل الأرقام إلى `Double` (يكفي لحرارة/إحداثيات؛ لا يُستخدم لمبالغ مالية).
 */
sealed class JsonValue {
  data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
  data class Arr(val items: List<JsonValue>) : JsonValue()
  data class Str(val value: String) : JsonValue()
  data class Num(val value: Double) : JsonValue()
  data class Bool(val value: Boolean) : JsonValue()
  data object Null : JsonValue()

  fun field(key: String): JsonValue? = (this as? Obj)?.fields?.get(key)

  fun stringOrNull(key: String): String? = (field(key) as? Str)?.value

  fun numberOrNull(key: String): Double? = (field(key) as? Num)?.value

  fun arrayOrNull(key: String): List<JsonValue>? = (field(key) as? Arr)?.items

  fun at(index: Int): JsonValue? = (this as? Arr)?.items?.getOrNull(index)

  /** مسار نصي: `dig("current", "temperature_2m")`. */
  fun dig(vararg path: String): JsonValue? {
    var current: JsonValue? = this
    for (segment in path) {
      current = current?.field(segment) ?: return null
    }
    return current
  }
}

class JsonParseException(message: String) : Exception(message)

object MiniJson {

  fun parse(text: String): JsonValue {
    val parser = Parser(text)
    parser.skipWhitespace()
    val value = parser.parseValue()
    parser.skipWhitespace()
    if (!parser.isAtEnd()) {
      throw JsonParseException("نص JSON يحتوي بيانات زائدة بعد القيمة الأساسية (الموضع ${parser.position})")
    }
    return value
  }

  fun parseOrNull(text: String): JsonValue? = try {
    parse(text)
  } catch (_: JsonParseException) {
    null
  }

  private class Parser(private val source: String) {
    var position = 0
      private set

    fun isAtEnd(): Boolean = position >= source.length

    fun skipWhitespace() {
      while (position < source.length && source[position].isWhitespace()) position++
    }

    fun parseValue(): JsonValue {
      skipWhitespace()
      if (isAtEnd()) throw JsonParseException("نص JSON منتهٍ بشكل غير متوقع")
      return when (val c = source[position]) {
        '{' -> parseObject()
        '[' -> parseArray()
        '"' -> JsonValue.Str(parseString())
        't', 'f' -> parseBoolean()
        'n' -> parseNull()
        else -> {
          if (c == '-' || c.isDigit()) parseNumber()
          else throw JsonParseException("رمز غير متوقع '$c' في الموضع $position")
        }
      }
    }

    private fun parseObject(): JsonValue.Obj {
      expect('{')
      val fields = LinkedHashMap<String, JsonValue>()
      skipWhitespace()
      if (peek() == '}') {
        position++
        return JsonValue.Obj(fields)
      }
      while (true) {
        skipWhitespace()
        val key = parseString()
        skipWhitespace()
        expect(':')
        fields[key] = parseValue()
        skipWhitespace()
        when (peek()) {
          ',' -> {
            position++
            continue
          }
          '}' -> {
            position++
            return JsonValue.Obj(fields)
          }
          else -> throw JsonParseException("متوقع ',' أو '}' في الموضع $position")
        }
      }
    }

    private fun parseArray(): JsonValue.Arr {
      expect('[')
      val items = mutableListOf<JsonValue>()
      skipWhitespace()
      if (peek() == ']') {
        position++
        return JsonValue.Arr(items)
      }
      while (true) {
        items.add(parseValue())
        skipWhitespace()
        when (peek()) {
          ',' -> {
            position++
            continue
          }
          ']' -> {
            position++
            return JsonValue.Arr(items)
          }
          else -> throw JsonParseException("متوقع ',' أو ']' في الموضع $position")
        }
      }
    }

    private fun parseString(): String {
      expect('"')
      val builder = StringBuilder()
      while (true) {
        if (isAtEnd()) throw JsonParseException("سلسلة نصية غير مغلقة")
        val c = source[position++]
        when (c) {
          '"' -> return builder.toString()
          '\\' -> {
            if (isAtEnd()) throw JsonParseException("هروب غير مكتمل")
            when (val escaped = source[position++]) {
              '"' -> builder.append('"')
              '\\' -> builder.append('\\')
              '/' -> builder.append('/')
              'b' -> builder.append('\b')
              'f' -> builder.append('\u000C')
              'n' -> builder.append('\n')
              'r' -> builder.append('\r')
              't' -> builder.append('\t')
              'u' -> {
                if (position + 4 > source.length) throw JsonParseException("ترميز يونيكود ناقص")
                val hex = source.substring(position, position + 4)
                position += 4
                val code = hex.toIntOrNull(16) ?: throw JsonParseException("ترميز يونيكود غير صالح: $hex")
                builder.append(code.toChar())
              }
              else -> throw JsonParseException("هروب غير معروف: \\$escaped")
            }
          }
          else -> builder.append(c)
        }
      }
    }

    private fun parseNumber(): JsonValue.Num {
      val start = position
      if (peek() == '-') position++
      while (!isAtEnd() && (source[position].isDigit() || source[position] in ".eE+-")) position++
      val literal = source.substring(start, position)
      val value = literal.toDoubleOrNull() ?: throw JsonParseException("رقم غير صالح: $literal")
      return JsonValue.Num(value)
    }

    private fun parseBoolean(): JsonValue.Bool {
      return when {
        source.startsWith("true", position) -> {
          position += 4
          JsonValue.Bool(true)
        }
        source.startsWith("false", position) -> {
          position += 5
          JsonValue.Bool(false)
        }
        else -> throw JsonParseException("قيمة منطقية غير صالحة في الموضع $position")
      }
    }

    private fun parseNull(): JsonValue.Null {
      if (!source.startsWith("null", position)) throw JsonParseException("قيمة null غير صالحة في الموضع $position")
      position += 4
      return JsonValue.Null
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[position]

    private fun expect(expected: Char) {
      if (isAtEnd() || source[position] != expected) {
        throw JsonParseException("متوقع '$expected' في الموضع $position")
      }
      position++
    }
  }
}
