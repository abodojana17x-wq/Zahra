package com.example.zahra.tools

import com.example.zahra.model.OutcomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات طبقة البيانات (المسار E) بلا شبكة: ناقل وهمي + لقطات استجابة حقيقية الشكل.
 * أي فشل شبكي يجب أن يُنتج رفضًا صريحًا، لا رقمًا مخترعًا.
 */
class DataToolsTest {

  // ——— MiniJson ———
  @Test
  fun miniJson_parsesNestedStructures() {
    val json = """{"a":{"b":[1,2,{"c":"نص"}]},"t":true,"z":null,"n":-1.5}"""
    val root = MiniJson.parse(json)
    assertEquals("نص", root.dig("a", "b")!!.at(2)!!.stringOrNull("c"))
    assertEquals(1.0, root.dig("a", "b")!!.at(0)!!.let { (it as JsonValue.Num).value }, 1e-9)
    assertEquals(true, (root.field("t") as JsonValue.Bool).value)
    assertEquals(JsonValue.Null, root.field("z"))
    assertEquals(-1.5, root.numberOrNull("n")!!, 1e-9)
  }

  @Test
  fun miniJson_handlesEscapesAndUnicode() {
    val root = MiniJson.parse("""{"s":"سطر\nثانٍ \"مقتبس\" \u0645"}""")
    val text = root.stringOrNull("s")!!
    assertTrue(text.contains("\n"))
    assertTrue(text.contains("\"مقتبس\""))
    assertTrue(text.contains("م"))
  }

  @Test
  fun miniJson_rejectsMalformedJson() {
    assertNull(MiniJson.parseOrNull("{"))
    assertNull(MiniJson.parseOrNull("""{"a":}"""))
    assertNull(MiniJson.parseOrNull("not json"))
    assertNull(MiniJson.parseOrNull("""{"a":1,}"""))
  }

  // ——— الشبكة: البوابة ———
  @Test
  fun offlinePolicy_blocksNetworkTools() {
    val policy = NetworkPolicy() // OFFLINE_ONLY افتراضيًا
    val decision = policy.gate("الطقس", NetworkRequirement.OPT_IN)
    assertTrue(decision is NetworkDecision.Blocked)
    assertTrue((decision as NetworkDecision.Blocked).message.contains("الوضع المتصل"))
  }

  @Test
  fun optInPolicy_allowsAndPersistedValueIsSafe() {
    val policy = NetworkPolicy(NetworkMode.OPT_IN_CONNECTED)
    assertTrue(policy.gate("الطقس", NetworkRequirement.OPT_IN) is NetworkDecision.Allowed)
    assertEquals(NetworkMode.OPT_IN_CONNECTED, NetworkPolicy.fromPersistedValue("OPT_IN_CONNECTED"))
    assertEquals(NetworkMode.OFFLINE_ONLY, NetworkPolicy.fromPersistedValue(null))
    assertEquals(NetworkMode.OFFLINE_ONLY, NetworkPolicy.fromPersistedValue("garbage"))
  }

  @Test
  fun weatherTool_refusesWhenOffline_evenWithTransport() {
    val transport = FakeTransport(HttpResult.Success(weatherJson(31.0, 0), 200))
    val client = OpenMeteoClient(transport, NetworkPolicy(), SourceCache())
    val result = client.fetch(Cities.egyptAndArab.first())
    assertTrue(result is DataToolResult.Failure)
    assertEquals(OutcomeState.UNSUPPORTED, (result as DataToolResult.Failure).outcomeState)
    assertTrue("لا يجوز إرسال طلب شبكي في الوضع الأوفلاين", transport.calls.isEmpty())
  }

  @Test
  fun weatherTool_parsesRealShapeResponse() {
    val transport = FakeTransport(HttpResult.Success(weatherJson(31.4, 2), 200))
    val policy = NetworkPolicy(NetworkMode.OPT_IN_CONNECTED)
    val client = OpenMeteoClient(transport, policy, SourceCache(), clock = { 1_700_000_000_000L })
    val result = client.fetch(Cities.egyptAndArab.first())
    assertTrue(result is DataToolResult.Answer)
    val envelope = (result as DataToolResult.Answer).envelope
    assertTrue(envelope.text.contains("31.4"))
    assertTrue(envelope.text.contains("غائم جزئيًا"))
    assertEquals("CC BY 4.0", envelope.license)
    assertEquals("Open-Meteo", envelope.sourceName)
    assertEquals(1_700_000_000_000L, envelope.fetchedAtMs)
    assertEquals(1, transport.calls.size)
  }

