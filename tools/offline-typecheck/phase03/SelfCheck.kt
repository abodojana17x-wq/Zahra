import com.example.zahra.core.ZahraNormalizer
import com.example.zahra.knowledge.unit.HybridRetriever
import com.example.zahra.knowledge.unit.HybridWeights
import com.example.zahra.knowledge.unit.IndexParams
import com.example.zahra.knowledge.unit.IndexStats
import com.example.zahra.knowledge.unit.KnowledgeUnit
import com.example.zahra.knowledge.unit.KnowledgeUnitIndex
import com.example.zahra.knowledge.unit.NoTfIdfSignal
import com.example.zahra.knowledge.unit.TfIdfSignal
import com.example.zahra.knowledge.unit.UnitIndexCodec
import com.example.zahra.knowledge.unit.UnitKnowledgeService
import com.example.zahra.knowledge.unit.UnitRetrievalGate
import com.example.zahra.knowledge.unit.UnitSource
import com.example.zahra.knowledge.unit.UnitTokenizer
import com.example.zahra.routing.ConfidenceGatePolicy
import com.example.zahra.routing.GateOutcome
import com.example.zahra.tools.JsonValue
import com.example.zahra.tools.MiniJson
import java.io.File
import kotlin.math.abs

/**
 * تحقق تنفيذي حقيقي لمنطق المرحلة 3 **خارج Gradle** (JDK + kotlinc فقط).
 *
 * ما يفعله: يستدعي كود الإنتاج الفعلي (فهرس الوحدات + المسترجع الهجين + البوابة + التقطيع) بمُدخلات مثبتة.
 * ما لا يفعله: لا يشغّل JUnit ولا Robolectric ولا أندرويد، ولا يدّعي أن اختبارات Gradle نجحت.
 */

private var passed = 0
private var failed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, condition: Boolean, detail: String = "") {
  if (condition) {
    passed++
  } else {
    failed++
    failures.add("$name${if (detail.isNotBlank()) " — $detail" else ""}")
    println("FAIL  $name${if (detail.isNotBlank()) " — $detail" else ""}")
  }
}

private fun checkClose(name: String, expected: Double, actual: Double, tolerance: Double = 1e-4) {
  check(name, abs(expected - actual) <= tolerance, "متوقع=$expected فعلي=$actual")
}

fun main(args: Array<String>) {
  val root = File(args.firstOrNull { !it.startsWith("--") } ?: ".").absoluteFile
  println("zahra phase-03 self-check · root=${root.path}")

  if ("--probe" in args) {
    probe(root, args.dropWhile { it != "--probe" }.drop(1))
    return
  }

  if ("--dump-features" in args) {
    dumpFeatures(root)
    return
  }

  tokenizerChecks()
  fixtureParityChecks(root)
  val index = loadIndex(root)
  indexShapeChecks(index)
  indexStatChecks(index)
  syntheticBm25Checks()
  retrievalChecks(index)
  gateChecks(index)
  evalChecks(root, index)

  println()
  println("النتيجة: نجح $passed · فشل $failed")
  if (failed > 0) {
    println("الإخفاقات:")
    failures.forEach { println("  - $it") }
    kotlin.system.exitProcess(1)
  }
}

// ————————————————————————————————————————————————————————
// 1) التقطيع والتطبيع
// ————————————————————————————————————————————————————————

