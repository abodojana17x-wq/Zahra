package com.example.advanced

import com.example.zahra.advanced.AdvancedDecision
import com.example.zahra.advanced.AdvancedModeGate
import com.example.zahra.advanced.AdvancedModeSettings
import com.example.zahra.advanced.DeviceProfile
import com.example.zahra.advanced.DisabledSlmRuntime
import com.example.zahra.advanced.GenerationConstraints
import com.example.zahra.advanced.GroundedGenerator
import com.example.zahra.advanced.GroundedOutcome
import com.example.zahra.advanced.ModelAcceptance
import com.example.zahra.advanced.ModelRegistry
import com.example.zahra.advanced.RequestKind
import com.example.zahra.advanced.SlmModelEntry
import com.example.zahra.advanced.SlmResult
import com.example.zahra.advanced.SlmRuntime
import com.example.zahra.advanced.SlmRuntimeKind
import com.example.zahra.knowledge.unit.IndexParams
import com.example.zahra.knowledge.unit.IndexStats
import com.example.zahra.knowledge.unit.KnowledgeUnit
import com.example.zahra.knowledge.unit.KnowledgeUnitIndex
import com.example.zahra.knowledge.unit.UnitSafety
import com.example.zahra.knowledge.unit.UnitSource
import com.example.zahra.knowledge.unit.UnitTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات سلوك المرحلة 7 (الوضع المتقدّم / النموذج التوليدي المحلي).
 * كل الاختبارات تعمل على JVM بلا Robolectric (الحزمة بلا اعتماد أندرويد).
 *
 * ملاحظة (D20): هذه الاختبارات توثّق السلوك ولا يُشترط تشغيلها لإكمال التنفيذ؛
 * وهي غير مُربوطة بأي قياس أداء فعلي للنموذج.
 */
class Phase7AdvancedModeTest {

  // ---- مساعدات ----

  private fun makeUnit(id: String, request: String, answer: String): KnowledgeUnit {
    val toks = UnitTokenizer.indexedTokens(request)
    val terms = toks.associateWith { 1.0 }
    return KnowledgeUnit(
      id = id, pack = "test", title = request, intent = "generate",
      questions = listOf(request), answers = listOf(answer), tags = emptyList(),
      source = UnitSource(name = "مصدر تجريبي", url = "https://example.com/s", license = "CC BY 4.0"),
      authored = false, author = "", verifiedAt = "2026-10-30",
      safety = UnitSafety.NONE, weight = 1.0,
      docLength = toks.size.coerceAtLeast(1), terms = terms
    )
  }

  private fun buildIndex(unit: KnowledgeUnit): KnowledgeUnitIndex = KnowledgeUnitIndex(
    schemaVersion = 1, builtAt = "2026-10-30", builderVersion = "test",
    params = IndexParams(),
    stats = IndexStats(
      unitCount = 1,
      avgDocLength = unit.docLength.toDouble().coerceAtLeast(1.0),
      bm25Ref = 1.0
    ),
    documentFrequency = unit.terms.mapValues { 1 },
    units = listOf(unit),
    contentHash = ""
  )

  private class FakeSlmRuntime(
    private val canned: String,
    private val available: Boolean = true
  ) : SlmRuntime {
    var generateCalls = 0
      private set

    override fun load(model: SlmModelEntry, onProgress: (Float) -> Unit) = available
    override fun isLoaded() = available
    override fun generate(
      prompt: String,
      constraints: GenerationConstraints,
      maxTokens: Int,
      budgetMs: Long
    ): SlmResult {
      generateCalls++
      return SlmResult(
        text = canned, finished = true, tokenCount = canned.length / 4,
        elapsedMs = 50, available = available
      )
    }
    override fun cancel() = Unit
    override fun unload() = Unit
    override fun capabilities() = com.example.zahra.advanced.RuntimeCapabilities(
      supported = available,
      kinds = setOf(SlmRuntimeKind.LLAMA_CPP_GGUF),
      maxContextTokens = 2048, supportsGrammar = true
    )
  }