  @Test
  fun weatherTool_secondCallComesFromCacheAndSaysSo() {
    val transport = FakeTransport(HttpResult.Success(weatherJson(27.0, 61), 200))
    val cache = SourceCache(clock = { 1_000_000L })
    var now = 1_000_000L
    val client = OpenMeteoClient(transport, NetworkPolicy(NetworkMode.OPT_IN_CONNECTED), cache, clock = { now })
    val first = (client.fetch(Cities.egyptAndArab[1]) as DataToolResult.Answer).envelope
    assertTrue(!first.cached)

    now += 5 * 60 * 1000L
    val second = (client.fetch(Cities.egyptAndArab[1]) as DataToolResult.Answer).envelope
    assertTrue("القراءة الثانية يجب أن تكون من الكاش", second.cached)
    assertEquals(1, transport.calls.size)

    now += 31 * 60 * 1000L // انتهاء صلاحية الطقس (30 دقيقة)
    val third = client.fetch(Cities.egyptAndArab[1])
    assertTrue(third is DataToolResult.Answer)
    assertEquals(2, transport.calls.size)
  }

  @Test
  fun weatherTool_transportFailureIsHonest() {
    val transport = FakeTransport(HttpResult.Failure("لا يوجد اتصال"))
    val client = OpenMeteoClient(transport, NetworkPolicy(NetworkMode.OPT_IN_CONNECTED), SourceCache())
    val result = client.fetch(Cities.egyptAndArab.first())
    assertTrue(result is DataToolResult.Failure)
    assertEquals(OutcomeState.EXECUTION_ERROR, (result as DataToolResult.Failure).outcomeState)
    assertTrue(result.message.contains("مش هأخترع"))
  }

  @Test
  fun weatherTool_malformedResponseIsRejectedNotGuessed() {
    assertNull(OpenMeteoClient.parseReading("""{"current":{}}"""))
    assertNull(OpenMeteoClient.parseReading("not json"))
  }

  // ——— ويكيبيديا ———
  @Test
  fun wikipediaUrl_usesActionApiWithOriginWildcard() {
    val url = WikipediaClient.summaryUrl("الخوارزمي", "ar")
    assertTrue(url.contains("action=query"))
    assertTrue(url.contains("origin=*"))
    assertTrue(url.startsWith("https://ar.wikipedia.org/w/api.php?"))
  }

  @Test
  fun wikipediaResponse_isParsedWithTitleAndExtract() {
    val json = """
      {"query":{"pages":[{"pageid":1,"title":"الخوارزمي","extract":"محمد بن موسى الخوارزمي عالم رياضيات.",
      "fullurl":"https://ar.wikipedia.org/wiki/الخوارزمي"}]}}
    """.trimIndent()
    val summary = WikipediaClient.parseSummary(json)
    assertNotNull(summary)
    assertEquals("الخوارزمي", summary!!.title)
    assertTrue(summary.extract.contains("رياضيات"))
    assertEquals("https://ar.wikipedia.org/wiki/الخوارزمي", summary.pageUrl)
  }

  @Test
  fun wikipediaMissingPage_returnsNullAndToolRefuses() {
    val json = """{"query":{"pages":[{"title":"صفحة غير موجودة","missing":true}]}}"""
    assertNull(WikipediaClient.parseSummary(json))

    val transport = FakeTransport(HttpResult.Success(json, 200))
    val client = WikipediaClient(transport, NetworkPolicy(NetworkMode.OPT_IN_CONNECTED), SourceCache())
    val result = client.fetch("صفحة غير موجودة")
    assertTrue(result is DataToolResult.Failure)
    assertTrue((result as DataToolResult.Failure).message.contains("لن أؤلّف"))
  }

  @Test
  fun wikipediaAlsoRefusesOffline() {
    val transport = FakeTransport(HttpResult.Success("{}", 200))
    val client = WikipediaClient(transport, NetworkPolicy(), SourceCache())
    assertTrue(client.fetch("أي شيء") is DataToolResult.Failure)
    assertTrue(transport.calls.isEmpty())
  }

