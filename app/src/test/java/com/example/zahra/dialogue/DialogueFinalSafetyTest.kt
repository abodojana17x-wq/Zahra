package com.example.zahra.dialogue

import org.junit.Assert.*
import org.junit.Test

/**
 * المرحلة 4 — توسيع التحقق النهائي وسلامة المعنى بعد طبقة الأسلوب (D8).
 * هذه فحوص نمطية محافظة، وليست إثباتًا دلاليًا شاملًا.
 */
class DialogueFinalSafetyTest {

    @Test fun styleNeverChangesNumbersLinksOrAbstention() {
        val cases = listOf(
            "النتيجة 12.50؛ المصدر https://example.com/a?x=9" to
                "✨ النتيجة 12.50؛ المصدر https://example.com/a?x=9",
            "لا أستطيع الإجابة دون مصدر موثوق." to
                "لا أستطيع الإجابة دون مصدر موثوق. 🙂",
            "السعر ١٥٥ جنيهًا عبر https://example.org/p" to
                "تمام: السعر ١٥٥ جنيهًا عبر https://example.org/p"
        )
        cases.forEach { (core, delivered) ->
            assertNull(DialogueResponseValidator.integrityProblem(core, delivered))
        }
    }

    @Test fun integrityCatchesChangedNumberLinkOrRemovedAbstention() {
        val core = "النتيجة 12.50 https://example.com/a"
        assertNotNull(DialogueResponseValidator.integrityProblem(core, "النتيجة 12.5 https://example.com/a"))
        assertNotNull(DialogueResponseValidator.integrityProblem(core, "النتيجة 12.50 https://example.com/b"))
        assertNotNull(DialogueResponseValidator.integrityProblem(core, "النتيجة بدون رابط"))
        assertNotNull(DialogueResponseValidator.integrityProblem(
            "لا أملك مصدرًا موثوقًا لهذا السعر.", "السعر أكيد 100 جنيه."))
        // إضافة كتلة افتتاحية لا تُعد انتهاكًا.
        assertNull(DialogueResponseValidator.integrityProblem(core, "أهلاً!\n\n$core"))
    }

    @Test fun unsourcedSuretyAndPercentagesAreRejected() {
        assertNotNull(DialogueResponseValidator.problem("أضمن لك النتيجة", false))
        assertNotNull(DialogueResponseValidator.problem("نسبة الشفاء 90% دائمًا", false))
        assertNull(DialogueResponseValidator.problem("المصدر يذكر نسبة الشفاء 90%", true))
        assertNull(DialogueResponseValidator.problem("المجموع 90% من القيمة المحسوبة", false))
    }

    @Test fun contradictionsAreRejectedOnlyWhenExplicit() {
        assertNotNull(DialogueResponseValidator.problem("هذا موجود ولا يوجد في نفس الوقت", false))
        assertNull(DialogueResponseValidator.problem("هذا موجود في النسخة الأولى وغير متوفر في الثانية", false))
    }

    @Test fun unexplainedNumbersHelpDistinguishToolOutputFromFabrication() {
        val tool = "النتيجة 12.50 (المصدر: حاسبة محلية)"
        assertTrue(DialogueResponseValidator.unexplainedNumbers(tool, "احسب 5 * 2.5", listOf("12.50")).isEmpty())
        assertFalse(DialogueResponseValidator.unexplainedNumbers("النتيجة 99.9", "احسب 5 * 2.5", listOf("12.50")).isEmpty())
        // الأرقام العربية-الهندية تُطبَّع قبل المقارنة.
        assertTrue(DialogueResponseValidator.unexplainedNumbers("المجموع ١٥", "اجمع 10 و5", listOf("15")).isEmpty())
    }

    @Test fun placeholderOnlyResponsesAreRejected() {
        assertNotNull(DialogueResponseValidator.problem("   ", false))
        assertNotNull(DialogueResponseValidator.problem("حسناً. حسناً. حسناً. حسناً تمام جدًا الآن", false))
    }

    @Test fun memoryClaimsRemainRejectedEvenWithARealTurn() {
        assertNotNull(DialogueResponseValidator.problem("كما ذكرت سابقًا أن اسمك علي", true))
        assertNull(DialogueResponseValidator.problem("الاسم المحفوظ عندك هو علي", true))
    }
}