private fun tokenizerChecks() {
  // التطبيع: إزالة التشكيل وتوحيد الحروف
  val norm = ZahraNormalizer.normalize("مُحَمَّدٌ أَحْمَد")
  check("normalize: إزالة التشكيل", !norm.contains("\u064E") && !norm.contains("\u064F"), norm)
  check("normalize: توحيد الألف", ZahraNormalizer.normalize("أحمد") == "احمد")
  check("normalize: توحيد التاء المربوطة", ZahraNormalizer.normalize("مدرسة").contains("ه"))
  check("normalize: أرقام هندية", ZahraNormalizer.normalize("١٢٣").contains("123"))

  // التقطيع
  val tokens = UnitTokenizer.tokenize("ما هو الـ Closure في JavaScript؟")
  check("tokenize: كلمات الوقف تُسقط", !tokens.contains("ما") && !tokens.contains("هو"), tokens.toString())
  check("tokenize: يحفظ المصطلح اللاتيني", tokens.contains("closure"), tokens.toString())
  check("tokenize: حروف صغيرة", tokens.none { it != it.lowercase() }, tokens.toString())
  check("tokenize: نص فارغ", UnitTokenizer.tokenize("   ").isEmpty())

  // كل النص كلمات وقف ⇒ لا نعيد قائمة فارغة (حتى يظل الاستعلام قابلًا للتفسير)
  val onlyStopwords = UnitTokenizer.tokenize("ما هو ده")
  check("tokenize: لا يُفرغ نصًا كله كلمات وقف", onlyStopwords.isNotEmpty(), onlyStopwords.toString())
  check("hasSubstantiveTokens: يكشف غياب موضوع", !UnitTokenizer.hasSubstantiveTokens("ما هو ده؟"))
  check("hasSubstantiveTokens: صحيح لسؤال حقيقي", UnitTokenizer.hasSubstantiveTokens("اشرح الـ closure"))

  // التجذيع
  check("stem: اللاتيني لا يُمس", UnitTokenizer.stem("closure") == "closure")
  check("stem: النفي محفوظ", UnitTokenizer.stem("مش") == "مش" && UnitTokenizer.stem("مفيش") == "مفيش")
  check("stem: قصير جدًا لا يُمس", UnitTokenizer.stem("هو") == "هو")
  val stemmed = UnitTokenizer.stem("والبرمجة")
  check("stem: لا يُنتج رمزًا أقصر من 3", stemmed.length >= 3, stemmed)
  check("stem: حتمي", UnitTokenizer.stem("الملاحظات") == UnitTokenizer.stem("الملاحظات"))

  // بصمات الحروف
  val grams = UnitTokenizer.charGrams("closure")
  check("charGrams: تحوي حدود الكلمة", grams.contains("^cl") && grams.contains("re$"), grams.toString())
  checkClose("jaccard: تطابق تام", 1.0, UnitTokenizer.jaccard(grams, UnitTokenizer.charGrams("closure")))
  checkClose("jaccard: مجموعة فارغة", 0.0, UnitTokenizer.jaccard(grams, emptySet()))
  val typoSimilarity = UnitTokenizer.jaccard(grams, UnitTokenizer.charGrams("closur"))
  check("jaccard: خطأ حرف واحد > 0.5", typoSimilarity > 0.5, "التشابه=$typoSimilarity")
  val unrelated = UnitTokenizer.jaccard(grams, UnitTokenizer.charGrams("قاعدة بيانات"))
  check("jaccard: كلمة غير ذات صلة ≈ 0", unrelated < 0.1, "التشابه=$unrelated")
  check("jaccard: تماثل", UnitTokenizer.jaccard(grams, UnitTokenizer.charGrams("closurex")) ==
    UnitTokenizer.jaccard(UnitTokenizer.charGrams("closurex"), grams))

  // أوزان الدمج
  checkClose("weights: التطبيع لمجموع 1", 1.0, HybridWeights(2.0, 2.0, 1.0).normalized().sum)
  checkClose("weights: الأوزان الافتراضية تطبيع", 1.0, HybridWeights().normalized().sum)
}

private fun fixtureParityChecks(root: File) {
  val path = File(root, "knowledge/unit/tokenizer-fixtures.json")
  if (!path.exists()) {
    check("توائم التقطيع موجودة", false, path.path)
    return
  }
  val parsed = try {
    MiniJson.parse(path.readText(Charsets.UTF_8))
  } catch (e: Exception) {
    check("توائم التقطيع: JSON صالح", false, e.message ?: "")
    return
  }
  val items = parsed.arrayOrNull("items").orEmpty()
  check("توائم التقطيع: عدد النصوص ≥ 20", items.size >= 20, items.size.toString())
  var mismatches = 0
  for (item in items) {
    val text = item.stringOrNull("text") ?: continue
    val expectedTokens = stringArray(item, "tokens")
    val expectedStems = stringArray(item, "stems")
    val actualTokens = UnitTokenizer.tokenize(text)
    val actualStems = actualTokens.map { UnitTokenizer.stem(it) }
    if (actualTokens != expectedTokens || actualStems != expectedStems) {
      mismatches++
      println("      اختلاف توائم: $text → متوقع=$expectedTokens فعلي=$actualTokens")
    }
  }
  check("توائم التقطيع: Kotlin الحالي مطابق للملف المحفوظ", mismatches == 0, "اختلافات=$mismatches")
}

private fun stringArray(node: JsonValue, key: String): List<String> =
  node.arrayOrNull(key).orEmpty().mapNotNull { (it as? JsonValue.Str)?.value }

// ————————————————————————————————————————————————————————
// 2) الفهرس المشحون
// ————————————————————————————————————————————————————————