  // ——— ويكي‌داتا ———
  @Test
  fun wikidataBindings_areParsed() {
    val json = """
      {"results":{"bindings":[
        {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"},
         "itemLabel":{"type":"literal","value":"القاهرة"}},
        {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q2"},
         "itemLabel":{"type":"literal","value":"الإسكندرية"}}
      ]}}
    """.trimIndent()
    val rows = WikidataClient.parseBindings(json)
    assertEquals(2, rows.size)
    assertEquals("القاهرة", rows[0]["itemLabel"])
    assertTrue(WikidataClient.sparqlUrl("SELECT ?item WHERE {}").contains("format=json"))
  }

  // ——— الكاش ———
  @Test
  fun cache_expiryAndLruBehaviour() {
    var now = 0L
    val cache = SourceCache(clock = { now }, maxEntries = 2)
    cache.put("a", "1", CacheKind.WEATHER)
    now += 1000
    cache.put("b", "2", CacheKind.FACT)

    // قراءة "a" تجعله الأحدث استخدامًا
    assertEquals("1", cache.get("a")!!.payload)
    assertEquals("2", cache.get("b")!!.payload)

    // تجاوز الحد ⇒ حذف الأقل استخدامًا ("b") وليس "a"
    cache.put("c", "3", CacheKind.GENERIC)
    assertEquals(2, cache.size())
    assertNull(cache.get("b"))
    assertNotNull(cache.get("a"))

    // انتهاء صلاحية الطقس (30 دقيقة) بينما تبقى المعلومات 30 يومًا
    now += CacheKind.WEATHER.ttlMs + 1
    assertNull(cache.get("a"))
    assertNotNull(cache.get("c"))
  }

  @Test
  fun cache_serializeAndRestore() {
    val cache = SourceCache(clock = { 42L })
    cache.put("weather|cairo", "{\"temp\":31}", CacheKind.WEATHER)
    cache.put("wiki|خط", "نص\nمتعدد|خطوط", CacheKind.FACT)
    val serialized = cache.serialize()

    val restored = SourceCache(clock = { 42L })
    restored.restore(serialized)
    assertEquals(2, restored.size())
    assertEquals("{\"temp\":31}", restored.get("weather|cairo")!!.payload)
    assertEquals("نص\nمتعدد|خطوط", restored.get("wiki|خط")!!.payload)
  }

  // ——— المدن ———
  @Test
  fun cityLookup_handlesArabicNames() {
    assertEquals("القاهرة", Cities.find("الطقس في القاهرة النهاردة")!!.nameAr)
    assertEquals("الإسكندرية", Cities.find("الجو في الإسكندرية")!!.nameAr)
    assertNull(Cities.find("مدينة غير معروفة"))
  }

  // ——— القصاصات (D3) ———
  @Test
  fun codeSnippets_existButExecutionIsDisabled() {
    val snippet = CodeSnippets.find("اكتب لي حلقة بايثون")
    assertNotNull(snippet)
    assertTrue(snippet!!.lines.isNotEmpty())
    assertNotNull(snippet.expectedOutput)

    val run = DisabledCodeRunner.run("python", snippet.code)
    assertTrue(run is CodeRunResult.Unavailable)
    assertTrue((run as CodeRunResult.Unavailable).message.contains("غير مفعّل"))
  }

  @Test
  fun codeSnippets_noFabricationWhenUnknown() {
    assertNull(CodeSnippets.find("اكتب لي كود بـ Rust"))
  }

  // ——— أدوات مساعدة ———
  private class FakeTransport(private val response: HttpResult) : HttpTransport {
    val calls = mutableListOf<String>()
    override fun get(url: String, headers: Map<String, String>): HttpResult {
      calls.add(url)
      return response
    }
  }

  private fun weatherJson(temperature: Double, code: Int): String = """
    {"latitude":30.05,"longitude":31.25,"current_units":{"temperature_2m":"°C","wind_speed_10m":"km/h"},
     "current":{"time":"2026-10-27T12:00","temperature_2m":$temperature,"weather_code":$code,"wind_speed_10m":12.5}}
  """.trimIndent()
}
