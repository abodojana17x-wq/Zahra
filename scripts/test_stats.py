#!/usr/bin/env python3
"""اختبارات مرجعية لإحصاءات المرحلة 0 (تُشغَّل محليًا: python3 scripts/test_stats.py).

القيم المرجعية مأخوذة من صيغة ويلسون المنشورة (Wilson 1927) وتُطابق اختبارات Kotlin
في app/src/test/java/com/example/eval/EvalStatsTest.kt — أي اختلاف بين التنفيذين = فشل.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from stats import bootstrap_ci, mcnemar_exact, wilson_interval  # noqa: E402

FAILS: list[str] = []


def check(name: str, got, want, tol=0.002):
    ok = abs(got - want) <= tol
    print(f"{'PASS' if ok else 'FAIL'} · {name}: got={got:.6f} want={want:.6f}")
    if not ok:
        FAILS.append(name)


def main() -> int:
    # جداول مرجعية منشورة: 60/100 -> [0.502, 0.691] ؛ 0/20 -> [0.000, 0.161]
    lo, hi = wilson_interval(60, 100)
    check("wilson 60/100 lower", lo, 0.502)
    check("wilson 60/100 upper", hi, 0.691)
    lo, hi = wilson_interval(0, 20)
    check("wilson 0/20 lower", lo, 0.0)
    check("wilson 0/20 upper", hi, 0.161)
    lo, hi = wilson_interval(20, 20)
    check("wilson 20/20 upper", hi, 1.0)
    check("wilson 20/20 lower", lo, 0.839)

    # تماثل: 1-ويلسون(k,n) = ويلسون(n-k,n)
    lo1, hi1 = wilson_interval(3, 10)
    lo2, hi2 = wilson_interval(7, 10)
    check("wilson symmetry", 1 - hi1, lo2)
    check("wilson symmetry upper", 1 - lo1, hi2)

    # bootstrap: بذرة ثابتة => نفس النتيجة، والنسبة 0/1 محاطة بالأطراف
    vals = [1.0] * 30 + [0.0] * 70
    a = bootstrap_ci(vals, iters=500, seed=7)
    b = bootstrap_ci(vals, iters=500, seed=7)
    print(f"{'PASS' if a == b else 'FAIL'} · bootstrap deterministic: {a} == {b}")
    if a != b:
        FAILS.append("bootstrap deterministic")
    lo, hi = bootstrap_ci([0.0] * 50)
    check("bootstrap constant lower", lo, 0.0, 1e-9)
    check("bootstrap constant upper", hi, 0.0, 1e-9)

    # McNemar: b=0,c=5 => p = 2/32 = 0.0625 ؛ b=c => p=1
    check("mcnemar 0 vs 5", mcnemar_exact(0, 5), 0.0625, 1e-9)
    check("mcnemar 3 vs 3", mcnemar_exact(3, 3), 1.0, 1e-9)
    check("mcnemar 0 vs 0", mcnemar_exact(0, 0), 1.0, 1e-9)
    check("mcnemar 1 vs 9", mcnemar_exact(1, 9), 2 * (math.comb(10, 0) + math.comb(10, 1)) / 1024, 1e-9)

    print()
    if FAILS:
        print(f"فشل {len(FAILS)} اختبار: {', '.join(FAILS)}")
        return 1
    print("كل اختبارات الإحصاء نجحت")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
