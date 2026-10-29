package com.example.zahra.dialogue

/** Conservative explicit-only mentions. Never use an inferred slot as a stored fact. */
object SlotExtractor {
    fun extract(input: String): Map<Slot, String> = buildMap {
        val normalized = input.replace('أ', 'ا').replace('إ', 'ا').replace('ة', 'ه')
        Regex("(?:الساعه|الساعه:)\\s*([0-9٠-٩]{1,2}(?::[0-9٠-٩]{2})?)").find(normalized)?.let { put(Slot.TIME, it.groupValues[1]) }
        Regex("(?:^|\\s)(جنيه|دولار|يورو|ريال)(?=\\s|[؟?،,.]|$)").find(normalized)?.let { put(Slot.CURRENCY, it.groupValues[1]) }
        Regex("(?:^|\\s)(كيلومتر|كيلوجرام|متر|جرام|ميل|فدان)(?=\\s|[؟?،,.]|$)").find(normalized)?.let { put(Slot.UNIT, it.groupValues[1]) }
        Regex("(?:اسمي|ناديني)\\s+(?:هو\\s+)?([\\p{L}]{2,20})").find(normalized)?.let {
            if (it.groupValues[1] != "مش") put(Slot.PERSON, it.groupValues[1])
        }
        Regex("(?:ترجم|باللغه)\\s+(?:الى\\s+)?(العربيه|الانجليزيه|الفرنسيه)").find(normalized)?.let { put(Slot.LANGUAGE, it.groupValues[1]) }
        Regex("(?:كود|برمجه|لغه)\\s+(بايثون|جافا|كوتلن|python|java|kotlin)", RegexOption.IGNORE_CASE).find(normalized)?.let { put(Slot.CODE_LANG, it.groupValues[1]) }
    }
}
