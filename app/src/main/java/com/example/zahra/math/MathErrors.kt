package com.example.zahra.math

/**
 * أخطاء محرّك التعبيرات الرياضي.
 *
 * كل خطأ يحمل رسالة عربية موجّهة للمستخدم. الفصل بين الأنواع ضروري لأن طبقة
 * `MathEngine` تحوّل كل نوع إلى `OutcomeState` مختلف:
 * - [MathSyntaxException] ⇒ `AMBIGUOUS` (التعبير ناقص/غير مفهوم، لا يصلح كخطأ تنفيذ).
 * - [MathDivisionByZeroException] / [MathDomainException] / [MathOverflowException] ⇒ `EXECUTION_ERROR`.
 */
sealed class MathExpressionException(message: String) : Exception(message)

/** تعبير غير صالح لغويًا (رمز غير مدعوم، قوس غير مغلق، معرّف مجهول، معامل ناقص...). */
class MathSyntaxException(message: String) : MathExpressionException(message)

/** قسمة أو باقي قسمة على صفر. */
class MathDivisionByZeroException(message: String) : MathExpressionException(message)

/** قيمة خارج نطاق الدالة (جذر سالب، لوغاريتم صفر/سالب، مضروب عدد غير صحيح...). */
class MathDomainException(message: String) : MathExpressionException(message)

/** نتيجة غير منتهية أو تجاوز نطاق `Double`. */
class MathOverflowException(message: String) : MathExpressionException(message)
