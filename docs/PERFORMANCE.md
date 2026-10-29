# الأداء على أندرويد — البروتوكول والحالة

## الحالة الحالية

**لم يُقَس أي مؤشر أداء على جهاز حقيقي أو محاكي.** لا يوجد زمن بدء، ولا p95،
ولا ذاكرة، ولا بطارية، ولا حجم APK. أداة [`tools/phase06/device_perf.py`](../tools/phase06/device_perf.py)
تحلل مخرجات `am start -W` إذا قدّمها المستخدم، لكنها لم تُشغَّل على زهرة.

| المؤشر | الحالة | الأداة المقترحة |
|---|---|---|
| بدء بارد/دافئ/حار | لم يُقَس | Macrobenchmark (`StartupTimingMetric`) |
| إطار أول محتوى (TTFD) | لم يُقَس | Macrobenchmark + Perfetto |
| jank/تقطيع | لم يُقَس | `FrameTimingMetric` / JankStats |
| p95 لزمن الرد داخل التطبيق | لم يُقَس | Harness المرحلة 0 + جهاز |
| الذاكرة/الـheap | لم يُقَس | Perfetto / Studio Profiler |
| البطارية | لم يُقَس | Battery Historian (اختياري، لا ادعاء) |
| حجم APK | لم يُقَس | `ls -lh app-debug.apk` |
| حجم الحزم/الفهرس | 181 وحدة، ملف الفهرس ≈323KB | قياس ملفات فقط، ليس أداءً |

## ما يُقاس

### تعريفات دقيقة

- **Cold start:** عملية التطبيق غير موجودة؛ يقاس حتى أول إطار مرسوم/محتوى جاهز.
- **Warm start:** العملية موجودة والنشاط يُعاد إنشاؤه.
- **Hot start:** النشاط موجود في المقدمة ويُعاد إظهاره.
- **p95:** مئين 95 لزمن الرد عبر تشغيلات/حالات، لا «متوسط» يتجاهل الذيل.
- **jank:** إطارات تتجاوز ميزانية الإطار (16.67ms على 60Hz، 8.33ms على 120Hz).

### ما لا يُقاس هنا

- لا قياس استهلاك بطارية مزعوم من «تشغيل يدوي» قصير.
- لا مقارنة مع نماذج سحابية على زمن الشبكة (خارج نطاق Zero-Network).
- لا تعميم من محاكي إلى هاتف ضعيف.
- لا قياس ضغط/حمل متزامن؛ التطبيق محلي وأحادي المستخدم في الغالب.

## البروتوكول

### 1) مصفوفة الأجهزة

| الفئة | مثال | لماذا |
|---|---|---|
| أدنى دعم | API 24 (Android 7.0)، RAM 2GB | التحقق من `minSdk 24` والسلوك القديم |
| وسط | API 31 (Android 12) | قواعد النسخ الاحتياطي/النقل والأذونات |
| حديث | API 34 | TTS/إشعارات/منبهات |
| أحدث | API 36 | `targetSdk 36` |
| جهاز ضعيف | 2–3GB RAM | أخطر حالة واقعية |

يُسجَّل لكل جهاز: الطراز، إصدار Android، API، المعالج، RAM، حالة الحرارة،
وضع الطاقة، وحالة بدء التطبيق.

### 2) Macrobenchmark (المرجع)

Macrobenchmark هو القياس المرجعي لبدء التطبيق وjank؛ يعيد التشغيل ويتحكم في
`StartupMode` و`CompilationMode`. مثال مرجعي (يُضاف في وحدة benchmark منفصلة
عند تنفيذ القياس؛ **لم يُضف هنا**):

```kotlin
@RunWith(AndroidJUnit4::class)
class ZahraStartupBenchmark {
  @get:Rule val rule = MacrobenchmarkRule()

  @Test
  fun coldStart() = rule.measureRepeated(
    packageName = "com.aistudio.zahra.kpxq",
    metrics = listOf(StartupTimingMetric()),
    iterations = 10,
    startupMode = StartupMode.COLD,
    compilationMode = CompilationMode.DEFAULT,
  ) {
    pressHome()
    startActivityAndWait()
  }
}
```

