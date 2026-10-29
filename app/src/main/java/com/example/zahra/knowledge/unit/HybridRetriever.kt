package com.example.zahra.knowledge.unit

import com.example.zahra.knowledge.SemanticSearchEngine
import com.example.zahra.routing.ConfidenceGatePolicy
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * إشارة التشابه الدلالي TF-IDF — واجهة قابلة للحقن حتى يبقى الاسترجاع الهجين قابلًا للاختبار
 * بلا محرك حقيقي، وتظل إشارة `SemanticSearchEngine` القائم واحدة من ثلاث إشارات لا النظام كله (المسار C.3).
 */
fun interface TfIdfSignal {
  /** درجات في [0,1] لكل معرّف وحدة؛ الوحدات الغائبة من الخريطة تُعامل بصفر. */
  fun scores(query: String, units: List<KnowledgeUnit>): Map<String, Double>
}

/** إشارة صفرية صريحة (للتشخيص وللاختبارات ولمقارنة الأثر). */
object NoTfIdfSignal : TfIdfSignal {
  override fun scores(query: String, units: List<KnowledgeUnit>): Map<String, Double> = emptyMap()
}

/**
 * يُعيد استخدام `SemanticSearchEngine` القائم (TF-IDF + cosine + مرادفات) **كإشارة واحدة**.
 * لا يعدّل سلوكه ولا عتبته الداخلية (0.1)، ويقصّ النتيجة إلى [0,1].
 */
class SemanticEngineSignal : TfIdfSignal {

  private var engine: SemanticSearchEngine? = null
  private var builtFor: List<KnowledgeUnit>? = null

  @Synchronized
  override fun scores(query: String, units: List<KnowledgeUnit>): Map<String, Double> {
    if (units.isEmpty() || query.isBlank()) return emptyMap()
    if (engine == null || builtFor !== units) {
      val fresh = SemanticSearchEngine()
      units.forEach { unit ->
        fresh.addDocument(
          SemanticSearchEngine.SearchDocument(
            id = unit.id,
            title = unit.title,
            content = unit.tfIdfText(),
            category = unit.pack,
            keywords = unit.tags
          )
        )
      }
      engine = fresh
      builtFor = units
    }
    val results = engine!!.search(query, topK = units.size)
    if (results.isEmpty()) return emptyMap()
    val out = HashMap<String, Double>(results.size * 2)
    for (r in results) {
      out[r.document.id] = min(1.0, max(0.0, r.score))
    }
    return out
  }
}

/** تفصيل الدرجة لكل نتيجة — شرط «مخرَج قابل للتفسير» في المسار C.6. */
data class ScoreBreakdown(
  val bm25Raw: Double,
  val bm25Normalized: Double,
  val charJaccard: Double,
  val phraseScore: Double,
  val tfidfCosine: Double,
  /** الدمج المطلق قبل النسبة إلى أفضل مرشح. */
  val fusedAbsolute: Double,
  /** نسبة الدمج المطلق إلى أفضل مرشح في نفس الاستعلام (شكل `baseScore` القديم). */
  val relative: Double,
  /** تغطية رموز الاستعلام الجوهرية (المطابقة التامة + نصف وزن للمطابقة التقريبية). */
  val coverage: Double,
  val weightMultiplier: Double,
  /** الدرجة النهائية بعد معامل التغطية ووزن الوحدة. */
  val fused: Double
) {
  fun explain(): String = buildString {
    append("bm25+=").append(fmt(bm25Raw))
    append(" (norm ").append(fmt(bm25Normalized)).append(")")
    append(" · حروف=").append(fmt(charJaccard))
    if (phraseScore > 0.0) append(" · عبارة=").append(fmt(phraseScore))
    append(" · tfidf=").append(fmt(tfidfCosine))
    append(" · نسبي=").append(fmt(relative))
    append(" · تغطية=").append(fmt(coverage))
    if (weightMultiplier != 1.0) append(" · وزن=").append(fmt(weightMultiplier))
    append(" ⇒ ").append(fmt(fused))
  }

  private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.3f", v)
}