private fun loadIndex(root: File): KnowledgeUnitIndex {
  val file = File(root, "app/src/main/assets/knowledge_index.json")
  check("فهرس الوحدات موجود في assets", file.exists(), file.path)
  val json = if (file.exists()) file.readText(Charsets.UTF_8) else "{}"
  val index = try {
    UnitIndexCodec.decode(json)
  } catch (e: Exception) {
    check("فهرس الوحدات يُقرأ", false, e.message ?: "")
    return KnowledgeUnitIndex(
      1, "", "", IndexParams(), IndexStats(0, 1.0, 1.0), emptyMap(), emptyList()
    )
  }
  check("فهرس الوحدات يُقرأ", true, "${index.units.size} وحدة")
  return index
}

private fun indexShapeChecks(index: KnowledgeUnitIndex) {
  // الأوزان المشحونة في الفهرس يجب أن تساوي هوامش التطبيق الافتراضية (HybridWeights.DEFAULT)،
  // وإلا صار ما نُقيسه في evidence/phase-03/weights.json غير ما يعمل به التطبيق.
  val defaults = HybridWeights()
  val loaded = index.params.weights.normalized()
  check("أوزان الفهرس = أوزان التطبيق الافتراضية",
    abs(loaded.bm25 - defaults.normalized().bm25) < 1e-6 &&
      abs(loaded.char - defaults.normalized().char) < 1e-6 &&
      abs(loaded.tfidf - defaults.normalized().tfidf) < 1e-6,
    "index=${loaded.describe()} app=${defaults.describe()}")
  check("الفهرس غير فارغ", index.units.isNotEmpty(), "وحدات=${index.units.size}")
  check("إصدار المخطط = 1", index.schemaVersion == 1, index.schemaVersion.toString())
  check("contentHash موجود", index.contentHash.length == 64, index.contentHash.take(12))
  check("المعاملات: k1=1.2 · b=0.75 · delta=1.0",
    index.params.k1 == 1.2 && index.params.b == 0.75 && index.params.delta == 1.0,
    "${index.params.k1}/${index.params.b}/${index.params.delta}")
  check("بصمة الحروف = 3", index.params.gramSize == 3, index.params.gramSize.toString())

  check("لا معرّفات مكررة", index.units.map { it.id }.toSet().size == index.units.size)
  check("كل وحدة لها ≥ 4 تنويعات سؤال",
    index.units.all { it.questions.size >= 4 },
    index.units.filter { it.questions.size < 4 }.joinToString { it.id })
  check("كل وحدة لها مصدر أو authored",
    index.units.all { it.source != null || it.authored },
    index.units.filter { it.source == null && !it.authored }.joinToString { it.id })
  check("كل وحدة لها إجابة", index.units.all { it.answers.isNotEmpty() })
  check("كل وحدة لها رموز مفهرسة", index.units.all { it.terms.isNotEmpty() })
  check("أوزان الوحدات في (0,2]", index.units.all { it.weight > 0.0 && it.weight <= 2.0 })
  check("حزم معلنة", index.sourcePacks.isNotEmpty(), index.sourcePacks.joinToString { it.pack })
  check("مجموع وحدات الحزم = وحدات الفهرس",
    index.sourcePacks.sumOf { it.units } == index.units.size,
    "${index.sourcePacks.sumOf { it.units }} مقابل ${index.units.size}")
  check("وحدات البرمجة تحتوي أمثلة كود بين `…`",
    index.units.filter { it.pack.startsWith("programming") }.all { u -> u.answers.any { it.contains("`") } })
  check("وحدات الصحة تحمل تنبيهًا طبيًا",
    index.units.filter { it.pack.startsWith("health") || it.safety == "medical_disclaimer" }
      .all { it.safety == "medical_disclaimer" })
}

private fun indexStatChecks(index: KnowledgeUnitIndex) {
  // df المشحون يجب أن يطابق إعادة الحساب من الأوزان المخزنة (حارس انحراف البناء)
  val recomputedDf = HashMap<String, Int>()
  for (unit in index.units) {
    for (term in unit.terms.keys) recomputedDf[term] = (recomputedDf[term] ?: 0) + 1
  }
  check("df المشحون = إعادة الحساب من الوحدات", recomputedDf == index.documentFrequency,
    "مفاتيح مشحونة=${index.documentFrequency.size} معادة=${recomputedDf.size}")

  val recomputedAvg = index.units.sumOf { it.docLength }.toDouble() / index.units.size
  checkClose("avgDocLength المشحون مطابق", recomputedAvg, index.stats.avgDocLength, 0.01)
  check("bm25ref موجب", index.stats.bm25Ref > 0.0, index.stats.bm25Ref.toString())
  check("أطوال المستندات موجبة", index.units.all { it.docLength >= 1 })
}

