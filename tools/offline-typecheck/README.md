# تحقق بدون Gradle (offline typecheck)

> **السبب:** بيئات الوكلاء المعزولة قد لا تحتوي JDK ولا Android SDK، وقد يكون الوصول الشبكي محصورًا في GitHub (بلا `services.gradle.org`/`repo1.maven.org`). في هذه الحالة لا يمكن تشغيل Gradle ولو مرة — ومع ذلك لا يصحّ تسليم كود Kotlin بلا أي تحقق.

هذه الأداة تعطي **تحققًا جزئيًا صادقًا**:

| ما تفعله | ما لا تفعله |
|---|---|
| تترجم `app/src/test/java/com/example/eval/*.kt` + `DiagnosticsEngine.kt` فعليًا بـ `kotlinc` (أخطاء النوع/المراجع/الصياغة تُكتشف) | لا تشغّل Robolectric ولا أي كود أندرويد حقيقي |
| تُشغّل `EvalStats.kt` و`TextNorm`/`EvalScorer` منطقيًا وتقارن الأرقام بمرجع Python | لا تُشغّل `ZahraRouter` ولا تُنتج خط أساس |
| تُثبت أن تنفيذ Kotlin للإحصاء مطابق لتنفيذ Python (نفس القيم المرجعية) | لا تُغني عن `./gradlew :app:testDebugUnitTest` |

التبعيات (Gson، JUnit، Robolectric، Android، أصناف زهرة) ممثَّلة في `stubs/` **بتوقيعات محاكية** — لا تُستورد في بناء Gradle ولا يعرفها المشروع (خارج `app/src`). أي فرق بين stub والـ API الحقيقي لا تكتشفه هذه الأداة، ولذلك تبقى CI هي الحكم.

## التشغيل

```bash
pip install jdk4py                  # JDK
npm pack kotlin-compiler@2.4.21     # ثم: tar xzf kotlin-compiler-2.4.21.tgz -C /tmp/kc
./tools/offline-typecheck/run.sh    # KC_DIR=/tmp/kc/package افتراضيًا
```

المخرج الحقيقي (2026-10-26) محفوظ في [`evidence/phase-00/kotlin-logic-check.log`](../../evidence/phase-00/kotlin-logic-check.log):

```
wilson 60/100 = 0.502003 0.690599          # مطابق لجداول ويلسون المنشورة
mcnemar 0,5 = 0.062500                     # = 2/32
bootstrap = 0.2100 0.4000 deterministic=true
pct = 86.0% interval=75.0%–85.4%           # 180/223
numbers = [56.0, 1.5, 1.536, 1536.0]       # تطبيع أرقام عربية-هندية + علامة آلاف
norm = اهلا وسهلا                          # إزالة التشكيل + توحيد الهمزات
```
