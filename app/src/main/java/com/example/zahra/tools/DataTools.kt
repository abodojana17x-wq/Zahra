package com.example.zahra.tools

import com.example.zahra.model.OutcomeState

/** نتيجة أداة بيانات: إمّا بطاقة مصدر كاملة، أو رفض صريح بسبب معلن. */
sealed class DataToolResult {
  data class Answer(val envelope: SourceEnvelope) : DataToolResult()
  data class Failure(
    val message: String,
    val outcomeState: OutcomeState = OutcomeState.UNSUPPORTED
  ) : DataToolResult()
}

/** إحداثيات المدن المدعومة (بدل الاعتماد على شبكة geocoding في كل طلب). */
data class CityCoordinates(val nameAr: String, val latitude: Double, val longitude: Double)

object Cities {
  val egyptAndArab: List<CityCoordinates> = listOf(
    CityCoordinates("القاهرة", 30.0444, 31.2357),
    CityCoordinates("الإسكندرية", 31.2001, 29.9187),
    CityCoordinates("الجيزة", 30.0131, 31.2089),
    CityCoordinates("أسوان", 24.0889, 32.8998),
    CityCoordinates("الأقصر", 25.6872, 32.6396),
    CityCoordinates("بورسعيد", 31.2653, 32.3019),
    CityCoordinates("الرياض", 24.7136, 46.6753),
    CityCoordinates("جدة", 21.4858, 39.1925),
    CityCoordinates("مكة", 21.3891, 39.8579),
    CityCoordinates("دبي", 25.2048, 55.2708),
    CityCoordinates("الدوحة", 25.2854, 51.5310),
    CityCoordinates("الكويت", 29.3759, 47.9774),
    CityCoordinates("الخرطوم", 15.5007, 32.5599),
    CityCoordinates("عمّان", 31.9539, 35.9106),
    CityCoordinates("بيروت", 33.8938, 35.5018),
    CityCoordinates("بغداد", 33.3152, 44.3661),
    CityCoordinates("الدار البيضاء", 33.5731, -7.5898),
    CityCoordinates("القدس", 31.7683, 35.2137)
  )

  fun find(text: String): CityCoordinates? =
    egyptAndArab
      .sortedByDescending { it.nameAr.length }
      .firstOrNull { text.contains(it.nameAr) || text.contains(it.nameAr.replace("أ", "ا")) }
}

/** قراءة طقس مستخرجة من استجابة Open-Meteo. */
data class WeatherReading(
  val temperatureC: Double,
  val windKmh: Double,
  val conditionAr: String,
  val weatherCode: Int
)

/**
 * عميل Open-Meteo (رخصة البيانات CC BY 4.0، بلا مفتاح).
 *
 * - لا شبكة إلا بعد [NetworkPolicy] بسماح صريح.
 * - كل استجابة ناجحة تُخزَّن في [SourceCache] بمدة 30 دقيقة، والرد يعلن `cached`.
 * - أي فشل يُعيد [DataToolResult.Failure] — لا قيم تقديرية ولا «حرارة تقريبية».
 */