// ————————————————————————————————————————————————————————
// 3) رياضيات BM25+ على فهرس اصطناعي (قيم محسوبة يدويًا)
// ————————————————————————————————————————————————————————

private fun unit(id: String, terms: Map<String, Double>, docLength: Int): KnowledgeUnit = KnowledgeUnit(
  id = id,
  pack = "synthetic",
  title = id,
  intent = "query_knowledge",
  questions = listOf("سؤال $id", "سؤال ثان $id", "سؤال ثالث $id", "question $id"),
  answers = listOf("إجابة اصطناعية للاختبار $id"),
  tags = emptyList(),
  source = UnitSource("اختبار", "", "", ""),
  authored = true,
  author = "self-check",
  verifiedAt = "2026-10-29",
  safety = "none",
  weight = 1.0,
  docLength = docLength,
  terms = terms
)

private fun syntheticIndex(units: List<KnowledgeUnit>, df: Map<String, Int>, avg: Double, ref: Double): KnowledgeUnitIndex =
  KnowledgeUnitIndex(
    schemaVersion = 1,
    builtAt = "2026-10-29",
    builderVersion = "self-check",
    params = IndexParams(k1 = 1.2, b = 0.75, delta = 1.0, gramSize = 3),
    stats = IndexStats(units.size, avg, ref),
    documentFrequency = df,
    units = units,
    sourcePacks = emptyList(),
    contentHash = "0".repeat(64)
  )

private fun syntheticBm25Checks() {
  val units = listOf(
    unit("u1", mapOf("alpha" to 3.0), 3),
    unit("u2", mapOf("alpha" to 1.0), 30),
    unit("u3", mapOf("beta" to 2.0), 20)
  )
  val avg = (3 + 30 + 20) / 3.0
  val index = syntheticIndex(units, mapOf("alpha" to 2, "beta" to 1), avg, ref = 1.0)
  val retriever = HybridRetriever(index, HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal)
  val result = retriever.search("alpha", maxResults = 5)
  check("BM25+: الوحدة غير المطابقة لا تظهر", result.matches.none { it.unit.id == "u3" })
  val u1 = result.matches.first { it.unit.id == "u1" }
  val u2 = result.matches.first { it.unit.id == "u2" }
  // القيم أدناه محسوبة يدويًا من معادلة BM25+:
  // idf(alpha)=ln(1+(3-2+0.5)/(2+0.5))=0.4700036 · tfNorm(u1)=3*2.2/(3+1.2*(0.25+0.75*3/17.6667))=1.91147
  checkClose("BM25+: درجة tF المطبَّعة للوحدة القصيرة", 1.3684040, u1.breakdown.bm25Raw, 1e-4)
  checkClose("BM25+: درجة الوحدة الطويلة", 0.8355968, u2.breakdown.bm25Raw, 1e-4)
  check("BM25+: الوحدة القصيرة تتفوق على الطويلة", u1.breakdown.bm25Raw > u2.breakdown.bm25Raw)
  checkClose("BM25+: التطبيع raw/(raw+ref)", u1.breakdown.bm25Raw / (u1.breakdown.bm25Raw + 1.0),
    u1.breakdown.bm25Normalized, 1e-6)
  check("BM25+: الدرجة النهائية في [0,1]", result.matches.all { it.score in 0.0..1.0 })
  check("BM25+: الترتيب حتمي", result.matches.first().unit.id == "u1")

  // جدول فهرس مختلف: delta يرفع أرضية المصطلح المطابق
  val floorIndex = syntheticIndex(units, mapOf("alpha" to 2, "beta" to 1), avg, ref = 1.0)
  val zeroDelta = HybridRetriever(
    floorIndex.copy(params = floorIndex.params.copy(delta = 0.0)), HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal
  ).search("alpha").matches.first { it.unit.id == "u1" }
  check("BM25+: delta=1 أعلى من delta=0 لنفس الوحدة", u1.breakdown.bm25Raw > zeroDelta.breakdown.bm25Raw)

  // إشارة TF-IDF وحدها تستطيع ترشيح وحدة بلا تطابق لفظي
  val tfIdfOnly = HashMap<String, Double>()
  tfIdfOnly["u3"] = 0.9
  val signal = TfIdfSignal { _: String, _: List<KnowledgeUnit> -> tfIdfOnly }
  val semantic = HybridRetriever(index, HybridWeights(0.0, 0.0, 1.0), signal).search("alpha")
  check("TF-IDF: إشارة مستقلة تستطيع ترشيح وحدة", semantic.matches.firstOrNull()?.unit?.id == "u3",
    semantic.matches.joinToString { it.unit.id })
  check("TF-IDF: لا دعم لفظي ⇒ hasLexicalSupport=false",
    semantic.matches.firstOrNull()?.hasLexicalSupport == false)

  // البوابة: وحدة بلا دعم لفظي لا تُجاب
  val noSupportGate = UnitRetrievalGate(minScore = 0.0)
  val noSupportDecision = noSupportGate.decide("alpha", semantic)
  check("البوابة: لا تُجيب بلا دعم لفظي", noSupportDecision.outcome == GateOutcome.ABSTAIN &&
    noSupportDecision.reasonCode == "no_lexical_support", noSupportDecision.reasonCode)

  // حارس: البوابة تستخدم عتبة السياسة الموحّدة لا رقمًا جديدًا
  checkClose("البوابة: أرضية من ConfidenceGatePolicy", ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE,
    HybridRetriever(index, HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal).acceptanceFloor(), 1e-12)
}

