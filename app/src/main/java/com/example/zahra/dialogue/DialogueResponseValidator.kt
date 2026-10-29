package com.example.zahra.dialogue

/**
 * فحوص نهائية محافظة. لا تُثبت صحة طبية أو دينية ولا تفهم المعنى بالكامل؛
 * هي حاجز نمطي يكشف أنماطًا معروفة فقط، وأي غياب لتنبيه ليس شهادة سلامة.
 */
object DialogueResponseValidator {
    private val medicalPromise = Regex(
        "(?:سيشفيك|يشفيك نهائيا|علاج مؤكد|(?:يضمن|مضمون|أضمن|اضمن)\\s*(?:\\d+%|١٠٠|100)?\\s*(?:الشفاء|المرض|السرطان|النتيجه|النتيجة))"
    )
    private val unsourcedReligiousClaim = Regex(
        "(?:حكم الله قطعا|فتوى مؤكدة|قال الله تعالى|قال النبي|النبي قال|الحديث الصحيح يثبت)"
    )
    private val unconditionalSurety = Regex(
        "(?:مضمون\\s*(?:\\d+%|١٠٠|100)\\s*%|نتائج?\\s*مضمونة|بلا أي احتمال|مستحيل أن يخطئ|أضمن لك)"
    )
    private val contradictionPairs = listOf(
        "هذا حلال وحرام", "هذا صحيح وغير صحيح", "متاح وغير متاح",
        "موجود ولا يوجد", "أستطيع ولا أستطيع", "مؤكد وغير مؤكد"
    )

    fun problem(text: String, hasGroundedSource: Boolean): String? {
        if (MemoryClaimGuard.containsUnverifiableClaim(text)) return "ذاكرة بلا مرجع قابل للتحقق"
        if (text.isBlank()) return "رد فارغ"
        val sentences = text.split(Regex("[.!؟?\\n]+"))
            .map { it.trim() }.filter { it.length >= 4 }
        if (sentences.groupingBy { it }.eachCount().values.any { it >= 3 }) return "تكرار الرد"
        if (contradictionPairs.any { text.contains(it) }) return "تناقض صريح"
        if (!hasGroundedSource && medicalPromise.containsMatchIn(text)) return "وعد طبي غير موثق"
        if (!hasGroundedSource && unsourcedReligiousClaim.containsMatchIn(text)) return "ادعاء ديني بلا مصدر"
        if (!hasGroundedSource && unconditionalSurety.containsMatchIn(text)) return "ضمان غير مسند"
        // نسبة مئوية بلا مصدر في سياق صحي/مالي ادعاء غير مسند.
        if (!hasGroundedSource && Regex("(?:نسبة الشفاء|احتمال النجاح|العائد المضمون|نسبة النجاح)\\s*\\d+\\s*%").containsMatchIn(text))
            return "رقم غير مسند في سياق ادعاء"
        return null
    }

    private val numberPattern = Regex("[0-9٠-٩][0-9٠-٩.,٫٬]*")
    private val linkPattern = Regex("https?://[^\\s،]+")

    private fun asciiDigits(value: String): String = buildString {
        value.forEach { ch ->
            append(if (ch in '٠'..'٩') ('0' + (ch - '٠')) else ch)
        }
    }

    fun numbers(text: String): Set<String> =
        numberPattern.findAll(text).map { asciiDigits(it.value).trim('.', ',') }.filter { it.isNotBlank() }.toSet()

    fun links(text: String): List<String> = linkPattern.findAll(text).map { it.value }.toList()

    /**
     * فحص D8: الأسلوب والشخصية يُضافان بعد الجوهر ولا يغيّران رقمًا أو رابطًا
     * أو يحوّلان امتناعًا إلى وعد.
     */
    fun integrityProblem(core: String, delivered: String): String? {
        if (core.isBlank()) return null
        if (links(core) != links(delivered)) return "تغيّر رابط في الرد"
        val coreNumbers = numbers(core)
        if (!numbers(delivered).containsAll(coreNumbers)) return "تغيّر رقم في الرد"
        val abstainMarkers = listOf("لا أستطيع", "لا أملك", "دون مصدر", "أمتنع", "لم أستطع", "لا أعرف")
        val coreAbstains = abstainMarkers.any { core.contains(it) }
        val deliveredAbstains = abstainMarkers.any { delivered.contains(it) }
        if (coreAbstains && !deliveredAbstains) return "حُذف نص الامتناع"
        return null
    }

    /**
     * أرقام في الرد لا تظهر في الطلب ولا في وقائع مؤصلة. تُستخدم في الاختبارات
     * وفي التشخيص، وليست بوابة عامة لأن كثيرًا من الأرقام المشروعة تأتي من قواعد
     * تحويل (وحدات/تواريخ) لا من نص واحد.
     */
    fun unexplainedNumbers(text: String, request: String, groundedValues: Collection<String>): Set<String> {
        val allowed = numbers(request) + groundedValues.flatMap { numbers(it) } + groundedValues
        return numbers(text) - allowed
    }
}
