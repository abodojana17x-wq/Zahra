# سجل مرحلة 02 — باب الأدوات والبيانات

- الفرع: `arena/61f57b22-zahra` · الأساس: `1ed5bb2` · التاريخ: 2026-10-29
- ملف المرحلة: `plans/02-tools.md` · سياسة التنفيذ: D20 سارية (لا CI، لا JUnit إجباري، لا APK، لا baseline)
- بحث ما قبل التنفيذ (R0): [`research.md`](research.md)

## حالة التنفيذ (وصف الواقع فقط)

**مكتمل التنفيذ على مستوى الكود والمنطق الحتمي، ومُتحقَّق منه تنفيذيًا خارج Gradle.**

| ما شُغّل فعلًا | النتيجة | السجل |
|---|---|---|
| ترجمة `math/` + `tools/` + `MathEngine` + اختبارات المرحلة بـ `kotlinc` | نجحت (exit 0) | [`compile.log`](compile.log) |
| تحقق تنفيذي حقيقي للمنطق (145 فحصًا: حساب، وحدات، مسائل، وقت/هجري، كاش، شبكة، كود) | **145/145 نجحت** | [`selfcheck.log`](selfcheck.log) |
| مقارنة جدول أم القرى المدمج بمرجع JDK يومًا بيوم (2018-09-11 → 2077-10-16) | **0 اختلاف / 21,586 يومًا** | [`hijri-umalqura-vs-jdk.log`](hijri-umalqura-vs-jdk.log) |
| فحص بنيوي لملف `ZahraRouter.kt` (أخطاء الصياغة) | **0 أخطاء صياغة** (الباقي مراجع خارجية متوقعة عند ترجمة الملف منفردًا) | [`router-structure-check.log`](router-structure-check.log) |
| تحقق نوعي لكتلة الأدوات المضافة داخل الراوتر مع أنواع المشروع الحقيقية | نجح (exit 0) | [`router-block-check.log`](router-block-check.log) |
| إعادة تشغيل تحقق المرحلة 0 (`tools/offline-typecheck/run.sh`) بعد تغيير `MathEngine` | لا تراجع (نفس مخرجات الإحصاء) | — |

| ما لم يُشغَّل | السبب |
|---|---|
| `./gradlew :app:testDebugUnitTest` | لا JDK كامل ولا Android SDK ولا Gradle في بيئة التنفيذ |
| `ZahraRouterPhase02IntegrationTest` (Robolectric) | يحتاج Android SDK + Robolectric |
| بناء APK وقياس الحجم/الأداء (p95/RAM) | لا Android SDK |
| جلب شبكي حقيقي من Open-Meteo/ويكيبيديا | البيئة لا تسمح، والاختبار يستخدم ناقلًا وهميًا بنفس شكل الاستجابة |

> لا يُوصف أي شيء أعلاه بأنه «اختبارات ناجحة» بالمعنى الكامل؛ النجاح يخص **منطق الأدوات** كما استُدعي مباشرةً بـ JDK.

---

## المخرجات المنفَّذة

### أ) الحساب (`math/`)

| المخرج | المسار |
|---|---|
| أخطاء رياضية مصنّفة (نحوي/قسمة على صفر/نطاق/طفحان) | `app/src/main/java/com/example/zahra/math/MathErrors.kt` |
| شجرة العمليات + تنسيق أرقام موحّد (4 منازل + صيغة علمية) | `math/MathAst.kt` |
| سجل 27 دالة + 4 ثوابت + أسماء عربية + تحقق arity | `math/MathFunctions.kt` |
| Lexer: أرقام هندية/فارسية، `√ π × ÷`، `**`، `5²`، `1e308`، رفض `!!` والرموز الغريبة | `math/MathLexer.kt` |
| Pratt parser: أسبقية، `^` يمينية، ضرب ضمني، رفض كل معرّف مجهول | `math/MathParser.kt` |
| `MathExpressionEngine` (واجهة التقييم + شجرة التدقيق) | `math/MathParser.kt` (أسفل الملف) |

### ب) الوحدات

| المخرج | المسار |
|---|---|
| 9 تصنيفات + جدول عوامل + تحويل أفيني للحرارة + وحدات مصرية (فدان/قيراط/سهم) | `math/UnitSystem.kt` |
| استخراج طلب التحويل من نص حر (5 صيغ، منها الترتيب المعكوس) | `math/UnitRequestParser.kt` |
| رفض: تصنيف مختلف، وحدة مجهولة، عملات بلا مصدر حي | `math/UnitSystem.kt` |