// ————————————————————————————————————————————————————————
// 4) الاسترجاع على الفهرس الحقيقي
// ————————————————————————————————————————————————————————

private fun retrievalChecks(index: KnowledgeUnitIndex) {
  if (index.units.isEmpty()) return
  val service = UnitKnowledgeService(index)
  val retriever = service.retriever

  fun topId(query: String): String? = retriever.search(query, maxResults = 3).best?.unit?.id

  // مطابقة سؤال حرفي
  val closureUnit = index.units.firstOrNull { it.id == "prog-closure" }
  if (closureUnit != null) {
    val exact = retriever.search(closureUnit.questions.first(), maxResults = 3)
    check("استرجاع: السؤال الحرفي يعيد وحدته", exact.best?.unit?.id == "prog-closure",
      exact.best?.unit?.id ?: "—")
    check("استرجاع: تفصيل الدرجات موجود",
      exact.best != null && exact.best!!.breakdown.bm25Raw >= 0.0)
    check("استرجاع: matchedTerms غير فارغة", exact.best!!.matchedTerms.isNotEmpty(),
      exact.best!!.matchedTerms.toString())

    // صياغة مختلفة (فصحى)
    check("استرجاع: صياغة فصحى تصل للوحدة", topId("إلى ماذا يشير مصطلح closure في جافاسكربت؟") == "prog-closure",
      topId("إلى ماذا يشير مصطلح closure في جافاسكربت؟") ?: "—")
    // فرانكو
    val francoResult = retriever.search("3ayez a3raf el closure", maxResults = 3)
    check("استرجاع: فرانكو يُطبَّع ويصل", francoResult.best?.unit?.id == "prog-closure",
      francoResult.best?.unit?.id ?: "—")
    check("استرجاع: فرانكو يُنتج رموزًا عربية", francoResult.queryTokens.any { it.any { c -> c in '\u0600'..'\u06FF' } },
      francoResult.queryTokens.toString())
    // خطأ إملائي في مصطلح لاتيني
    val typoResult = retriever.search("closur fel javascript", maxResults = 3)
    check("استرجاع: التقريب الإملائي يعمل", typoResult.matches.any { it.fuzzyMatches.isNotEmpty() },
      typoResult.matches.joinToString { it.unit.id + it.fuzzyMatches })
    check("استرجاع: الاستعلام بخطأ إملائي يظل يصل للوحدة", typoResult.best?.unit?.id == "prog-closure",
      typoResult.best?.unit?.id ?: "—")
  } else {
    check("استرجاع: وحدة prog-closure موجودة", false)
  }

  // حتمية
  val q = "الفرق بين SQL و NoSQL"
  val a = retriever.search(q).matches.map { it.unit.id to it.score }
  val b = retriever.search(q).matches.map { it.unit.id to it.score }
  check("استرجاع: حتمي بلا عشوائية", a == b)
  check("استرجاع: سؤال SQL يعيد وحدته", topId(q) == "prog-sql-nosql", topId(q) ?: "—")

  // عدد النتائج
  check("استرجاع: maxResults يحترم الحد", retriever.search(q, maxResults = 2).matches.size <= 2)

  // استعلام طويل
  val longQuery = q + " " + "و".repeat(10) + " " + List(300) { "كلمة$it" }.joinToString(" ")
  val longResult = retriever.search(longQuery)
  check("استرجاع: استعلام طويل يُقتطع بتحذير", longResult.warnings.any { it.contains("اقتطاع") },
    longResult.warnings.toString())

  // استعلام بلا موضوع
  val gate = service.gate
  check("بوابة: استعلام كلمات وقف ⇒ امتناع",
    gate.decide("ما هو ده", retriever.search("ما هو ده")).reasonCode == "no_substantive_tokens")
  check("بوابة: استعلام فارغ ⇒ امتناع",
    gate.decide("", retriever.search("")).outcome == GateOutcome.ABSTAIN)
  val nonsense = "زقزقة النورس عند الفجر في المحيط الهندي"
  val nonsenseDecision = gate.decide(nonsense, retriever.search(nonsense))
  check("بوابة: سؤال خارج المعرفة ⇒ امتناع", nonsenseDecision.outcome == GateOutcome.ABSTAIN,
    "${nonsenseDecision.outcome}/${nonsenseDecision.reasonCode}")

  // إجابة كاملة مع سند
  if (closureUnit != null) {
    val answer = service.answer("يعني ايه closure في جافاسكربت")
    check("خدمة: تُجيب بسند", answer.answered && answer.text != null)
    check("خدمة: النص يحتوي المصدر", answer.text?.contains("المصدر") == true, answer.text?.take(80) ?: "—")
    check("خدمة: sourceLabel غير فارغ", !answer.sourceLabel.isNullOrBlank())
    check("خدمة: الثقة في [0,1]", answer.confidence in 0.0..1.0, answer.confidence.toString())
  }

  // وحدة الصحة تحمل تنبيهًا
  val medical = index.units.firstOrNull { it.safety == "medical_disclaimer" }
  if (medical != null) {
    val answer = service.answer(medical.questions.first())
    check("سلامة: تنبيه طبي ظاهر", answer.text?.contains("طبيب") == true, answer.text?.take(80) ?: "—")
  }
}

