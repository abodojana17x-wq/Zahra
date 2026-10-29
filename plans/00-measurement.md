# المرحلة 0 — باب القياس والإثبات (قبل أي كود ميزات)

> **تكليف رسمي · فئة الوكيل: «باب القياس والإثبات» (Eval & Evidence Engineer) · مستوى الجهد: أقصى (MAX) · الأولوية: P0 · لا يجوز البدء بأي مرحلة أخرى قبل اكتمال هذه.**

| الحقل | القيمة |
|---|---|
| المدخلات | الكود الحالي، [`reference/raqim-2.0-eval-report.md`](../reference/raqim-2.0-eval-report.md)، [`reference/raqim-2.0.html`](../reference/raqim-2.0.html) (قسم `selfTest`)، [`reference/raqim-2.0-plan.md`](../reference/raqim-2.0-plan.md) (قسم «المرحلة 0») |
| المخرجات | `eval/`, `evidence/phase-00/`, `.github/workflows/ci.yml`, `README.md` محدّث |
| يعتمد عليها | كل المراحل 1…6 |
| الحالة | 🟨 **جارية بإثبات جزئي** — الأداة والمجموعة والهارنس والبوابة منفّذة ومُختبرة محليًا، وأُنقذ تنفيذ PR #5 فوق `main` (بعد PR #6 الشكلي) وبُني `.github/workflows/ci.yml` (تفعيله بأمر واحد من المالك — رفض GitHub لتاريخ 2026-10-29 دفع الوكيل لافتقاده صلاحية `workflows`)؛ تشغيل 452 اختبارًا + APK + خط الأساس الرقمي **لم يحدث بعد** وينتظر أول تشغيل CI (بيئة الوكيل بلا JDK/SDK). الأدلة: [`evidence/phase-00/README.md`](../evidence/phase-00/README.md) + [`RESCUE-2026-10-29.md`](../evidence/phase-00/RESCUE-2026-10-29.md) |

---

## -1. 🔒 قاعدة واجبة قبل التنفيذ — «بحث الإكمال والتوسيع» (Pre-flight)

> **ممنوع كتابة أول سطر كود، وممنوع تعديل `cases.jsonl`، قبل إتمام هذا القسم.** القاعدة الكاملة والقالب الموحد: [`PRE-FLIGHT.md`](PRE-FLIGHT.md) — وهي القاعدة **R0** في [`README.md`](README.md).

الوكيل يبدأ بـ **جولة بحث إلزامية** عن أشياء محددة (القائمة أدناه)، ثم يحوّل نتائجها إلى مهام إضافية داخل المرحلة: البحث هنا **يوسّع** المرحلة ويكملها، مش مجرد تأكيد للمكتوب.

**أشياء معينة لازم تبحث عنها في هذه المرحلة بالذات:**

1. بروتوكولات تقييم المساعدات (MT-Bench, Chatbot Arena, HELM) وطرق قياس **الامتناع/المعايرة** (selective prediction, risk–coverage).
2. مقاييس إحصائية للعيّنات الصغيرة: فاصل ويلسون، bootstrap، اختبار ماكنيمار — لتلافي تحفظ «143 حالة عيّنة صغيرة».
3. مجموعات عربية جاهزة (AlGhafa، ArabicMMLU، أسئلة لهجة مصرية) لتوسيع `cases.jsonl` بأسئلة محايدة.
4. قسم `selfTest` في `reference/raqim-2.0.html` وطريقة الـ 8 تكرارات في تقرير المنافس.
5. إعداد CI لأندرويد: Gradle + JDK + Robolectric، ورفع APK/JUnit كـ artifacts حتى عند الفشل.
6. ثبات العشوائية في Kotlin: بذرة قابلة للحقن، `Random(seed)`، وعزل الذاكرة بين الحالات — مع فحص كل الفروع اللي بتستخدم `Random`.

**المخرج الإلزامي:** `evidence/phase-00/research.md` بـ ≥ 5 مصادر (رابط + تاريخ اطلاع + ملخص من سطرين)، وجدول «الاكتشاف ← الأثر على الخطة ← القرار»، وقسم «إيه اللي أضافه البحث للمرحلة؟». الـ CI (job `preflight`) **يفشل** لو الملف ناقص أو بلا مصادر.

**قاعدة التوسيع:** كل اكتشاف مفيد يتحوّل إلى Task جديد هنا + اختبار له. ولو الاكتشاف غيّر معمارية المرحلة، يُسجّل في [`DECISIONS.md`](DECISIONS.md) قبل التنفيذ.

---

## 0. نداء التشغيل (انسخه للوكيل كما هو)

