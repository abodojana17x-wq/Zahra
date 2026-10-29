#!/usr/bin/env python3
"""تضمين آخر نتيجة قياس داخل الـ APK (زر «تشخيص» يقرأها وقت التشغيل).

    python3 scripts/export_eval_summary.py build/reports/zahra-eval/results.json \
        app/src/main/assets/eval_summary.json

الحقول المكتوبة مشتقة بالكامل من results.json (لا رقم يدوي)، ومعها هاش المجموعة
حتى يعرف المستخدم على أي إصدار أسئلة قيس هذا الرقم.
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def wilson_text(passed: int, total: int) -> str:
    if total <= 0:
        return "—"
    import math

    z = 1.959963984540054
    p = passed / total
    denom = 1 + z * z / total
    center = (p + z * z / (2 * total)) / denom
    half = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / denom
    lo, hi = max(0.0, center - half), min(1.0, center + half)
    return f"{lo * 100:.1f}%–{hi * 100:.1f}%"


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    results_path, out_path = Path(argv[0]), Path(argv[1])
    if not results_path.exists():
        print(f"WARN · لا يوجد results.json في {results_path} — لن تُضمَّن نتيجة في الـ APK")
        return 0

    data = json.loads(results_path.read_text(encoding="utf-8"))
    meta = data.get("meta", {})
    passed = int(meta.get("passed", 0))
    total = int(meta.get("total_counted", 0))
    payload = {
        "generated_at_utc": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "commit": str(meta.get("commit", "?"))[:12],
        "cases_sha256": meta.get("cases_sha256", "?"),
        "dataset_version": "v1",
        "passed": passed,
        "total": total,
        "failed": int(meta.get("failed", 0)),
        "skipped": int(meta.get("skipped", 0)),
        "rate": (passed / total) if total else 0.0,
        "wilson": wilson_text(passed, total),
        "repeats": meta.get("repeats"),
        "seed": meta.get("seed"),
        "domains": [
            {
                "domain": d.get("domain"),
                "passed": d.get("passed"),
                "total": d.get("total"),
            }
            for d in data.get("domains", [])
        ],
        "note": "قياس آلي من CI — 223 حالة × 8 تكرارات، حكم معجمي/رقمي حتمي بلا نموذج توليدي",
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"كتبت {out_path} — {passed}/{total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
