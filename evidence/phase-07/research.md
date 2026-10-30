# بحث ما قبل التنفيذ — المرحلة 07 (باب النموذج التوليدي المحلي)

- الكود: `731309c` · الفرع: `arena/171762b8-zahra` · التاريخ: 2026-10-30
- الوكيل: «باب النموذج المحلي» (On-Device SLM Engineer) · ملف المرحلة: [`plans/07-on-device-llm.md`](../../plans/07-on-device-llm.md)
- القرارات المرتبطة: **D9** (السماح بنموذج توليدي محلي — Opt-in معزول)، **D10** (العائلة/الحجم/الترخيص)، **D11** (تنزيل عند الطلب)، **D7** (ميزانية الحجم)، **D1** (Zero-Network)، **D20** (إلغاء إلزام الإثبات).
- قرارات جديدة سُجّلت **قبل** الكود: **D35** (توضيح ميزانية حجم النموذج في المرحلة 7).

> **R0 نُفِّذت قبل أول سطر كود.** بدأت الجولة **داخل المستودع** (خطوة 3 في PRE-FLIGHT): مراجعة `plans/07-on-device-llm.md`، `DECISIONS.md` (D7/D9/D10/D11)، `reference/raqim-2.0-plan.md` (القسم 8 «سقف الطريقة»)، و`HybridRetriever`/`UnitRetrievalGate`/`DialogueResponseValidator`/`MemoryClaimGuard`/`ZahraCrypto`/`NetworkPolicy`. ثم بحث منشور أدناه (2026). لم يُعثر على `git show 4edbb18:ZAHRA_FUTURE_VISION_RESEARCH.md` في تاريخ هذا المستودع — الملف المشار إليه افتراضي في الخطة وغير موجود هنا، فاعتمدت على المراجع الموجودة فعليًا.

---

## 1) ما وجدته داخل المستودع قبل الإنترنت

| الاكتشاف (من الكود) | الأثر على التنفيذ |
|---|---|
| `HybridRetriever` + `UnitRetrievalGate` (المرحلة 3) يقدّمان استرجاعًا هجينًا وبوابة قرار بثلاثة مخارج | يُعاد استخدامهما كما هما في `GroundedGenerator` لحقن المصادر وفحص السند — لا تعديل للنواة |
| `DialogueResponseValidator.problem(text, hasGroundedSource)` + `unexplainedNumbers` + `numbers` + `MemoryClaimGuard` (المرحلة 4) | الفاحص «ما بعد التوليد» جاهز: تناقض/وعد طبي/ادعاء ديني/ذاكرة وهمية/أرقام غير مسندة |
| `ZahraCrypto` (AES-256-GCM، fail-closed) و`NetworkPolicy` (D1: أوفلاين افتراضيًا) | التخزين المشفّر للـ manifest والمعزل التام عن الشبكة وقت الاستدلال |
| `ConfidenceGatePolicy.KNOWLEDGE_MIN_RELEVANCE` (legacy) | أرضية القبول تُستعار لشفافية درجة الثقة في الرد المؤسَّس |
| `reference/raqim-2.0-plan.md` §8 «سقف الطريقة»: «لو عايز حوار حر حقيقي لازم نموذج توليدي ولو صغير على الجهاز… يُضاف كوضع متقدّم اختياري من غير ما يمس النواة» | هو التبرير المباشر للمرحلة؛ التنفيذ يلتزم «معزول + Opt-in + لا يمس النواة» |
| D9/D10/D11 **ما زالت ⏳ بانتظار المالك** | التنفيذ يتبنّى «الخيار المفضّل» لكل منها (B / بحثي / B) دون ادّعاء حسمها؛ تُوثَّف صراحةً في README المرحلة |

---

## 2) المصادر المنشورة (≥ 5)

| # | المصدر | الرابط | تاريخ الاطلاع | الملخص | موثوق؟ |
|---|--------|--------|---------------|--------|---------|
| 1 | On-device LLM inference on Android (Grokipedia) | https://grokipedia.com/page/On-device_LLM_inference_on_Android | 2026-03 | MediaPipe LLM Inference API (`com.google.mediapipe:tasks-genai`) يدعم Gemma-3 1B 4-bit وPhi-2 بصيغة `.task`؛ llama.cpp عبر JNI بصيغة GGUF؛ معايير 13–17 tok/s لـ Gemma-2B | نعم (مراجع تقنية محددة) |
| 2 | How to Run Local LLM in Android Studio (androidexperto) | https://androidexperto.com/how-to-run-local-llm-ai-in-android-studio/ | 2026-05 | جدول أوقات التشغيل: llama.cpp (GGUF)، TFLite (.tflite)، ONNX Runtime Mobile (.onnx)، MediaPipe؛ minSdk 26 عمليًا؛ نماذج 1B–3B مكمّمة | نعم |
| 3 | RightNow-Arabic-0.5B-Turbo (arXiv:2605.28827) | https://arxiv.org/abs/2605.28827 | 2026-04 | أصغر نموذج عربي متخصص (518M)؛ ~398MB Q4 عبر llama.cpp؛ 635 tok/s على H100 | نعم (ورقة علمية) |
| 4 | Best Arabic Local LLMs 2026 (promptquorum) | https://www.promptquorum.com/local-llms/best-arabic-local-llms-2026 | 2026-08 | Jais/ALLaM/AceGPT/Fanar: أحجام ورخص؛ Jais 13B/30B وALLaM 7B كبيرة للجوال؛ Fanar مغلق | نعم (تجميع، يُراجع قبل الاعتماد) |
| 5 | Best Local AI for Arabic (plugsky) | https://plugsky.com/articles/best-local-ai-for-arabic | 2026 | Qwen2.5 (Apache-2.0) أداء عربي قوي بأحجام 0.5B–72B؛ AceGPT/Jais عربي متخصص لكن أثقل | نعم |
| 6 | awesome-mobile-llm (GitHub) | https://github.com/ananasDDA/awesome-mobile-llm | — | قائمة أوقات تشغيل الجوال: llama.cpp، MLC-LLM (TVM)، MediaPipe، ONNX Runtime | نعم (مستودع مجتمعي) |