### ج) المسائل الكلامية

| المخرج | المسار |
|---|---|
| 9 قوالب حتمية (خصم، سلة، نسبة، زيادة، متوسط، دائرة نصف قطر/قطر، مستطيل، باقي المال) + حالات سلبية | `math/WordProblems.kt` |

### د) الوقت والهجري (`tools/`)

| المخرج | المسار |
|---|---|
| أدوات «الساعة كام / النهاردة كام / بكرة / امبارح / بعد بكرة / كام يوم للجمعة» بساعة ومنطقة قابلتين للحقن | `tools/TimeTools.kt` |
| واجهة `HijriCalculator` + جدول أم القرى المدمج (1440–1500هـ) + بديل حسابي معلن | `tools/HijriCalculators.kt` |
| مزوّد `android.icu` بنوع `ISLAMIC_UMALQURA` صريح + سقوط آمن | `tools/android/AndroidHijriCalculator.kt` |

### هـ) البيانات والمصادر

| المخرج | المسار |
|---|---|
| سياسة الشبكة (OFFLINE_ONLY افتراضي / OPT_IN) + بوابة الأدوات | `tools/NetworkPolicy.kt` |
| بطاقة مصدر (نص/رابط/رخصة/وقت جلب/من الكاش) + سجل المصادر وتراخيصها | `tools/SourceEnvelope.kt` |
| كاش TTL (طقس 30 دقيقة، معلومات 30 يومًا) + LRU + حفظ/استرجاع | `tools/SourceCache.kt` |
| ناقل HTTP قابل للحقن + ترميز روابط | `tools/HttpTransport.kt` |
| قارئ JSON نقي بلا اعتمادية | `tools/MiniJson.kt` |
| عميل Open-Meteo (CC BY 4.0) + جدول أكواد WMO + كاش | `tools/DataTools.kt` |
| عميل ويكيبيديا Action API (`origin=*`) + مقتطف + رفض التوليف | `tools/DataTools.kt` |
| عميل ويكي‌داتا SPARQL (CC0) | `tools/DataTools.kt` |
| قصاصات كود + `CodeRunner` معطّل صراحةً (D3) | `tools/CodeSnippets.kt` |
| لقطات طقس أوفلاين للقياس العادل | `eval/fixtures/weather.json` |

### و) التكامل

| المخرج | المسار |
|---|---|
| `MathEngine` صار Adapter: نفس `MathResult` والتوقيعات، مع `operationTree` إضافي | `engines/MathEngine.kt` |
| حاجز أدوات حتمية قبل التصنيف: وقت/هجري → طقس (سياسة الشبكة) → وحدات → مسائل كلامية، مع انسحاب أمام أوامر الجدولة والعبادات | `engines/ZahraRouter.kt` |
| فرع `convert_unit` يستخدم نظام الوحدات العام بدل الأزواج العشرة | `engines/ZahraRouter.kt` |
| فرع `calculate` يجرّب المسائل الكلامية أولًا | `engines/ZahraRouter.kt` |
| فرعان جديدان `ask_date`/`ask_time` و`weather` | `engines/ZahraRouter.kt` |

---

## الاختبارات المكتوبة (لم تُشغَّل عبر Gradle)

| الملف | التغطية |
|---|---|
| `app/src/test/java/com/example/zahra/math/MathExpressionEngineTest.kt` | أسبقية/ترابط، ضرب ضمني، 27 دالة، أسماء عربية، أرقام هندية، حالات حدية، محاولات حقن |
| `app/src/test/java/com/example/zahra/math/UnitSystemTest.kt` | 61 تحويلًا مرجعيًا + عكسية + أسماء عربية + رفض تصنيف/وحدة/عملة |
| `app/src/test/java/com/example/zahra/math/WordProblemsTest.kt` | 9 قوالب + 7 حالات سلبية |
| `app/src/test/java/com/example/zahra/tools/TimeToolsTest.kt` | ساعة ثابتة، أمس/غدًا/بعد غد، هجري، مراسي أم القرى، تدرّج يومي، حواجز الجدولة والعبادات |
| `app/src/test/java/com/example/zahra/tools/DataToolsTest.kt` | MiniJson، الكاش، سياسة الشبكة، Open-Meteo، ويكيبيديا، ويكي‌داتا، القصاصات |
| `app/src/test/java/com/example/ZahraRouterPhase02IntegrationTest.kt` | تكامل المسار الفعلي (Robolectric) — 14 حالة |