  private val enabledSettings = AdvancedModeSettings(enabledByUser = true)
  private val bigDevice = DeviceProfile(totalRamMB = 4096, availableRamMB = 3000)

  // ---- التسجيل (المسار A) ----

  @Test
  fun `registry rejects cloud-dependent model (blacklist)`() {
    val cloud = ModelRegistry.candidates.first { it.requiresNetworkForInference }
    assertEquals(ModelAcceptance.BLOCKED_CLOUD, ModelRegistry.assess(cloud))
  }

  @Test
  fun `registry rejects proprietary license (no redistribution)`() {
    val fanar = ModelRegistry.candidates.first { it.id == "fanar-2-q4" }
    assertEquals(ModelAcceptance.BLOCKED_LICENSE, ModelRegistry.assess(fanar))
  }

  @Test
  fun `registry rejects oversized model`() {
    val jais = ModelRegistry.candidates.first { it.id == "jais-13b-chat-q4" }
    assertEquals(ModelAcceptance.BLOCKED_SIZE, ModelRegistry.assess(jais))
  }

  @Test
  fun `registry keeps candidate pending until arabic score is measured`() {
    val qwen = ModelRegistry.candidates.first { it.id == "qwen2.5-1.5b-instruct-q4" }
    assertEquals(ModelAcceptance.PENDING_BENCHMARK, ModelRegistry.assess(qwen))
  }

  @Test
  fun `registry marks measured eligible candidate as ELIGIBLE`() {
    val measured = ModelRegistry.candidates.first { it.id == "rightnow-arabic-0.5b-turbo-q4" }
      .copy(arabicScore = 0.82, egyptianScore = 0.78)
    assertEquals(ModelAcceptance.ELIGIBLE, ModelRegistry.assess(measured, deviceRamMB = 4096))
  }

  @Test
  fun `eligible list is empty before any benchmark`() {
    assertTrue(ModelRegistry.eligible().isEmpty())
  }

  // ---- بوابة الوضع المتقدّم ----

  @Test
  fun `gate routes to core when opt-in disabled`() {
    val d = AdvancedModeGate.decide(
      AdvancedModeSettings(enabledByUser = false), bigDevice, FakeSlmRuntime("x"), RequestKind.GENERATIVE
    )
    assertTrue(d is AdvancedDecision.UseCore)
    assertEquals("disabled", (d as AdvancedDecision.UseCore).code)
  }

  @Test
  fun `gate routes factual and sensitive to core`() {
    val runtime = FakeSlmRuntime("x")
    val factual = AdvancedModeGate.decide(enabledSettings, bigDevice, runtime, RequestKind.FACTUAL)
    val sensitive = AdvancedModeGate.decide(enabledSettings, bigDevice, runtime, RequestKind.SENSITIVE)
    val deterministic = AdvancedModeGate.decide(enabledSettings, bigDevice, runtime, RequestKind.DETERMINISTIC)
    assertTrue(factual is AdvancedDecision.UseCore)
    assertTrue(sensitive is AdvancedDecision.UseCore)
    assertTrue(deterministic is AdvancedDecision.UseCore)
  }

  @Test
  fun `gate routes to core when model not loaded`() {
    val d = AdvancedModeGate.decide(enabledSettings, bigDevice, DisabledSlmRuntime, RequestKind.GENERATIVE)
    assertTrue(d is AdvancedDecision.UseCore)
    assertEquals("no_model", (d as AdvancedDecision.UseCore).code)
  }

  @Test
  fun `gate routes to core on low ram by default`() {
    val lowDevice = DeviceProfile(totalRamMB = 1024, availableRamMB = 700)
    val d = AdvancedModeGate.decide(enabledSettings, lowDevice, FakeSlmRuntime("x"), RequestKind.GENERATIVE)
    assertTrue(d is AdvancedDecision.UseCore)
    assertEquals("low_ram", (d as AdvancedDecision.UseCore).code)
  }

