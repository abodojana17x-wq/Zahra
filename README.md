# 🌸 زهرة (Zahra) — مساعد ذكي يعمل بالكامل على الجهاز

**Offline · Zero-LLM · Zero-Network (افتراضيًا) · Kotlin + Jetpack Compose**

> هذا الملف هو نقطة الدخول الوحيدة للمشروع. **كل المخططات والتقارير القديمة أُزيلت** واستُبدلت بخطة مرحلية واحدة من **10 مراحل (0–9)**، لكل مرحلة ملف مستقل يكلّف فئة متخصصة من الوكلاء ببذل أقصى جهد، وتبدأ بقاعدة بحث إلزامية: [`plans/README.md`](plans/README.md).

> ### حالة الإصدار 3.1 — اقرأ هذا أولًا
> **لا يوجد APK منشور، ولا نتائج قياس منشورة، ولا مراجعة بشرية للمحتوى.**
> القناة الحالية **source-only**. اختبارات أندرويد (703 وسمًا) مكتوبة ولم تُشغَّل عبر Gradle/JUnit هنا؛
> نُفِّذت فقط اختبارات Python و7 اختبارات Kotlin لمنطق معلومات الإصدار.
> القيود الكاملة (28 قيدًا) في [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md)،
> والحالة التفصيلية في [`docs/RESULTS.md`](docs/RESULTS.md).
> ابدأ من [`docs/README.md`](docs/README.md) · [الاستخدام](docs/USAGE.md) ·
> [الخصوصية](docs/PRIVACY.md) · [التوزيع](docs/DISTRIBUTION.md).

---

## سياسة التنفيذ (2026-10-29)

بطلب المالك، أُلغيت نهائيًا في جميع المراحل شروط CI وJUnit وAPK والـbaseline والقياسات وartifacts وبوابات القبول والانتقال المرتبطة بها. ليست مؤجلة إلى بيئة أخرى. لا يمنع غيابها بدء العمل أو اكتمال التنفيذ. التفاصيل: [plans/README.md](plans/README.md) و[D20](plans/DECISIONS.md).

الاختبارات وأدوات القياس والمجموعة المجمدة باقية دون تعديل. أوامر التشغيل أدناه اختيارية، وليست شروط إكمال. اكتمال التنفيذ لا يعني نجاح الاختبارات أو ثبوت دقة أو أداء، ولا يجوز اختلاق نتائج. البحث والأمان والخصوصية وقرارات المالك ما زالت سارية.

## 1) الحالة الحالية (آخر تحديث: 2026-10-29)

| البند | الوضع الموثّق | الدليل |
|---|---|---|
| الكود | 128 ملف Kotlin إنتاجي + 61 ملف اختبار (703 وسم `@Test` في 56 ملفًا) | `find app/src -name '*.kt'` · `grep -rn '@Test' app/src` |
| التنفيذ | `minSdk 24` · `targetSdk 36` · AGP 9.1.1 · Kotlin 2.2.10 · Gradle 9.3.1 | `app/build.gradle.kts` |
| الـ wrapper | **كان ناقصًا** (لا `gradlew` ولا `gradle-wrapper.jar`) — أُضيف في المرحلة 0 | `gradle/wrapper/gradle-wrapper.jar` + `gradlew` |
| الـ CI | الملف جاهز (5 وظائف) في `ci/github-actions.yml`، لكن `.github/workflows` **غير مفعّل بعد** لأن توكن التنفيذ لا يملك صلاحية `workflows` | `./scripts/install_ci.sh --commit` |
| الاختبارات | **لم يُشغَّل منها شيء بعد بأمر موثّق**: التقرير القديم قال `Tests Created, Execution Pending`، ولا يوجد سجل تشغيل منشور | سيتغيّر عند أول تشغيل CI (artifact باسم `junit-report`) |
| مجموعة القياس | **223 حالة + 50 held-out** مجمّدة بهاش | `eval/MANIFEST.sha256` · `eval/CHANGELOG.md` |
| أدوات القياس المُختبرة فعليًا | بوابة R0 + تحقق المجموعة + إحصاءات (Wilson/bootstrap/McNemar) تعمل وتنجح الآن | `python3 scripts/validate_eval.py --phase 00` · `python3 scripts/test_stats.py` |
| الإطلاق | لا يوجد APK منشور بعد؛ مسار البناء جاهز في CI (artifact باسم `zahra-debug-apk`) | بعد تفعيل CI |
| المحتوى | **المرحلة 5 (2026-10-29):** 181 وحدة معرفة في 11 حزمة + مدقق وتقرير تراخيص. المحتوى الجديد مولّد آليًا بموافقة المالك و**دون مراجعة بشرية** (D31)، وموسوم بذلك؛ بعض تراخيص المحتوى الموروث غير محسومة. | `packs/` · [`evidence/phase-05`](evidence/phase-05/README.md) |
| الحساب والوحدات | **المرحلة 2 (2026-10-29):** محلّل كامل (27 دالة + أسماء عربية + شجرة عمليات) ونظام وحدات عام (9 تصنيفات، فدان/قيراط) و9 قوالب مسائل كلامية؛ `MathEngine` صار Adapter متوافقًا | [`evidence/phase-02/README.md`](evidence/phase-02/README.md) |
| الوقت والبيانات | **المرحلة 2:** أدوات وقت/تاريخ بساعة قابلة للحقن + هجري أم القرى (ICU + جدول مدمج) + طبقة مصادر Opt-in (Open-Meteo/ويكيبيديا/ويكي‌داتا) بالرخصة والكاش | [`plans/02-tools.md`](plans/02-tools.md) |
| الفهم | **المرحلة 3 + تحديث المحتوى 5:** فهرس البناء الحالي يضم 181 وحدة في 11 حزمة؛ مسترجع **BM25+ / بصمات 3-gram / TF-IDF cosine** وبوابة `ANSWER/CLARIFY/ABSTAIN`. لا embeddings (D2/D26). | [`evidence/phase-03/README.md`](evidence/phase-03/README.md) · [`evidence/phase-05/README.md`](evidence/phase-05/README.md) |
| الحكم والإطلاق | **المرحلة 6 (2026-10-29):** وثائق إطلاق وقيود (28 قيدًا مولّدًا)، بطاقة نظام، بروتوكول تقييم أعمى، أدوات BT/Elo/bootstrap/McNemar/تحليل أداء، ومدقق إطلاق. **لا APK، ولا قياس، ولا CI مفعّل**؛ القناة source-only. | [`docs/README.md`](docs/README.md) · [`evidence/phase-06/README.md`](evidence/phase-06/README.md) |

