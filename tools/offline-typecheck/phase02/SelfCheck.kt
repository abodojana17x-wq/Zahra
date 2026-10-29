import com.example.zahra.math.MathDivisionByZeroException
import com.example.zahra.math.MathDomainException
import com.example.zahra.math.MathExpressionEngine
import com.example.zahra.math.MathFormatting
import com.example.zahra.math.MathOverflowException
import com.example.zahra.math.MathSyntaxException
import com.example.zahra.math.UnitConversionResult
import com.example.zahra.math.UnitRequestParseResult
import com.example.zahra.math.UnitRequestParser
import com.example.zahra.math.UnitSystem
import com.example.zahra.math.WordProblemResult
import com.example.zahra.math.WordProblems
import com.example.zahra.model.OutcomeState
import com.example.zahra.tools.CacheKind
import com.example.zahra.tools.CodeRunResult
import com.example.zahra.tools.CodeSnippets
import com.example.zahra.tools.Cities
import com.example.zahra.tools.DataToolResult
import com.example.zahra.tools.DisabledCodeRunner
import com.example.zahra.tools.HttpResult
import com.example.zahra.tools.HttpTransport
import com.example.zahra.tools.MiniJson
import com.example.zahra.tools.NetworkDecision
import com.example.zahra.tools.NetworkMode
import com.example.zahra.tools.NetworkPolicy
import com.example.zahra.tools.NetworkRequirement
import com.example.zahra.tools.OpenMeteoClient
import com.example.zahra.tools.SourceCache
import com.example.zahra.tools.TabularHijriCalculator
import com.example.zahra.tools.UmmAlQuraHijriCalculator
import com.example.zahra.tools.TimeTools
import com.example.zahra.tools.WikipediaClient
import com.example.zahra.tools.WikidataClient
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * تحقق تنفيذي حقيقي لمنطق المرحلة 2 **خارج Gradle** (JDK + kotlinc فقط).
 *
 * ما يفعله: يستدعي الكود الفعلي (math/ + tools/ + MathEngine) بمُدخلات مثبتة ويطبع النتائج.
 * ما لا يفعله: لا يشغّل JUnit ولا Robolectric ولا أندرويد، ولا يقول إن اختبارات Gradle نجحت.
 */
private var passed = 0
private var failed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, condition: Boolean, detail: String = "") {
  if (condition) {
    passed++
  } else {
    failed++
    failures.add("$name ${if (detail.isNotBlank()) "— $detail" else ""}")
    println("FAIL  $name ${if (detail.isNotBlank()) "— $detail" else ""}")
  }
}

private fun checkClose(name: String, expected: Double, actual: Double, tolerance: Double = 1e-9) {
  check(name, abs(expected - actual) <= tolerance, "متوقع=$expected فعلي=$actual")
}

private fun valueOf(expression: String): Double? = try {
  MathExpressionEngine.evaluate(expression).value
} catch (e: Exception) {
  println("      (${expression}) ⇒ ${e::class.simpleName}: ${e.message}")
  null
}