class OpenMeteoClient(
  private val transport: HttpTransport,
  private val policy: NetworkPolicy = NetworkPolicy(),
  private val cache: SourceCache = SourceCache(),
  private val clock: () -> Long = { System.currentTimeMillis() }
) {

  fun fetch(city: CityCoordinates, forceRefresh: Boolean = false): DataToolResult {
    when (val decision = policy.gate("الطقس", NetworkRequirement.OPT_IN)) {
      is NetworkDecision.Blocked -> return DataToolResult.Failure(decision.message, decision.outcomeState)
      NetworkDecision.Allowed -> Unit
    }

    val cacheKey = "open-meteo:${city.latitude},${city.longitude}"
    if (!forceRefresh) {
      cache.get(cacheKey)?.let { entry ->
        val reading = parseReading(entry.payload)
          ?: return DataToolResult.Failure("البيانات المخزّنة غير قابلة للقراءة؛ أعد المحاولة لاحقًا.")
        return DataToolResult.Answer(buildEnvelope(city, reading, entry.storedAtMs, cached = true))
      }
    }

    val url = forecastUrl(city.latitude, city.longitude)
    return when (val response = transport.get(url)) {
      is HttpResult.Failure -> DataToolResult.Failure(
        "تعذّر جلب الطقس: ${response.reason}. مش هأخترع قراءة من عندي.",
        OutcomeState.EXECUTION_ERROR
      )
      is HttpResult.Success -> {
        val reading = parseReading(response.body)
          ?: return DataToolResult.Failure(
            "استجابة الطقس لم تكن بالشكل المتوقع؛ لن أعرض رقمًا غير مؤكد.",
            OutcomeState.EXECUTION_ERROR
          )
        val now = clock()
        cache.put(cacheKey, response.body, CacheKind.WEATHER, now)
        DataToolResult.Answer(buildEnvelope(city, reading, now, cached = false))
      }
    }
  }

  private fun buildEnvelope(
    city: CityCoordinates,
    reading: WeatherReading,
    fetchedAtMs: Long,
    cached: Boolean
  ): SourceEnvelope = SourceEnvelope(
    text = "الطقس في ${city.nameAr} الآن: ${formatTemp(reading.temperatureC)}°م، ${reading.conditionAr}، " +
      "ورياح ${formatTemp(reading.windKmh)} كم/س.",
    sourceName = ZahraSources.OPEN_METEO.name,
    sourceUrl = ZahraSources.OPEN_METEO.url,
    license = ZahraSources.OPEN_METEO.license,
    fetchedAtMs = fetchedAtMs,
    cached = cached,
    confidence = if (cached) 0.9 else 1.0,
    notes = listOf(
      ZahraSources.OPEN_METEO.attributionNote,
      if (cached) "هذه قراءة مخزّنة (أقل من 30 دقيقة) وليست جلبًا لحظيًا." else "قراءة حيّة عند وقت الجلب."
    )
  )

  companion object {
    fun forecastUrl(latitude: Double, longitude: Double, timezone: String = "auto"): String =
      "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
        "&current=temperature_2m,weather_code,wind_speed_10m&timezone=$timezone"

    fun formatTemp(value: Double): String =
      com.example.zahra.math.MathFormatting.formatNumber(value)

    /** يقرأ `current` من استجابة Open-Meteo؛ يعيد `null` بدل قيمة مفترضة. */
    fun parseReading(json: String): WeatherReading? {
      val root = MiniJson.parseOrNull(json) ?: return null
      val current = root.field("current") ?: return null
      val temperature = current.numberOrNull("temperature_2m") ?: return null
      val wind = current.numberOrNull("wind_speed_10m") ?: 0.0
      val code = current.numberOrNull("weather_code")?.toInt() ?: -1
      return WeatherReading(
        temperatureC = temperature,
        windKmh = wind,
        conditionAr = describeWeatherCode(code),
        weatherCode = code
      )
    }

    /** أكواد WMO المستخدمة في Open-Meteo ⇒ وصف عربي. */
    fun describeWeatherCode(code: Int): String = when (code) {
      0 -> "صافٍ"
      1 -> "صافٍ غالبًا"
      2 -> "غائم جزئيًا"
      3 -> "غائم"
      45, 48 -> "شبورة/ضباب"
      51, 53, 55 -> "رذاذ خفيف"
      56, 57 -> "رذاذ متجمّد"
      61, 63, 65 -> "مطر"
      66, 67 -> "مطر متجمّد"
      71, 73, 75 -> "ثلوج"
      77 -> "حبيبات ثلجية"
      80, 81, 82 -> "زخات مطر"
      85, 86 -> "زخات ثلجية"
      95 -> "عاصفة رعدية"
      96, 99 -> "عاصفة رعدية مع برَد"
      else -> "حالة غير معروفة في جدول الأكواد"
    }
  }
}

data class WikiSummary(val title: String, val extract: String, val pageUrl: String)

/**
 * عميل ويكيبيديا عبر **Action API** (`api.php`) مع `origin=*` (المسار الموثّق للـ CORS)،
 * ورخصة المحتوى CC BY-SA 4.0. النص مقتطف تمهيدي (`exintro`) لا مقال كامل.
 */