**الخلاصة الاستراتيجية:** زهرة عمارة أكبر من منافسها المباشر (رقيم 2.0)، لكنها «غير مُثبتة». المرحلة 0 بَنَت **أداة الإثبات** (مجموعة أسئلة مجمّدة + هارنس 223×8 + إحصاءات عيّنة صغيرة + بوابة CI + زر تشخيص داخل التطبيق)، ولم تُثبت بعد تشغيل الاختبارات الـ703. لا يُنشر رقم قبل تشغيل حقيقي — التفصيل الصادق (المنفَّذ مقابل المنتظر) في [`evidence/phase-00/README.md`](evidence/phase-00/README.md).

---

## 2) أوامر بناء وتشغيل اختيارية

```bash
# 0) بوابة المرحلة 0 (تعمل بلا JDK ولا شبكة — Python فقط)
python3 scripts/validate_eval.py --phase 00     # بحث R0 + تجميد المجموعة + AC-0.7
python3 scripts/test_stats.py                   # Wilson/bootstrap/McNemar (مرجع مستقل)

# 1) كل الاختبارات (يحتاج JDK 21 + Android SDK)
./gradlew :app:testDebugUnitTest --no-daemon

# 2) هارنس القياس: 223 حالة × 8 تكرارات ⇒ build/reports/zahra-eval/{results.json,SUMMARY.md}
./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest' --no-daemon
#    خيارات: -PzahraEvalRepeats=4 -PzahraEvalSeed=42

# 3) APK للتجربة (ثم زر «تشخيص» في الإعدادات يعرض فحوصات حقيقية على الجهاز)
./gradlew :app:assembleDebug

# 4) اختبارات الجهاز (تحتاج Emulator) — لم تُشغَّل بعد في CI عمدًا
./gradlew :app:connectedDebugAndroidTest

# 5) تفعيل CI (مرة واحدة من صاحب المستودع — دفع .github/workflows يحتاج صلاحية workflows)
./scripts/install_ci.sh --commit && git push
```

> ⚠️ أي رقم أداء أو دقة يُذكر في أي ملف لازم يكون مصحوبًا بأمر التشغيل ومخرجاته. يُذكر بوضوح ما لم يُشغّل، دون حظر عبارة بعينها.
> 🧊 مجموعة الأسئلة مجمّدة بهاش (`eval/MANIFEST.sha256`): أي تعديل يلزمه إصدار v2 + إعادة قياس كل المراحل ([`eval/CHANGELOG.md`](eval/CHANGELOG.md)).

---

## 3) هيكل الخطة

