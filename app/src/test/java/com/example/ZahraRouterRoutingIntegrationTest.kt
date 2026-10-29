package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.model.OutcomeState
import com.example.zahra.model.ConfidenceTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * تكامل طبقة التوجيه الجديدة في المسار الفعلي لـ `ZahraRouter.process()` (المسار ج).
 *
 * يتطلب Android Context عبر Robolectric، ولا توجد JDK/ Android SDK في بيئة هذه
 * الحجرة، لذلك الاختبار مكتوب ووثائقي ولم يُشغّل بعد. لا يُوصف بأنه ناجح قبل تشغيل فعلي.
 */
@RunWith(RobolectricTestRunner::class)
class ZahraRouterRoutingIntegrationTest {

 private lateinit var context: Context
 private lateinit var router: ZahraRouter

 @Before
 fun setUp() {
  context = ApplicationProvider.getApplicationContext()
  router = ZahraRouter(context)
 }

 // --- التوصيف: السلوك الحالي قبل إعادة الهيكلة يُحفظ ---

 @Test
 fun process_calculationIsSupported() {
  val res = router.process("احسب 50 * 4 + 20")
  assertEquals(OutcomeState.SUPPORTED, res.outcomeState)
  assertTrue(res.text.contains("220"))
 }

 @Test
 fun process_divisionByZeroIsExecutionError() {
  val res = router.process("احسب 100 / (5 - 5)")
  assertEquals(OutcomeState.EXECUTION_ERROR, res.outcomeState)
  assertTrue(res.text.contains("صفر") || res.text.contains("غير معرفة"))
 }

 @Test
 fun process_unsupportedUnitNeverFabricates() {
  val res = router.process("حول 50 كجم إلى كيلوفولت")
  assertEquals(OutcomeState.UNSUPPORTED, res.outcomeState)
  assertTrue(res.text.contains("غير مدعوم"))
 }

 @Test
 fun process_scheduleWithoutTimeIsAmbiguous() {
  val initial = router.scheduleEngine.getSchedules().size
  val res = router.process("فكرني بمراجعة بحث التخرج وكتابة الملخص")
  assertEquals(OutcomeState.AMBIGUOUS, res.outcomeState)
  assertNull(res.createdSchedule)
  assertEquals(initial, router.scheduleEngine.getSchedules().size)
 }

 @Test
 fun process_unknownLowConfidenceHonestFallback() {
  val res = router.process("عكعكع كسكس ليس له معنى")
  assertTrue(res.text.contains("مش متأكد") || res.text.contains("أمتنع") || res.text.contains("يمتنع"))
  assertEquals(OutcomeState.UNSUPPORTED, res.outcomeState)
 }

 // --- وصول المسار الفعلي إلى طبقة routing الجديدة ---

 @Test
 fun process_reachesRoutingLayer_traceIdRecorded() {
  val res = router.process("مرحبا")
  // traceId يُسجّل في أثر التتبع عبر الكيانات
  val traceId = res.evidenceTrace?.extractedEntities?.get("traceId")
  assertNotNull("يجب أن يحمل أثر التتبع traceId من قرار التوجيه", traceId)
  assertTrue("traceId يجب أن يكون معرفًا فريدًا غير فارغ", traceId.isNotBlank())
 }

 @Test
 fun process_unknownLowConfidenceUsesRoutingGate() {
  // نية غير معروفة وثقة متدنية → الامتناع عبر بوابة التوجيه (generateFallback)
  val res = router.process("زقرplet غير مفهوم")
  assertEquals(OutcomeState.UNSUPPORTED, res.outcomeState)
  assertTrue(res.text.contains("مش متأكد") || res.text.contains("أمتنع") || res.text.contains("يمتنع"))
 }
}