class WikipediaClient(
  private val transport: HttpTransport,
  private val policy: NetworkPolicy = NetworkPolicy(),
  private val cache: SourceCache = SourceCache(),
  private val clock: () -> Long = { System.currentTimeMillis() }
) {

  fun fetch(query: String, language: String = "ar", forceRefresh: Boolean = false): DataToolResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return DataToolResult.Failure("لا يوجد عنوان أو موضوع للبحث عنه.")
    when (val decision = policy.gate("ويكيبيديا", NetworkRequirement.OPT_IN)) {
      is NetworkDecision.Blocked -> return DataToolResult.Failure(decision.message, decision.outcomeState)
      NetworkDecision.Allowed -> Unit
    }

    val cacheKey = "wikipedia:$language:${trimmed.lowercase()}"
    if (!forceRefresh) {
      cache.get(cacheKey)?.let { entry ->
        val summary = parseSummary(entry.payload)
          ?: return DataToolResult.Failure("محتوى الكاش غير قابل للقراءة.")
        return DataToolResult.Answer(buildEnvelope(summary, entry.storedAtMs, cached = true))
      }
    }

    return when (val response = transport.get(summaryUrl(trimmed, language))) {
      is HttpResult.Failure -> DataToolResult.Failure(
        "تعذّر الوصول إلى ويكيبيديا: ${response.reason}.",
        OutcomeState.EXECUTION_ERROR
      )
      is HttpResult.Success -> {
        val summary = parseSummary(response.body)
          ?: return DataToolResult.Failure(
            "لم أجد مقالًا مطابقًا في ويكيبيديا، ولن أؤلّف تعريفًا من ذاكرتي.",
            OutcomeState.UNSUPPORTED
          )
        val now = clock()
        cache.put(cacheKey, response.body, CacheKind.FACT, now)
        DataToolResult.Answer(buildEnvelope(summary, now, cached = false))
      }
    }
  }

  private fun buildEnvelope(summary: WikiSummary, fetchedAtMs: Long, cached: Boolean): SourceEnvelope =
    SourceEnvelope(
      text = "${summary.title}: ${summary.extract}",
      sourceName = "${ZahraSources.WIKIPEDIA.name} — ${summary.title}",
      sourceUrl = summary.pageUrl,
      license = ZahraSources.WIKIPEDIA.license,
      fetchedAtMs = fetchedAtMs,
      cached = cached,
      confidence = if (cached) 0.85 else 0.9,
      notes = listOf(
        ZahraSources.WIKIPEDIA.attributionNote,
        "المقتطف من مقدمة المقال، وليس نصًا كاملًا."
      )
    )

  companion object {
    fun summaryUrl(title: String, language: String = "ar"): String =
      "https://$language.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&origin=*" +
        "&prop=extracts|info&inprop=url&explaintext=1&exintro=1&redirects=1" +
        "&titles=${UrlEncoding.encode(title)}"

    fun parseSummary(json: String): WikiSummary? {
      val root = MiniJson.parseOrNull(json) ?: return null
      val pagesValue = root.dig("query", "pages") ?: return null
      val page = when (pagesValue) {
        is JsonValue.Arr -> pagesValue.items.firstOrNull { it is JsonValue.Obj }
        is JsonValue.Obj -> pagesValue.fields.values.firstOrNull { it is JsonValue.Obj }
        else -> null
      } ?: return null
      if (page.field("missing") != null) return null
      val title = page.stringOrNull("title") ?: return null
      val extract = page.stringOrNull("extract")?.trim().orEmpty()
      if (extract.isEmpty()) return null
      val url = page.stringOrNull("fullurl") ?: "https://ar.wikipedia.org/wiki/${UrlEncoding.encode(title)}"
      return WikiSummary(title = title, extract = extract, pageUrl = url)
    }
  }
}

/**
 * عميل ويكي‌داتا (SPARQL، رخصة CC0). لا يُستخدم في النواة الأوفلاين،
 * ويحتاج `User-Agent` وطريقة رسمية لتفادي حدود الخدمة.
 */
class WikidataClient(
  private val transport: HttpTransport,
  private val policy: NetworkPolicy = NetworkPolicy(),
  private val clock: () -> Long = { System.currentTimeMillis() }
) {

  fun query(sparql: String): DataToolResult {
    when (val decision = policy.gate("ويكي‌داتا", NetworkRequirement.OPT_IN)) {
      is NetworkDecision.Blocked -> return DataToolResult.Failure(decision.message, decision.outcomeState)
      NetworkDecision.Allowed -> Unit
    }
    return when (val response = transport.get(sparqlUrl(sparql), mapOf("Accept" to "application/sparql-results+json"))) {
      is HttpResult.Failure -> DataToolResult.Failure(
        "تعذّر الوصول إلى ويكي‌داتا: ${response.reason}.",
        OutcomeState.EXECUTION_ERROR
      )
      is HttpResult.Success -> {
        val bindings = parseBindings(response.body)
        if (bindings.isEmpty()) {
          DataToolResult.Failure("لم تُرجع ويكي‌داتا أي نتيجة لهذا الاستعلام.", OutcomeState.AMBIGUOUS)
        } else {
          val lines = bindings.take(5).joinToString("\n") { row ->
            row.entries.joinToString(" · ") { (key, value) -> "$key: $value" }
          }
          DataToolResult.Answer(
            SourceEnvelope(
              text = lines,
              sourceName = ZahraSources.WIKIDATA.name,
              sourceUrl = ZahraSources.WIKIDATA.url,
              license = ZahraSources.WIKIDATA.license,
              fetchedAtMs = clock(),
              cached = false,
              confidence = 0.9,
              notes = listOf(ZahraSources.WIKIDATA.attributionNote)
            )
          )
        }
      }
    }
  }

  companion object {
    fun sparqlUrl(sparql: String): String =
      "https://query.wikidata.org/sparql?format=json&query=${UrlEncoding.encode(sparql)}"

    fun parseBindings(json: String): List<Map<String, String>> {
      val root = MiniJson.parseOrNull(json) ?: return emptyList()
      val bindings = root.dig("results", "bindings") as? JsonValue.Arr ?: return emptyList()
      return bindings.items.mapNotNull { row ->
        val fields = (row as? JsonValue.Obj)?.fields ?: return@mapNotNull null
        val mapped = LinkedHashMap<String, String>()
        for ((key, value) in fields) {
          val text = value.stringOrNull("value") ?: continue
          mapped[key] = text
        }
        mapped
      }
    }
  }
}
