#!/usr/bin/env python3
"""بوابة ما قبل التنفيذ + تحقق مجموعة الأسئلة (المرحلة 0).

يُشغَّل في CI في job اسمه `preflight` (وأيضًا محليًا بأمر واحد):

    python3 scripts/validate_eval.py                 # كل الفحوصات
    python3 scripts/validate_eval.py --phase 00      # فحص بحث R0 للمرحلة 0 فقط

يفشل (exit != 0) إذا:
  1. مجموعة الأسئلة لا تطابق التوزيع المعلن أو بها معرفات مكررة/أنواع خاطئة.
  2. هاش الملفات لا يطابق eval/MANIFEST.sha256 (المجموعة مجمّدة — القاعدة 2).
  3. evidence/phase-XX/research.md ناقص: < 5 مصادر، أو بلا جدول قرارات، أو بلا قسم توسيع.
  4. يوجد أي نص «Pending Execution» في المستودع (AC-0.7).
     استثناء مُعلن: plans/ (يذكر المعيار نفسه)، scripts/ (هذا الفاحص)، evidence/ (سجل التدقيق).
     الفحص يغطي الكود والتوثيق وREADME وreference — أي مكان يُنشر فيه ادعاء.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVAL = ROOT / "eval"

EXPECTED_DOMAINS = {
    "greet": 30,
    "calc": 30,
    "units": 18,
    "datetime": 10,
    "general": 40,
    "weather": 8,
    "coding": 30,
    "mood": 20,
    "safety": 5,
    "context": 20,
    "unknown": 12,
}
ALLOWED_TYPES = {"numeric", "regex", "contains_any", "refusal", "source_required", "exact_code", "slot_value"}

FAILS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)
    print(f"FAIL · {msg}")


def ok(msg: str) -> None:
    print(f"PASS · {msg}")


def read_jsonl(path: Path) -> list[dict]:
    rows = []
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError as exc:
            fail(f"{path.name}:{i}: JSON غير صالح ({exc})")
    return rows


def check_dataset() -> None:
    cases_path = EVAL / "cases.jsonl"
    held_path = EVAL / "held-out.jsonl"
    for p in (cases_path, held_path):
        if not p.exists():
            fail(f"ملف مفقود: {p.relative_to(ROOT)}")
            return

    cases = read_jsonl(cases_path)
    held = read_jsonl(held_path)

    if len(cases) < 200:
        fail(f"عدد الحالات {len(cases)} < 200 (AC-0.1)")
    else:
        ok(f"عدد الحالات الأساسية {len(cases)} ≥ 200")

    counts: dict[str, int] = {}
    for c in cases:
        counts[c["domain"]] = counts.get(c["domain"], 0) + 1
    for domain, expected in EXPECTED_DOMAINS.items():
        if counts.get(domain, 0) != expected:
            fail(f"توزيع المجال {domain}: متوقع {expected} وموجود {counts.get(domain, 0)}")
    if not FAILS:
        ok("التوزيع مطابق للمعلن في plans/00-measurement.md")

    if len(held) < 50:
        fail(f"held-out {len(held)} < 50 (AC-0.1)")
    else:
        ok(f"held-out {len(held)} ≥ 50")

    all_rows = cases + held
    ids = [r["id"] for r in all_rows]
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        fail(f"معرفات مكررة: {sorted(dupes)[:10]}")
    else:
        ok("كل المعرفات فريدة")

    for row in all_rows:
        cid = row.get("id", "?")
        expect = row.get("expect") or {}
        etype = expect.get("type")
        if etype not in ALLOWED_TYPES:
            fail(f"{cid}: نوع توقع غير مسموح ({etype})")
        if row.get("must_refuse") != (etype == "refusal"):
            fail(f"{cid}: must_refuse غير متسق مع expect.type")
        if not str(row.get("question", "")).strip():
            fail(f"{cid}: سؤال فارغ")
        if etype == "numeric" and expect.get("value") is None:
            fail(f"{cid}: قيمة رقمية مفقودة")
        if etype == "contains_any" and not expect.get("value"):
            fail(f"{cid}: قائمة بدائل فارغة")
        if row.get("domain") == "context" and not row.get("context_turns"):
            fail(f"{cid}: حالة سياق بلا context_turns")
    if not FAILS:
        ok("مخطط كل الحالات صحيح")

    # التجميد: الهاش لا بد أن يطابق MANIFEST
    manifest = EVAL / "MANIFEST.sha256"
    if not manifest.exists():
        fail("eval/MANIFEST.sha256 مفقود (AC-0.1)")
        return
    entries = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) >= 2:
            entries[parts[-1]] = parts[0]
    for name, path in (("eval/cases.jsonl", cases_path), ("eval/held-out.jsonl", held_path)):
        want = entries.get(name) or entries.get(path.name)
        got = hashlib.sha256(path.read_bytes()).hexdigest()
        if want is None:
            fail(f"MANIFEST لا يذكر {name}")
        elif want != got:
            fail(f"هاش {name} تغيّر عن المجموعة المجمّدة — أي تعديل يلزمه إصدار v2 + إعادة قياس كاملة")
        else:
            ok(f"هاش {name} مطابق للمجمّد")


def check_research(phase: str) -> None:
    path = ROOT / "evidence" / f"phase-{phase}" / "research.md"
    if not path.exists():
        fail(f"بحث R0 مفقود: {path.relative_to(ROOT)} — المرحلة لا تُقبل بلا بحث (plans/PRE-FLIGHT.md)")
        return
    text = path.read_text(encoding="utf-8")
    sources = len(re.findall(r"^\|\s*\d+\s*\|", text, flags=re.MULTILINE))
    if sources < 5:
        fail(f"{path.name}: عدد المصادر {sources} < 5")
    else:
        ok(f"{path.name}: {sources} مصادر")
    if "## الاكتشافات وتأثيرها" not in text:
        fail(f"{path.name}: جدول «الاكتشافات وتأثيرها» مفقود")
    else:
        ok(f"{path.name}: جدول الاكتشافات موجود")
    if "التوسيع" not in text:
        fail(f"{path.name}: قسم «ما أضافه البحث للمرحلة» (التوسيع) مفقود")
    else:
        ok(f"{path.name}: قسم التوسيع موجود")
    if not re.search(r"أمر|امر|gradlew|python3", text):
        fail(f"{path.name}: أوامر القياس غير مكتوبة")
    else:
        ok(f"{path.name}: أوامر القياس مكتوبة")


def check_no_pending_execution() -> None:
    pattern = re.compile(r"pending\s+execution", re.IGNORECASE)
    hits = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if rel.parts[0] in {".git", "build", ".gradle", ".kotlin"} or "__pycache__" in rel.parts:
            continue
        if rel.parts[0] in {"plans", "scripts", "evidence"}:
            continue
        if path.suffix.lower() in {".png", ".jpg", ".jar", ".apk", ".zip", ".aab"}:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), start=1):
            if pattern.search(line):
                hits.append(f"{rel}:{i}")
    if hits:
        fail(f"عبارة «Pending Execution» موجودة في: {hits[:10]} (AC-0.7)")
    else:
        ok("لا يوجد أي «Pending Execution» في المستودع (AC-0.7)")


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--phase", default=None, help="رقم المرحلة لفحص research.md (مثال: 00)")
    ap.add_argument("--skip-pending-scan", action="store_true")
    args = ap.parse_args(argv)

    check_dataset()
    if args.phase is not None:
        check_research(args.phase)
    if not args.skip_pending_scan:
        check_no_pending_execution()

    print()
    if FAILS:
        print(f"فشل {len(FAILS)} فحصًا من فحوصات بوابة المرحلة 0.")
        return 1
    print("كل فحوصات بوابة المرحلة 0 نجحت.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
