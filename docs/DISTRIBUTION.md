# التوزيع والإصدار — زهرة 3.1

## حالة التوزيع

| البند | الحالة |
|---|---|
| قناة الإصدار | **source-only** (مصدر فقط) |
| APK | **لا يوجد** — لم يُبنَ أو يُنشر |
| AAB | **لا يوجد** |
| Google Play | لا قائمة ولا حساب مطور معلن |
| F-Droid | لا وصفة بناء ولا مراجعة |
| GitHub Releases | لا إصدار منشور |
| التوقيع | إعدادات موجودة، **لم يُتحقق** أي توقيع إصدار |
| البناء قابل لإعادة الإنتاج | **لم يُتحقق** |
| CI | **غير مفعّل** |

إذا رأيت APK منسوبًا إلى زهرة على الإنترنت الآن، فهو **ليس إصدارًا رسميًا**
من هذا المستودع. لا تثبّته.

## ما الذي يمكن توزيعه اليوم؟

- الشيفرة المصدرية والوثائق في هذا المستودع.
- نسخة debug يبنيها المستخدم بنفسه للاختبار، مع كل تحذيرات [دليل الاستخدام](USAGE.md).
- لا حزم محتوى منفصلة للتحميل عند الطلب (D7/D11 لم يُحسما).

## التوقيع

### الإعداد الحالي

- `signingConfigs.release` تقرأ `KEYSTORE_PATH` و`STORE_PASSWORD` و`KEY_PASSWORD`
  من البيئة، والافتراضي `my-upload-key.jks` في جذر المشروع (غير موجود/غير متعقب).
- `signingConfigs.debugConfig` تستخدم `debug.keystore` بكلمة `android` — للاختبار فقط.
- لا مفاتيح ولا كلمات مرور في Git.

### خطوات الإصدار (لم تُنفَّذ هنا)

```bash
# 1) أنشئ keystore خارج المستودع (مرة واحدة، واحتفظ بنسخة آمنة)
keytool -genkeypair -v -keystore /secure/path/my-upload-key.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias upload

# 2) صدّر المتغيرات في جلستك (لا تكتبها في ملف متعقب)
export KEYSTORE_PATH=/secure/path/my-upload-key.jks
export STORE_PASSWORD='...'
export KEY_PASSWORD='...'

# 3) ابنِ release
./gradlew :app:assembleRelease --no-daemon

# 4) تحقق من التوقيع
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk

# 5) بصمة الشهادة للنشر
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk \
  | sha256sum
```

لا تُشغّل الخطوات أعلاه في بيئة مشتركة بلا تدقيق. لا يوجد APK ناتج في المستودع،
ولا يُرفع APK/AAB إلى Git (`.gitignore` يمنعه).

### ما يجب نشره مع أي APK

- `versionName` و`versionCode`.
- SHA-256 للملف.
- بصمة شهادة التوقيع (SHA-256).
- commit الكود والبناء.
- قائمة بالقيود المعروفة (رابط `docs/LIMITATIONS.md`).
- حالة القياس (غالبًا `not-run`).

## قابل لإعادة الإنتاج

**الحالة: لم يُتحقق.** لا يوجد بناءان متطابقان ولا مقارنة بايت-ببايت.

العوائق المعروفة:

- Gradle/MSBuild قد تحل تبعيات بترتيب/إصدارات مختلفة بلا lockfile.
- طوابع زمنية/ترتيب ZIP داخل APK.
- توقيع v2/v3 يغطي بايتات APK كاملة؛ يجب مقارنة البنية قبل التوقيع أو استخدام
  أدوات إعادة توقيع (مثل `apksigcopier`) كما تفعل F-Droid.
- اختلاف Build Tools/AGP/JDK/SDK.
- Firebase/Google services قد تُدخل قيمًا مولدة.
- لا Baseline Profile/توليد حتمي مضبوط.

قبل ادعاء «قابل لإعادة الإنتاج»:

1. ثبّت JDK/AGP/Gradle/SDK/Build Tools بالضبط.
2. أضف dependency verification/lockfiles.
3. ابنِ مرتين على مسارين نظيفين وقارن SHA-256.
4. استخدم `diffoscope` أو أداة مكافئة.
5. انشر الفرق إن وُجد؛ لا تخفِه.

## التحقق

للمستخدم/المراجع:

```bash
# هاش الملف
sha256sum app-release.apk

# توقيع صالح على كل إصدارات Android المدعومة
apksigner verify --verbose app-release.apk

# بصمة الشهادة (قارنها بالمنشورة)
apksigner verify --print-certs app-release.apk

# اسم الحزمة/الإصدار
aapt dump badging app-release.apk | head
```

لا تعتمد على «حجم الملف» أو «يعمل عندي» كتحقق. إذا لم تُنشر البصمة والهاش
رسميًا، لا يمكن التحقق من الأصالة.

## القنوات المحتملة (تحتاج قرار مالك)

| القناة | المتطلبات | الحالة |
|---|---|---|
| GitHub Releases | بناء موقّع، نشر الهاش/البصمة، سياسة تحديث | غير جاهزة |
| F-Droid | بناء قابل لإعادة الإنتاج، وصفة، مراجعة تراخيص | غير جاهزة |
| Google Play | حساب، سياسة خصوصية، Data safety، مراجعة | غير جاهزة |
| توزيع مباشر | توقيع/هاش/تحذيرات | غير جاهز |

لا يُفتح أي منها بلا قرار المالك (D7 وقناة التوزيع) وبلا فحص إصدار ناجح.

## الإصدارات والتوافق

- `versionName=3.1` و`versionCode=2` غير متسقين دلاليًا؛ سياسة الترقية غير معلنة.
- لا ضمان توافق خلفي لبيانات SharedPreferences بين الإصدارات غير المنشورة.
- لا قناة beta/nightly.
- تحديث التطبيق يتطلب نفس مفتاح التوقيع؛ لا توزّع نسخًا بمفاتيح مختلفة.
- حذف/إعادة تثبيت قد يفقد البيانات؛ لا مزامنة سحابية.

## الأسرار والامتثال

- لا أسرار في المستودع؛ يُفحص آليًا بـ`release_audit.py`.
- لا مراجعة قانونية للتراخيص/الخصوصية/التوزيع.
- لا امتثال مُثبت لGoogle Play Data Safety أو سياسات المتاجر.
- راجع [الأمان](SECURITY.md) و[الخصوصية](PRIVACY.md) قبل النشر.

## قائمة تحقق سريعة قبل النشر

- [ ] بناء release ناجح ومُختبر على جهاز.
- [ ] `apksigner verify` ناجح وبصمة شهادة منشورة.
- [ ] SHA-256 منشور.
- [ ] `docs/LIMITATIONS.md` محدّث ومولّد.
- [ ] `docs/RESULTS.md` يعلن الحالة الحقيقية (لا نتائج أو نتائج موثقة).
- [ ] `release/release_manifest.json` محدّث (channel/measurement/signed).
- [ ] `python3 tools/phase06/release_audit.py --check` ناجح.
- [ ] مراجعة أمنية/قانونية بشرية (غير موجودة حاليًا).
- [ ] قرار المالك D7 وقناة التوزيع.

القائمة الكاملة: [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).
