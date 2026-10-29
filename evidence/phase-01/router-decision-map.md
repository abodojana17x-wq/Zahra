# خريطة قرار الموجه — المرحلة 01 (قبل إعادة الهيكلة)

المصدر: `app/src/main/java/com/example/zahra/engines/ZahraRouter.process()`،commit `8622f34`، أرقام الأسنان לפי نسخة الملف الحالية.
الغرض: توثيق كل فرع يتخذ مسارًا أو يستخدم `confidence` قبل reemplacingه ببوابة موحّدة (`routing/`). أي تعديل على الكود يُستمد من هذه الخريطة ولا يُحذف سلوك without اختبار توصيف يغطيه.

التصنيف: `يُبْقى` = يُحتفظ به كما هو في طبقة التنفيذ؛ `يُستبدل بالبوابة` = ينتقل منطق القرار إلى `DefaultRouter`+`ConfidenceGatePolicy`؛ `يُزال` = يُحذف لأنه غير أمين (ثابت مكتوب يدويًا)ويًا) أو مكرر.

| # | الدالة / السطر | النية والمسار | الشرط والعتبة | مصدر الدرجة | يُبقى/يُستبدل/يُزال | السبب | اختبار التوصيف المقابل |
|---|---|---|---|---|---|---|---|
| B1 | `process()` ~57 | `change_personality` → رد تأكيد شخصية | `personalityCmd != null && cmdResult is Success` | ثابت مكتوب يدويًا `0.99` | يُبْقى (تنفيذ أمر نظام) | أمر نظام صريح من المستخدم، درجة اليقين عالية مبررة | لا (مוגן كأمر نظام) |
| B2 | `process()` ~100 | `ask_religion` → حماية المنوعات المحرمة | `impermissibleRel != null` (من `ReligionEngine.checkImpermissibleRequest`) | `impermissibleRel.confidence` من محرك الدين | يُبْعى | أولوية أمان/رفض — precedes routing | `Phase4SafetyAndReligiousIntegrityTest` (موجود) |
| B3 | `process()` ~126 | `correct_fact` → تحديث حقيقة ذاكرة | `correction is UpdateFact` | ثابت `0.99` | يُبْقى (تنفيذ أمر نظام) | تصريح صريح من المستخدم | لا |
| B4 | `process()` ~145 | `undo_action` | `correction is UndoAction` | ثابت `0.95` | يُبْقى (تنفيذ أمر نظام) | إجراء نظام | لا |
| B5 | `process()` ~168 | `compound_plan` | `StructuredTaskPlanner.isCompoundRequest(rawInput)` | `executedPlan.overallConfidence` (من محرك التخطيط) | يُبْقى | أمر نظام مركب موجود بالفعل | `Phase5IntelligenceEngineTest` (موجود) |
| B6 | `process()` ~205 | `query_memory` → "ماذا تعرف عني؟" | كلمات الذاكرة موجودة في `normalized` | ثابت `0.99` | يُبْعى (تنفيذ أمر نظام/ذاكرة) | استعلام ذاكرة صريح | `Phase6MemorySystemTest` (موجود) |
| B7 | `process()` ~235–248 | `forget_fact` | `isForgetReq` | ثابت `0.98` 성공 / `0.85` عدم عثoor | يُبْقى | أمر نظام | لا |
| B8 | `process()` ~260–285 | `remember_fact` | `memoryEngine.saveExplicitMemory(clean) != null` | `0.99` / `0.80` / `0.90` حسب النتيجة | يُبْقى | أمر نظام | لا |
| B9 | `process()` ~296 | `remember_fact` (استخراج تلقائي) | `autoExtractedFact != null && (intent == unknown || greet || confidence < 0.45)` | ثابت `0.98` | يُستبدل بالبوابة | عتبة `0.45` مكتوبة يدويًا داخل مسار التنفيذ؛ البوابة تقرر CLARIFY/ABSTAIN بدلاً من خلط الذاكرة بالتصنيف | `ZahraRouterRoutingTest` (جديد) |
| B10 | `process()` ~462–464 | تصنيف النية (مسار غير المطابق regex) | `calibratedScore.topProbability >= 0.50 && !isAbstentionRecommended` | `calibratedClassifier` softmax (حرارة 1.0) | يُستبدل بالبوابة | العتبة `0.50` مكتوبة يدويًا؛ مصدر الدرجة مصنف غير معاير (D4) | `DefaultRouterTest` + `ConfidenceGateTest` |
| B11 | `process()` ~467 | fallback إلى NB | `calibratedScore.isAbstentionRecommended && nbScore.confidence < 0.40` → `0.20` | `nbClassifier.confidence` | يُستبدل بالبوابة | `0.20` و`0.40` ثابتان يدويًا | `DefaultRouterTest` |
| B12 | `process()` ~491 | `generate_image` | `(intent == unknown || confidence < 0.85) && isImageReq` | `0.98` يُفرض بعد القرار | يُستبدل بالبوابة | `0.85` مكتوبة يدويًا؛ Bilder هو أداة محلية (Deterministic tool) | `DefaultRouterTest` |
| B13 | `process()` ~511 | `create_schedule` | `(intent == unknown || confidence < 0.85) && isSchedReq` | `0.98` يُفرض | يُستبدل بالبوابة | كما في السطر السابق | `DefaultRouterTest` |
| B14 | `process()` ~513 | نية مقترحة من اللهجة المصرية | `(intent == unknown || confidence < 0.70) && adapted.inferredIntentOverride != null` | `0.95` يُفرض | يُبْقى (من `DialectAndColloquialAdapter` الحالي) | جزء من Frib understanding الحالي؛ لا يسجل كثقة مصنف | لا جديد |
| B15 | `process()` ~519–521 | فرض درجات اليقين | `generate_image`/`create_schedule`/`cancel_schedule` → `0.98`؛ override → `0.95` | ثابت مكتوب يدويًا | يُبْقى مؤقتًا (تنفيذي) | قيمة نجاح أداة، ليست من مصنف؛ لم تُزال لتجنب كسر suite موجود دون تشغيل للتحقق؛ موثقة كقيودknown limitation | `ConfidenceGateTest` (توثيق أن البوابة لا تخترع قيمًا جديدة) |
| B16 | `process()` ~555 | esf fallback الصادق | `confidence < 0.35 && finalIntent == unknown` → `generateFallback()` | الدرجة المحسوبة | يُستبدل بالبوابة | `0.35` مكتوبة يدويًا؛ تصبح `UNKNOWN_ABSTAIN` في `ConfidenceGatePolicy` مع توثيق المصدر | `DefaultRouterTest` + `ZahraRouterRoutingTest` |
| B17 | `process()` ~1232–1233 | تصنيف مستوى الثقة | `confidence >= 0.75` → DIRECT، `>= 0.40` → QUALIFIED، else UNCERTAIN | الدرجة المحسوبة | يُبْقى (مستوى عرضي فقط) | لا يُستخدم في قرار التوجيه؛ يُحتفظ به لتقسيم العرض | `ConfidenceTierTest` (جديد، خفيف) |
| R1 | `ReligionEngine.searchQuran` | `ask_quran` | `bestScore >= 0.40` | نتيجة توافق معجمي للقرآن | يُبْقى | عتبة محرك الدين موثقة المصدر (محلي) | `IslamicKnowledgeEngineTest` (موجود) |
| R2 | `ReligionEngine.searchHadith` | `ask_hadith` | `score < 0.30` → امتناع (ترجع null) | نتيجة توافق الحديث | يُبْعى | حارس حديث محلي | `IslamicKnowledgeEngineTest` (موجود) |
| R3 | `ReligionEngine.checkImpermissibleRequest` |protective | فئات محجوزة → `confidence = 0.99` | محرك الدين | يُبْقى | حاجز أمان أولوية | موجود في اختبارات السلامة الدينية |

