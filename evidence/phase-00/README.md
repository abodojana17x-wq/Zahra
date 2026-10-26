# المرحلة 0 — باب القياس والإثبات · ملف الأدلة

**التاريخ:** 2026-10-26 · **الـ commit الأساس:** `fdeb159` · **الفرع:** `arena/d1cfa995-zahra`

> **القاعدة الحاكمة هنا:** «الادعاء بلا دليل = لم يحدث». هذا الملف يفصل بين **ما نُفِّذ وتحقّق فعلًا في هذه البيئة**، و**ما هو جاهز لكن لم يُشغَّل**، و**ما فشل/تعطّل ولماذا**. لا يوجد في هذا المجلد رقم واحد غير مُشتق من تشغيل.

---

## 1) ما نُفِّذ وتم التحقق منه فعليًا (بمخرجات محفوظة)

| # | الأمر | المخرج | النتيجة |
|---|---|---|---|
| 1 | `python3 scripts/validate_eval.py --phase 00` | مخرَج الطرفية (أدناه) | ✅ كل الفحوصات نجحت: 223 حالة، 50 held-out، هاش مطابق، بحث R0 كامل، صفر «Pending Execution» |
| 2 | `python3 scripts/test_stats.py` | [`stats-selftest.log`](stats-selftest.log) | ✅ 15 فحصًا مرجعيًا ناجحًا (Wilson من جداول منشورة، تماثل، bootstrap حتمي، McNemar دقيق) |
| 3 | `python3 scripts/gen_eval_cases.py --check` | مخرَج الطرفية | ✅ المُولّد يُنتج نفس البايتات (حتمية التوليد — شرط إعادة الإنتاج) |
| 4 | `sha256sum eval/cases.jsonl eval/held-out.jsonl` | [`../../eval/MANIFEST.sha256`](../../eval/MANIFEST.sha256) | ✅ التجميد مثبت |
| 5 | `./tools/offline-typecheck/run.sh` | [`kotlin-logic-check.log`](kotlin-logic-check.log) | ✅ ترجمة فعلية لكل ملفات القياس (`kotlinc`) + تنفيذ `EvalStats`/`TextNorm` ومطابقة أرقامها بمرجع Python (Wilson 60/100 = 0.502003/0.690599 · McNemar 0,5 = 0.0625 · interval(180/223) = 75.0%–85.4%) |

```
$ python3 scripts/validate_eval.py --phase 00
PASS · عدد الحالات الأساسية 223 ≥ 200
PASS · التوزيع مطابق للمعلن في plans/00-measurement.md
PASS · held-out 50 ≥ 50
PASS · كل المعرفات فريدة
PASS · مخطط كل الحالات صحيح
PASS · هاش eval/cases.jsonl مطابق للمجمّد
PASS · هاش eval/held-out.jsonl مطابق للمجمّد
PASS · research.md: 12 مصادر
PASS · research.md: جدول الاكتشافات موجود
PASS · research.md: قسم التوسيع موجود
PASS · research.md: أوامر القياس مكتوبة
PASS · لا يوجد أي «Pending Execution» في المستودع (AC-0.7)
كل فحوصات بوابة المرحلة 0 نجحت.
```

---

## 2) ما هو جاهز ولم يُشغَّل (وسبب التعطّل — مُعلن)

| البند | الحالة | السبب الفعلي |
|---|---|---|
| `./gradlew :app:testDebugUnitTest` (AC-0.2، 452 وسم `@Test`) | **لم يُشغَّل** | بيئة الوكيل بلا JDK وبلا Android SDK، والوصول الشبكي محصور في `github.com` (لا `services.gradle.org` ولا `repo1.maven.org`) ⇒ لا يمكن جلب Gradle ولا التبعيات. انظر §3 |
| هارنس القياس 223×8 (AC-0.4) | **جاهز ومترجم، لم يُنفَّذ** | الكود يُترجم فعليًا (تحقق §1/5) لكن تشغيل الراوتر يحتاج Robolectric + android.jar عبر Gradle ⇒ نفس سبب التعطّل |
| `:app:assembleDebug` (APK، AC-0.5) | **جاهز، لم يُشغَّل** | نفس السبب |
| خط أساس رقمي (AC-0.3) | **مؤجَّل** | لا يُخترع رقم. أول تشغيل حقيقي يكتب `eval/BASELINE.md` بوضع `pending` (D19) |
| وظائف CI الخمس | **جاهزة في `ci/github-actions.yml` وجاهزة للتثبيت** | دفع الملفات داخل `.github/workflows` يحتاج صلاحية `workflows` في توكن GitHub، وهي غير متاحة لهذه الجلسة. التفعيل بأمر واحد: `./scripts/install_ci.sh --commit && git push` |