| الملف | المحتوى |
|---|---|
| [`plans/README.md`](plans/README.md) | الخريطة الكاملة: 10 مراحل، القواعد الذهبية، الأبواب وفئات الوكلاء، جدول الحالة |
| [`plans/PRE-FLIGHT.md`](plans/PRE-FLIGHT.md) | 🔒 **قاعدة R0 الواجبة**: بحث إلزامي قبل تنفيذ أي مرحلة، مع قائمة أشياء معيّنة لكل مرحلة وقالب `research.md` |
| [`plans/DECISIONS.md`](plans/DECISIONS.md) | سجل القرارات D1…D15 (الشبكة، embeddings، النموذج المحلي، التكافؤ…) |
| [`plans/00-measurement.md`](plans/00-measurement.md) … [`plans/06-final-evaluation.md`](plans/06-final-evaluation.md) | **المسار الأساسي (الإثبات):** 7 ملفات مستقلة |
| [`plans/07-on-device-llm.md`](plans/07-on-device-llm.md) … [`plans/09-frontier-parity.md`](plans/09-frontier-parity.md) | **مسار التقارب:** نموذج محلي اختياري + استدلال وكيلي + قياس تكافؤ مع GPT · Claude · Grok · Gemini · DeepSeek |
| [`reference/`](reference/README.md) | خطة المنافس، تقرير قياسه، المقارنة، ونسخ HTML للدراسة فقط |
| [`evidence/`](evidence/README.md) | قواعد حفظ أدلة التنفيذ لكل مرحلة |

> 📌 **سقف صادق:** الهدف من المراحل 7–9 هو **تقليص فجوة مُقاسة** مع النماذج الكبرى في قدرات محددة، لا ادعاء المساواة بنماذج سحابية. النواة (0–6) تبقى Zero-LLM / Zero-Network، وأي نموذج محلي اختياري ومعزول خلف موافقة المستخدم.

---

## 4) الإطلاق والقيود — ما هو متاح فعلًا (المرحلة 6)

| السؤال | الجواب الصادق | المرجع |
|---|---|---|
| هل يوجد APK جاهز؟ | **لا.** القناة source-only؛ ابنِه بنفسك إن كنت مطورًا. | [`docs/DISTRIBUTION.md`](docs/DISTRIBUTION.md) |
| هل توجد نتائج دقة/أداء؟ | **لا.** لم يُشغَّل الهارنس ولا القياس ولا الأداء. | [`docs/RESULTS.md`](docs/RESULTS.md) |
| ما الذي لا يعمل؟ | 28 قيدًا موثقًا: محتوى بلا مراجعة بشرية، OCR بلا tessdata، تخزين جزئي غير مشفر، لا تدقيق أمني… | [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) |
| كيف أستخدمه؟ | بناء بالـwrapper، تثبيت debug، أذونات، حذف البيانات، استكشاف الأخطاء. | [`docs/USAGE.md`](docs/USAGE.md) |
| كيف يُقاس مستقبلًا؟ | بروتوكول مسجل مسبقًا، تبديل موضع، BT أساسي/Elo ثانوي، bootstrap عنقودي، McNemar/Holm. | [`docs/EVALUATION-PROTOCOL.md`](docs/EVALUATION-PROTOCOL.md) |
| ما وضع الخصوصية؟ | لا إذن إنترنت، تشفير جزئي، استثناء صريح للنسخ الاحتياطي/النقل (غير مُختبر على جهاز). | [`docs/PRIVACY.md`](docs/PRIVACY.md) |
| ما وضع الأمان/التوقيع؟ | لا تدقيق مستقل، لا APK موقّع، لا بناء قابل لإعادة الإنتاج متحقق، CI غير مفعّل. | [`docs/SECURITY.md`](docs/SECURITY.md) |

**تصفح الوثائق:** [الفهرس](docs/README.md) · [الاستخدام](docs/USAGE.md) · [القيود](docs/LIMITATIONS.md) · [النتائج](docs/RESULTS.md) · [التقييم](docs/EVALUATION-PROTOCOL.md) · [الأداء](docs/PERFORMANCE.md) · [الخصوصية](docs/PRIVACY.md) · [الأمان](docs/SECURITY.md) · [التوزيع](docs/DISTRIBUTION.md) · [بطاقة النظام](docs/SYSTEM-CARD.md) · [قائمة الفحص](docs/RELEASE-CHECKLIST.md) · [ما بعد الإطلاق](docs/POST-RELEASE.md).

```bash
# توليد القيود/أصل التطبيق وفحص الإطلاق (Python فقط، بلا Android SDK)
python3 tools/phase06/release_audit.py --write
python3 tools/phase06/release_audit.py --check
python3 tools/test_phase06.py
```

> ⚠️ نجاح المدقق يعني أن الوثائق/الملفات متسقة مع الحالة المعلنة، **لا** أن
> التطبيق آمن أو دقيق أو جاهز للنشر.

## 5) مبدأ واحد لا يتغير

**الصدق عند الجهل قبل الذكاء عند الجواب.** أي رد بلا مصدر أو بلا أداة موثوقة = امتناع صريح + سؤال توضيح، لا قالب «عميق» مفبرك ولا ذاكرة متخيّلة.
