# دليل الأدلة (Evidence)

> كل مرحلة في [`plans/`](../plans/README.md) **لا تُعتبر منتهية** إلا بمجلد `evidence/phase-XX/` يحتوي مخرجات أوامر فعلية. القاعدة: «الادعاء بلا دليل = لم يحدث».

## البنية المتوقعة (يُنشئها وكيل كل مرحلة)

```
evidence/
  phase-00/  gradle-test.log · eval-run.log · assemble.log · failing-tests.md · SUMMARY-after.md
  phase-01/  router-decision-map.md · confidence-calibration.csv · run.log · SUMMARY-after.md
  phase-02/  run.log · SUMMARY-after.md · no-eval-audit.txt · before-after.csv
  phase-03/  build-index.log · weights.json · paraphrase-report.md · run.log · SUMMARY-after.md
  phase-04/  run.log · SUMMARY-after.md · fake-memory-scan.txt · mood-matrix-before-after.md
  phase-05/  validation.json · license-audit.md · pack-impact.csv · run.log · SUMMARY-after.md
  phase-06/  commit.txt · corpus.sha256 · final-eval.log · final-build.log · blind-sheet.csv · performance.md · FINAL-SUMMARY.md
```

## قواعد الحفظ

1. **سجلات الأوامر** تُحفظ كما هي (بما فيها الفشل) — لا تُحرّر ولا تُختصر يدويًا.
2. **الأرقام** لازم تكون مصحوبة بـ: الأمر، تاريخ التنفيذ، هاش الكود (`git rev-parse HEAD`)، وهاش مجموعة الأسئلة.
3. **الملفات الضخمة** (APK، تقارير HTML، فيديوهات قياس) لا تُرفع إلى git؛ تُرفع كـ CI artifacts ويُشار إليها برابط.
4. **التقارير المختصرة** (`SUMMARY-after.md`) تُنسخ من `build/reports/zahra-eval/SUMMARY.md` كما هي.
5. أي تدهور في مجال من المجالات يُسجّل في نفس المجلد بملف `REGRESSION.md` — لا يُخفى.

هذا المجلد فارغ حاليًا عن قصد: المرحلة 0 هي من تملأه أولًا.