private fun gateChecks(index: KnowledgeUnitIndex) {
  if (index.units.isEmpty()) return
  val retriever = HybridRetriever(index, HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal)
  val decision = UnitRetrievalGate().decide("يعني ايه closure", retriever.search("يعني ايه closure"))
  check("بوابة: أعلى مرشح له تفسير كامل",
    decision.best == null || decision.diagnostics.containsKey("breakdown"))
  if (decision.best != null) {
    check("بوابة: التشخيص يحمل الهوامش", decision.diagnostics.containsKey("margin") ||
      decision.diagnostics["runnerUp"] == null)
  }

  // تصنيع حالة تقارب بين حزمتين مختلفتين ⇒ توضيح
  val close = listOf(
    unit("packA-unit", mapOf("alpha" to 3.0), 3).copy(pack = "packA"),
    unit("packB-unit", mapOf("alpha" to 2.9), 3).copy(pack = "packB")
  )
  val closeIndex = syntheticIndex(close, mapOf("alpha" to 2), 3.0, ref = 1.0)
  val closeRetriever = HybridRetriever(closeIndex, HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal)
  val closeDecision = UnitRetrievalGate(minScore = 0.0).decide("alpha", closeRetriever.search("alpha"))
  check("بوابة: تقارب بين حزمتين ⇒ توضيح", closeDecision.outcome == GateOutcome.CLARIFY,
    "${closeDecision.outcome}/${closeDecision.reasonCode}")
  check("بوابة: خيارات التوضيح من العناوين", closeDecision.clarifyOptions.isNotEmpty())

  val samePack = close.map { it.copy(pack = "packA") }
  val sameRetriever = HybridRetriever(syntheticIndex(samePack, mapOf("alpha" to 2), 3.0, ref = 1.0),
    HybridWeights(1.0, 0.0, 0.0), NoTfIdfSignal)
  check("بوابة: تقارب داخل نفس الحزمة ⇒ إجابة",
    UnitRetrievalGate(minScore = 0.0).decide("alpha", sameRetriever.search("alpha")).outcome == GateOutcome.ANSWER)

  // عتبة عالية ⇒ امتناع صريح. الاستعلام يغطي نصف الرموز فقط، والدرجة القصوى الممكنة = 1.0،
  // لذا لا يمكن أن يعبر عتبة 0.999 — وهذا يثبت أن فحص «تحت العتبة» يسبق فحص «التقارب».
  val highFloor = UnitRetrievalGate(minScore = 0.999).decide("alpha gamma", closeRetriever.search("alpha gamma"))
  check("بوابة: تحت العتبة ⇒ امتناع", highFloor.outcome == GateOutcome.ABSTAIN &&
    highFloor.reasonCode == "below_threshold", highFloor.reasonCode)
}

