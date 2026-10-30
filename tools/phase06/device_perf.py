#!/usr/bin/env python3
"""المرحلة 6 — تحليل قياس أداء أندرويد من مخرجات `adb shell am start -W`.

⚠️ ما يفعله وما لا يفعله:
  - يقرأ مخرجات نصية **حقيقية** يلتقطها المستخدم من جهاز/محاكي، ويحوّلها إلى تقرير
    وصفي: العدد، الوسيط، p95، النطاق، وفاصل bootstrap للوسيط.
  - لا يشغّل adb ولا جهازًا ولا يبني APK. لا يحتوي أي رقم أداء لزهرة.
  - `am start -W` مناسب للفحص السريع لا كقياس إصدار: يتأثر بحمل الجهاز، ولا يغطي
    إطار أول محتوى أو jank. القياس المرجعي هو Macrobenchmark + Perfetto كما في
    docs/PERFORMANCE.md.
  - التقرير لا يُوسم «نجاحًا» إذا كان n < 10 أو الحالة مختلطة أو الحالة غير معروفة.

الاستخدام:
    adb shell am force-stop com.aistudio.zahra.kpxq
    adb shell am start -W -n com.aistudio.zahra.kpxq/com.example.MainActivity > run01.txt
    # كرّر 10 مرات على الأقل (يفضل 30)، ثم:
    python3 tools/phase06/device_perf.py parse --input run01.txt run02.txt \\
        --api-level 34 --device "Pixel 6" --build-type release --android-version 14 \\
        --commit <sha> --out build/phase06/startup.json --markdown build/phase06/startup.md

    python3 tools/phase06/device_perf.py selftest
"""
from __future__ import annotations

import argparse
import json
import math
import random
import re
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[2]
TOOL_VERSION = "phase06-device-perf/1"
MIN_RUNS = 10

BLOCK_SPLIT = re.compile(r"(?=^Starting:\s)", re.MULTILINE)
FIELD_RE = re.compile(r"^\s*(Status|LaunchState|Activity|ThisTime|TotalTime|WaitTime|Error)\s*:\s*(.+?)\s*$", re.MULTILINE)


@dataclass(frozen=True)
class Launch:
    status: str | None
    launch_state: str | None
    activity: str | None
    this_time_ms: int | None
    total_time_ms: int | None
    wait_time_ms: int | None
    error: str | None
    source: str


def parse_am_start(text: str, source: str = "<input>") -> list[Launch]:
    launches: list[Launch] = []
    for block in BLOCK_SPLIT.split(text):
        if "Starting:" not in block and "TotalTime:" not in block:
            continue
        fields: dict[str, str] = {}
        for name, value in FIELD_RE.findall(block):
            fields.setdefault(name, value.strip())

        def as_int(name: str) -> int | None:
            value = fields.get(name)
            if value is None:
                return None
            match = re.search(r"-?\d+", value)
            return int(match.group()) if match else None

        launches.append(
            Launch(
                status=fields.get("Status"),
                launch_state=fields.get("LaunchState"),
                activity=fields.get("Activity"),
                this_time_ms=as_int("ThisTime"),
                total_time_ms=as_int("TotalTime"),
                wait_time_ms=as_int("WaitTime"),
                error=fields.get("Error"),
                source=source,
            )
        )
    return launches


def percentile(values: Sequence[float], fraction: float) -> float:
    """مئين بالاستيفاء الخطي (interpolation) — نفس تعريف blind_bench."""
    if not values:
        raise ValueError("values must not be empty")
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    rank = (len(ordered) - 1) * fraction
    low, high = math.floor(rank), math.ceil(rank)
    if low == high:
        return float(ordered[low])
    return float(ordered[low] + (ordered[high] - ordered[low]) * (rank - low))


def bootstrap_median_ci(values: Sequence[float], iters: int = 5000, seed: int = 20261029) -> dict:
    if not values:
        return {"low": None, "high": None, "iters": 0}
    rng = random.Random(seed)
    medians = []
    n = len(values)
    for _ in range(iters):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        medians.append(statistics.median(sample))
    medians.sort()
    return {
        "low": medians[int(math.floor(0.025 * iters))],
        "high": medians[min(iters - 1, int(math.ceil(0.975 * iters)) - 1)],
        "iters": iters,
    }


