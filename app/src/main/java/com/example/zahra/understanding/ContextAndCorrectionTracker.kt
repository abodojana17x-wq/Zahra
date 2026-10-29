package com.example.zahra.understanding

import com.example.zahra.core.ZahraNormalizer
import com.example.zahra.dialogue.DialogueHistorySource
import com.example.zahra.model.FactCategory
import com.example.zahra.model.ZahraResponse

data class DialogTurn(
  val userQuery: String,
  val response: ZahraResponse,
  val timestamp: Long = System.currentTimeMillis()
)

sealed interface CorrectionResolution {
  data class UpdateFact(val category: FactCategory, val key: String, val newValue: String) : CorrectionResolution
  data class RefinedQuery(val reformulatedQuery: String, val reason: String) : CorrectionResolution
  data class UndoAction(val actionDescription: String) : CorrectionResolution
  object None : CorrectionResolution
}

/**
 * Multi-turn Dialog Context and User Correction Tracker.
 * Recognizes corrections ("لا مش كده", "اسمي أحمد مش محمد", "تراجع")
 * and resolves follow-up references using recent session context.
 *
 * المرحلة 4: هذا الصنف **لم يعد** يملك سجلًا مستقلًا في الوضع الافتراضي.
 * يُمرَّر مزوّد مصدر (`historyProvider`) يقرأ من `DialogueState`، فلا يوجد
 * سجل ثانٍ يمكن أن ينحرف عن الدورات المسلَّمة فعلًا. النسخة المحلية تبقى
 * متاحة فقط في وضع التشخيص `LegacyContextMode.MIRRORED`.
 */
