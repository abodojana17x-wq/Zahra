# النتائج — الحالة الصادقة

## الحالة الحالية

**لا توجد نتائج قياس منشورة لهذا الإصدار.** ليس «قيد النشر»، وليس «قريبًا»، وليس
رقمًا محفوظًا في مكان آخر. لم يُشغَّل أي من المسارات التالية في بيئة تحرير هذا
الإصدار:

| القياس | الأمر | الحالة |
|---|---|---|
| اختبارات الوحدة (703 وسوم) | `./gradlew :app:testDebugUnitTest --no-daemon` | **لم يُشغَّل** — لا Android SDK/تبعيات Gradle؛ اختبارات Kotlin المنطقية نُفِّذت منفصلة |
| هارنس القياس 223 × 8 | `./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest' --no-daemon` | **لم يُشغَّل** |
| مجموعة held-out (50) | نفس الهارنس مع `eval/held-out.jsonl` | **لم يُشغَّل** |
| مقارنة عمياء | `tools/phase06/blind_bench.py` | **لم تُنفَّذ** — لا محكّمين |
| أداء على جهاز | `docs/PERFORMANCE.md` | **لم يُقَس** |
| بناء APK موقّع | `./gradlew :app:assembleRelease` | **لم يُبنَ** |
| CI | `.github/workflows` | **غير مفعّل** |

لذلك لا يوجد `results.json` ولا `SUMMARY.md` ولا فاصل Wilson ولا McNemar ولا
Bradley-Terry لأي نظام. أي رقم يُنسب إلى زهرة خارج هذا الملف يجب أن يحمل:
**الأمر + التاريخ + commit + هاش المجموعة + الملف الخام**. غير ذلك = غير موثوق.

## لماذا لا نعرض رقمًا الآن؟

لأن نشر رقم بلا تشغيل فعلي يخالف مبدأ المشروع (D20) ويحوّل «اكتمال التنفيذ» إلى
«ادعاء نجاح». كذلك:

- المجموعة صغيرة (223 + 50)؛ نسبة وحدها بلا فاصل ثقة مضللة.
- كتابة الأسئلة ومراجعة النظام ليستا مستقلتين تمامًا (LIM-023).
- لا محكّم بشري مستقل ولا أصوات خام للمقارنة العمياء.
- بعض الحالات (الطقس/الشبكة) معلّمة `skipped`، ولا تدخل أي نسبة.
- النتيجة على محاكي أو جهاز واحد لا تثبت الأداء على API 24–36.

## كيف تُنتج نتيجة قابلة للنشر

### 1) شغّل المجموعة الأساسية

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest' --no-daemon
```

المخرجات المتوقعة:

```text
build/reports/zahra-eval/results.json
build/reports/zahra-eval/SUMMARY.md
```

### 2) أضف إحصاء العينة الصغيرة

```bash
python3 scripts/stats.py --from-results build/reports/zahra-eval/results.json
python3 scripts/stats.py 180 223          # مثال ويلسون فقط
python3 scripts/stats.py --mcnemar 4 11   # b/c بين إصدارين
```

### 3) قارن إصدارين بنفس الحالات

```bash
python3 tools/phase06/blind_bench.py compare-results \
  --a build/old/results.json --b build/new/results.json \
  --label-a old --label-b new \
  --out build/phase06/compare.json
```

### 4) نفّذ المقارنة العمياء (تحتاج محكّمين مستقلين)

```bash
# 1. تحقق من البروتوكول المسجل واملأ نسخة قبل رؤية النتائج
python3 tools/phase06/blind_bench.py validate-protocol \
  --protocol release/blind-protocol.example.json

# 2. أنشئ جدول العروض المتوازن
python3 tools/phase06/blind_bench.py schedule \
  --cases eval/held-out.jsonl --systems zahra baseline --seed 20261029 \
  --out build/phase06/schedule.jsonl

# 3. اجمع الأصوات الخام ثم حلّلها
python3 tools/phase06/blind_bench.py analyze \
  --schedule build/phase06/schedule.jsonl --votes build/phase06/votes.jsonl \
  --protocol release/blind-protocol.example.json --target zahra --ref baseline \
  --out build/phase06/analysis.json --markdown build/phase06/analysis.md
```

### 5) سجّل الحالة في المانيفست

لا تُغيّر `release/release_manifest.json` يدويًا إلا بعد وجود الملف الخام:

```json
{
  "measurement": {
    "status": "measured",
    "resultsFile": "build/reports/zahra-eval/results.json",
    "resultsSha256": "<sha256>",
    "lastRunAt": "2026-10-30T10:00:00Z",
    "command": "./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest' --no-daemon"
  }
}
```

ثم:

```bash
sha256sum build/reports/zahra-eval/results.json
python3 tools/phase06/release_audit.py --check
```

المدقق يفشل إذا أعلنت `measured` بلا ملف أو هاش أو أمر أو تاريخ، ويفشل إذا وُجد
`results.json` محلي والحالة `not-run`.

## قواعد النشر

1. **لا نسبة بلا فاصل ثقة**، ولا فاصل بلا حجم عينة ووحدة إعادة أخذ.
2. **كل الحالات تُنشر**، بما فيها الفشل و`skipped`؛ لا حذف صامت.
3. **McNemar** للمقارنات الزوجية على نفس الحالات، و**bootstrap عنقودي** للحالات
   المترابطة، وإذا تعددت المجالات تُصحح المقارنات (Holm–Bonferroni).
4. **المقارنة العمياء فقط** تُنشر كتفضيل؛ Elo ثانوي لأن ترتيب الأصوات يؤثر فيه.
5. **لا تعميم**: النتيجة تخص هذه المجموعة، هذا البروتوكول، هذا الجهاز/البناء.
6. **لا اختلاق**: إذا لم يُشغَّل شيء، يُكتب «لم يُشغَّل» كما في هذا الملف.
7. **المجموعة مجمدة**: أي تعديل على `eval/cases.jsonl` أو `eval/held-out.jsonl`
   يلزمه إصدار v2 وإعادة قياس كل المراحل ([eval/CHANGELOG.md](../eval/CHANGELOG.md)).

## قالب جدول نتيجة (لا يُملأ إلا بعد تشغيل)

```markdown
| المجال | ناجح/الكل | النسبة | Wilson 95% |
|---|---|---|---|
| ... | ... | ... | ... |
| **الإجمالي** | **...** | **...** | **...** |

- الأمر: `<الأمر>`
- التاريخ (UTC): `<YYYY-MM-DDTHH:MM:SSZ>`
- commit: `<sha>`
- هاش cases.jsonl: `<sha256>`
- هاش results.json: `<sha256>`
- الإصدار: `3.1 (2)`
- ملاحظات الفشل: `<عدد>` حالة، منها `<عدد>` skipped
```

## ما الذي يُغيّر هذه الصفحة؟

فقط تشغيل فعلي مع مخرجات محفوظة. تغيير النص أو تصفير الحالة أو «تقدير» رقم
ليس نتيجة. إن تغيّرت الحالة، يُحدَّث المانيفست والوثيقة في نفس التغيير، ويُحفظ
الملف الخام أو هاشه في سجل المرحلة 6.

## مراجع

- [بروتوكول التقييم](EVALUATION-PROTOCOL.md)
- [الأداء](PERFORMANCE.md)
- [القيود](LIMITATIONS.md)
- [سجل إصدارات المجموعة](../eval/CHANGELOG.md)
- [هارنس المرحلة 0](../app/src/test/java/com/example/eval/EvalReportTest.kt)
