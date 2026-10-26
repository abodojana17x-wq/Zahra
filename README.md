# 🌸 زهرة (Zahra) — مساعد ذكي يعمل بالكامل على الجهاز

**Offline · Zero-LLM · Zero-Network (افتراضيًا) · Kotlin + Jetpack Compose**

> هذا الملف هو نقطة الدخول الوحيدة للمشروع. **كل المخططات والتقارير القديمة أُزيلت** واستُبدلت بخطة مرحلية واحدة من **10 مراحل (0–9)**، لكل مرحلة ملف مستقل يكلّف فئة متخصصة من الوكلاء ببذل أقصى جهد، وتبدأ بقاعدة بحث إلزامية: [`plans/README.md`](plans/README.md).

---

## 1) الحالة الصادقة (آخر تحديث: 2026-10-26)

| البند | الوضع الموثّق | الدليل |
|---|---|---|
| الكود | 87 ملف Kotlin إنتاجي + 33 ملف اختبار + 452 وسم `@Test` | `find app/src -name '*.kt' \| wc -l` |
| التنفيذ | `minSdk 24` · `targetSdk 36` · AGP 9.1.1 · Kotlin 2.2.10 · Gradle 9.3.1 | `app/build.gradle.kts` |
| الـ wrapper | **كان ناقصًا** (لا `gradlew` ولا `gradle-wrapper.jar`) — أُضيف في المرحلة 0 | `gradle/wrapper/gradle-wrapper.jar` + `gradlew` |
| الـ CI | الملف جاهز (5 وظائف) في `ci/github-actions.yml`، لكن `.github/workflows` **غير مفعّل بعد** لأن توكن التنفيذ لا يملك صلاحية `workflows` | `./scripts/install_ci.sh --commit` |
| الاختبارات | **لم يُشغَّل منها شيء بعد بأمر موثّق**: التقرير القديم قال `Tests Created, Execution Pending`، ولا يوجد سجل تشغيل منشور | سيتغيّر عند أول تشغيل CI (artifact باسم `junit-report`) |
| مجموعة القياس | **223 حالة + 50 held-out** مجمّدة بهاش | `eval/MANIFEST.sha256` · `eval/CHANGELOG.md` |
| أدوات القياس المُختبرة فعليًا | بوابة R0 + تحقق المجموعة + إحصاءات (Wilson/bootstrap/McNemar) تعمل وتنجح الآن | `python3 scripts/validate_eval.py --phase 00` · `python3 scripts/test_stats.py` |
| الإطلاق | لا يوجد APK منشور بعد؛ مسار البناء جاهز في CI (artifact باسم `zahra-debug-apk`) | بعد تفعيل CI |
| المحتوى | `general_kb.json` ≈ 3.6KB، `religion_kb.json` ≈ 11.9KB، `translation_kb.json` ≈ 5.8KB، و`intent_corpus.json` فيه 25 نية فقط | `app/src/main/assets/` |
| الحساب | `MathEngine` فيه 10 أزواج تحويل وحدات مثبتة يدويًا فقط | `app/src/main/java/com/example/zahra/engines/MathEngine.kt` |
| الفهم | `SemanticSearchEngine` يعتمد TF-IDF + cosine + مرادفات، **لا يوجد BM25 ولا embeddings** | المرحلة 3 |

**الخلاصة الاستراتيجية:** زهرة عمارة أكبر من منافسها المباشر (رقيم 2.0)، لكنها «غير مُثبتة». المرحلة 0 بَنَت **أداة الإثبات** (مجموعة أسئلة مجمّدة + هارنس 223×8 + إحصاءات عيّنة صغيرة + بوابة CI + زر تشخيص داخل التطبيق)، ولم تُثبت بعد تشغيل الـ 452 اختبارًا. لا يُنشر رقم قبل تشغيل حقيقي — التفصيل الصادق (المنفَّذ مقابل المنتظر) في [`evidence/phase-00/README.md`](evidence/phase-00/README.md).

---

## 2) البناء والتشغيل

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

> ⚠️ أي رقم أداء أو دقة يُذكر في أي ملف لازم يكون مصحوبًا بأمر التشغيل ومخرجاته. **ممنوع نشر «اختبارات معلّقة التنفيذ» مرة أخرى.**
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

## 4) مبدأ واحد لا يتغير

**الصدق عند الجهل قبل الذكاء عند الجواب.** أي رد بلا مصدر أو بلا أداة موثوقة = امتناع صريح + سؤال توضيح، لا قالب «عميق» مفبرك ولا ذاكرة متخيّلة.