class ContextAndCorrectionTracker(
  private val historyProvider: (() -> DialogueHistorySource?)? = null
) {

  // نسخة محلية لا تُستخدم إلا في وضع MIRRORED (رجوع آمن/تشخيص).
  private val recentTurns = mutableListOf<DialogTurn>()
  private val maxTurns = 5

  private fun projected(): DialogueHistorySource? = historyProvider?.invoke()

  /**true حين يقرأ الصنف من مصدر الحالة الوحيد. */
  fun isProjected(): Boolean = projected() != null

  @Synchronized
  fun recordTurn(query: String, response: ZahraResponse) {
    // في الوضع المُسقَط، التسجيل تمّ مرة واحدة في DialogueState؛ لا نكرّره هنا.
    if (projected() != null) return
    recentTurns.add(DialogTurn(query, response))
    if (recentTurns.size > maxTurns) {
      recentTurns.removeAt(0)
    }
  }

  /** يُعاد `null` في الوضع المُسقَط: لا يوجد كائن رد قديم كامل للقراءة الآمنة. */
  @Synchronized
  fun getLastTurn(): DialogTurn? = if (projected() != null) null else recentTurns.lastOrNull()

  @Synchronized
  fun getLastUserQuery(): String? =
    projected()?.lastUserQuery() ?: recentTurns.lastOrNull()?.userQuery

  @Synchronized
  fun getLastZahraResponse(): String? =
    projected()?.lastResponseText() ?: recentTurns.lastOrNull()?.response?.text

  @Synchronized
  fun clear() {
    recentTurns.clear()
  }

  /**
   * Analyzes an incoming query against the immediate previous turn to detect corrections or follow-up references.
   */
  fun detectCorrectionOrFollowup(rawInput: String): CorrectionResolution {
    val clean = rawInput.trim()
    val norm = ZahraNormalizer.normalize(clean)
    val source = projected()
    val lastQuery: String? = source?.lastUserQuery() ?: recentTurns.lastOrNull()?.userQuery
    val lastResponseText: String? = source?.lastResponseText() ?: recentTurns.lastOrNull()?.response?.text
    val lastIntent: String? = source?.lastIntent() ?: recentTurns.lastOrNull()?.response?.intent
    val hasLast = lastQuery != null

    // 1. Explicit undo / cancel:
    if (norm == "تراجع" || norm == "الغي ده" || norm == "تراجع عن ذلك" || norm == "الغ الاخير") {
      val desc = lastResponseText?.take(40) ?: "الإجراء الأخير"
      return CorrectionResolution.UndoAction(desc)
    }

    // 2. Fact correction pattern: "لا، أنا اسمي [X] مش [Y]" or "اسمي مش [Y]، اسمي [X]"
    // Require an explicit name correction. A bare "مش ..." can be an emotion,
    // not permission to replace the user's name.
    val nameCorrection = Regex("""^(?:اسمي\s+مش\s+[^\s،,]+\s*[,،]?\s*(?:بل|لكن|اسمي)\s+|(?:لا\s*[,،]?\s*)?(?:انا\s+)?اسمي\s+)([^\s،,]+)(?:\s+مش\s+[^\s،,]+)?""").find(norm)
    if (nameCorrection != null) {
      val newName = nameCorrection.groupValues[1].trim()
      if (newName.length >= 2 && newName != "مش") {
        return CorrectionResolution.UpdateFact(FactCategory.PERSONAL, "الاسم", newName)
      }
    }

    // Direct name update: "اسمي الجديد خالد" or "عدل اسمي الى خالد"
    val directNameUpdate = Regex("""(?:عدل\s+اسمي\s+(?:الى|إلى|الي)|اسمي\s+الجديد)\s+(.+)""").find(norm)
    if (directNameUpdate != null) {
      val newName = directNameUpdate.groupValues[1].trim()
      if (newName.isNotBlank()) {
        return CorrectionResolution.UpdateFact(FactCategory.PERSONAL, "الاسم", newName)
      }
    }

    // 3. User negation / refinement of previous turn:
    // e.g. "لا مش كده، قصدي..." or "قصدي احسب 100 * 2"
    if (norm.startsWith("قصدي ") || norm.startsWith("لا قصدي ") || norm.startsWith("لا، قصدي ") ||
        norm.startsWith("اقصد ") || norm.startsWith("لا اقصد ")) {
      val refined = clean.replace(Regex("""^(?:لا\s*[,،]?\s*)?(?:قصدي|اقصد)\s*"""), "").trim()
      if (refined.isNotBlank()) {
        return CorrectionResolution.RefinedQuery(refined, "تصحيح لمقصد المستخدم بناءً على اعتراضه")
      }
    }

    // 4. Follow-up query references:
    // "وماذا عن [س]؟" or "طب و [س]؟"
    if ((norm.startsWith("وماذا عن ") || norm.startsWith("طب و ") || norm.startsWith("ماذا عن ")) && hasLast) {
      val subject = clean.replace(Regex("""^(?:وماذا\s+عن|طب\s+و|ماذا\s+عن)\s*"""), "").trim()
      if (subject.isNotBlank()) {
        val reformulated = when (lastIntent) {
          "query_knowledge", "define_word" -> "معلومات عن $subject"
          "ask_religion" -> "حكم $subject"
          "translate" -> "ترجم $subject"
          else -> subject
        }
        return CorrectionResolution.RefinedQuery(reformulated, "متابعة لنفس سياق السؤال السابق ($lastIntent)")
      }
    }

    // 4b. نفس السؤال بس <تغيير>: يُعاد نفس السؤال السابق مع الجزء الجديد فقط،
    // ولا يُبنى سؤال من فراغ إذا لم توجد دورة سابقة.
    if (hasLast && lastQuery != null && Regex("^(?:و|طب\\s*)?نفس\\s+السؤال(?:\\s+بس)?\\s*(.*)$").matches(norm)) {
      if (norm.contains("نفس السؤال") && !lastQuery.contains("نفس السؤال")) {
        val suffix = clean.replace(Regex("""^(?:و|طب\s*)?نفس\s+السؤال(?:\s+بس)?\s*"""), "").trim()
        if (suffix.isNotBlank()) {
          return CorrectionResolution.RefinedQuery("$lastQuery $suffix", "نفس السؤال السابق مع تغيير السياق")
        }
      }
    }

    // 5. Follow-up summarization: "لخصه" / "لخص الرد"
    if ((norm == "لخصه" || norm == "لخص هذا" || norm == "لخص الرد") && lastResponseText != null) {
      if (lastResponseText.length >= 20) {
        return CorrectionResolution.RefinedQuery("لخص $lastResponseText", "طلب تلخيص لمخرجات الرد السابق")
      }
    }

    return CorrectionResolution.None
  }
}
