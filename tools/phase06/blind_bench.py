#!/usr/bin/env python3
"""المرحلة 6 — هارنس التقييم الأعمى والتحليل الزوجي (Python قياسي، بلا شبكة).

لماذا هذا الملف؟
----------------
المرحلة 0 بنت مجموعة أسئلة مجمدة وهارنس تشغيل واحد. المرحلة 6 تضيف **مقارنة
أعمى** بين نظامين (زهرة وخط أساس) على نفس الحالات، مع ضبط تحيّز الموضع وتحيّز
المحكّم، وتحليل إحصائي صغير العيّنة:

  * جدول عروض متوازن: كل حالة تُعرض مرتين (A/B وB/A) وبترتيب عشوائي مثبّت البذرة.
  * أصوات خام (A/B/tie/invalid) لكل محكّم.
  * نسبة تفضيل + فاصل Wilson على الأصوات المحسومة + bootstrap عنقودي على مستوى الحالة.
  * Bradley-Terry (MLE بخوارزمية MM) كأساس للترتيب، وElo كعرض ثانوي ترتيبي.
  * اتفاق المحكّمين (Cohen / Fleiss kappa)، وثبات الموضع (position consistency)،
    ونسبة فوز الموضع الأول مع اختبار ذيليين.
  * مقارنة نتائج system A/B الصحيحة (passed) عبر McNemar الدقيق + bootstrap زوجي.

⚠️ حدود صريحة:
  - لا يحتوي هذا الملف أي نتيجة جاهزة، ولا نتيجة لزهرة. كل رقم يخرج منه مشتق من
    ملفات مدخلات يقدمها المستخدم. تشغيل `selftest` يستخدم بيانات اصطناعية موسومة.
  - الأصوات ليست مستقلة إحصائيًا إذا تكرر المحكّم نفسه؛ نتائج BT هنا وصفية ولا
    تُعامَل كتقدير سكاني عام. الفاصل الرئيسي هو bootstrap عنقودي على الحالات.
  - Elo يعتمد على ترتيب الأصوات، لذا لا يُستخدم كدليل أساسي؛ BT هو الأساس.
  - أدوات المرحلة 0 (`scripts/stats.py`) هي المرجع لحساب Wilson/bootstrap/McNemar،
    ويُعاد استخدامها هنا بدل نسخ أرقامها.

الاستخدام:
    python3 tools/phase06/blind_bench.py schedule \\
        --cases eval/held-out.jsonl --systems zahra baseline --seed 20261029 \\
        --out build/phase06/schedule.jsonl

    python3 tools/phase06/blind_bench.py analyze \\
        --schedule build/phase06/schedule.jsonl --votes build/phase06/votes.jsonl \\
        --protocol release/blind-protocol.example.json --target zahra --ref baseline \\
        --out build/phase06/analysis.json --markdown build/phase06/analysis.md

    python3 tools/phase06/blind_bench.py compare-results \\
        --a build/a/results.json --b build/b/results.json \\
        --label-a zahra --label-b baseline --out build/phase06/compare.json

    python3 tools/phase06/blind_bench.py validate-protocol \\
        --protocol release/blind-protocol.example.json

    python3 tools/phase06/blind_bench.py selftest
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Sequence

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from stats import mcnemar_exact, wilson_interval  # noqa: E402

TOOL_VERSION = "phase06-blind-bench/1"
VALID_VERDICTS = ("A", "B", "tie", "invalid")
PROTOCOL_STATUSES = ("template-not-run", "preregistered", "running", "complete")
PRIMARY_ENDPOINTS = ("pairwise_preference_win_rate",)


# --------------------------------------------------------------------------------------
# مدخلات/مخرجات JSON
# --------------------------------------------------------------------------------------
def read_jsonl(path: Path) -> list[dict]:
    rows: list[dict] = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        try:
            rows.append(json.loads(stripped))
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{lineno}: JSON غير صالح ({exc})") from exc
    return rows


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: Sequence[float], q: float) -> float:
    """مئين خطي (interpolation) — معلن هنا لأن p95 في doc/PERFORMANCE يستخدمه."""
    if not values:
        raise ValueError("values must not be empty")
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    rank = (len(ordered) - 1) * q
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return float(ordered[low])
    return float(ordered[low] + (ordered[high] - ordered[low]) * (rank - low))


# --------------------------------------------------------------------------------------
# 1) جدول العروض المتوازن
# --------------------------------------------------------------------------------------
def build_schedule(case_ids: Iterable[str], systems: Sequence[str], seed: int) -> list[dict]:
    """كل حالة × كل ترتيب للنظامين = عرضان، ثم خلط حتمي ببذرة معلنة.

    التوازن مقصود: كل نظام يظهر في الموضع A مرة وفي الموضع B مرة لكل حالة.
    هذا يضبط تحيّز الموضع بدل الاعتماد على عشوائية قد تصادف اختلالًا.
    """
    ids = list(case_ids)
    if len(set(ids)) != len(ids):
        raise ValueError("معرّفات الحالات مكررة في ملف العروض")
    if len(systems) != 2:
        raise ValueError("البروتوكول الحالي يدعم نظامين فقط (A/B) — أي توسعة تحتاج بروتوكولًا جديدًا")
    if systems[0] == systems[1]:
        raise ValueError("معرّفا النظامين متطابقان")
    rows: list[dict] = []
    for case_id in ids:
        for first, second in ((systems[0], systems[1]), (systems[1], systems[0])):
            rows.append(
                {
                    "presentation_id": f"{case_id}::{first}::{second}",
                    "case_id": case_id,
                    "position_a": first,
                    "position_b": second,
                }
            )
    random.Random(seed).shuffle(rows)
    return rows


# --------------------------------------------------------------------------------------
# 2) تحميل الأصوات والتحقق
# --------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Judgment:
    presentation_id: str
    case_id: str
    judge_id: str
    system_a: str
    system_b: str
    verdict: str

    @property
    def winner(self) -> str | None:
        if self.verdict == "A":
            return self.system_a
        if self.verdict == "B":
            return self.system_b
        if self.verdict == "tie":
            return None
        return None

    @property
    def is_valid(self) -> bool:
        return self.verdict in VALID_VERDICTS and self.verdict != "invalid"


def load_judgments(schedule: Sequence[dict], votes: Sequence[dict]) -> tuple[list[Judgment], list[str]]:
    warnings: list[str] = []
    by_id = {str(row.get("presentation_id")): row for row in schedule}
    if len(by_id) != len(schedule):
        raise ValueError("جدول العروض يحتوي presentation_id مكررًا")
    seen: set[tuple[str, str]] = set()
    judgments: list[Judgment] = []
    for index, vote in enumerate(votes, start=1):
        pid = str(vote.get("presentation_id", "")).strip()
        judge = str(vote.get("judge_id", "")).strip()
        verdict = str(vote.get("verdict", "")).strip()
        if not pid or not judge:
            raise ValueError(f"صوت #{index}: presentation_id/judge_id مفقود")
        if verdict not in VALID_VERDICTS:
            raise ValueError(f"صوت #{index}: verdict غير معروف ({verdict!r}) — المسموح {VALID_VERDICTS}")
        row = by_id.get(pid)
        if row is None:
            raise ValueError(f"صوت #{index}: العرض {pid} غير موجود في جدول العروض")
        key = (pid, judge)
        if key in seen:
            raise ValueError(f"صوت مكرر لنفس المحكّم والعرض: {pid} / {judge}")
        seen.add(key)
        judgments.append(
            Judgment(
                presentation_id=pid,
                case_id=str(row["case_id"]),
                judge_id=judge,
                system_a=str(row["position_a"]),
                system_b=str(row["position_b"]),
                verdict=verdict,
            )
        )
    missing = [pid for pid in by_id if pid not in {j.presentation_id for j in judgments}]
    if missing:
        warnings.append(f"{len(missing)} عرضًا بلا أي صوت؛ تُعامل مفقودة ولا تُخترع لها إجابة")
    invalid = sum(1 for j in judgments if not j.is_valid)
    if invalid:
        warnings.append(f"{invalid} صوتًا غير صالح (invalid) استُبعد من النسب ولم يُحوَّل إلى خسارة")
    return judgments, warnings


# --------------------------------------------------------------------------------------
# 3) نسب التفضيل والفاصل
# --------------------------------------------------------------------------------------
def preference_counts(judgments: Sequence[Judgment], system: str) -> dict[str, int | float]:
    wins = losses = ties = invalid = 0
    for j in judgments:
        if not j.is_valid:
            invalid += 1
            continue
        winner = j.winner
        if winner is None:
            ties += 1
        elif winner == system:
            wins += 1
        else:
            losses += 1
    decided = wins + losses
    return {
        "system": system,
        "wins": wins,
        "losses": losses,
        "ties": ties,
        "invalid": invalid,
        "decided": decided,
        "decided_win_rate": (wins / decided) if decided else None,
        "half_win_rate": ((wins + 0.5 * ties) / (wins + losses + ties)) if (wins + losses + ties) else None,
    }


def cluster_bootstrap(
    judgments: Sequence[Judgment],
    statistic: Callable[[Sequence[Judgment]], float],
    iters: int,
    seed: int,
    alpha: float = 0.05,
) -> dict:
    """bootstrap بإعادة أخذ الحالات (clusters) مع كل أصواتها، لا الأصوات منفردة.

    السبب: أصوات المحكّمين على نفس الحالة مترابطة؛ إعادة أخذ الأصوات منفردة
    تضيّق الفاصل كذبًا. الوحدة هنا هي الحالة، والأصوات تتبعها.
    """
    by_case: dict[str, list[Judgment]] = {}
    for j in judgments:
        by_case.setdefault(j.case_id, []).append(j)
    case_ids = sorted(by_case)
    if not case_ids:
        return {"iters": 0, "low": None, "high": None, "note": "لا أصوات"}
    rng = random.Random(seed)
    samples: list[float] = []
    for _ in range(iters):
        picked: list[Judgment] = []
        for _ in case_ids:
            picked.extend(by_case[case_ids[rng.randrange(len(case_ids))]])
        try:
            samples.append(float(statistic(picked)))
        except (ValueError, ZeroDivisionError):
            continue
    if not samples:
        return {"iters": 0, "low": None, "high": None, "note": "تعذّر حساب الإحصاءة في كل العينات"}
    samples.sort()
    low_idx = max(0, int(math.floor(alpha / 2 * len(samples))))
    high_idx = min(len(samples) - 1, int(math.ceil((1 - alpha / 2) * len(samples))) - 1)
    return {"iters": len(samples), "low": samples[low_idx], "high": samples[high_idx]}


# --------------------------------------------------------------------------------------
# 4) Bradley-Terry وElo
# --------------------------------------------------------------------------------------
def fit_bradley_terry(systems: Sequence[str], judgments: Sequence[Judgment]) -> dict[str, float]:
    """MLE لـ Bradley-Terry بخوارزمية MM، مع معالجة التعادل كـ0.5 فوز لكل طرف.

    p_i = w_i / Σ_j n_ij/(p_i+p_j) ثم تطبيع المجموع إلى 1. التطبيع لا يغيّر
    الاحتمالات النسبية، ويجعل المقارنة قابلة للتكرار.
    """
    if len(systems) < 2:
        raise ValueError("BT يحتاج نظامين على الأقل")
    wins = {s: 0.0 for s in systems}
    pairs: dict[tuple[str, str], int] = {}
    for j in judgments:
        if not j.is_valid:
            continue
        pairs[(j.system_a, j.system_b)] = pairs.get((j.system_a, j.system_b), 0) + 1
        if j.winner is not None:
            wins[j.winner] += 1.0
        else:
            wins[j.system_a] += 0.5
            wins[j.system_b] += 0.5
    if sum(wins.values()) == 0:
        raise ValueError("لا توجد أصوات صالحة لـBT")
    p = {s: 1.0 / len(systems) for s in systems}
    for _ in range(20000):
        updated: dict[str, float] = {}
        for i in systems:
            denom = 0.0
            for j in systems:
                if i == j:
                    continue
                n_ij = pairs.get((i, j), 0) + pairs.get((j, i), 0)
                if n_ij:
                    denom += n_ij / max(p[i] + p[j], 1e-300)
            updated[i] = wins[i] / denom if denom > 0 else 0.0
        total = sum(updated.values())
        if total <= 0:
            raise ValueError("BT انهار: لا فائز قابل للتقدير")
        updated = {s: v / total for s, v in updated.items()}
        if max(abs(updated[s] - p[s]) for s in systems) < 1e-13:
            p = updated
            break
        p = updated
    return p


def bt_summary(systems: Sequence[str], judgments: Sequence[Judgment], ref: str) -> dict:
    strengths = fit_bradley_terry(systems, judgments)
    log_strength: dict[str, float | None] = {}
    for s in systems:
        if strengths[s] <= 0 or strengths[ref] <= 0:
            log_strength[s] = None
        else:
            log_strength[s] = math.log(strengths[s]) - math.log(strengths[ref])
    probabilities = {
        f"{a}_vs_{b}": strengths[a] / (strengths[a] + strengths[b])
        for a in systems
        for b in systems
        if a != b and strengths[a] + strengths[b] > 0
    }
    return {
        "method": "Bradley-Terry MM (ties=0.5 win each)",
        "strengths": strengths,
        "log_strength_vs_ref": log_strength,
        "win_probability": probabilities,
        "ref": ref,
        "note": "احتمالات وصفية على الأصوات المقدمة؛ ليست تقديرًا سكانيًا عامًا.",
    }


def elo_ratings(systems: Sequence[str], judgments: Sequence[Judgment], k: float = 32.0, start: float = 1500.0) -> dict:
    ratings = {s: start for s in systems}
    ordered = sorted(judgments, key=lambda j: (j.case_id, j.presentation_id, j.judge_id))
    for j in ordered:
        if not j.is_valid:
            continue
        a, b = j.system_a, j.system_b
        if a not in ratings or b not in ratings:
            continue
        expected_a = 1.0 / (1.0 + 10 ** ((ratings[b] - ratings[a]) / 400.0))
        score_a = 0.5 if j.winner is None else (1.0 if j.winner == a else 0.0)
        ratings[a] += k * (score_a - expected_a)
        ratings[b] += k * ((1.0 - score_a) - (1.0 - expected_a))
    return {
        "method": "Elo (order-dependent)",
        "k": k,
        "start": start,
        "ratings": ratings,
        "note": "ثانوي فقط؛ يعتمد على ترتيب الأصوات ولا يُستخدم لادعاء تفوق عام.",
    }


# --------------------------------------------------------------------------------------
# 5) تحيّز الموضع واتفاق المحكّمين
# --------------------------------------------------------------------------------------
def position_consistency(judgments: Sequence[Judgment]) -> dict:
    """هل يختار المحكّم نفس النظام عندما ينقلب موضع العرض؟

    لكل (حالة، محكّم) نحتاج صوتين صالحين. التفضيل يُنسب للنظام لا للموضع.
    التعادل في العرضين متسق؛ تعادل مقابل فوز غير متسق.
    """
    grouped: dict[tuple[str, str], list[Judgment]] = {}
    for j in judgments:
        if j.is_valid:
            grouped.setdefault((j.case_id, j.judge_id), []).append(j)
    pairs_total = 0
    consistent = 0
    for votes in grouped.values():
        if len(votes) < 2:
            continue
        pairs_total += 1
        labels = {v.winner for v in votes}
        if len(labels) == 1:
            consistent += 1
    rate = consistent / pairs_total if pairs_total else None
    interval = wilson_interval(consistent, pairs_total) if pairs_total else None
    return {
        "pairs": pairs_total,
        "consistent": consistent,
        "rate": rate,
        "wilson_95": interval,
        "note": "أقل من زوجين لكل (حالة، محكّم) = غير قابل للحساب، ويُنشر العدد.",
    }


def first_position_bias(judgments: Sequence[Judgment]) -> dict:
    valid = [j for j in judgments if j.is_valid and j.winner is not None]
    first_wins = sum(1 for j in valid if j.verdict == "A")
    total = len(valid)
    rate = first_wins / total if total else None
    p_value = binomial_two_sided(first_wins, total, 0.5) if total else None
    return {
        "decided": total,
        "first_position_wins": first_wins,
        "first_position_rate": rate,
        "binomial_two_sided_p_vs_0.5": p_value,
        "note": "اقتراب النسبة من 0.5 لا يثبت غياب التحيّز، لكن انحرافًا كبيرًا مؤشر مباشر.",
    }


def binomial_two_sided(k: int, n: int, p: float = 0.5) -> float:
    """اختبار ذيليين دقيق لتوزيع ثنائي متماثل (p=0.5 افتراضيًا)."""
    if n <= 0 or not (0 <= k <= n):
        raise ValueError("k/n خارج النطاق")
    if p == 0.5:
        tail = sum(math.comb(n, i) for i in range(0, min(k, n - k) + 1)) / (2 ** n)
        return min(1.0, 2.0 * tail)
    # حالة عامة: مجموع الاحتمالات الأقل من أو تساوي احتمال المشاهدة
    def pmf(i: int) -> float:
        return math.comb(n, i) * (p ** i) * ((1 - p) ** (n - i))

    observed = pmf(k)
    total = sum(pmf(i) for i in range(n + 1) if pmf(i) <= observed + 1e-15)
    return min(1.0, total)


def _labels(judgments: Sequence[Judgment]) -> dict[str, dict[str, str]]:
    """presentation -> judge -> label (اسم النظام أو tie أو invalid)."""
    out: dict[str, dict[str, str]] = {}
    for j in judgments:
        label = "invalid" if not j.is_valid else (j.winner if j.winner else "tie")
        out.setdefault(j.presentation_id, {})[j.judge_id] = label
    return out


def cohen_kappa(pairs: Sequence[tuple[str, str]]) -> dict:
    if not pairs:
        return {"n": 0, "kappa": None, "observed_agreement": None, "expected_agreement": None}
    n = len(pairs)
    categories = sorted({a for a, _ in pairs} | {b for _, b in pairs})
    observed = sum(1 for a, b in pairs if a == b) / n
    expected = 0.0
    for c in categories:
        ca = sum(1 for a, _ in pairs if a == c) / n
        cb = sum(1 for _, b in pairs if b == c) / n
        expected += ca * cb
    if expected >= 1.0:
        kappa = 1.0 if observed >= 1.0 else 0.0
    else:
        kappa = (observed - expected) / (1.0 - expected)
    return {
        "n": n,
        "kappa": kappa,
        "observed_agreement": observed,
        "expected_agreement": expected,
        "note": "Cohen kappa على تفضيل النظام (أو tie) لكل عرض مشترك.",
    }


def fleiss_kappa(ratings_by_item: dict[str, list[str]]) -> dict:
    items = {k: v for k, v in ratings_by_item.items() if len(v) >= 2}
    if not items:
        return {"items": 0, "kappa": None, "observed_agreement": None, "expected_agreement": None}
    total_assignments = sum(len(v) for v in items.values())
    p_bar_values: list[float] = []
    categories = sorted({label for v in items.values() for label in v})
    category_totals = {c: 0 for c in categories}
    for votes in items.values():
        n = len(votes)
        counts = {c: votes.count(c) for c in categories}
        sum_sq = sum(v * v for v in counts.values())
        p_bar_values.append((sum_sq - n) / (n * (n - 1)))
        for c in categories:
            category_totals[c] += counts[c]
    p_bar = sum(p_bar_values) / len(p_bar_values)
    p_e = sum((total_assignments and count / total_assignments) ** 2 for count in category_totals.values())
    kappa = None if p_e >= 1.0 else (p_bar - p_e) / (1.0 - p_e)
    return {
        "items": len(items),
        "kappa": kappa,
        "observed_agreement": p_bar,
        "expected_agreement": p_e,
        "note": "Fleiss kappa مع أحجام تقييم مختلفة لكل عنصر (صيغة P_i القياسية).",
    }


def inter_judge_agreement(judgments: Sequence[Judgment]) -> dict:
    by_presentation = _labels(judgments)
    judges = sorted({j.judge_id for j in judgments})
    pairwise: dict[str, dict] = {}
    for i, a in enumerate(judges):
        for b in judges[i + 1 :]:
            pairs = []
            for labels in by_presentation.values():
                if a in labels and b in labels and "invalid" not in (labels[a], labels[b]):
                    pairs.append((labels[a], labels[b]))
            pairwise[f"{a}|{b}"] = cohen_kappa(pairs)
    fleiss_items = {
        pid: [label for label in labels.values() if label != "invalid"]
        for pid, labels in by_presentation.items()
    }
    return {
        "judges": judges,
        "pairwise_cohen_kappa": pairwise,
        "fleiss_kappa": fleiss_kappa(fleiss_items),
        "note": "اتفاق منخفض يعني أن المقارنة غير مستقرة حتى لو كانت النسبة تبدو واضحة.",
    }


# --------------------------------------------------------------------------------------
# 6) تحليل كامل
# --------------------------------------------------------------------------------------
def analyze(
    schedule: Sequence[dict],
    votes: Sequence[dict],
    target: str,
    ref: str,
    bootstrap_iters: int = 2000,
    seed: int = 20261029,
    alpha: float = 0.05,
) -> dict:
    judgments, warnings = load_judgments(schedule, votes)
    systems = sorted(
        {str(row["position_a"]) for row in schedule} | {str(row["position_b"]) for row in schedule}
    )
    if target not in systems or ref not in systems:
        raise ValueError(f"النظامان {target}/{ref} غير موجودين في الأصوات؛ الموجود: {systems}")
    valid = [j for j in judgments if j.is_valid]
    if not valid:
        raise ValueError("لا توجد أصوات صالحة للتحليل")

    counts = preference_counts(judgments, target)
    decided = int(counts["decided"])
    wilson = wilson_interval(int(counts["wins"]), decided) if decided else None
    half_win = counts["half_win_rate"]
    half_ci = (
        cluster_bootstrap(
            judgments,
            lambda js: float(preference_counts(js, target)["half_win_rate"] or 0.0),
            bootstrap_iters,
            seed,
            alpha,
        )
        if half_win is not None
        else None
    )
    bt = bt_summary(systems, judgments, ref)
    bt_diff = bt["log_strength_vs_ref"].get(target)
    bt_ci = None
    if bt_diff is not None:
        bt_ci = cluster_bootstrap(
            judgments,
            lambda js: float(bt_summary(systems, js, ref)["log_strength_vs_ref"][target] or 0.0),
            bootstrap_iters,
            seed + 1,
            alpha,
        )
    return {
        "tool": TOOL_VERSION,
        "dataset": {
            "cases": len({j.case_id for j in judgments}),
            "schedule_rows": len(schedule),
            "votes": len(votes),
            "valid_votes": len(valid),
            "invalid_votes": sum(1 for j in judgments if not j.is_valid),
            "missing_presentations": len(schedule) - len({j.presentation_id for j in judgments}),
        },
        "preference": {
            "target": target,
            "counts": counts,
            "decided_win_rate_wilson_95": wilson,
            "half_win_rate_cluster_bootstrap_95": half_ci,
            "note": "النتيجة الأساسية هي نسبة نصف الفوز (tie=0.5) مع bootstrap عنقودي على الحالات؛ Wilson على المحسوم فقط.",
        },
        "bradley_terry": {**bt, "target_vs_ref_log_strength_cluster_bootstrap_95": bt_ci},
        "elo_secondary": elo_ratings(systems, judgments),
        "position_consistency": position_consistency(judgments),
        "first_position_bias": first_position_bias(judgments),
        "inter_judge_agreement": inter_judge_agreement(judgments),
        "warnings": warnings,
        "disclaimer": "هذه نتائج مدخلات مقدمة. لا تصبح قياسًا منشورًا لزهرة إلا مع بروتوكول مسجل وأصوات خام وهاش مجموعة مجمّدة.",
    }


def markdown_report(report: dict) -> str:
    pref = report["preference"]
    counts = pref["counts"]
    bt = report["bradley_terry"]
    pos = report["position_consistency"]
    first = report["first_position_bias"]
    agreement = report["inter_judge_agreement"]
    lines = [
        "# تقرير مقارنة عمياء (المرحلة 6)",
        "",
        "> مولّد من `tools/phase06/blind_bench.py`. كل رقم هنا مشتق من الأصوات المقدمة، وليس قياسًا جرى في هذا المستودع.",
        "",
        "## البيانات",
        "",
        "| البند | القيمة |",
        "|---|---|",
        f"| الحالات | {report['dataset']['cases']} |",
        f"| العروض | {report['dataset']['schedule_rows']} |",
        f"| الأصوات | {report['dataset']['votes']} (صالح {report['dataset']['valid_votes']} / غير صالح {report['dataset']['invalid_votes']}) |",
        f"| عروض مفقودة | {report['dataset']['missing_presentations']} |",
        "",
        "## التفضيل",
        "",
        f"النظام المستهدف: **{pref['target']}**",
        "",
        "| فوز | خسارة | تعادل | محسوم | نسبة المحسوم | فاصل Wilson 95% | نصف فوز | bootstrap عنقودي 95% |",
        "|---|---|---|---|---|---|---|---|",
        f"| {counts['wins']} | {counts['losses']} | {counts['ties']} | {counts['decided']} | "
        f"{fmt_pct(counts['decided_win_rate'])} | {fmt_ci(pref['decided_win_rate_wilson_95'])} | "
        f"{fmt_pct(counts['half_win_rate'])} | {fmt_ci(pref['half_win_rate_cluster_bootstrap_95'])} |",
        "",
        "## Bradley-Terry (أساسي) وElo (ثانوي)",
        "",
        "| النظام | BT strength | log-strength vs ref | Elo |",
        "|---|---|---|---|",
    ]
    for system in bt["strengths"]:
        log_strength = bt["log_strength_vs_ref"].get(system)
        lines.append(
            f"| {system} | {bt['strengths'][system]:.4f} | "
            f"{'—' if log_strength is None else f'{log_strength:+.3f}'} | "
            f"{report['elo_secondary']['ratings'].get(system, 0):.1f} |"
        )
    lines += [
        "",
        f"فاصل bootstrap للفرق اللوغاريتمي: {fmt_ci(bt['target_vs_ref_log_strength_cluster_bootstrap_95'])}",
        "",
        "## تحيّز الموضع والاتفاق",
        "",
        f"- ثبات الموضع: {pos['consistent']}/{pos['pairs']} ({fmt_pct(pos['rate'])}) · Wilson 95%: {fmt_ci(pos['wilson_95'])}",
        f"- فوز الموضع الأول: {first['first_position_wins']}/{first['decided']} ({fmt_pct(first['first_position_rate'])}) · p ثنائي: {fmt_p(first['binomial_two_sided_p_vs_0.5'])}",
        f"- Fleiss kappa: {fmt_kappa(agreement['fleiss_kappa']['kappa'])}",
    ]
    for pair, value in agreement["pairwise_cohen_kappa"].items():
        lines.append(f"- Cohen kappa ({pair}): {fmt_kappa(value['kappa'])} على {value['n']} عرضًا")
    lines += [
        "",
        "## التحذيرات",
        "",
    ]
    if report["warnings"]:
        lines += [f"- {w}" for w in report["warnings"]]
    else:
        lines.append("- لا تحذيرات بنيوية من المدقق (هذا لا يعني صحة التصميم).")
    lines += [
        "",
        "## ما لا يقوله هذا التقرير",
        "",
        "- لا يقول إن زهرة «أفضل» في العموم؛ هذا يخص هذه المجموعة وهذا البروتوكول.",
        "- لا يصلح بديلًا عن عينة كبيرة من المحكّمين المستقلين أو مراجعة بشرية للمحتوى.",
        "- Elo ليس دليلًا أساسيًا لأنه يعتمد على ترتيب الأصوات.",
        "",
    ]
    return "\n".join(lines)


def fmt_pct(value: float | None) -> str:
    return "—" if value is None else f"{value * 100:.1f}%"


def fmt_ci(ci: dict | tuple | None) -> str:
    if ci is None:
        return "—"
    if isinstance(ci, tuple):
        low, high = ci
    else:
        low, high = ci.get("low"), ci.get("high")
    if low is None or high is None:
        return "—"
    return f"{low * 100:.1f}–{high * 100:.1f}%"


def fmt_p(value: float | None) -> str:
    return "—" if value is None else f"{value:.4f}"


def fmt_kappa(value: float | None) -> str:
    return "—" if value is None else f"{value:.3f}"


# --------------------------------------------------------------------------------------
# 7) مقارنة ملفي نتائج (passed لكل حالة)
# --------------------------------------------------------------------------------------
def load_result_cases(path: Path) -> tuple[dict[str, dict], dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    cases = data.get("cases")
    if not isinstance(cases, list):
        raise ValueError(f"{path}: لا يحتوي cases[]")
    out: dict[str, dict] = {}
    skipped: list[str] = []
    for row in cases:
        cid = str(row.get("id", "")).strip()
        if not cid:
            raise ValueError(f"{path}: حالة بلا id")
        if cid in out:
            raise ValueError(f"{path}: id مكرر {cid}")
        if row.get("status") == "skipped":
            skipped.append(cid)
            continue
        out[cid] = {
            "passed": bool(row.get("passed")),
            "domain": str(row.get("domain", "?")),
        }
    meta = data.get("meta") if isinstance(data.get("meta"), dict) else {}
    if skipped:
        meta = {**meta, "skipped_excluded": skipped}
    return out, meta


def holm_bonferroni(p_values: Sequence[tuple[str, float]]) -> list[dict]:
    ordered = sorted(p_values, key=lambda item: item[1])
    m = len(ordered)
    out: list[dict] = []
    previous = 0.0
    for index, (name, p) in enumerate(ordered):
        adjusted = min(1.0, max(previous, p * (m - index)))
        previous = adjusted
        out.append({"name": name, "p": p, "p_holm": adjusted})
    return out


def compare_results(
    a_cases: dict[str, dict],
    b_cases: dict[str, dict],
    label_a: str,
    label_b: str,
    bootstrap_iters: int = 5000,
    seed: int = 20261029,
) -> dict:
    common = sorted(set(a_cases) & set(b_cases))
    if not common:
        raise ValueError("لا حالات مشتركة بين الملفين")
    only_a = sorted(set(a_cases) - set(b_cases))
    only_b = sorted(set(b_cases) - set(a_cases))
    a_pass = [1 if a_cases[c]["passed"] else 0 for c in common]
    b_pass = [1 if b_cases[c]["passed"] else 0 for c in common]
    diff = sum(b_pass) / len(common) - sum(a_pass) / len(common)
    rng = random.Random(seed)
    samples: list[float] = []
    for _ in range(bootstrap_iters):
        total_a = total_b = 0
        for _ in common:
            idx = rng.randrange(len(common))
            total_a += a_pass[idx]
            total_b += b_pass[idx]
        samples.append((total_b - total_a) / len(common))
    samples.sort()
    low = samples[int(math.floor(0.025 * len(samples)))]
    high = samples[min(len(samples) - 1, int(math.ceil(0.975 * len(samples))) - 1)]
    b = sum(1 for i in range(len(common)) if a_pass[i] == 0 and b_pass[i] == 1)
    c = sum(1 for i in range(len(common)) if a_pass[i] == 1 and b_pass[i] == 0)
    domains: dict[str, list[int]] = {}
    for i, cid in enumerate(common):
        dom = a_cases[cid]["domain"]
        domains.setdefault(dom, []).append(i)
    per_domain = []
    p_values: list[tuple[str, float]] = []
    for dom, indices in sorted(domains.items()):
        da = sum(a_pass[i] for i in indices)
        db = sum(b_pass[i] for i in indices)
        n = len(indices)
        db_only = sum(1 for i in indices if a_pass[i] == 0 and b_pass[i] == 1)
        da_only = sum(1 for i in indices if a_pass[i] == 1 and b_pass[i] == 0)
        p = mcnemar_exact(db_only, da_only)
        p_values.append((dom, p))
        per_domain.append(
            {
                "domain": dom,
                "n": n,
                f"{label_a}_passed": da,
                f"{label_b}_passed": db,
                "delta": (db - da) / n,
                "mcnemar_p": p,
                "a_only": da_only,
                "b_only": db_only,
            }
        )
    holm = {row["name"]: row for row in holm_bonferroni(p_values)}
    for row in per_domain:
        row["mcnemar_p_holm"] = holm[row["domain"]]["p_holm"]
    return {
        "tool": TOOL_VERSION,
        "labels": {"a": label_a, "b": label_b},
        "n_common": len(common),
        "only_a": only_a,
        "only_b": only_b,
        "passed": {
            label_a: sum(a_pass),
            label_b: sum(b_pass),
            "delta_b_minus_a": diff,
            "paired_bootstrap_95": {"low": low, "high": high, "iters": len(samples)},
        },
        "mcnemar": {
            "a_fail_b_pass": b,
            "a_pass_b_fail": c,
            "exact_two_sided_p": mcnemar_exact(b, c),
            "note": "McNemar على الحالات المشتركة فقط؛ الحالات غير المشتركة تُذكر ولا تُحذف بصمت.",
        },
        "per_domain": per_domain,
        "warnings": (
            (["عدد الحالات المشتركة أقل من 50 — الفاصل واسع والاستنتاج ضعيف"] if len(common) < 50 else [])
            + (["ملفان لا يغطيان نفس الحالات؛ المقارنة الزوجية على التقاطع فقط"] if only_a or only_b else [])
        ),
    }


# --------------------------------------------------------------------------------------
# 8) التحقق من البروتوكول
# --------------------------------------------------------------------------------------
def validate_protocol(path: Path) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []

    def fail(code: str, message: str) -> None:
        findings.append(("FAIL", f"{code}: {message}"))

    def warn(code: str, message: str) -> None:
        findings.append(("WARN", f"{code}: {message}"))

    if not path.exists():
        return [("FAIL", f"ملف البروتوكول مفقود: {path}")]
    try:
        protocol = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [("FAIL", f"بروتوكول غير صالح JSON: {exc}")]
    for key in ("protocolId", "status", "dataset", "systems", "design", "endpoints", "analysis", "stoppingRule", "results"):
        if key not in protocol:
            fail("protocol-field", f"حقل مفقود: {key}")
    if any(level == "FAIL" for level, _ in findings):
        return findings
    if protocol["status"] not in PROTOCOL_STATUSES:
        fail("protocol-status", f"status غير مسموح: {protocol['status']}")
    dataset = protocol["dataset"]
    data_file = ROOT / str(dataset.get("file", ""))
    if not data_file.exists():
        fail("dataset-file", f"ملف المجموعة غير موجود: {data_file}")
    else:
        actual_hash = sha256_file(data_file)
        if dataset.get("sha256") != actual_hash:
            fail("dataset-hash", f"هاش المجموعة لا يطابق البروتوكول ({actual_hash})")
        actual_cases = sum(1 for line in data_file.read_text(encoding="utf-8").splitlines() if line.strip())
        if dataset.get("cases") != actual_cases:
            fail("dataset-count", f"عدد الحالات في البروتوكول {dataset.get('cases')} ≠ الفعلي {actual_cases}")
    systems = protocol["systems"]
    if not isinstance(systems, list) or len(systems) != 2:
        fail("systems", "المطلوب نظامان بالضبط في هذا البروتوكول")
    else:
        ids = [str(s.get("id")) for s in systems]
        if len(set(ids)) != 2:
            fail("systems-ids", "معرّفات الأنظمة غير فريدة")
    design = protocol["design"]
    if design.get("blind") is not True:
        fail("design-blind", "التقييم يجب أن يكون أعمى")
    if design.get("swapPositions") is not True:
        fail("design-swap", "تبديل الموضع مطلوب لضبط تحيّز الترتيب")
    if design.get("presentationsPerPair") != 2:
        fail("design-presentations", "المطلوب عرضان لكل حالة (A/B وB/A)")
    if not isinstance(design.get("randomizationSeed"), int):
        fail("design-seed", "بذرة العشوائية يجب أن تكون عددًا صحيحًا")
    judges = design.get("judges")
    if not isinstance(judges, int) or judges < 1:
        fail("design-judges", "عدد المحكّمين غير صالح")
    elif judges < 2:
        warn("design-judges", "محكّم واحد لا يسمح بقياس الاتفاق")
    if design.get("tiePolicy") not in ("half-win", "exclude", "tie-as-win"):
        fail("design-tie", "tiePolicy غير معروف")
    endpoints = protocol["endpoints"]
    if endpoints.get("primary") not in PRIMARY_ENDPOINTS:
        fail("endpoints-primary", f"النهاية الأساسية غير مدعومة: {endpoints.get('primary')}")
    if not endpoints.get("secondary"):
        warn("endpoints-secondary", "لا توجد نهايات ثانوية معلنة")
    analysis = protocol["analysis"]
    if not (0 < float(analysis.get("alpha", 0)) < 1):
        fail("analysis-alpha", "alpha خارج (0,1)")
    if float(analysis.get("confidenceLevel", 0)) != 0.95:
        fail("analysis-confidence", "البروتوكول الحالي ينشر فاصل 95% صراحةً")
    if int(analysis.get("bootstrapIters", 0)) < 1000:
        fail("analysis-bootstrap", "عدد bootstrap أقل من 1000")
    if analysis.get("noPercentageWithoutCi") is not True:
        fail("analysis-ci", "يجب منع نشر نسبة بلا فاصل ثقة")
    if not analysis.get("multipleComparisons"):
        fail("analysis-multiplicity", "سياسة تعدد المقارنات مطلوبة")
    if not str(protocol.get("stoppingRule", "")).strip():
        fail("stopping", "قاعدة الإيقاف مطلوبة")
    results = protocol["results"]
    if protocol["status"] == "complete":
        result_file = results.get("resultFile")
        if not result_file:
            fail("results-file", "بروتوكول مكتمل بلا resultFile")
        else:
            result_path = ROOT / str(result_file)
            if not result_path.exists():
                fail("results-file", f"ملف النتيجة غير موجود: {result_path}")
            elif results.get("resultSha256") != sha256_file(result_path):
                fail("results-hash", "هاش ملف النتيجة لا يطابق البروتوكول")
        for key in ("resultSha256", "publishedAt"):
            if not results.get(key):
                fail("results-meta", f"بروتوكول مكتمل بلا {key}")
    elif results.get("resultFile"):
        warn("results-early", "يوجد resultFile قبل status=complete؛ راجع التسجيل المسبق")
    if protocol["status"] == "template-not-run":
        warn("protocol-template", "هذا قالب غير مسجل؛ لا يصلح لنشر نتيجة (وهذا متوقع الآن)")
    return findings


# --------------------------------------------------------------------------------------
# 9) CLI
# --------------------------------------------------------------------------------------
def _case_ids(path: Path) -> list[str]:
    rows = read_jsonl(path)
    ids = [str(row.get("id", "")).strip() for row in rows]
    if any(not cid for cid in ids):
        raise ValueError(f"{path}: حالة بلا id")
    return ids


def cmd_schedule(args: argparse.Namespace) -> int:
    ids = _case_ids(Path(args.cases))
    systems = [s.strip() for s in args.systems if s.strip()]
    rows = build_schedule(ids, systems, args.seed)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8")
    print(f"PASS · جدول عروض: {len(rows)} عرضًا لـ{len(ids)} حالة → {out}")
    print(f"INFO · بذرة العشوائية: {args.seed} · الأنظمة: {systems}")
    return 0


def cmd_analyze(args: argparse.Namespace) -> int:
    schedule = read_jsonl(Path(args.schedule))
    votes = read_jsonl(Path(args.votes))
    report = analyze(
        schedule,
        votes,
        target=args.target,
        ref=args.ref,
        bootstrap_iters=args.bootstrap_iters,
        seed=args.seed,
    )
    report.setdefault("warnings", [])
    if args.protocol:
        protocol_path = Path(args.protocol)
        findings = validate_protocol(protocol_path)
        failures = [message for level, message in findings if level == "FAIL"]
        if failures:
            for message in failures:
                print(f"FAIL · {message}")
            print("\nرفض التحليل: البروتوكول غير صالح. لا تُنشر نتيجة تحت بروتوكول مكسور.")
            return 1
        protocol = json.loads(protocol_path.read_text(encoding="utf-8"))
        report["protocol"] = {
            "path": str(protocol_path),
            "sha256": sha256_file(protocol_path),
            "protocolId": protocol.get("protocolId"),
            "status": protocol.get("status"),
            "preregisteredAt": protocol.get("preregisteredAt"),
            "findings": [f"{level}: {message}" for level, message in findings],
        }
        if protocol.get("status") == "template-not-run":
            report["warnings"].append(
                "البروتوكول قالب غير مسجل (template-not-run)؛ النتيجة استكشافية ولا تُنشر كقياس."
            )
        elif protocol.get("status") != "complete":
            report["warnings"].append(
                f"حالة البروتوكول {protocol.get('status')} وليست complete؛ لا تُنشر كنتيجة نهائية."
            )
    else:
        report["warnings"].append("لم يُمرَّر --protocol؛ النتيجة غير قابلة للنشر كقياس مسجل.")
    if args.out:
        write_json(Path(args.out), report)
        print(f"PASS · تقرير JSON → {args.out}")
    if args.markdown:
        path = Path(args.markdown)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown_report(report), encoding="utf-8")
        print(f"PASS · تقرير Markdown → {path}")
    if not args.out and not args.markdown:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


def cmd_compare(args: argparse.Namespace) -> int:
    a_cases, a_meta = load_result_cases(Path(args.a))
    b_cases, b_meta = load_result_cases(Path(args.b))
    report = compare_results(a_cases, b_cases, args.label_a, args.label_b, args.bootstrap_iters, args.seed)
    for label, meta in ((args.label_a, a_meta), (args.label_b, b_meta)):
        if meta.get("skipped_excluded"):
            report["warnings"].append(
                f"{label}: استُبعدت {len(meta['skipped_excluded'])} حالة skipped من المقارنة"
            )
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        write_json(Path(args.out), report)
        print(f"PASS · مقارنة النتائج → {args.out}")
    else:
        print(payload)
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    findings = validate_protocol(Path(args.protocol))
    for level, message in findings:
        print(f"{level} · {message}")
    failed = sum(1 for level, _ in findings if level == "FAIL")
    if failed:
        print(f"\nفشل {failed} فحصًا من فحوصات البروتوكول.")
        return 1
    print("\nكل فحوصات البروتوكول البنيوية نجحت (لا يعني ذلك صحة التصميم العلمي).")
    return 0


def cmd_selftest(_: argparse.Namespace) -> int:
    """بيانات اصطناعية موسومة بوضوح — تختبر الحساب فقط، ولا تُنشر كقياس."""
    systems = ["A_sys", "B_sys"]
    cases = [f"synthetic-{i:03d}" for i in range(1, 21)]
    schedule = build_schedule(cases, systems, 7)
    rng = random.Random(11)
    votes = []
    for row in schedule:
        # نظام A أقوى صناعيًا: 70% فوز عند المواجهة، مع تعادل 10%.
        if row["position_a"] == "A_sys":
            verdict = "A" if rng.random() < 0.7 else ("tie" if rng.random() < 0.5 else "B")
        else:
            verdict = "B" if rng.random() < 0.7 else ("tie" if rng.random() < 0.5 else "A")
        votes.append({"presentation_id": row["presentation_id"], "judge_id": "j1", "verdict": verdict})
    report = analyze(schedule, votes, target="A_sys", ref="B_sys", bootstrap_iters=300, seed=3)
    assert report["preference"]["counts"]["wins"] > report["preference"]["counts"]["losses"]
    assert report["bradley_terry"]["strengths"]["A_sys"] > report["bradley_terry"]["strengths"]["B_sys"]
    assert report["position_consistency"]["pairs"] == len(cases)
    print("PASS · selftest على بيانات اصطناعية: BT واتجاه التفضيل وثبات الموضع متسقة.")
    print("INFO · هذه ليست نتيجة زهرة ولا تُنشر كقياس.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="مقارنة عمياء وتحليل إحصائي صغير العيّنة — المرحلة 6")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("schedule", help="بناء جدول عروض متوازن ومخلوط ببذرة")
    p.add_argument("--cases", required=True)
    p.add_argument("--systems", nargs=2, required=True)
    p.add_argument("--seed", type=int, default=20261029)
    p.add_argument("--out", required=True)
    p.set_defaults(func=cmd_schedule)

    p = sub.add_parser("analyze", help="تحليل أصوات خام")
    p.add_argument("--schedule", required=True)
    p.add_argument("--votes", required=True)
    p.add_argument("--target", required=True)
    p.add_argument("--ref", required=True)
    p.add_argument("--bootstrap-iters", type=int, default=2000)
    p.add_argument("--seed", type=int, default=20261029)
    p.add_argument("--protocol", help="بروتوكول مسجل مسبقًا؛ يُرفض التحليل إذا كان غير صالح")
    p.add_argument("--out")
    p.add_argument("--markdown")
    p.set_defaults(func=cmd_analyze)

    p = sub.add_parser("compare-results", help="مقارنة passed في ملفي results.json")
    p.add_argument("--a", required=True)
    p.add_argument("--b", required=True)
    p.add_argument("--label-a", required=True)
    p.add_argument("--label-b", required=True)
    p.add_argument("--bootstrap-iters", type=int, default=5000)
    p.add_argument("--seed", type=int, default=20261029)
    p.add_argument("--out")
    p.set_defaults(func=cmd_compare)

    p = sub.add_parser("validate-protocol", help="فحص التسجيل المسبق")
    p.add_argument("--protocol", default=str(ROOT / "release/blind-protocol.example.json"))
    p.set_defaults(func=cmd_validate)

    p = sub.add_parser("selftest", help="حساب على بيانات اصطناعية فقط")
    p.set_defaults(func=cmd_selftest)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
