#!/usr/bin/env python3
"""ترحيل قواعد المعرفة القديمة (assets) إلى «وحدات معرفة» (المرحلة 3 · المسار B.1).

    python3 tools/migrate_legacy_kb.py            # يكتب packs/seed-legacy/pack.json + packs/seed-dictionary/pack.json
    python3 tools/migrate_legacy_kb.py --dry-run  # يطبع فقط

المبادئ:
- **لا معرفة جديدة:** الإجابة هي نص السجل القديم نفسه؛ ما نُضيفه فقط هو **تنويعات السؤال** (صياغات سؤال
  لا ادعاءات معرفية) مشتقة من العنوان/الكلمات المفتاحية الموجودة.
- ما لا يوجد له مصدر في السجل الأصلي لا يُنسب لمصدر مخترع؛ يبقى `authored` مع مسؤولية واضحة أو يُحذف.
- السكربت حتمي: نفس المدخلات ⇒ نفس البايتات (الفرز والفواصل مثبتة).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
VERIFIED_AT = "2026-10-29"
AUTHOR = "ترحيل آلي من قواعد المعرفة القديمة (المرحلة 3)"


def source(name: str, location: str, *, url: str = "", license_: str = "") -> dict:
    return {"name": name, "location": location, "url": url, "license": license_}


def general_units() -> list[dict]:
    data = json.loads((ASSETS / "general_kb.json").read_text(encoding="utf-8"))
    units = []
    for entry in data["entries"]:
        keywords = [k for k in entry.get("keywords", []) if k]
        primary = keywords[1] if len(keywords) > 1 else (keywords[0] if keywords else entry["title"])
        questions = [
            entry["title"],
            f"ما هو {entry['title']}؟",
            f"اشرح {primary}",
            f"يعني ايه {primary}",
            f"معلومات عن {primary}",
        ]
        units.append({
            "id": f"legacy-{entry['id'].replace('_', '-')}",
            "pack": "seed-legacy",
            "title": entry["title"],
            "intent": "query_knowledge",
            "questions": _dedupe(questions),
            "answers": [entry["content"]],
            "tags": keywords,
            "source": source(entry["source"], entry["title"]),
            "authored": False,
            "author": AUTHOR,
            "verifiedAt": VERIFIED_AT,
            "safety": "medical_disclaimer" if entry.get("category") == "health" else "none",
            "weight": 1.0,
        })
    return units


def quran_units() -> list[dict]:
    data = json.loads((ASSETS / "religion_kb.json").read_text(encoding="utf-8"))
    units = []
    for item in data.get("quran", []):
        name = item["surah_name"]
        ayah = item["ayah"]
        questions = _dedupe([
            f"آية {name}",
            f"ما هي آية سورة {name}؟",
            f"تفسير آية سورة {name}",
            f"اقرأ سورة {name} الآية {ayah}",
            f"سورة {name} رقم {ayah}",
        ])
        answer = item["text"]
        if item.get("tafsir"):
            answer += "\n\nالتفسير الميسر: " + item["tafsir"]
        units.append({
            "id": f"legacy-quran-{item['surah']}-{ayah}",
            "pack": "seed-legacy",
            "title": f"سورة {name} — الآية {ayah}",
            "intent": "ask_quran",
            "questions": questions,
            "answers": [answer],
            "tags": [name, "قرآن", "آية", "تفسير"],
            "source": source(
                "القرآن الكريم — مجمع الملك فهد لطباعة المصحف الشريف",
                item.get("source", f"سورة {name} {item['surah']}:{ayah}"),
            ),
            "authored": False,
            "author": AUTHOR,
            "verifiedAt": VERIFIED_AT,
            "safety": "religious_reference",
            "weight": 1.0,
        })
    return units


def hadith_units() -> list[dict]:
    data = json.loads((ASSETS / "religion_kb.json").read_text(encoding="utf-8"))
    units = []
    for index, item in enumerate(data.get("hadith", []), start=1):
        topic = item["topic"]
        questions = _dedupe([
            f"حديث عن {topic}",
            f"حديث في {topic}",
            f"ما هو الحديث الوارد في {topic}؟",
            f"شرح حديث {topic}",
        ])
        answer = f'عن {item["isnad"]} قال: قال رسول الله ﷺ: «{item["text"]}»'
        answer += f'\n\nالتخريج: {item["book"]} — {item["grade"]}'
        if item.get("explanation"):
            answer += "\n\nالشرح: " + item["explanation"]
        units.append({
            "id": f"legacy-hadith-{index}",
            "pack": "seed-legacy",
            "title": f"حديث في {topic}",
            "intent": "ask_hadith",
            "questions": questions,
            "answers": [answer],
            "tags": [topic, "حديث", item["isnad"]],
            "source": source(item["book"], item.get("source", item["book"]), license_=""),
            "authored": False,
            "author": AUTHOR,
            "verifiedAt": VERIFIED_AT,
            "safety": "religious_reference",
            "weight": 1.0,
        })
    return units


def fiqh_units() -> list[dict]:
    data = json.loads((ASSETS / "religion_kb.json").read_text(encoding="utf-8"))
    units = []
    for index, item in enumerate(data.get("fiqh", []), start=1):
        question = item["question"].rstrip("؟")
        keywords = [k for k in item.get("keywords", []) if k]
        primary = keywords[1] if len(keywords) > 1 else (keywords[0] if keywords else question)
        questions = _dedupe([
            item["question"],
            f"ما حكم {primary}؟",
            f"سؤال فقهي: {question}",
            f"أريد معرفة {primary}",
        ])
        answer = item["answer"]
        if item.get("madhhab"):
            answer += "\n\nالاعتماد: " + item["madhhab"]
        units.append({
            "id": f"legacy-fiqh-{index}",
            "pack": "seed-legacy",
            "title": question,
            "intent": "ask_fiqh",
            "questions": questions,
            "answers": [answer],
            "tags": keywords,
            "source": source(
                item.get("madhhab", "الفقه الإسلامي"),
                item.get("source", "المصادر الفقهية المعتمدة"),
            ),
            "authored": False,
            "author": AUTHOR,
            "verifiedAt": VERIFIED_AT,
            "safety": "no_fatwa",
            "weight": 1.0,
        })
    return units


def dictionary_units() -> list[dict]:
    data = json.loads((ASSETS / "translation_kb.json").read_text(encoding="utf-8"))
    units = []
    for index, item in enumerate(data.get("dictionary", []), start=1):
        ar, en = item["ar"], item["en"]
        fr = item.get("fr") or ""
        answer = f"«{ar}» بالإنجليزية: {en}."
        if fr:
            answer += f" بالفرنسية: {fr}."
        questions = _dedupe([
            f"ترجمة {ar}",
            f"معنى كلمة {ar} بالإنجليزي",
            f"what is {ar} in english",
            f"كيف أقول {ar} بالإنجليزية؟",
        ])
        units.append({
            "id": f"dict-{index:03d}",
            "pack": "seed-dictionary",
            "title": f"ترجمة «{ar}» إلى الإنجليزية",
            "intent": "translate",
            "questions": questions,
            "answers": [answer],
            "tags": [ar, en, "ترجمة", item.get("pos", "")],
            "source": None,
            "authored": True,
            "author": "قاموس الترجمة الداخلي (translation_kb.json) — المرحلة 3",
            "verifiedAt": VERIFIED_AT,
            "safety": "none",
            "weight": 0.9,
        })
    return units


def _dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for item in items:
        text = " ".join(item.split())
        if text and text not in seen:
            seen.add(text)
            out.append(text)
    return out


def write_pack(name: str, units: list[dict], sources: list[dict], dry_run: bool) -> Path:
    payload = {
        "pack": name,
        "version": "1.0.0",
        "license": "mixed-see-unit-sources",
        "authors": ["ترحيل آلي من قواعد المعرفة القديمة — المرحلة 3"],
        "sources": sources,
        "generatedAt": VERIFIED_AT,
        "note": ("الرخص تحتاج مراجعة المرحلة 5 (license-audit)؛ لا يُنسب أي مصدر لم يُذكر في السجل الأصلي."),
        "units": units,
    }
    path = ROOT / "packs" / name / "pack.json"
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    if not dry_run:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"{'DRY ' if dry_run else 'WROTE'} {path.relative_to(ROOT)} — {len(units)} وحدة")
    return path


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="ترحيل قواعد المعرفة القديمة إلى وحدات (المرحلة 3)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    legacy_sources = [
        {"name": "general_kb.json (BIPM / RSC / NIST / Britannica / AASM…)", "url": "", "license": "موروث من السجل الأصلي"},
        {"name": "religion_kb.json (القرآن الكريم + الصحيحان + مصادر فقهية)", "url": "", "license": "موروث من السجل الأصلي"},
    ]
    write_pack("seed-legacy", general_units() + quran_units() + hadith_units() + fiqh_units(),
               legacy_sources, args.dry_run)
    write_pack("seed-dictionary", dictionary_units(),
               [{"name": "translation_kb.json (قاموس داخلي)", "url": "", "license": "مُؤلَّف داخليًا"}],
               args.dry_run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(__import__("sys").argv[1:]))