---

## إصلاح عارض بناء سابق (اكتُشف أثناء التحقق)

ملف `app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt` (من المرحلة 1) كان فيه **ثلاث فواصل ناقصة** في نهاية عناصر مصفوفة (الأسطر 84 و115 و154 قبل الإصلاح)، ما يجعل ترجمة الملف تفشل بـ `error: Expecting ','` — أي أن التطبيق **لم يكن قابلًا للبناء** بعد المرحلة 1، ولم يُكتشف ذلك لأن Gradle لم يُشغَّل. أُصلحت الفواصل فقط (بلا أي تغيير في أي قيمة أو منطق)، وتأكدت الترجمة:

```bash
kotlinc app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt -d /tmp/norm.jar   # exit 0
```

هذا ليس ادعاء نجاح اختبارات؛ هو إزالة عائق ترجمة فعلي. بقي أن تُشغَّل اختبارات المرحلة 1 في بيئة بها Gradle.

---

## القيود المعروفة (بصراحة)

1. **الهجري على الجهاز:** يعتمد على `android.icu` (API 24+) بنوع أم القرى؛ إن فشل لأي سبب (OEM/استثناء) يسقط إلى جدول أم القرى المدمج، وإن خرج التاريخ عن 1440–1500هـ يسقط إلى حساب جدولي **ويُعلن أنه تقديري**. الجدول الحسابي يخالف أم القرى بيوم في ~53% من الأيام المقاسة — لذلك ليس الافتراضي.
2. **الأس الهجري:** تصحيح الشهر يبقى مسؤولية المستخدم؛ لا نعرض «رؤية» ولا نتبنّى تقويمًا محليًا غير أم القرى.
3. **الطقس/ويكيبيديا:** العميلان مكتملان ومختبَران بناقل وهمي، لكن الجلب الحقيقي يحتاج **خيطًا خلفيًا**؛ `ZahraRouter.process` يُستدعى من `Dispatchers.IO` في `ZahraViewModel`، وأي استدعاء من الواجهة مباشرة يجب أن يحترم ذلك. لا يوجد بعد زر في الإعدادات يبدّل `NetworkMode` (الواجهة خارج نطاق هذه المرحلة، والوضع الافتراضي أوفلاين دائمًا).
4. **الكاش:** نصّي في الذاكرة وقابل للحفظ/الاسترجاع، **غير مربوط بعد** بتفضيلات مشفّرة (`SharedPreferences`/`ZahraCrypto`)؛ الربط خطوة تخزين لاحقة.
5. **العملات:** لا تحويل إطلاقًا. أي طلب عملة ⇒ رسالة «محتاج مصدر حي».
6. **تنفيذ الكود:** غير مفعّل (D3)؛ القصاصات + شرح سطر بسطر فقط.
7. **الوحدات:** الوحدات العربية التقليدية (فدان/قيراط/سهم) وبايتات 1024 **قيم عرفية** تُعلن في نص الرد (`isApproximate`).
8. **`MathEngine.normalizeArabicMath`:** أُبقي حرفيًا (بما فيه استبدال «في» بضرب) لعدم كسر 85 ملفًا قائمًا؛ هذا سلوك تاريخي موثّق وليس تصميمًا جديدًا، وتغييره يحتاج قرارًا واختبارات مستقلة.
9. **قيمة 19.635 في نص الخطة:** تخص دائرة **قطرها 5** لا نصف قطرها 5. نُفِّذت الدلالتان بشكل صحيح ومفصول، ووُثّق الفرق في `WordProblemsTest`.
10. **لا أرقام أداء أو دقة** نُشرت هنا؛ ما وُجد هو نتائج تشغيل منطق الأدوات فقط.

---

## إعادة إنتاج التحقق

```bash
./tools/offline-typecheck/phase02/run.sh                     # 145 فحصًا (يتوقع RESULTS: passed=145 failed=0)
./tools/offline-typecheck/run.sh                             # تحقق المرحلة 0 (يجب أن يبقى ناجحًا)
# مطابقة الهجري مع مرجع JDK:
kotlinc -cp /tmp/verify-phase02 tools/offline-typecheck/phase02/VerifyUmmAlQura.kt -d /tmp/verify-phase02/v.jar
java -cp /tmp/verify-phase02:$KC_DIR/lib/kotlin-stdlib.jar VerifyUmmAlQuraKt   # mismatches=0 / 21586
```
