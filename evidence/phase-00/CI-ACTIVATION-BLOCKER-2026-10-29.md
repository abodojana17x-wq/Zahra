# عائق تفعيل CI — المرحلة 0 (2026-10-29)

**الفرع:** `arena/25ba8c72-zahra`  
**الـ commit وقت الفحص:** `96fc0fe5f8139668133d05630fd8a0e33aa5c17d`  
**النطاق:** توثيق صريح فقط؛ لا تغيير في كود المنتج أو الاختبارات أو مجموعة القياس.

## ما تحقق فعليًا في هذا الفحص

| الأمر | النتيجة | المخرج المحفوظ |
|---|---|---|
| `sha256sum eval/cases.jsonl eval/held-out.jsonl` | الهاشان يطابقان `eval/MANIFEST.sha256` | [`freeze-check-2026-10-29.log`](freeze-check-2026-10-29.log) |
| `sha256sum --check eval/MANIFEST.sha256` | الملفان `OK` | [`manifest-check-2026-10-29.log`](manifest-check-2026-10-29.log) |
| `python3 scripts/validate_eval.py --phase 00` | نجحت بوابة المجموعة وR0 وAC-0.7 | [`validate-eval-2026-10-29.log`](validate-eval-2026-10-29.log) |
| `python3 scripts/test_stats.py` | نجحت اختبارات Wilson/bootstrap/McNemar المرجعية | [`stats-selftest-2026-10-29.log`](stats-selftest-2026-10-29.log) |
| `python3 scripts/gen_eval_cases.py --check` | تطابق التوليد حتميًا بايت-ببايت | [`gen-eval-cases-check-2026-10-29.log`](gen-eval-cases-check-2026-10-29.log) |
| فحص `.github/workflows/ci.yml` | الملف **غائب** | مخرج فحص الطرفية في جلسة التنفيذ؛ القالب موجود في [`../../ci/github-actions.yml`](../../ci/github-actions.yml) |

الهاشان المشاهدان:

```text
c12270d25a0e2f836b05e3741e7af72522e8c158966aa0a2712ad00717753546  eval/cases.jsonl
1a1b43349f859c98baa058800ae1f4208a6a3e7eff5307287eca9096ee7d255d  eval/held-out.jsonl
```

## ما لم يحدث ولماذا

لا يوجد workflow GitHub Actions على هذا الفرع الآن؛ لذلك لا يوجد تشغيل CI يمكن متابعته أو تنزيل artifacts منه. وعليه لا يتوفر بعد:

- تقرير JUnit من `:app:testDebugUnitTest` (AC-0.2).
- `build/reports/zahra-eval/results.json` و`SUMMARY.md` من هارنس 223 × 8 (AC-0.4).
- `eval/BASELINE.md` بوضع `recorded` ورقم خط أساس حقيقي (AC-0.3).
- artifacts: `junit-report` و`eval-report` و`zahra-debug-apk` أو رابط APK (AC-0.5).
- إثبات نجاح أمر Gradle/القياس من تشغيل فعلي (AC-0.6).
- صفوف فشل قابلة للتصنيف في [`failing-tests.md`](failing-tests.md).

هذا **ليس** سببًا لحذف أي معيار قبول أو اختبار أو توقع. لا تزال حالة المرحلة 0: 🟨 جارية بإثبات جزئي.

## الإجراء الوحيد المطلوب من مالك المستودع

ينفذ مالك المستودع — بحساب يملك صلاحية إنشاء/تحديث GitHub Actions workflows — من الفرع الحالي:

```bash
./scripts/install_ci.sh --commit && git push
```

ينسخ هذا الأمر القالب المراجع في `ci/github-actions.yml` إلى `.github/workflows/ci.yml` ويبدأ أول تشغيل حقيقي. بعد ظهور الرابط، تكون الخطوة التالية هي تنزيل artifacts، وتصنيف أي فشل من السجل الفعلي فقط، ثم تثبيت خط الأساس من النتائج الفعلية.

## ما لم يتغير عمدًا

- لم يُمس `eval/cases.jsonl` أو `eval/held-out.jsonl` أو `eval/MANIFEST.sha256`.
- لم يُحذف اختبار، ولم يُخفف أي توقع.
- لم يُسجل رقم دقة أو APK أو نجاح CI بلا تشغيل فعلي.
- لم يُحسم D1 أو D2 أو D3 بدل مالك المشروع.