// ————————————————————————————————————————————————————————
// 5) مجموعات الصياغة (train/heldout) + الحالة السلبية
// ————————————————————————————————————————————————————————

private fun evalChecks(root: File, index: KnowledgeUnitIndex) {
  val paraphrase = File(root, "eval/phase03/paraphrase-100.jsonl")
  if (!paraphrase.exists()) {
    check("مجموعة الصياغة موجودة", false, paraphrase.path)
    return
  }
  val service = UnitKnowledgeService(index)
  val rows = readJsonl(paraphrase)
  check("مجموعة الصياغة: 100 صف", rows.size == 100, rows.size.toString())

  data class Stat(var total: Int = 0, var top1: Int = 0, var answered: Int = 0, var rr: Double = 0.0)
  val bySplit = LinkedHashMap<String, Stat>()
  val byKind = LinkedHashMap<String, Stat>()

  for (row in rows) {
    val query = row.stringOrNull("query") ?: continue
    val expected = row.stringOrNull("expected") ?: continue
    val split = row.stringOrNull("split") ?: "train"
    val kind = row.stringOrNull("kind") ?: "other"
    val result = service.retriever.search(query, maxResults = 5)
    val ranked = result.matches.map { it.unit.id }
    val rank = ranked.indexOf(expected)
    val isTop1 = rank == 0
    val answered = service.gate.decide(query, result).answered
    for ((map, key) in listOf(bySplit to split, byKind to kind)) {
      val stat = map.getOrPut(key) { Stat() }
      stat.total++
      if (isTop1) stat.top1++
      if (answered) stat.answered++
      if (rank >= 0) stat.rr += 1.0 / (rank + 1)
    }
  }

  fun report(label: String, stats: Map<String, Stat>) {
    println()
    println("[$label] split/kind · عدد · Top-1 · مُجاب · MRR")
    for ((key, stat) in stats) {
      val top = if (stat.total > 0) stat.top1 * 100.0 / stat.total else 0.0
      val ans = if (stat.total > 0) stat.answered * 100.0 / stat.total else 0.0
      val mrr = if (stat.total > 0) stat.rr / stat.total else 0.0
      println("   %-14s %3d   %5.1f%%   %5.1f%%   %.3f".format(key, stat.total, top, ans, mrr))
    }
  }
  report("paraphrase-100 · by split", bySplit)
  report("paraphrase-100 · by kind", byKind)

  check("توزيع الصياغة يشمل train و heldout",
    bySplit.containsKey("train") && bySplit.containsKey("heldout"), bySplit.keys.toString())
  check("توزيع الصياغة يشمل فرانكو وإملاء وإنجليزي",
    listOf("franco", "typo", "en").all { byKind.containsKey(it) }, byKind.keys.toString())

  val negFile = File(root, "eval/phase03/negative-control.jsonl")
  if (!negFile.exists()) {
    check("مجموعة الحالة السلبية موجودة", false, negFile.path)
    return
  }
  val negatives = readJsonl(negFile)
  check("الحالة السلبية: 12 صفًا على الأقل", negatives.size >= 12, negatives.size.toString())
  // فحص على مستويين:
  // - strict=true  ⇒ الترتيب نفسه ممنوع: أعلى نتيجة لا تكون وحدة ممنوعة (حالات يفصلها المعجم).
  // - strict=false ⇒ تصادم معجمي حقيقي (نفس الكلمة بمعنى آخر) لا يحلّه بحث لفظي بمستوى A؛ المطلوب
  //   عنده ألا تُقدَّم الوحدة الممنوعة كإجابة، أي الامتناع أو اختيار غيرها. هذا لا يُخفّف الفحص الأول.
  var strictViolations = 0
  var answerViolations = 0
  var strictRows = 0
  for (row in negatives) {
    val query = row.stringOrNull("query") ?: continue
    val forbidden = stringArray(row, "forbidden")
    val strict = (row as? JsonValue.Obj)?.fields?.get("strict")?.let { it as? JsonValue.Bool }?.value ?: true
    val result = service.retriever.search(query, maxResults = 3)
    val top = result.best?.unit?.id
    if (strict) {
      strictRows++
      if (top != null && forbidden.contains(top)) {
        strictViolations++
        println("      انتهاك ترتيب: «$query» → $top")
      }
    } else {
      val decision = service.gate.decide(query, result)
      val answeredForbidden = decision.answered && decision.best?.unit?.id in forbidden
      if (answeredForbidden) {
        answerViolations++
        println("      انتهاك إجابة: «$query» → ${decision.best?.unit?.id}")
      }
    }
  }
  check("الحالة السلبية: 8 صفوف صارمة على الأقل", strictRows >= 8, strictRows.toString())
  check("الحالة السلبية الصارمة: لا يعيد وحدة ممنوعة كأعلى نتيجة", strictViolations == 0,
    "انتهاكات=$strictViolations")
  check("الحالة السلبية (تصادم معجمي): لا تُقدَّم وحدة ممنوعة كإجابة", answerViolations == 0,
    "انتهاكات=$answerViolations")
}