/** نتيجة وحدة واحدة مع تفسير كامل. */
data class UnitMatch(
  val unit: KnowledgeUnit,
  val score: Double,
  /** رموز الاستعلام التي طابقت هذه الوحدة (بعد التجذيع). */
  val matchedTerms: List<String>,
  /** مطابقات تقريبية (خطأ إملائي) بالشكل `query≈term` مع نسبة تشابه الحروف. */
  val fuzzyMatches: List<String>,
  /** فهرس أفضل سؤال مطابق داخل الوحدة، أو -1. */
  val matchedQuestionIndex: Int,
  val breakdown: ScoreBreakdown
) {
  val hasLexicalSupport: Boolean get() = matchedTerms.isNotEmpty() || fuzzyMatches.isNotEmpty()
}

/** نتيجة استعلام هجين كاملة. */
data class HybridSearchResult(
  val query: String,
  val queryTokens: List<String>,
  val matches: List<UnitMatch>,
  val warnings: List<String> = emptyList()
) {
  val best: UnitMatch? get() = matches.firstOrNull()
}

/**
 * المسترجع الهجين (المرحلة 3 · المسار C).
 *
 * `score = w1*bm25Norm + w2*max(charJaccard, phrase) + w3*tfidf` ثم يُضرب في وزن الوحدة ويُقص إلى [0,1].
 *
 * **BM25+ (Lv & Zhai, CIKM 2011):**
 * ```
 * idf(t)    = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5))
 * tfNorm(t) = tf(t)*(k1+1) / (tf(t) + k1*(1 - b + b*dl/avgdl))
 * score(D)  = Σ_t idf(t) * (tfNorm(t) + δ)
 * ```
 * مع `k1=1.2, b=0.75, δ=1.0` (قيم الورقة الافتراضية) وأوزان حقول مدموجة في `tf` زمن البناء:
 * سؤال×3، عنوان×2، وسوم×2، إجابة×1.
 *
 * **تطبيع الإشارة:** `bm25Norm = raw/(raw + bm25ref)` حيث `bm25ref` وسيط درجة مطابقة الوحدات لأسئلتها
 * الأساسية (محسوب زمن البناء) — لا ثابت مكتوب يدويًا (D24).
 *
 * **تطبيع الدرجة النهائية (D25):** بعد الدمج المطلق `fused = w1*bm25Norm + w2*max(jaccard, phrase) + w3*tfidf`
 * تُنسَب الدرجة إلى أفضل مرشح في نفس الاستعلام، ثم تُضرب في معامل تغطية الرموز — نفس الشكل التحليلي
 * المستخدم في `OfflineKnowledgeIndex` (`baseScore × (0.35 + 0.65·coverage)` مع سقف 0.25 عند تغطية < 40%
 * لاستعلام ≥ 3 رموز). الهدف: تبقى عتبة `ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE` (0.50) بمعناها القديم
 * بدل أن تصبح رقمًا جديدًا مقارنًا بمقياس مختلف.
 *
 * **التقريب الإملائي:** لأي رمز استعلام لا يوجد في المفردات، نبحث عن أقرب رمز بمجموعات الحروف
 * بحد تشابه [FUZZY_MIN_GRAM_SIMILARITY] وخصم [FUZZY_DISCOUNT]. الحد اختير من مثال محسوب: خطأ حرف واحد
 * ≈ 0.55 مقابل 0.0 لكلمة غير ذات صلة (انظر `evidence/phase-03/research.md` §4).
 */