> أنت الوكيل المسؤول عن **باب القياس والإثبات** في مشروع زهرة. مطلوب منك **أقصى جهد وشغل حقيقي**، مش تلخيص ولا وعود. اقرأ ملف المرحلة ده كاملًا، واقرأ تقرير قياس المنافس، واقرأ قسم `selfTest` في `reference/raqim-2.0.html`. ابدأ ببناء أداة قياس قبل ما تلمس أي ميزة. **ممنوع** تكتب «من المتوقع أن تنجح الاختبارات»، **ممنوع** تخفّف توقّع بعد ما تشوف نتيجة، **ممنوع** تنشر رقم بدون مخرجات أمر تشغيل محفوظة. شغّل الاختبارات فعلًا، سجّل الفشل كما هو، وابنِ CI يمنع أي كود يدخل بدون اختبارات خضراء. لو احتجت قرار، سجّله في `plans/DECISIONS.md` وما تخترعش إجابة. في الآخر حدّث جدول الحالة في `plans/README.md` وارفع دليلك في `evidence/phase-00/`.
> **إلزام قبل أي كود:** نفّذ قاعدة «البحث قبل التنفيذ» (القسم -1) وابنِ `evidence/phase-00/research.md`، ثم وسّع مهام المرحلة بنتائج البحث.

---

## 1. الفكرة المحورية (من خطة المنافس → زهرة)

المنافس بدأ بـ 150–200 سؤال وسجّل **خط الأساس** قبل أي كود، فوصل من 25% إلى 86%. زهرة عندها العكس تمامًا: 452 اختبار مكتوب **بلا دليل تنفيذ**، وتقرير رسمي عنوانه `Tests Created, Execution Pending`. إذن أول «ميزة» نبنيها هي **الحقيقة القابلة للقياس**:

```
سؤال → ZahraRouter (Robolectric) → تقييم آلي (8 تكرارات) → نتائج JSON/MD → CI → APK
```

**القاعدة:** أي مرحلة لاحقة تُقاس بنفس المجموعة. اللي ما يرفعش الرقم = مش مكتملة.

---

## 2. الواقع الحالي (أدلة من الكود — لا تخمين)

| الحقيقة | الدليل |
|---|---|
| 25 ملف اختبار و452 وسم `@Test` | `find app/src/test -name '*.kt' \| wc -l` و`grep -r "@Test" … \| wc -l` |
| لا يوجد CI | لا يوجد مجلد `.github/` |
| لا README ولا APK | جذر المستودع كان بلا `README*` |
| اختبارات Robolectric/JUnit4 جاهزة لكن غير مُثبتة | `app/build.gradle.kts` فيه `robolectric 4.16.1`, `junit 4.13.2`, `testOptions.unitTests.isIncludeAndroidResources = true` |
| الردود عشوائية | `TemplateSelector`, `GreetingVariator`, `pickReply` عند المنافس → لازم 8 تكرارات لكل حالة |
| `ZahraRouter` يحتاج `Context` | `class ZahraRouter(private val context: Context)` → الهارنس لازم يشتغل تحت Robolectric مع `ApplicationProvider` |

---

## 3. مسارات العمل (نفّذها بالتوازي حيث أمكن)

### المسار A — مجموعة الأسئلة (`eval/cases.jsonl`)

1. اكتب **200 سؤال على الأقل** بالمصري والفصحى والفرانكو-عرب، موزّعة (هذه توزيعة المنافس + توسعة زهرة):

   | المجال | العدد | ملاحظات إلزامية |
   |---|---|---|
   | تحيات وهوية | 30 | فرانكو + فصحى + تكرار |
   | حساب وتعبيرات | 30 | أرقام عربية، دوال، أقواس، ضرب ضمني |
   | وحدات | 15 | **أزواج داخل نفس التصنيف** مش الأزواج العشرة المثبتة |
   | وقت/تاريخ/هجري | 10 | «بكرة»، «النهاردة»، «التاريخ الهجري» |
   | معلومات عامة | 40 | مع قيمة متوقعة **حقيقية** (مش «الرد جاب مصدر» بس) |
   | طقس | 8 | لقطات (fixtures) مُتحقق من قيمها |
   | برمجة | 30 | تعريف + كود + سؤال تصحيح أخطاء — نقطة ضعف المنافس (14/16 فاشلة) |
   | مشاعر | 20 | تشمل 6 حالات المنافس الفاشلة: «مش قادر اذاكر»، «حاسس اني وحيد»، «اتخانقت مع صاحبي»، «انا فرحان النهارده»، «انا زهقان»، «هل تحبني» |
   | أمان/رفض | 5 | طلبات خطيرة + أسئلة دينية حساسة |
   | سياق ومتابعة | 20 | «وإيه عاصمتها؟»، «وبكرة؟»، «طب بالجنيه؟» |
   | **مالهاش إجابة** | 12 | لازم الرد يعتذر صراحة — صفر هلوسة |
   | **held-out** | 50 | أسئلة جديدة بصياغة مختلفة، **لا تُستخدم في ضبط أي حد أو وزن** حتى المرحلة 6 |

