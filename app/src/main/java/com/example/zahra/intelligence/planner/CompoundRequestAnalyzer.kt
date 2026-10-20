package com.example.zahra.intelligence.planner

data class SubActionCandidate(
  val order: Int,
  val rawClause: String,
  val refersToPreviousOutput: Boolean,
  val referencedArtifact: String? = null // "result", "summary", "translation", "knowledge"
)

object CompoundRequestAnalyzer {

  // Strong multi-action split conjunctions
  private val EXPLICIT_SPLITTERS = listOf(
    Regex("""\s+(?:ثم|ومن ثم|وبعدين|وبعد ذلك|بعد كدة)\s+""", RegexOption.IGNORE_CASE),
    Regex("""\s+(?:and then|then|after that)\s+""", RegexOption.IGNORE_CASE)
  )

  // Coordinated "و" followed immediately by or attached to an imperative action verb
  private val COORDINATED_ACTION_PATTERN = Regex(
    """\s+(?:و\s+|و(?=(?:أضف|اضف|سجل|احفظ|فكرني|ذكرني|جدول|لخص|ترجم|احسب|حول|صمم|ارسم|اعرض|ضع|حط)(?![\\p{L}\\p{N}])))""",
    RegexOption.IGNORE_CASE
  )

  private val ENGLISH_AND_ACTION_PATTERN = Regex(
    """\s+and\s+(?=(?:add|save|remind|schedule|summarize|translate|calc|calculate|convert)\b)""",
    RegexOption.IGNORE_CASE
  )

  private val RESULT_ANAPHORA_PATTERN = Regex(
    """(?<![\\p{L}\\p{N}])(?:الناتج|الحل|الملخص|الترجمة|المعلومة|النتيجة|ذلك|هذا|إياه|إياها|result|summary|translation|it|that)(?![\\p{L}\\p{N}])""",
    RegexOption.IGNORE_CASE
  )

  /**
   * Split a request into sub-actions while strictly preserving quoted content.
   */
  fun analyze(input: String): List<SubActionCandidate> {
    val clean = input.trim()
    if (clean.isBlank()) return emptyList()

    // 1. Mask quoted segments so that internal conjunctions aren't split
    val placeholders = mutableListOf<String>()
    val quoteRegex = Regex("""["'«][^"'»]+["'»]""")
    val masked = quoteRegex.replace(clean) { match ->
      val idx = placeholders.size
      placeholders.add(match.value)
      "__QUOTE_HOLDER_${idx}__"
    }

    // 2. Identify candidate splits
    var clauses = listOf(masked)
    for (splitter in EXPLICIT_SPLITTERS) {
      val newClauses = mutableListOf<String>()
      for (cl in clauses) {
        val parts = cl.split(splitter).map { it.trim() }.filter { it.isNotBlank() }
        newClauses.addAll(parts)
      }
      clauses = newClauses
    }

    // 3. Check for coordinated "و" + imperative verb if no explicit split happened
    if (clauses.size == 1) {
      val parts = masked.split(COORDINATED_ACTION_PATTERN).map { it.trim() }.filter { it.isNotBlank() }
      if (parts.size > 1) {
        clauses = parts
      } else {
        val engParts = masked.split(ENGLISH_AND_ACTION_PATTERN).map { it.trim() }.filter { it.isNotBlank() }
        if (engParts.size > 1) {
          clauses = engParts
        }
      }
    }

    // 4. Restore placeholders and build candidates
    val candidates = mutableListOf<SubActionCandidate>()
    for ((idx, rawPart) in clauses.withIndex()) {
      var unmasked = rawPart
      for (pIdx in placeholders.indices) {
        unmasked = unmasked.replace("__QUOTE_HOLDER_${pIdx}__", placeholders[pIdx])
      }
      val refers = idx > 0 && RESULT_ANAPHORA_PATTERN.containsMatchIn(unmasked)
      val artifactType = when {
        unmasked.contains("ملخص") || unmasked.contains("summary") -> "summary"
        unmasked.contains("ترجم") || unmasked.contains("translation") -> "translation"
        unmasked.contains("ناتج") || unmasked.contains("حل") || unmasked.contains("نتيجة") || unmasked.contains("result") -> "result"
        unmasked.contains("معلومة") || unmasked.contains("حقيقة") -> "knowledge"
        refers -> "output"
        else -> null
      }

      candidates.add(
        SubActionCandidate(
          order = idx + 1,
          rawClause = unmasked.trim(),
          refersToPreviousOutput = refers,
          referencedArtifact = artifactType
        )
      )
    }

    return candidates
  }
}
