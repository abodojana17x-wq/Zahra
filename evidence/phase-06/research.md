# بحث ما قبل التنفيذ — المرحلة 06

- الكود: `c0bccc39f8836331b01e3076727609fe5c10c9bb` · التاريخ: 2026-10-29 · الوكيل: «باب الحكم والإطلاق»
- ملف المرحلة: `plans/06-final-evaluation.md` · القرارات المرتبطة: D1، D2، D3، D5، D7، D14، D15، D20
- حالة التنفيذ: نُفّذت الأدوات والوثائق، **ولم يُشغَّل** قياس أو بناء أو CI (D20).

## لماذا بحث مختلف عن المرحلة 0؟

المرحلة 0 بنت أداة الإثبات: مجموعة مجمدة + هارنس + إحصاءات. المرحلة 6 لا تعيد
بناء ذلك، بل تضيف ما ينقص قبل أي إطلاق: **بروتوكول مقارنة أعمى ومحكّمين**،
**تحليل تحيّز الموضع والاتفاق**، **قياس أداء أندرويد**، **وثائق إفصاح عن القيود**،
**توقيع/إعادة إنتاج/أسرار**، و**بوابة تمنع نشر ادعاء بلا أثر**. البحث هنا يوسّع
المرحلة لا يؤكدها فقط.

## المصادر (≥ 5)

