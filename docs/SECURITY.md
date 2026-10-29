# الأمان — زهرة 3.1

> **لا يوجد تدقيق أمني مستقل ولا اختبار اختراق ولا fuzzing لهذا الإصدار.**
> هذه الوثيقة تصف نموذج التهديد والضوابط الموجودة والثغرات المعروفة، ولا تدّعي
> أن التطبيق «آمن». غياب حادث معروف لا يساوي إثبات أمان.

## نموذج التهديد

### ما نحاول حمايته

- محتوى المحادثات والذاكرة والملاحظات.
- بيانات المهام والمواعيد والمزاج (غير مشفرة بطبقة التطبيق).
- مفاتيح التوقيع وأسرار البناء (خارج المستودع).
- سلامة الحزم/الفهرس والشيفرة الموزعة.

### خصوم محتملون

| الخصم | القدرة | الضابط الحالي | الفجوة |
|---|---|---|---|
| تطبيق خبيث على الجهاز | استدعاء مكونات/قراءة ما يسمح به النظام | `exported=false` للـreceivers الداخلية، صلاحيات أندرويد | لا عزل كامل ضد root/إمكانية وصول خاصة |
| سارق الجهاز | فتح التطبيق/نسخ الملفات | تشفير بعض البيانات + قفل أندرويد | بيانات غير مشفرة ونسخ احتياطي غير مُتحقق |
| مهاجم سلسلة توريد | التلاعب بتبعية/إجراء CI | `contents: read`، لا أسرار، إجراءات مثبتة بـSHA، `persist-credentials: false` | CI غير مفعّل/غير مُشغَّل، Gradle بلا lockfile تحقّق |
| مهاجم بيانات/محتوى | إدخال نص خبيث في حزمة/سؤال | مدقق حزم، لا تنفيذ كود، لا `eval` | لا fuzzing ولا مراجعة بشرية للمحتوى |
| مهاجم بناء | سرقة keystore/كلمة مرور | متغيرات بيئة، `.gitignore`، مدقق أسرار | لا تدقيق CI ولا توقيع موثّق |

### خارج النطاق

- حماية الجهاز المروّت (root) أو نسخ نظام كاملة.
- هجمات القنوات الجانبية على AndroidKeyStore.
- خدمة سحابية (لا توجد).
- ضمان صحة المحتوى الديني/الصحي (راجع [القيود](LIMITATIONS.md)).

## الضوابط الموجودة

- **Zero-Network:** لا إذن `INTERNET`/`ACCESS_NETWORK_STATE`؛ `usesCleartextTraffic=false`.
- **تشفير محلي:** AES-256-GCM وAndroidKeyStore للأرشيف/الذاكرة (افتراضي)/الملاحظات،
  fail-closed بلا رجوع لنص واضح.
- **مكونات مصدّرة محدودة:** `MainActivity` فقط + receiver تمهيد محمي بـ
  `RECEIVE_BOOT_COMPLETED`؛ منبهات داخلية `exported=false`.
- **نسخ احتياطي مقيد:** `allowBackup=false` + استثناء صريح لكل النطاقات من
  `cloud-backup` و`device-transfer`.
- **لا أسرار في المستودع:** مدقق `release_audit.py` يفحص أسماء/محتوى الملفات،
  و`.gitignore` يحمي `.env` وkeystores.
- **بيانات غير موثوقة لا تُنفّذ:** `MiniJson` بلا `eval`/انعكاس، والقصاصات
  البرمجية لا تُشغَّل (D3 معلّق).
- **مدقق إطلاق:** يمنع نشر «نتيجة» بلا ملف/هاش/أمر/تاريخ، ويفحص الادعاءات
  المطلقة في الواجهة.

## الأسرار والتوقيع

### الموجود

`app/build.gradle.kts`:

```kotlin
signingConfigs {
  create("release") {
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    storeFile = file(keystorePath)
    storePassword = System.getenv("STORE_PASSWORD")
    keyAlias = "upload"
    keyPassword = System.getenv("KEY_PASSWORD")
  }
  create("debugConfig") {
    storeFile = file("${rootDir}/debug.keystore")
    storePassword = "android"
    keyAlias = "androiddebugkey"
    keyPassword = "android"
  }
}
```

- كلمة مرور الإصدار تأتي من متغيرات بيئة؛ **لا تُكتب في المستودع**.
- كلمة مرور debug (`android`) هي الافتراضية لأدوات Android، وليست سرًا إنتاجيًا؛
  لا يجوز استخدام debug keystore لإصدار.
- لا يوجد APK موقّع ولا بصمة شهادة منشورة؛ `signedReleaseVerified=false`.

### قواعد

