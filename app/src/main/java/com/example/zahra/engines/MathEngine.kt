package com.example.zahra.engines

import com.example.zahra.math.MathDivisionByZeroException
import com.example.zahra.math.MathDomainException
import com.example.zahra.math.MathExpressionEngine
import com.example.zahra.math.MathFormatting
import com.example.zahra.math.MathOverflowException
import com.example.zahra.math.MathSyntaxException
import com.example.zahra.math.UnitConversionResult
import com.example.zahra.math.UnitRequest
import com.example.zahra.math.UnitRequestParseResult
import com.example.zahra.math.UnitRequestParser
import com.example.zahra.math.UnitSystem
import com.example.zahra.math.WordProblemResult
import com.example.zahra.math.WordProblems
import com.example.zahra.model.OutcomeState

data class MathResult(
  val formattedText: String,
  val source: String,
  val confidence: Double,
  val outcomeState: OutcomeState = OutcomeState.SUPPORTED,
  val isSuccess: Boolean = true,
  val errorMessage: String? = null,
  val numericResult: Double? = null,
  /** شجرة العمليات (تمثيل بادئي) — للمراجعة والتصحيح، لا تُعرض للمستخدم افتراضيًا. */
  val operationTree: String? = null
)

/**
 * واجهة الحساب العامة — **مُحافظة على التوافق** مع بقية التطبيق.
 *
 * المرحلة 2 استبدلت المحلّل القديم (tokenizer + RPN محدود) بمحرّك كامل في
 * [com.example.zahra.math]، و`when` الوحدات بعشرة فروع بـ [UnitSystem]. هذه الفئة
 * تحوّل نتائج المحرّك الجديد إلى نفس `MathResult` الذي تعرفه بقية الملفات، حتى لا
 * تنكسر أي واجهة قائمة (`ZahraRouter`, `PlanExecutor`, `DiagnosticsEngine`, الاختبارات).
 */
object MathEngine {

  private const val SOURCE_EXPRESSION = "محرك الحساب الرياضي الدقيق (Zahra Math Engine)"
  private const val SOURCE_UNITS = "محول الوحدات الفيزيائية"
  private const val SOURCE_WORD_PROBLEM = "محرّك المسائل الكلامية الحتمي (قوالب مُختبَرة)"

