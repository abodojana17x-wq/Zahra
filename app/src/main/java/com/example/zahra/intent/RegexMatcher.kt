package com.example.zahra.intent

data class RegexMatch(
  val intent: String,
  val confidence: Double,
  val extractedCommand: String? = null
)

object RegexMatcher {
  private data class PatternRule(
    val intent: String,
    val regex: Regex,
    val confidence: Double,
    val extractGroup: Int? = null
  )

  private val RULES = listOf(
    // 1. Math calculation
    PatternRule("calculate", Regex("^(?:احسب|كم\\s+يساوي|ناتج|calc(?:ulate)?)\\s+([0-9+\\-*/^().\\s%]+)", RegexOption.IGNORE_CASE), 0.98, 1),
    // 2. Unit conversion
    PatternRule("convert_unit", Regex("^(?:حول|تحويل|convert)\\s+(.+?)\\s+(?:الى|إلى|الي|to)\\s+(.+)", RegexOption.IGNORE_CASE), 0.95),
    // 3. Translation
    PatternRule("translate", Regex("^(?:ترجم(?:لي)?|ما\\s+ترجمة|translate)\\s+(.+?)(?:\\s+(?:الى|إلى|الي|to)\\s+(.+))?$", RegexOption.IGNORE_CASE), 0.95, 1),
    // 4. Summarization
    PatternRule("summarize", Regex("^(?:لخص(?:لي)?|تلخيص|أعطني\\s+ملخص|summarize)\\s+(.+)", RegexOption.DOT_MATCHES_ALL), 0.95, 1),
    // 5. Note creation
    PatternRule("remember_note", Regex("^(?:سجل\\s+ملاحظة|اكتب\\s+ملاحظة|احفظ\\s+ملاحظة|تذكر\\s+ان|تذكر\\s+أن|note:?)\\s*(.+)", RegexOption.DOT_MATCHES_ALL), 0.96, 1),
    // 6. Task creation
    PatternRule("create_task", Regex("^(?:اضف\\s+مهمة|أضف\\s+مهمة|انشئ\\s+مهمة|سجل\\s+مهمة|مهمة\\s+جديدة|task:?)\\s*(.+)", RegexOption.DOT_MATCHES_ALL), 0.96, 1),
    // 7. Prayer times
    PatternRule("ask_prayer_time", Regex("^(?:متى\\s+صلاة|مواقيت\\s+الصلاة|موعد\\s+اذان|موعد\\s+أذان|كم\\s+بقي\\s+على\\s+صلاة|prayer\\s+times?)", RegexOption.IGNORE_CASE), 0.96),
    // 8. Qibla
    PatternRule("ask_religion", Regex("^(?:اين\\s+القبلة|أين\\s+القبلة|اتجاه\\s+القبلة|qibla)", RegexOption.IGNORE_CASE), 0.95),
    // 9. Quran
    PatternRule("ask_quran", Regex("^(?:سورة|اية|آية|تفسير\\s+اية|تفسير\\s+سورة|قران|قرآن)\\s+(.+)", RegexOption.IGNORE_CASE), 0.93, 1),
    // 10. Hadith
    PatternRule("ask_hadith", Regex("^(?:حديث|هل\\s+هذا\\s+حديث|تخريج\\s+حديث|قال\\s+رسول\\s+الله)\\s*(.*)", RegexOption.IGNORE_CASE), 0.92),
    // 11. Greetings
    PatternRule("greet", Regex("^(?:السلام\\s+عليكم|مرحبا|أهلا|اهلا|صباح\\s+الخير|مساء\\s+الخير|hi|hello|bonjour)\\b", RegexOption.IGNORE_CASE), 0.95),
    // 12. Farewells
    PatternRule("farewell", Regex("^(?:مع\\s+السلامة|إلى\\s+اللقاء|الى\\s+اللقاء|وداعا|باي|bye|goodbye|au\\s+revoir)\\b", RegexOption.IGNORE_CASE), 0.95),
    // 13. Help
    PatternRule("help", Regex("^(?:ساعدني|مساعدة|ماذا\\s+تستطيعين|كيف\\s+استخدمك|دليل|help)\\b", RegexOption.IGNORE_CASE), 0.95),
    // 14. Definitions / General
    PatternRule("define_word", Regex("^(?:ما\\s+هو|ما\\s+هي|ما\\s+معنى|عرف(?:لي)?|what\\s+is)\\s+(.+)", RegexOption.IGNORE_CASE), 0.92, 1),
    // 15. Knowledge Search & Curated Facts
    PatternRule("query_knowledge", Regex("^(?:ابحث\\s+في\\s+المعرف[ةه]|معلومات\\s+عن|ما\\s+هي\\s+سرع[ةه]|ما\\s+درج[ةه]|درج[ةه]\\s+غليان|من\\s+هو|من\\s+هي|اعلى\\s+قم[ةه]|أعلى\\s+قم[ةه]|كم\\s+ساع[ةه]\\s+نوم|كم\\s+ساعات\\s+النوم|ما\\s+هو\\s+تشفير|معيار\\s+التشفير|معلومات\\s+عن\\s+الخوارزمي)\\s*(.*)", RegexOption.IGNORE_CASE), 0.95, 1),
    // 16. Local Image Generation (Procedural canvas, quotes, pixel art)
    PatternRule("generate_image", Regex(".*(?:انش[ئيا]|ارسم|صمم|ولد|اعمل|سوي)?\\s*(?:لي)?\\s*(?:صوره|صورة|لوحه|لوحة|خلفيه|خلفية|رسمه|رسمة|بطاقه|بطاقة|بكسل|pixel|draw|paint|generate\\s*image).*|^(?:انش[ئيا]|ارسم|صمم|ولد)\\b.*", RegexOption.IGNORE_CASE), 0.98, 1),
    // 16. Schedule and Alarms (Autonomous Agent Reminders)
    PatternRule("create_schedule", Regex(".*(?:جدول|فكرني|ذكرني|نبهني|ابعتلي|تذكير|منبه|schedule|remind).*\\b.*", RegexOption.IGNORE_CASE), 0.98, 1),
    // 17. List Schedules
    PatternRule("list_schedules", Regex(".*(?:اعرض\\s+الجداول|المواعيد|مواعيدي|المنبهات|قائمة\\s+التنبيهات|المجدول|schedules).*", RegexOption.IGNORE_CASE), 0.95),
    // 18. Wisdom/Quotes
    PatternRule("ask_wisdom", Regex(".*(?:حكمة|مثل|قولي\\s+حكمة|اقتباس|حكمة\\s+اليوم|كلام\\s+جميل|quote|wisdom).*", RegexOption.IGNORE_CASE), 0.94),
    // 19. Mood Journal
    PatternRule("mood_journal", Regex(".*(?:مزاجي|مزاج|كيف\\s+مزاجي|إزاي\\s+مزاجي|mood\\s*journal|melancholy).*", RegexOption.IGNORE_CASE), 0.93),
    // 20. Semantic Search
    PatternRule("semantic_search", Regex("^(?:ابحث\\s+عن|دور\\s+على|شوفلي|ابحثلي|find|search)\\s+(.+)", RegexOption.IGNORE_CASE), 0.92, 1),
    // 21. Image Analysis
    PatternRule("analyze_image", Regex(".*(?:حلل|تحليل|انظر|شوف|فحص|analyze|examine)\\s*(?:الصورة|الصوره|صوره|صورة|image|photo).*", RegexOption.IGNORE_CASE), 0.96),
    // 22. Text Extraction (OCR)
    PatternRule("extract_text", Regex(".*(?:اقرا|اقرأ|استخرج|نص|كلام|مكتوب|read|extract|ocr)\\s*(?:النص|الكلام|من\\s+الصورة|من\\s+الصوره|text|from\\s+image).*", RegexOption.IGNORE_CASE), 0.95),
    // 23. Face Detection
    PatternRule("detect_faces", Regex(".*(?:وجوه|وجه|كام\\s+وجه|عدد\\s+الوجوه|faces|detect\\s+face).*", RegexOption.IGNORE_CASE), 0.94),
    // 24. Voice Input
    PatternRule("voice_input", Regex(".*(?:سجل|تسجيل|صوت|كلمني|اسمع|voice|record|listen|mic).*", RegexOption.IGNORE_CASE), 0.93),
    // 25. Speak Response (TTS)
    PatternRule("speak_response", Regex(".*(?:انطق|نطق|تكلم|قوليلي|speak|say|talk|tts).*", RegexOption.IGNORE_CASE), 0.93),
    
    // 26. Islamic Knowledge - General
    PatternRule("ask_islamic", Regex(".*(?:علمني|فهميني|اشرحي|قولي\\s+لي|ما\\s+هو|ما\\s+هي|عرفيني)\\s*(?:الفقه|السيرة|العقيدة|التزكية|الإسلام|الدين|العبادات|الطهارة|الصلاة|الزكاة|الصيام|الحج|النبي|الرسول|محمد|التوحيد|الإيمان|النفس|القلب|الدنيا).*", RegexOption.IGNORE_CASE), 0.94, 1),
    
    // 27. Islamic Knowledge - Fiqh
    PatternRule("ask_fiqh", Regex(".*(?:فقه|فقه\\s+يسير|أحكام|حكم|الطهارة|الوضوء|الصلاة|الزكاة|الصيام|الحج|العبادات|الفرائض).*", RegexOption.IGNORE_CASE), 0.95),
    
    // 28. Islamic Knowledge - Seerah
    PatternRule("ask_seerah", Regex(".*(?:سيرة|سيرة\\s+يسير|النبي|الرسول|محمد|مولد|هجرة|غزوة|بدر|أحد|فتح|مكة|المدينة|صحابي|صحابية).*", RegexOption.IGNORE_CASE), 0.95),
    
    // 29. Islamic Knowledge - Aqeedah
    PatternRule("ask_aqeedah", Regex(".*(?:عقيدة|عقيدة\\s+يسير|التوحيد|الإيمان|أركان\\s+الإيمان|الملائكة|الكتب|الرسل|القدر|اليوم\\s+الآخر|الجنة|النار|الله|أسماء\\s+الله).*", RegexOption.IGNORE_CASE), 0.95),
    
    // 30. Islamic Knowledge - Tazkiyah
    PatternRule("ask_tazkiyah", Regex(".*(?:تزكية|تزكية\\s+يسيرة|النفس|القلب|الهوى|الإخلاص|الرياء|الدنيا|الآخرة|التغيير|العبودية|الأسباب|القرآن|جهاد\\s+النفس).*", RegexOption.IGNORE_CASE), 0.95)
  )

  fun match(normalizedText: String): RegexMatch? {
    val clean = normalizedText.trim()
    for (rule in RULES) {
      val m = rule.regex.find(clean)
      if (m != null) {
        val extracted = if (rule.extractGroup != null && m.groupValues.size > rule.extractGroup) {
          m.groupValues[rule.extractGroup].trim()
        } else null
        return RegexMatch(rule.intent, rule.confidence, extracted)
      }
    }
    return null
  }
}
