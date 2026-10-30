# المرحلة 7 — الوضع المتقدّم (النموذج التوليدي المحلي) · سجل التنفيذ

- **الحالة:** ✅ مكتمل التنفيذ وظيفيًا · ⛔ **غير مُشغَّل على جهاز/بدون قياس فعلي** (D20).
- الكود: `731309c` · الفرع: `arena/171762b8-zahra` · التاريخ: 2026-10-30.
- ملف المرحلة: [`plans/07-on-device-llm.md`](../../plans/07-on-device-llm.md) · القرارات: D7/D9/D10/D11/D1/D20.

## ما نُفِّذ (المخرجات الوظيفية)

| المخرج | المسار | الملف |
|---|---|---|
| سجل النماذج + معايير القبول/القائمة السوداء | A (D10/D11) | `app/src/main/java/com/example/zahra/advanced/ModelRegistry.kt` |
| طبقة تشغيل موحّدة معزولة عن النواة | B (D9) | `app/src/main/java/com/example/zahra/advanced/SlmRuntime.kt` |
| المولّد المُؤسَّد (استرجاع → حقن مصادر → توليد مقيد → فاحص ما بعد) | C (C.1–C.5) | `app/src/main/java/com/example/zahra/advanced/GroundedGenerator.kt` |
| بوابة تفعيل الوضع المتقدّم | التدفّق | `app/src/main/java/com/example/zahra/advanced/AdvancedModeGate.kt` |
| تسليم مشفّر + sha256 + زر حذف (Opt-in) | A.3 (D11) | `app/src/main/java/com/example/zahra/advanced/ModelDelivery.kt` |
| مجموعة قياس المقارنة بالنماذج الكبرى | — | `eval/frontier-set.jsonl` (50 حالة) |
| بحث ما قبل التنفيذ + هذا السجل | R0 | `evidence/phase-07/research.md`، `evidence/phase-07/README.md` |

الاختبارات السلوكية (JVM، بلا Robolectric): `app/src/test/java/com/example/advanced/Phase7AdvancedModeTest.kt`.

## القرارات المُتبنّاة (الخيارات المفضّلة من D9/D10/D11)

> **D9/D10/D11 ما زالت ⏳ بانتظار المالك.** التنفيذ يتبنّى الخيار المفضّل لكل منها دون ادّعاء حسمها:
- **D9 = B:** الوضع المتقدّم خلف `AdvancedModeGate` Opt-in، معزول تمامًا عن النواة (0–6) التي لا تُمس.
- **D10 = بحثي:** `ModelRegistry` يسرد المرشّحين من بحث R0 (2026) ويطبّق معايير القبول؛ الدرجات `arabicScore/egyptianScore` تُملأ من قياس فعلي على `frontier-set` (حاليًا `-1.0` = غير مقاسة).
- **D11 = B:** تنزيل عند الطلب + `sha256` + تخزين مشفّر (manifest) + زر حذف؛ لا وزن داخل APK الأساسي.

## القيود المعلنة (لا تُخفى)

1. **لا خلفية تشغيل حقيقية مدمجة.** لا `llama.cpp`/`MLC`/`MediaPipe` في `build.gradle.kts`. لذلك الوضع الإنتاجي الافتراضي هو `DisabledSlmRuntime` الذي يعيد «غير متاح» فيعيد زهرة فورًا للنواة الحتمية. **لا يحدث أي توليد فعلي الآن.**
2. **لا نموذج مُنزَّل.** `OfflineModelDelivery` معطّل بأمان (D1: أوفلاين افتراضيًا)؛ لا اتصال شبكة صامت. التحميل الفعلي يتطلب Opt-in صريحًا + ربط HTTP بقرار مالك.
3. **الدرجات العربية/المصرية غير مقاسة.** كل المرشّحين `PENDING_BENCHMARK`؛ لا يوجد مرشّح `ELIGIBLE` حتى يُشغَّل القياس على `frontier-set` (50 حالة) على جهاز حقيقي.
4. **تشفير الأوزان الكامل مؤجَّل.** `EncryptedModelStore` يشفّر manifest فقط؛ أوزان النموذج تُحفظ كملف مُحقَّق بـ sha256 (التشفير الكامل مقيد بالأداء لملفات بمئات MB–GB).
5. **النواة لا تُمس.** لا عتبات جديدة، ولا تعديل لـ `HybridRetriever`/`UnitRetrievalGate`/`DialogueResponseValidator`؛ الوضع المتقدّم فرع إضافي يعود للنواة عند أي عطل (تدهور رشيق).

## لم يُنفَّذ (بقرار صريح، لا كقصور)

- تشغيل Gradle/CI/APK/قياس أداء/`results.json` (أُلغي إلزامه بـ D20).
- بناء/ربط خلفية تشغيل فعلية (llama.cpp/GGUF أو MediaPipe/.task) — تُضاف خلف `SlmRuntime` لاحقًا.
- قياس `arabicScore/egyptianScore` الفعلي على `frontier-set` (يتطلب نموذجًا محمّلًا على جهاز).
- `git show 4edbb18:ZAHRA_FUTURE_VISION_RESEARCH.md` المشار إليه في الخطة **غير موجود** في تاريخ هذا المستودع؛ اعتمدنا على المراجع الموجودة فعليًا (`reference/raqim-2.0-plan.md` §8).

## طريقة التشغيل المستقبلي (عند موافقة المالك)

1. اختيار خلفية (`llama.cpp`/GGUF أو MediaPipe/.task) وإضافتها كتطبيق لـ `SlmRuntime`.
2. تثبيت نموذج مرشّح مكمّم (RightNow-0.5B أو Qwen2.5-1.5B) وتوفير رابط ثابت + `sha256` في `ModelRegistry`.
3. قياس `arabicScore/egyptianScore` على `eval/frontier-set.jsonl`؛ رفع الدرجات المقاسة لترقية المرشّح إلى `ELIGIBLE`.
4. تفعيل `OfflineModelDelivery` الفعلي خلف Opt-in صريح + `NetworkPolicy.OPT_IN_CONNECTED`.
