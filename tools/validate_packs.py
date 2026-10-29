#!/usr/bin/env python3
"""مدقق pack.json وتقرير provenance/الرخص — Python قياسي فقط، بلا اتصال شبكي.

الاستخدام:
  python3 tools/validate_packs.py
  python3 tools/validate_packs.py --strict-licenses
  python3 tools/validate_packs.py --write-license-report

يستعين بعقد المرحلة 3 ومدقق التكرار الموجود؛ لا يدّعي أن URL يعمل أو أن الاستعمال قانوني.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from knowledge_units import ContentError, check_duplicates, load_packs  # noqa: E402

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
LICENSES = {
    "CC-BY-SA-2.5": "راجع شرط النسب وShareAlike؛ راجع الرخصة المحددة للوثيقة.",
    "CC-BY-SA-3.0": "راجع شرط النسب وShareAlike.",
    "CC-BY-SA-4.0": "راجع شرط النسب وShareAlike للمادة المشتقة المنشورة.",
    "CC0-1.0": "المصدر يعلن الإهداء للملكية العامة؛ تحقق من نطاق البيانات.",
    "MIT": "يسمح عادةً بالاستخدام التجاري مع إبقاء إشعار الترخيص وحقوق النشر.",
    "PSF-2.0": "رخصة برمجيات Python؛ تحقّق من نطاق المادة المنقولة.",
    "GPL-2.0": "رخصة برمجيات؛ لا تفترض تلقائيًا انطباقها/توافقها على نص الدليل.",
    "Public Domain": "تحقق من الولاية القضائية ومصدر الادعاء.",
    "CC-BY-3.0": "نسب مطلوب؛ راجع الإشعار الكامل وشروط المصدر.",
    "CC-BY-4.0": "نسب ورابط للرخصة وبيان التغييرات عند انطباقها.",
    "CC-BY-ND-3.0": "لا تشارك مادة مشتقة؛ شروط النص قد تمنع التعديل.",
    "CC-BY-SA-2.5-or-later": "نسب وShareAlike؛ افحص ترخيص الصفحة بعينها.",
}


def fail(message: str) -> None:
    raise ValueError(message)


def valid_url(value: str) -> bool:
    try:
        parts = urlsplit(value)
        return parts.scheme in {"http", "https"} and bool(parts.hostname) and " " not in value
    except ValueError:
        return False


def audit_pack_metadata(root: Path, strict_licenses: bool) -> tuple[list[str], list[str], list[dict]]:
    errors: list[str] = []
    warnings: list[str] = []
    sources: list[dict] = []
    packs_dir = root / "packs"
    files = sorted(packs_dir.glob("*/pack.json"))
    if not files:
        errors.append("لا توجد ملفات packs/*/pack.json")
    for path in files:
        relative = path.relative_to(root).as_posix()
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{relative}: JSON/UTF-8 غير صالح: {exc}")
            continue
        if not isinstance(data, dict):
            errors.append(f"{relative}: جذر الملف يجب أن يكون كائنًا")
            continue
        folder = path.parent.name
        if data.get("pack") != folder:
            errors.append(f"{relative}: pack يجب أن يطابق اسم المجلد ({folder})")
        version = str(data.get("version", ""))
        if not SEMVER_RE.fullmatch(version):
            errors.append(f"{relative}: version ليس SemVer: {version!r}")
        authors = data.get("authors")
        if not isinstance(authors, list) or not authors or not all(isinstance(a, str) and a.strip() for a in authors):
            errors.append(f"{relative}: authors يجب أن تكون قائمة أسماء غير فارغة")
        generated = str(data.get("generatedAt", ""))
        if not DATE_RE.fullmatch(generated):
            errors.append(f"{relative}: generatedAt يجب أن يكون YYYY-MM-DD")
        else:
            try:
                date.fromisoformat(generated)
            except ValueError:
                errors.append(f"{relative}: generatedAt تاريخ غير صالح: {generated}")
        pack_sources = data.get("sources", [])
        if not isinstance(pack_sources, list):
            errors.append(f"{relative}: sources يجب أن تكون قائمة")
            pack_sources = []
        for index, source in enumerate(pack_sources):
            if not isinstance(source, dict):
                errors.append(f"{relative}: sources[{index}] يجب أن تكون كائنًا")
                continue
            name = str(source.get("name", "")).strip()
            url = str(source.get("url", "")).strip()
            license_name = str(source.get("license", "")).strip()
            if not name:
                errors.append(f"{relative}: sources[{index}] بلا name")
            if url and not valid_url(url):
                errors.append(f"{relative}: رابط مصدر غير صالح: {url}")
            if not license_name:
                warnings.append(f"{relative}: ترخيص pack-source غير مثبت: {name or '(بلا اسم)'}")
            sources.append({"pack": folder, "unit": "(pack source)", "name": name, "url": url,
                            "license": license_name, "content_license": str(data.get("license", "")),
                            "review_status": str(data.get("reviewStatus", "")),
                            "generated_by": str(data.get("generatedBy", "")),
                            "attribution": str(source.get("attribution", "")).strip(),
                            "location": str(source.get("location", "")).strip()})

        for unit in data.get("units", []) if isinstance(data.get("units"), list) else []:
            if not isinstance(unit, dict):
                continue
            unit_id = str(unit.get("id", "?"))
            src = unit.get("source") if isinstance(unit.get("source"), dict) else {}
            source_name = str(src.get("name", "")).strip()
            source_url = str(src.get("url", "")).strip()
            source_license = str(src.get("license", "")).strip()
            source_location = str(src.get("location", "")).strip()
            if source_url and not valid_url(source_url):
                errors.append(f"{relative}::{unit_id}: رابط المصدر ليس HTTP(S): {source_url}")
            if src:
                sources.append({"pack": folder, "unit": unit_id, "name": source_name, "url": source_url,
                                "license": source_license, "content_license": str(unit.get("contentLicense", data.get("license", ""))),
                                "review_status": str(unit.get("reviewStatus", data.get("reviewStatus", ""))),
                                "generated_by": str(unit.get("generatedBy", data.get("generatedBy", ""))),
                                "attribution": str(src.get("attribution", "")).strip(),
                                "location": source_location})
                if not source_license:
                    warnings.append(f"{relative}::{unit_id}: رخصة المصدر غير محددة")
                if source_license.upper().startswith("CC-BY") and not source_url and not source_location:
                    errors.append(f"{relative}::{unit_id}: مادة CC-BY بلا موضع صالح للنسب")
            elif not unit.get("authored"):
                errors.append(f"{relative}::{unit_id}: يلزم source أو authored:true")
            else:
                sources.append({"pack": folder, "unit": unit_id, "name": "محتوى مؤلف؛ لا مصدر خارجي مُسجل", "url": "",
                                "license": "", "content_license": str(unit.get("contentLicense", data.get("license", ""))),
                                "review_status": str(unit.get("reviewStatus", data.get("reviewStatus", ""))),
                                "generated_by": str(unit.get("generatedBy", data.get("generatedBy", ""))),
                                "attribution": "", "location": "محتوى داخلي"})
            if "programming" in folder:
                answers = unit.get("a", unit.get("answers", []))
                examples = unit.get("examples")
                if not any(isinstance(answer, str) and ("```" in answer or "`" in answer) for answer in answers):
                    errors.append(f"{relative}::{unit_id}: programming answer needs a code example")
                if not isinstance(examples, list) or not examples:
                    errors.append(f"{relative}::{unit_id}: programming unit needs structured examples")
                else:
                    for ex_index, example in enumerate(examples):
                        if not isinstance(example, dict) or not all(str(example.get(k, "")).strip() for k in ("lang", "code", "expected")):
                            errors.append(f"{relative}::{unit_id}: examples[{ex_index}] needs lang/code/expected")
            if "religious_reference" == unit.get("safety") or "religion" in folder:
                if not src or not (source_location or source_url):
                    errors.append(f"{relative}::{unit_id}: religious content needs a specific reference/location")

    if strict_licenses:
        errors.extend(warnings)
    return errors, warnings, sources


def write_license_report(path: Path, sources: list[dict]) -> None:
    unique: dict[tuple[str, str, str, str, str], dict] = {}
    for item in sources:
        key = (item["name"], item["url"], item["license"], item["content_license"], item["location"])
        unique.setdefault(key, item)
    rows = [
        "# تدقيق تراخيص المحتوى والحزم — المرحلة 5",
        "",
        "- تاريخ إنشاء التقرير: 2026-10-29.",
        "- التقرير جرد آلي للبيانات المسجلة في `packs/*/pack.json` فقط؛ ليس رأيًا قانونيًا، ولا إثباتًا بأن الرابط حي أو أن الوحدة صحيحة.",
        "- D31 يسمح بإدراج النص المولد دون مراجعة بشرية، ويضع المحتوى الأصلي المولد تحت CC BY-SA 4.0؛ لا يثبت ذلك صحة الحقائق أو حقوق أي نص خارجي مستشهد به.",
        "- لم تُجرَ طلبات HTTP. حالة `reviewStatus` و`generatedBy` تُعرض للتتبع؛ المدقق لا ينشئ مراجعة قانونية أو علمية.",
        "",
        "## سجل كل موضع مصدر ومحتوى",
        "",
        "| الحزمة/الوحدة | المصدر | الموضع/الرابط | ترخيص المصدر/النقل | ترخيص محتوى الحزمة | الالتزام/التجاري | المنشأ/المراجعة |",
        "|---|---|---|---|---|---|---|",
    ]
    for item in sorted(unique.values(), key=lambda x: (x["pack"], x["unit"], x["name"])):
        source_lic = item["license"]
        content_lic = item.get("content_license", "")
        normalized = content_lic.upper().replace(" ", "")
        note = next((text for key, text in LICENSES.items() if key.upper().replace(" ", "") == normalized),
                    "ترخيص محتوى الحزمة غير محدد بما يكفي؛ راجع السجل قبل التوزيع.")
        commercial = "يسمح بالاستخدام التجاري مع النسب وShareAlike" if "CC-BY-SA-4.0" in normalized else "غير معلوم — راجع شروط المحتوى"
        if "CC-BY-NC" in normalized:
            commercial = "غير مسموح وفق القيد NC"
        elif "CC-BY-ND" in normalized:
            commercial = "مقيّد؛ لا مشاركة لمادة مشتقة"
        location = item["url"] or item["location"] or "—"
        if item["name"].startswith("محتوى مؤلف؛ لا مصدر خارجي"):
            status = "لا مصدر خارجي منقول؛ يُراجع ترخيص المحتوى المسجل"
        elif source_lic.startswith("reference-only"):
            status = "المرجع للاستدلال فقط؛ لا ادعاء نسخ/ترخيص للمصدر"
        else:
            status = "مصدر ترخيصه مجهول" if not source_lic else "المصدر مسجل؛ لم يُفحص الرابط آليًا"
        provenance = f"{item.get('generated_by') or 'محتوى موروث/غير موسوم'}; {item.get('review_status') or 'لا توجد حالة مراجعة مسجلة'}"
        pack_unit = f"`{item['pack']}` / `{item['unit']}`"
        def cell(text: str) -> str:
            return text.replace("|", "\\|").replace("\n", " ")
        rows.append(f"| {cell(pack_unit)} | {cell(item['name'] or 'غير مسمى')} | {cell(location)} | {cell(source_lic or 'غير محدد/لا ينطبق')} | {cell(content_lic or 'غير محدد')} | {cell(note)} · {commercial} | {cell(provenance)}; {status} |")
    rows.extend([
        "",
        "## خلاصة مراجعة المصادر",
        "",
        "- MDN: لا تُعمّم رخصة الوثائق على عينات الشيفرة؛ وثائق MDN غالبًا CC BY-SA 2.5 أو أحدث، وبعض الأمثلة CC0، ويُراجع الترخيص على الصفحة/المثال المحدد. انظر https://developer.mozilla.org/en-US/docs/MDN/Writing_guidelines/Attrib_copyright_license.",
        "- ويكيبيديا/Wikimedia: أغلب النصوص CC BY-SA 4.0 أو تراخيص محددة أخرى؛ يلزم رابط/نسب وبيان تعديلات وShareAlike حيث ينطبق. الصور/الوسائط مستقلة الترخيص. لم تُضف لقطة ويكيبيديا في هذه المرحلة.",
        "- Wikidata: البيانات البنيوية CC0، وليس كل ما يشير إليه العنصر (ويكيبيديا/الصور). تُستحسن نسبة المصدر لأغراض التتبع.",
        "- OSCAR: CC0 لتغليف/بيانات وصفية معينة لا يمنح ترخيصًا لنصوص Common Crawl. لم يُستورد أي نص OSCAR.",
        "- القرآن/Tanzil: شروط Tanzil تسمح بنسخ النص حرفيًا فقط مع الإشعار والرابط ولا تجيز تغييره؛ لا يُدمج التفسير تحت هذا الترخيص. لم يُستورد نص Tanzil في المرحلة.",
        "- المحتوى الديني الموروث في `seed-legacy` يحمل أسماء كتب/مواضع مرجعية لكن بعضها بلا رابط أو تفاصيل طبعة/إذن. وجود مرجع علمي لا يثبت حق إعادة توزيع النص؛ يلزم فحص بشري مختص قبل شحنه أو توسيعه.",
        "- وحدات `daily-life`, `study`, `money`, `health`, `facts`, `religion`, `code-snippets` والإضافات البرمجية مولدة آليًا دون مراجعة بشرية وفق D31، وموسومة بذلك. المحتوى الأصلي معلن CC BY-SA 4.0؛ المواد الخارجية ليست منقولة، والاستشهاد لا ينقل ترخيص المصدر.",
        "- حزمة `seed-legacy` والمراجع الدينية الموروثة لا تزال تحمل تراخيص مصدر غير مثبتة. إدراج الحزمة لا يعني أن حقوق طبعات التفاسير/الكتب أو نصوص الحديث حُسمت.",
        "- لا يصنف هذا الملف الحالة إلى موافقة قانونية نهائية ولا يؤكد دقة المادة. على مالك المشروع مراجعة أي مطالبة حقوق طرف ثالث قبل التوزيع العام؛ لا يُنسب ذلك إلى مراجعة بشرية تمت.",
        "",
        "## التزامات النسب التي يلزم تنفيذها قبل النشر",
        "",
        "1. إظهار اسم المصدر ورابط الصفحة/الموضع، ونص الرخصة ورابطها حيث يلزم، والإشارة لأي تغيير.",
        "2. تجميع إشعارات CC BY-SA/توافق الرخصة بالمثل وعدم وضع قيود إضافية على إعادة استخدام المادة المرخصة.",
        "3. فصل الأكواد المرخصة CC0 عن الوثائق المرخصة CC BY-SA عند اقتباسها.",
        "4. عدم ادعاء أن OSCAR/واجهة API/ذكر كتاب يوفر حق تنزيل أو إعادة توزيع المحتوى.",
        "5. أخذ إذن مكتوب عندما شروط المصدر أو بيانات حقوق النص الديني غير واضحة.",
        "",
    ])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(rows), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict-licenses", action="store_true", help="اجعل كل ترخيص مجهول خطأً")
    parser.add_argument("--write-license-report", action="store_true", help="اكتب evidence/phase-05/license-audit.md")
    args = parser.parse_args()
    errors, warnings, sources = audit_pack_metadata(ROOT, args.strict_licenses)
    try:
        units, _packs, pack_warnings = load_packs(ROOT)
        check_duplicates(units)
        warnings.extend(pack_warnings)
    except (ContentError, json.JSONDecodeError, OSError, TypeError, ValueError) as exc:
        errors.append(f"فشل عقد الوحدات/التكرار: {exc}")
    if args.write_license_report:
        write_license_report(ROOT / "evidence/phase-05/license-audit.md", sources)
    print(f"الوحدات المفحوصة: {len(units) if 'units' in locals() else 0}; المصادر المسجلة: {len(sources)}")
    for warning in sorted(set(warnings)):
        print(f"تحذير: {warning}")
    for error in errors:
        print(f"خطأ: {error}")
    print("النتيجة: " + ("FAIL" if errors else "PASS مع تحفظات الترخيص" if warnings else "PASS"))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
