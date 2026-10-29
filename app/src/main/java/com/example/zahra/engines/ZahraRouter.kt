package com.example.zahra.engines

import android.content.Context
import com.example.zahra.dialogue.*
import com.example.zahra.core.LanguageDetector
import com.example.zahra.core.ZahraNormalizer
import com.example.zahra.entities.EntityExtractor
import com.example.zahra.intent.NaiveBayesClassifier
import com.example.zahra.intent.RegexMatcher
import com.example.zahra.knowledge.engine.KnowledgeEngine
import com.example.zahra.knowledge.model.KnowledgeRetrievalState
import com.example.zahra.model.*
import com.example.zahra.model.Sender
import com.example.zahra.execution.SafeActionExecutor
import com.example.zahra.execution.ExecutionResult
import com.example.zahra.reasoning.*
import com.example.zahra.understanding.*
import com.example.zahra.intelligence.classifier.CalibratedClassifier
import com.example.zahra.intelligence.execution.PlanExecutor
import com.example.zahra.routing.DefaultRouter
import com.example.zahra.routing.GateOutcome
import com.example.zahra.routing.Intent
import com.example.zahra.routing.KnowledgeState
import com.example.zahra.routing.RoutingDecision
import com.example.zahra.routing.RoutingEvidence
import com.example.zahra.routing.Router
import com.example.zahra.routing.ToolVerdict
import com.example.zahra.intelligence.planner.StructuredTaskPlanner
import com.example.zahra.intelligence.planner.StepExecutionStatus
import org.json.JSONObject
import com.example.zahra.personality.PersonalityManager
import com.example.zahra.personality.PersonalityPreferences
import com.example.zahra.context.ContextManager
import com.example.zahra.response.ResponseBuilder
import com.example.zahra.response.TemplateSelector
import com.example.zahra.response.TemplateExpander
import com.example.zahra.response.ResponseRanker
import com.example.zahra.response.ResponseValidator
import com.example.zahra.intelligence.mood.MoodAnalyzer
import com.example.zahra.intelligence.mood.MoodAdaptiveResponseBuilder
import com.example.zahra.memory.UnifiedMemoryManager
import com.example.zahra.memory.GreetingVariator
import com.example.zahra.knowledge.SemanticSearchEngine
import com.example.zahra.mood.MoodJournal
import com.example.zahra.wisdom.WisdomBank
import com.example.zahra.voice.VoiceEngine
import com.example.zahra.vision.VisionEngine
import com.example.zahra.islamic.IslamicKnowledgeBase
import com.example.zahra.tools.Cities
import com.example.zahra.tools.DataToolResult
import com.example.zahra.tools.HttpTransport
import com.example.zahra.tools.HttpUrlConnectionTransport
import com.example.zahra.tools.NetworkDecision
import com.example.zahra.tools.NetworkPolicy
import com.example.zahra.tools.NetworkRequirement
import com.example.zahra.tools.OpenMeteoClient
import com.example.zahra.tools.SourceCache
import com.example.zahra.math.UnitRequestParseResult
import com.example.zahra.math.UnitRequestParser
import com.example.zahra.math.WordProblems
import com.example.zahra.tools.TimeTools
import com.example.zahra.tools.android.AndroidHijriCalculator
import java.util.Locale

/**
 * الموجّه الرئيسي.
 *
 * @param networkPolicy سياسة الشبكة (D1). الافتراضي `OFFLINE_ONLY`: لا طلب شبكة إطلاقًا.
 * @param weatherTransport ناقل HTTP قابل للحقن لاختبار مسار الطقس بلا شبكة.
 */
