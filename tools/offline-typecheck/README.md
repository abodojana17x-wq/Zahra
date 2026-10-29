# تحقق بدون Gradle (offline typecheck)

> **السبب:** بيئات الوكلاء المعزولة قد لا تحتوي JDK ولا Android SDK، وقد يكون الوصول الشبكي محصورًا في GitHub (بلا `services.gradle.org`/`repo1.maven.org`). في هذه الحالة لا يمكن تشغيل Gradle ولو مرة — ومع ذلك لا يصحّ تسليم كود Kotlin بلا أي تحقق.

هذه الأداة تعطي **تحققًا جزئيًا صادقًا**:

| ما تفعله | ما لا تفعله |
|---|---|
| تترجم `app/src/test/java/com/example/eval/*.kt` + `DiagnosticsEngine.kt` فعليًا بـ `kotlinc` (أخطاء النوع/المراجع/الصياغة تُكتشف) | لا تشغّل Robolectric ولا أي كود أندرويد حقيقي |
| تُشغّل `EvalStats.kt` و`TextNorm`/`EvalScorer` منطقيًا وتقارن الأرقام بمرجع Python | لا تُشغّل `ZahraRouter` ولا تُنتج خط أساس |
| تُثبت أن تنفيذ Kotlin للإحصاء مطابق لتنفيذ Python (نفس القيم المرجعية) | لا تُغني عن `./gradlew :app:testDebugUnitTest` |

التبعيات (Gson، JUnit، Robolectric، Android، أصناف زهرة) ممثَّلة في `stubs/` **بتوقيعات محاكية** — لا تُستورد في بناء Gradle ولا يعرفها المشروع (خارج `app/src`). أي فرق بين stub والـ API الحقيقي لا تكتشفه هذه الأداة، ولذلك تبقى CI هي الحكم.

## التشغيل

```bash
pip install jdk4py                  # JDK
npm pack kotlin-compiler@2.4.21     # ثم: tar xzf kotlin-compiler-2.4.21.tgz -C /tmp/kc
./tools/offline-typecheck/run.sh    # KC_DIR=/tmp/kc/package افتراضيًا
```

المخرج الحقيقي (2026-10-26) محفوظ في [`evidence/phase-00/kotlin-logic-check.log`](../../evidence/phase-00/kotlin-logic-check.log):

```
wilson 60/100 = 0.502003 0.690599          # مطابق لجداول ويلسون المنشورة
mcnemar 0,5 = 0.062500                     # = 2/32
bootstrap = 0.2100 0.4000 deterministic=true
pct = 86.0% interval=75.0%–85.4%           # 180/223
numbers = [56.0, 1.5, 1.536, 1536.0]       # تطبيع أرقام عربية-هندية + علامة آلاف
norm = اهلا وسهلا                          # إزالة التشكيل + توحيد الهمزات
```

## المرحلة 3 — `phase03/`

```bash
./tools/offline-typecheck/phase03/run.sh                     # SelfCheck: 93 فحصًا لمنطق الاسترجاع والبوابة
./tools/offline-typecheck/phase03/run.sh --emit-fixtures     # توائم التقطيع من Kotlin → knowledge/unit/tokenizer-fixtures.json
./tools/offline-typecheck/phase03/run.sh --emit-lexicon      # قاموس الفرانكو من Kotlin → knowledge/unit/franco-lexicon.json
./tools/offline-typecheck/phase03/run.sh --dump-features     # سمات كل مرشح (يستهلكها tools/phase03/tune_weights.py)
./tools/offline-typecheck/phase03/run.sh --probe "سؤال" …    # تشخيص: المرشحون + تفسير الدرجات + قرار البوابة
```

## المرحلة 4 — `phase04/`

```bash
./tools/offline-typecheck/phase04/run.sh                # فحوص منطق الحوار المعزولة (SelfCheck)
./tools/offline-typecheck/phase04/run-tests.sh          # 35 دالة اختبار Kotlin خالص بJUnit مصغّر حقيقي
./tools/offline-typecheck/phase04/run-memory-typecheck.sh  # تحقق نوعي لحزمة الذاكرة + التشفير مقابل stubs
```

`run-tests.sh` يستخدم `testkit/` (بديل مصغّر عن JUnit: `@Test` و`Assert` **تنفّذ فعلًا**، بلا Rules/Lifecycle)
وينسخ الاختبارات مؤقتًا مستبدلًا `import org.junit.Assert.*` بـ`import org.junit.*` — لا يُعدَّل أي ملف اختبار في المستودع.
`run-memory-typecheck.sh` يستخدم `phase04/stubs/` (توقيعات Android/org.json محاكية) ويُثبت الترجمة فقط؛
لا يشغّل SharedPreferences ولا AndroidKeyStore ولا Robolectric ولا `ZahraRouter` ولا Compose.

`phase03` لا يستخدم `stubs/`: كود الاسترجاع والبوابة **نقيّ** (لا أندرويد ولا `org.json`)، فيُترجم ويُشغَّل
كما هو. التفاصيل والأرقام في [`evidence/phase-03/README.md`](../../evidence/phase-03/README.md).
