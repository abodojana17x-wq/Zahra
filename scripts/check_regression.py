#!/usr/bin/env python3
"""حارس الانحدار — يقارن نتائج القياس الحالية بخط الأساس المجمّد (المرحلة 0).

الاستخدام (CI job `guard`):
    python3 scripts/check_regression.py build/reports/zahra-eval/results.json eval/BASELINE.md

قواعد الحكم:
  - لو BASELINE.md غير موجود أو mode != recorded → لا حكم (exit 0) مع رسالة صريحة.
  - لو هاش المجموعة مختلف → فشل (المقارنة غير عادلة).
  - لو الإجمالي أقل من خط الأساس → فشل.
  - أي انحدار في مجال واحد يُطبع كتحذير (لا يفشل وحده، لأن العيّنة صغيرة وحركة حالة تعادل ±2%).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def parse_baseline(path: Path) -> dict | None:
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8")
    match = re.search(r"```json\s*(\{[\s\S]*?\})\s*```", text)
    if not match:
        return None
    return json.loads(match.group(1))


def main(argv: list[str]) -> int:
    results_path = Path(argv[0]) if argv else Path("build/reports/zahra-eval/results.json")
    baseline_path = Path(argv[1]) if len(argv) > 1 else Path("eval/BASELINE.md")

    if not results_path.exists():
        print(f"FAIL · ملف النتائج مفقود: {results_path}")
        return 1
    results = json.loads(results_path.read_text(encoding="utf-8"))
    meta = results.get("meta", {})
    current_passed = meta.get("passed")
    current_total = meta.get("total_counted")
    current_hash = meta.get("cases_sha256")
    print(f"الحالي: {current_passed}/{current_total} · هاش المجموعة {str(current_hash)[:12]}…")

    baseline = parse_baseline(baseline_path)
    if baseline is None:
        print(f"لا يوجد خط أساس في {baseline_path} — لا حكم (سيُنشأ في أول تشغيل).")
        return 0
    mode = baseline.get("mode", "pending")
    if mode != "recorded":
        print(f"خط الأساس بوضع `{mode}` (ليس مجمّدًا) — لا حكم. راجع evidence/phase-00/ لتجميده.")
        return 0
    if baseline.get("cases_sha256") != current_hash:
        print("FAIL · هاش المجموعة مختلف عن خط الأساس — المقارنة غير عادلة، أعد القياس أو أصدر v2.")
        return 1
    baseline_passed = baseline.get("passed", 0)
    if current_passed < baseline_passed:
        print(f"FAIL · انحدار: {current_passed} < {baseline_passed} (خط الأساس). راجع SUMMARY.md.")
        return 1
    print(f"PASS · لا انحدار ({current_passed} ≥ {baseline_passed}).")

    # تحذيرات لكل مجال
    base_domains = {d["domain"]: d for d in baseline.get("domains", []) if isinstance(d, dict)}
    if base_domains:
        for d in results.get("domains", []):
            base = base_domains.get(d["domain"])
            if base and d["passed"] < base["passed"]:
                print(f"WARN · المجال {d['domain']} نزل من {base['passed']} إلى {d['passed']} — راجع الفاصل قبل الحكم.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