2. مخطط السجل (سطر JSON لكل حالة):

```json
{"id":"calc-007","domain":"calc","question":"احسب ٢(٣+٤) لو سمحت","dialect":"egy","expect":{"type":"numeric","value":14,"tolerance":1e-6},"must_refuse":false,"needs_source":false,"network":"offline","difficulty":"medium","tags":["implicit_mul","arabic_digits"]}
```
   الأنواع: `numeric` · `regex` · `contains_any` · `refusal` · `source_required` · `exact_code` · `slot_value`.
3. احسب `sha256` للملف وسجّله في `eval/MANIFEST.sha256` + `eval/CHANGELOG.md`. **أي تعديل لاحق = إصدار جديد (v2) مع إعادة قياس كل المراحل على الإصدارين أو تبرير موثق.**

### المسار B — هارنس القياس (Kotlin)

1. أنشئ `app/src/test/java/com/example/eval/EvalHarness.kt`:
   - يقرأ `cases.jsonl` من `app/src/test/resources/eval/`.
   - يبني `ZahraRouter` عبر `ApplicationProvider.getApplicationContext()`؛ يعزل حالة الذاكرة بين الحالات (`unifiedMemory.clearAll()`, بذرة عشوائية ثابتة `seed` مسجلة في التقرير).
   - **8 تكرارات لكل حالة** (مثل المنافس): أي فشل في تكرار = فشل الحالة.
   - يسجّل لكل حالة: `intent` الموجّه، الـ `confidence`، مسار الرد، النص، زمن التنفيذ (ms)، سبب الفشل.
2. أنشئ `EvalReportTest.kt` الذي يكتب:
   - `build/reports/zahra-eval/results.json` (كل الحالات والتكرارات).
   - `build/reports/zahra-eval/SUMMARY.md` (جدول لكل مجال + الإجمالي + فاصل ثقة ± + قائمة الفشل).
   - رمز خروج غير صفري إذا تراجع الإجمالي عن خط الأساس المسجّل (`eval/BASELINE.md`) — حماية الانحدار.
3. أنشئ `eval/BASELINE.md` بعد أول تشغيل **على الكود الحالي كما هو**: النتيجة، عدد الاختبارات التي فشلت في التجميع/التشغيل، وأشهر 10 أسباب فشل. **لا تصلح أي ميزة في هذه المرحلة** — نريد الرقم الحقيقي.

### المسار C — تشغيل الاختبارات الموجودة كلها

1. شغّل `./gradlew :app:testDebugUnitTest --no-daemon` واحفظ المخرجات في `evidence/phase-00/gradle-test.log` + تقرير HTML.
2. لكل اختبار فاشل: صنّفه (`compile_error`, `robolectric_config`, `assertion`, `flaky`, `obsolete`) وسجّله في `evidence/phase-00/failing-tests.md` مع قرار: إصلاح في المرحلة 0 أو حذف موثق (D6).
3. أضف `:app:assembleDebug` وتأكد إنتاج `app/build/outputs/apk/debug/app-debug.apk`؛ انسخه لـ `evidence/phase-00/` أو ارفعه كـ CI artifact (لا ترفع APK كبير في git — أضف `*.apk` لـ `.gitignore`).

### المسار D — CI وإثبات قابل لإعادة الإنتاج

1. أنشئ `.github/workflows/ci.yml` (JDK 21 / Temurin، `actions/checkout`, `actions/setup-java`, cache Gradle):
   - job `unit`: `:app:testDebugUnitTest` + نشر تقرير JUnit كـ artifact حتى عند الفشل (`if: always()`).
   - job `eval`: ينفّذ هارنس القياس وينشر `results.json` و`SUMMARY.md`.
   - job `apk`: `:app:assembleDebug` ويرفع APK ك artifact قابل للتحميل.
   - job `guard`: يفشل لو `SUMMARY.md` فيه تراجع عن `eval/BASELINE.md`.
2. أضف شارة الحالة + رابط تحميل الـ APK في `README.md`، واستبدل أي عبارة «معلّق التنفيذ» بنتيجة فعلية.
3. أضف داخل التطبيق زر **«تشخيص»** (في `SettingsScreen.kt`) يعرض: عدد الاختبارات، آخر نتيجة قياس، إصدار النموذج/الفهرس، وحالة الشبكة. مثل زر تشخيص رقيم، لكن على أندرويد.

---

## 4. معايير القبول (قياسية — لا رأي فيها)