| # | المصدر | الرابط | تاريخ الاطلاع | الملخص (سطران) | موثوق؟ |
|---|---|---|---|---|---|
| 1 | Chiang et al., *Chatbot Arena: An Open Platform for Evaluating LLMs by Human Preference* (2024) | https://arxiv.org/abs/2403.04132 | 2026-10-29 | تقييم زوجي مجهول الهوية، ترتيب Bradley-Terry، وقياس تحيّز الموضع بإجراء حكمين لكل سؤال مع تبديل A/B. يقرّ بأن التقييم الجماهيري يختلف عن الخبراء (اتفاق 72–83%). | نعم — ورقة محكّمة/arXiv موثّقة |
| 2 | Shi et al., *Judging the Judges: A Systematic Study of Position Bias in LLM-as-a-Judge* (IJCNLP-AACL 2025) | https://aclanthology.org/2025.ijcnlp-long.18.pdf | 2026-10-29 | 15 محكّمًا و150 ألف حالة؛ تحيّز الموضع ليس عشوائيًا ويختلف بالمحكّم/المهمة، ويُقاس بـRepetition Stability وPosition Consistency وPreference Fairness. الفروق الصغيرة في الجودة هي الأخطر. | نعم — ACL Anthology |
| 3 | Bradley & Terry (1952), *Rank Analysis of Incomplete Block Designs* | https://doi.org/10.1093/biomet/39.3-4.324 | 2026-10-29 | الأساس لاحتمال الفوز اللوجستي بين نظامين، ومنه MLE؛ يسمح بترتيب بلا افتراض مقياس مطلق. | نعم — Biometrika |
| 4 | Efron (1979), *Bootstrap Methods: Another Look at the Jackknife* | https://doi.org/10.1214/aos/1176344552 | 2026-10-29 | إعادة أخذ مع الإبدال لتقدير توزيع الإحصاءة بلا افتراضات توزيعية؛ مناسب للوسيط وp95 والعينة الصغيرة، مع حدوده عند الذيل/الترابط. | نعم — Annals of Statistics |
| 5 | McNemar (1947), *Note on the sampling error of the difference between correlated proportions* | https://doi.org/10.1007/BF02295996 | 2026-10-29 | اختبار الفروق الزوجية على نفس الحالات عبر الأزواج المتنافرة b/c؛ الأنسب لمقارنة `passed` بين نظامين. | نعم — Psychometrika |
| 6 | Wilson (1927), *Probable Inference, the Law of Succession, and Statistical Inference* | https://doi.org/10.1080/01621459.1927.10502953 | 2026-10-29 | فاصل ثقة للنسبة لا ينهار عند p قرب 0/1، بخلاف Wald؛ الأنسب لعينة 50+50. | نعم — JASA |
| 7 | Dietterich (1998), *Approximate Statistical Tests for Comparing Supervised Classification Learning Algorithms* | https://doi.org/10.1162/089976698300017197 | 2026-10-29 | شرح اختبارات McNemar مقابل bootstrap/permutation وتحذير من انتهاك استقلال عينات التدريب؛ يدعم اختيار McNemar للمقارنة الزوجية. | نعم — Neural Computation |
| 8 | NIST, *AI Risk Management Framework: Generative AI Profile* (NIST AI 600-1, 2024) | https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf | 2026-10-29 | يطالب بتوثيق حدود التعميم والافتراضات والقيود والشفافية (MEASURE 2.5/2.8)، وببطاقات النظام/النموذج ضمن أدوات الشفافية. | نعم — وثيقة حكومية |
| 9 | Mitchell et al., *Model Cards for Model Reporting* (2019) | https://arxiv.org/abs/1810.03993 | 2026-10-29 | بنية بطاقة: تفاصيل، استخدام مقصود/خارج النطاق، عوامل، مقاييس، بيانات تقييم، اعتبارات أخلاقية، وقيود. لا تكتمل البطاقة بلا إفصاح الفشل. | نعم — ورقة FAT* معروفة |
| 10 | Android Developers, *Inspect app performance with Macrobenchmark* | https://developer.android.com/codelabs/android-macrobenchmark-inspect | 2026-10-29 | `MacrobenchmarkRule.measureRepeated` مع `StartupTimingMetric` و`StartupMode.COLD` وتكرارات؛ القياس المرجعي لبدء التطبيق وjank. | نعم — توثيق رسمي |
| 11 | Perfetto, *System tracing documentation* | https://perfetto.dev/docs/ | 2026-10-29 | تتبع نظامي لتحليل الجدولة/الإطارات/IO؛ أداة تشخيص لا تُغني عن قياس إحصائي. | نعم — مشروع Google مفتوح |
| 12 | Android Developers, *adb shell am* | https://developer.android.com/tools/adb#am | 2026-10-29 | `am start -W` يطبع ThisTime/TotalTime/WaitTime؛ فحص سريع يتأثر بحمل الجهاز، وليس بديلًا عن Macrobenchmark. | نعم — توثيق رسمي |
| 13 | Android Developers, *Back up user data with Auto Backup* | https://developer.android.com/identity/data/autobackup | 2026-10-29 | يوضح `allowBackup` و`dataExtractionRules`: غياب قسم `device-transfer` يعني تفعيل النقل لكل المحتوى افتراضيًا؛ لذا الاستثناء الصريح ضروري. | نعم — توثيق رسمي |
| 14 | Android Developers, *Sign your app* | https://developer.android.com/studio/publish/app-signing | 2026-10-29 | التوقيع إلزامي للتوزيع، ومفتاح الإصدار يجب أن يبقى آمنًا؛ يمكن التوقيع من Gradle بأخطاء env بدل كتابة كلمة المرور. | نعم — توثيق رسمي |
| 15 | F-Droid, *Reproducible Builds* | https://f-droid.org/docs/Reproducible_Builds/ | 2026-10-29 | التحقق يعتمد على توقيع APK ومقارنة البناء؛ v2/v3 يغطيان بايتات APK كاملة، لذا يجب تطابق البنية قبل التوقيع. | نعم — وثائق F-Droid |
| 16 | GitHub Docs, *Security hardening for GitHub Actions* | https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions | 2026-10-29 | أقل صلاحيات، تثبيت الإجراءات، عدم تشغيل كود fork بأسرار، وعدم تمرير أسرار في `run`. | نعم — توثيق GitHub |
| 17 | OWASP MASVS | https://mas.owasp.org/MASVS/ | 2026-10-29 | معايير تخزين/تشفير/شبكة/خصوصية لتطبيقات الموبايل؛ استُخدمت كقائمة أسئلة لا كشهادة. | نعم — OWASP |
| 18 | هذا المستودع: `git log` / `git show` | محلي | 2026-10-29 | تاريخ المستودع مضغوط في التزام واحد (`c0bccc3`)، ولا يوجد `4edbb18` ولا `ZAHRA_FUTURE_VISION_RESEARCH.md`. | نعم — دليل مستودع |