1. لا ترفع `my-upload-key.jks` أو `keystore.properties` أو `.env` أو أي مفتاح.
2. لا تطبع الأسرار في السجلات، ولا تمررها كوسائط سطر أوامر إن أمكن.
3. استخدم أسرار بيئة/CI محمية، ودوّرها عند الاشتباه.
4. احتفظ بنسخة احتياطية آمنة خارج المستودع؛ فقدان مفتاح الإصدار يمنع التحديث.
5. لا توقّع بإعدادات debug للإصدار.
6. راجع [التوزيع](DISTRIBUTION.md) لخطوات التحقق.

## سلسلة التوريد

| البند | الحالة | الحد |
|---|---|---|
| Gradle Wrapper | موجود | لم يُشغَّل هنا |
| تبعيات Maven | نسخ محددة في `libs.versions.toml` | لا dependency verification/lockfile |
| Firebase/Gemini | تبعيات غير مستخدمة + بيانات وصفية مضللة | LIM-022 |
| Tesseract/ML Kit | مكتبات أصلية/خدمات | OCR بلا tessdata؛ ML Kit يعتمد على Play services |
| GitHub Actions | ملف CI موجود وإجراءاته مثبتة بـSHA كامل | **غير مفعّل وغير مُشغَّل**؛ لم يُثبت أي بناء |
| توقيع إصدار | إعدادات فقط | لا بناء موقّع مُتحقق |
| بناء قابل لإعادة الإنتاج | غير مُتحقق | LIM-019 |

ما نُفّذ في المرحلة 6 (بنيويًا فقط):

- كل إجراءات CI مثبتة بـcommit SHA كامل مع تعليق الوسم.
- `persist-credentials: false` في كل `actions/checkout`.
- `python3 tools/phase06/release_audit.py --check` يفحص الأسرار في الملفات
  المتعقبة (مفاتيح خاصة، رموز GitHub/Google/OpenAI/Slack/AWS) ويفشل عند وجودها.

توصيات قبل أي إصدار (لم تُنفَّذ):

- فعّل Dependabot/Renovate ومراجعة التبعيات.
- أضف `dependencyVerification` أو lockfile عند توفر الشبكة/البناء.
- أزل Firebase/Gemini غير المستخدمة أو صحّح `metadata.json` بقرار مالك.
- أضف gitleaks/trufflehog في CI كطبقة ثانية (المدقق الحالي بنيوي لا تاريخي).
- لا تمنح CI `contents: write` إلا لوظيفة نشر معتمدة.

## الخصوصية والأمان التشغيلي

- **النسخ الاحتياطي:** راجع [الخصوصية](PRIVACY.md#النسخ-الاحتياطي-والنقل-بين-الأجهزة).
  الاستثناءات لم تُختبر على جهاز.
- **التشفير:** لم يُختبر AndroidKeyStore على API 24–36. `isHardwareBacked()`
  لا يدّعي حماية عتادية بلا تحقق.
- **الحذف:** لا محو جنائي؛ حذف SharedPreferences/التطبيق قد تبقى له آثار على
  وسائط التخزين.
- **السجلات:** لا تُسجَّل نصوص المحادثات في logcat من طرف زهرة (مراجعة نصية،
  لا اختبار جهاز). أي تشخيص لا يطبع مفاتيح.

## الإبلاغ عن ثغرة

لا يعلن المستودع بريدًا أمنيًا أو برنامج مكافآت. الطريق المسؤول:

1. لا تفتح Issue عامًا يحتوي تفاصيل استغلال أو بيانات شخصية.
2. استخدم **GitHub Security Advisory** الخاص بالمستودع (Private vulnerability
   reporting) إن كان مفعّلًا لدى المالك.
3. إن لم يكن متاحًا، أبلغ المالك مباشرة عبر قناة خاصة، مع: الإصدار/commit،
   الوصف، خطوات الإعادة، الأثر، وأي إثباتات بلا بيانات حساسة.
4. لا تختبر على أجهزة/بيانات الآخرين، ولا تُجرِ هجمات حجب خدمة.

لا يوجد التزام بزمن استجابة أو مكافأة؛ هذه ليست وثيقة برنامج أمني. يُنشر
التصحيح/القيود في CHANGELOG والوثائق عند توفره، بلا اختلاق.

## ما لا ندّعيه

- لا «حماية كاملة» ولا «مستحيل الاختراق».
- لا أمان مقابل تطبيق خبيث بصلاحيات عالية أو جهاز مروّت.
- لا تحقق مستقل من الشيفرة/التبعيات.
- لا ضمان أن كل مسار خطأ يفشل مغلقًا في كل إصدارات Android؛ التصميم كذلك،
  والاختبار على جهاز ناقص.

## مراجع

- [نموذج التهديد وضوابط OWASP MASVS](https://mas.owasp.org/MASVS/)
- [Android backup](https://developer.android.com/identity/data/autobackup)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions security hardening](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [SLSA](https://slsa.dev/) · [Reproducible Builds](https://reproducible-builds.org/)