## سلوك يجب الحفاظ عليه (Characterization)

1. `احسب 50 * 4 + 20` → `calculate`، `SUPPORTED`, النص يحوي "220".
2. `احسب 100 / (5 - 5)` → `EXECUTION_ERROR`، النص يحوي "صفر" أو "غير معرفة".
3. `احسب 25 / 0` → `EXECUTION_ERROR`، لا يعود "الناتج: 25 / 0 = 0".
4. `حول 50 كجم إلى كيلوفولت` → `UNSUPPORTED`، النص يحوي "غير مدعوم".
5. `فكرني بمراجعة بحث التخرج وكتابة الملخص` (بدون وقت) → `AMBIGUOUS`، لا يُنشأ تذكير.
6. `فكرني غداً` → `AMBIGUOUS`، لا يُنشأ تذكير.
7. "ما هو حديث النبي عن قيادة الطائرات الفضائية؟" → `UNSUPPORTED`، نص الامتناع الصادق ("غير مسجل"/"أمتنع"/"يمتنع"/"مش متأكد").
8. `generateFallback()` يرد نص امتناع صريح بدون اقتباس سؤال المستخدم وثقة `0.15`.
9. persona/greeting/memory/system branches ترجع بثقة عالية ثابتة (`0.95`–`0.99`) — سلوك التنفيذ الحالي يُحفظ دون تغيير في هذه المرحلة.

