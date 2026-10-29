# سجل التغييرات

كل ما هو مذكور هنا يخص **الشيفرة والوثائق**، لا نتائج قياس. لا يوجد إصدار
منشور بعد.

## [3.1] — غير منشور

**التاريخ:** 2026-10-29 · **versionCode:** 2 · **القناة:** source-only

### أُضيف

- **المرحلة 6 — الحكم والإطلاق:** وثائق استخدام/قيود/نتائج/تقييم/أداء/خصوصية/
  أمان/توزيع/بطاقة نظام/قائمة فحص/تذكرة ما بعد الإطلاق.
- مصدر قيود واحد `release/limitations.json` (28 قيدًا) يُولّد منه
  `docs/LIMITATIONS.md` و`app/src/main/assets/release_info.json`.
- صفحة «الاستخدام والحدود» داخل التطبيق تعرض القيود الحقيقية وحالة القياس
  والقناة بدل رسالة Toast عامة.
- `release/release_manifest.json` لحالة الإصدار/القياس/التوزيع.
- قالب تسجيل مسبق للمقارنة العمياء `release/blind-protocol.example.json`.
- `tools/phase06/blind_bench.py`: جدول عروض متوازن، Bradley-Terry/Elo،
  Wilson/bootstrap عنقودي، McNemar، ثبات الموضع، اتفاق المحكّمين.
- `tools/phase06/device_perf.py`: تحليل `am start -W` مع تحذيرات n<10/حالات مختلطة.
- `tools/phase06/release_audit.py`: مدقق نسخ/وثائق/أسرار/ادعاءات/قياس/نسخ احتياطي.
- `tools/test_phase06.py` واختبارات Kotlin لمعلومات الإصدار.
- استثناء صريح لكل النطاقات من النسخ الاحتياطي والنقل بين الأجهزة (API 31+ و≤30).
- تحديث README/plans/evidence وسجل القرارات D32–D34.
- إنشاء تذكرة ما بعد الإطلاق ZAHRA-POST-001 (issue #16).
- تثبيت كل إجراءات CI بـcommit SHA كامل + `persist-credentials: false`
  (لم يُفعّل CI ولم يُشغَّل؛ الملف باقٍ في `ci/`).

### تغيّر

- رسائل الواجهة المطلقة («حماية كاملة»، «مشفر 100%»، «استخدام غير محدود»)
  استُبدلت بصياغة قابلة للإثبات.
- نافذة التشخيص لم تعد تدّعي نشر نتائج CI غير موجودة.
- `.gitignore` يحمي keystores/keystore.properties.

### لم يُشغَّل

- Gradle/JUnit/Robolectric/بناء APK أو AAB.
- هارنس 223×8 أو held-out أو أي مقارنة عمياء.
- قياس أداء أو جهاز أو TalkBack/RTL.
- CI (الملف موجود في `ci/` وغير مفعّل ولم تُشغَّل وظائفه).
- Gradle/JUnit/Robolectric لاختبارات أندرويد الـ703.
- أي جهاز/محاكي أو `adb` أو Macrobenchmark/Perfetto.

### تحقق فعلي محدود

- `python3 tools/test_phase06.py`: **32 اختبارًا ناجحًا**.
- `tools/offline-typecheck/phase06/run-tests.sh`: **7/7 اختبارات Kotlin ناجحة**
  (kotlinc 2.4.21 من npm + JDK 17 عبر `jdk4py`، بلا Gradle/Android SDK).
- `python3 tools/phase06/release_audit.py --check`: **0 فشل · 0 تحذير**.
- `python3 scripts/validate_eval.py --phase 06` و`tools/test_phase05_content.py`
  و`tools/build_index.py --check` و`scripts/test_stats.py`: ناجحة.
- هذا تحقق أدوات/وثائق، **ليس** تحققًا لمنطق التطبيق كاملًا ولا لأداء/دقة.

### قيود معروفة

راجع [docs/LIMITATIONS.md](docs/LIMITATIONS.md). الأهم: لا نتائج قياس منشورة،
لا APK، المحتوى مولّد آليًا بلا مراجعة بشرية، بعض البيانات غير مشفرة، ولا تدقيق
أمني/قانوني مستقل.

## ما قبل 3.1

المراحل 0–5 نُفّذت وظيفيًا في تغييرات سابقة: مجموعة قياس مجمدة وهارنس، أمانة
وتوجيه، أدوات حساب/وحدات/وقت/هجري، استرجاع هجين، حوار وإحالات وذاكرة، ومحتوى
181 وحدة. التفاصيل في `evidence/phase-00` … `evidence/phase-05` و
`plans/DECISIONS.md`.

> لا يوجد رقم دقة أو أداء في هذا السجل لأنه لم يُشغَّل قياس. أي رقم يُضاف لاحقًا
> يلزمه أمر وتاريخ وcommit وهاش مجموعة وملف خام.
