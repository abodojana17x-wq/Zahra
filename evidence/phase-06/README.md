# سجل المرحلة 6 — الحكم والإطلاق

**الحالة: نُفّذت وظيفيًا، غير مختبرة على أندرويد/جهاز.** أُنجزت وثائق الإطلاق
والقيود والمقارنة العمياء وأدوات الأداء ومدقق الإطلاق، وصفحة «الاستخدام والحدود»
داخل التطبيق. **لم يُشغَّل** Gradle/JUnit/CI/APK، ولم تُجرَ أي مقارنة عمياء أو
قياس أداء. لا يوجد APK منشور، ولا نتيجة دقة.

## المخرجات

| المخرج | الملف | ملاحظة |
|---|---|---|
| بحث R0 | [`research.md`](research.md) | 18 مصدرًا، اكتشافات/توسيع/قرارات |
| مصدر القيود | `release/limitations.json` | 28 قيدًا بفئات/خطورة/دليل |
| مانيفست الإصدار | `release/release_manifest.json` | 3.1/2، source-only، القياس not-run |
| بروتوكول أعمى | `release/blind-protocol.example.json` | قالب غير مسجل |
| وثائق الإطلاق | `docs/` | 11 وثيقة + فهرس |
| سجل التغييرات | `CHANGELOG.md` | 3.1 غير منشور |
| قيود مولّدة | `docs/LIMITATIONS.md` | من المصدر، لا تُحرَّر يدويًا |
| أصل التطبيق | `app/src/main/assets/release_info.json` | نفس القيود للعرض |
| صفحة في التطبيق | `app/.../diagnostics/ReleaseInfo.kt` + `ui/screens/ReleaseInfoDialog.kt` | تُفتح من الإعدادات |
| هارنس أعمى | `tools/phase06/blind_bench.py` | BT/Elo/Wilson/bootstrap/McNemar/kappa |
| أداة أداء | `tools/phase06/device_perf.py` | تحليل `am start -W` |
| مدقق الإطلاق | `tools/phase06/release_audit.py` | نسخ/وثائق/أسرار/ادعاءات/قياس/backup |
| اختبارات | `tools/test_phase06.py` + `ReleaseInfoTest.kt` | 32 Python + 7 Kotlin (نُفّذت) |
| قواعد أمان | `app/src/main/res/xml/{data_extraction_rules,backup_rules}.xml` | استثناء كل النطاقات |
| تحديثات | README/plans/DECISIONS/CI/.gitignore | D32–D34 وحالة المرحلة |

## ما تحقق فعليًا

| الأمر | النتيجة الفعلية | حدود الدلالة |
|---|---|---|
| `python3 tools/phase06/blind_bench.py selftest` | PASS — بيانات اصطناعية | يختبر الحساب لا صحة تصميم ولا نتيجة زهرة |
| `python3 tools/phase06/blind_bench.py validate-protocol` | PASS بنيوي مع WARN `protocol-template` | القالب غير مسجل ولا يصلح للنشر |
| `python3 tools/phase06/device_perf.py selftest` | PASS — عينة اصطناعية | لا يشغّل adb ولا جهازًا |
| `python3 tools/phase06/release_audit.py --write` | يُنشئ LIMITATIONS + release_info.json | لا يثبت صحة المحتوى |
| `python3 tools/phase06/release_audit.py --check` | PASS/Fail حسب السجل أدناه | بنيوي فقط، لا أمان/أداء |
| `python3 tools/test_phase06.py` | PASS — 32 اختبارًا | اختبارات أدوات، لا Android |
| `python3 scripts/validate_eval.py --phase 06` | PASS — كل بوابات المرحلة 0 | R0 + تجميد، لا دقة |
| Gradle/JUnit/Robolectric/APK/جهاز | **لم يُشغَّل** | D20 لا يجعلها شرطًا، لكن لا ادعاء نجاح |
| هارنس 223×8/held-out/مقارنة عمياء | **لم يُنفّذ** | لا نتائج |
| Macrobenchmark/Perfetto/`am start -W` | **لم يُنفّذ على زهرة** | لا أرقام أداء |

### السجل الفعلي (2026-10-29)