> ملاحظة منهجية: المصادر 1–2 تخص محكّمين بشريين/نماذج LLM؛ لا يوجد في زهرة
> محكّم LLM ولا نموذج توليدي، لذا استُخدمت للمبادئ (العمى/التبديل/الاتفاق) لا
> لتقليد LLM-as-judge. المصادر 10–16 توثيق رسمي، لا أدبيات محكّمة.

## الاكتشافات وتأثيرها

| الاكتشاف | الأثر على خطة المرحلة | القرار | Task جديد | اختبار جديد |
|---|---|---|---|---|
| Arena تستخدم حكمين لكل سؤال مع تبديل A/B، لا حكمًا واحدًا | إضافة `swapPositions` و`presentationsPerPair=2` وقياس ثبات الموضع | D33 | T6.1 | `test_schedule_is_balanced_and_deterministic` · `test_position_consistency_detects_same_winner_across_swap` |
| تحيّز الموضع يختلف بالمحكّم/المهمة وقد يقلب النتيجة عند فروق صغيرة | عدم الاكتفاء بنسبة؛ نشر ثبات الموضع وفوز الموضع الأول وkappa | D33 | T6.1/T6.2 | `test_first_position_bias_counts_only_decided_votes` · `test_kappa_perfect_and_chance_level` |
| BT لا Elo هو المناسب للترتيب؛ Elo يعتمد على الترتيب الزمني | BT أساسي وElo ثانوي معلن | D33 | T6.2 | `test_bradley_terry_orders_the_stronger_system` |
| bootstrap الأصوات المفردة يضيّق الفاصل عند ترابط المحكّمين | bootstrap عنقودي على مستوى الحالة | D33 | T6.2 | `test_analyze_reports_counts_and_disclaimer` |
| McNemar الدقيق مناسب للمقارنة الزوجية، مع تصحيح تعدد المجالات | إضافة `compare-results` وHolm | D33 | T6.2 | `test_compare_results_pairs_common_cases_only` · `test_holm_bonferroni_is_monotone` |
| `am start -W` فحص سريع لا قياس إصدار؛ Macrobenchmark مرجعي | وثيقة أداء تفصل الأدوات وتشترط ≥10 تشغيلات وMacrobenchmark للنشر | D32 | T6.3 | `test_summarize_is_descriptive_and_warns_without_provenance` · `test_summarize_flags_debug_and_mixed_states` |
| `allowBackup=false` وحده لا يكفي؛ غياب `device-transfer`/`cross-platform-transfer` يعني تفعيل النقل | إضافة استثناء صريح لكل النطاقات في الأوضاع الثلاثة وAPI ≤30 | D34 | T6.4 | `release_audit.check_manifest_and_backup` + `docs/PRIVACY` (لا اختبار Kotlin) |
| Firebase/Gemini و`metadata.json` يعلنان قدرة سحابية بلا إذن/استخدام | لا حذف آلي (قد يكسر البناء)؛ توثيق التناقض كقيد وتذكرة بعد الإطلاق | D32 | T6.5 | `test_full_repository_audit_passes` + LIM-022 |
| OCR بلا tessdata؛ ML Kit يعتمد على Play services | توثيق القيد ومنع ادعاء OCR/وجوه عامل | D32 | T6.5 | LIM-008/LIM-009 + فحص الوثائق |
| بعض SharedPreferences (مهام/مواعيد/مزاج/شخصية) غير مشفرة | عدم اعتماد «كل البيانات مشفرة»؛ تصحيح نسخ الواجهة وتوثيق التخزين | D32 | T6.4 | `docs/PRIVACY.md` + `release_audit.check_ui_claims` |
| نسخ الواجهة تحتوي «حماية كاملة»، «مشفر 100%»، «استخدام غير محدود» | فحص آلي للادعاءات المطلقة + إصلاح النصوص | D32 | T6.6 | `release_audit.check_ui_claims` + `test_full_repository_audit_passes` |
| نتائج CI غير موجودة، ونافذة التشخيص تدّعي نشرها | حذف الادعاء واستبداله بحالة حقيقية | D32 | T6.6 | `release_audit.check_ui_claims` |
| توثيق NIST/Model Cards يطلب حدودًا وافتراضات وفشلًا لا نجاحًا فقط | `LIMITATIONS.md` مولّد من مصدر واحد + بطاقة نظام + `POST-RELEASE` | D32 | T6.5 | `test_load_limitations_reads_real_source` · `test_generated_files_are_byte_stable` |
| F-Droid: v2/v3 يغطي بايتات APK كاملة، والتحقق يحتاج بناءين | لا ادعاء إعادة إنتاج؛ وثيقة تشرح العوائق وخطوات التحقق | D32 | T6.7 | `release_audit` (DISTRIBUTION + `signedReleaseVerified`) |
| GitHub Actions: تثبيت SHA وأقل صلاحيات وعدم تمرير أسرار | تثبيت كل الإجراءات بـcommit SHA و`persist-credentials: false`، ومدقق يرفض غير المثبت | D32 | T6.7 | `release_audit.check_ci` (SHA + persist-credentials) |
| `ZAHRA_FUTURE_VISION_RESEARCH.md` غير موجود؛ الالتزام 4edbb18 غير موجود | عدم اختلاق أفكاره؛ تسجيل الغياب كقيد والاعتماد على بحث المرحلة 6 | D32 | T6.8 | `release_audit.check_research` + LIM-024 |
| المجموعة 223+50 صغيرة، ولا محكّم مستقل بعد | لا نشر نسبة بلا فاصل؛ اعتبار التحليل استكشافيًا؛ تذكرة مراجعة مستقلة | D33 | T6.9 | LIM-023 |

