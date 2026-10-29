package com.example.zahra.math

/** طلب تحويل مستخرج من نص حر. */
data class UnitRequest(
  val value: Double,
  val from: UnitDefinition,
  val to: UnitDefinition,
  val explicitValue: Boolean,
  val rawText: String
)

/** نتيجة استخراج طلب التحويل — الحالات السلبية صريحة، وليست `null` غامضًا. */
sealed class UnitRequestParseResult {
  data class Parsed(val request: UnitRequest) : UnitRequestParseResult()

  /** ذُكرت وحدة، ولم تُذكر وحدة الهدف. */
  data class MissingTarget(val from: UnitDefinition) : UnitRequestParseResult()

  /** الوحدة الهدف مذكورة لكنها غير معروفة للمحرّك. */
  data class UnknownUnit(val token: String, val from: UnitDefinition?) : UnitRequestParseResult()

  /** لا توجد أي وحدة معروفة في النص. */
  data object NoUnit : UnitRequestParseResult()
}

/**
 * يستخرج (القيمة، الوحدة المصدر، الوحدة الهدف) من جملة عربية/إنجليزية.
 *
 * الصيغ المدعومة:
 * - `حوّل 10 كيلومتر إلى ميل`
 * - `500 جرام كام كيلوجرام؟`
 * - `كم متر في الكيلومتر` (ترتيب معكوس: الهدف أولًا)
 * - `3 فدان إلى متر مربع`
 * - `10 km to m`
 * - `الفدان كام متر مربع` (بدون رقم ⇒ القيمة 1)
 */
object UnitRequestParser {

  private val QUANTITY_WORDS = setOf("كام", "كم", "قد", "ايه", "إيه", "هو", "هي", "بكام")
  private val SEPARATORS = setOf("الى", "الي", "to", "into", "=", "فى", "في")
  private val STOP_WORDS = setOf("كام", "كم", "قد", "ايه", "هو", "هي", "بس", "يا", "من", "عايز", "عاوز", "احسب", "حول", "حو")

  private data class Mention(val start: Int, val end: Int, val unit: UnitDefinition)

  fun parse(rawInput: String): UnitRequestParseResult {
    val normalized = UnitSystem.normalizeKey(rawInput)
    val words = tokenize(normalized)
    if (words.isEmpty()) return UnitRequestParseResult.NoUnit

    val mentions = findMentions(words)
    if (mentions.isEmpty()) return UnitRequestParseResult.NoUnit

    val numbers = words.withIndex().mapNotNull { (index, word) ->
      val numeric = parseNumber(word)
      if (numeric != null) index to numeric else null
    }

    fun numberBefore(index: Int): Double? = numbers.lastOrNull { it.first < index }?.second

    // الحالة الأولى: وحدتان أو أكثر
    if (mentions.size >= 2) {
      val first = mentions[0]
      val second = mentions[1]
      val reversed = isReversedQuestion(words, first, second)

      val fromMention = if (reversed) second else first
      val toMention = if (reversed) first else second

      val explicitValue = numberBefore(fromMention.start)
      return UnitRequestParseResult.Parsed(
        UnitRequest(
          value = explicitValue ?: 1.0,
          from = fromMention.unit,
          to = toMention.unit,
          explicitValue = explicitValue != null,
          rawText = rawInput
        )
      )
    }

    // الحالة الثانية: وحدة واحدة
    val single = mentions[0]
    val separatorIndex = words.indexOfFirst { it in SEPARATORS && it.isNotBlank() }
    if (separatorIndex > single.end) {
      val candidate = words.drop(separatorIndex + 1)
        .firstOrNull { it.isNotBlank() && it !in STOP_WORDS && it !in QUANTITY_WORDS && parseNumber(it) == null }
      if (candidate != null) {
        return UnitRequestParseResult.UnknownUnit(candidate, single.unit)
      }
    }
    return UnitRequestParseResult.MissingTarget(single.unit)
  }

  /** «كم متر في الكيلومتر» ⇒ الوحدة الأولى هي المطلوب حسابه، والمصدر هو الثانية. */
  private fun isReversedQuestion(words: List<String>, first: Mention, second: Mention): Boolean {
    val quantityIndex = words.indexOfFirst { it in QUANTITY_WORDS }
    if (quantityIndex < 0 || quantityIndex >= first.start) return false
    val connectorBetween = (first.end + 1 until second.start).any { words.getOrNull(it) in setOf("فى", "في", "من", "لكل") }
    return connectorBetween
  }

  private fun findMentions(words: List<String>): List<Mention> {
    val mentions = mutableListOf<Mention>()
    var i = 0
    while (i < words.size) {
      val maxSpan = minOf(3, words.size - i)
      var matched: Mention? = null
      for (span in maxSpan downTo 1) {
        val candidate = words.subList(i, i + span)
        if (candidate.any { it.isBlank() || parseNumber(it) != null }) continue
        val compact = candidate.joinToString("").replace(" ", "")
        val unit = lookupCompact(compact)
        if (unit != null) {
          matched = Mention(i, i + span - 1, unit)
          break
        }
      }
      if (matched != null) {
        mentions.add(matched)
        i = matched.end + 1
      } else {
        i++
      }
    }
    return mentions
  }

  private val compactLookup: Map<String, UnitDefinition> by lazy {
    buildMap {
      for ((alias, unit) in UnitSystem.aliasesByLength) {
        val compact = alias.replace(" ", "")
        if (compact.isNotBlank() && !containsKey(compact)) put(compact, unit)
      }
    }
  }

  private fun lookupCompact(compact: String): UnitDefinition? = compactLookup[compact]

  private fun tokenize(normalized: String): List<String> {
    val builder = StringBuilder()
    for (ch in normalized) {
      val keep = ch.isLetterOrDigit() || ch == '.' || ch == '/' || ch == '%' || ch == '-'
      builder.append(if (keep) ch else ' ')
    }
    return builder.toString().split(Regex("\\s+")).filter { it.isNotBlank() }
  }

  private fun parseNumber(word: String): Double? {
    val cleaned = word.replace("٫", ".").trim('.', '-')
    if (cleaned.isEmpty()) return null
    if (!cleaned.all { it.isDigit() || it == '.' }) return null
    return cleaned.toDoubleOrNull()
  }
}
