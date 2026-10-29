package com.example.zahra.dialogue

import com.example.zahra.personality.CommunicationTone
import com.example.zahra.personality.EmojiFrequency
import com.example.zahra.personality.PersonalityPreferences
import com.example.zahra.personality.StyleApplier
import org.junit.Assert.*
import org.junit.Test

class StyleIntegrityTest {
    @Test fun styleNeverChangesTheCoreNumbersLinksOrAbstention() {
        val cores = listOf("النتيجة 12.50؛ المصدر https://example.com/a?x=9", "لا أستطيع الإجابة دون مصدر موثوق.")
        CommunicationTone.values().forEach { tone ->
            EmojiFrequency.values().forEach { frequency ->
                val personality = PersonalityPreferences.getDefault().copy(tone = tone, emojiFrequency = frequency)
                repeat(8) { cores.forEach { core ->
                    val styled = StyleApplier.applyToText(core, personality)
                    assertTrue(styled.startsWith(core))
                } }
            }
        }
    }
}
