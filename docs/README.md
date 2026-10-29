# وثائق زهرة — الإصدار 3.1 (غير منشور)

> **الحالة باختصار:** التنفيذ الوظيفي للمراحل 0–6 مكتمل، **لكن** لا يوجد APK منشور،
> ولا قياس كامل، ولا مراجعة بشرية للمحتوى، ولا تدقيق أمني مستقل. هذه الصفحة تفهرس
> ما هو متاح وما هو غير متاح، بدل الاكتفاء بوصف تسويقي.

## ابدأ من هنا

| إذا كنت… | اقرأ |
|---|---|
| مستخدمًا يريد التثبيت أو الاستخدام | [دليل الاستخدام](USAGE.md) |
| تريد معرفة ما لا يعمل قبل التجربة | [القيود المعروفة](LIMITATIONS.md) |
| باحثًا يريد أرقامًا | [النتائج](RESULTS.md) — لا توجد نتائج منشورة بعد |
| تريد إعادة إنتاج تقييم أو مقارنة | [بروتوكول التقييم](EVALUATION-PROTOCOL.md) |
| مهتمًا بالخصوصية | [الخصوصية](PRIVACY.md) |
| مهتمًا بالأمان والتوقيع | [الأمان](SECURITY.md) · [التوزيع](DISTRIBUTION.md) |
| تريد بطاقة نظام كاملة | [بطاقة النظام](SYSTEM-CARD.md) |
| مراجعًا للإصدار | [قائمة فحص الإصدار](RELEASE-CHECKLIST.md) |
| مساهمًا أو مالكًا | [ما بعد الإطلاق](POST-RELEASE.md) |

## حالة الإصدار

| البند | الحالة | الدليل |
|---|---|---|
| الإصدار | `versionName=3.1` · `versionCode=2` | `app/build.gradle.kts` · [المانيفست](../release/release_manifest.json) |
| القناة | **مصدر فقط (source-only)** — لا APK/AAB منشور | [التوزيع](DISTRIBUTION.md) |
| الاختبارات | اختبارات أندرويد (703 وسوم) **لم تُشغَّل** عبر Gradle/JUnit؛ اختبارات Python و7 اختبارات Kotlin لمنطق الإصدار نُفِّذت فعليًا | [RESULTS](RESULTS.md) · [سجل المرحلة 6](../evidence/phase-06/README.md) |
| القياس | **لم يُشغَّل** — لا `results.json` ولا فاصل ثقة منشور | [النتائج](RESULTS.md) |
| المقارنة العمياء | بروتوكول وأدوات جاهزة، **لم تُنفَّذ** | [بروتوكول التقييم](EVALUATION-PROTOCOL.md) |
| الأداء | **لم يُقَس** على جهاز | [الأداء](PERFORMANCE.md) |
| المحتوى | 181 وحدة، مولّدة آليًا بلا مراجعة بشرية (D31) | [القيود](LIMITATIONS.md) · [تدقيق التراخيص](../evidence/phase-05/license-audit.md) |
| التوقيع | إعدادات موجودة، **لم يُتحقق** توقيع إصدار | [التوزيع](DISTRIBUTION.md) |
| CI | ملف جاهز وإجراءاته مثبتة بـSHA، **غير مفعّل ولم يُشغَّل** | [الأمان](SECURITY.md) |

## مبدأ واحد

**الصدق عند الجهل قبل الذكاء عند الجواب.** أي رقم أو ادعاء بلا ملف نتيجة وأمر تشغيل
وتاريخ وهاش مجموعة = غير موثوق، ولا يُنشر هنا. القيود ليست فقرة في نهاية المستند؛
هي جزء من المنتج تظهر أيضًا داخل التطبيق من نفس مصدر البيانات.

## خريطة الملفات

```text
release/
  limitations.json        ← مصدر القيود الوحيد
  release_manifest.json   ← حالة الإصدار والقياس والتوزيع
  blind-protocol.example.json ← قالب تسجيل مسبق للمقارنة العمياء
docs/
  README.md  USAGE.md  LIMITATIONS.md  RESULTS.md
  EVALUATION-PROTOCOL.md  PERFORMANCE.md
  PRIVACY.md  SECURITY.md  DISTRIBUTION.md
  SYSTEM-CARD.md  RELEASE-CHECKLIST.md  POST-RELEASE.md
tools/phase06/
  blind_bench.py  device_perf.py  release_audit.py
app/src/main/assets/release_info.json ← مولّد للعرض داخل التطبيق
```

## تحديث هذه الوثائق

```bash
python3 tools/phase06/release_audit.py --write   # توليد LIMITATIONS + release_info.json
python3 tools/phase06/release_audit.py --check   # فحص التطابق والادعاءات والأسرار
python3 tools/test_phase06.py                    # 32 اختبار Python للأدوات
JDK4PY_PYTHON=/tmp/jdkvenv/bin/python \
  ./tools/offline-typecheck/phase06/run-tests.sh # 7 اختبارات Kotlin (بلا Gradle)
```

المدقق لا يثبت الأمان ولا الدقة ولا الأداء؛ يثبت أن الوثائق والحالة المعلنة
متسقتان مع ملفات المستودع.