fun main() {
  println("== MathExpressionEngine ==")
  checkClose("2+3*4", 14.0, valueOf("2 + 3 * 4") ?: Double.NaN)
  checkClose("(2+3)*4", 20.0, valueOf("(2 + 3) * 4") ?: Double.NaN)
  checkClose("2^3^2", 512.0, valueOf("2 ^ 3 ^ 2") ?: Double.NaN)
  checkClose("-2^2", -4.0, valueOf("-2 ^ 2") ?: Double.NaN)
  checkClose("2(3+4)", 14.0, valueOf("2(3+4)") ?: Double.NaN)
  checkClose("2sqrt(9)", 6.0, valueOf("2sqrt(9)") ?: Double.NaN)
  checkClose("جذر 144", 12.0, valueOf("جذر 144") ?: Double.NaN)
  checkClose("٥٦", 56.0, valueOf("٥٦") ?: Double.NaN)
  checkClose("5²", 25.0, valueOf("5²") ?: Double.NaN)
  checkClose("avg(4,8,10)", 22.0 / 3.0, valueOf("avg(4, 8, 10)") ?: Double.NaN)
  checkClose("fact(5)", 120.0, valueOf("fact(5)") ?: Double.NaN)
  checkClose("gcd(12,18)", 6.0, valueOf("gcd(12, 18)") ?: Double.NaN)
  checkClose("2^-3", 0.125, valueOf("2^-3") ?: Double.NaN)

  for (bad in listOf("2 +", "* 5", "2 * / 3", "(2 + 3", "2 + 3)", "", "process.exit(1)", "alert(1)", "3!!")) {
    val threw = try {
      MathExpressionEngine.evaluate(bad); false
    } catch (_: MathSyntaxException) {
      true
    } catch (_: Exception) {
      false
    }
    check("رفض تعبير غير صالح: $bad", threw)
  }
  for (bad in listOf("5 / 0", "0 / 0", "10 / (3 - 3)", "15 % 0")) {
    val threw = try {
      MathExpressionEngine.evaluate(bad); false
    } catch (_: MathDivisionByZeroException) {
      true
    }
    check("رفض القسمة على صفر: $bad", threw)
  }
  check("جذر سالب ⇒ MathDomainException", runCatching { MathExpressionEngine.evaluate("sqrt(-16)") }.exceptionOrNull() is MathDomainException)
  check("طفحان ⇒ MathOverflowException", runCatching { MathExpressionEngine.evaluate("1E308 * 10") }.exceptionOrNull() is MathOverflowException)

  println("== MathFormatting / أثر العمليات ==")
  check("شجرة العمليات", MathExpressionEngine.evaluate("2 + 3 * 4").operationTree == "(+ 2 (* 3 4))")
  check("تنسيق 6.2137", MathFormatting.formatNumber(6.2137119) == "6.2137")

  println("== UnitSystem ==")
  fun convert(value: Double, from: String, to: String): Double? {
    val result = UnitSystem.convert(value, from, to)
    return if (result is UnitConversionResult.Success) result.value else {
      println("      (تحويل $from→$to) ⇒ ${result::class.simpleName}")
      null
    }
  }
  val reference = listOf(
    Triple(10.0 to "km", "mi", 6.213711922),
    Triple(30.0 to "cm", "inch", 11.8110236),
    Triple(6.0 to "ft", "m", 1.8288),
    Triple(3.0 to "yd", "ft", 9.0),
    Triple(500.0 to "g", "kg", 0.5),
    Triple(2.0 to "ton", "kg", 2000.0),
    Triple(150.0 to "lb", "kg", 68.0388555),
    Triple(0.0 to "c", "f", 32.0),
    Triple(72.0 to "f", "c", 22.2222222),
    Triple(35.0 to "c", "f", 95.0),
    Triple(-40.0 to "c", "f", -40.0),
    Triple(0.0 to "c", "k", 273.15),
    Triple(2000.0 to "ml", "l", 2.0),
    Triple(2.0 to "l", "gal", 0.5283441047),
    Triple(3.0 to "feddan", "m2", 12602.499),
    Triple(1.0 to "feddan", "qirat", 24.0),
    Triple(1.0 to "hectare", "m2", 10000.0),
    Triple(1.0 to "km2", "m2", 1_000_000.0),
    Triple(3.0 to "h", "min", 180.0),
    Triple(90.0 to "min", "h", 1.5),
    Triple(1.0 to "week", "day", 7.0),
    Triple(36.0 to "kmh", "mps", 10.0),
    Triple(1.5 to "gb", "mb", 1536.0),
    Triple(500.0 to "mb", "gb", 0.48828125),
    Triple(1.0 to "byte", "bit", 8.0),
    Triple(1.0 to "mile", "yard", 1760.0)
  )
  for ((pair, target, expected) in reference) {
    val (value, from) = pair
    val actual = convert(value, from, target)
    if (actual == null) check("تحويل $value $from→$target", false, "فشل التحويل")
    else checkClose("تحويل $value $from→$target", expected, actual, maxOf(abs(expected) * 1e-5, 1e-6))
  }
  for ((from, to) in listOf("km" to "mi", "kg" to "lb", "c" to "f", "m2" to "feddan", "h" to "min", "gb" to "mb")) {
    val forward = convert(7.5, from, to) ?: continue
    val backward = convert(forward, to, from) ?: continue
    checkClose("عودة $from→$to→$from", 7.5, backward, 1e-9)
  }
  check("رفض تحويل بين تصنيفين", UnitSystem.convert(5.0, "kg", "m") is UnitConversionResult.CrossCategory)
  check("رفض وحدة مجهولة", UnitSystem.convert(1.0, "kg", "كيلوفولت") is UnitConversionResult.Unsupported)
  check("لا سعر عملة مخترع", UnitSystem.convert(100.0, "usd", "egp") is UnitConversionResult.RequiresLiveSource)
  check("الفدان معلَّم كقيمة عرفية", (UnitSystem.convert(3.0, "فدان", "متر مربع") as UnitConversionResult.Success).isApproximate)

  println("== UnitRequestParser (أسئلة القياس المجمّدة: units-001..018) ==")
  val frozenUnitQuestions = listOf(
    Triple("حوّل ١٠ كيلومتر إلى ميل", 6.2137, 0.0124),
    Triple("٥٠٠ جرام كام كيلوجرام؟", 0.5, 0.01),
    Triple("حوّل ٣٠ سنتيمتر إلى بوصة", 11.811, 0.024),
    Triple("٢٠٠٠ ملليلتر كام لتر؟", 2.0, 0.01),
    Triple("حوّل ١.٥ جيجابايت إلى ميجابايت", 1536.0, 3.1),
    Triple("٥٠٠ ميجابايت كام جيجابايت؟", 0.4883, 0.01),
    Triple("حوّل ٢ طن إلى كيلوجرام", 2000.0, 4.0),
    Triple("٦ أقدام كام متر؟", 1.8288, 0.01),
    Triple("حوّل ٢ لتر إلى جالون", 0.5283, 0.01),
    Triple("٣ ساعات كام دقيقة؟", 180.0, 0.36),
    Triple("٩٠ دقيقة كام ساعة؟", 1.5, 0.01),
    Triple("حوّل ١٥٠ باوند إلى كيلوجرام", 68.0389, 0.14),
    Triple("١٢ بوصة كام سنتيمتر؟", 30.48, 0.06),
    Triple("٥ كيلومتر كام متر؟", 5000.0, 10.0),
    Triple("حوّل ٣ ياردات إلى قدم", 9.0, 0.018),
    Triple("حوّل ٧٢ فهرنهايت إلى مئوية", 22.2222, 0.05),
    Triple("٣٥ درجة مئوية كام فهرنهايت؟", 95.0, 0.05),
    Triple("حوّل ٠ درجة مئوية إلى فهرنهايت", 32.0, 0.05)
  )
  for ((question, expected, tolerance) in frozenUnitQuestions) {
    when (val parsed = UnitRequestParser.parse(question)) {
      is UnitRequestParseResult.Parsed -> checkClose("سؤال: $question", expected, UnitSystem.convert(parsed.request).let {
        if (it is UnitConversionResult.Success) it.value else Double.NaN
      }, tolerance)
      else -> check("سؤال: $question", false, "لم يُستخرج الطلب: ${parsed::class.simpleName}")
    }
  }
  check("3 فدان إلى متر مربع", (UnitRequestParser.parse("3 فدان إلى متر مربع") as? UnitRequestParseResult.Parsed)?.let {
    (UnitSystem.convert(it.request) as UnitConversionResult.Success).value
  }?.let { abs(it - 12602.499) < 1e-3 } == true)
  check("كم متر في الكيلومتر (ترتيب معكوس)", (UnitRequestParser.parse("كم متر في الكيلومتر") as? UnitRequestParseResult.Parsed)?.let {
    it.request.value == 1.0 && (UnitSystem.convert(it.request) as UnitConversionResult.Success).value == 1000.0
  } == true)
  check("وحدة مجهولة بعد «إلى»", UnitRequestParser.parse("حول 50 كجم إلى كيلوفولت") is UnitRequestParseResult.UnknownUnit)

  println("== WordProblems ==")
  fun solved(input: String): Double? = (WordProblems.solve(input) as? WordProblemResult.Solved)?.value
  checkClose("500 وخصم 20%", 400.0, solved("500 وخصم 20%") ?: Double.NaN)
  checkClose("20% من 500", 100.0, solved("20% من 500") ?: Double.NaN)
  checkClose("متوسط 4 و8 و10", 22.0 / 3.0, solved("متوسط 4 و8 و10") ?: Double.NaN)
  checkClose("دائرة نصف قطرها 5", Math.PI * 25, solved("مساحة دائرة نصف قطرها 5") ?: Double.NaN)
  checkClose("دائرة قطرها 5", 19.6349540849, solved("مساحة دائرة قطرها 5") ?: Double.NaN)
  checkClose("اشتريت 3 حاجات بـ25 وخصم 10%", 67.5, solved("اشتريت 3 حاجات بـ 25 جنيه وخصم 10%") ?: Double.NaN)
  checkClose("معايا 1000 وصرفت 350", 650.0, solved("لو معايا 1000 وصرفت 350 كام يفضل") ?: Double.NaN)
  checkClose("مستطيل 4×6", 24.0, solved("مساحة مستطيل طوله 4 وعرضه 6") ?: Double.NaN)
  checkClose("زيادة 15% على 1000", 1150.0, solved("1000 وبعد زيادة 15%") ?: Double.NaN)
  check("حالة سلبية: الخصم بلا قيمة", WordProblems.solve("الخصم في المحل 20%") is WordProblemResult.NotApplicable)
  check("حالة سلبية: مساحة بلا أرقام", WordProblems.solve("مساحة الدائرة") is WordProblemResult.NotApplicable)
  check("حالة سلبية: خصم > 100%", WordProblems.solve("500 وخصم 120%") is WordProblemResult.NotApplicable)

  println("== TimeTools (ساعة ثابتة) ==")
  val zone = TimeZone.getTimeZone("Africa/Cairo")
  val now = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.OCTOBER, 29, 15, 7, 0) }.timeInMillis
  fun timeAnswer(question: String) = TimeTools.answer(question, nowMs = now, zone = zone)
  val clock = timeAnswer("الساعة كام دلوقتي؟")
  check("سؤال الساعة", clock != null && Regex("\\d{1,2}[:.]\\d{2}").containsMatchIn(clock!!.text))
  val today = timeAnswer("النهاردة كام؟")
  check("تاريخ النهاردة", today?.text?.contains("29/10/2026") == true && today.text.contains("الخميس"))
  check("بكرة", timeAnswer("بكرة يوافق كام؟")?.text?.contains("30/10/2026") == true)
  check("امبارح يوم إيه", timeAnswer("امبارح كان يوم إيه؟")?.text?.contains("الأربعاء") == true)
  check("بعد بكرة", timeAnswer("بعد بكرة تاريخه كام؟")?.text?.contains("31/10/2026") == true)
  val hijri = timeAnswer("إيه التاريخ الهجري النهاردة؟")
  check("التاريخ الهجري يحتوي اسم شهر", hijri != null && Regex("(محرم|صفر|ربيع|جمادى|رجب|شعبان|رمضان|شوال|ذو)").containsMatchIn(hijri!!.text))
  println("      هجري محسوب: ${hijri?.text?.lines()?.firstOrNull()}")
  check("الشهر الهجري", timeAnswer("الشهر الهجري الحالي إيه؟")?.text?.contains("الشهر الهجري") == true)
  check("عدّ الجمعة", timeAnswer("كام يوم للجمعة؟")?.text?.contains("الجمعة") == true)
  check("لا استيلاء على أوامر الجدولة", timeAnswer("فكرني بكرة الساعة 8 بمراجعة البحث") == null)
  check("لا استيلاء على مواقيت الصلاة", timeAnswer("مواقيت الصلاة النهاردة") == null)
  check("نص غير زمني", timeAnswer("مرحبا يا زهرة") == null)

  val hijriCalc = TabularHijriCalculator()
  var previousSerial = -1L
  var monotonic = true
  for (offset in 0..40) {
    val date = hijriCalc.fromEpochMillis(now + offset * 86_400_000L, zone)
    val serial = date.year * 12L * 30L + date.month * 30L + date.day
    if (serial <= previousSerial) monotonic = false
    previousSerial = serial
    if (date.month !in 1..12 || date.day !in 1..30) monotonic = false
  }
  check("تدرّج التاريخ الهجري الحسابي", monotonic)

  println("== تقويم أم القرى المدمج: مطابقة مرجع JDK (java.time.chrono.HijrahDate) ==")
  val tableCalculator = UmmAlQuraHijriCalculator()
  var hijriMismatch = 0
  var hijriTotal = 0
  var cursor = java.time.LocalDate.of(2019, 1, 1)
  val hijriEnd = java.time.LocalDate.of(2076, 12, 31)
  while (!cursor.isAfter(hijriEnd)) {
    val reference = java.time.chrono.HijrahDate.from(cursor)
    val expected = "${reference.get(java.time.temporal.ChronoField.YEAR)}-" +
      "${reference.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)}-" +
      "${reference.get(java.time.temporal.ChronoField.DAY_OF_MONTH)}"
    val epochMs = cursor.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val actual = tableCalculator.fromEpochMillis(epochMs, TimeZone.getTimeZone("UTC"))
    hijriTotal++
    if (expected != "${actual.year}-${actual.month}-${actual.day}") hijriMismatch++
    cursor = cursor.plusDays(1)
  }
  check("مطابقة جدول أم القرى لمرجع java.time", hijriMismatch == 0, "اختلافات=$hijriMismatch من $hijriTotal يومًا")
  println("      أيام مقارنة=$hijriTotal اختلافات=$hijriMismatch")
  val anchor = tableCalculator.fromEpochMillis(
    java.time.LocalDate.of(2018, 9, 11).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
    TimeZone.getTimeZone("UTC")
  )
  check("مرساة 2018-09-11 = 1440-01-01", anchor.year == 1440 && anchor.month == 1 && anchor.day == 1, anchor.toString())
  val hijriNowAnchor = tableCalculator.fromEpochMillis(now, zone)
  check("تاريخ 2026-10-29 الهجري = 1448-05-18", hijriNowAnchor.year == 1448 && hijriNowAnchor.month == 5 && hijriNowAnchor.day == 18, hijriNowAnchor.toString())

  println("== MiniJson / Cache / Policy ==")
  val json = """{"a":{"b":[1,2,{"c":"نص"}]},"t":true,"z":null,"n":-1.5}"""
  val root = MiniJson.parse(json)
  check("قراءة مسار متداخل", root.dig("a", "b")?.at(2)?.stringOrNull("c") == "نص")
  check("قراءة عدد", root.numberOrNull("n") == -1.5)
  check("رفض JSON تالف", MiniJson.parseOrNull("{") == null && MiniJson.parseOrNull("""{"a":1,}""") == null)

  var clockNow = 0L
  val cache = SourceCache(clock = { clockNow }, maxEntries = 2)
  cache.put("a", "1", CacheKind.WEATHER)
  clockNow += 1000
  cache.put("b", "2", CacheKind.FACT)
  val lruKeepsRecent = cache.get("a")?.payload == "1"
  cache.put("c", "3", CacheKind.GENERIC)
  check("LRU يحذف الأقدم استخدامًا", lruKeepsRecent && cache.get("b") == null && cache.get("a") != null)
  clockNow += CacheKind.WEATHER.ttlMs + 1
  check("انتهاء TTL للطقس", cache.get("a") == null && cache.get("c") != null)

  val restored = SourceCache(clock = { 42L })
  val source2 = SourceCache(clock = { 42L })
  source2.put("weather|cairo", """{"temp":31}""", CacheKind.WEATHER)
  source2.put("wiki|خط", "نص\nمتعدد|خطوط", CacheKind.FACT)
  restored.restore(source2.serialize())
  check("حفظ/استرجاع الكاش", restored.size() == 2 && restored.get("weather|cairo")?.payload == """{"temp":31}""")

  check("بوابة الشبكة تمنع افتراضيًا", NetworkPolicy().gate("الطقس", NetworkRequirement.OPT_IN) is NetworkDecision.Blocked)
  check("بوابة الشبكة تسمح بعد Opt-in", NetworkPolicy(NetworkMode.OPT_IN_CONNECTED).gate("الطقس", NetworkRequirement.OPT_IN) is NetworkDecision.Allowed)
  check("الوضع الافتراضي آمن عند قيمة تالفة", NetworkPolicy.fromPersistedValue("garbage") == NetworkMode.OFFLINE_ONLY)

  println("== OpenMeteo / Wikipedia / Wikidata (ناقل وهمي) ==")
  class FakeTransport(private val response: HttpResult) : HttpTransport {
    val calls = mutableListOf<String>()
    override fun get(url: String, headers: Map<String, String>): HttpResult {
      calls.add(url)
      return response
    }
  }

  val weatherJson = """{"current":{"time":"2026-10-27T12:00","temperature_2m":31.4,"weather_code":2,"wind_speed_10m":12.5}}"""
  val offlineTransport = FakeTransport(HttpResult.Success(weatherJson, 200))
  val offlineResult = OpenMeteoClient(offlineTransport, NetworkPolicy(), SourceCache()).fetch(Cities.egyptAndArab.first())
  check("لا طلب شبكة في الوضع الأوفلاين", offlineResult is DataToolResult.Failure && offlineTransport.calls.isEmpty())

  val onlineTransport = FakeTransport(HttpResult.Success(weatherJson, 200))
  val fixedWeatherNow = 1_700_000_000_000L
  val onlineClient = OpenMeteoClient(
    onlineTransport,
    NetworkPolicy(NetworkMode.OPT_IN_CONNECTED),
    SourceCache(clock = { fixedWeatherNow }),
    clock = { fixedWeatherNow }
  )
  val weatherEnvelope = (onlineClient.fetch(Cities.egyptAndArab.first()) as? DataToolResult.Answer)?.envelope
  check("قراءة الطقس من الاستجابة", weatherEnvelope != null && weatherEnvelope.text.contains("31.4") && weatherEnvelope.text.contains("غائم جزئيًا"))
  check("بطاقة المصدر تحمل الرخصة", weatherEnvelope?.license == "CC BY 4.0" && weatherEnvelope.sourceName == "Open-Meteo")
  check("بطاقة الطقس تُظهر المصدر والرخصة والحالة", weatherEnvelope?.chipText()?.let {
    it.contains("Open-Meteo") && it.contains("CC BY 4.0") && it.contains("حي") && it.isNotBlank()
  } == true)
  val cachedEnvelope = (onlineClient.fetch(Cities.egyptAndArab.first()) as? DataToolResult.Answer)?.envelope
  check("القراءة الثانية من الكاش", cachedEnvelope?.cached == true, "cached=${cachedEnvelope?.cached} calls=${onlineTransport.calls.size}")
  check("لا طلب شبكي ثانٍ خلال مدة الصلاحية", onlineTransport.calls.size == 1, "calls=${onlineTransport.calls.size}")

  check("فشل الشبكة لا يُنتج رقمًا", OpenMeteoClient(FakeTransport(HttpResult.Failure("لا اتصال")), NetworkPolicy(NetworkMode.OPT_IN_CONNECTED), SourceCache())
    .fetch(Cities.egyptAndArab.first()) is DataToolResult.Failure)
  check("استجابة طقس تالفة تُرفض", OpenMeteoClient.parseReading("""{"current":{}}""") == null)

  val wikiJson = """{"query":{"pages":[{"pageid":1,"title":"الخوارزمي","extract":"محمد بن موسى الخوارزمي عالم رياضيات.","fullurl":"https://ar.wikipedia.org/wiki/الخوارزمي"}]}}"""
  val wiki = WikipediaClient.parseSummary(wikiJson)
  check("تحليل استجابة ويكيبيديا", wiki?.title == "الخوارزمي" && wiki.extract.contains("رياضيات"))
  check("رابط ويكيبيديا يستخدم Action API و origin=*", WikipediaClient.summaryUrl("الخوارزمي").let {
    it.contains("action=query") && it.contains("origin=*")
  })
  check("ويكيبيديا ترفض أوفلاين", run {
    val t = FakeTransport(HttpResult.Success(wikiJson, 200))
    WikipediaClient(t, NetworkPolicy(), SourceCache()).fetch("الخوارزمي") is DataToolResult.Failure && t.calls.isEmpty()
  })
  val bindings = WikidataClient.parseBindings("""{"results":{"bindings":[{"itemLabel":{"type":"literal","value":"القاهرة"}}]}}""")
  check("تحليل نتائج ويكي‌داتا", bindings.size == 1 && bindings[0]["itemLabel"] == "القاهرة")

  println("== D3: قصاصات الكود بلا تنفيذ ==")
  val snippet = CodeSnippets.find("اكتب لي حلقة بايثون")
  check("توجد قصاصة مطابقة", snippet != null && snippet!!.lines.isNotEmpty() && snippet.expectedOutput != null)
  check("التنفيذ معطّل صراحةً", DisabledCodeRunner.run("python", "print(1)") is CodeRunResult.Unavailable)
  check("لا اختراع كود لطلب غير مدعوم", CodeSnippets.find("اكتب كود بـ Rust") == null)

  println("== MathEngine (توافق الواجهة القديمة) ==")
  val engineDivision = com.example.zahra.engines.MathEngine.evaluate("5 / 0")
  check("قسمة على صفر ⇒ EXECUTION_ERROR", !engineDivision.isSuccess && engineDivision.outcomeState == OutcomeState.EXECUTION_ERROR)
  val engineEmpty = com.example.zahra.engines.MathEngine.evaluate("")
  check("تعبير فارغ ⇒ AMBIGUOUS", engineEmpty.outcomeState == OutcomeState.AMBIGUOUS)
  val engineSqrt = com.example.zahra.engines.MathEngine.evaluate("sqrt(-16)")
  check("جذر سالب ⇒ EXECUTION_ERROR", engineSqrt.outcomeState == OutcomeState.EXECUTION_ERROR)
  val engineArabic = com.example.zahra.engines.MathEngine.evaluate("٢٥ ضرب ٤ زائد ١٠")
  check("كلمات عربية ⇒ 110", engineArabic.numericResult == 110.0)
  val engineKm = com.example.zahra.engines.MathEngine.convertUnit(10.0, "km", "mi")
  check("convertUnit القديم يحفظ النص", engineKm.isSuccess && engineKm.formattedText.contains("6.21"))
  val engineUnknown = com.example.zahra.engines.MathEngine.convertUnit(50.0, "kg", "كيلوفولت")
  check("وحدة غير مدعومة ⇒ UNSUPPORTED + نص صريح", engineUnknown.outcomeState == OutcomeState.UNSUPPORTED && engineUnknown.formattedText.contains("غير مدعوم"))
  val engineWordProblem = com.example.zahra.engines.MathEngine.solveWordProblem("500 وخصم 20%")
  check("المسألة الكلامية عبر MathEngine", engineWordProblem?.numericResult == 400.0)

  println()
  println("RESULTS: passed=$passed failed=$failed")
  if (failed > 0) {
    println("FAILURES:")
    failures.forEach { println(" - $it") }
    kotlin.system.exitProcess(1)
  }
}