## ما الذي استبعدناه ولماذا؟

- **LLM-as-judge:** لا نموذج سحابي ولا أوزان ولا إذن شبكة في النواة، وإدخال محكّم
  LLM يخلق تحيّزًا وتكلفة/شبكة تخالف Zero-LLM. يبقى المحكّم البشري المستقل هو
  الافتراضي؛ أي بديل يحتاج قرار مالك ووثيقة تحيّز.
- **Elo وحده:** يعتمد على ترتيب الأصوات ولا يعطي فاصلًا طبيعيًا؛ أُبقي ثانويًا.
- **مكتبة إحصاء جاهزة (scipy/statsmodels):** لا تبعيات خارجية في المستودع؛
  أعدنا استخدام `scripts/stats.py` المختبَر (Wilson/bootstrap/McNemar) وكتبنا
  BT/kappa/Holm بـstdlib. لا تُضاف تبعية بلا مبرر/شبكة.
- **Macrobenchmark module كامل:** يتطلب Gradle/Android SDK ووحدة `benchmark`
  جديدة؛ لم تُضف لتفادي كود غير قابل للبناء/التحقق. البروتوكول موثق، وأداة
  `device_perf.py` تعالج `am start -W` فقط.
- **F-Droid/Play publish:** قرار مالك وقناة غير محسومين؛ لا فتح قناة تلقائيًا.
- **حذف Firebase/Gemini/Google services:** قد يكسر البناء أو يخالف إعداد AI Studio؛
  وُثّق كقيد/تذكرة بدل تغيير غير قابل للتحقق.
- **إضافة tessdata:** ملفات ثنائية كبيرة برخصة/حجم غير مقيسين في D7؛ وُثّق القيد.
- **تشفير المهام/المواعيد/المزاج فورًا:** يغيّر صيغ تخزين ويحتاج ترحيلًا واختبار
  جهاز؛ لم يُنفّذ في مرحلة إطلاق/توثيق، وأُدرج P1 في `POST-RELEASE`.
- **تشغيل CI:** لم يُفعّل ولم تُشغَّل أي وظيفة؛ تثبيت الإجراءات بـSHA نُفّذ
  بنيويًا عبر GitHub API، لكنه لا يثبت بناءً أو أمانًا بلا تشغيل.
- **نشر أرقام من مصادر خارجية:** لا توجد نتائج زهرة؛ أي رقم خارجي لا يُنسب إليها.

## إيه اللي أضافه البحث للمرحلة؟ (قسم التوسيع)

### مهام جديدة

