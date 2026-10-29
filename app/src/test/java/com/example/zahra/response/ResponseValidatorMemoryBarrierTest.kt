package com.example.zahra.response

import com.example.zahra.model.ConfidenceTier
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ZahraResponse
import com.example.zahra.personality.PersonalityPreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * حاجز ResponseValidator ضد ادعاءات الذاكرة السابقة بدون turn حقيقي (المرحلة 1، الحاجز الأول).
 * لم يُشغّل بعد.
 */
class ResponseValidatorMemoryBarrierTest {

  private val validator = ResponseValidator()
  private val personality = PersonalityPreferences() // الافتراضية

  private fun response(text: String) = ZahraResponse(
    text = text,
    intent = "query_memory",
    confidence = 0.99,
    confidenceTier = ConfidenceTier.DIRECT,
    outcomeState = OutcomeState.SUPPORTED,
    source = "اختبار"
  )

  @Test
  fun validate_passesNormalResponse() {
    val res = response("مرحبا بك! كيف يمكنني مساعدتك؟")
    assertTrue(validator.validate(res, personality))
    assertTrue(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = false))
  }

  @Test
  fun hasNoFabricatedMemoryClaims_blocksClaimWithoutTurn() {
    val res = response("ما زال في ذاكرتي أنك تحب القراءة")
    assertFalse(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = false))
  }

  @Test
  fun hasNoFabricatedMemoryClaims_blocksAsMentionedEarlierWithoutTurn() {
    val res = response("كما ذكرت سابقًا، اسمك أحمد")
    assertFalse(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = false))
  }

  @Test
  fun hasNoFabricatedMemoryClaims_blocksPhoneClaimWithoutTurn() {
    val res = response("رقمي محفوظ لدي وهو ...")
    assertFalse(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = false))
  }

  @Test
  fun hasNoFabricatedMemoryClaims_allowsWithRealTurn() {
    val res = response("كما ذكرت سابقًا، اسمك أحمد")
    assertTrue(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = true))
  }

  @Test
  fun hasNoFabricatedMemoryClaims_ignoresUnrelatedText() {
    val res = response("مواقيت الصلاة اليوم عند الفجر 04:45 ص")
    assertTrue(validator.hasNoFabricatedMemoryClaims(res, hasRealTurn = false))
  }
}
