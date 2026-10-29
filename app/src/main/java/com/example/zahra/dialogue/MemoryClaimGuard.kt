package com.example.zahra.dialogue

/** Legacy text lacks claim-level evidence. Reject memory-claim templates rather than
 * treating the mere existence of an unrelated previous turn as proof. */
object MemoryClaimGuard {
    private val claims = listOf("كما ذكرت", "كما أخبرتني", "كما اخبرتني", "قلت لي", "حديثك السابق", "ما زال في ذاكرتي", "لسة فاكرة", "لسه فاكره", "بالمناسبة")
    fun containsUnverifiableClaim(text: String): Boolean = claims.any { text.contains(it, ignoreCase = true) }
}
