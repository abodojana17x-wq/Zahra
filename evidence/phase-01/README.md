# سجل مرحلة 01 — باب الأمانة والتوجيه

- الفرع: `arena/af98a2a0-zahra` · الأساس: commit `8622f34` · التاريخ: 2026-10-29
- ملف المرحلة: `plans/01-honesty-routing.md` · سياسة التنفيذ: D20 سارية (لا CI، لا JUnit إجباري، لا APK، لا baseline، لا results.json)

## حالة التنفيذ (وصف الواقع فقط)

**مكتمل التنفيذ، غير مختبر تشغيليًا.**

لا توجد JDK ولا Android SDK في بيئة هذه الحجرة، لذلك لم يُشغّل أي اختبار من الاختبارات الجديدة. لم يُوصف أي اختبار بأنه ناجح. لم يُنشر أي رقم أداء أو دقة.

## المخرجات المنفذة

| المسار | المخرج | المسار (path) |
|---|---|---|
| A — خريطة القرار | توثيق فروع `ZahraRouter.process()` التي تستخدم confidence مع تصنيف يبقى/يستبدل/يُزال + اختبارات توصيف | `evidence/phase-01/router-decision-map.md` |
| B — التطبيع واللغة | توسع `ZahraNormalizer` (فرانكو عبارات أولاً، قاموس 319 مدخلًا، توحيد همزة/مربوطة/مقصورة/تشكيل/أرقام، تجذيع خفيف مع استثناءات، حماية المعنى) | `app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt` |
| B — كشف اللغة | `LanguageDetector` يعيد درجة ثقة للفرانكو (لا Boolean فقط) ونصوص مختلطة | `app/src/main/java/com/example/zahra/core/LanguageDetector.kt` |
| ج — الموجّه الموحد | حزمة `routing/` بـ KDoc: `RoutingIntent`, `Route`, `GateOutcome`, `RoutingDecision`, `ConfidenceGatePolicy` (عتبات قديمة موثقة المصدر)، `Router` + `DefaultRouter` | `app/src/main/java/com/example/zahra/routing/` |
| ج — التكامل | `ZahraRouter.process()` يبني `RoutingEvidence`، يستدعي `router.route()`، ويُنفّذ البوابة (ANSWER/CLARIFY/ABSTAIN)، ويُسجّل `traceId` في أثر التتبع | `app/src/main/java/com/example/zahra/engines/ZahraRouter.kt` |
| د — البوابة والأمانة | `ResponseValidator.hasNoFabricatedMemoryClaims` — حاجز ضد ادعاءات الذاكرة بدون turn حقيقي | `app/src/main/java/com/example/zahra/response/ResponseValidator.kt` |

## الاختبارات المكتوبة (لم تُشغّل)

- `app/src/test/java/com/example/zahra/core/ZahraNormalizerTest.kt`
- `app/src/test/java/com/example/zahra/core/LanguageDetectorTest.kt`
- `app/src/test/java/com/example/zahra/routing/ConfidenceGatePolicyTest.kt`
- `app/src/test/java/com/example/zahra/routing/DefaultRouterTest.kt`
- `app/src/test/java/com/example/zahra/response/ResponseValidatorMemoryBarrierTest.kt`
- `app/src/test/java/com/example/ZahraRouterRoutingIntegrationTest.kt` (يحتاج Robolectric/Android SDK)

## العتبات القديمة المنقولة

جميعها من `ZahraRouter.process()` ومن `ReligionEngine` ومن مرجع المنافس المحلي (0.42 موثّق للمراجعة فقط، غير مستخدم). المعايرة **غير منفّذة** — موثّق بذلك صراحةً في `ConfidenceGatePolicy`. لا عتبات جديدة مخترعة.

## القيود المتبقية

- ثوابت الثقة `0.98`/`0.95` في فروع التنفيذ (صورة/جدولة/شخصية) بقيت كما هي (قيود موثقة، لم تُزال لتجنب كسر suite موجود دون إمكانية تشغيل للتحقق).
- التكامل مع `ResponseValidator` ضد ادعاءات الذكريات المزوّرة لا يزال حاجزًا أوليًا؛ الربط بسجل turn الفعلي في المرحلة 4.
- المعايرة لكل نية (D4) تحتاج قرار الم_owner وقياس فعلي — خارج نطاق هذه المرحلة.