قواعد:

- ≥ 10 تكرارات (30 مستحسن) بعد 3–5 إحماء.
- `CompilationMode.DEFAULT` مع Baseline Profile يمثل المستخدم، و`None` يمثل
  أسوأ حالة؛ يُنشر أي وضع استُخدم.
- لا تقيس أثناء تحديث/فهرسة/حرارة عالية.
- احفظ `benchmarkData.json` والتتبعات.

### 3) `am start -W` (فحص سريع فقط)

```bash
adb shell am force-stop com.aistudio.zahra.kpxq
adb logcat -c
adb shell am start -W -n com.aistudio.zahra.kpxq/com.example.MainActivity \
  | tee run01.txt
```

كرّر ≥ 10 مرات، ثم:

```bash
python3 tools/phase06/device_perf.py parse --input run*.txt \
  --api-level 34 --android-version 14 --device "Pixel 6" --build-type release \
  --commit <sha> --out build/phase06/startup.json \
  --markdown build/phase06/startup.md
```

قيود الأداة المعلنة:

- `am start -W` يتأثر بحمل الخلفية، ولا يقيس أول محتوى مفيد.
- إذا غاب `LaunchState` فلا يجوز وصف القياس بـCOLD.
- أقل من 10 تشغيلات = استكشافي لا منشور.
- الأداة تحذر عند اختلاط COLD/WARM/HOT أو غياب build type/commit.

### 4) Perfetto (تشخيص)

```bash
adb shell perfetto -o /data/misc/perfetto-traces/zahra.pftrace -t 10s \
  sched freq idle am wm gfx view binder_driver
adb pull /data/misc/perfetto-traces/zahra.pftrace
```

افتح التتبع في [Perfetto UI](https://ui.perfetto.dev) واربطه بالحالة المرصودة.
Perfetto للتشخيص، لا يُعوّض القياس الإحصائي.

### 5) داخل التطبيق

هارنس المرحلة 0 يسجل `latency_ms_median` لكل حالة. عند تشغيله:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.eval.EvalReportTest' --no-daemon
```

هذا زمن على JVM/جهاز اختبار، لا زمن تجربة المستخدم النهائية. لا يُنشر كـp95
للتطبيق بلا سياق.

## القيود

- لا جهاز ولا محاكي ولا Android SDK في بيئة تحرير هذا الإصدار؛ كل الأرقام غائبة.
- لا Baseline Profile مُولّد/مُختبر.
- لا قياس لحجم APK الفعلي أو أثر الحزم والتبعيات (Firebase/ML Kit/Tesseract).
- لا قياس استهلاك بطارية أو ذاكرة أو حرارة.
- لا اختبار شبكة/طيران لأن التطبيق بلا إذن شبكة، لكن أدوات Opt-in غير قابلة للعمل.
- القياس على محاكي لا يمثل هاتفًا ضعيفًا أو OEM مختلفًا.
- لا `connectedDebugAndroidTest` ولا اختبار واجهة Compose.

## ما يُنشر لاحقًا

عند التنفيذ، يُنشر: الجهاز/الإصدار، البناء وcommit، الأمر، عدد التشغيلات،
الوسيط/p95/فاصل bootstrap، التتبع الخام أو هاشه، وكل تحذير. لا يُنشر «تحسن»
بلا مقارنة على نفس الجهاز ونفس الحالات. راجع [النتائج](RESULTS.md) لقواعد النشر.

## مراجع خارجية

- [Macrobenchmark](https://developer.android.com/studio/profile/macrobenchmark)
- [Perfetto](https://perfetto.dev/docs/)
- [`adb shell am`](https://developer.android.com/tools/adb#am)
- [App startup time](https://developer.android.com/topic/performance/vitals/launch-time)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