private fun readJsonl(file: File): List<JsonValue> =
  file.readLines(Charsets.UTF_8)
    .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
    .mapNotNull { line ->
      try {
        MiniJson.parse(line)
      } catch (e: Exception) {
        println("      سطر JSON غير صالح: ${line.take(60)}")
        null
      }
    }

// ————————————————————————————————————————————————————————
// 6) تفريغ السمات: --dump-features — يُستخدم في ضبط الأوزان (tools/phase03/tune_weights.py)
// ————————————————————————————————————————————————————————

/**
 * يطبع سطر JSON لكل استعلام في مجموعة الصياغة يحوي سمات كل مرشح (bm25 مطبَّع، تشابه حروف، tfidf،
 * تغطية، وزن الوحدة). الضبط يتم على هذه السمات نفسها التي يحسبها Kotlin — لا إعادة تنفيذ في Python.
 */
private fun dumpFeatures(root: File) {
  val index = loadIndex(root)
  val retriever = HybridRetriever(index)
  val rows = readJsonl(File(root, "eval/phase03/paraphrase-100.jsonl"))
  for (row in rows) {
    val query = row.stringOrNull("query") ?: continue
    val result = retriever.search(query, maxResults = index.units.size)
    val candidates = result.matches.joinToString(",") { m ->
      val b = m.breakdown
      "{\"id\":${quote(m.unit.id)},\"pack\":${quote(m.unit.pack)},\"bm25\":${num(b.bm25Normalized)}," +
        "\"char\":${num(maxOf(b.charJaccard, b.phraseScore))}," +
        "\"tfidf\":${num(b.tfidfCosine)},\"coverage\":${num(b.coverage)}," +
        "\"nMatch\":${num((m.matchedTerms.size + HybridRetriever.FUZZY_COVERAGE_CREDIT * m.fuzzyMatches.size).toDouble())}," +
        "\"weight\":${num(m.unit.weight)}}"
    }
    println(
      "{\"query\":${quote(query)},\"expected\":${quote(row.stringOrNull("expected") ?: "")}," +
        "\"split\":${quote(row.stringOrNull("split") ?: "train")}," +
        "\"kind\":${quote(row.stringOrNull("kind") ?: "other")}," +
        "\"tokens\":${result.queryTokens.size},\"candidates\":[$candidates]}"
    )
  }
}

private fun quote(text: String): String {
  val sb = StringBuilder("\"")
  for (c in text) {
    when (c) {
      '"' -> sb.append("\\\"")
      '\\' -> sb.append("\\\\")
      '\n' -> sb.append("\\n")
      else -> sb.append(c)
    }
  }
  return sb.append('"').toString()
}

private fun num(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)

// ————————————————————————————————————————————————————————
// 7) وضع الفحص اليدوي: --probe "سؤال"
// ————————————————————————————————————————————————————————

private fun probe(root: File, queries: List<String>) {
  val index = loadIndex(root)
  if (index.units.isEmpty()) {
    println("الفهرس فارغ — لا شيء للفحص")
    return
  }
  val service = UnitKnowledgeService(index)
  for (query in queries) {
    println()
    println("سؤال: $query")
    println("رمزات: ${UnitTokenizer.indexedTokens(query)}")
    val result = service.retriever.search(query, maxResults = 3)
    val decision = service.gate.decide(query, result)
    for (match in result.matches) {
      println("  %.3f  %-24s  %s".format(match.score, match.unit.id, match.breakdown.explain()))
      if (match.matchedTerms.isNotEmpty()) println("         رموز: ${match.matchedTerms}")
      if (match.fuzzyMatches.isNotEmpty()) println("         تقريب: ${match.fuzzyMatches}")
    }
    println("  القرار: ${decision.outcome} (${decision.reasonCode}) — ${decision.reason}")
    println("  الأوزان: ${service.retriever.usedWeights.describe()}")
  }
}
