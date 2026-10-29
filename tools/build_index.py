#!/usr/bin/env python3
"""المرحلة 3 — خط بناء فهرس وحدات المعرفة (زمن البناء فقط، بلا شبكة وبلا اعتماديات).

    python3 tools/build_index.py --build        # يبني app/src/main/assets/knowledge_index.json + manifest
    python3 tools/build_index.py --check        # يفشل إذا كان الفهرس المشحون غير مطابق للمصادر (حارس نضارة)
    python3 tools/build_index.py --self-test    # يفحص تطابق مُقطِّع Python مع توائم Kotlin
    python3 tools/build_index.py --stats        # إحصاءات الحزم والفهرس

مخرجات البناء:
- `app/src/main/assets/knowledge_index.json` (يقرأه Kotlin عبر `UnitIndexCodec`).
- `knowledge/unit/index-manifest.json` (هاش المحتوى + هاش الملف + إحصاءات).

كل الحساب (TF مرجّح، df، متوسط الطول، درجة BM25+ المرجعية) يحدث هنا — وقت التشغيل يقطّع الاستعلام فقط (D24).
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import knowledge_units as ku  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ASSET_PATH = ROOT / "app" / "src" / "main" / "assets" / "knowledge_index.json"
MANIFEST_PATH = ROOT / "knowledge" / "unit" / "index-manifest.json"

FAILS: list[str] = []
WARNINGS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)
    print(f"FAIL · {msg}")


def ok(msg: str) -> None:
    print(f"PASS · {msg}")


def warn(msg: str) -> None:
    WARNINGS.append(msg)
    print(f"WARN · {msg}")


def build() -> tuple[dict, str, list]:
    if not ku.FRANCO_LEXICON_PATH.exists():
        warn("قاموس الفرانكو المصدر (knowledge/unit/franco-lexicon.json) مفقود — النصوص الفرانكو في الوحدات "
             "ستُفهرس كما هي. أعِد توليده: ./tools/offline-typecheck/phase03/run.sh --emit-lexicon")
    units, pack_info, warnings = ku.load_packs(ROOT)
    for w in warnings:
        warn(w)
    if not units:
        raise ku.ContentError("لا توجد وحدات معرفة في packs/ — لا يمكن بناء فهرس فارغ")
    ku.check_duplicates(units)
    built_at = datetime.date.today().isoformat()
    index = ku.build_index(units, pack_info, built_at)
    payload = ku.dump_index(index)
    return index, payload, units


def write_outputs(index: dict, payload: str) -> None:
    ASSET_PATH.parent.mkdir(parents=True, exist_ok=True)
    ASSET_PATH.write_text(payload, encoding="utf-8")
    asset_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    manifest = {
        "schemaVersion": ku.SCHEMA_VERSION,
        "builderVersion": ku.BUILDER_VERSION,
        "builtAt": index["builtAt"],
        "units": index["stats"]["units"],
        "contentHash": index["contentHash"],
        "assetSha256": asset_sha,
        "assetBytes": len(payload.encode("utf-8")),
        "avgDocLength": index["stats"]["avgDocLength"],
        "bm25ref": index["stats"]["bm25ref"],
        "packs": index["sourcePacks"],
        "howToRebuild": "python3 tools/build_index.py --build",
    }
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cmd_build() -> int:
    try:
        index, payload, units = build()
    except ku.ContentError as exc:
        fail(f"محتوى غير صالح: {exc}")
        return 1
    write_outputs(index, payload)
    ok(f"بُني الفهرس: {index['stats']['units']} وحدة، {len(payload.encode('utf-8'))} بايت")
    ok(f"contentHash = {index['contentHash'][:16]}… · bm25ref = {index['stats']['bm25ref']:.4f} · "
       f"avgDocLength = {index['stats']['avgDocLength']}")
    for pack in index["sourcePacks"]:
        print(f"     - {pack['pack']} v{pack['version']}: {pack['units']} وحدة ({pack['license'] or 'بلا رخصة معلنة'})")
    print(f"     كُتب: {ASSET_PATH.relative_to(ROOT)}")
    print(f"     كُتب: {MANIFEST_PATH.relative_to(ROOT)}")
    return 0


def cmd_check() -> int:
    if not ASSET_PATH.exists() or not MANIFEST_PATH.exists():
        fail("الفهرس المشحون أو manifest مفقود — شغّل --build")
        return 1
    try:
        index, _, _ = build()
    except ku.ContentError as exc:
        fail(f"محتوى غير صالح: {exc}")
        return 1

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    committed_payload = ASSET_PATH.read_text(encoding="utf-8")
    committed_sha = hashlib.sha256(committed_payload.encode("utf-8")).hexdigest()
    committed_index = json.loads(committed_payload)

    if manifest.get("assetSha256") != committed_sha:
        fail("هاش الملف المشحون لا يطابق manifest — الملف عُدّل يدويًا أو تلف")
    else:
        ok("هاش ملف الفهرس مطابق لـ manifest")

    if committed_index.get("contentHash") != manifest.get("contentHash"):
        fail("contentHash داخل الملف لا يطابق manifest")
    else:
        ok("contentHash داخل الملف مطابق لـ manifest")

    if index["contentHash"] != committed_index.get("contentHash"):
        fail("الفهرس المشحون غير مطابق لمحتوى packs/ الحالي — شغّل: python3 tools/build_index.py --build")
        print(f"       المتوقع: {index['contentHash'][:16]}… · المشحون: {str(committed_index.get('contentHash'))[:16]}…")
    else:
        ok("الفهرس المشحون مطابق لمحتوى packs/ (حارس النضارة)")

    if index["builtAt"] != committed_index.get("builtAt"):
        warn(f"تاريخ البناء مختلف (مشحون {committed_index.get('builtAt')} ≠ اليوم {index['builtAt']}) — محتوى فقط، ليس عدم نضارة")

    return 0 if not FAILS else 1


def cmd_self_test() -> int:
    count, problems = ku.verify_fixtures(ROOT)
    if problems:
        for p in problems:
            fail(p)
        return 1
    ok(f"توائم التقطيع متطابقة: {count} نصًا (Kotlin ↔ Python)")
    return 0


def cmd_stats() -> int:
    try:
        index, payload, _ = build()
    except ku.ContentError as exc:
        fail(f"محتوى غير صالح: {exc}")
        return 1
    print(f"الوحدات: {index['stats']['units']}")
    print(f"مصطلحات الفهرس (df): {len(index['df'])}")
    print(f"متوسط طول المستند: {index['stats']['avgDocLength']}")
    print(f"الدرجة المرجعية bm25ref: {index['stats']['bm25ref']}")
    print(f"حجم الملف المتوقع: {len(payload.encode('utf-8'))} بايت")
    for pack in index["sourcePacks"]:
        print(f"  - {pack['pack']}: {pack['units']}")
    return 0


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="بناء فهرس وحدات المعرفة — المرحلة 3")
    group = ap.add_mutually_exclusive_group(required=True)
    group.add_argument("--build", action="store_true", help="بناء الفهرس وكتابته")
    group.add_argument("--check", action="store_true", help="فحص نضارة الفهرس المشحون")
    group.add_argument("--self-test", action="store_true", help="فحص توائم التقطيع")
    group.add_argument("--stats", action="store_true", help="إحصاءات فقط بلا كتابة")
    args = ap.parse_args(argv)

    if args.build:
        code = cmd_build()
    elif args.check:
        code = cmd_check()
    elif args.self_test:
        code = cmd_self_test()
    else:
        code = cmd_stats()

    if WARNINGS:
        print(f"INFO · {len(WARNINGS)} تحذيرًا (لا تمنع البناء)")
    return code


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
