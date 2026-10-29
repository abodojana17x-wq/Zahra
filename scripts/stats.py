#!/usr/bin/env python3
"""إحصاءات القياس الصغيرة العيّنة — مرجع مستقل (Python) مطابق لتنفيذ Kotlin.

السبب: مجموعة المرحلة 0 فيها 223 حالة و50 held-out، وهي «عيّنة صغيرة» بالأرقام.
لذلك لا ننشر نسبة مئوية وحدها (تحفظ تقرير المنافس)، بل:

  1. فاصل ويلسون 95% لكل نسبة (Wilson, 1927) — آمن عند p قريب من 0/1.
  2. bootstrap percentile لمقاييس ليست نسبة (مثل زمن p95).
  3. اختبار ماكنيمار للفروق الزوجية بين إصدارين على نفس الأسئلة (McNemar, 1947).

الدوال هنا تُستخدم في تقارير CI (Python) وتنفيذ Kotlin يطابقها (EvalStats.kt)
ومختبَر في `scripts/test_stats.py` و`EvalStatsTest.kt` بنفس الأرقام المرجعية.

الاستخدام:
    python3 scripts/stats.py 180 223      # ويلسون لنسبة 180/223
    python3 scripts/stats.py --from-results build/reports/zahra-eval/results.json
"""
from __future__ import annotations

import argparse
import json
import math
import random
import sys
from dataclasses import dataclass
from pathlib import Path

Z_95 = 1.959963984540054


def wilson_interval(successes: int, total: int, z: float = Z_95) -> tuple[float, float]:
    """فاصل ويلسون للنسبة (حدود دنيا/عليا)، لا يخرج خارج [0,1]."""
    if total <= 0:
        raise ValueError("total must be > 0")
    if successes < 0 or successes > total:
        raise ValueError("successes must be within [0, total]")
    p = successes / total
    denom = 1.0 + z * z / total
    center = (p + z * z / (2 * total)) / denom
    half = (z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total))) / denom
    return max(0.0, center - half), min(1.0, center + half)


def bootstrap_ci(values: list[float], iters: int = 2000, alpha: float = 0.05, seed: int = 20261026) -> tuple[float, float]:
    """فاصل ثقة bootstrap لنسبة/متوسط — بذرة ثابتة حتى يكون قابلًا للتكرار."""
    if not values:
        raise ValueError("values must not be empty")
    rng = random.Random(seed)
    n = len(values)
    means = []
    for _ in range(iters):
        total = 0.0
        for _ in range(n):
            total += values[rng.randrange(n)]
        means.append(total / n)
    means.sort()
    lo_idx = int(math.floor(alpha / 2 * iters))
    hi_idx = min(iters - 1, int(math.ceil((1 - alpha / 2) * iters)) - 1)
    return means[lo_idx], means[hi_idx]


def mcnemar_exact(b: int, c: int) -> float:
    """قيمة p لاختبار ماكنيمار الدقيق (ذيلين) لمن لم ينجحوا في A ونجحوا في B (b) والعكس (c)."""
    if b < 0 or c < 0:
        raise ValueError("counts must be non-negative")
    n = b + c
    if n == 0:
        return 1.0
    k = min(b, c)
    # p = 2 * P(X <= k) حيث X ~ Binomial(n, 0.5)
    tail = sum(math.comb(n, i) for i in range(0, k + 1)) / (2 ** n)
    return min(1.0, 2.0 * tail)


@dataclass
class DomainStat:
    domain: str
    passed: int
    total: int

    @property
    def rate(self) -> float:
        return self.passed / self.total if self.total else 0.0

    @property
    def ci(self) -> tuple[float, float]:
        return wilson_interval(self.passed, self.total)

    def line(self) -> str:
        lo, hi = self.ci
        return f"| {self.domain} | {self.passed}/{self.total} | {self.rate * 100:.1f}% | {lo * 100:.1f}–{hi * 100:.1f}% |"


def summarize_results(path: Path) -> str:
    data = json.loads(path.read_text(encoding="utf-8"))
    cases = data.get("cases", [])
    per: dict[str, list[bool]] = {}
    for c in cases:
        per.setdefault(c.get("domain", "?"), []).append(bool(c.get("passed")))
    stats = [DomainStat(d, sum(v), len(v)) for d, v in sorted(per.items())]
    total_passed = sum(s.passed for s in stats)
    total = sum(s.total for s in stats)
    overall = DomainStat("الإجمالي", total_passed, total)
    out = ["| المجال | ناجح/الكل | النسبة | فاصل ويلسون 95% |", "|---|---|---|---|"]
    out += [s.line() for s in stats]
    out.append(overall.line())
    lat = [float(c["latency_ms_median"]) for c in cases if c.get("latency_ms_median") is not None]
    if lat:
        lo, hi = bootstrap_ci(lat)
        out.append("")
        out.append(f"متوسط زمن الرد {sum(lat) / len(lat):.1f}ms · bootstrap 95%: {lo:.1f}–{hi:.1f}ms")
    return "\n".join(out)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("successes", nargs="?", type=int)
    ap.add_argument("total", nargs="?", type=int)
    ap.add_argument("--from-results", type=Path)
    ap.add_argument("--mcnemar", nargs=2, type=int, metavar=("b", "c"))
    args = ap.parse_args(argv)

    if args.from_results:
        print(summarize_results(args.from_results))
        return 0
    if args.mcnemar:
        b, c = args.mcnemar
        print(f"McNemar exact p = {mcnemar_exact(b, c):.6f} (b={b}, c={c})")
        return 0
    if args.successes is None or args.total is None:
        ap.print_help()
        return 2
    lo, hi = wilson_interval(args.successes, args.total)
    print(f"{args.successes}/{args.total} = {args.successes / args.total * 100:.2f}% · Wilson 95% [{lo * 100:.2f}%, {hi * 100:.2f}%]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
