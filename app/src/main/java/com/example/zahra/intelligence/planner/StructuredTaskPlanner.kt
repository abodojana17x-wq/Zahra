package com.example.zahra.intelligence.planner

import com.example.zahra.core.ZahraNormalizer

object StructuredTaskPlanner {

  /**
   * Determine if the input represents a compound multi-step goal.
   */
  fun isCompoundRequest(input: String): Boolean {
    val candidates = CompoundRequestAnalyzer.analyze(input)
    return candidates.size > 1
  }

  /**
   * Plan a structured execution graph for compound or single requests.
   */
  fun plan(rawInput: String): ExecutionPlan {
    val candidates = CompoundRequestAnalyzer.analyze(rawInput)

    if (candidates.size <= 1) {
      val singleType = inferActionType(rawInput)
      val singleStep = PlannedStep(
        stepNumber = 1,
        title = getStepTitle(singleType, rawInput),
        actionType = singleType,
        rawCommand = rawInput.trim(),
        parameters = extractStepParams(singleType, rawInput.trim())
      )
      return ExecutionPlan(
        originalRequest = rawInput,
        title = singleStep.title,
        steps = listOf(singleStep),
        overallConfidence = 0.95
      )
    }

    val plannedSteps = mutableListOf<PlannedStep>()
    var previousStep: PlannedStep? = null

    for (candidate in candidates) {
      val actionType = inferActionType(candidate.rawClause)
      val stepNum = candidate.order
      val stepId = "step_${stepNum}_${java.util.UUID.randomUUID().toString().take(6)}"

      val dependencies = mutableListOf<String>()
      val bindings = mutableListOf<ArgumentBinding>()

      val prev = previousStep
      val isConsumer = actionType in listOf(
        PlanActionType.CREATE_NOTE,
        PlanActionType.CREATE_TASK,
        PlanActionType.CREATE_SCHEDULE,
        PlanActionType.REMEMBER_FACT
      )
      val isProducer = prev != null && prev.actionType in listOf(
        PlanActionType.CALCULATE,
        PlanActionType.CONVERT_UNIT,
        PlanActionType.SUMMARIZE,
        PlanActionType.TRANSLATE,
        PlanActionType.QUERY_KNOWLEDGE
      )

      if (prev != null && (candidate.refersToPreviousOutput || (isConsumer && isProducer))) {
        dependencies.add(prev.id)
        val targetParam = when (actionType) {
          PlanActionType.CREATE_NOTE -> "content"
          PlanActionType.CREATE_TASK -> "title"
          PlanActionType.CREATE_SCHEDULE -> "title"
          PlanActionType.REMEMBER_FACT -> "value"
          else -> "input"
        }
        bindings.add(
          ArgumentBinding(
            targetParam = targetParam,
            sourceStepId = prev.id,
            sourceField = "output"
          )
        )
      }

      val stepTitle = getStepTitle(actionType, candidate.rawClause)
      val params = extractStepParams(actionType, candidate.rawClause)

      val step = PlannedStep(
        id = stepId,
        stepNumber = stepNum,
        title = stepTitle,
        actionType = actionType,
        rawCommand = candidate.rawClause,
        parameters = params,
        argumentBindings = bindings,
        dependsOnStepIds = dependencies,
        isRollbackAvailable = actionType in listOf(PlanActionType.CREATE_TASK, PlanActionType.CREATE_NOTE, PlanActionType.CREATE_SCHEDULE),
        rollbackDescription = "التراجع عن إنشاء العنصر والتأكد من حذفه من قاعدة البيانات المحلية"
      )

      plannedSteps.add(step)
      previousStep = step
    }

    // Validate DAG for cycles
    validateDag(plannedSteps)

    val planTitle = "خطة عمل متسلسلة (${plannedSteps.size} خطوات)"
    return ExecutionPlan(
      originalRequest = rawInput,
      title = planTitle,
      steps = plannedSteps,
      overallConfidence = 0.94
    )
  }