---

## 3) الاكتشافات وتأثيرها

| الاكتشاف | الأثر على خطة المرحلة | القرار | Task جديد | اختبار جديد |
|---|---|---|---|---|
| لا يوجد وقت تشغيل LLM مدمج في `build.gradle.kts` (لا llama.cpp/MLC/MediaPipe) | لا يمكن تشغيل نموذج فعليًا الآن | واجهة `SlmRuntime` معزولة + `DisabledSlmRuntime` افتراضي يعيد للنواة (تدهور رشيق)؛ تُضاف الخلفية الحقيقية لاحقًا بقرار مالك | T-7.1 | `DisabledSlmRuntime` unsupported |
| أفضل مرشّح مصري/عربي على جهاز متوسط = ≤3B Q4 (RightNow 0.5B ~398MB، Qwen2.5-1.5B ~1.1GB، Gemma-3 1B ~550MB) | `ModelRegistry` يحدّد مرشّحين ضمن الميزانية؛ النماذج 7B+ تُرفض تلقائيًا بالحجم (BLOCKED_SIZE) | سقف حجم افتراضي 1.5GB وRAM 2GB (D35)؛ Jais/ALLaM/AceGPT/Fanar خارج الميزانية أو مغلقة | T-7.2 | اختبارات `assess` للرفض/القبول |
| أي نموذج يطلب سحابة وقت الاستدلال يخرق Zero-Network (D1) | قائمة سوداء صريحة | `requiresNetworkForInference=true` ⇒ BLOCKED_CLOUD دائمًا | T-7.3 | اختبار السحابة المرفوضة |
| التوليد يجب أن يكون «مؤسَّدًا» ولا يهلوس فوق مكاسب الصدق (المرحلة 1) | `GroundedGenerator` يسترجع أولًا (أعلى 3) ويحقن المصادر + فاحص ما بعد التوليد | إعادة استخدام `HybridRetriever`+`DialogueResponseValidator`+`MemoryClaimGuard` بلا تعديل | T-7.4 | اختبارات: مصدر/سقوط/رقم غير مسند/ديني |
| التخزين المشفّر + sha256 + زر حذف (D11) | `ModelDelivery` + `Sha256Verifier` + `EncryptedModelStore` (manifest فقط) | الأوزان تُحفظ كملف مُحقَّق بـ sha256؛ التشفير الكامل للأوزان موثَّق كخطوة لاحقة بحد حجم | T-7.5 | `Sha256Verifier` |

---

## 4) ما الذي استبعدناه ولماذا؟

- **تضمين أي وزن نموذجي داخل APK:** يخرق D11 وD7 (النواة ≤60MB)؛ التنزيل عند الطلب فقط.
- **MLC-LLM / ExecuTorch كخلفية أولى:** يحتاجان سلاسل بناء TVM/PyTorch ثقيلة؛ أُبقي كخيار لاحق خلف الواجهة ذاتها.
- **نماذج 7B+ (Jais/ALLaM/AceGPT/Fanar-27B):** تتجاوز ميزانية الجوال (RAM/الحجم) ⇒ تُسجَّل في `ModelRegistry` لكن تُرفض تلقائيًا (BLOCKED_SIZE / BLOCKED_LICENSE).
- **Gemma Terms of Use كخيار أوّل:** رخصتها بها قيود على «تحسين نماذج أخرى»؛ تُركت كمرشّح مع ملاحظة، والأفضل Apache-2.0 (Qwen/RightNow).

---

## 5) إيه اللي أضافه البحث للمرحلة؟ (قسم التوسيع)

- **مهام جديدة:** T-7.1 (واجهة وقت تشغيل معزولة + تعطيل افتراضي)، T-7.2 (سجل نماذج + قبول/رفض)، T-7.3 (قائمة سوداء للسحابة)، T-7.4 (مولّد مؤسَّس + فاحص ما بعد التوليد)، T-7.5 (تسليم مشفّر + sha256).
- **حالات حدية جديدة (في `eval/frontier-set.jsonl`، 50 حالة):** تلخيص/إعادة صياغة/كتابة/شرح كود + محادثة حرة + حالات حسّاسة (ديني/طبي) موسومة `must_refuse` لإعادتها للنواة الآمنة.
- **قرار معماري جديد:** **D35** — توضيح ميزانية حجم النموذج (النواة ≤60MB لـ D7؛ النماذج الإضافية تُقاس كل على حدة بسقف افتراضي 1.5GB وRAM ≤2GB، بلا وزن في APK الأساسي).
- **قيد معلن:** لا توجد خلفية تشغيل حقيقية مدمجة ⇒ `DisabledSlmRuntime` هو الافتراضي، وأي «إجابة من النموذج» لا تحدث فعليًا حتى يُضيف المالك خلفية + ينزّل نموذجًا. لا ندّعي تشغيلًا أو دقة.

---

## 6) مراجعة البحث

- [x] ≥ 5 مصادر · [x] جدول قرارات · [x] قسم توسيع
- توقيع المراجعة: وكيل باب النموذج المحلي (تنفيذ وظيفي، غير مُشغَّل على جهاز/قياس).
