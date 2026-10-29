package com.example.zahra.routing

/**
 * مَعرّف النية داخل طبقة التوجيه.
 *
 * النوايا في زهرة تُعرَّف تاريخيًا كسلاسل نصية (مثل `"calculate"`, `"greet"`، `"ask_religion"`).
 * هذه الفئة تُغلّف الوسم النصي لحفظ توافق المستهلكين الحاليين مع إعطاء النوع معنىً واضحًا في قرار التوجيه.
 * لا تُستخدَم كقائمة مُغلقة (enum) حتى لا تُكسر conven-tions القائمة عند إضافة نوايا مستقبلية.
 */
value class Intent(val label: String) {

  companion object {
    const val UNKNOWN = "unknown"

    // محادثة عامة
    const val GREET = "greet"
    const val FAREWELL = "farewell"
    const val HELP = "help"
    const val THANK = "thank"

    // أدوات حتمية
    const val CALCULATE = "calculate"
    const val CONVERT_UNIT = "convert_unit"
    const val TRANSLATE = "translate"
    const val SUMMARIZE = "summarize"

    // معرفة
    const val DEFINE_WORD = "define_word"
    const val QUERY_KNOWLEDGE = "query_knowledge"

    // ذاكرة وملاحظات ومهام وجدولة (أوامر نظام)
    const val REMEMBER_NOTE = "remember_note"
    const val LIST_NOTES = "list_notes"
    const val DELETE_NOTE = "delete_note"
    const val CREATE_TASK = "create_task"
    const val LIST_TASKS = "list_tasks"
    const val COMPLETE_TASK = "complete_task"
    const val CREATE_SCHEDULE = "create_schedule"
    const val LIST_SCHEDULES = "list_schedules"
    const val CANCEL_SCHEDULE = "cancel_schedule"
    const val QUERY_MEMORY = "query_memory"
    const val FORGET_FACT = "forget_fact"
    const val REMEMBER_FACT = "remember_fact"
    const val UPDATE_FACT = "correct_fact"
    const val UNDO_ACTION = "undo_action"

    // دين
    const val ASK_RELIGION = "ask_religion"
    const val ASK_PRAYER_TIME = "ask_prayer_time"
    const val ASK_QURAN = "ask_quran"
    const val ASK_HADITH = "ask_hadith"
    const val ASK_ISLAMIC = "ask_islamic"
    const val ASK_FIQH = "ask_fiqh"
    const val ASK_SEERAH = "ask_seerah"
    const val ASK_AQEEDAH = "ask_aqeedah"
    const val ASK_TAZKIYAH = "ask_tazkiyah"

    // أخرى في التنفيذ الحالي
    const val CHANGE_PERSONALITY = "change_personality"
    const val COMPOUND_PLAN = "compound_plan"
    const val GENERATE_IMAGE = "generate_image"
    const val ASK_WISDOM = "ask_wisdom"
    const val MOOD_JOURNAL = "mood_journal"
    const val SEMANTIC_SEARCH = "semantic_search"
    const val ANALYZE_IMAGE = "analyze_image"
    const val EXTRACT_TEXT = "extract_text"
    const val DETECT_FACES = "detect_faces"
    const val VOICE_INPUT = "voice_input"
    const val SPEAK_RESPONSE = "speak_response"
  }
}