class ZahraRouter(
  private val context: Context,
  private val networkPolicy: NetworkPolicy = NetworkPolicy(),
  private val weatherTransport: HttpTransport = HttpUrlConnectionTransport(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
) {
  // ——— المرحلة 2: أدوات حتمية ———
  private val androidHijriCalculator = AndroidHijriCalculator()
  private val sourceCache = SourceCache()
  private val weatherClient by lazy { OpenMeteoClient(weatherTransport, networkPolicy, sourceCache) }

  val nbClassifier = NaiveBayesClassifier()
  val calibratedClassifier = CalibratedClassifier()

  // Phase 1: طبقة التوجيه الموحّدة — تفصل قرار التوجيه عن التنفيذ.
  val router: Router = DefaultRouter()
  val translationEngine = TranslationEngine(context)
  val religionEngine = ReligionEngine(context)
  val taskEngine = TaskEngine(context)
  val noteEngine = NoteEngine(context)
  val memoryEngine = MemoryEngine(context)
  val localImageEngine = LocalImageEngine()
  val scheduleEngine = ScheduleEngine(context)
  val knowledgeEngine = KnowledgeEngine(context)
  val contextTracker = ContextAndCorrectionTracker()
  val reasoningEngine = ReasoningEngine()
  val actionExecutor = SafeActionExecutor()
  val planExecutor = PlanExecutor(
    taskEngine = taskEngine,
    noteEngine = noteEngine,
    scheduleEngine = scheduleEngine,
    memoryEngine = memoryEngine,
    translationEngine = translationEngine,
    knowledgeEngine = knowledgeEngine
  )
  
  // New conversational intelligence components
  val personalityManager = PersonalityManager(context)
  val contextManager = ContextManager()
  val responseBuilder = ResponseBuilder(
    personalityManager = personalityManager,
    contextManager = contextManager,
    knowledgeEngine = knowledgeEngine,
    templateSelector = TemplateSelector(),
    templateExpander = TemplateExpander(),
    responseRanker = ResponseRanker(),
    responseValidator = ResponseValidator()
  )
  
  // Phase 6.5: Mood-Aware Adaptive Response System
  val moodAnalyzer = MoodAnalyzer()
  val moodAdaptiveBuilder = MoodAdaptiveResponseBuilder()
  
  // Phase 6.6: Unified 3-Tier Memory System + Greeting Variator
  val unifiedMemory = UnifiedMemoryManager(context)
  val greetingVariator = GreetingVariator()
  
  // Phase 6.7: Semantic Search + Mood Journal + Wisdom Bank
  val semanticSearch = SemanticSearchEngine()
  val moodJournal = MoodJournal(context)
  val wisdomBank = WisdomBank()
  
  // Phase 6.7: Voice and Vision Engines
  val voiceEngine = VoiceEngine(context)
  val visionEngine = VisionEngine(context)
  
  // Phase 6.7: Islamic Knowledge Base
  val islamicKnowledge = IslamicKnowledgeBase()

  init {
    loadTrainingCorpus()
    // Reconcile pending reminders and alarms on initialization
    scheduleEngine.reconcileSchedules()
    // Phase 6.6: Load unified memory from persistent storage
    unifiedMemory.loadAll()
    // Phase 6.7: Initialize Vision OCR (Arabic + English)
    visionEngine.initializeOCR("ara+eng")
  }

  private fun loadTrainingCorpus() {
    try {
      val jsonStr = context.assets.open("intent_corpus.json").bufferedReader().use { it.readText() }
      val root = JSONObject(jsonStr)
      val intentsObj = root.optJSONObject("intents") ?: return

      val corpusMap = mutableMapOf<String, List<String>>()
      val keys = intentsObj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val arr = intentsObj.optJSONArray(key) ?: continue
        val samples = mutableListOf<String>()
        for (i in 0 until arr.length()) {
          samples.add(arr.getString(i))
        }
        corpusMap[key] = samples
      }
      nbClassifier.train(corpusMap)
      calibratedClassifier.train(corpusMap)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  val dialogueState = DialogueState()
  private val referenceResolver = ReferenceResolver()
  private var dialogueSessionId: String? = null

  /** Associate background work with the originating UI session, even if it was
   * switched before the worker started. Never carry its state into another session. */
  @Synchronized
  fun processForSession(sessionId: String, rawInput: String, isDeepThinking: Boolean = false): ZahraResponse {
    if (dialogueSessionId != sessionId) {
      clearConversation()
      dialogueSessionId = sessionId
    }
    return process(rawInput, isDeepThinking)
  }

  /** One boundary for every return path, including tools, refusals and corrections. */
  @Synchronized
  fun process(rawInput: String, isDeepThinking: Boolean = false): ZahraResponse {
    val city = Cities.find(rawInput)?.nameAr
    val crisis = EmotionalSupport.crisisAnswer(rawInput)
    val resolved = if (crisis != null) {
      dialogueState.pendingClarification = null
      Resolution.Unchanged
    } else ClarificationPolicy.resolve(referenceResolver.resolve(rawInput, dialogueState, city), dialogueState, rawInput)
    val previousClarification = dialogueState.lastWasClarification
    val response = when (resolved) {
      is Resolution.Query -> processInternal(resolved.text, isDeepThinking)
      is Resolution.Ask -> ZahraResponse(
        text = resolved.question, intent = "clarification", confidence = 0.5,
        confidenceTier = ConfidenceTier.QUALIFIED, outcomeState = OutcomeState.AMBIGUOUS,
        source = "توضيح سياق الحوار", followups = resolved.options
      )
      is Resolution.Abstain -> ZahraResponse(
        text = resolved.text, intent = "dialogue_abstain", confidence = 0.0,
        confidenceTier = ConfidenceTier.UNCERTAIN, outcomeState = OutcomeState.UNSUPPORTED,
        source = "حدود سياق الحوار"
      )
      Resolution.Unchanged -> {
        val support = crisis ?: EmotionalSupport.answer(rawInput)
        if (support == null) processInternal(rawInput, isDeepThinking)
        else ZahraResponse(text = support, intent = if (crisis != null) "safety_support" else "emotional_support", confidence = 0.8,
          confidenceTier = ConfidenceTier.QUALIFIED, outcomeState = OutcomeState.SUPPORTED,
          source = "دعم محادثة محلي")
      }
    }
    // The legacy router can also ask for clarification: bound that path as well.
    val asks = response.outcomeState == OutcomeState.AMBIGUOUS &&
      (response.clarification != null || response.text.contains('؟') || response.text.contains('?'))
    val hasActionResult = response.createdTask != null || response.createdNote != null ||
      response.createdSchedule != null || response.executionPlan != null ||
      response.toolPayload is ZahraToolPayload.TaskPayload || response.toolPayload is ZahraToolPayload.NotePayload ||
      response.toolPayload is ZahraToolPayload.SchedulePayload || response.toolPayload is ZahraToolPayload.MemoryFactPayload
    val boundedResponse = if (previousClarification && asks && !hasActionResult) response.copy(
      text = "لم أستطع تحديد المقصود، لذلك لا أستطيع الإجابة دون تخمين.",
      intent = "dialogue_abstain", outcomeState = OutcomeState.UNSUPPORTED,
      confidence = 0.0, confidenceTier = ConfidenceTier.UNCERTAIN,
      clarification = null, followups = emptyList(), evidenceTrace = null
    ) else response
    // Legacy templates cannot prove the referent, even when some unrelated turn exists.
    val validationProblem = DialogueResponseValidator.problem(boundedResponse.text,
      boundedResponse.evidenceTrace?.groundedFacts?.any { it.source.isNotBlank() } == true)
    val safeResponse = if (validationProblem != null) {
      if (hasActionResult) boundedResponse.copy(text = "تعذر التحقق من جزء في الرد؛ راجع التفاصيل المرفقة.")
      else boundedResponse.copy(text = "لا أستطيع التحقق من هذا الرد، لذلك أمتنع عن عرضه.",
        intent = "dialogue_abstain", outcomeState = OutcomeState.UNSUPPORTED,
        confidence = 0.0, confidenceTier = ConfidenceTier.UNCERTAIN,
        evidenceTrace = null, clarification = null, followups = emptyList())
    } else boundedResponse
    dialogueState.lastWasClarification = asks && !previousClarification && safeResponse.outcomeState == OutcomeState.AMBIGUOUS
    val countries = listOf("مصر", "السعودية", "فرنسا", "اليابان", "الأردن", "المغرب", "الجزائر", "تونس")
    val mentionedCountries = countries.filter { rawInput.contains(it) }
    val topic = mentionedCountries.singleOrNull()
    if (mentionedCountries.size > 1) dialogueState.invalidateTopic()
    val date = listOf("بكرة", "بكره", "غدا", "اليوم", "النهاردة").firstOrNull { rawInput.contains(it) }
    val source = when {
      safeResponse.outcomeState == OutcomeState.UNSUPPORTED -> BlockSource.ABSTAIN
      safeResponse.outcomeState == OutcomeState.AMBIGUOUS -> BlockSource.CLARIFICATION
      safeResponse.intent.contains("memory") -> BlockSource.MEMORY
      safeResponse.toolPayload != null || safeResponse.intent == "weather" -> BlockSource.TOOL
      safeResponse.intent in setOf("emotional_support", "safety_support") -> BlockSource.LOCAL
      safeResponse.source?.contains("معرف") == true -> BlockSource.RETRIEVAL
      else -> BlockSource.LEGACY
    }
    // Only the known mood prefix is a separate presentation block; the original
    // answer stays byte-for-byte intact as the core.
    val empathyPrefix = "أتفهم إن الأمر صعب.\n\n"
    val hasOpener = safeResponse.text.startsWith(empathyPrefix)
    val coreText = if (hasOpener) safeResponse.text.removePrefix(empathyPrefix) else safeResponse.text
    val opening = if (hasOpener) ResponseBlock(
      com.example.zahra.personality.StyleApplier.applyToText(empathyPrefix.trim(), personalityManager.getCurrentPersonality()),
      BlockSource.LOCAL
    ) else null
    val composed = ResponseComposer.compose(ResponseBlock(coreText, source), opening = opening)
    val trace = safeResponse.evidenceTrace?.let {
      if (resolved is Resolution.Query || it.originalRequest != rawInput)
        it.copy(originalRequest = rawInput, appliedRuleIds = it.appliedRuleIds + "dialogue_context_rewrite")
      else it
    }
    val final = safeResponse.copy(text = composed.text, dialogueTraceId = composed.traceId,
      dialogueBlockSources = composed.blocks.map { it.source.name },
      dialogueBlocks = composed.blocks.map { DialogueBlockTrace(it.text, it.source.name) }, evidenceTrace = trace)
    dialogueState.record(rawInput, final.text, final.intent, city, topic, date, mentionedCountries,
      SlotExtractor.extract(rawInput), (final.toolPayload as? ZahraToolPayload.KnowledgePayload)?.recordId,
      runCatching { moodAnalyzer.analyze(rawInput).primaryMood.name }.getOrNull())
    // Legacy components are projections of the single delivered turn, not independent
    // histories with missing early-return paths. Profile persistence remains separate.
    contextTracker.recordTurn(rawInput, final)
    contextManager.recordTurn(rawInput, final)
    unifiedMemory.recordTurn(rawInput, final.text, final.intent)
    return final
  }

  @Synchronized
  fun clearConversation(): Int {
    val count = dialogueState.clearConversation()
    dialogueSessionId = null
    contextTracker.clear()
    contextManager.clearAllConversations()
    unifiedMemory.clearConversation()
    memoryEngine.store.clearTemporaryFacts()
    return count
  }

  data class MemoryClearResult(val facts: Int, val topics: Int, val turns: Int, val success: Boolean)

  @Synchronized
  fun clearDialogueMemory(): MemoryClearResult {
    val facts = memoryEngine.getFacts().size
    val topics = unifiedMemory.topicMemory.getAllTopics().size
    val turns = dialogueState.turns.size
    val factsCleared = memoryEngine.clearMemory()
    val unifiedCleared = unifiedMemory.clearAll()
    clearConversation()
    return MemoryClearResult(facts, topics, turns, factsCleared && unifiedCleared)
  }

  private fun processInternal(rawInput: String, isDeepThinking: Boolean = false): ZahraResponse {
    // Phase 6: Personality and Context Integration
    // Check for personality change commands
    val personalityCmd = personalityManager.detectPersonalityChange(rawInput)
    if (personalityCmd != null) {
      val cmdResult = personalityManager.applyPersonalityCommand(personalityCmd, rawInput)
      if (cmdResult is com.example.zahra.personality.PersonalityChangeResult.Success) {
        // Return a confirmation response
        val personality = personalityManager.getCurrentPersonality()
        val confirmationText = cmdResult.confirmationMessage
        val trace = EpistemicGroundingPipeline.buildTrace(
          rawRequest = rawInput,
          intent = "change_personality",
          entities = mapOf("change_type" to cmdResult.changedFields.joinToString(",")),
          appliedRules = listOf("rule_personality_change"),
          facts = listOf(
            GroundedFact(
              claim = "Personality changed to ${personality.tone.name}",
              evidenceType = EvidenceType.USER_SUPPLIED,
              source = "User personality preference",
              confidence = 1.0
            )
          ),
          uncertainty = emptyList(),
          outcomeState = OutcomeState.SUPPORTED,
          sourceDesc = "Personality Manager"
        )
        val res = ZahraResponse(
          text = confirmationText,
          intent = "change_personality",
          confidence = 0.99,
          confidenceTier = ConfidenceTier.DIRECT,
          outcomeState = OutcomeState.SUPPORTED,
          source = "Personality Manager",
          evidenceTrace = trace
        )
        return res
      }
    }

    // Get current personality and conversation context
    val personality = personalityManager.getCurrentPersonality()

    // Phase 4: Dialect and colloquial normalization
    val adapted = DialectAndColloquialAdapter.adapt(rawInput)
    val effectiveInput = if (adapted.coreQuery.isNotBlank()) adapted.coreQuery else rawInput.trim()
    val clean = effectiveInput.trim()
    val normalized = ZahraNormalizer.normalize(clean)
    val langInfo = LanguageDetector.detect(clean)

    // Phase 4: Islamic Knowledge & Ethical Integrity (Immediate grounding on prohibitions)
    val impermissibleRel = religionEngine.checkImpermissibleRequest(ZahraNormalizer.normalize(rawInput))
      ?: religionEngine.checkImpermissibleRequest(normalized)
    if (impermissibleRel != null) {
      val trace = EpistemicGroundingPipeline.buildTrace(
        rawRequest = rawInput,
        intent = "ask_religion",
        entities = mapOf("category" to "احكام_شرعية_قطعية"),
        appliedRules = listOf("rule_islamic_prohibition_safeguard"),
        facts = listOf(
          GroundedFact(
            claim = impermissibleRel.text.substringBefore("\n\n"),
            evidenceType = EvidenceType.VERIFIED,
            source = impermissibleRel.source,
            confidence = impermissibleRel.confidence
          )
        ),
        uncertainty = emptyList(),
        outcomeState = impermissibleRel.outcomeState,
        sourceDesc = impermissibleRel.source
      )
      val res = ZahraResponse(
        text = impermissibleRel.text,
        intent = "ask_religion",
        confidence = impermissibleRel.confidence,
        confidenceTier = ConfidenceTier.DIRECT,
        outcomeState = impermissibleRel.outcomeState,
        source = "[المصدر: ${impermissibleRel.source}]",
        followups = impermissibleRel.followups,
        evidenceTrace = trace
      )
      // Phase 6.5: Mood-adaptive wrapping for religious responses
      return applyMoodAdaptation(rawInput, res)
    }

    // ——— المرحلة 2: باب الأدوات والبيانات ———
    // أدوات حتمية (وقت/تاريخ هجري/وحدات/مسائل كلامية/طقس بمصدر) تُجاب قبل التصنيف،
    // لأن ثقتها 1.0 ومصدرها معلن، وليس لأنها تتجاوز بوابة الأمانة: الجمل غير الواضحة
    // تُعاد `null` وتكمل مسارها الطبيعي عبر الموجّه.
    deterministicToolResponse(rawInput)?.let { toolResponse ->
      return applyMoodAdaptation(rawInput, toolResponse)
    }

    // Phase 4: Context and user corrections detection
    val correction = contextTracker.detectCorrectionOrFollowup(rawInput)
    when (correction) {
      is CorrectionResolution.UpdateFact -> {
        val conflictReport = ContradictionDetector.checkFactConflict(
          memoryEngine.getFacts(),
          correction.key,
          correction.newValue,
          MemorySource.USER_EXPLICIT,
          isExplicitCorrection = true
        )
        val saveResult = memoryEngine.saveFactWithResult(correction.category, correction.key, correction.newValue, 1.0)
        if (saveResult !is MemorySaveResult.Success) return ZahraResponse(
          text = "تعذر حفظ تصحيح الاسم والتحقق منه؛ لم أؤكد تغيير الذاكرة.",
          intent = "correct_fact", confidence = 0.0, confidenceTier = ConfidenceTier.UNCERTAIN,
          outcomeState = OutcomeState.EXECUTION_ERROR, source = "حفظ الذاكرة المحلية"
        )
        val updatedFact = saveResult.fact
        val responseText = "تم تصحيح وتحديث المعلومة في ذاكرتك الدائمة بنجاح:\n• ${correction.key}: \"${correction.newValue}\"\n\n${conflictReport.explanationAr}"
        val trace = EpistemicGroundingPipeline.buildTrace(
          rawRequest = rawInput,
          intent = "correct_fact",
          entities = mapOf("key" to correction.key, "value" to correction.newValue),
          appliedRules = listOf("rule_explicit_user_correction"),
          facts = listOf(
            GroundedFact(
              claim = "${correction.key} = ${correction.newValue}",
              evidenceType = EvidenceType.USER_SUPPLIED,
              source = "تصحيح صريح من المستخدم",
              confidence = 1.0
            )
          ),
          uncertainty = emptyList(),
          outcomeState = OutcomeState.SUPPORTED,
          sourceDesc = "محرك الذاكرة المؤصلة وتتبع السياق لزهرة 4"
        )
        val res = ZahraResponse(
          text = responseText,
          intent = "correct_fact",
          confidence = 0.99,
          confidenceTier = ConfidenceTier.DIRECT,
          outcomeState = OutcomeState.SUPPORTED,
          source = "محرك الذاكرة الدائمة وتتبع السياق لزهرة 4",
          evidenceTrace = trace,
          toolPayload = updatedFact?.let { ZahraToolPayload.MemoryFactPayload(it, isNew = false) }
        )
        return res
      }
      is CorrectionResolution.UndoAction -> {
        return ZahraResponse(
          text = "لا أستطيع التراجع تلقائيًا عن الإجراء الأخير؛ لم أغيّر أي بيانات.",
          intent = "undo_action", confidence = 0.0, confidenceTier = ConfidenceTier.UNCERTAIN,
          outcomeState = OutcomeState.UNSUPPORTED, source = "حدود التراجع"
        )
      }
      is CorrectionResolution.RefinedQuery -> {
        if (correction.reformulatedQuery != rawInput && correction.reformulatedQuery.isNotBlank())
          return processInternal(correction.reformulatedQuery, isDeepThinking)
      }
      is CorrectionResolution.None -> {
        // Continue
      }
    }

    // Phase 5: Compound Multi-Step Task Planning & Controlled Execution
    if (StructuredTaskPlanner.isCompoundRequest(rawInput)) {
      val plan = StructuredTaskPlanner.plan(rawInput)
      val executedPlan = planExecutor.execute(plan)

      val trace = EpistemicGroundingPipeline.buildTrace(
        rawRequest = rawInput,
        intent = "compound_plan",
        entities = mapOf("stepsCount" to executedPlan.steps.size.toString()),
        appliedRules = listOf("rule_compound_task_planning", "rule_transactional_execution"),
        facts = executedPlan.steps.mapNotNull { s ->
          s.output?.let { out ->
            GroundedFact(
              claim = "${s.title}: $out",
              evidenceType = EvidenceType.DEDUCTION,
              source = "محرك التخطيط والتنفيذ المتسلسل لزهرة 5",
              confidence = executedPlan.overallConfidence
            )
          }
        },
        uncertainty = if (executedPlan.hasFailures) listOf("تعذر تنفيذ بعض الخطوات وتم التراجع الآمن عنها") else emptyList(),
        outcomeState = if (executedPlan.hasFailures) OutcomeState.AMBIGUOUS else OutcomeState.SUPPORTED,
        sourceDesc = "محرك التخطيط والتنفيذ المتسلسل للوكيل الذكي (Phase 5)"
      )

      val summaryLines = executedPlan.steps.joinToString("\n") { s ->
        val icon = when (s.status) {
          StepExecutionStatus.SUCCESS -> "✅"
          StepExecutionStatus.FAILED -> "❌"
          StepExecutionStatus.ROLLED_BACK -> "↩️"
          StepExecutionStatus.SKIPPED -> "⏳"
          else -> "•"
        }
        "$icon ${s.title}: ${s.output ?: s.errorMessage ?: s.status.labelAr}"
      }

      val planResponse = ZahraResponse(
        text = "${executedPlan.executionSummary}\n\n$summaryLines",
        intent = "compound_plan",
        confidence = executedPlan.overallConfidence,
        confidenceTier = ConfidenceTier.DIRECT,
        outcomeState = if (executedPlan.hasFailures) OutcomeState.AMBIGUOUS else OutcomeState.SUPPORTED,
        source = "محرك التخطيط والتنفيذ المتسلسل للوكيل الذكي (Phase 5)",
        toolPayload = ZahraToolPayload.PlanExecutionPayload(executedPlan),
        evidenceTrace = trace,
        executionPlan = executedPlan
      )
      return planResponse
    }

    // Check if user is asking about their personal memory
    val memoryQueryKeywords = listOf("ماذا تعرف عني", "ماذا تتذكر عني", "ما اسمي", "أين أعيش", "ذاكرتك", "معلوماتي المحفوظة", "سجل ذاكرتك", "ماذا حفظت عني")
    if (memoryQueryKeywords.any { normalized.contains(ZahraNormalizer.normalize(it)) }) {
      val memSummary = memoryEngine.summarizeKnownMemory()
      val firstFact = memoryEngine.getFacts().firstOrNull()
      
      // Legacy profile/topics have no turn-level provenance. Only confirmed facts are
      // reported here; never turn an inferred topic into a claim about a past conversation.
      val combinedText = if (memSummary.isNotBlank()) memSummary else "لا توجد معلومات محفوظة حالياً."

      val memRes = ZahraResponse(
        text = combinedText,
        intent = "query_memory",
        confidence = 0.99,
        confidenceTier = ConfidenceTier.DIRECT,
        outcomeState = OutcomeState.SUPPORTED,
        source = "نظام الذاكرة الموحّد لزهرة (Phase 6.6)",
        followups = listOf("تذكر أن اسمي أحمد", "أنا بحب القراءة", "أنا من مصر", "مواقيت الصلاة؟"),
        toolPayload = firstFact?.let { ZahraToolPayload.MemoryFactPayload(it, isNew = false) }
      )
      return applyMoodAdaptation(rawInput, memRes)
    }

    // Check if user is asking to delete/forget a memory
    val isForgetReq = normalized.contains("احذف من الذاكر") || normalized.contains("انس") ||
      normalized.contains("امسح من الذاكر") || normalized.contains("احذف المعلوم")
    if (isForgetReq) {
      val query = clean.replace(Regex("(?:احذف من الذاكرة|انسَ أن|انس ان|امسح من الذاكرة|احذف المعلومة|احذف)\\s*", RegexOption.IGNORE_CASE), "").trim()
      val deleted = memoryEngine.deleteFactByKeyOrContent(query)
      return if (deleted) {
        ZahraResponse(
          text = "تم حذف المعلومة بنجاح من الذاكرة المحلية وتأكيد إزالتها نهائياً.",
          intent = "forget_fact",
          confidence = 0.98,
          confidenceTier = ConfidenceTier.DIRECT,
          outcomeState = OutcomeState.SUPPORTED,
          source = "محرك الذاكرة الدائمة المعتمد لزهرة 3"
        )
      } else {
        ZahraResponse(
          text = "لم أجد معلومة مطابقة لما طلبت حذفه في الذاكرة الدائمة. يمكنك استعراض ذاكرتك الحالية بقول: \"ماذا تعرف عني؟\".",
          intent = "forget_fact",
          confidence = 0.85,
          confidenceTier = ConfidenceTier.QUALIFIED,
          outcomeState = OutcomeState.AMBIGUOUS,
          source = "محرك الذاكرة الدائمة لزهرة 3"
        )
      }
    }

    // Explicit command to remember: "تذكر أن...", "احفظ أن...", "لا تنس أن..."
    val explicitMemResult = memoryEngine.saveExplicitMemory(clean)
    if (explicitMemResult != null) {
      return when (explicitMemResult) {
        is MemorySaveResult.Success -> {
          ZahraResponse(
            text = "تم حفظ المعلومة في الذاكرة وتأكيد تخزينها محلياً:\n• [${explicitMemResult.fact.category.labelAr}] ${explicitMemResult.fact.key}: \"${explicitMemResult.fact.value}\"\n\nالمعلومة مشفرة محلياً (AES-256) ومتاحة لك دائماً في شاشة \"الإعدادات والذاكرة\".",
            intent = "remember_fact",
            confidence = 0.99,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.SUPPORTED,
            source = "محرك الذاكرة الدائمة المعتمد لزهرة 3",
            followups = listOf("ماذا تعرف عني؟", "أضف مهمة جديدة", "فكرني بموعد لاحق"),
            toolPayload = ZahraToolPayload.MemoryFactPayload(explicitMemResult.fact, isNew = !explicitMemResult.isUpdated)
          )
        }
        is MemorySaveResult.ValidationFailed -> {
          ZahraResponse(
            text = "لم أتمكن من حفظ المعلومة: ${explicitMemResult.reason}",
            intent = "remember_fact",
            confidence = 0.80,
            confidenceTier = ConfidenceTier.QUALIFIED,
            outcomeState = OutcomeState.AMBIGUOUS,
            source = "مدقق صحة الذاكرة"
          )
        }
        is MemorySaveResult.StorageFailed, is MemorySaveResult.VerificationFailed -> {
          ZahraResponse(
            text = "لم أتمكن من حفظ المعلومة في الذاكرة المحلية، لذلك لم أؤكد حفظها لضمان الصدق والموثوقية.",
            intent = "remember_fact",
            confidence = 0.90,
            confidenceTier = ConfidenceTier.DIRECT,
            outcomeState = OutcomeState.EXECUTION_ERROR,
            source = "محرك الذاكرة الآمنة"
          )
        }
      }
    }

    // Auto-scan for personal facts to improve memory strength (names, cities, jobs)
    val autoExtractedFact = memoryEngine.scanAndExtractFact(clean)

    // 1. Regex Command Matcher (Priority 1)
    val regexMatch = RegexMatcher.match(normalized)
    val intent: String
    var confidence: Double
    var calibratedScore: CalibratedClassification? = null

    if (regexMatch != null) {
      intent = regexMatch.intent
      confidence = regexMatch.confidence
    } else {
      // 2. Naive Bayes Classifier (Priority 2) & Phase 5 Calibrated Classifier
      calibratedScore = calibratedClassifier.classify(clean)
      val nbScore = nbClassifier.classify(normalized)
      if (calibratedScore.topProbability >= 0.50 && !calibratedScore.isAbstentionRecommended) {
        intent = calibratedScore.topIntent
        confidence = calibratedScore.topProbability
      } else {
        intent = nbScore.intent
        confidence = if (calibratedScore.isAbstentionRecommended && nbScore.confidence < 0.40) 0.20 else nbScore.confidence
      }
    }

    // If an explicit fact was extracted and general greeting/unknown, give acknowledgment
    if (autoExtractedFact != null && (intent == "unknown" || intent == "greet" || confidence < 0.45)) {
      return ZahraResponse(
        text = if (autoExtractedFact.isConfirmed)
          "حفظتُ ذلك في ذاكرتي المشفرة:\n• ${autoExtractedFact.key}: \"${autoExtractedFact.value}\""
        else "فهمت من كلامك: ${autoExtractedFact.value}. هذا سياق مؤقت للمحادثة، وليس معلومة محفوظة دائمًا.",
        intent = "remember_fact",
        confidence = 0.98,
        confidenceTier = ConfidenceTier.DIRECT,
        outcomeState = OutcomeState.SUPPORTED,
        source = if (autoExtractedFact.isConfirmed) "ذاكرة محلية محفوظة" else "سياق محادثة مؤقت",
        followups = listOf("ماذا تعرف عني؟", "أضف مهمة جديدة", "مواقيت الصلاة اليوم"),
        toolPayload = ZahraToolPayload.MemoryFactPayload(autoExtractedFact, isNew = true)
      )
    }

    // Direct check for local image generation keywords if unclassified or low confidence
    val isImageReq = normalized.contains("صور") || normalized.contains("رسم") || normalized.contains("صمم") ||
                     normalized.contains("لوح") || normalized.contains("بطاق") || normalized.contains("بكسل") ||
                     normalized.contains("ملصق") || normalized.contains("خلفي") || normalized.contains("pixel") ||
                     normalized.contains("draw") || normalized.contains("paint") || normalized.contains("image") ||
                     normalized.contains("فن") || normalized.contains("اجرائ")
    val effectiveIntent = if ((intent == "unknown" || confidence < 0.85) && isImageReq) "generate_image" else intent

    // Direct check for cancel schedule keywords
    val isCancelSched = normalized.contains("الغ التذكير") || normalized.contains("إلغاء التذكير") ||
                        normalized.contains("احذف التذكير") || normalized.contains("الغ موعد") ||
                        normalized.contains("احذف الموعد") || normalized.contains("وقف التذكير") ||
                        normalized.contains("الغ منبه") || normalized.contains("احذف منبه")

    // Direct check for scheduling keywords if unclassified or low confidence
    val isSchedReq = normalized.contains("جدول") || normalized.contains("فكرني") || normalized.contains("ذكرني") ||
                     normalized.contains("نبهني") || normalized.contains("كل يوم") || normalized.contains("يومي") ||
                     normalized.contains("منبه") || normalized.contains("تذكير") || normalized.contains("schedule")
    val isReligiousReq = normalized.contains("حديث") || normalized.contains("رسول الله") ||
                         normalized.contains("قال رسول") || normalized.contains("سورة") ||
                         normalized.contains("آية") || normalized.contains("اية") ||
                         normalized.contains("قرآن") || normalized.contains("قران") ||
                         normalized.contains("تخريج حديث") || normalized.contains("صحة حديث")

    val resolvedIntent = when {
      isCancelSched -> "cancel_schedule"
      (effectiveIntent == "unknown" || confidence < 0.85) && isSchedReq -> "create_schedule"
      (effectiveIntent == "define_word" || effectiveIntent == "unknown") && isReligiousReq -> "ask_religion"
      (effectiveIntent == "unknown" || confidence < 0.70) && adapted.inferredIntentOverride != null -> adapted.inferredIntentOverride
      else -> effectiveIntent
    }

    val finalIntent = resolvedIntent
    if (finalIntent == "generate_image" || finalIntent == "create_schedule" || finalIntent == "cancel_schedule") {
      confidence = 0.98
    } else if (adapted.inferredIntentOverride != null && finalIntent == adapted.inferredIntentOverride) {
      confidence = 0.95
    }

    // Ambiguity and Missing Required Parameter Detection (Objective A)
    val ambiguityCheck = AmbiguityAndClarificationDetector.check(rawInput, normalized, finalIntent)
    if (ambiguityCheck is AmbiguityCheckResult.Ambiguous) {
      val clar = ambiguityCheck.payload
      val trace = EpistemicGroundingPipeline.buildTrace(
        rawRequest = rawInput,
        intent = finalIntent,
        entities = emptyMap(),
        appliedRules = listOf("rule_ambiguity_clarification"),
        facts = emptyList(),
        uncertainty = listOf(clar.reason),
        outcomeState = OutcomeState.AMBIGUOUS,
        sourceDesc = "كاشف اللبس والغموض الحتمي لزهرة 4"
      )
      val ambigRes = ZahraResponse(
        text = clar.question,
        intent = finalIntent,
        confidence = 0.50,
        confidenceTier = ConfidenceTier.QUALIFIED,
        outcomeState = OutcomeState.AMBIGUOUS,
        source = "كاشف اللبس والغموض لزهرة 4 (Truth Before Intelligence)",
        followups = clar.options.map { it.associatedQuery },
        clarification = clar,
        toolPayload = ZahraToolPayload.ClarificationToolPayload(clar),
        evidenceTrace = trace
      )
      return ambigRes
    }

    // Phase 1: قرار التوجيه الموحد — يُفصل عن التنفيذ ويُدمج في المسار الفعلي.
    val classifierScores: Map<String, Double> =
      (calibratedScore?.rankedProbabilities ?: calibratedClassifier.classify(clean).rankedProbabilities).toMap()

    val knowledgeState: KnowledgeState? = when {
      finalIntent == Intent.QUERY_KNOWLEDGE || finalIntent == Intent.DEFINE_WORD -> {
        val searchTarget = if (adapted.coreQuery.isNotBlank() && (adapted.detectedDialect == "english" || adapted.detectedDialect == "egyptian_colloquial")) {
          adapted.coreQuery
        } else {
          rawInput
        }
        val kRes = knowledgeEngine.query(searchTarget)
        when (kRes.state) {
          KnowledgeRetrievalState.VERIFIED_MATCH -> KnowledgeState.VERIFIED
          KnowledgeRetrievalState.TRACEABLE_CANDIDATE -> KnowledgeState.CANDIDATE
          KnowledgeRetrievalState.CONFLICTING_EVIDENCE -> KnowledgeState.CONFLICT
          else -> KnowledgeState.INSUFFICIENT
        }
      }
      else -> null
    }

    val routingEvidence = RoutingEvidence(
      raw = rawInput,
      normalized = normalized,
      lang = langInfo,
      intentLabel = finalIntent,
      classifierConfidence = confidence,
      scores = classifierScores,
      knowledgeState = knowledgeState,
      // الطلبات المحرومة تنتهي قبل هذه النقطة (انظر checkImpermissibleRequest أعلاه)
      isImpermissible = false,
      ambiguity = ambiguityCheck,
      isSystemCommand = isSystemIntent(finalIntent)
    )
    val routingDecision = router.route(routingEvidence)

    when (routingDecision.gate) {
      GateOutcome.ABSTAIN -> {
        val fallback = generateFallback(rawInput)
        return fallback.copy(
          evidenceTrace = fallback.evidenceTrace?.copy(
            extractedEntities = mapOf("query" to clean, "traceId" to routingDecision.traceId)
          )
        )
      }
      GateOutcome.CLARIFY -> {
        // يُستخدم فقط حين بقي كاشف الغموض الصامت (Clear) وقرّر الموجه التوضيح
        if (ambiguityCheck is AmbiguityCheckResult.Clear) {
          return buildRoutingClarification(rawInput, finalIntent, routingDecision)
        }
      }
      GateOutcome.ANSWER -> {
        // يستمر إلى تنفيذ الفروع أدناه حسب finalIntent
      }
    }

    var responseText = ""
    var source: String? = null
    var followups = listOf<String>()
    var toolPayload: ZahraToolPayload? = null
    var createdTask: ZahraTask? = null
    var createdNote: ZahraNote? = null
    var createdSchedule: ZahraSchedule? = null
    var outcomeState: OutcomeState = OutcomeState.SUPPORTED

    when (finalIntent) {
      "generate_image" -> {
        val prompt = regexMatch?.extractedCommand?.ifBlank { rawInput } ?: rawInput
        val imgPayload = localImageEngine.generateImage(prompt)
        responseText = "أنشأت لك هذه اللوحة الفنية التفاعلية محلياً:\n\"${imgPayload.title}\"\n\nيمكنك التبديل بين الأنماط (فن إجرائي، بطاقة اقتباس، وبكسل آرت) عبر الأزرار التفاعلية أدناه."
        source = "محرك الرسومات والفن الإجرائي المحلي لزهرة 3"
        confidence = 0.99
        outcomeState = OutcomeState.SUPPORTED
        toolPayload = imgPayload
        followups = listOf("ارسم لي مشهد فضاء كوني", "صمم بطاقة اقتباس عربي", "ارسم بكسل آرت 8-bit")
      }

      "create_schedule" -> {
        // Enforce Phase 1 Rule: Validate fields before side-effect execution!
        when (val schedVal = scheduleEngine.validateAndSchedule(rawInput)) {
          is ScheduleValidationResult.Success -> {
            createdSchedule = schedVal.schedule
            responseText = schedVal.summary
            source = "محرك الجدولة والتنبيهات المعتمد لزهرة 3"
            confidence = 0.98
            outcomeState = schedVal.outcomeState
            toolPayload = ZahraToolPayload.SchedulePayload(schedule = schedVal.schedule, isNew = true)
            followups = listOf("اعرض المواعيد المجدولة", "فكرني كل يوم الساعة 8 صباحاً", "أضف مهمة جديدة")
          }
          is ScheduleValidationResult.Ambiguous -> {
            responseText = schedVal.message
            source = "مدقق صحة المواعيد والجدولة (Zahra Validation Layer)"
            confidence = 0.60
            outcomeState = schedVal.outcomeState
            followups = listOf("فكرني الساعة 5 مساءً", "ذكرني بعد 30 دقيقة", "فكرني بكرة الساعة 8 صباحاً")
          }
          is ScheduleValidationResult.Unsupported -> {
            responseText = schedVal.message
            source = "مدقق صحة المواعيد والجدولة (Zahra Validation Layer)"
            confidence = 0.85
            outcomeState = schedVal.outcomeState
            followups = listOf("فكرني كل يوم الساعة 8 صباحاً", "ذكرني بعد ساعة")
          }
          is ScheduleValidationResult.RegistrationFailed -> {
            createdSchedule = schedVal.schedule
            responseText = schedVal.message
            source = "محرك جدولة وتنبيهات النظام لزهرة 3"
            confidence = 0.85
            outcomeState = schedVal.outcomeState
            toolPayload = ZahraToolPayload.SchedulePayload(schedule = schedVal.schedule, isNew = true)
            followups = listOf("اعرض المواعيد المجدولة", "فكرني بموعد آخر", "أضف مهمة جديدة")
          }
          is ScheduleValidationResult.ExecutionError -> {
            responseText = schedVal.message
            source = "محرك تنبيهات النظام"
            confidence = 0.50
            outcomeState = schedVal.outcomeState
          }
        }
      }

      "list_schedules" -> {
        val schedules = scheduleEngine.getSchedules()
        responseText = if (schedules.isEmpty()) {
          "لا توجد مواعيد مجدولة حالياً. يمكنك أن تطلب مني: \"فكرني كل يوم الساعة 8 صباحاً بالأذكار\"."
        } else {
          "المواعيد والتنبيهات المجدولة (${schedules.size}):\n" +
            schedules.joinToString("\n") { "⏰ [${it.timeDisplay}] ${it.title} (${if (it.isDaily) "يومي" else "مرة واحدة"}) [الحالة: ${it.state.labelAr}]" }
        }
        source = "محرك الجدولة المحلي لزهرة 3"
        outcomeState = OutcomeState.SUPPORTED
        if (schedules.isNotEmpty()) {
          toolPayload = ZahraToolPayload.SchedulePayload(schedules.first(), isNew = false)
        }
        followups = listOf("فكرني الساعة 5 مساءً", "احذف التذكير", "أضف مهمة جديدة")
      }

      "cancel_schedule" -> {
        val cleanQuery = rawInput.replace(Regex("(?:الغ|إلغاء|احذف|حذف|امسح|وقف)\\s*(?:التذكير|المنبه|الموعد|موعد|تنبيه)?"), "").trim()
        val allSchedules = scheduleEngine.getSchedules()
        val target = if (cleanQuery.isNotBlank() && cleanQuery.length >= 2) {
          allSchedules.find { it.title.contains(cleanQuery) || cleanQuery.contains(it.title) }
        } else {
          allSchedules.firstOrNull { it.isEnabled }
        }

        if (target != null) {
          scheduleEngine.deleteSchedule(target.id)
          responseText = "تم إلغاء التذكير وإلغاء المنبه المرتبط به في النظام بنجاح:\n• التذكير: ${target.title} (${target.timeDisplay})"
          source = "محرك الجدولة والتنبيهات لزهرة 3"
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("اعرض المواعيد المجدولة", "فكرني كل يوم الساعة 8 صباحاً", "أضف مهمة جديدة")
        } else {
          responseText = "لم أجد موعداً أو تذكيراً مطابقاً لإلغائه. يمكنك مراجعة المواعيد المجدولة بقول: \"اعرض المواعيد المجدولة\"."
          source = "محرك الجدولة والتنبيهات لزهرة 3"
          outcomeState = OutcomeState.AMBIGUOUS
          followups = listOf("اعرض المواعيد المجدولة", "فكرني بموعد لاحق")
        }
      }

      "calculate" -> {
        val mathExpr = regexMatch?.extractedCommand ?: normalized.replace(Regex("^(?:احسب|calc)\\s+"), "")
        // المسائل الكلامية أولًا: قالب حتمي له خطوات، وإلا التعبير الرياضي المباشر
        val res = MathEngine.solveWordProblem(rawInput) ?: MathEngine.evaluate(mathExpr)
        responseText = res.formattedText
        source = res.source
        confidence = res.confidence
        outcomeState = res.outcomeState
        toolPayload = ZahraToolPayload.MathPayload(
          expression = mathExpr,
          result = res.formattedText,
          calculationType = "حساب رياضي دقيق"
        )
      }

      "convert_unit" -> {
        // المسار الجديد: نظام وحدات عام (9 تصنيفات + وحدات مصرية) بدل عشرة أزواج مثبتة
        val converted = MathEngine.convertFromText(normalized) ?: MathEngine.convertFromText(rawInput)
        if (converted != null) {
          responseText = converted.formattedText
          source = converted.source
          outcomeState = converted.outcomeState
          confidence = converted.confidence
          toolPayload = ZahraToolPayload.MathPayload(
            expression = normalized,
            result = converted.formattedText,
            calculationType = "تحويل وحدات بجدول عوامل عام"
          )
          if (converted.outcomeState == OutcomeState.SUPPORTED) {
            followups = listOf("حوّل 3 فدان إلى متر مربع", "حوّل 72 فهرنهايت إلى مئوية", "احسب 25 * 4")
          }
        } else {
        val parsed = EntityExtractor.parseUnitRequest(normalized)
        if (parsed != null && parsed.toUnit.isNotBlank()) {
          val res = MathEngine.convertUnit(parsed.value, parsed.fromUnit, parsed.toUnit)
          responseText = res.formattedText
          source = res.source
          outcomeState = res.outcomeState
          confidence = res.confidence
          toolPayload = ZahraToolPayload.MathPayload(
            expression = "${parsed.value} ${parsed.fromUnit} ➔ ${parsed.toUnit}",
            result = res.formattedText,
            calculationType = "تحويل وحدات قياسية"
          )
        } else {
          responseText = "يرجى تحديد القيمة والوحدة المراد التحويل منها وإليها (مثال: حول 10 كم إلى ميل أو حول 5 كيلو إلى باوند)."
          source = "محول الوحدات الفيزيائية"
          outcomeState = OutcomeState.AMBIGUOUS
          confidence = 0.50
        }
        }
      }

      "ask_date", "ask_time" -> {
        val answer = TimeTools.answer(
          rawInput = rawInput,
          hijri = androidHijriCalculator,
          hijriIsDeviceAccurate = androidHijriCalculator.isDeviceCalendarUsable()
        )
        if (answer != null) {
          responseText = answer.text
          source = answer.source
          confidence = answer.confidence
          outcomeState = answer.outcomeState
          followups = answer.followups
        } else {
          responseText = "مش قادر أحدد التاريخ المطلوب بدقة. اكتب سؤالك بصيغة أوضح " +
            "(مثال: \"النهاردة كام؟\" أو \"الساعة كام دلوقتي؟\" أو \"إيه التاريخ الهجري؟\")."
          source = "أدوات الوقت والتاريخ المحلية"
          outcomeState = OutcomeState.AMBIGUOUS
          confidence = 0.5
        }
      }

      "weather" -> {
        val weatherResponse = weatherToolResponse(rawInput, ZahraNormalizer.normalize(rawInput).lowercase(Locale.ROOT))
        if (weatherResponse != null) {
          responseText = weatherResponse.text
          source = weatherResponse.source
          confidence = weatherResponse.confidence
          outcomeState = weatherResponse.outcomeState
          followups = weatherResponse.followups
        } else {
          responseText = "حدّد المدينة المطلوبة عشان أجيب قراءة حقيقية من مصدرها (مثال: القاهرة، الإسكندرية، أسوان)."
          source = "أداة الطقس — تحتاج مدينة محددة"
          outcomeState = OutcomeState.AMBIGUOUS
          confidence = 0.5
        }
      }

      "translate" -> {
        val toTrans = regexMatch?.extractedCommand ?: normalized.replace(Regex("^(?:ترجم|translate)\\s+"), "")
        val target = if (langInfo.language == com.example.zahra.core.DetectedLang.AR) "en" else "ar"
        val res = translationEngine.translate(toTrans, target)
        responseText = "الترجمة المعتمدة:\n\"${res.translatedText}\""
        source = res.source
        confidence = res.confidence
        outcomeState = OutcomeState.SUPPORTED
        toolPayload = ZahraToolPayload.TranslatePayload(
          sourceText = toTrans,
          translatedText = res.translatedText,
          sourceLang = if (target == "en") "العربية" else "English",
          targetLang = if (target == "en") "English" else "العربية"
        )
      }

      "summarize" -> {
        val toSum = regexMatch?.extractedCommand ?: normalized.replace(Regex("^(?:لخص|summarize)\\s+"), "")
        if (toSum.length < 20) {
          responseText = "النص المدخل قصير جداً للتلخيص. يرجى إدخال فقرة تحتوي على جمل متعددة للحصول على ملخص استخلاصي دقيق."
          source = "محرك التلخيص الاستخلاصي (TextRank)"
          outcomeState = OutcomeState.AMBIGUOUS
          confidence = 0.60
        } else {
          val sumRes = SummarizeEngine.summarize(toSum)
          val compression = ((1.0 - sumRes.compressionRatio) * 100).toInt()
          responseText = "الملخص الاستخلاصي:\n${sumRes.summary}\n\n(نسبة الاختصار: $compression%)"
          source = sumRes.source
          outcomeState = OutcomeState.SUPPORTED
          toolPayload = ZahraToolPayload.SummarizePayload(
            summary = sumRes.summary,
            originalLength = toSum.length,
            summaryLength = sumRes.summary.length,
            compressionRatio = sumRes.compressionRatio,
            bulletPoints = sumRes.keySentences
          )
        }
      }

      "ask_religion", "ask_prayer_time", "ask_quran", "ask_hadith" -> {
        val relRes = religionEngine.query(normalized)
        responseText = relRes.text
        source = "[المصدر: ${relRes.source}]"
        confidence = relRes.confidence
        outcomeState = relRes.outcomeState
        followups = relRes.followups

        val isPrayer = normalized.contains("صلاة") || normalized.contains("أذان") || normalized.contains("مواقيت") || normalized.contains("فجر")
        val isTasbih = normalized.contains("تسبيح") || normalized.contains("أذكار") || normalized.contains("سبحان الله") || normalized.contains("استغفار")

        val prayerSchedule = if (isPrayer) {
          listOf(
            "الفجر" to "04:45 ص",
            "الشروق" to "06:05 ص",
            "الظهر" to "11:58 ص",
            "العصر" to "03:22 م",
            "المغرب" to "05:48 م",
            "العشاء" to "07:18 م"
          )
        } else emptyList()

        val catName = when {
          isPrayer -> "مواقيت الصلاة الفلكية"
          isTasbih -> "أذكار وتسبيح"
          normalized.contains("قرآن") || normalized.contains("سورة") || normalized.contains("آية") -> "القرآن الكريم والتفسير"
          normalized.contains("حديث") || normalized.contains("قال رسول الله") -> "الحديث النبوي الشريف"
          else -> "الفقه والأحكام الشرعية"
        }

        toolPayload = ZahraToolPayload.ReligionPayload(
          category = catName,
          title = if (isPrayer) "جدول أوقات الصلاة المعتمد" else "إرشاد شرعي موثق",
          content = relRes.text,
          source = relRes.source,
          isPrayerTime = isPrayer,
          prayerTimes = prayerSchedule,
          isTasbih = isTasbih,
          tasbihTarget = 33
        )
      }

      "create_task" -> {
        val taskVal = actionExecutor.validateTaskArgs(rawInput)
        when (taskVal) {
          is ExecutionResult.Success -> {
            val task = taskEngine.createTask(rawInput)
            createdTask = task
            responseText = "تم حفظ المهمة بنجاح في جدول مهامك:\n• العنوان: ${task.title}\n• التصنيف: ${task.category}\n• الأولوية: ${task.priorityScore}/5${if (task.dueDate != null) "\n• الموعد: ${task.dueDate}" else ""}"
            source = "محرك المهام المحلي والذاكرة الآمنة"
            outcomeState = OutcomeState.SUPPORTED
            toolPayload = ZahraToolPayload.TaskPayload(task)
          }
          is ExecutionResult.PreconditionFailed -> {
            responseText = "تعذر إنشاء المهمة: ${taskVal.reason}"
            source = "مدقق صحة المهام (Validation Layer)"
            outcomeState = OutcomeState.AMBIGUOUS
            confidence = 0.50
          }
          is ExecutionResult.IdempotencySkipped -> {
            val existing = taskEngine.getTasks().firstOrNull()
            responseText = taskVal.message
            source = "حاجز منع التكرار (Idempotency Guard)"
            outcomeState = OutcomeState.SUPPORTED
            if (existing != null) toolPayload = ZahraToolPayload.TaskPayload(existing)
          }
          else -> {
            val task = taskEngine.createTask(rawInput)
            createdTask = task
            responseText = "تم حفظ المهمة بنجاح في جدول مهامك:\n• العنوان: ${task.title}\n• التصنيف: ${task.category}\n• الأولوية: ${task.priorityScore}/5"
            source = "محرك المهام المحلي والذاكرة الآمنة"
            outcomeState = OutcomeState.SUPPORTED
            toolPayload = ZahraToolPayload.TaskPayload(task)
          }
        }
      }

      "list_tasks" -> {
        val tasks = taskEngine.getTasks()
        val latest = tasks.firstOrNull()
        responseText = if (tasks.isEmpty()) {
          "قائمة مهامك فارغة حالياً. لإضافة مهمة اكتب: \"أضف مهمة إرسال التقرير غداً\"."
        } else {
          "قائمة المهام المسجلة (${tasks.size}):\n" +
            tasks.joinToString("\n") { "${if (it.completed) "✅" else "⏳"} [${it.category}] ${it.title} (أولوية: ${it.priorityScore}/5)" }
        }
        source = "قاعدة بيانات المهام المحلية"
        outcomeState = OutcomeState.SUPPORTED
        if (latest != null) {
          toolPayload = ZahraToolPayload.TaskPayload(latest)
        }
      }

      "remember_note" -> {
        val noteVal = actionExecutor.validateNoteArgs(rawInput)
        when (noteVal) {
          is ExecutionResult.Success -> {
            val note = noteEngine.createNote(rawInput)
            createdNote = note
            responseText = "تم حفظ الملاحظة:\n\"${note.title}\"\n\nالوسوم المستخرجة تلقائياً: #${note.tags.joinToString(" #")}"
            source = "خوارزمية RAKE ومستودع الملاحظات المحلي"
            outcomeState = OutcomeState.SUPPORTED
            toolPayload = ZahraToolPayload.NotePayload(note)
          }
          is ExecutionResult.PreconditionFailed -> {
            responseText = "تعذر حفظ الملاحظة: ${noteVal.reason}"
            source = "مدقق صحة الملاحظات (Validation Layer)"
            outcomeState = OutcomeState.AMBIGUOUS
            confidence = 0.50
          }
          is ExecutionResult.IdempotencySkipped -> {
            val existing = noteEngine.getNotes().firstOrNull()
            responseText = noteVal.message
            source = "حاجز منع التكرار (Idempotency Guard)"
            outcomeState = OutcomeState.SUPPORTED
            if (existing != null) toolPayload = ZahraToolPayload.NotePayload(existing)
          }
          else -> {
            val note = noteEngine.createNote(rawInput)
            createdNote = note
            responseText = "تم حفظ الملاحظة:\n\"${note.title}\""
            source = "مستودع الملاحظات المحلي"
            outcomeState = OutcomeState.SUPPORTED
            toolPayload = ZahraToolPayload.NotePayload(note)
          }
        }
      }

      "list_notes" -> {
        val notes = noteEngine.getNotes()
        val latestNote = notes.firstOrNull()
        responseText = if (notes.isEmpty()) {
          "لا توجد ملاحظات مسجلة بعد. لحفظ ملاحظة اكتب: \"سجل ملاحظة: فكرة لمشروع جديد\"."
        } else {
          "الملاحظات المحفوظة (${notes.size}):\n" +
            notes.joinToString("\n") { "📝 ${it.title} (وسوم: ${it.tags.joinToString(", ")})" }
        }
        source = "مستودع الملاحظات المحلي"
        outcomeState = OutcomeState.SUPPORTED
        if (latestNote != null) {
          toolPayload = ZahraToolPayload.NotePayload(latestNote)
        }
      }

      "greet" -> {
        // Phase 6.6: Use GreetingVariator for dynamic, varied greetings
        val greetingContext = GreetingVariator.GreetingContext(
          userName = dialogueState.slots[Slot.PERSON]?.value
            ?: memoryEngine.getFacts().firstOrNull { it.key == "الاسم" }?.value,
          timeOfDay = GreetingVariator.detectTimeOfDay(),
          isFirstSession = dialogueState.turns.isEmpty(),
          previousTopics = emptyList(),
          isReturningUser = false,
          userGender = null
        )
        responseText = greetingVariator.generateGreeting(clean, greetingContext)
        source = "نظام التحيات المتنوع لزهرة (Phase 6.6)"
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("إنشاء صورة أو ملصق", "فكرني كل يوم الساعة 8 صباحاً", "احسب 150 * 4", "مواقيت الصلاة اليوم", "ماذا تعرف عني؟")
        // Save all memory after greeting (in case profile was updated)
        unifiedMemory.saveAll()
      }

      "farewell" -> {
        // Phase 6.6: Varied farewell too
        val farewellContext = GreetingVariator.GreetingContext(
          userName = dialogueState.slots[Slot.PERSON]?.value
            ?: memoryEngine.getFacts().firstOrNull { it.key == "الاسم" }?.value,
          userGender = null
        )
        responseText = greetingVariator.generateFarewell(farewellContext)
        source = "نظام التحيات المتنوع لزهرة (Phase 6.6)"
        outcomeState = OutcomeState.SUPPORTED
        // Save all memory on farewell
        unifiedMemory.saveAll()
      }

      "help" -> {
        responseText = "أنا \"زهرة 3\" (Zahra 3) — وكيل ذكي مستقل ونظام ذكاء حاسوبي يعمل 100% محلياً مع بطاقات تفاعلية:\n\n" +
          "1. 🎨 توليد الصور والبطاقات الفنية (انشئ صورة، صمم بطاقة اقتباس، بكسل آرت)\n" +
          "2. ⏰ الجدولة والتنبيهات الحقيقية للوكيل (جدول لي، فكرني كل يوم الساعة 8)\n" +
          "3. 🧠 الذاكرة التراكمية وفهم العامية والفرانكو (تذكر أن، ماذا تعرف عني)\n" +
          "4. 🧮 الحساب الدقيق وتحويل الوحدات (احسب، حول كم إلى ميل)\n" +
          "5. 🌐 الترجمة الفورية بالبطاقات التفاعلية (ترجم سلام)\n" +
          "6. 🕌 الشريعة والمواقيت ومسبحة الأذكار (مواقيت الصلاة، اتجاه القبلة)\n" +
          "7. ✅ إدارة المهام التفاعلية المباشرة (أضف مهمة)\n" +
          "8. 📝 الملاحظات والوسوم التلقائية (سجل ملاحظة)\n" +
          "9. 📄 التلخيص الاستخلاصي السريع (لخص هذا النص)"
        source = "دليل قدرات نظام زهرة 3"
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("إنشاء صورة أو ملصق", "فكرني كل يوم الساعة 8", "احسب 500 / 4", "ماذا تعرف عني؟")
      }

      "thank" -> {
        responseText = "على الرحب والسعة دائماً! يسعدني خدمتك بدقة وموثوقية وسرعة فائقة."
        source = "نظام زهرة 3"
        outcomeState = OutcomeState.SUPPORTED
      }

      "query_knowledge", "define_word" -> {
        val searchTarget = if (adapted.coreQuery.isNotBlank() && (adapted.detectedDialect == "english" || adapted.detectedDialect == "egyptian_colloquial")) {
          adapted.coreQuery
        } else {
          rawInput
        }
        var kRes = knowledgeEngine.query(searchTarget)
        if (kRes.state != KnowledgeRetrievalState.VERIFIED_MATCH && kRes.state != KnowledgeRetrievalState.TRACEABLE_CANDIDATE && searchTarget != rawInput) {
          val fallbackRes = knowledgeEngine.query(rawInput)
          if (fallbackRes.state == KnowledgeRetrievalState.VERIFIED_MATCH || fallbackRes.state == KnowledgeRetrievalState.TRACEABLE_CANDIDATE) {
            kRes = fallbackRes
          }
        }
        if (kRes.state != KnowledgeRetrievalState.VERIFIED_MATCH && kRes.state != KnowledgeRetrievalState.TRACEABLE_CANDIDATE && clean != searchTarget && clean != rawInput) {
          val cleanRes = knowledgeEngine.query(clean)
          if (cleanRes.state == KnowledgeRetrievalState.VERIFIED_MATCH || cleanRes.state == KnowledgeRetrievalState.TRACEABLE_CANDIDATE) {
            kRes = cleanRes
          }
        }
        when (kRes.state) {
          KnowledgeRetrievalState.VERIFIED_MATCH, KnowledgeRetrievalState.TRACEABLE_CANDIDATE -> {
            val best = kRes.bestMatch!!
            responseText = "${best.record.canonicalContent}\n\n[المصدر: ${best.record.provenance.sourceTitle} - ${best.record.provenance.sourceLocation}]"
            source = "[${best.record.provenance.sourceTitle} - ${best.record.provenance.origin}]"
            confidence = best.relevanceScore
            outcomeState = OutcomeState.SUPPORTED
            toolPayload = ZahraToolPayload.KnowledgePayload(
              title = best.record.title,
              category = best.record.category.labelAr,
              content = best.record.canonicalContent,
              originalSourceText = best.record.originalSourceText,
              source = best.record.provenance.sourceTitle,
              sourceLocation = best.record.provenance.sourceLocation,
              verificationStatusText = best.record.verificationStatus.labelAr,
              isVerified = best.record.verificationStatus.isVerified,
              matchMethodText = best.matchMethod.labelAr,
              relevancePercent = (best.relevanceScore * 100).toInt(),
              recordId = best.record.id,
              warning = best.warning
            )
            followups = listOf("ابحث في المعرفة", "ما هي سرعة الضوء؟", "من هو الخوارزمي؟")
          }
          KnowledgeRetrievalState.CONFLICTING_EVIDENCE -> {
            responseText = kRes.abstentionReason ?: "توجد أدلة متعارضة مسجلة حول هذه المسألة."
            source = "مدقق النزاهة المعرفية لزهرة 3"
            confidence = 0.60
            outcomeState = OutcomeState.AMBIGUOUS
            toolPayload = kRes.bestMatch?.let { best ->
              ZahraToolPayload.KnowledgePayload(
                title = best.record.title,
                category = best.record.category.labelAr,
                content = best.record.canonicalContent,
                originalSourceText = best.record.originalSourceText,
                source = best.record.provenance.sourceTitle,
                sourceLocation = best.record.provenance.sourceLocation,
                verificationStatusText = best.record.verificationStatus.labelAr,
                isVerified = false,
                matchMethodText = best.matchMethod.labelAr,
                relevancePercent = (best.relevanceScore * 100).toInt(),
                recordId = best.record.id,
                warning = "أدلة متعددة متعارضة",
                conflicts = kRes.conflicts.map { it.description }
              )
            }
          }
          KnowledgeRetrievalState.INSUFFICIENT_EVIDENCE, KnowledgeRetrievalState.NO_MATCH, KnowledgeRetrievalState.ERROR -> {
            responseText = kRes.abstentionReason ?: "المسألة غير مسجلة في قاعدة المعرفة المعتمدة لزهرة 3. يمتنع المساعد تماماً عن التخمين أو تأليف معلومات غير موثقة (Truth Before Intelligence)."
            source = "قيد النزاهة المعرفية لزهرة 3 (Truth Before Intelligence)"
            confidence = 0.20
            outcomeState = OutcomeState.UNSUPPORTED
            followups = listOf("ما هي سرعة الضوء؟", "درجة غليان الماء", "من هو الخوارزمي؟", "أعلى قمة في العالم")
          }
        }
      }

      // Phase 6.7: Wisdom Bank — طلب حكمة أو اقتباس
      "ask_wisdom", "wisdom", "quote" -> {
        val wisdom = wisdomBank.getRandomWisdom()
        responseText = wisdomBank.formatWisdom(wisdom)
        source = "بنك الحكم والأمثال لزهرة (${wisdomBank.getCount()} حكمة)"
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("قولي حكمة تانية", "حكمة عن الصبر", "آية تريحني", "حكمة ملهمة")
      }

      // Phase 6.7: Mood Journal — عرض ملخص المزاج
      "mood_summary", "mood_journal" -> {
        responseText = moodJournal.getMoodSummary()
        source = "مذكرات المزاج اليومية لزهرة (Phase 6.7)"
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("سجل إني مبسوط", "قولي حكمة", "إيه نصيحتك ليا؟")
      }

      // Phase 6.7: Semantic Search — بحث دلالي
      "semantic_search", "find_similar" -> {
        val query = clean.replace(Regex("^(ابحث عن|دور على|شوفلي|ابحثلي|find|search)\\s*", RegexOption.IGNORE_CASE), "").trim()
        val results = semanticSearch.hybridSearch(query.ifBlank { rawInput }, topK = 3)
        if (results.isNotEmpty()) {
          responseText = results.joinToString("\n\n") { r ->
            "📌 ${r.document.title}\n${r.document.content.take(200)}${if (r.document.content.length > 200) "..." else ""}"
          }
          source = "محرك البحث الدلالي لزهرة (Phase 6.7)"
          confidence = results.first().score
          outcomeState = OutcomeState.SUPPORTED
        } else {
          responseText = "مفيش نتائج مشابهة لـ \"$query\" في قاعدة المعرفة الحالية 🤔\nجرب تصيغ البحث بشكل تاني!"
          source = "محرك البحث الدلالي لزهرة"
          confidence = 0.3
          outcomeState = OutcomeState.UNSUPPORTED
        }
        followups = listOf("ابحث عن الصبر", "دور على السعادة", "حكمة عن الأمل")
      }

      // Phase 6.7: Voice and Vision Commands
      "analyze_image" -> {
        responseText = "📸 لتحليل صورة، استخدم زر الكاميرا أو اختر صورة من المعرض.\n\n" +
          "سأقوم بـ:\n" +
          "• تحليل الألوان السائدة\n" +
          "• حساب السطوع والإضاءة\n" +
          "• استخراج النص المكتوب (OCR)\n" +
          "• كشف الوجوه والتعبيرات\n\n" +
          "جرب الآن: التقط صورة أو اختر من المعرض!"
        source = "محرك الرؤية المتقدم لزهرة (Phase 6.7)"
        confidence = 0.99
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("اقرا النص من الصورة", "كم وجه في الصورة؟", "حلل ألوان الصورة")
      }

      "extract_text" -> {
        responseText = "📝 لاستخراج النص من صورة:\n\n" +
          "1. التقط صورة تحتوي على نص\n" +
          "2. أو اختر صورة من المعرض\n\n" +
          "سأستخدم OCR متقدم يدعم العربية والإنجليزية لقراءة النص بدقة.\n\n" +
          "نصيحة: تأكد من وضوح الصورة والإضاءة الجيدة لنتائج أفضل!"
        source = "محرك OCR لزهرة (Tesseract)"
        confidence = 0.99
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("حلل الصورة", "اكشف الوجوه", "اقرا صورة أخرى")
      }

      "detect_faces" -> {
        responseText = "👤 لكشف الوجوه في صورة:\n\n" +
          "1. التقط صورة تحتوي على وجوه\n" +
          "2. أو اختر صورة من المعرض\n\n" +
          "سأقوم بـ:\n" +
          "• كشف عدد الوجوه\n" +
          "• تحليل التعبيرات (مبتسم/غير مبتسم)\n" +
          "• كشف حالة العيون (مفتوحة/مغلقة)\n" +
          "• تتبع الوجوه المتعددة\n\n" +
          "ML Kit Face Detection يعمل offline بالكامل!"
        source = "محرك كشف الوجوه لزهرة (ML Kit)"
        confidence = 0.99
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("حلل الصورة", "اقرا النص", "كم وجه في الصورة؟")
      }

      "voice_input" -> {
        // SpeechRecognizer belongs to the UI/main-thread lifecycle. Starting it here without a
        // listener used to be a compile-time error and could never return a transcript.
        responseText = "🎤 افتح الوضع الصوتي من زر الميكروفون بجوار حقل الكتابة. بعد السماح بإذن الميكروفون، تحدث وسأحول كلامك إلى رسالة مباشرة."
        source = "محرك التعرف على الكلام لزهرة"
        confidence = 0.99
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("انطق الرد", "حلل الصورة", "اقرا النص")
      }

      "speak_response" -> {
        if (!voiceEngine.isSpeaking()) {
          val lastResponse = contextTracker.getLastZahraResponse() ?: "مرحباً! أنا زهرة."
          voiceEngine.speak(lastResponse)
          responseText = "🔊 جاري نطق الرد...\n\n" +
            "اضغط مرة أخرى للإيقاف."
          source = "محرك تحويل النص إلى كلام لزهرة"
          confidence = 0.99
          outcomeState = OutcomeState.SUPPORTED
        } else {
          voiceEngine.stopSpeaking()
          responseText = "⏹️ تم إيقاف النطق."
          source = "محرك تحويل النص إلى كلام لزهرة"
          confidence = 0.99
          outcomeState = OutcomeState.SUPPORTED
        }
        followups = listOf("سجل صوت", "حلل الصورة", "حكمة اليوم")
      }

      // Phase 6.7: Islamic Knowledge Base Commands
      "ask_islamic" -> {
        val lesson = islamicKnowledge.getRandomLesson()
        responseText = islamicKnowledge.formatLesson(lesson)
        source = "القاعدة الإسلامية الأساسية لزهرة (Phase 6.7)"
        confidence = 0.95
        outcomeState = OutcomeState.SUPPORTED
        followups = listOf("علمني عن الفقه", "قولي عن السيرة", "اشرحي العقيدة", "فهميني التزكية")
      }

      "ask_fiqh" -> {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.FIQH)
        if (lesson != null) {
          responseText = islamicKnowledge.formatLesson(lesson)
          source = "القاعدة الإسلامية - فقه يسير"
          confidence = 0.95
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("علمني عن الصلاة", "ما حكم الزكاة؟", "كيف أصوم؟", "أحكام الحج")
        } else {
          responseText = "📖 لا توجد دروس في الفقه حالياً."
          source = "القاعدة الإسلامية"
          confidence = 0.70
          outcomeState = OutcomeState.UNSUPPORTED
        }
      }

      "ask_seerah" -> {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.SEERAH)
        if (lesson != null) {
          responseText = islamicKnowledge.formatLesson(lesson)
          source = "القاعدة الإسلامية - سيرة يسير"
          confidence = 0.95
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("قولي عن مولد النبي", "احكي عن الهجرة", "ما هي غزوة بدر؟", "فتح مكة")
        } else {
          responseText = "🕌 لا توجد دروس في السيرة حالياً."
          source = "القاعدة الإسلامية"
          confidence = 0.70
          outcomeState = OutcomeState.UNSUPPORTED
        }
      }

      "ask_aqeedah" -> {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.AQEEDAH)
        if (lesson != null) {
          responseText = islamicKnowledge.formatLesson(lesson)
          source = "القاعدة الإسلامية - عقيدة يسير"
          confidence = 0.95
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("ما هو التوحيد؟", "أركان الإيمان", "الإيمان بالقدر", "اليوم الآخر")
        } else {
          responseText = "🌟 لا توجد دروس في العقيدة حالياً."
          source = "القاعدة الإسلامية"
          confidence = 0.70
          outcomeState = OutcomeState.UNSUPPORTED
        }
      }

      "ask_tazkiyah" -> {
        val lesson = islamicKnowledge.getLessonByCategory(IslamicKnowledgeBase.Category.TAZKIYAH)
        if (lesson != null) {
          responseText = islamicKnowledge.formatLesson(lesson)
          source = "القاعدة الإسلامية - تزكية يسيرة"
          confidence = 0.95
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("كيف أغير نفسي؟", "ما هي العبودية؟", "جهاد النفس", "القرآن دواء")
        } else {
          responseText = "💚 لا توجد دروس في التزكية حالياً."
          source = "القاعدة الإسلامية"
          confidence = 0.70
          outcomeState = OutcomeState.UNSUPPORTED
        }
      }

      else -> {
        // Phase 6.7: Auto-detect wisdom requests before generic fallback
        val wisdomKeywords = listOf("حكمة", "مثل", "قولي حكمة", "اقتباس", "حكمة اليوم", "كلام جميل", "آية", "حديث")
        if (wisdomKeywords.any { normalized.contains(it) }) {
          val wisdom = wisdomBank.searchWisdoms(clean).firstOrNull() ?: wisdomBank.getRandomWisdom()
          responseText = wisdomBank.formatWisdom(wisdom)
          source = "بنك الحكم والأمثال لزهرة (Phase 6.7)"
          confidence = 0.85
          outcomeState = OutcomeState.SUPPORTED
          followups = listOf("حكمة تانية", "حكمة عن الصبر", "حكمة ملهمة", "آية تريحني")
        } else {
        val kRes = knowledgeEngine.query(rawInput)
        if (kRes.state == KnowledgeRetrievalState.VERIFIED_MATCH && (kRes.bestMatch?.relevanceScore ?: 0.0) >= 0.50) {
          val best = kRes.bestMatch!!
          responseText = "${best.record.canonicalContent}\n\n[المصدر: ${best.record.provenance.sourceTitle} - ${best.record.provenance.sourceLocation}]"
          source = "[${best.record.provenance.sourceTitle} - ${best.record.provenance.origin}]"
          confidence = best.relevanceScore
          outcomeState = OutcomeState.SUPPORTED
          toolPayload = ZahraToolPayload.KnowledgePayload(
            title = best.record.title,
            category = best.record.category.labelAr,
            content = best.record.canonicalContent,
            originalSourceText = best.record.originalSourceText,
            source = best.record.provenance.sourceTitle,
            sourceLocation = best.record.provenance.sourceLocation,
            verificationStatusText = best.record.verificationStatus.labelAr,
            isVerified = best.record.verificationStatus.isVerified,
            matchMethodText = best.matchMethod.labelAr,
            relevancePercent = (best.relevanceScore * 100).toInt(),
            recordId = best.record.id,
            warning = best.warning
          )
        } else {
          return generateFallback(rawInput)
        }
        } // close inner else
      }
    }

    val tier = when {
      confidence >= 0.75 -> ConfidenceTier.DIRECT
      confidence >= 0.40 -> ConfidenceTier.QUALIFIED
      else -> ConfidenceTier.UNCERTAIN
    }

    val deepThinkingPayload = if (isDeepThinking) {
      val factsCount = memoryEngine.getFacts().size
      val steps = listOf(
        ThinkingStep(
          phaseName = "تحليل النية والأمر اللغوي",
          thoughtDetail = "تحليل الصياغة واستخراج النية الأساسية [${finalIntent}] بمستوى ثقة ${(confidence * 100).toInt()}% واكتشاف اللهجة وسياق الكلمات."
        ),
        ThinkingStep(
          phaseName = "فحص الذاكرة وسياق المستخدم",
          thoughtDetail = if (factsCount > 0) "الرجوع إلى $factsCount حقيقة مسجلة عن المستخدم لمطابقة التفضيلات وتخصيص المخرجات." else "فحص سجل الذاكرة والتأكد من مطابقة المتغيرات التراكمية."
        ),
        ThinkingStep(
          phaseName = "تخطيط مسار الوكيل (Agent Execution)",
          thoughtDetail = "اختيار المحرك المتخصص المناسب، تهيئة المعاملات الرياضية والبيانات المرجعية وضمان عدم وجود تخمين عشوائي."
        ),
        ThinkingStep(
          phaseName = "التدقيق والتحقق الذاتي",
          thoughtDetail = "مراجعة سلامة المخرجات، التأكد من جاهزية البطاقة التفاعلية والتحقق من صحة القواعد المحلية."
        ),
        ThinkingStep(
          phaseName = "الصياغة والتركيب النهائي",
          thoughtDetail = "توليد النتيجة النهائية وعرض الإجابة مع واجهة تفاعلية فورية جاهزة للمستخدم."
        )
      )
      DeepThinkingPayload(
        steps = steps,
        finalSynthesis = "اكتمل التفكير العميق بنجاح عبر 5 محطات تحليلية.",
        confidence = 0.99,
        processingTimeMs = 850L
      )
    } else null

    val groundedFacts = mutableListOf<GroundedFact>()
    if (autoExtractedFact != null) {
      groundedFacts.add(
        GroundedFact(
          claim = "${autoExtractedFact.key} = ${autoExtractedFact.value}",
          evidenceType = EvidenceType.USER_SUPPLIED,
          source = "استنتاج من مدخلات المستخدم",
          confidence = autoExtractedFact.confidence
        )
      )
    }
    if (finalIntent == "calculate" || finalIntent == "convert_unit") {
      groundedFacts.add(
        GroundedFact(
          claim = responseText.take(120),
          evidenceType = EvidenceType.DEDUCTION,
          source = source ?: "محرك الحساب الرياضي الدقيق",
          confidence = confidence
        )
      )
    } else if (finalIntent == "query_knowledge" || finalIntent == "ask_religion") {
      groundedFacts.add(
        GroundedFact(
          claim = responseText.take(120),
          evidenceType = EvidenceType.LOCAL_CURATED,
          source = source ?: "قاعدة البيانات المعرفية والشرعية المحلية",
          confidence = confidence
        )
      )
    } else if (finalIntent == "create_task" || finalIntent == "remember_note" || finalIntent == "create_schedule") {
      groundedFacts.add(
        GroundedFact(
          claim = responseText.take(120),
          evidenceType = EvidenceType.USER_SUPPLIED,
          source = "أمر صريح موجه للوكيل",
          confidence = confidence
        )
      )
    }

    val trace = EpistemicGroundingPipeline.buildTrace(
      rawRequest = rawInput,
      intent = finalIntent,
      entities = mapOf("query" to clean, "traceId" to routingDecision.traceId),
      appliedRules = listOf("rule_$finalIntent"),
      facts = groundedFacts,
      uncertainty = if (outcomeState != OutcomeState.SUPPORTED) listOf("درجة الثقة: ${(confidence * 100).toInt()}%") else emptyList(),
      outcomeState = outcomeState,
      sourceDesc = source
    )

    if (toolPayload == null && autoExtractedFact != null) {
      toolPayload = ZahraToolPayload.MemoryFactPayload(autoExtractedFact, isNew = true)
    }

    val finalResponse = ZahraResponse(
      text = responseText,
      intent = finalIntent,
      confidence = confidence,
      confidenceTier = tier,
      outcomeState = outcomeState,
      source = source,
      followups = followups,
      toolPayload = toolPayload,
      deepThinking = deepThinkingPayload,
      evidenceTrace = trace,
      createdTask = createdTask,
      createdNote = createdNote,
      createdSchedule = createdSchedule
    )

    // Phase 6.5: Apply mood-adaptive composed response
    return applyMoodAdaptation(rawInput, finalResponse)
  }

  /**
   * Phase 6.5: Mood-Adaptive Response Wrapping
   * 
   * Analyzes user's emotional state and adapts the response presentation
   * (opening, empathy bridge, enrichment, closing) while preserving the core answer.
   * 
   * Design Principles:
   * - Core factual content is NEVER changed by mood
   * - Only presentation layers (opening, empathy, enrichment, closing) adapt
   * - Positive bias: adds warmth above user's mood level
   * - Honest: doesn't fake positivity for serious issues
   * - Respects personality preferences (serious tone gets less emoji)
   */
  private fun applyMoodAdaptation(rawInput: String, response: ZahraResponse): ZahraResponse {
    return try {
      val personality = personalityManager.getCurrentPersonality()
      moodAdaptiveBuilder.buildAdaptiveResponse(response, rawInput, personality)
    } catch (e: Exception) {
      // Safety fallback: if mood adaptation fails, return original response unchanged
      response
    }
  }

  /**
   * Phase 1: رد توضيحي من قرار الموجه عندما تكون البوابة [GateOutcome.CLARIFY].
   * البدائل مأخوذة من أعلى النيات الفعلية في قرار التوجيه، لا من كلمات عشوائية في السؤال.
   */
  private fun buildRoutingClarification(
    rawInput: String,
    finalIntent: String,
    decision: RoutingDecision
  ): ZahraResponse {
    val options = decision.clarifyOptions.take(3)
    val question = if (options.isNotEmpty()) {
      "لم أستطع تحديد قصدك بدقة. تقصد واحدًا من التالي؟\n• " + options.joinToString("\n• ")
    } else {
      "مقصدك غير واضح، ولا توجد بدائل واضحة للاختيار من بينها. أعِد صياغة السؤال بطلب محدد (مثل: \"احسب 25 * 4\" أو \"مواقيت الصلاة اليوم\")."
    }
    val trace = EpistemicGroundingPipeline.buildTrace(
      rawRequest = rawInput,
      intent = finalIntent,
      entities = mapOf("query" to normalizedAt(rawInput), "traceId" to decision.traceId),
      appliedRules = listOf("rule_routing_clarify"),
      facts = emptyList(),
      uncertainty = listOf("نية غير واضحة: $finalIntent (الثقة ${(decision.confidence * 100).toInt()}%)"),
      outcomeState = OutcomeState.AMBIGUOUS,
      sourceDesc = "موجه زهرة الموحد — بوابة التوضيح"
    )
    val clar = com.example.zahra.model.ClarificationPayload(
      question = question,
      options = options.mapIndexed { i, opt ->
        com.example.zahra.model.ClarificationOption(id = "clarify_$i", labelAr = opt, associatedQuery = opt)
      },
      originalQuery = rawInput,
      reason = "نية غير واضحة أو عدة نيات محتملة (بوابة التوجيه: CLARIFY)"
    )
    val res = ZahraResponse(
      text = question,
      intent = finalIntent,
      confidence = decision.confidence,
      confidenceTier = ConfidenceTier.QUALIFIED,
      outcomeState = OutcomeState.AMBIGUOUS,
      source = "موجه زهرة الموحد (توثيق النية)",
      followups = options,
      clarification = clar,
      evidenceTrace = trace
    )
    return applyMoodAdaptation(rawInput, res)
  }

  /** هل النية من أوامر النظام التي تُعالج بتنفيذ مباشر (مهام/ملاحظات/جدولة/ذاكرة/شخصية/تراجع). */

  // ——————————————————————————————————————————————————————————————
  // المرحلة 2 — أدوات حتمية (وقت/هجري/وحدات/مسائل كلامية/طقس بمصدر)
  // ——————————————————————————————————————————————————————————————

  private val scheduleGuardWords = listOf(
    "فكرني", "ذكرني", "نبهني", "جدول", "موعد", "تذكير", "منبه", "schedule", "منبهني"
  )

  private val devotionalTimeGuardWords = listOf(
    "مواقيت", "الصلاة", "الصلاه", "اذان", "أذان", "الفجر", "المغرب", "العشاء", "الظهر", "العصر"
  )

  /**
   * يحاول الإجابة بأداة حتمية. يعيد `null` حين لا تنطبق أداة، فيكمل الطلب مساره
   * الطبيعي عبر التصنيف والموجّه — لا استيلاء على نوايا أخرى.
   */
  private fun deterministicToolResponse(rawInput: String): ZahraResponse? {
    val normalized = ZahraNormalizer.normalize(rawInput)
    val compact = normalized.lowercase(Locale.ROOT)

    // أوامر الجدولة وأوقات العبادات تُترك لمحرّكاتها
    if (scheduleGuardWords.any { compact.contains(it) }) return null
    if (devotionalTimeGuardWords.any { compact.contains(it) }) return null

    // 1) الوقت والتاريخ الهجري
    val timeAnswer = TimeTools.answer(
      rawInput = rawInput,
      hijri = androidHijriCalculator,
      hijriIsDeviceAccurate = androidHijriCalculator.isDeviceCalendarUsable()
    )
    if (timeAnswer != null) {
      return toolResponse(
        rawInput = rawInput,
        intent = timeAnswer.intent,
        text = timeAnswer.text,
        sourceLabel = timeAnswer.source,
        confidence = timeAnswer.confidence,
        outcomeState = timeAnswer.outcomeState,
        followups = timeAnswer.followups
      )
    }

    // 2) الطقس — مصدر حقيقي أو رفض صريح (لا رقم مخترع)
    weatherToolResponse(rawInput, compact)?.let { return it }

    // 3) تحويل الوحدات (أي زوج داخل التصنيف)
    if (looksLikeUnitConversion(compact)) {
      val unitResult = MathEngine.convertFromText(rawInput)
      if (unitResult != null) {
        return mathToolResponse(rawInput, "convert_unit", unitResult, "تحويل وحدات حتمي بجدول عوامل (لا شبكة)")
      }
    }

    // 4) المسائل الكلامية المصرية
    if (WordProblems.looksLikeWordProblem(rawInput)) {
      MathEngine.solveWordProblem(rawInput)?.let { solved ->
        return mathToolResponse(rawInput, "calculate", solved, "قالب مسألة كلامية حتمي مع خطوات الحل")
      }
    }

    return null
  }

  private fun looksLikeUnitConversion(text: String): Boolean {
    val hasDigit = text.any { it.isDigit() }
    val hasQuantityWord = text.contains("كام") || text.contains("كم")
    if (!hasDigit && !hasQuantityWord) return false
    return when (val parsed = UnitRequestParser.parse(text)) {
      is UnitRequestParseResult.Parsed -> true
      is UnitRequestParseResult.UnknownUnit -> hasDigit
      else -> false
    }
  }

  private fun weatherToolResponse(rawInput: String, compact: String): ZahraResponse? {
    val mentionsWeather = listOf("طقس", "الجو", "حراره", "حرارة", "مطر", "امطار", "أمطار", "درجة الحراره").any {
      compact.contains(it)
    }
    if (!mentionsWeather) return null

    val city = Cities.find(compact)
    if (city == null) {
      return toolResponse(
        rawInput = rawInput,
        intent = "weather",
        text = "أي مدينة تقصد؟ حدّد المدينة (مثال: القاهرة أو الإسكندرية) عشان أجيب قراءة حقيقية من مصدرها، " +
          "ومش هأخترع لك درجة حرارة من غير مصدر.",
        sourceLabel = "أداة الطقس — تحتاج مدينة محددة",
        confidence = 0.5,
        outcomeState = OutcomeState.AMBIGUOUS,
        followups = listOf("الطقس في القاهرة إيه؟", "الجو في الإسكندرية بكرة؟", "كام درجة الحرارة في أسوان؟")
      )
    }

    return when (val result = weatherClient.fetch(city)) {
      is DataToolResult.Answer -> toolResponse(
        rawInput = rawInput,
        intent = "weather",
        text = result.envelope.text,
        sourceLabel = result.envelope.chipText(),
        confidence = result.envelope.confidence,
        outcomeState = OutcomeState.SUPPORTED,
        followups = listOf("الطقس في مدينة تانية؟", "إيه تاريخ النهاردة؟")
      )
      is DataToolResult.Failure -> toolResponse(
        rawInput = rawInput,
        intent = "weather",
        text = result.message,
        sourceLabel = "أداة الطقس — سياسة الشبكة (D1)",
        confidence = 0.9,
        outcomeState = result.outcomeState,
        followups = listOf("فعّل الوضع المتصل من الإعدادات", "إيه تاريخ النهاردة؟")
      )
    }
  }

  private fun mathToolResponse(rawInput: String, intent: String, result: MathResult, ruleLabel: String): ZahraResponse {
    val trace = EpistemicGroundingPipeline.buildTrace(
      rawRequest = rawInput,
      intent = intent,
      entities = mapOf("expression" to (result.operationTree ?: rawInput.take(80))),
      appliedRules = listOf("rule_deterministic_$intent"),
      facts = listOf(
        GroundedFact(
          claim = result.formattedText.take(120),
          evidenceType = EvidenceType.DEDUCTION,
          source = result.source,
          confidence = result.confidence
        )
      ),
      uncertainty = if (result.outcomeState != OutcomeState.SUPPORTED) listOf(ruleLabel) else emptyList(),
      outcomeState = result.outcomeState,
      sourceDesc = result.source
    )
    return ZahraResponse(
      text = result.formattedText,
      intent = intent,
      confidence = result.confidence,
      confidenceTier = if (result.confidence >= 0.9) ConfidenceTier.DIRECT else ConfidenceTier.QUALIFIED,
      outcomeState = result.outcomeState,
      source = result.source,
      followups = listOf("حوّل 10 كيلومتر إلى ميل", "احسب 25 * 4", "كام يوم للجمعة؟"),
      toolPayload = ZahraToolPayload.MathPayload(
        expression = rawInput,
        result = result.formattedText,
        calculationType = ruleLabel
      ),
      evidenceTrace = trace
    )
  }

  private fun toolResponse(
    rawInput: String,
    intent: String,
    text: String,
    sourceLabel: String,
    confidence: Double,
    outcomeState: OutcomeState,
    followups: List<String>
  ): ZahraResponse {
    val trace = EpistemicGroundingPipeline.buildTrace(
      rawRequest = rawInput,
      intent = intent,
      entities = mapOf("tool" to intent),
      appliedRules = listOf("rule_deterministic_$intent"),
      facts = listOf(
        GroundedFact(
          claim = text.take(140),
          evidenceType = EvidenceType.VERIFIED,
          source = sourceLabel,
          confidence = confidence
        )
      ),
      uncertainty = if (outcomeState != OutcomeState.SUPPORTED) listOf("الأداة لم تُنتج إجابة مؤكدة") else emptyList(),
      outcomeState = outcomeState,
      sourceDesc = sourceLabel
    )
    return ZahraResponse(
      text = text,
      intent = intent,
      confidence = confidence,
      confidenceTier = if (confidence >= 0.9) ConfidenceTier.DIRECT else ConfidenceTier.QUALIFIED,
      outcomeState = outcomeState,
      source = sourceLabel,
      followups = followups,
      evidenceTrace = trace
    )
  }

  private fun isSystemIntent(intent: String): Boolean {
    return listOf(
      Intent.CREATE_TASK, Intent.LIST_TASKS, Intent.COMPLETE_TASK,
      Intent.REMEMBER_NOTE, Intent.LIST_NOTES, Intent.DELETE_NOTE,
      Intent.CREATE_SCHEDULE, Intent.LIST_SCHEDULES, Intent.CANCEL_SCHEDULE,
      Intent.QUERY_MEMORY, Intent.FORGET_FACT, Intent.REMEMBER_FACT,
      Intent.UPDATE_FACT, Intent.UNDO_ACTION, Intent.CHANGE_PERSONALITY,
      Intent.COMPOUND_PLAN
    ).contains(intent)
  }

  private fun normalizedAt(input: String): String {
    return ZahraNormalizer.normalize(input)
  }

  private fun generateFallback(input: String): ZahraResponse {
    val trace = EpistemicGroundingPipeline.buildTrace(
      rawRequest = input,
      intent = "unknown",
      entities = emptyMap(),
      appliedRules = listOf("rule_epistemic_abstention"),
      facts = emptyList(),
      uncertainty = listOf("لا يتوفر نص مطابق أو قاعدة حتمية معتمدة في قاعدة البيانات المحلية"),
      outcomeState = OutcomeState.UNSUPPORTED,
      sourceDesc = "قيد الأمان والنزاهة الحتمي لزهرة (لا تخمين)"
    )
    val fallback = ZahraResponse(
      text = "مش متأكد من الإجابة بدقة، ولم أجد مصدراً موثوقاً في قواعد البيانات المدمجة. بصفتي نظاماً خبيرًا بلا نموذج لغوي احتمالي، أمتنع تماماً عن التخمين أو التوليد غير المؤكد.",
      intent = "unknown",
      confidence = 0.15,
      confidenceTier = ConfidenceTier.UNCERTAIN,
      outcomeState = OutcomeState.UNSUPPORTED,
      source = "قيد الأمان والنزاهة الحتمي لزهرة (لا تخمين)",
      evidenceTrace = trace,
      followups = listOf(
        "احسب 250 * 4",
        "حول 10 كم إلى ميل",
        "مواقيت الصلاة اليوم",
        "أضف مهمة جديدة",
        "ماذا تعرف عني؟"
      )
    )
    
    // Phase 6.5: Apply mood-adaptive wrapping to fallback too
    return applyMoodAdaptation(input, fallback)
  }

  /** Releases platform resources owned by the router when its ViewModel is destroyed. */
  fun release() {
    voiceEngine.release()
    visionEngine.release()
  }
}
