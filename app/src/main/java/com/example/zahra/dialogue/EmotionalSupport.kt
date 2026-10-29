package com.example.zahra.dialogue

/** Narrow, contextual responses; avoids diagnosing or claiming human feelings. */
object EmotionalSupport {
    fun crisisAnswer(input: String): String? {
        val t = input.replace('أ', 'ا').replace('إ', 'ا').replace('ؤ', 'و').replace('ى', 'ي')
        if (!Regex("(?:انتحر|اقتل نفسي|اوذي نفسي|اذي نفسي|الانتحار)").containsMatchIn(t)) return null
        if (!Regex("(?:انا|نفسي|عايز|عاوزه|عاوز|اريد|افكر|هقتل|سوف|حاولت|سأ)").containsMatchIn(t)) return null
        return "أنا آسفة إنك بتمر بده، وسلامتك مهمة. لو ممكن تؤذي نفسك الآن، ابعد عن أي وسيلة مؤذية وتواصل فورًا مع الطوارئ المحلية أو اذهب لأقرب مستشفى. كلم شخصًا تثق به واطلب منه يفضل معك الآن."
    }

    fun answer(input: String): String? {
        val t = input.trim().replace('أ', 'ا').replace('إ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
        if (t.contains("/s") || t.contains("سخريه")) return null
        if (t.length > 100 || Regex("(فكرني|ذكرني|نبهني|احذف|احسب|احفظ|حكم|دواء|اقتل|ايوذي)").containsMatchIn(t)) return null
        if (Regex("(انتحر|انتحار|اؤذي نفسي|اوذي نفسي|اقتل نفسي)").containsMatchIn(t)) return null
        if (Regex("(مش|لا|لست)\\s+(?:حاسس\\s+(?:اني\\s+)?)?(وحيد|وحيده)").containsMatchIn(t)) return null
        return when {
            Regex("مش قادر(?:ه)? (?:اذاكر|اذكر)").containsMatchIn(t) -> "المذاكرة صعبة دلوقتي، وده مفهوم. جرّب تفتح جزءًا صغيرًا وتذاكر خمس دقائق، ثم خذ استراحة قصيرة."
            Regex("(?:حاسس|حسيت|اشعر).*(?:وحيد|لوحدي)").containsMatchIn(t) -> "الإحساس بالوحدة متعب. ممكن تقلل الوحدة بخطوة صغيرة: ابعت رسالة لشخص تثق فيه وقل له إنك محتاج تتكلم."
            Regex("(?:اتخانقت|تخانقت).*(?:صاحبي|صديقي|صاحبتي)").containsMatchIn(t) -> "الخناقة مع صاحبك ممكن تكون صعبة. لما تهدأ، جرّب تشرح لصاحبك شعورك وتسمع وجهة نظره."
            Regex("(?:انا )?فرحان(?:ه)?").containsMatchIn(t) && !t.contains("مش فرحان") -> "جميل إنك فرحان النهارده! استمتع باللحظة."
            Regex("(?:انا )?زهقان(?:ه)?").containsMatchIn(t) && !t.contains("مش زهقان") -> "الزهق ممكن يخف بتغيير بسيط: جرّب تمشي شوية أو تعمل حاجة بتحبها."
            Regex("^(?:هل |انت |انتي )?(?:تحبني|بتحبني)[؟?]?$ ".trim()).matches(t) -> "أنا برنامج وما عنديش مشاعر حب بشرية، لكن أقدر أسمعك وأساعدك باهتمام."
            else -> null
        }
    }
}