- **T6.1** جدول عروض متوازن وأصوات خام وبروتوكول تسجيل مسبق.
- **T6.2** تحليل: Wilson/bootstrap عنقودي/BT/McNemar/Holm/kappa/ثبات الموضع.
- **T6.3** أداة تحليل `am start -W` + بروتوكول Macrobenchmark/Perfetto.
- **T6.4** ضبط النسخ الاحتياطي/النقل وتصحيح ادعاءات التخزين/التشفير.
- **T6.5** مصدر قيود واحد + بطاقة نظام + تذكرة ما بعد الإطلاق.
- **T6.6** مدقق ادعاءات الواجهة وإصلاح النصوص المطلقة.
- **T6.7** مدقق أسرار/توقيع/CI/إعادة إنتاج + وثيقة توزيع.
- **T6.8** توثيق غياب ملف الرؤية بدل اختلاقه.
- **T6.9** التذكرة تشمل مراجعة مستقلة وقياسًا لاحقًا.

### حالات حدية جديدة (أُضيفت للاختبارات/المدقق)

1. محكّم يعطي نتائج متعارضة عند تبديل الموضع ⇒ `position_consistency` أقل من 1.
2. نظام بلا أي فوز ⇒ BT عند الحد؛ لا اختلاق قوة موجبة.
3. ملفان مختلفا الحالات ⇒ مقارنة على التقاطع فقط مع تحذير.
4. حالة `skipped` ⇒ استبعاد معلن من المقارنة لا تصفير/فشل.
5. `am start -W` بلا `LaunchState` ⇒ لا وصف COLD.
6. أقل من 10 تشغيلات ⇒ تقرير استكشافي.
7. حالة `allowBackup=false` بلا `device-transfer` ⇒ مدقق يفشل.
8. ادعاء «حماية كاملة» في الواجهة ⇒ مدقق يفشل.
9. مانيفست `measured` بلا ملف/هاش ⇒ مدقق يفشل.
10. قيد بلا دليل موجود ⇒ مدقق يفشل.

### تغيير معماري/قرارات

- **D32:** سياسة الصدق الإطلاقي (لا ادعاء بلا أثر؛ مصدر قيود واحد؛ قناة source-only).
- **D33:** بروتوكول التقييم الأعمى (تسجيل مسبق، تبديل، BT، bootstrap عنقودي، kappa).
- **D34:** استثناء كل النطاقات من النسخ الاحتياطي/النقل مع بقاء `allowBackup=false`،
  والاعتراف بأن السلوك لم يُختبر على جهاز.

### ما لم يُنفّذ رغم البحث

- **لم يُشغَّل** `gradlew` ولا JUnit/Robolectric ولا APK.
- **لم يُشغَّل** هارنس 223×8 ولا held-out ولا أي مقارنة عمياء.
- **لم يُقَس** أداء على أي جهاز/محاكي.
- **لم يُفعّل** CI ولم يُشغَّل، رغم تثبيت كل الإجراءات بـSHA.
- **لم تُراجع** الوثائق قانونيًا/أمنيًا/بشريًا.

## أوامر التحقق (منفّذة جزئيًا)

```bash
python3 tools/phase06/blind_bench.py selftest
python3 tools/phase06/device_perf.py selftest
python3 tools/phase06/blind_bench.py validate-protocol
python3 tools/phase06/release_audit.py --write
python3 tools/phase06/release_audit.py --check
python3 tools/test_phase06.py
python3 scripts/validate_eval.py --phase 06
python3 scripts/test_stats.py
```

النتائج الفعلية في [`README.md`](README.md) و`*.log`. أي أمر لم يُشغَّل مكتوب
صراحةً، ولا تُحوَّل «الأداة موجودة» إلى «القياس ناجح».

## مراجعة البحث

- [x] ≥ 5 مصادر (18 مصدرًا، منها 16 خارجية)
- [x] جدول «الاكتشافات وتأثيرها»
- [x] قسم توسيع يضيف مهام/حالات/قرارات
- [x] فصل ما استُبعد وما لم يُنفّذ
- [x] عدم اختلاق ملف/رابط/نتيجة
- توقيع المراجعة: وكيل المرحلة 6 — 2026-10-29 (مراجعة بشرية: **لا توجد**)