def summarize(
    launches: Sequence[Launch],
    api_level: int | None = None,
    device: str | None = None,
    build_type: str | None = None,
    android_version: str | None = None,
    commit: str | None = None,
    bootstrap_iters: int = 5000,
    seed: int = 20261029,
) -> dict:
    warnings: list[str] = []
    valid = [launch for launch in launches if launch.total_time_ms is not None]
    missing = len(launches) - len(valid)
    if missing:
        warnings.append(f"{missing} تشغيلًا بلا TotalTime؛ استُبعد ولم يُملأ بصفر")
    values = [float(launch.total_time_ms) for launch in valid if launch.total_time_ms is not None]
    states = sorted({(launch.launch_state or "UNKNOWN") for launch in valid})
    statuses = sorted({(launch.status or "UNKNOWN") for launch in valid})
    if len(valid) < MIN_RUNS:
        warnings.append(f"عدد التشغيلات الصالحة {len(valid)} < {MIN_RUNS}: تقرير استكشافي فقط، لا يُنشر كقياس إصدار")
    if len(states) > 1:
        warnings.append(f"حالات بدء مختلطة ({states})؛ لا تخلط COLD/WARM/HOT في إحصاء واحد")
    if "UNKNOWN" in states:
        warnings.append("بعض التشغيلات بلا LaunchState (أجهزة/إصدارات قديمة)؛ صرّح بذلك ولا تدّعِ أنها باردة")
    if statuses and statuses != ["ok"]:
        warnings.append(f"حالات Status غير ok: {statuses}")
    if api_level is None:
        warnings.append("لم يُذكر API level؛ لا يمكن التحقق من دعم minSdk 24")
    elif api_level < 24:
        warnings.append(f"API {api_level} أقل من minSdk المعلن 24")
    if not build_type:
        warnings.append("لم يُذكر build type (debug/release)؛ نتائج debug لا تمثل الإصدار")
    elif build_type.lower() == "debug":
        warnings.append("build type=debug: القياس لا يمثل الإصدار النهائي")
    if not device:
        warnings.append("لم يُذكر اسم الجهاز/المحاكي")
    if not android_version:
        warnings.append("لم يُذكر إصدار أندرويد")
    if not commit:
        warnings.append("لم يُذكر commit؛ النتيجة غير قابلة للنسب إلى كود محدد")
    summary = {
        "tool": TOOL_VERSION,
        "status": "descriptive-not-a-release-benchmark",
        "provenance": {
            "apiLevel": api_level,
            "device": device,
            "buildType": build_type,
            "androidVersion": android_version,
            "commit": commit,
            "method": "adb shell am start -W",
        },
        "runs": {
            "total": len(launches),
            "valid": len(valid),
            "missing_total_time": missing,
            "launch_states": states,
            "statuses": statuses,
        },
        "total_time_ms": {
            "median": statistics.median(values) if values else None,
            "mean": statistics.fmean(values) if values else None,
            "min": min(values) if values else None,
            "max": max(values) if values else None,
            "p95": percentile(values, 0.95) if values else None,
            "bootstrap_median_95": bootstrap_median_ci(values, bootstrap_iters, seed) if values else None,
        },
        "raw_total_time_ms": values,
        "warnings": warnings,
        "disclaimer": "هذه قراءة وصفية من am start -W؛ ليست Macrobenchmark ولا تقيس jank أو إطار أول محتوى.",
    }
    return summary


def markdown_report(report: dict) -> str:
    t = report["total_time_ms"]
    prov = report["provenance"]
    lines = [
        "# قياس بدء تشغيل (وصفي) — المرحلة 6",
        "",
        "> من `adb shell am start -W` عبر `tools/phase06/device_perf.py`. ليس Macrobenchmark وليس ادعاء أداء.",
        "",
        "| البند | القيمة |",
        "|---|---|",
        f"| الجهاز | {prov['device'] or '—'} |",
        f"| Android | {prov['androidVersion'] or '—'} (API {prov['apiLevel'] if prov['apiLevel'] is not None else '—'}) |",
        f"| build | {prov['buildType'] or '—'} |",
        f"| commit | {prov['commit'] or '—'} |",
        f"| تشغيلات صالحة | {report['runs']['valid']} / {report['runs']['total']} |",
        f"| حالات البدء | {', '.join(report['runs']['launch_states']) or '—'} |",
        "",
        "| الوسيط | المتوسط | p95 | الأدنى | الأعلى | bootstrap للوسيط 95% |",
        "|---|---|---|---|---|---|",
        f"| {fmt_ms(t['median'])} | {fmt_ms(t['mean'])} | {fmt_ms(t['p95'])} | {fmt_ms(t['min'])} | {fmt_ms(t['max'])} | {fmt_ci(t['bootstrap_median_95'])} |",
        "",
        "## التحذيرات",
        "",
    ]
    if report["warnings"]:
        lines += [f"- {warning}" for warning in report["warnings"]]
    else:
        lines.append("- لا تحذيرات بنيوية (هذا لا يجعل القياس مرجعيًا).")
    lines += [
        "",
        "## القيود",
        "",
        "- `am start -W` يتأثر بحمل الجهاز/الخلفية، والقياس على محاكي لا يمثل هاتفًا حقيقيًا.",
        "- غياب العلم `LaunchState` يعني أن نوع البدء غير معروف، ولا يجوز وصفه بـCOLD.",
        "- عدد أقل من 10 تشغيلات = استكشاف فقط. للقياس المنشور استخدم Macrobenchmark وPerfetto وسجّل البصمات.",
        "",
    ]
    return "\n".join(lines)