  private fun inferActionType(clause: String): PlanActionType {
    val norm = ZahraNormalizer.normalize(clause)
    return when {
      norm.contains("احسب") || norm.contains("calc") || norm.contains("ضرب") ||
        norm.contains("قسم") || norm.contains("جذر") -> PlanActionType.CALCULATE
      norm.contains("حول") || norm.contains("convert") -> PlanActionType.CONVERT_UNIT
      norm.contains("لخص") || norm.contains("summarize") -> PlanActionType.SUMMARIZE
      norm.contains("ترجم") || norm.contains("translate") -> PlanActionType.TRANSLATE
      norm.contains("مهم") || norm.contains("تاسك") || norm.contains("task") ||
        norm.contains("اضف مهم") -> PlanActionType.CREATE_TASK
      norm.contains("ملاحظ") || norm.contains("note") || norm.contains("سجل ملاحظ") ||
        norm.contains("احفظ في ملاحظ") -> PlanActionType.CREATE_NOTE
      norm.contains("فكرني") || norm.contains("تذكير") || norm.contains("جدول") ||
        norm.contains("منبه") || norm.contains("remind") || norm.contains("schedule") -> PlanActionType.CREATE_SCHEDULE
      norm.contains("تذكر ان") || norm.contains("احفظ ان") -> PlanActionType.REMEMBER_FACT
      norm.contains("ما هي") || norm.contains("ما هو") || norm.contains("من هو") ||
        norm.contains("what is") || norm.contains("define") -> PlanActionType.QUERY_KNOWLEDGE
      else -> PlanActionType.UNKNOWN
    }
  }

  private fun getStepTitle(type: PlanActionType, clause: String): String {
    return when (type) {
      PlanActionType.CALCULATE -> "حساب العملية الرياضية"
      PlanActionType.CONVERT_UNIT -> "تحويل الوحدة القياسية"
      PlanActionType.SUMMARIZE -> "تلخيص النص استخلاصياً"
      PlanActionType.TRANSLATE -> "ترجمة النص"
      PlanActionType.QUERY_KNOWLEDGE -> "استعلام قاعدة المعرفة المعتمدة"
      PlanActionType.CREATE_TASK -> "إنشاء مهمة جديدة في القائمة"
      PlanActionType.CREATE_NOTE -> "حفظ تدوينة وملاحظة مشفرة"
      PlanActionType.CREATE_SCHEDULE -> "جدولة تذكير وتنبيه محلي"
      PlanActionType.REMEMBER_FACT -> "تثبيت حقيقة في الذاكرة الدائمة"
      PlanActionType.UNKNOWN -> "معالجة الأمر: ${clause.take(25)}"
    }
  }

  private fun extractStepParams(type: PlanActionType, clause: String): Map<String, String> {
    val params = mutableMapOf<String, String>()
    params["raw"] = clause
    when (type) {
      PlanActionType.CALCULATE -> {
        val expr = clause.replace(Regex("^(?:احسب|calc|معلش احسبلي)\\s+", RegexOption.IGNORE_CASE), "").trim()
        params["expression"] = expr
      }
      PlanActionType.CREATE_TASK -> {
        val title = clause.replace(Regex("^(?:أضف\\s+مهمة|اضف\\s+مهمة|أضف\\s+مهمه|اضف\\s+مهمه|سجل\\s+مهمة|سجل\\s+مهمه|مهمة|مهمه|add task)\\s*", RegexOption.IGNORE_CASE), "").trim()
        params["title"] = title
      }
      PlanActionType.CREATE_NOTE -> {
        val noteContent = clause.replace(Regex("^(?:سجل\\s+ملاحظة|سجل\\s+ملاحظه|احفظ\\s+في\\s+ملاحظة|احفظ\\s+في\\s+ملاحظه|احفظ\\s+ملاحظة|احفظ\\s+ملاحظه|اكتب\\s+عندك\\s+ملاحظة|اكتب\\s+عندك\\s+ملاحظه|ملاحظة|ملاحظه|save note)\\s*", RegexOption.IGNORE_CASE), "").trim()
        params["content"] = noteContent
      }
      PlanActionType.CREATE_SCHEDULE -> {
        params["clause"] = clause
      }
      PlanActionType.SUMMARIZE -> {
        val toSum = clause.replace(Regex("^(?:لخص|summarize|لخص النص التالي|لخص هذا المقال)\\s*", RegexOption.IGNORE_CASE), "").trim()
        params["text"] = toSum
      }
      PlanActionType.TRANSLATE -> {
        val toTrans = clause.replace(Regex("^(?:ترجم|translate)\\s*", RegexOption.IGNORE_CASE), "").trim()
        params["text"] = toTrans
      }
      else -> {}
    }
    return params
  }

  private fun validateDag(steps: List<PlannedStep>) {
    val stepIds = steps.map { it.id }.toSet()
    for (step in steps) {
      for (depId in step.dependsOnStepIds) {
        if (!stepIds.contains(depId)) {
          throw IllegalArgumentException("Invalid plan: step '${step.id}' depends on non-existent step '$depId'")
        }
        if (depId == step.id) {
          throw IllegalArgumentException("Invalid plan: self-dependency cycle in step '${step.id}'")
        }
      }
    }
  }
}