```text
$ python3 tools/phase06/release_audit.py --write
النتيجة: 0 فشل · 0 تحذير
فحص الإطلاق نجح بنيويًا (لا يعني جاهزية نشر أو صحة محتوى/أمان/أداء).

$ python3 tools/phase06/release_audit.py --check
النتيجة: 0 فشل · 0 تحذير

$ python3 tools/phase06/release_audit.py --check --json build/reports/phase06/audit.json
INFO · ملخص JSON → build/reports/phase06/audit.json

$ python3 tools/test_phase06.py
Ran 32 tests in 0.206s — OK

$ JDK4PY_PYTHON=/tmp/jdkvenv/bin/python ./tools/offline-typecheck/phase06/run-tests.sh
PASS ReleaseInfoTest.parsesGeneratedShapeAndKeepsHonestStatus
PASS ReleaseInfoTest.groupsCategoriesAndSortsCriticalFirst
PASS ReleaseInfoTest.rejectsDuplicateLimitationIds
PASS ReleaseInfoTest.rejectsUnknownSchemaAndEmptyLimitations
PASS ReleaseInfoTest.rejectsMissingFieldsInsteadOfShowingEmptyList
PASS ReleaseInfoTest.allLimitationsHaveReadableText
—
نجح 7، فشل 0

$ python3 tools/phase06/blind_bench.py selftest
PASS · selftest على بيانات اصطناعية: BT واتجاه التفضيل وثبات الموضع متسقة.
INFO · هذه ليست نتيجة زهرة ولا تُنشر كقياس.

$ python3 tools/phase06/blind_bench.py validate-protocol
WARN · protocol-template: هذا قالب غير مسجل؛ لا يصلح لنشر نتيجة (وهذا متوقع الآن)
كل فحوصات البروتوكول البنيوية نجحت (لا يعني ذلك صحة التصميم العلمي).

$ python3 tools/phase06/device_perf.py selftest
PASS · selftest: تحليل عينة اصطناعية، تحذير n<10، وكشف الحالات المختلطة.
INFO · العينة اصطناعية للاختبار فقط وليست قياسًا لزهرة.

$ python3 scripts/validate_eval.py --phase 06
PASS · هاش eval/cases.jsonl مطابق للمجمّد
PASS · هاش eval/held-out.jsonl مطابق للمجمّد
PASS · research.md: 18 مصادر
PASS · research.md: جدول الاكتشافات موجود
PASS · research.md: قسم التوسيع موجود
كل فحوصات بوابة المرحلة 0 نجحت.

$ python3 tools/test_phase05_content.py
Ran 8 tests — OK

$ python3 tools/build_index.py --check
PASS · الفهرس المشحون مطابق لمحتوى packs/ (حارس النضارة)

$ python3 scripts/test_stats.py
كل اختبارات الإحصاء نجحت
```

**ما تعنيه هذه السطور:** أن أدوات Python والقيود والوثائق متسقة، وأن 7 اختبارات
Kotlin لمنطق `ReleaseInfo` تُرجمت ونُفّذت فعليًا بمترجم Kotlin 2.4.21/JDK 17
(عبر `jdk4py` في بيئة مؤقتة). **لا تعني** أن اختبارات أندرويد الـ703 تعمل، ولا
أن APK يُبنى، ولا أن أي نتيجة دقة/أداء/مقارنة عمياء موجودة.

## قرارات المرحلة

- **D32:** لا ادعاء مقاس بلا ملف/هاش/أمر/تاريخ؛ مصدر قيود واحد؛ القناة source-only
  حتى قرار المالك؛ تصحيح ادعاءات الواجهة المطلقة.
- **D33:** بروتوكول تقييم أعمى: تسجيل مسبق، تبديل موضع كامل، BT أساسي وElo ثانوي،
  bootstrap عنقودي، McNemar/Holm، kappa، ونشر الفشل.
- **D34:** `allowBackup=false` + استثناء كل النطاقات من `cloud-backup` و
  `device-transfer` (API 31+) و`full-backup-content` (API ≤30)، مع الاعتراف بأن
  السلوك لم يُختبر على جهاز/OEM.

القرارات مسجلة في [`plans/DECISIONS.md`](../../plans/DECISIONS.md).

## ما لم يُنفّذ / قيود باقية

- لا APK/AAB، لا توقيع إصدار، لا `apksigner verify`، لا هاش/بصمة منشورة.
- لا تشغيل لاختبارات Android (703 وسوم) ولا Compose ولا Robolectric.
- لا قياس دقة/أداء/ذاكرة/بطارية/حجم APK.
- لا محكّمين مستقلين ولا أصوات خام ولا مقارنة عمياء.
- لا مراجعة بشرية للمحتوى (D31) ولا مراجعة قانونية للتراخيص.
- لا تدقيق أمني/اختبار اختراق/fuzzing.
- Firebase/Gemini/metadata لم تُنظّف (LIM-022)؛ OCR بلا tessdata (LIM-008).
- مهام/مواعيد/مزاج/شخصية غير مشفرة بطبقة التطبيق (LIM-015).
- CI غير مفعّل ولم يُشغَّل؛ الإجراءات مثبتة الآن بـSHA و`persist-credentials: false`، لكن التثبيت البنيوي لا يثبت بناءً (LIM-020).
- لا اختبار وصول/TalkBack/RTL، ولا اختبار نسخ احتياطي/D2D.
- `ZAHRA_FUTURE_VISION_RESEARCH.md` غير موجود في تاريخ Git؛ لم يُختلق.

## الملفات المرتبطة

- [`../../docs/README.md`](../../docs/README.md) — فهرس الوثائق
- [`../../docs/LIMITATIONS.md`](../../docs/LIMITATIONS.md) — القيود الكاملة
- [`../../docs/RESULTS.md`](../../docs/RESULTS.md) — لا نتائج منشورة
- [`../../docs/POST-RELEASE.md`](../../docs/POST-RELEASE.md) — ZAHRA-POST-001
- [`../../plans/06-final-evaluation.md`](../../plans/06-final-evaluation.md) — تكليف المرحلة

## إعادة التوليد/الفحص

```bash
python3 tools/phase06/release_audit.py --write
python3 tools/phase06/release_audit.py --check
python3 tools/test_phase06.py
python3 scripts/validate_eval.py --phase 06
python3 tools/phase06/blind_bench.py selftest
python3 tools/phase06/device_perf.py selftest
python3 tools/phase06/blind_bench.py validate-protocol
```

لا يوجد أمر واحد يُنتج «نجاح المرحلة»: القياس يحتاج جهازًا/محكّمين، والبناء
يحتاج SDK، والمراجعة تحتاج بشرًا مستقلين.