  @Test
  fun `gate uses advanced when enabled loaded and ram sufficient`() {
    val d = AdvancedModeGate.decide(enabledSettings, bigDevice, FakeSlmRuntime("x"), RequestKind.GENERATIVE)
    assertTrue(d is AdvancedDecision.UseAdvanced)
  }

  // ---- المولّد المُؤسَّس ----

  @Test
  fun `generative request with grounded source yields answered result`() {
    val unit = makeUnit("u1", "اكتب بيت شعر قصير عن الصباح", "الصباح نور والضحى أمل.")
    val gen = GroundedGenerator(
      index = buildIndex(unit),
      runtime = FakeSlmRuntime("قصيدة: صباحُ النورِ يبدأُ بالأملِ."),
      settings = enabledSettings,
      device = bigDevice
    )
    val out = gen.generate("اكتب بيت شعر قصير عن الصباح")
    assertTrue(out is GroundedOutcome.Answered)
    val ans = (out as GroundedOutcome.Answered).result
    assertTrue(ans.usedModel)
    assertTrue(ans.sources.isNotEmpty())
    assertTrue(ans.transparencyLabel.contains("مولّد محليًا"))
    assertTrue(ans.transparencyLabel.contains("مصد"))
  }

  @Test
  fun `runtime unavailable falls back to core`() {
    val unit = makeUnit("u2", "اكتب مقدمة عن القراءة", "القراءة باب المعرفة.")
    val gen = GroundedGenerator(
      index = buildIndex(unit),
      runtime = DisabledSlmRuntime,
      settings = enabledSettings,
      device = bigDevice
    )
    val out = gen.generate("اكتب مقدمة عن القراءة")
    assertTrue(out is GroundedOutcome.UseCore)
    assertEquals("no_model", (out as GroundedOutcome.UseCore).code)
  }

  @Test
  fun `unexplained number in output falls back to core`() {
    val gen = GroundedGenerator(
      index = buildIndex(makeUnit("u3", "لخّص النص التالي عن الصلاة", "الصلاة ركن الإسلام.")),
      runtime = FakeSlmRuntime("النتيجة هي ٤٢ كما وضّحنا."),
      settings = enabledSettings,
      device = bigDevice
    )
    val out = gen.generate("لخّص النص التالي عن الصلاة والعبادة")
    assertTrue(out is GroundedOutcome.UseCore)
    assertEquals("post_check", (out as GroundedOutcome.UseCore).code)
  }

  @Test
  fun `sensitive religious request never reaches model`() {
    val runtime = FakeSlmRuntime("فتوى: هذا حرام.")
    val gen = GroundedGenerator(
      index = buildIndex(makeUnit("u4", "ادعي لي بفتوى عن الموسيقى", "لا مصدر.")),
      runtime = runtime,
      settings = enabledSettings,
      device = bigDevice
    )
    val out = gen.generate("ادعي لي بفتوى عن حكم الموسيقى")
    assertTrue(out is GroundedOutcome.UseCore)
    assertEquals(0, runtime.generateCalls) // لم يُستدعَ النموذج أصلًا
  }

  @Test
  fun `factual request routes to core without model call`() {
    val runtime = FakeSlmRuntime("x")
    val gen = GroundedGenerator(
      index = buildIndex(makeUnit("u5", "ما عاصمة مصر", "القاهرة.")),
      runtime = runtime,
      settings = enabledSettings,
      device = bigDevice
    )
    val out = gen.generate("ما هي عاصمة مصر؟")
    assertTrue(out is GroundedOutcome.UseCore)
    assertEquals("factual", (out as GroundedOutcome.UseCore).code)
    assertEquals(0, runtime.generateCalls)
  }

  @Test
  fun `disabled runtime reports unsupported capabilities`() {
    assertFalse(DisabledSlmRuntime.capabilities().supported)
    assertFalse(DisabledSlmRuntime.isLoaded())
  }
}
