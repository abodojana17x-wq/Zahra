package com.example.zahra.intelligence.planner

enum class PlanActionType(val labelAr: String) {
  CALCULATE("حساب رياضي"),
  CONVERT_UNIT("تحويل وحدات"),
  SUMMARIZE("تلخيص استخلاصي"),
  TRANSLATE("ترجمة فورية"),
  QUERY_KNOWLEDGE("استعلام معرفي"),
  CREATE_TASK("إنشاء مهمة"),
  CREATE_NOTE("حفظ ملاحظة"),
  CREATE_SCHEDULE("جدولة تنبيه"),
  REMEMBER_FACT("تثبيت في الذاكرة"),
  UNKNOWN("إجراء غير محدد")
}

enum class StepExecutionStatus(val labelAr: String) {
  PENDING("قيد الانتظار"),
  RUNNING("جاري التنفيذ"),
  SUCCESS("تم بنجاح"),
  FAILED("تعذر التنفيذ"),
  SKIPPED("تم التخطي"),
  ROLLED_BACK("تم التراجع")
}

data class ArgumentBinding(
  val targetParam: String,      // e.g., "title", "content", "expression", "text"
  val sourceStepId: String,     // ID of step providing the output
  val sourceField: String = "output" // Field to extract: "output", "title", "id"
)

data class PlannedStep(
  val id: String = java.util.UUID.randomUUID().toString(),
  val stepNumber: Int,
  val title: String,
  val actionType: PlanActionType,
  val rawCommand: String,
  val parameters: Map<String, String> = emptyMap(),
  val argumentBindings: List<ArgumentBinding> = emptyList(),
  val dependsOnStepIds: List<String> = emptyList(),
  val status: StepExecutionStatus = StepExecutionStatus.PENDING,
  val output: String? = null,
  val errorMessage: String? = null,
  val isRollbackAvailable: Boolean = false,
  val rollbackDescription: String? = null
)

data class ExecutionPlan(
  val id: String = java.util.UUID.randomUUID().toString(),
  val originalRequest: String,
  val title: String,
  val steps: List<PlannedStep>,
  val overallConfidence: Double,
  val isCompleted: Boolean = false,
  val hasFailures: Boolean = false,
  val executionSummary: String = "",
  val executedAt: Long = System.currentTimeMillis()
)