## سلوك غير أمين يجب إصلاحه (ليس للثبيت في الاختبارات)

- ثبات `0.98`/`0.95` على نيات الجدولة والصورة والشخصية — لا تأتي من الأداة أو المصنف.
- عتبات `0.50`/`0.40`/`0.35`/`0.85`/`0.70`/`0.45` مكتوبة يدويًا داخل `process()` (D4: غير معايّرة).
- فرعية `confidence < 0.35 && intent == unknown` فقط: نية غير معروفة بدرجة 0.40 تُمرر للتنفيذ بدون بوابة.
- `CLARIFY` الحالي لا يقدّم بدائل من أعلى النيات الفعلية في جميع الفروع.

## نقاط التكامل المنفذة في `ZahraRouter.process()`

- حقل `val router: Router = DefaultRouter()` يُنشأ مع الراوتر.
- بعد حل النية وفرض الثوابت التنفيذية، يُبنى `RoutingEvidence` من (`rawInput`, `normalized`, `langInfo`, `finalIntent`, `confidence`, درجات `calibratedClassifier.rankedProbabilities`، حالة المعرفة من `knowledgeEngine.query` للنوايا المعرفية، وكاشف الغموض الحالي) ويُمرر إلى `router.route()`.
- `GateOutcome.ABSTAIN` → `generateFallback(rawInput)` مع `traceId` في أثر التتبع (يحل محل الفحص المنعزل `confidence < 0.35 && intent == unknown`).
- `GateOutcome.CLARIFY` → `buildRoutingClarification(...)` يبقى كاشف الغموض restait `Clear` (بدائل من أعلى النيات الفعلية في القرار).
- `traceId` يُسجّل في `EvidenceGroundingTrace.extractedEntities` عند البناء النهائي للأثر.
- الفروع النظامية (الشخصية، الذاكرة، الجدولة، المهام، الملاحظات، التخطيط المركب، الحظر الديني) تُرجع قبل/أثناء المسار دون تغيير في سلوكها الحالي.

## قرارات اتخذتها الخريطة

1. يُنتقل كل قرار استخدام `confidence` إلى `routing.DefaultRouter`+`ConfidenceGatePolicy` (T-1.7/T-1.8).
2. تبقى فروع الأوامر النظامية (الشخصية، الذاكرة، الجدولة، المهام، الملاحظات، التخطيط المركب) في `ZahraRouter` كتنفيذ للقرار الذي يعيده الموجه.
3. يُزال فرض الثوابت `0.98`/`0.95` في التنفيذ؛ تصبح градация الدرجة من المصدر الحقيقي فقط.
4. `traceId` يُضاف إلى `RoutingDecision` ويُسجّل في `EvidenceGroundingTrace` عبر `extractedEntities`.