> **الخلاصة بصراحة:** المرحلة 0 أنجزت **أداة** الإثبات (المجموعة + الهارنس + الإحصاء + البوابة + زر التشخيص)، لكنها **لم تنجح بعد في إنتاج رقم أساس**، لأن تشغيل Gradle مستحيل في هذه البيئة. أي جدول حالة يقول غير ذلك يكون مخالفًا لقاعدة «الدليل أولًا».

---

## 3) سجل الفشل/التعطّل (لا يُخفى)

| التاريخ | الأمر | الفشل |
|---|---|---|
| 2026-10-26 | `curl https://services.gradle.org/distributions/` | `000` — لا وصول (الشبكة مسموحة لـ GitHub فقط) |
| 2026-10-26 | `curl https://repo1.maven.org/maven2/` | `000` — لا وصول ⇒ لا تبعيات Android/Kotlin |
| 2026-10-26 | `which java` / `javac` | غير موجودين ⇒ لا يمكن تشغيل Kotlin/JVM محليًا |
| 2026-10-26 | `git push` (مع `.github/workflows/ci.yml`) | `refusing to allow a GitHub App to create or update workflow ... without workflows permission` ⇒ نُقل الملف إلى `ci/github-actions.yml` |
| 2026-10-26 (اكتشاف) | `VisionEngine.initializeOCR` تحت Robolectric | `TessBaseAPI()` يفشل بـ `UnsatisfiedLinkError` وهو `Error` لا `Exception` ⇒ كان سيُسقط كل اختبار وحدة. عُولج في D16 |

---

## 4) الملفات في هذا المجلد

| الملف | المحتوى |
|---|---|
| [`research.md`](research.md) | 🔒 بوابة R0: 12 مصدرًا + 11 اكتشافًا + قرارات D16…D19 + التوسيع + أوامر القياس |
| [`stats-selftest.log`](stats-selftest.log) | مخرج `scripts/test_stats.py` الفعلي |
| `gradle-test.log` · `eval-run.log` · `assemble.log` · `SUMMARY-after.md` · `results.json` | **لم تُنشأ بعد** — تُرفع تلقائيًا من CI (`if: always()`) عند التفعيل |
| [`failing-tests.md`](failing-tests.md) | سياسة تصنيف/معالجة الاختبارات الفاشلة (D6) + القالب الجاهز للتعبئة من أول تشغيل |
| `BASELINE` (خارج المجلد) | [`../../eval/BASELINE.md`](../../eval/BASELINE.md) يُكتب آليًا من أول تشغيل |

---

## 5) الخطوة التالية المطلوبة من صاحب المستودع (خطوة واحدة)

```bash
./scripts/install_ci.sh --commit && git push
```

بعدها يعمل `preflight → unit → eval → apk/guard`، وسيظهر:

1. نتيجة **452 اختبارًا** الفعلية (وبدلًا من التخمين: كل فاشل يُصنَّف في `failing-tests.md` بحسب D6).
2. رقم أساس حقيقي في `eval/BASELINE.md` + `SUMMARY.md` بفاصل ويلسون.
3. APK قابل للتحميل كـ artifact، وداخله نتيجة القياس في زر «تشخيص».

> إن تعذّر منح صلاحية `workflows`، البديل: لصق محتوى `ci/github-actions.yml` يدويًا في `.github/workflows/ci.yml` من واجهة GitHub (نفس المحتوى، 5 وظائف).