  fun evaluate(rawExpression: String): MathResult {
    val trimmed = rawExpression.trim()
    if (trimmed.isBlank()) {
      return MathResult(
        formattedText = "يرجى كتابة تعبير رياضي صالح للحساب (مثال: 25 * 4 + 10 أو جذر 144).",
        source = SOURCE_EXPRESSION,
        confidence = 0.5,
        outcomeState = OutcomeState.AMBIGUOUS,
        isSuccess = false,
        errorMessage = "التعبير الرياضي فارغ"
      )
    }

    val normalized = normalizeArabicMath(trimmed)

    return try {
      val evaluation = MathExpressionEngine.evaluate(normalized)
      MathResult(
        formattedText = "الناتج: $trimmed = ${MathFormatting.formatNumber(evaluation.value)}",
        source = SOURCE_EXPRESSION,
        confidence = 1.0,
        outcomeState = OutcomeState.SUPPORTED,
        isSuccess = true,
        numericResult = evaluation.value,
        operationTree = evaluation.operationTree
      )
    } catch (e: MathDivisionByZeroException) {
      errorResult(e.message ?: "خطأ رياضي: القسمة على صفر غير معرفة.", OutcomeState.EXECUTION_ERROR, 1.0)
    } catch (e: MathDomainException) {
      errorResult(e.message ?: "قيمة خارج نطاق الدالة الرياضية.", OutcomeState.EXECUTION_ERROR, 1.0)
    } catch (e: MathOverflowException) {
      errorResult(e.message ?: "قيمة غير معرفة أو طفحان رقمي.", OutcomeState.EXECUTION_ERROR, 1.0)
    } catch (e: MathSyntaxException) {
      MathResult(
        formattedText = "تعبير رياضي غير صالح: ${e.message}",
        source = SOURCE_EXPRESSION,
        confidence = 0.8,
        outcomeState = OutcomeState.AMBIGUOUS,
        isSuccess = false,
        errorMessage = e.message
      )
    } catch (e: IllegalArgumentException) {
      MathResult(
        formattedText = "تعبير رياضي غير صالح: ${e.message}",
        source = SOURCE_EXPRESSION,
        confidence = 0.8,
        outcomeState = OutcomeState.AMBIGUOUS,
        isSuccess = false,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      MathResult(
        formattedText = "تعذر إجراء العملية الحسابية: تعبير غير مكتمل أو غير مدعوم.",
        source = SOURCE_EXPRESSION,
        confidence = 0.4,
        outcomeState = OutcomeState.EXECUTION_ERROR,
        isSuccess = false,
        errorMessage = e.message
      )
    }
  }

  private fun errorResult(message: String, state: OutcomeState, confidence: Double): MathResult =
    MathResult(
      formattedText = message,
      source = SOURCE_EXPRESSION,
      confidence = confidence,
      outcomeState = state,
      isSuccess = false,
      errorMessage = message
    )

  /** التحويل بالأسماء (يُحافظ على التوقيع القديم المستخدم في التطبيق والاختبارات). */
  fun convertUnit(value: Double, from: String, to: String): MathResult =
    toMathResult(UnitSystem.convert(value, from, to))

  /** تحويل من طلب مُحلّل مسبقًا (يفيد المسار الفعلي للراوتر). */
  fun convertUnit(request: UnitRequest): MathResult = toMathResult(UnitSystem.convert(request))

  /**
   * يحاول حل طلب تحويل من نص حر. يعيد `null` إذا لم تكن الجملة تحويل وحدات،
   * و`MathResult` صريحًا (نجاحًا أو رفضًا) إذا كانت كذلك.
   */
  fun convertFromText(rawInput: String): MathResult? = when (val parsed = UnitRequestParser.parse(rawInput)) {
    is UnitRequestParseResult.Parsed -> convertUnit(parsed.request)
    is UnitRequestParseResult.UnknownUnit -> {
      val baseMessage = UnitSystem.unsupportedMessage(parsed.token)
      toMathResult(UnitConversionResult.Unsupported(baseMessage, parsed.token))
    }
    is UnitRequestParseResult.MissingTarget -> MathResult(
      formattedText = "عايز أحوّل من ${parsed.from.arabicName} إلى إيه؟ حدّد الوحدة الهدف " +
        "(مثال: «حوّل 10 ${parsed.from.arabicName} إلى ميل»).",
      source = SOURCE_UNITS,
      confidence = 0.5,
      outcomeState = OutcomeState.AMBIGUOUS,
      isSuccess = false,
      errorMessage = "الوحدة الهدف غير محددة"
    )
    UnitRequestParseResult.NoUnit -> null
  }

  /** يحاول حل مسألة كلامية؛ `null` تعني أن الجملة ليست من قوالب المسائل المعروفة. */
  fun solveWordProblem(rawInput: String): MathResult? =
    when (val result = WordProblems.solve(rawInput)) {
      is WordProblemResult.Solved -> MathResult(
        formattedText = "الناتج: ${MathFormatting.formatNumber(result.value)}" +
          (result.unitLabel?.let { " $it" } ?: "") +
          "\n• التعبير: ${result.expression}" +
          "\n• ${result.explanation}",
        source = SOURCE_WORD_PROBLEM,
        confidence = result.confidence,
        outcomeState = OutcomeState.SUPPORTED,
        isSuccess = true,
        numericResult = result.value,
        operationTree = result.templateId
      )
      is WordProblemResult.NotApplicable -> null
    }

  private fun toMathResult(result: UnitConversionResult): MathResult = when (result) {
    is UnitConversionResult.Success -> MathResult(
      formattedText = result.text,
      source = result.sourceName,
      confidence = result.confidence,
      outcomeState = OutcomeState.SUPPORTED,
      isSuccess = true,
      numericResult = result.value
    )
    is UnitConversionResult.Unsupported -> MathResult(
      formattedText = result.message,
      source = SOURCE_UNITS,
      confidence = 0.3,
      outcomeState = OutcomeState.UNSUPPORTED,
      isSuccess = false,
      errorMessage = result.message
    )
    is UnitConversionResult.CrossCategory -> MathResult(
      formattedText = result.message,
      source = SOURCE_UNITS,
      confidence = 0.6,
      outcomeState = OutcomeState.UNSUPPORTED,
      isSuccess = false,
      errorMessage = result.message
    )
    is UnitConversionResult.RequiresLiveSource -> MathResult(
      formattedText = result.message,
      source = "سياسة المصادر الحيّة (لا أسعار عملات مخزّنة)",
      confidence = 0.9,
      outcomeState = OutcomeState.UNSUPPORTED,
      isSuccess = false,
      errorMessage = result.message
    )
  }

  /**
   * تطبيع الكلمات العربية إلى معاملات رياضية — **سلوك تاريخي محفوظ كما هو**
   * (بما فيه استبدال «في» بضرب) لأن عدة واجهات تعتمد عليه. أي تغيير في الدلالات
   * يحتاج قرارًا مكتوبًا واختبارات مستقلة.
   */
  private fun normalizeArabicMath(raw: String): String {
    var s = raw
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    for (i in 0..9) {
      s = s.replace(arabicDigits[i], ('0' + i))
    }

    s = s.replace("×", "*")
      .replace("÷", "/")
      .replace("**", "^")
      .replace("√", "sqrt ")
      .replace("جذر", "sqrt ")
      .replace("زائد", "+")
      .replace("ناقص", "-")
      .replace("ضرب", "*")
      .replace("في", "*")
      .replace("على", "/")
      .replace("تقسيم", "/")
      .replace("مرفوع للقوة", "^")
      .replace("مرفوع للاس", "^")
      .replace("مرفوع للأس", "^")
      .replace("أس", "^")
      .replace("قوة", "^")

    return s.trim()
  }

  /** تنسيق عام يُستخدم في بقية الأدوات (متوافق مع السلوك القديم). */
  fun formatNumber(value: Double): String = MathFormatting.formatNumber(value)
}