def fmt_ms(value: float | None) -> str:
    return "—" if value is None else f"{value:.0f}ms"


def fmt_ci(ci: dict | None) -> str:
    if not ci or ci.get("low") is None:
        return "—"
    return f"{ci['low']:.0f}–{ci['high']:.0f}ms"


def cmd_parse(args: argparse.Namespace) -> int:
    launches: list[Launch] = []
    inputs = []
    for name in args.input:
        path = Path(name)
        if not path.exists():
            print(f"FAIL · ملف مفقود: {path}", file=sys.stderr)
            return 1
        launches.extend(parse_am_start(path.read_text(encoding="utf-8", errors="replace"), str(path)))
        inputs.append({"path": str(path), "runs": len(parse_am_start(path.read_text(encoding="utf-8", errors="replace")))})
    if not launches:
        print("FAIL · لم يُعثر على أي بلوك LaunchState/TotalTime في المدخلات", file=sys.stderr)
        return 1
    report = summarize(
        launches,
        api_level=args.api_level,
        device=args.device,
        build_type=args.build_type,
        android_version=args.android_version,
        commit=args.commit,
        bootstrap_iters=args.bootstrap_iters,
        seed=args.seed,
    )
    report["inputs"] = inputs
    if args.out:
        path = Path(args.out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"PASS · تقرير JSON → {path}")
    if args.markdown:
        path = Path(args.markdown)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown_report(report), encoding="utf-8")
        print(f"PASS · تقرير Markdown → {path}")
    if not args.out and not args.markdown:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    for warning in report["warnings"]:
        print(f"WARN · {warning}")
    return 0


SAMPLE = """Starting: Intent { cmp=com.aistudio.zahra.kpxq/com.example.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.aistudio.zahra.kpxq/com.example.MainActivity
ThisTime: 420
TotalTime: 512
WaitTime: 530
"""


def cmd_selftest(_: argparse.Namespace) -> int:
    assert len(parse_am_start(SAMPLE)) == 1
    assert parse_am_start(SAMPLE)[0].total_time_ms == 512
    assert summarize(parse_am_start(SAMPLE), api_level=34, device="test", build_type="release",
                     android_version="14", commit="deadbeef")["runs"]["valid"] == 1
    assert any("استكشافي" in w for w in summarize(parse_am_start(SAMPLE), api_level=34)["warnings"])
    mixed = parse_am_start(SAMPLE + SAMPLE.replace("COLD", "WARM").replace("512", "300"))
    assert len({launch.launch_state for launch in mixed}) == 2
    print("PASS · selftest: تحليل عينة اصطناعية، تحذير n<10، وكشف الحالات المختلطة.")
    print("INFO · العينة اصطناعية للاختبار فقط وليست قياسًا لزهرة.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="تحليل مخرجات am start -W — المرحلة 6")
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("parse", help="تحليل ملفات مخرجات am start -W")
    p.add_argument("--input", nargs="+", required=True)
    p.add_argument("--api-level", type=int)
    p.add_argument("--device")
    p.add_argument("--build-type")
    p.add_argument("--android-version")
    p.add_argument("--commit")
    p.add_argument("--bootstrap-iters", type=int, default=5000)
    p.add_argument("--seed", type=int, default=20261029)
    p.add_argument("--out")
    p.add_argument("--markdown")
    p.set_defaults(func=cmd_parse)
    p = sub.add_parser("selftest", help="اختبار على عينة اصطناعية")
    p.set_defaults(func=cmd_selftest)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