class HybridRetriever(
  private val index: KnowledgeUnitIndex,
  private val weights: HybridWeights = index.params.weights.normalized(),
  private val tfIdfSignal: TfIdfSignal = SemanticEngineSignal(),
  private val tokenizer: UnitTokenizer = UnitTokenizer
) {

  companion object {
    const val FUZZY_MIN_GRAM_SIMILARITY = 0.5
    const val FUZZY_DISCOUNT = 0.5
    const val MAX_QUERY_LENGTH = 1000
    const val PHRASE_EXACT = 1.0
    const val PHRASE_CONTAINS = 0.85
    /** أقل طول لعبارة قبل اعتبار «احتواء» مطابقةً — قصير جدًا يزيد الإيجابيات الكاذبة. */
    const val MIN_PHRASE_LENGTH = 5
    /** ثابتا معامل التغطية — **منقولان حرفيًا** من `OfflineKnowledgeIndex` (وليسا اختراعًا جديدًا). */
    const val COVERAGE_BASELINE = 0.35
    const val COVERAGE_WEIGHT = 0.65
    /** سقف الدرجة عند تغطية ضعيفة لاستعلام طويل — منقول أيضًا من `OfflineKnowledgeIndex`. */
    const val LOW_COVERAGE_CAP = 0.25
    const val LOW_COVERAGE_LIMIT = 0.40
    /** وزن المطابقة التقريبية في حساب التغطية (نصف مطابقة). */
    const val FUZZY_COVERAGE_CREDIT = 0.5
    /**
     * حد أدنى للمطابقة (minimum-should-match): استعلام من ٣ رموز جوهيرة أو أكثر لا يُقبل فيه مرشح
     * مطابق لرمز واحد فقط. هذا يقلّل الإيجابيات الكاذبة الناتجة عن كلمة واحدة مشتركة عامة
     * («فرع»، «كود»)، وهو مبدأ IR معروف؛ ولا يمسّ استعلامات الرمز الواحد/الرمزين.
     */
    const val LONG_QUERY_TOKENS = 3
    const val MIN_MATCHES_FOR_LONG_QUERY = 2
    /** معامل العقوبة عند مطابقة رمز واحد فقط في استعلام طويل — يمنع الإجابة لا الترتيب. */
    const val SINGLE_MATCH_PENALTY = 0.55
  }

  private val units: List<KnowledgeUnit> = index.units
  private val normalizedWeights: HybridWeights = weights
  private val unitCount: Int = max(1, units.size)
  private val avgDocLength: Double = max(1e-6, index.stats.avgDocLength)
  private val bm25Ref: Double = if (index.stats.bm25Ref > 0.0) index.stats.bm25Ref else 1.0

  /** فهرس معكوس: رمز → مؤشرات الوحدات التي تحتويه. */
  private val postings: Map<String, IntArray> = buildPostings()
  /** بصمات حروف لكل وحدة (عنوان + كل الأسئلة) — تُحسب مرة واحدة عند التحميل. */
  private val unitGrams: List<Set<String>> = units.map { unit ->
    val grams = HashSet<String>()
    grams.addAll(UnitTokenizer.charGrams(unit.title))
    unit.questions.forEach { grams.addAll(UnitTokenizer.charGrams(it)) }
    grams
  }
  /** الأسئلة المطبَّعة لكل وحدة (لتطابق العبارة). */
  private val normalizedQuestions: List<List<String>> = units.map { unit ->
    unit.questions.map { com.example.zahra.core.ZahraNormalizer.normalize(it) }
  }
  private val normalizedTitles: List<String> = units.map { com.example.zahra.core.ZahraNormalizer.normalize(it.title) }
  /** مفردات الفهرس بحجم يُبنى مرة واحدة لأجل البحث التقريبي. */
  private val vocabulary: List<String> = postings.keys.toList()
  private val vocabularyGrams: Map<String, Set<String>> = vocabulary.associateWith { UnitTokenizer.charGrams(it) }
  private val gramToVocabulary: Map<String, List<String>> = buildGramToVocabulary()

  val indexedUnitCount: Int get() = units.size
  val usedWeights: HybridWeights get() = normalizedWeights

  private fun buildPostings(): Map<String, IntArray> {
    val map = HashMap<String, MutableList<Int>>(1024)
    units.forEachIndexed { unitIndex, unit ->
      for (term in unit.terms.keys) {
        map.getOrPut(term) { mutableListOf() }.add(unitIndex)
      }
    }
    return map.mapValues { (_, list) -> list.toIntArray() }
  }

  private fun buildGramToVocabulary(): Map<String, List<String>> {
    val map = HashMap<String, MutableList<String>>(4096)
    for ((term, grams) in vocabularyGrams) {
      for (gram in grams) {
        map.getOrPut(gram) { mutableListOf() }.add(term)
      }
    }
    return map
  }

  /**
   * استرجاع هجين.
   *
   * @param maxResults أقصى عدد نتائج مُرتَّبة (الافتراضي 5).
   */
  fun search(rawQuery: String, maxResults: Int = 5): HybridSearchResult {
    val warnings = mutableListOf<String>()
    if (rawQuery.isBlank()) {
      return HybridSearchResult(rawQuery, emptyList(), emptyList(), listOf("استعلام فارغ"))
    }
    if (units.isEmpty()) {
      return HybridSearchResult(rawQuery, emptyList(), emptyList(), listOf("فهرس وحدات المعرفة فارغ"))
    }
    val safeQuery = if (rawQuery.length > MAX_QUERY_LENGTH) rawQuery.substring(0, MAX_QUERY_LENGTH) else rawQuery
    if (rawQuery.length > MAX_QUERY_LENGTH) {
      warnings.add("تم اقتطاع الاستعلام عند $MAX_QUERY_LENGTH حرفًا")
    }

    val queryTokens = UnitTokenizer.indexedTokens(safeQuery).distinct()
    if (queryTokens.isEmpty()) {
      return HybridSearchResult(rawQuery, emptyList(), emptyList(), warnings + "لا توجد رموز قابلة للبحث في الاستعلام")
    }

    val normalizedQuery = com.example.zahra.core.ZahraNormalizer.normalize(safeQuery)

    val rawScores = DoubleArray(units.size)
    val matchedTerms = Array(units.size) { mutableListOf<String>() }
    val fuzzyMatches = Array(units.size) { mutableListOf<String>() }

    for (token in queryTokens) {
      val exact = postings.containsKey(token)
      if (exact) {
        accumulate(token, token, 1.0, rawScores, matchedTerms, fuzzyMatches)
      } else {
        val fuzzy = nearestVocabularyTerm(token)
        if (fuzzy != null) {
          accumulate(token, fuzzy.term, FUZZY_DISCOUNT, rawScores, matchedTerms, fuzzyMatches)
        }
      }
    }

    val tfIdfScores = try {
      tfIdfSignal.scores(safeQuery, units)
    } catch (e: Exception) {
      warnings.add("تعذّر حساب إشارة TF-IDF: ${e.message}")
      emptyMap()
    }

    val queryGrams = UnitTokenizer.charGrams(safeQuery)
    val candidates = ArrayList<Candidate>(units.size)
    for (i in units.indices) {
      val unit = units[i]
      val raw = rawScores[i]
      val tfidf = min(1.0, max(0.0, tfIdfScores[unit.id] ?: 0.0))
      if (raw <= 0.0 && tfidf <= 0.0) continue

      val bm25Norm = if (raw <= 0.0) 0.0 else raw / (raw + bm25Ref)
      val jaccard = UnitTokenizer.jaccard(queryGrams, unitGrams[i])
      val (phrase, questionIndex) = phraseScore(normalizedQuery, i)
      val charScore = max(jaccard, phrase)
      val exactMatches = matchedTerms[i].distinct()
      val fuzzy = fuzzyMatches[i].distinct()
      val effectiveMatches = exactMatches.size + FUZZY_COVERAGE_CREDIT * fuzzy.size
      // عقوبة «حد أدنى للمطابقة»: لا تُسقط المرشح (قد يكون صحيحًا لكن السؤال محشوّ بكلمات لا يعرفها
      // الفهرس) بل تخفض ثقتنا به، فلا يعبر أرضية القبول. الترتيب لا يتغيّر.
      val singleMatchPenalty =
        if (queryTokens.size >= LONG_QUERY_TOKENS && effectiveMatches < MIN_MATCHES_FOR_LONG_QUERY) {
          SINGLE_MATCH_PENALTY
        } else 1.0

      val fusedAbsolute = normalizedWeights.bm25 * bm25Norm +
        normalizedWeights.char * charScore +
        normalizedWeights.tfidf * tfidf

      candidates.add(
        Candidate(
          unit = unit,
          bm25Raw = raw,
          bm25Normalized = bm25Norm,
          jaccard = jaccard,
          phrase = phrase,
          tfidf = tfidf,
          fusedAbsolute = fusedAbsolute * singleMatchPenalty,
          matchedTerms = exactMatches,
          fuzzyMatches = fuzzy,
          matchedQuestionIndex = questionIndex
        )
      )
    }

    if (candidates.isEmpty()) {
      return HybridSearchResult(rawQuery, queryTokens, emptyList(), warnings)
    }

    // نسبة كل مرشح إلى أفضل مرشح في نفس الاستعلام (شكل `baseScore` في OfflineKnowledgeIndex)
    val bestAbsolute = candidates.maxOf { it.fusedAbsolute }
    val matches = candidates.map { candidate ->
      val relative = if (bestAbsolute > 0.0) candidate.fusedAbsolute / bestAbsolute else 0.0
      val coverage = coverageOf(candidate, queryTokens.size)
      var adjusted = relative * (COVERAGE_BASELINE + COVERAGE_WEIGHT * coverage)
      if (queryTokens.size >= 3 && coverage < LOW_COVERAGE_LIMIT) {
        adjusted = min(LOW_COVERAGE_CAP, adjusted)
      }
      val withWeight = min(1.0, max(0.0, adjusted * candidate.unit.weight))
      UnitMatch(
        unit = candidate.unit,
        score = withWeight,
        matchedTerms = candidate.matchedTerms,
        fuzzyMatches = candidate.fuzzyMatches,
        matchedQuestionIndex = candidate.matchedQuestionIndex,
        breakdown = ScoreBreakdown(
          bm25Raw = candidate.bm25Raw,
          bm25Normalized = candidate.bm25Normalized,
          charJaccard = candidate.jaccard,
          phraseScore = candidate.phrase,
          tfidfCosine = candidate.tfidf,
          fusedAbsolute = candidate.fusedAbsolute,
          relative = relative,
          coverage = coverage,
          weightMultiplier = candidate.unit.weight,
          fused = withWeight
        )
      )
    }

    val ordered = matches.sortedWith(
      compareByDescending<UnitMatch> { it.score }
        .thenByDescending { it.breakdown.fusedAbsolute }
        .thenBy { it.unit.id }
    ).take(maxResults)

    return HybridSearchResult(rawQuery, queryTokens, ordered, warnings)
  }

  /** تغطية رموز الاستعلام: مطابقة تامة = 1، تقريبية = [FUZZY_COVERAGE_CREDIT]. */
  private fun coverageOf(candidate: Candidate, queryTokenCount: Int): Double {
    if (queryTokenCount <= 0) return 0.0
    val effective = candidate.matchedTerms.size + FUZZY_COVERAGE_CREDIT * candidate.fuzzyMatches.size
    return min(1.0, effective / queryTokenCount)
  }

  private data class Candidate(
    val unit: KnowledgeUnit,
    val bm25Raw: Double,
    val bm25Normalized: Double,
    val jaccard: Double,
    val phrase: Double,
    val tfidf: Double,
    val fusedAbsolute: Double,
    val matchedTerms: List<String>,
    val fuzzyMatches: List<String>,
    val matchedQuestionIndex: Int
  )



  /**
   * يضيف مساهمة رمز واحد في درجات كل وحدة تحتوي [indexTerm].
   *
   * @param queryToken رمز الاستعلام كما كتبه المستخدم (بعد التطبيع والتجذيع).
   * @param indexTerm رمز الفهرس الفعلي (يساوي [queryToken] في المطابقة التامة).
   * @param discount 1.0 للمطابقة التامة، و[FUZZY_DISCOUNT] للمطابقة التقريبية.
   */
  private fun accumulate(
    queryToken: String,
    indexTerm: String,
    discount: Double,
    rawScores: DoubleArray,
    matchedTerms: Array<MutableList<String>>,
    fuzzyMatches: Array<MutableList<String>>
  ) {
    val posting = postings[indexTerm] ?: return
    val idfValue = idf(indexTerm)
    for (unitIndex in posting) {
      val unit = units[unitIndex]
      val tf = unit.terms[indexTerm] ?: continue
      val tfNorm = (tf * (index.params.k1 + 1.0)) /
        (tf + index.params.k1 * (1.0 - index.params.b + index.params.b * (unit.docLength / avgDocLength)))
      rawScores[unitIndex] += discount * idfValue * (tfNorm + index.params.delta)
      if (discount == 1.0) {
        matchedTerms[unitIndex].add(queryToken)
      } else {
        fuzzyMatches[unitIndex].add("$queryToken≈$indexTerm")
      }
    }
  }

  /** أرضية القبول الموصى بها من سياسة الثقة الموحّدة (لا عتبة جديدة). */
  fun acceptanceFloor(): Double = ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE

  private fun idf(term: String): Double {
    val df = index.documentFrequency[term] ?: 0
    return ln(1.0 + (unitCount - df + 0.5) / (df + 0.5))
  }

  private fun phraseScore(normalizedQuery: String, unitIndex: Int): Pair<Double, Int> {
    if (normalizedQuery.length < MIN_PHRASE_LENGTH) return 0.0 to -1
    var best = 0.0
    var bestIndex = -1
    normalizedQuestions[unitIndex].forEachIndexed { i, q ->
      if (q.isBlank()) return@forEachIndexed
      if (q == normalizedQuery) {
        if (PHRASE_EXACT > best) {
          best = PHRASE_EXACT
          bestIndex = i
        }
      } else if (normalizedQuery.length >= MIN_PHRASE_LENGTH && q.contains(normalizedQuery)) {
        if (PHRASE_CONTAINS > best) {
          best = PHRASE_CONTAINS
          bestIndex = i
        }
      } else if (q.length >= MIN_PHRASE_LENGTH && normalizedQuery.contains(q)) {
        val v = PHRASE_CONTAINS - 0.1
        if (v > best) {
          best = v
          bestIndex = i
        }
      }
    }
    val title = normalizedTitles[unitIndex]
    if (title.length >= MIN_PHRASE_LENGTH) {
      if (title == normalizedQuery) {
        if (PHRASE_EXACT > best) best = PHRASE_EXACT
      } else if (title.contains(normalizedQuery) || normalizedQuery.contains(title)) {
        if (PHRASE_CONTAINS > best) best = PHRASE_CONTAINS
      }
    }
    return best to bestIndex
  }

  private data class FuzzyTerm(val term: String, val similarity: Double)

  /**
   * أقرب رمز في المفردات لرمز استعلام غير موجود، عبر Jaccard على بصمات الحروف.
   * يُقيَّد المرشحون بالرموز المشتركة في بصمة واحدة على الأقل قبل الحساب الدقيق (أداء + حتمية).
   */
  private fun nearestVocabularyTerm(token: String): FuzzyTerm? {
    val tokenGrams = UnitTokenizer.charGrams(token)
    if (tokenGrams.isEmpty()) return null
    val candidates = LinkedHashSet<String>()
    for (gram in tokenGrams) {
      val terms = gramToVocabulary[gram] ?: continue
      candidates.addAll(terms)
    }
    if (candidates.isEmpty()) return null
    var bestTerm: String? = null
    var bestSimilarity = 0.0
    for (candidate in candidates) {
      val grams = vocabularyGrams[candidate] ?: continue
      val sim = UnitTokenizer.jaccard(tokenGrams, grams)
      if (sim > bestSimilarity || (sim == bestSimilarity && bestTerm != null && candidate < bestTerm)) {
        bestSimilarity = sim
        bestTerm = candidate
      }
    }
    if (bestTerm == null || bestSimilarity < FUZZY_MIN_GRAM_SIMILARITY) return null
    return FuzzyTerm(bestTerm, bestSimilarity)
  }
}