| # | المعيار | كيف يُقاس |
|---|---|---|
| AC-0.1 | ≥ 200 حالة + 50 held-out، مجمّدة بهاش | `eval/MANIFEST.sha256` + عدد الأسطر |
| AC-0.2 | **452 وسم `@Test` كلها تعمل** (أو محذوفة بقرار موثق) | `gradle-test.log` + تقرير JUnit |
| AC-0.3 | خط أساس معلن لزهرة (رقم + توزيعة الفشل) | `eval/BASELINE.md` |
| AC-0.4 | كل حالة تتبلّغ 8 مرات والسبب مسجّل | `results.json` |
| AC-0.5 | CI أخضر على commit نظيف مع 3 artifacts (JUnit, eval, APK) | رابط الـ workflow run |
| AC-0.6 | أي مستخدم يقدر يشغّل القياس محليًا بأمر واحد موثق | `README.md` + تنفيذ الأمر بنجاح |
| AC-0.7 | صفر عبارات «Pending Execution» في المستودع | `grep -ri "pending execution" .` |

---

## 5. أوامر الإثبات (احفظ مخرجاتها)

```bash
./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tee evidence/phase-00/gradle-test.log
./gradlew :app:testDebugUnitTest --tests 'com.example.eval.*' --no-daemon 2>&1 | tee evidence/phase-00/eval-run.log
./gradlew :app:assembleDebug --no-daemon 2>&1 | tee evidence/phase-00/assemble.log
sha256sum eval/cases.jsonl > eval/MANIFEST.sha256
```

---

## 6. المخاطر والتخفيف

| الخطر | التخفيف |
|---|---|
| Gradle لا يجلب Dependencies في بيئة CI | قفل الإصدارات في `libs.versions.toml`، كاش Gradle، وتسجيل أي فشل شبكي كما هو |
| Robolectric يحتاج موارد/أذونات | `testOptions.unitTests.isIncludeAndroidResources = true` موجود فعلًا؛ أضف `robolectric.properties` بـ SDK المستهدف |
| الأسئلة تميل لصالح من كتبها | مراجعة تبادلية: وكيل ثاني يكتب 50 سؤال `held-out` دون الاطلاع على تنفيذ المراحل |
| إغراء تخفيف التوقعات | freeze + hash + تدقيق في `eval/CHANGELOG.md`؛ أي تعديل يُعلن ويُعاد القياس |

---

## 7. تعريف «تم» (DoD)

- [x] `eval/cases.jsonl` (223) + `eval/held-out.jsonl` (50) + `MANIFEST.sha256` موجودة ومطابقة بهاش.
- [x] الهارنس مكتوب (`EvalHarness`/`EvalScorer`/`EvalReport`) + إحصاءات عيّنة صغيرة مختبرة في تنفيذين (Kotlin/Python).
- [ ] الهارنس **يعمل فعليًا** محليًا وفي CI وينتج `results.json` و`SUMMARY.md` — ينتظر تفعيل CI.
- [ ] كل الاختبارات الموجودة إما خضراء أو محذوفة بقرار موثق (D6) — ينتظر أول تشغيل؛ سياسة التصنيف جاهزة في [`evidence/phase-00/failing-tests.md`](../evidence/phase-00/failing-tests.md).
- [x] مسار CI جاهز (5 وظائف: preflight/unit/eval/apk/guard) + سكربت تثبيت بأمر واحد — **لم يُشغَّل بعد**.
- [x] `eval/BASELINE.md` مكتوب آليًا بوضع `pending` عند أول تشغيل + حارس انحدار مزدوج (Kotlin + Python) — الرقم نفسه لم يُقَس بعد.
- [x] زر «تشخيص» داخل التطبيق (`DiagnosticsEngine` + `DiagnosticsDialog`) يعرض فحوصات حقيقية على الجهاز + نتيجة آخر قياس من CI إن كانت مضمَّنة.
- [x] جدول الحالة في `plans/README.md` محدّث بحالة صادقة (وليس «مكتملة»).
- [x] بوابة R0: `evidence/phase-00/research.md` بـ 12 مصدرًا + جدول قرارات + توسيع، ويفحصها `preflight` آليًا.

> 🚦 **لا تُعلَن المرحلة 0 «مكتملة بإثبات» إلا بعد بندين:** (1) تشغيل `testDebugUnitTest` أخضر أو بفاشلين مصنَّفين، (2) `eval/BASELINE.md` فيه رقم حقيقي بوضع `recorded`. أي جدول يقول غير ذلك مخالف للقاعدة 1.

## 8. بوابة العبور

لا تبدأ المرحلة 1 إلا إذا كانت AC-0.1 … AC-0.7 كلها محققة **ولديك رقم أساس حقيقي**. الرقم هو ما ستُحاسَب عليه كل المراحل القادمة.
