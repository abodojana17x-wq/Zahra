package com.example.zahra.intelligence.execution

import com.example.zahra.engines.*
import com.example.zahra.intelligence.planner.*
import com.example.zahra.knowledge.engine.KnowledgeEngine
import com.example.zahra.knowledge.model.KnowledgeRetrievalState
import com.example.zahra.model.OutcomeState

class PlanExecutor(
  private val taskEngine: TaskEngine,
  private val noteEngine: NoteEngine,
  private val scheduleEngine: ScheduleEngine,
  private val memoryEngine: MemoryEngine,
  private val translationEngine: TranslationEngine,
  private val knowledgeEngine: KnowledgeEngine
) {

  data class ExecutionContext(
    val outputsByStepId: MutableMap<String, String> = mutableMapOf(),
    val createdTaskIds: MutableList<String> = mutableListOf(),
    val createdNoteIds: MutableList<String> = mutableListOf(),
    val createdScheduleIds: MutableList<String> = mutableListOf()
  )

  /**
   * Execute a structured plan step-by-step with dependency resolution and transactional rollback.
   */
  fun execute(plan: ExecutionPlan): ExecutionPlan {
    val context = ExecutionContext()
    val updatedSteps = mutableListOf<PlannedStep>()
    var executionFailed = false
    var failureReason: String? = null

    for (step in plan.steps) {
      if (executionFailed) {
        updatedSteps.add(
          step.copy(
            status = StepExecutionStatus.SKIPPED,
            errorMessage = "تم تخطي هذه الخطوة بسبب تعذر تنفيذ خطوة سابقة: $failureReason"
          )
        )
        continue
      }

      // 1. Resolve Bound Arguments from Preceding Steps
      val resolvedParams = step.parameters.toMutableMap()
      for (binding in step.argumentBindings) {
        val prevOutput = context.outputsByStepId[binding.sourceStepId]
        if (!prevOutput.isNullOrBlank()) {
          when (step.actionType) {
            PlanActionType.CREATE_NOTE -> {
              val current = resolvedParams["content"] ?: ""
              resolvedParams["content"] = if (current.isBlank() || current.contains("ملاحظة")) prevOutput else "$current\n$prevOutput"
            }
            PlanActionType.CREATE_TASK -> {
              val current = resolvedParams["title"] ?: ""
              resolvedParams["title"] = if (current.isBlank() || current.contains("مهمة")) prevOutput else "$current: $prevOutput"
            }
            PlanActionType.CREATE_SCHEDULE -> {
              val current = resolvedParams["clause"] ?: ""
              resolvedParams["clause"] = if (current.isBlank()) prevOutput else "$current $prevOutput"
            }
            PlanActionType.CALCULATE -> {
              val current = resolvedParams["expression"] ?: ""
              resolvedParams["expression"] = if (current.isBlank()) prevOutput else current
            }
            else -> {
              resolvedParams[binding.targetParam] = prevOutput
            }
          }
        }
      }

      // 2. Execute Step by Action Type
      try {
        val execResult = executeStep(step, resolvedParams, context)
        if (execResult.isSuccess) {
          context.outputsByStepId[step.id] = execResult.output ?: ""
          updatedSteps.add(
            step.copy(
              status = StepExecutionStatus.SUCCESS,
              output = execResult.output
            )
          )
        } else {
          executionFailed = true
          failureReason = execResult.errorMessage ?: "فشل غير محدد أثناء تنفيذ الخطوة"
          updatedSteps.add(
            step.copy(
              status = StepExecutionStatus.FAILED,
              errorMessage = failureReason
            )
          )
        }
      } catch (e: Exception) {
        executionFailed = true
        failureReason = e.message ?: e.javaClass.simpleName
        updatedSteps.add(
          step.copy(
            status = StepExecutionStatus.FAILED,
            errorMessage = failureReason
          )
        )
      }
    }

    // 3. Transactional Rollback if Plan Execution Failed
    if (executionFailed) {
      rollbackMutations(context)
      // Update statuses of rolled back steps
      for (i in updatedSteps.indices) {
        val s = updatedSteps[i]
        if (s.status == StepExecutionStatus.SUCCESS && s.isRollbackAvailable) {
          updatedSteps[i] = s.copy(
            status = StepExecutionStatus.ROLLED_BACK,
            output = "${s.output} [تم التراجع وحذف السجل لضمان سلامة البيانات]"
          )
        }
      }
    }

    val summary = if (!executionFailed) {
      "تم تنفيذ الخطة بنجاح وبشكل متسلسل (${plan.steps.size} خطوات)."
    } else {
      "تعذر إكمال الخطة بالكامل بسبب: $failureReason. تم تفعيل التراجع الآمن عن الإجراءات الجزئية."
    }

    return plan.copy(
      steps = updatedSteps,
      isCompleted = !executionFailed,
      hasFailures = executionFailed,
      executionSummary = summary,
      executedAt = System.currentTimeMillis()
    )
  }

  private data class StepResult(
    val isSuccess: Boolean,
    val output: String? = null,
    val errorMessage: String? = null
  )

  private fun executeStep(
    step: PlannedStep,
    params: Map<String, String>,
    context: ExecutionContext
  ): StepResult {
    return when (step.actionType) {
      PlanActionType.CALCULATE -> {
        val expr = params["expression"] ?: step.rawCommand
        val res = MathEngine.evaluate(expr)
        if (res.outcomeState == OutcomeState.SUPPORTED) {
          StepResult(true, output = res.formattedText)
        } else {
          StepResult(false, errorMessage = res.formattedText)
        }
      }

      PlanActionType.CONVERT_UNIT -> {
        val parsed = com.example.zahra.entities.EntityExtractor.parseUnitRequest(step.rawCommand)
        if (parsed != null && parsed.toUnit.isNotBlank()) {
          val res = MathEngine.convertUnit(parsed.value, parsed.fromUnit, parsed.toUnit)
          StepResult(true, output = res.formattedText)
        } else {
          StepResult(false, errorMessage = "لم يتم تحديد الوحدات القياسية بشكل صحيح للتحويل")
        }
      }

      PlanActionType.SUMMARIZE -> {
        val textToSum = params["text"] ?: step.rawCommand
        if (textToSum.length < 15) {
          StepResult(true, output = textToSum)
        } else {
          val sumRes = SummarizeEngine.summarize(textToSum)
          StepResult(true, output = sumRes.summary)
        }
      }

      PlanActionType.TRANSLATE -> {
        val text = params["text"] ?: step.rawCommand
        val langInfo = com.example.zahra.core.LanguageDetector.detect(text)
        val target = if (langInfo.language == com.example.zahra.core.DetectedLang.AR) "en" else "ar"
        val res = translationEngine.translate(text, target)
        StepResult(true, output = res.translatedText)
      }

      PlanActionType.QUERY_KNOWLEDGE -> {
        val query = params["raw"] ?: step.rawCommand
        val kRes = knowledgeEngine.query(query)
        if (kRes.state == KnowledgeRetrievalState.VERIFIED_MATCH || kRes.state == KnowledgeRetrievalState.TRACEABLE_CANDIDATE) {
          StepResult(true, output = kRes.bestMatch?.record?.canonicalContent)
        } else {
          StepResult(false, errorMessage = kRes.abstentionReason ?: "لم يُعثر على حقيقة محققة مطابقة")
        }
      }

      PlanActionType.CREATE_TASK -> {
        val title = params["title"] ?: step.rawCommand
        if (title.isBlank()) {
          return StepResult(false, errorMessage = "عنوان المهمة فارغ")
        }
        val task = taskEngine.createTask(title)
        context.createdTaskIds.add(task.id)
        StepResult(true, output = "المهمة: \"${task.title}\" (أولوية: ${task.priorityScore}/5)")
      }

      PlanActionType.CREATE_NOTE -> {
        val noteContent = params["content"] ?: step.rawCommand
        if (noteContent.isBlank()) {
          return StepResult(false, errorMessage = "محتوى الملاحظة فارغ")
        }
        val note = noteEngine.createNote(noteContent)
        context.createdNoteIds.add(note.id)
        StepResult(true, output = "الملاحظة: \"${note.title}\"")
      }

      PlanActionType.CREATE_SCHEDULE -> {
        val clause = params["clause"] ?: step.rawCommand
        when (val res = scheduleEngine.validateAndSchedule(clause)) {
          is ScheduleValidationResult.Success -> {
            context.createdScheduleIds.add(res.schedule.id)
            StepResult(true, output = res.summary)
          }
          else -> {
            StepResult(false, errorMessage = "تعذر التحقق من صيغة الموعد أو جدولته بدقة")
          }
        }
      }

      PlanActionType.REMEMBER_FACT -> {
        val raw = step.rawCommand
        val res = memoryEngine.saveExplicitMemory(raw)
        if (res is com.example.zahra.model.MemorySaveResult.Success) {
          StepResult(true, output = "تم تثبيت الحقيقة: ${res.fact.key} = ${res.fact.value}")
        } else {
          StepResult(false, errorMessage = "تعذر التحقق من صيغة الحقيقة أو حفظها")
        }
      }

      PlanActionType.UNKNOWN -> {
        StepResult(true, output = "تمت معالجة الأمر: ${step.rawCommand}")
      }
    }
  }

  private fun rollbackMutations(context: ExecutionContext) {
    for (taskId in context.createdTaskIds) {
      try {
        taskEngine.deleteTask(taskId)
      } catch (_: Exception) {}
    }
    for (noteId in context.createdNoteIds) {
      try {
        noteEngine.deleteNote(noteId)
      } catch (_: Exception) {}
    }
    for (schedId in context.createdScheduleIds) {
      try {
        scheduleEngine.deleteSchedule(schedId)
      } catch (_: Exception) {}
    }
  }
}
