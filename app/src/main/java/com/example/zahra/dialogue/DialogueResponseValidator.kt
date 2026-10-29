package com.example.zahra.dialogue

/** Conservative final checks; these rules do not prove medical or religious correctness. */
object DialogueResponseValidator {
    fun problem(text: String, hasGroundedSource: Boolean): String? {
        if (MemoryClaimGuard.containsUnverifiableClaim(text)) return "ذاكرة بلا مرجع قابل للتحقق"
        val sentences = text.split(Regex("[.!؟?\\n]+"))
            .map { it.trim() }.filter { it.length >= 20 }
        if (sentences.groupingBy { it }.eachCount().values.any { it >= 3 }) return "تكرار الرد"
        if (text.contains("هذا حلال وحرام") || text.contains("هذا صحيح وغير صحيح")) return "تناقض صريح"
        if (!hasGroundedSource && Regex("(?:سيشفيك|يشفيك نهائيا|علاج مؤكد|(?:يضمن|مضمون).{0,50}(?:الشفاء|المرض|السرطان))").containsMatchIn(text))
            return "وعد طبي غير موثق"
        if (!hasGroundedSource && Regex("(?:حكم الله قطعا|فتوى مؤكدة|قال الله تعالى|قال النبي)").containsMatchIn(text))
            return "ادعاء ديني بلا مصدر"
        return null
    }
}
