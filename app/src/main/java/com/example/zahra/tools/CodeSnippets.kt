package com.example.zahra.tools

/**
 * القرار D3: في هذه المرحلة **لا تنفيذ كود**.
 *
 * ما يُقدَّم: قصاصة كود + شرح سطر بسطر + ناتج متوقع (مُدقَّق يدويًا).
 * ما لا يُقدَّم: تشغيل كود المستخدم (لا WebView عشوائي، لا Chaquopy).
 * واجهة [CodeRunner] موجودة لتفعيل التنفيذ لاحقًا بقرار مالك، وتُعيد اليوم `Unavailable`.
 */

data class CodeLineExplanation(val lineNumber: Int, val code: String, val explanationAr: String)

data class CodeSnippet(
  val id: String,
  val language: String,
  val title: String,
  val code: String,
  val expectedOutput: String?,
  val lines: List<CodeLineExplanation>,
  val notes: List<String> = emptyList()
)

sealed class CodeRunResult {
  data class Unavailable(val message: String) : CodeRunResult()
  data class Executed(val stdout: String) : CodeRunResult()
}

fun interface CodeRunner {
  fun run(language: String, code: String): CodeRunResult
}

/** التنفيذ غير مفعّل — أي طلب تنفيذ يحصل على رفض صريح. */
object DisabledCodeRunner : CodeRunner {
  override fun run(language: String, code: String): CodeRunResult = CodeRunResult.Unavailable(
    "تنفيذ الكود غير مفعّل في زهرة (القرار D3). أقدر أشرح القصاصة سطرًا بسطر وأعرض الناتج المتوقع، " +
      "لكن مش هأشغّل كودًا في جهازك ولا هأدّعي إني شغّلته."
  )
}

object CodeSnippets {

  val catalog: List<CodeSnippet> = listOf(
    CodeSnippet(
      id = "python-loop-sum",
      language = "python",
      title = "جمع الأرقام من 1 إلى 5",
      code = """
        total = 0
        for i in range(1, 6):
            total = total + i
        print(total)
      """.trimIndent(),
      expectedOutput = "15",
      lines = listOf(
        CodeLineExplanation(1, "total = 0", "نبدأ بمتغير المجموع بقيمة صفر."),
        CodeLineExplanation(2, "for i in range(1, 6):", "حلقة تمر على 1،2،3،4،5 (النهاية 6 غير مشمولة)."),
        CodeLineExplanation(3, "    total = total + i", "نضيف كل رقم إلى المجموع، مع إزاحة سطر واحد داخل الحلقة."),
        CodeLineExplanation(4, "print(total)", "نطبع الناتج النهائي: 15.")
      )
    ),
    CodeSnippet(
      id = "kotlin-list-average",
      language = "kotlin",
      title = "متوسط قائمة أرقام في Kotlin",
      code = """
        val numbers = listOf(4, 8, 10)
        val average = numbers.average()
        println(average)
      """.trimIndent(),
      expectedOutput = "7.333333333333333",
      lines = listOf(
        CodeLineExplanation(1, "val numbers = listOf(4, 8, 10)", "قائمة ثابتة (val) بثلاث قيم."),
        CodeLineExplanation(2, "val average = numbers.average()", "average() تجمع وتقسم على العدد، وتُعيد Double."),
        CodeLineExplanation(3, "println(average)", "الطباعة تُظهر الجزء العشري كاملًا حسب تمثيل Double.")
      ),
      notes = listOf("لو عايز تنسيق منزلتين استخدم String.format(\"%.2f\", average).")
    ),
    CodeSnippet(
      id = "javascript-fetch-note",
      language = "javascript",
      title = "قراءة قيمة من كائن JSON",
      code = """
        const response = { city: "Cairo", temp: 31 };
        console.log(response.city, response.temp);
      """.trimIndent(),
      expectedOutput = "Cairo 31",
      lines = listOf(
        CodeLineExplanation(1, "const response = { ... }", "كائن ثابت يحاكي استجابة JSON."),
        CodeLineExplanation(2, "console.log(...)", "طباعة خاصيتين مفصولتين بمسافة.")
      ),
      notes = listOf("هذه القصاصة توضيحية فقط ولا تُنفَّذ داخل زهرة.")
    )
  )

  /** يبحث عن قصاصة مناسبة بحسب كلمات الطلب؛ يعيد `null` بدل اختراع كود. */
  fun find(request: String): CodeSnippet? {
    val text = request.lowercase()
    val language = when {
      text.contains("python") || text.contains("بايثون") -> "python"
      text.contains("kotlin") || text.contains("كوتلن") -> "kotlin"
      text.contains("javascript") || text.contains("جافا سكريبت") || text.contains("js") -> "javascript"
      else -> null
    }
    val wantsAverage = text.contains("متوسط") || text.contains("average")
    val wantsLoop = text.contains("حلقه") || text.contains("loop") || text.contains("for")
    val wantsJson = text.contains("json")

    return catalog.firstOrNull { snippet ->
      (language == null || snippet.language == language) &&
        when {
          wantsAverage -> snippet.id == "kotlin-list-average"
          wantsLoop -> snippet.id == "python-loop-sum"
          wantsJson -> snippet.id == "javascript-fetch-note"
          else -> language != null && snippet.language == language
        }
    }
  }
}
