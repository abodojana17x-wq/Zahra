package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.model.OutcomeState
import com.example.zahra.tools.HttpResult
import com.example.zahra.tools.HttpTransport
import com.example.zahra.tools.NetworkMode
import com.example.zahra.tools.NetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * تكامل أدوات المرحلة 2 في المسار الفعلي لـ `ZahraRouter.process()`.
 *
 * ⚠️ مكتوب ومراجع، **ولم يُشغَّل**: بيئة التنفيذ بلا Android SDK ولا Robolectric.
 * المنطق الحتمي نفسه (بدون أندرويد) شُغّل فعليًا عبر
 * `tools/offline-typecheck/phase02/run.sh` وسجلّه `evidence/phase-02/selfcheck.log`.
 */
@RunWith(RobolectricTestRunner::class)
class ZahraRouterPhase02IntegrationTest {

  private lateinit var context: Context
  private lateinit var router: ZahraRouter

  private class FixedTransport(private val response: HttpResult) : HttpTransport {
    var calls = 0
    override fun get(url: String, headers: Map<String, String>): HttpResult {
      calls++
      return response
    }
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    router = ZahraRouter(context)
  }

  // ——— المسار A: الحساب ———
  @Test
  fun process_calculationStillWorksThroughNewParser() {
    val res = router.process("احسب 50 * 4 + 20")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("220"))
  }

  @Test
  fun process_implicitMultiplicationIsSupported() {
    val res = router.process("احسب 2(3+4)")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("14"))
  }

  // ——— المسار B: الوحدات ———
  @Test
  fun process_feddanToSquareMeters() {
    val res = router.process("حوّل 3 فدان إلى متر مربع")
    AssertEqualsSupported(res)
    assertTrue("النص: ${res.text}", res.text.contains("12602.5"))
  }

  @Test
  fun process_arabicUnitQuestion() {
    val res = router.process("٥٠٠ جرام كام كيلوجرام؟")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("0.5"))
  }

  @Test
  fun process_currencyNeverFabricatesRate() {
    val res = router.process("حول 100 دولار إلى جنيه")
    assertTrue(res.outcomeState == OutcomeState.UNSUPPORTED)
    assertFalse(res.text.contains("= 4"))
  }

  @Test
  fun process_crossCategoryIsRefusedEducationally() {
    val res = router.process("حول 10 كجم إلى متر")
    assertTrue(res.outcomeState == OutcomeState.UNSUPPORTED)
    assertTrue(res.text.contains("مفيش تحويل"))
  }

  // ——— المسار C: المسائل الكلامية ———
  @Test
  fun process_discountWordProblem() {
    val res = router.process("500 وخصم 20%")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("400"))
    assertTrue("يجب عرض خطوات الحل", res.text.contains("خصم"))
  }

  @Test
  fun process_circleAreaWordProblem() {
    val res = router.process("مساحة دائرة نصف قطرها 5")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("78.53"))
  }

  @Test
  fun process_ambiguousDiscountIsNotGuessed() {
    val res = router.process("الخصم في المحل 20%")
    assertFalse("لا يجوز إنتاج ناتج من جملة ناقصة", res.text.contains("400"))
  }

  // ——— المسار D: الوقت والهجري ———
  @Test
  fun process_dateQuestionAnsweredDeterministically() {
    val res = router.process("النهاردة كام؟")
    AssertEqualsSupported(res)
    assertTrue(Regex("\\d{1,4}").containsMatchIn(res.text))
  }

  @Test
  fun process_hijriQuestionContainsMonthName() {
    val res = router.process("إيه التاريخ الهجري النهاردة؟")
    AssertEqualsSupported(res)
    assertTrue(
      Regex("(محرم|صفر|ربيع|جمادى|رجب|شعبان|رمضان|شوال|ذو)").containsMatchIn(res.text)
    )
  }

  @Test
  fun process_schedulingIsNotHijackedByDateTools() {
    val before = router.scheduleEngine.getSchedules().size
    val res = router.process("فكرني بكرة الساعة 8 بمراجعة البحث")
    // يبقى سلوك محرّك الجدولة: إمّا نجاح بجدولة، أو توضيح — وليس رد تاريخ
    assertTrue(
      res.createdSchedule != null || res.outcomeState == OutcomeState.AMBIGUOUS || before < router.scheduleEngine.getSchedules().size
    )
    assertFalse(res.text.contains("ميلادي"))
  }

  // ——— المسار E: الطقس ———
  @Test
  fun process_weatherOfflineRefusesExplicitly() {
    val res = router.process("الطقس في القاهرة إيه؟")
    assertEqualsUnsupported(res)
    assertTrue(res.text.contains("الوضع المتصل"))
  }

  @Test
  fun process_weatherOnlineUsesInjectedSource() {
    val transport = FixedTransport(
      HttpResult.Success(
        """{"current":{"temperature_2m":31,"weather_code":0,"wind_speed_10m":12}}""",
        200
      )
    )
    val onlineRouter = ZahraRouter(context, NetworkPolicy(NetworkMode.OPT_IN_CONNECTED), transport)
    val res = onlineRouter.process("الطقس في القاهرة إيه؟")
    AssertEqualsSupported(res)
    assertTrue(res.text.contains("31"))
    assertTrue(res.text.contains("صافٍ"))
    assertNotNull(res.source)
    assertTrue(res.source!!.contains("Open-Meteo"))
    assertTrue("يجب ذكر الرخصة", res.source!!.contains("CC BY 4.0"))
    assertTrue(transport.calls >= 1)
  }

  @Test
  fun process_weatherWithoutCityAsksClarification() {
    val res = router.process("الطقس النهاردة إيه؟")
    assertTrue(res.outcomeState == OutcomeState.AMBIGUOUS)
  }

  // ——— أدوات مساعدة ———
  private fun AssertEqualsSupported(res: com.example.zahra.model.ZahraResponse) {
    assertTrue("الحالة: ${res.outcomeState} — النص: ${res.text}", res.outcomeState == OutcomeState.SUPPORTED)
  }

  private fun assertEqualsUnsupported(res: com.example.zahra.model.ZahraResponse) {
    assertTrue("الحالة: ${res.outcomeState} — النص: ${res.text}", res.outcomeState == OutcomeState.UNSUPPORTED)
  }
}
