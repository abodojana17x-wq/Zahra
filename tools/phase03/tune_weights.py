#!/usr/bin/env python3
"""ضبط أوزان الدمج الهجين (المرحلة 3) على **مجموعة التدريب فقط**.

كيف يعمل؟
1. يطلب من Kotlin تفريغ سمات كل مرشح عبر `./tools/offline-typecheck/phase03/run.sh --dump-features`.
   السمات (bm25 مطبَّع، تشابه حروف، tfidf، تغطية، عدد المطابقات، وزن الوحدة) تُحسب بكود الإنتاج نفسه،
   فلا يوجد تنفيذ ثانٍ للمسترجِع في Python يتفرّع عنه بصمت.
2. يكرّر البحث الشبكي على مبسّط الأوزان (خطوة 0.05، كل وزن بين 0.05 و0.85) ويعيد تنفيذ *نفس* خط أنابيب
   الدرجة في `HybridRetriever` (نسبة إلى أفضل مرشح × معامل التغطية × وزن الوحدة × عقوبة المطابقة الواحدة).
3. يختار الأوزان التي ترفع MRR@5 على صفوف `split=train` فقط، ثم يقيس `heldout` مرة واحدة للتقرير.

الحدود المعلنة:
- الأرقام الناتجة وصفٌ لهذه المجموعة (100 استعلام مؤلَّف داخل المستودع)، وليست ادعاء دقة على العالم.
- لا يوجد أي «رقم مقبول» مستهدف (D20): السكربت يكتب ما حصل عليه فقط.
"""

from __future__ import annotations

import argparse
import itertools
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]

# ————— ثوابت خط أنابيب الدرجة: نسخة حرفية من HybridRetriever (تُفحص في SelfCheck) —————
CLOSE_CALL_MARGIN = 0.15
COVERAGE_BASELINE = 0.35
COVERAGE_WEIGHT = 0.65
LOW_COVERAGE_CAP = 0.25
LOW_COVERAGE_LIMIT = 0.40
LONG_QUERY_TOKENS = 3
MIN_MATCHES_FOR_LONG_QUERY = 2
SINGLE_MATCH_PENALTY = 0.55

WEIGHT_MIN = 0.05
WEIGHT_MAX = 0.85
STEP = 0.05

# أولوية معلنة مسبقًا: إذا تعادلت عدة توليفات على المقاييس، تُفضَّل التوليفة التي تُبقي كل إشارة
# فاعلة (أكبر حد أدنى للوزن) لأن الادعاء «هجين» يجب أن يبقى صادقًا في التفسير؛ ثم الأقرب إلى
# نقطة البداية المبنية على المصادر. التعادل شائع هنا لأن char و tfidf متباعدان في هذه المجموعة.
PRIOR = (0.60, 0.24, 0.16)


def load_features(root: Path) -> list[dict[str, Any]]:
    """يُشغّل Kotlin ويقرأ سطور JSON فقط (السطر الأول عنوان الترويسة)."""
    script = root / "tools/offline-typecheck/phase03/run.sh"
    if not script.exists():
        raise SystemExit(f"لم أجد {script}")
    proc = subprocess.run(
        [str(script), "--dump-features"],
        cwd=root,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stdout[-2000:])
        sys.stderr.write(proc.stderr[-2000:])
        raise SystemExit("فشل تفريغ السمات من Kotlin")
    rows = [json.loads(line) for line in proc.stdout.splitlines() if line.startswith("{")]
    if not rows:
        raise SystemExit("لم يُرجع Kotlin أي سطر سمات")
    return rows


def score_candidate(candidate: dict[str, Any], weights: tuple[float, float, float],
                    tokens: int, best_absolute: float) -> dict[str, float]:
    """إعادة تنفيذ معادلة `HybridRetriever.search` بعد الدمج المطلق."""
    bm25, char, tfidf = weights
    absolute = (bm25 * candidate["bm25"] + char * candidate["char"] + tfidf * candidate["tfidf"])
    penalty = SINGLE_MATCH_PENALTY if (tokens >= LONG_QUERY_TOKENS
                                       and candidate["nMatch"] < MIN_MATCHES_FOR_LONG_QUERY) else 1.0
    absolute *= penalty
    relative = (absolute / best_absolute) if best_absolute > 0 else 0.0
    adjusted = relative * (COVERAGE_BASELINE + COVERAGE_WEIGHT * candidate["coverage"])
    if tokens >= LONG_QUERY_TOKENS and candidate["coverage"] < LOW_COVERAGE_LIMIT:
        adjusted = min(LOW_COVERAGE_CAP, adjusted)
    final = min(1.0, max(0.0, adjusted * candidate["weight"]))
    return {"absolute": absolute, "relative": relative, "score": final}


def rank_row(row: dict[str, Any], weights: tuple[float, float, float]) -> list[dict[str, Any]]:
    tokens = row["tokens"]
    best_absolute = max(
        (weights[0] * c["bm25"] + weights[1] * c["char"] + weights[2] * c["tfidf"])
        * (SINGLE_MATCH_PENALTY if (tokens >= LONG_QUERY_TOKENS
                                    and c["nMatch"] < MIN_MATCHES_FOR_LONG_QUERY) else 1.0)
        for c in row["candidates"]
    ) if row["candidates"] else 0.0
    scored = []
    for candidate in row["candidates"]:
        detail = score_candidate(candidate, weights, tokens, best_absolute)
        scored.append({"id": candidate["id"], "pack": candidate.get("pack", ""), **detail})
    scored.sort(key=lambda item: (-item["score"], -item["absolute"], item["id"]))
    return scored


