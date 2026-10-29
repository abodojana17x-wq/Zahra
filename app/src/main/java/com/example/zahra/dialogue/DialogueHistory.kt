package com.example.zahra.dialogue

/**
 * المصدر الوحيد لقراءة سجل الحوار (المرحلة 4، المسار A).
 *
 * الواجهات القديمة (`ContextAndCorrectionTracker`, `ContextManager`) لم تعد تحتفظ
 * بنسخة ثانية من الدورات؛ تقرأ من `DialogueState` عبر هذا العقد. هذا يمنع انحرافًا
 * بين سجلين ينتج عنه «ذاكرة» لا يقابلها دور حقيقي.
 */
interface DialogueHistorySource {
    fun turns(): List<DialogueTurn>
    fun lastTurn(): DialogueTurn? = turns().lastOrNull()
    fun lastUserQuery(): String? = lastTurn()?.user
    fun lastResponseText(): String? = lastTurn()?.response
    fun lastIntent(): String? = lastTurn()?.intent
    fun turnCount(): Int = turns().size
}

/** إسقاط للقراءة فقط فوق حالة الحوار. لا يخزّن شيئًا ولا يعدّل الحالة. */
class DialogueStateHistory(private val state: DialogueState) : DialogueHistorySource {
    override fun turns(): List<DialogueTurn> = state.turns
    override fun lastTurn(): DialogueTurn? = state.turns.lastOrNull()
    override fun lastUserQuery(): String? = state.turns.lastOrNull()?.user
    override fun lastResponseText(): String? = state.turns.lastOrNull()?.response
    override fun lastIntent(): String? = state.turns.lastOrNull()?.intent
    override fun turnCount(): Int = state.turns.size
}

/**
 * مفتاح الرجوع الآمن (بند المعمارية المتبقي في المرحلة 4).
 *
 * - `PROJECTED` (الافتراضي): كل الواجهات القديمة تقرأ من `DialogueState` فقط.
 * - `MIRRORED`: تُعاد النسخ المحلية القديمة (نسخة لكل مستهلك) لأغراض التشخيص أو
 *   الرجوع عند اكتشاف خلل في الإسقاط. الاختبارات تثبت تكافؤ المسارين.
 *
 * لا يجوز ضبط `MIRRORED` كإعداد مستخدم عادي؛ هو مفتاح تشخيصي لا يغيّر المحتوى
 * بل يغيّر مصدر قراءة السجل.
 */
enum class LegacyContextMode { PROJECTED, MIRRORED }

object LegacyContextPolicy {
    @Volatile
    var mode: LegacyContextMode = LegacyContextMode.PROJECTED
        private set

    /** يُستعمل في الاختبار فقط، ويُعاد إلى `PROJECTED` بعد كل حالة. */
    fun setModeForTesting(mode: LegacyContextMode) {
        this.mode = mode
    }

    /**
     * يعيد مصدر الحالة حين يكون الوضع `PROJECTED`، و`null` حين يجب أن يعود
     * المستهلك القديم إلى نسخته المحلية (`MIRRORED`).
     */
    fun projectedSource(source: DialogueHistorySource): DialogueHistorySource? =
        if (mode == LegacyContextMode.PROJECTED) source else null
}

/**
 * فحص تكافؤ بين نسخة محلية قديمة (مرآة) وبين المصدر الوحيد.
 * يُستعمل في الاختبارات وليس في مسار الرد؛ لا يعيد كتابة أي نص.
 */
object DialogueContextEquivalence {
    data class MirroredTurn(val user: String, val response: String, val intent: String)

    fun mirrorOf(history: DialogueHistorySource): List<MirroredTurn> =
        history.turns().map { MirroredTurn(it.user, it.response, it.intent) }

    /** قائمة فروق مقروءة؛ القائمة الفارغة تعني تكافؤًا تامًا. */
    fun drift(history: DialogueHistorySource, mirror: List<MirroredTurn>): List<String> {
        val projected = mirrorOf(history)
        val problems = mutableListOf<String>()
        if (projected.size != mirror.size) {
            problems += "عدد الدورات مختلف: ${projected.size} مقابل ${mirror.size}"
        }
        val common = minOf(projected.size, mirror.size)
        for (i in 0 until common) {
            if (projected[i] != mirror[i]) problems += "الدورة ${i + 1} لا تطابق السجل المحلي"
        }
        return problems
    }

    /**
     * هل يمكن لهذا النص أن يدّعي دورًا غير موجود في المصدر؟
     * يُستخدم للتحقق من أن الردود لا تستشهد بسجل مفقود.
     */
    fun claimHasNoTurn(history: DialogueHistorySource, claim: String): Boolean {
        if (claim.isBlank()) return false
        val normalizedClaim = claim.replace(Regex("\\s+"), " ")
        val firstWords = normalizedClaim.split(' ').take(4).joinToString(" ")
        return history.turns().none { it.user.contains(firstWords) || normalizedClaim.contains(it.user) }
    }
}
