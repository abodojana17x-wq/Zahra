#!/usr/bin/env python3
"""المرحلة 6 — مدقق الإطلاق والصدق التوثيقي (Python قياسي، بلا شبكة).

يفحص ما يمكن فحصه فعليًا قبل الإطلاق:
  1. تطابق نسخة التطبيق في build.gradle.kts مع release/release_manifest.json.
  2. وجود وثائق الاستخدام/القيود/النتائج/الخصوصية/الأمن/التوزيع/بطاقة النظام،
     والعناوين الإلزامية داخلها.
  3. أن القيود مصدر واحد (release/limitations.json) تُولَّد منه docs/LIMITATIONS.md
     وapp/src/main/assets/release_info.json، وأن الملفين محدثان.
  4. عدم وجود أسرار أو متاجر مفاتيح متعقبة، وسياسة توقيع لا تحمل كلمات مرور.
  5. أن الادعاءات المقاسة لا تُنشر بلا ملف نتيجة/هاش/أمر/تاريخ، وأن «not-run»
     تظهر صراحةً في الوثائق.
  6. أن القائمة/البيانات الوصفية لا تدّعي شبكة: لا إذن INTERNET، والنسخ الاحتياطي
     والنقل بين الأجهزة مستثنيان صراحةً.
  7. أن نسخ الواجهة لا تحتوي ادعاءات مطلقة غير مدعومة.
  8. أن عدد الحزم والوحدات في الحزم والفهرس يطابق ما يعلنه المانيفست.

⚠️ المدقق لا يثبت صحة المحتوى ولا الأمان ولا الأداء. نجاحه يعني «الوثائق والملفات
متسقة مع الحالة المعلنة»، لا «الإصدار جاهز للنشر».

الاستخدام:
    python3 tools/phase06/release_audit.py --write     # توليد الملفات المشتقة
    python3 tools/phase06/release_audit.py --check     # فحص (الافتراضي)
    python3 tools/phase06/release_audit.py --check --strict   # تحذيرات = فشل
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence

ROOT = Path(__file__).resolve().parents[2]
RELEASE_DIR = ROOT / "release"
LIMITATIONS_SOURCE = RELEASE_DIR / "limitations.json"
MANIFEST = RELEASE_DIR / "release_manifest.json"
GENERATED_LIMITATIONS = ROOT / "docs" / "LIMITATIONS.md"
GENERATED_ASSET = ROOT / "app" / "src" / "main" / "assets" / "release_info.json"

CATEGORY_LABELS = {
    "measurement": "القياس والإثبات",
    "capability": "القدرات",
    "content": "المحتوى",
    "privacy": "الخصوصية",
    "security": "الأمن",
    "platform": "المنصة والتوافق",
    "process": "العملية والمراجعة",
    "legal": "التراخيص والحقوق",
    "release": "التوزيع والإصدار",
    "accessibility": "الوصول والاستخدام",
}
SEVERITY_LABELS = {"critical": "حاسم", "major": "جسيم", "minor": "محدود", "info": "معلومة"}
SEVERITY_ORDER = {"critical": 0, "major": 1, "minor": 2, "info": 3}
ALLOWED_CATEGORIES = set(CATEGORY_LABELS)
ALLOWED_SEVERITIES = set(SEVERITY_LABELS)
ID_RE = re.compile(r"^LIM-\d{3}$")

REQUIRED_DOCS: dict[str, list[str]] = {
    "docs/USAGE.md": ["البداية السريعة", "البناء من المصدر", "الأذونات", "ما لا يفعله", "استكشاف الأخطاء"],
    "docs/LIMITATIONS.md": ["القيود المعروفة", "كيف يُضاف قيد", "ما لا تعنيه"],
    "docs/RESULTS.md": ["الحالة الحالية", "لا توجد نتائج", "كيف تُنتج", "قواعد النشر"],
    "docs/EVALUATION-PROTOCOL.md": ["التسجيل المسبق", "العمى وتبديل الموضع", "التحليل الإحصائي", "ما لا يُدَّعى"],
    "docs/PERFORMANCE.md": ["الحالة الحالية", "ما يُقاس", "البروتوكول", "القيود"],
    "docs/PRIVACY.md": ["بيانات محفوظة", "الشبكة", "النسخ الاحتياطي", "الحذف", "الحدود"],
    "docs/SECURITY.md": ["نموذج التهديد", "الأسرار", "سلسلة التوريد", "الإبلاغ"],
    "docs/DISTRIBUTION.md": ["حالة التوزيع", "التوقيع", "قابل لإعادة الإنتاج", "التحقق"],
    "docs/SYSTEM-CARD.md": ["الاستخدام المقصود", "خارج النطاق", "القياس", "القيود", "الاعتبارات"],
    "docs/RELEASE-CHECKLIST.md": ["قبل الإصدار", "التوقيع", "بعد الإصدار"],
    "docs/POST-RELEASE.md": ["ZAHRA-POST-001", "D1", "D2", "D3", "D7", "الوضع المتقدم"],
}

PERMISSION_NAMES = [
    "POST_NOTIFICATIONS",
    "RECORD_AUDIO",
    "VIBRATE",
    "SCHEDULE_EXACT_ALARM",
    "RECEIVE_BOOT_COMPLETED",
]

FORBIDDEN_UI_CLAIMS = [
    "حماية كاملة",
    "آمن 100",
    "آمنة 100",
    "مشفر محلياً 100",
    "مشفرة محلياً 100",
    "دقة 100",
    "مضمون 100",
    "لا أخطاء",
    "يتفوق على",
    "مستحيل الاختراق",
    "بدون أي عيوب",
    "أفضل مساعد",
    "100% محلي",
    "100% محلياً",
    "محلي 100%",
    "محلية 100%",
]

SECRET_FILENAME_PATTERNS = [
    re.compile(r".*\.jks$", re.I),
    re.compile(r".*\.keystore$", re.I),
    re.compile(r".*\.p12$", re.I),
    re.compile(r".*\.pfx$", re.I),
    re.compile(r".*\.pem$", re.I),
    re.compile(r"(^|/)keystore\.properties$", re.I),
    re.compile(r"(^|/)id_rsa$", re.I),
    re.compile(r"(^|/)\.env$", re.I),
]
SECRET_CONTENT_PATTERNS = [
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("github-token", re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}")),
    ("github-fine-grained", re.compile(r"github_pat_[A-Za-z0-9_]{20,}")),
    ("google-api-key", re.compile(r"AIza[0-9A-Za-z_\-]{30,}")),
    ("openai-key", re.compile(r"(?<![A-Za-z0-9])sk-[A-Za-z0-9]{20,}")),
    ("slack-token", re.compile(r"xox[baprs]-[A-Za-z0-9\-]{10,}")),
    ("aws-key", re.compile(r"AKIA[0-9A-Z]{16}")),
]
TEXT_SUFFIXES = {".md", ".txt", ".kt", ".kts", ".py", ".json", ".jsonl", ".xml", ".yml", ".yaml", ".gradle", ".sh", ".toml", ".properties"}


@dataclass
class Finding:
    level: str
    code: str
    message: str


@dataclass
class Audit:
    findings: list[Finding] = field(default_factory=list)

    def fail(self, code: str, message: str) -> None:
        self.findings.append(Finding("FAIL", code, message))

    def warn(self, code: str, message: str) -> None:
        self.findings.append(Finding("WARN", code, message))

    def passed(self, message: str) -> None:
        print(f"PASS · {message}")

    def info(self, message: str) -> None:
        print(f"INFO · {message}")

    def check(self, condition: bool, code: str, ok: str, bad: str) -> bool:
        if condition:
            self.passed(ok)
            return True
        self.fail(code, bad)
        print(f"FAIL · {bad}")
        return False

    @property
    def failed(self) -> int:
        return sum(1 for finding in self.findings if finding.level == "FAIL")

    @property
    def warnings(self) -> int:
        return sum(1 for finding in self.findings if finding.level == "WARN")


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def sha256_file(path: Path) -> str:
    import hashlib

    return hashlib.sha256(path.read_bytes()).hexdigest()


# --------------------------------------------------------------------------------------
# القيود: تحميل/تحقق/توليد
# --------------------------------------------------------------------------------------
def load_limitations(path: Path = LIMITATIONS_SOURCE) -> dict:
    data = read_json(path)
    errors: list[str] = []
    if data.get("schema") != 1:
        errors.append("schema يجب أن يساوي 1")
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", str(data.get("updatedAt", ""))):
        errors.append("updatedAt يجب أن يكون YYYY-MM-DD")
    limitations = data.get("limitations")
    if not isinstance(limitations, list) or len(limitations) < 15:
        errors.append("limitations يجب أن تكون قائمة بـ15 قيدًا على الأقل")
        limitations = []
    seen: set[str] = set()
    for index, item in enumerate(limitations, start=1):
        label = item.get("id") or f"#{index}"
        if not ID_RE.match(str(item.get("id", ""))):
            errors.append(f"{label}: id بصيغة خاطئة")
        if item.get("id") in seen:
            errors.append(f"{label}: id مكرر")
        seen.add(item.get("id"))
        if item.get("category") not in ALLOWED_CATEGORIES:
            errors.append(f"{label}: category غير مسموح ({item.get('category')})")
        if item.get("severity") not in ALLOWED_SEVERITIES:
            errors.append(f"{label}: severity غير مسموح ({item.get('severity')})")
        for key in ("titleAr", "detailAr"):
            value = str(item.get(key, "")).strip()
            if len(value) < 8:
                errors.append(f"{label}: {key} قصير جدًا")
        if not isinstance(item.get("inApp"), bool):
            errors.append(f"{label}: inApp يجب أن يكون boolean")
        evidence = str(item.get("evidence", "")).strip()
        if not evidence:
            errors.append(f"{label}: evidence مفقود")
        elif not evidence.startswith("owner-decision:"):
            evidence_path = evidence.split("#", 1)[0]
            if not (ROOT / evidence_path).exists():
                errors.append(f"{label}: مسار الدليل غير موجود ({evidence_path})")
    if errors:
        raise ValueError("; ".join(errors))
    data["limitations"] = sorted(
        limitations,
        key=lambda item: (SEVERITY_ORDER[item["severity"]], item["id"]),
    )
    return data


def _link_from_docs(target: str) -> str:
    path = target.split("#", 1)[0]
    rel_path = os.path.relpath(ROOT / path, GENERATED_LIMITATIONS.parent).replace(os.sep, "/")
    anchor = f"#{target.split('#', 1)[1]}" if "#" in target else ""
    return f"[`{target}`]({rel_path}{anchor})"


def render_limitations(data: dict, manifest: dict) -> str:
    limitations = data["limitations"]
    counts: dict[str, int] = {}
    worst: dict[str, str] = {}
    for item in limitations:
        category = item["category"]
        counts[category] = counts.get(category, 0) + 1
        current = worst.get(category)
        if current is None or SEVERITY_ORDER[item["severity"]] < SEVERITY_ORDER[current]:
            worst[category] = item["severity"]
    severity_counts = {severity: sum(1 for item in limitations if item["severity"] == severity) for severity in SEVERITY_ORDER}
    lines = [
        "<!-- مولّد آليًا من release/limitations.json بواسطة tools/phase06/release_audit.py — لا تُعدّله يدويًا. -->",
        "",
        "# القيود المعروفة — زهرة " + str(manifest["app"]["versionName"]),
        "",
        "> هذه القائمة جزء من الإصدار، لا ملحق اختياري. كل قيد هنا معروف قبل النشر، ومصدره",
        "> [`release/limitations.json`](../release/limitations.json). صفحة «الاستخدام والحدود»",
        "> داخل التطبيق تُعرض من الملف نفسه بعد توليده إلى `app/src/main/assets/release_info.json`.",
        "",
        f"**آخر تحديث:** {data['updatedAt']} · **القناة:** {manifest['release']['channel']} · "
        f"**حالة القياس:** {manifest['measurement']['status']}",
        "",
        "| الخطورة | العدد |",
        "|---|---|",
    ]
    lines += [f"| {SEVERITY_LABELS[s]} | {severity_counts[s]} |" for s in SEVERITY_ORDER]
    lines += [
        "",
        "| الفئة | العدد | أعلى خطورة |",
        "|---|---|---|",
    ]
    for category in CATEGORY_LABELS:
        if counts.get(category):
            lines.append(f"| {CATEGORY_LABELS[category]} | {counts[category]} | {SEVERITY_LABELS[worst[category]]} |")
    lines += ["", "---", ""]
    for category in CATEGORY_LABELS:
        items = [item for item in limitations if item["category"] == category]
        if not items:
            continue
        lines += [f"## {CATEGORY_LABELS[category]}", ""]
        for item in sorted(items, key=lambda i: (SEVERITY_ORDER[i["severity"]], i["id"])):
            lines += [
                f"### {item['id']} — {item['titleAr']} ({SEVERITY_LABELS[item['severity']]})",
                "",
                item["detailAr"],
                "",
                f"**الدليل/المرجع:** {_link_from_docs(item['evidence'])}",
                "",
            ]
    lines += [
        "## كيف يُضاف قيد جديد؟",
        "",
        "1. أضف العنصر إلى `release/limitations.json` بمعرّف جديد وفئة وخطورة ودليل موجود.",
        "2. شغّل `python3 tools/phase06/release_audit.py --write` لتحديث هذا الملف وملف التطبيق.",
        "3. شغّل `python3 tools/phase06/release_audit.py --check`.",
        "4. لا تحذف قيدًا لأنه يبدو محرجًا؛ أصلحه أو وثّق أنه مقبول بوعي.",
        "",
        "## ما لا تعنيه هذه القائمة",
        "",
        "- ليست تصريحًا بالأمان أو الدقة؛ هي إفصاح عن حدود معروفة.",
        "- لا تحصر كل العيوب؛ قد توجد مشكلات لم تُكتشف بعد.",
        "- لا تُغني عن مراجعة قانونية أو أمنية أو بشرية مستقلة.",
        "",
    ]
    return "\n".join(lines)


def render_in_app(data: dict, manifest: dict) -> str:
    channel_ar = {
        "source-only": "مصدر فقط — لا يوجد APK منشور",
        "github-release": "إصدار على GitHub",
        "store": "منشور في متجر",
    }.get(manifest["release"]["channel"], str(manifest["release"]["channel"]))
    measurement_ar = {
        "not-run": "لم يُشغَّل قياس كامل بعد",
        "measured": "توجد نتائج مقاسة منشورة",
    }.get(manifest["measurement"]["status"], str(manifest["measurement"]["status"]))
    payload = {
        "schema": 1,
        "app": manifest["app"]["nameAr"],
        "versionName": manifest["app"]["versionName"],
        "versionCode": manifest["app"]["versionCode"],
        "channel": manifest["release"]["channel"],
        "channelAr": channel_ar,
        "measurementStatus": manifest["measurement"]["status"],
        "measurementAr": measurement_ar,
        "updatedAt": data["updatedAt"],
        "generatedFrom": rel(LIMITATIONS_SOURCE),
        "docsHintAr": "التفاصيل الكاملة في docs/LIMITATIONS.md وdocs/RESULTS.md داخل المستودع.",
        "limitations": [
            {
                "id": item["id"],
                "category": item["category"],
                "categoryAr": CATEGORY_LABELS[item["category"]],
                "severity": item["severity"],
                "severityAr": SEVERITY_LABELS[item["severity"]],
                "titleAr": item["titleAr"],
                "detailAr": item["detailAr"],
            }
            for item in data["limitations"]
            if item.get("inApp")
        ],
    }
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


# --------------------------------------------------------------------------------------
# فحوصات
# --------------------------------------------------------------------------------------
def parse_gradle(root: Path = ROOT) -> dict:
    text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

    def grab(pattern: str) -> str | None:
        match = re.search(pattern, text)
        return match.group(1) if match else None

    return {
        "applicationId": grab(r'applicationId\s*=\s*"([^"]+)"'),
        "versionName": grab(r'versionName\s*=\s*"([^"]+)"'),
        "versionCode": grab(r"versionCode\s*=\s*(\d+)"),
        "minSdk": grab(r"minSdk\s*=\s*(\d+)"),
        "targetSdk": grab(r"targetSdk\s*=\s*(\d+)"),
    }


def validate_limitations_generated(audit: Audit, data: dict, manifest: dict, write: bool) -> None:
    docs_text = render_limitations(data, manifest)
    asset_text = render_in_app(data, manifest)
    if write:
        GENERATED_LIMITATIONS.parent.mkdir(parents=True, exist_ok=True)
        GENERATED_LIMITATIONS.write_text(docs_text, encoding="utf-8")
        GENERATED_ASSET.parent.mkdir(parents=True, exist_ok=True)
        GENERATED_ASSET.write_text(asset_text, encoding="utf-8")
        audit.passed(f"توليد {rel(GENERATED_LIMITATIONS)} و{rel(GENERATED_ASSET)}")
    else:
        actual_docs = GENERATED_LIMITATIONS.read_text(encoding="utf-8") if GENERATED_LIMITATIONS.exists() else ""
        if actual_docs != docs_text:
            audit.fail("generated-limitations", f"{rel(GENERATED_LIMITATIONS)} غير محدّث — شغّل --write")
        else:
            audit.passed(f"{rel(GENERATED_LIMITATIONS)} مطابق للمصدر")
        actual_asset = GENERATED_ASSET.read_text(encoding="utf-8") if GENERATED_ASSET.exists() else ""
        if actual_asset != asset_text:
            audit.fail("generated-asset", f"{rel(GENERATED_ASSET)} غير محدّث — شغّل --write")
        else:
            audit.passed(f"{rel(GENERATED_ASSET)} مطابق للمصدر")


def check_versions(audit: Audit, manifest: dict) -> None:
    gradle = parse_gradle()
    app = manifest.get("app", {})
    for key in ("applicationId", "versionName", "minSdk", "targetSdk"):
        expected = str(app.get(key))
        actual = str(gradle.get(key))
        audit.check(actual == expected, f"version-{key}", f"{key}={expected}", f"{key}: build.gradle={actual} والمانيفست={expected}")
    audit.check(str(gradle.get("versionCode")) == str(app.get("versionCode")), "version-code",
                f"versionCode={app.get('versionCode')}",
                f"versionCode: build.gradle={gradle.get('versionCode')} والمانيفست={app.get('versionCode')}")
    audit.check(bool(gradle.get("versionName")) and "." in str(gradle.get("versionName")), "version-name-format",
                "versionName بصيغة رقمية معلنة", "versionName غير صالح")


def check_docs(audit: Audit, manifest: dict) -> None:
    docs = manifest.get("docs", {})
    for key, path in docs.items():
        audit.check((ROOT / path).exists(), f"docs-exists-{key}", f"وثيقة موجودة: {path}", f"وثيقة مفقودة ({key}): {path}")
    for path, headings in REQUIRED_DOCS.items():
        file = ROOT / path
        if not file.exists():
            continue
        text = file.read_text(encoding="utf-8")
        if path != "docs/LIMITATIONS.md":
            audit.check(len(text) > 400, f"docs-size-{path}", f"{path}: {len(text)} حرفًا", f"{path}: قصير جدًا")
        missing = [heading for heading in headings if heading not in text]
        audit.check(not missing, f"docs-headings-{path}", f"{path}: العناوين الإلزامية موجودة",
                    f"{path}: عناوين مفقودة: {missing}")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    for needed in ("docs/README.md", "docs/USAGE.md", "docs/LIMITATIONS.md", "docs/RESULTS.md", "docs/PRIVACY.md", "docs/DISTRIBUTION.md"):
        audit.check(needed in readme, f"readme-link-{needed}", f"README يربط {needed}", f"README لا يربط {needed}")
    audit.check("المرحلة 6" in readme, "readme-phase6", "README يذكر المرحلة 6", "README لا يذكر المرحلة 6")
    changelog = ROOT / "CHANGELOG.md"
    if changelog.exists():
        text = changelog.read_text(encoding="utf-8")
        audit.check("## [3.1]" in text and "غير منشور" in text, "changelog-version",
                    "CHANGELOG يعلن 3.1 كغير منشور", "CHANGELOG لا يعلن 3.1 كغير منشور")
    else:
        audit.fail("changelog-missing", "CHANGELOG.md مفقود")


def check_links(audit: Audit) -> None:
    files = [ROOT / "README.md", ROOT / "CHANGELOG.md"]
    for folder in ("docs", "evidence/phase-06"):
        files.extend(sorted((ROOT / folder).glob("*.md")) if (ROOT / folder).exists() else [])
    link_re = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
    broken: list[str] = []
    checked = 0
    for file in files:
        if not file.exists():
            continue
        text = file.read_text(encoding="utf-8")
        in_fence = False
        for line in text.splitlines():
            if line.strip().startswith("```"):
                in_fence = not in_fence
                continue
            if in_fence:
                continue
            for target in link_re.findall(line):
                target = target.strip().strip("<>").split(" ")[0].strip()
                if not target or target.startswith(("#", "http://", "https://", "mailto:", "tel:", "data:")):
                    continue
                if "{{" in target or target.startswith("/"):
                    continue
                path_part = target.split("#", 1)[0]
                if not path_part:
                    continue
                resolved = (file.parent / path_part).resolve()
                checked += 1
                try:
                    resolved.relative_to(ROOT.resolve())
                except ValueError:
                    broken.append(f"{rel(file)} → {target} (خارج المستودع)")
                    continue
                if not resolved.exists():
                    broken.append(f"{rel(file)} → {target}")
    audit.check(not broken, "broken-links", f"فحص {checked} رابطًا نسبيًا: لا كسر",
                f"روابط مكسورة: {broken[:10]}")


def android_ns(tag: str) -> str:
    return f"{{http://schemas.android.com/apk/res/android}}{tag}"


def check_manifest_and_backup(audit: Audit, manifest: dict) -> None:
    app_xml = ROOT / "app/src/main/AndroidManifest.xml"
    text = app_xml.read_text(encoding="utf-8")
    root = ET.fromstring(text)
    permissions: list[str] = []
    application = None
    for child in root:
        if child.tag == "uses-permission":
            name = child.get(android_ns("name")) or ""
            permissions.append(name.rsplit(".", 1)[-1])
        elif child.tag == "application":
            application = child
    audit.check("INTERNET" not in permissions, "no-internet-permission",
                "لا يوجد إذن INTERNET — Zero-Network مضمون بالقائمة", "إذن INTERNET موجود!")
    audit.check("ACCESS_NETWORK_STATE" not in permissions, "no-network-state-permission",
                "لا يوجد إذن ACCESS_NETWORK_STATE", "إذن ACCESS_NETWORK_STATE موجود!")
    if application is None:
        audit.fail("manifest-application", "عنصر application مفقود")
        return
    audit.check(application.get(android_ns("allowBackup")) == "false", "allow-backup-false",
                "allowBackup=false", "allowBackup ليس false")
    rules_attr = application.get(android_ns("dataExtractionRules")) or ""
    audit.check("data_extraction_rules" in rules_attr, "data-extraction-rules-attr",
                "dataExtractionRules مربوط", "dataExtractionRules غير مربوط")
    audit.check(application.get(android_ns("usesCleartextTraffic")) == "false", "cleartext-false",
                "usesCleartextTraffic=false", "النص الصريح غير محظور")
    for receiver in root.iter("receiver"):
        name = receiver.get(android_ns("name")) or "receiver"
        exported = receiver.get(android_ns("exported"))
        permission = receiver.get(android_ns("permission"))
        if exported == "true":
            audit.check(bool(permission), f"receiver-{name}", f"{name}: exported مع إذن", f"{name}: exported=true بلا إذن")
    privacy = (ROOT / "docs/PRIVACY.md").read_text(encoding="utf-8")
    missing = [name for name in PERMISSION_NAMES if name in permissions and name not in privacy]
    audit.check(not missing, "privacy-permissions", "كل الأذونات مذكورة في وثيقة الخصوصية",
                f"أذونات غير مذكورة في PRIVACY.md: {missing}")
    audit.check("INTERNET" in privacy, "privacy-internet", "PRIVACY.md يذكر غياب إذن INTERNET",
                "PRIVACY.md لا يذكر INTERNET")
    for token in ("zahra_tasks_prefs", "zahra_schedules_prefs", "zahra_mood_journal"):
        audit.check(token in privacy, f"privacy-plaintext-{token}", f"PRIVACY.md يذكر {token}",
                    f"PRIVACY.md لا يذكر التخزين غير المشفر {token}")
    # قواعد API 31+
    rules_path = ROOT / "app/src/main/res/xml/data_extraction_rules.xml"
    rules = ET.fromstring(rules_path.read_text(encoding="utf-8"))
    for section in ("cloud-backup", "device-transfer", "cross-platform-transfer"):
        node = rules.find(section)
        if node is None:
            audit.fail(f"backup-{section}", f"قسم {section} مفقود في data_extraction_rules")
            continue
        excluded = {exclude.get("domain") for exclude in node.findall("exclude") if exclude.get("path") == "."}
        required_domains = {
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        }
        audit.check(required_domains <= excluded, f"backup-{section}-excludes",
                    f"{section}: كل النطاقات مستثناة", f"{section}: نطاقات غير مستثناة: {sorted(required_domains - excluded)}")
    # قواعد API ≤30
    legacy_path = ROOT / "app/src/main/res/xml/backup_rules.xml"
    legacy = ET.fromstring(legacy_path.read_text(encoding="utf-8"))
    legacy_excluded = {exclude.get("domain") for exclude in legacy.findall("exclude") if exclude.get("path") == "."}
    audit.check({"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"} <= legacy_excluded, "backup-legacy-excludes",
                "backup_rules.xml يستثني كل النطاقات", "backup_rules.xml لا يستثني كل النطاقات")


def check_content(audit: Audit, manifest: dict) -> None:
    packs = sorted((ROOT / "packs").glob("*/pack.json"))
    units = 0
    ids: set[str] = set()
    duplicates: list[str] = []
    for pack in packs:
        try:
            data = read_json(pack)
        except json.JSONDecodeError as exc:
            audit.fail(f"pack-json-{pack}", f"JSON غير صالح: {exc}")
            continue
        for unit in data.get("units", []):
            units += 1
            uid = str(unit.get("id", ""))
            if uid in ids:
                duplicates.append(uid)
            ids.add(uid)
    content = manifest.get("content", {})
    audit.check(len(packs) == content.get("packs"), "pack-count",
                f"عدد الحزم {len(packs)}", f"عدد الحزم {len(packs)} ≠ المانيفست {content.get('packs')}")
    audit.check(units == content.get("units"), "unit-count",
                f"عدد الوحدات {units}", f"عدد الوحدات {units} ≠ المانيفست {content.get('units')}")
    audit.check(not duplicates, "unit-ids", "معرّفات الوحدات فريدة", f"معرّفات مكررة: {duplicates[:5]}")
    index = read_json(ROOT / "app/src/main/assets/knowledge_index.json")
    audit.check(len(index.get("units", [])) == units, "index-units",
                f"الفهرس المشحون يضم {units} وحدة", f"الفهرس يضم {len(index.get('units', []))} ≠ {units}")
    audit.check((ROOT / content.get("licenseAudit", "")).exists(), "license-audit-exists",
                f"تدقيق التراخيص موجود: {content.get('licenseAudit')}",
                "تدقيق التراخيص مفقود")


def check_measurement(audit: Audit, manifest: dict) -> None:
    measurement = manifest.get("measurement", {})
    status = measurement.get("status")
    audit.check(status in ("not-run", "measured"), "measurement-status",
                f"حالة القياس معلنة: {status}", f"حالة قياس غير مسموحة: {status}")
    results_path = ROOT / "build/reports/zahra-eval/results.json"
    if status == "not-run":
        audit.check(not measurement.get("resultsFile"), "measurement-no-file",
                    "not-run بلا ملف نتيجة معلن", "not-run مع resultsFile — تناقض")
        audit.check(measurement.get("resultsSha256") is None, "measurement-no-hash",
                    "not-run بلا هاش نتيجة", "not-run مع resultsSha256")
        audit.check(not results_path.exists(), "measurement-no-local-artifact",
                    "لا يوجد results.json محلي", "يوجد results.json محلي لكن المانيفست not-run — حدّثه")
        results_doc = (ROOT / "docs/RESULTS.md").read_text(encoding="utf-8")
        audit.check("لا توجد نتائج" in results_doc and "لم يُشغَّل" in results_doc, "results-doc-honest",
                    "RESULTS.md يعلن عدم وجود نتائج", "RESULTS.md لا يعلن عدم وجود نتائج بوضوح")
    elif status == "measured":
        file_value = measurement.get("resultsFile")
        if not file_value:
            audit.fail("measurement-file-required", "measured بلا resultsFile")
        else:
            path = ROOT / file_value
            audit.check(path.exists(), "measurement-file-exists", f"ملف النتيجة موجود: {file_value}", "ملف النتيجة مفقود")
            if path.exists():
                audit.check(measurement.get("resultsSha256") == sha256_file(path), "measurement-file-hash",
                            "هاش النتيجة مطابق", "هاش النتيجة غير مطابق")
                try:
                    data = read_json(path)
                    audit.check(isinstance(data.get("cases"), list) and bool(data.get("cases")), "measurement-file-schema",
                                "ملف النتيجة يضم حالات", "ملف النتيجة لا يضم cases[]")
                except json.JSONDecodeError:
                    audit.fail("measurement-file-json", "ملف النتيجة JSON غير صالح")
        for key in ("lastRunAt", "command"):
            audit.check(bool(measurement.get(key)), f"measurement-{key}", f"{key} معلن", f"{key} مفقود في measured")
    protocol_path = RELEASE_DIR / "blind-protocol.example.json"
    protocol = read_json(protocol_path)
    if measurement.get("blindBenchmark") == "not-run":
        audit.check(protocol.get("status") in ("template-not-run", "preregistered"), "blind-status",
                    f"المقارنة العمياء لم تُنفّذ (status={protocol.get('status')})",
                    "blindBenchmark=not-run مع بروتوكول مكتمل — تناقض")
    try:
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        from blind_bench import validate_protocol  # type: ignore

        findings = validate_protocol(protocol_path)
        fails = [message for level, message in findings if level == "FAIL"]
        audit.check(not fails, "blind-protocol", "بروتوكول المقارنة العمياء صالح بنيويًا", f"بروتوكول غير صالح: {fails}")
    except Exception as exc:  # pragma: no cover - حماية من كسر الاستيراد
        audit.warn("blind-protocol-import", f"تعذّر التحقق من البروتوكول: {exc}")


def tracked_files() -> list[Path]:
    try:
        output = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        return [ROOT / name for name in output.split("\0") if name]
    except (subprocess.CalledProcessError, FileNotFoundError):
        files: list[Path] = []
        for path in ROOT.rglob("*"):
            if not path.is_file():
                continue
            if any(part in {".git", "build", "node_modules", "__pycache__", ".gradle"} for part in path.parts):
                continue
            files.append(path)
        return files


def check_secrets(audit: Audit, manifest: dict) -> None:
    files = tracked_files()
    filename_hits: list[str] = []
    content_hits: list[tuple[str, str]] = []
    for path in files:
        relative = rel(path) if path.is_relative_to(ROOT) else str(path)
        if relative.endswith(".env.example"):
            continue
        if any(pattern.search(relative) for pattern in SECRET_FILENAME_PATTERNS):
            filename_hits.append(relative)
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            if path.stat().st_size > 2_000_000:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for name, pattern in SECRET_CONTENT_PATTERNS:
            if pattern.search(text):
                content_hits.append((relative, name))
    audit.check(not filename_hits, "secret-files", "لا ملفات مفاتيح/بيانات بيئة متعقبة",
                f"ملفات أسرار متعقبة: {filename_hits}")
    audit.check(not content_hits, "secret-content", "لا أنماط مفاتيح في الملفات المتعقبة",
                f"أنماط مفاتيح: {content_hits[:10]}")
    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for pattern in (".env", "*.jks", "*.keystore", "keystore.properties", "my-upload-key.jks"):
        audit.check(pattern in gitignore, f"gitignore-{pattern}", f".gitignore يحمي {pattern}", f".gitignore لا يحمي {pattern}")
    gradle_text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    literals = re.findall(r'storePassword\s*=\s*"([^"]+)"', gradle_text)
    audit.check(all(value == "android" for value in literals), "signing-passwords",
                "كلمات مرور الإصدار من متغيرات بيئة (لا literal)",
                f"كلمة مرور توقيع مكتوبة حرفيًا: {literals}")
    release = manifest.get("release", {})
    if not release.get("signedReleaseVerified"):
        dist = (ROOT / "docs/DISTRIBUTION.md").read_text(encoding="utf-8")
        audit.check("لم يُتحقق" in dist or "لم يُختبر" in dist, "signing-unverified-doc",
                    "DISTRIBUTION يعلن أن التوقيع لم يُتحقق", "DISTRIBUTION لا يعلن حالة التوقيع")


def check_ci(audit: Audit, manifest: dict) -> None:
    ci_path = ROOT / "ci/github-actions.yml"
    text = ci_path.read_text(encoding="utf-8")
    audit.check("pull_request_target" not in text, "ci-no-pr-target",
                "CI لا يستخدم pull_request_target", "CI يستخدم pull_request_target")
    audit.check(re.search(r"^permissions:\s*$", text, re.MULTILINE) is not None, "ci-permissions",
                "CI يعلن permissions", "CI بلا permissions صريحة")
    if "secrets." in text:
        audit.warn("ci-secrets", "CI يستخدم أسرارًا — تأكد أنها بيئية ولا تُطبع")
    else:
        audit.passed("CI لا يستخدم أسرارًا")
    unpinned = re.findall(r"uses:\s*([^\s#]+)@(v\d+|[A-Za-z0-9_.-]+)", text)
    unpinned = [(action, ref) for action, ref in unpinned if not re.fullmatch(r"[0-9a-f]{40}", ref)]
    if unpinned:
        audit.warn("ci-unpinned", f"{len(unpinned)} إجراءًا بوسم غير SHA (مثال: {unpinned[:3]}) — يُوثَّق كقيد")
    else:
        audit.passed("كل إجراءات CI مثبتة بـSHA")
    if "persist-credentials" not in text:
        audit.warn("ci-persist-credentials", "actions/checkout بلا persist-credentials: false")
    workflows = ROOT / ".github/workflows"
    activated = workflows.exists() and any(workflows.glob("*.yml"))
    if manifest.get("release", {}).get("ciActivated") is False:
        audit.check(not activated, "ci-activated-mismatch",
                    "CI غير مفعّل كما يعلن المانيفست", "توجد workflows لكن المانيفست يقول ciActivated=false")
    audit.info(f"وظائف CI المعلنة: {len(re.findall(r'^  [a-zA-Z0-9_-]+:', text, re.MULTILINE))} (لم تُشغّل هنا)")


def check_ui_claims(audit: Audit) -> None:
    # نصوص المستخدم قد تكون في أي ملف Kotlin (رسائل الراوتر/البطاقات/الحوارات)،
    # وليس في شاشات ui/ فقط. فحص كل المصدر يمنع ادعاءً مطلقًا خارج نطاق المراجعة.
    ui_files = sorted((ROOT / "app/src/main/java").rglob("*.kt")) + [
        ROOT / "app/src/main/res/values/strings.xml",
    ]
    hits: list[tuple[str, str]] = []
    quoted = re.compile(r'"((?:[^"\\]|\\.)*)"')
    for path in ui_files:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        strings = quoted.findall(text) if path.suffix == ".kt" else [text]
        for literal in strings:
            for phrase in FORBIDDEN_UI_CLAIMS:
                if phrase in literal:
                    hits.append((rel(path), phrase))
    audit.check(not hits, "ui-absolute-claims", "نسخ الواجهة بلا ادعاءات مطلقة محظورة",
                f"ادعاءات مطلقة في الواجهة: {hits[:10]}")
    diagnostics = ROOT / "app/src/main/java/com/example/zahra/ui/screens/DiagnosticsDialog.kt"
    if diagnostics.exists():
        text = diagnostics.read_text(encoding="utf-8")
        audit.check("تُنشر في CI artifacts" not in text, "ui-ci-artifact-claim",
                    "نافذة التشخيص لا تدّعي نشر نتائج CI", "نافذة التشخيص تدّعي نشر نتائج غير موجودة")
    release_kt = ROOT / "app/src/main/java/com/example/zahra/diagnostics/ReleaseInfo.kt"
    audit.check(release_kt.exists(), "release-info-kt", "موديل معلومات الإصدار موجود", "ReleaseInfo.kt مفقود")
    if release_kt.exists():
        text = release_kt.read_text(encoding="utf-8")
        audit.check("release_info.json" in text, "release-info-asset-name",
                    "ReleaseInfo.kt يقرأ release_info.json", "ReleaseInfo.kt لا يشير إلى الأصل")


def check_generated_asset(audit: Audit, manifest: dict, data: dict) -> None:
    if not GENERATED_ASSET.exists():
        audit.fail("asset-missing", "app/src/main/assets/release_info.json مفقود")
        return
    try:
        asset = read_json(GENERATED_ASSET)
    except json.JSONDecodeError as exc:
        audit.fail("asset-json", f"الأصل JSON غير صالح: {exc}")
        return
    audit.check(asset.get("versionName") == manifest["app"]["versionName"], "asset-version",
                "أصل التطبيق يطابق versionName", "أصل التطبيق versionName غير مطابق")
    audit.check(asset.get("versionCode") == manifest["app"]["versionCode"], "asset-code",
                "أصل التطبيق يطابق versionCode", "أصل التطبيق versionCode غير مطابق")
    audit.check(asset.get("channel") == manifest["release"]["channel"], "asset-channel",
                "أصل التطبيق يطابق القناة", "أصل التطبيق القناة غير مطابقة")
    audit.check(asset.get("measurementStatus") == manifest["measurement"]["status"], "asset-measurement",
                "أصل التطبيق يطابق حالة القياس", "أصل التطبيق حالة القياس غير مطابقة")
    expected = sum(1 for item in data["limitations"] if item["inApp"])
    audit.check(len(asset.get("limitations", [])) == expected, "asset-limitations-count",
                f"أصل التطبيق يضم {expected} قيدًا", "عدد قيود أصل التطبيق غير مطابق")
    bad = [item.get("id") for item in asset.get("limitations", []) if not str(item.get("titleAr", "")).strip() or not str(item.get("detailAr", "")).strip()]
    audit.check(not bad, "asset-limitations-fields", "كل قيود الأصل مكتملة", f"قيود ناقصة: {bad[:5]}")


def check_research(audit: Audit) -> None:
    path = ROOT / "evidence/phase-06/research.md"
    if not path.exists():
        audit.fail("research-missing", "evidence/phase-06/research.md مفقود (قاعدة R0)")
        return
    text = path.read_text(encoding="utf-8")
    sources = len(re.findall(r"^\|\s*\d+\s*\|", text, flags=re.MULTILINE))
    audit.check(sources >= 5, "research-sources", f"research.md: {sources} مصدرًا", f"مصادر أقل من 5 ({sources})")
    for token in ("## الاكتشافات وتأثيرها", "التوسيع", "ZAHRA_FUTURE_VISION_RESEARCH.md", "4edbb18"):
        audit.check(token in text, f"research-{token}", f"research.md يضم «{token}»", f"research.md يفتقد «{token}»")
    audit.check("لم يُشغَّل" in text or "لم يُنفّذ" in text, "research-not-run", "research.md يعلن ما لم يُشغّل",
                "research.md لا يعلن ما لم يُشغّل")


def check_post_release(audit: Audit) -> None:
    path = ROOT / "docs/POST-RELEASE.md"
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    for token in ("D1", "D2", "D3", "D7", "الوضع المتقدم", "ZAHRA-POST-001"):
        audit.check(token in text, f"post-release-{token}", f"POST-RELEASE يضم {token}", f"POST-RELEASE يفتقد {token}")


def run_checks(write: bool) -> Audit:
    audit = Audit()
    for path in (MANIFEST, LIMITATIONS_SOURCE):
        if not path.exists():
            audit.fail("source-missing", f"ملف مصدر مفقود: {rel(path)}")
            return audit
    try:
        manifest = read_json(MANIFEST)
    except json.JSONDecodeError as exc:
        audit.fail("manifest-json", f"المانيفست JSON غير صالح: {exc}")
        return audit
    try:
        data = load_limitations()
        audit.passed(f"release/limitations.json صالح: {len(data['limitations'])} قيدًا")
    except ValueError as exc:
        audit.fail("limitations-invalid", f"قيود غير صالحة: {exc}")
        return audit

    check_versions(audit, manifest)
    validate_limitations_generated(audit, data, manifest, write)
    check_docs(audit, manifest)
    check_links(audit)
    check_manifest_and_backup(audit, manifest)
    check_content(audit, manifest)
    check_measurement(audit, manifest)
    check_secrets(audit, manifest)
    check_ci(audit, manifest)
    check_ui_claims(audit)
    check_generated_asset(audit, manifest, data)
    check_research(audit)
    check_post_release(audit)
    return audit


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="مدقق الإطلاق والصدق التوثيقي — المرحلة 6")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="توليد docs/LIMITATIONS.md وrelease_info.json")
    mode.add_argument("--check", action="store_true", help="فحص فقط (الافتراضي)")
    parser.add_argument("--strict", action="store_true", help="اعتبار التحذيرات فشلًا")
    parser.add_argument("--json", help="كتابة ملخص الفحص JSON إلى هذا المسار")
    args = parser.parse_args(argv)

    audit = run_checks(write=args.write)
    print()
    print(f"النتيجة: {audit.failed} فشل · {audit.warnings} تحذير")
    if audit.findings:
        print("\nالملاحظات:")
        for finding in audit.findings:
            print(f"{finding.level} · {finding.code} · {finding.message}")
    if args.json:
        path = Path(args.json)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "failed": audit.failed,
                    "warnings": audit.warnings,
                    "findings": [finding.__dict__ for finding in audit.findings],
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"INFO · ملخص JSON → {path}")
    if audit.failed or (args.strict and audit.warnings):
        print("\nفحص الإطلاق فشل. الإصدار غير جاهز للنشر حتى تُصلح البنود أعلاه.")
        return 1
    print("\nفحص الإطلاق نجح بنيويًا (لا يعني جاهزية نشر أو صحة محتوى/أمان/أداء).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