def metrics(rows: list[dict[str, Any]], weights: tuple[float, float, float],
            floor: float) -> dict[str, Any]:
    total = top1 = answered = 0
    reciprocal = 0.0
    for row in rows:
        ranked = rank_row(row, weights)[:5]
        ids = [item["id"] for item in ranked]
        rank = ids.index(row["expected"]) if row["expected"] in ids else None
        total += 1
        if rank == 0:
            top1 += 1
        if rank is not None:
            reciprocal += 1.0 / (rank + 1)
        # مرآة `UnitRetrievalGate`: الإجابة تتطلب عبور الأرضية **وألا** يكون أعلى مرشحين
        # متقاربين ضمن الهامش من حزمتين مختلفتين (تلك الحالة توضيح لا إجابة).
        if ranked and ranked[0]["score"] >= floor:
            close_call = (
                len(ranked) > 1
                and (ranked[0]["score"] - ranked[1]["score"]) <= CLOSE_CALL_MARGIN
                and ranked[0]["pack"] != ranked[1]["pack"]
            )
            if not close_call:
                answered += 1
    return {
        "queries": total,
        "top1": round(top1 / total, 4) if total else 0.0,
        "answered": round(answered / total, 4) if total else 0.0,
        "mrrAt5": round(reciprocal / total, 4) if total else 0.0,
    }


def simplex() -> list[tuple[float, float, float]]:
    grid = []
    steps = int(round(1.0 / STEP))
    for i in range(1, steps):
        for j in range(1, steps - i):
            k = steps - i - j
            if k < 1:
                continue
            bm25, char, tfidf = i * STEP, j * STEP, k * STEP
            if min(bm25, char, tfidf) < WEIGHT_MIN or max(bm25, char, tfidf) > WEIGHT_MAX:
                continue
            grid.append((round(bm25, 4), round(char, 4), round(tfidf, 4)))
    return grid


def main() -> int:
    parser = argparse.ArgumentParser(description="ضبط أوزان BM25+/حروف/TF-IDF على train فقط")
    parser.add_argument("--out", default="evidence/phase-03/weights.json")
    parser.add_argument("--floor", type=float, default=0.50,
                        help="أرضية القبول المستخدمة في حساب نسبة «مُجاب» (افتراضيًا عتبة البوابة)")
    args = parser.parse_args()

    rows = load_features(ROOT)
    train = [row for row in rows if row.get("split") == "train"]
    heldout = [row for row in rows if row.get("split") != "train"]
    if not train:
        raise SystemExit("لا صفوف تدريب في مجموعة الصياغة")

    grid = simplex()
    best_weights = None
    best_key = None
    for weights in grid:
        result = metrics(train, weights, args.floor)
        key = (
            result["mrrAt5"],
            result["top1"],
            round(min(weights), 4),
            -round(sum(abs(w - p) for w, p in zip(weights, PRIOR)), 4),
        )
        if best_key is None or key > best_key:
            best_key = key
            best_weights = weights
    assert best_weights is not None

    def by_kind(subset: list[dict[str, Any]]) -> dict[str, Any]:
        kinds: dict[str, list[dict[str, Any]]] = {}
        for row in subset:
            kinds.setdefault(row.get("kind", "other"), []).append(row)
        return {kind: metrics(items, best_weights, args.floor) for kind, items in sorted(kinds.items())}

    train_metrics = metrics(train, best_weights, args.floor)
    heldout_metrics = metrics(heldout, best_weights, args.floor)

    payload = {
        "schemaVersion": 1,
        "phase": 3,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generator": "tools/phase03/tune_weights.py",
        "featuresFrom": "tools/offline-typecheck/phase03/run.sh --dump-features (Kotlin production scoring)",
        "trainSet": {
            "path": "eval/phase03/paraphrase-100.jsonl",
            "split": "train",
            "queries": train_metrics["queries"],
        },
        "heldoutSet": {"split": "heldout", "queries": heldout_metrics["queries"]},
        "search": {
            "space": f"simplex step {STEP}, each weight in [{WEIGHT_MIN}, {WEIGHT_MAX}]",
            "objective": "MRR@5 on train (tie-break: Top-1, then max-min weight, then L1 to the source-based prior)",
            "prior": {"bm25": PRIOR[0], "char": PRIOR[1], "tfidf": PRIOR[2]},
            "candidatesEvaluated": len(grid),
        },
        "selected": {"bm25": best_weights[0], "char": best_weights[1], "tfidf": best_weights[2]},
        "train": train_metrics,
        "heldout": heldout_metrics,
        "byKind": by_kind(rows),
        "caveats": [
            "المجموعة مؤلَّفة داخل المستودع (100 استعلام، 5 أنواع) وليست عيّنة عشوائية من مستخدمين حقيقيين.",
            "لا يوجد رقم قبول مستهدف (D20)؛ هذه قياسات وصفية قابلة لإعادة التوليد فقط.",
            "الأرضية المستخدمة في حساب «مُجاب» هي عتبة البوابة القديمة غير المعايَرة (D4).",
            "المرجع النهائي للأرقام هو تشغيل Kotlin (`tools/offline-typecheck/phase03/run.sh`)؛ قد يختلف هنا في خانة عشرية بسبب ترتيب التعادل.",
        ],
    }

    out = ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"عدد توليفات الأوزان: {len(grid)} · التدريب: {train_metrics['queries']} استعلامًا")
    print(f"الملف: {out.relative_to(ROOT)}")
    print(f"الأوزان المختارة: bm25={best_weights[0]:.2f} حروف={best_weights[1]:.2f} tfidf={best_weights[2]:.2f}")
    for label, data in (("train", train_metrics), ("heldout", heldout_metrics)):
        print(f"  {label:8s} Top-1={data['top1']:.1%} مُجاب={data['answered']:.1%} MRR@5={data['mrrAt5']:.3f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
