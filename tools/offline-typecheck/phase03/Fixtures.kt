import com.example.zahra.core.ZahraNormalizer
import com.example.zahra.knowledge.unit.UnitTokenizer
import java.io.File

/**
 * يُولّد `knowledge/unit/tokenizer-fixtures.json` من **Kotlin** (مرجع الحقيقة للتقطيع).
 *
 * `tools/build_index.py --self-test` يفحص أن مرآة Python تساوي هذه المخرجات بالحرف، وأي اختلاف يُفشل البناء (D24).
 * التشغيل: `./tools/offline-typecheck/phase03/run.sh --emit-fixtures`
 */
private val FIXTURE_TEXTS = listOf(
    "ما هو الـ Closure في JavaScript؟",
    "يعني ايه closure في جافاسكربت",
    "ازاي أوسط div في CSS",
    "3ayez a3raf el closure",
    "شرح الـ closures وأمثلة عليها",
    "ما هي سرعة الضوء في الفراغ؟",
    "الإسلام والإيمان: ما الفرق؟",
    "أريد أن أعرف معنى الصبر",
    "القراءة والكتابة في المدرسة",
    "الصلاة والصيام والزكاة",
    "دالة متغير برمجة كود",
    "What is an API?",
    "How do I center a div with flexbox",
    "Python list vs tuple",
    "قاعدة بيانات علائقية SQL",
    "الملاحظات والمهام والجدولة",
    "الكلمات المكررة ١٢٣ في النص ٤٥٦",
    "مُشكَّلٌ بِالتَّشْكيل والـتـطـويل",
    "أ ب ج د",
    "في من على إلى عن",
    "الحديث الشريف عن النية",
    "الخوارزمي مؤسس علم الجبر",
    "ما هو الفرق بين SQL و NoSQL",
    "كيف أذاكر بفاعلية؟",
    "الحمد لله رب العالمين",
    "طريقة ضغط الصور قبل الرفع",
    "إن شاء الله غدا",
    "الوضوء وشروطه",
    "تعريف الذكاء الاصطناعي",
    "برمجة تطبيق أندرويد"
)

private fun escape(text: String): String {
  val sb = StringBuilder(text.length + 8)
  for (ch in text) {
    when (ch) {
      '"' -> sb.append("\\\"")
      '\\' -> sb.append("\\\\")
      '\n' -> sb.append("\\n")
      '\r' -> sb.append("\\r")
      '\t' -> sb.append("\\t")
      else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
    }
  }
  return sb.toString()
}

private fun list(values: List<String>): String = values.joinToString(",", "[", "]") { "\"${escape(it)}\"" }

/**
 * يُصدّر قاموس الفرانكو من Kotlin حتى تستطيع مرآة Python في `tools/build_index.py` تحويل نصوص الوحدات
 * بنفس قواعد Kotlin (بدلًا من تكرار 319 مدخلًا يدويًا).
 */
private fun emitLexicon(root: File) {
  val out = File(root, "knowledge/unit/franco-lexicon.json")
  out.parentFile.mkdirs()
  val phrases = ZahraNormalizer.FRANCO_PHRASES.joinToString(",\n") { (from, to) ->
    "    [\"${escape(from)}\", \"${escape(to)}\"]"
  }
  val words = ZahraNormalizer.FRANCO_LEXICON.entries
    .sortedBy { it.key }
    .joinToString(",\n") { (from, to) -> "    \"${escape(from)}\": \"${escape(to)}\"" }
  val payload = buildString {
    append("{\n")
    append("  \"generatedBy\": \"ZahraNormalizer (Kotlin) — tools/offline-typecheck/phase03/Fixtures.kt\",\n")
    append("  \"note\": \"لا تُحرّر يدويًا. أعِد التوليد: ./tools/offline-typecheck/phase03/run.sh --emit-lexicon\",\n")
    append("  \"phrases\": [\n").append(phrases).append("\n  ],\n")
    append("  \"words\": {\n").append(words).append("\n  }\n")
    append("}\n")
  }
  out.writeText(payload, Charsets.UTF_8)
  println("WROTE ${out.path} — ${ZahraNormalizer.FRANCO_PHRASES.size} عبارة، ${ZahraNormalizer.FRANCO_LEXICON.size} كلمة")
}

fun main(args: Array<String>) {
  val root = File(args.firstOrNull() ?: ".")
  if ("--emit-lexicon" in args) {
    emitLexicon(root)
    return
  }
  val out = File(root, "knowledge/unit/tokenizer-fixtures.json")
  out.parentFile.mkdirs()

  val items = FIXTURE_TEXTS.joinToString(",\n") { text ->
    val tokens = UnitTokenizer.tokenize(text)
    val stems = tokens.map { UnitTokenizer.stem(it) }
    "    {\"text\": \"${escape(text)}\", \"tokens\": ${list(tokens)}, \"stems\": ${list(stems)}}"
  }

  val payload = buildString {
    append("{\n")
    append("  \"generatedBy\": \"UnitTokenizer (Kotlin) — tools/offline-typecheck/phase03/Fixtures.kt\",\n")
    append("  \"gramSize\": ").append(UnitTokenizer.GRAM_SIZE).append(",\n")
    append("  \"note\": \"لا تُحرّر يدويًا. أعِد التوليد بالأمر: ./tools/offline-typecheck/phase03/run.sh --emit-fixtures\",\n")
    append("  \"items\": [\n")
    append(items)
    append("\n  ]\n")
    append("}\n")
  }
  out.writeText(payload, Charsets.UTF_8)
  println("WROTE ${out.path} — ${FIXTURE_TEXTS.size} نصًا")
}
