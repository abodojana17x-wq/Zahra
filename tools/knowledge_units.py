#!/usr/bin/env python3
"""مكتبة المرحلة 3: تقطيع/تجذيع مطابق لـ Kotlin، تحقق من وحدات المعرفة، وبناء فهرس زمن البناء.

⚠️ مرجع الحقيقة للتقطيع هو Kotlin: `app/src/main/java/com/example/zahra/knowledge/unit/UnitTokenizer.kt`
و`app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt`. النسخة هنا **مرآة** لها، ويُفحص التطابق
مقابل `knowledge/unit/tokenizer-fixtures.json` (يُولَّد من Kotlin) عبر `python3 tools/build_index.py --self-test`.
أي اختلاف = فشل بناء، لا تحذير (D24).

لا يستخدم هذا الملف أي مكتبة خارجية ولا شبكة: Python 3 القياسي فقط.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

SCHEMA_VERSION = 1
BUILDER_VERSION = "1.0.0"

# ————————————————————————————————————————————————————————————————
# 1) تطبيع وتقطيع (مرآة Kotlin)
# ————————————————————————————————————————————————————————————————

TASHKEEL_REGEX = re.compile(r"[\u064B-\u065F\u0670\u0640]")
WHITESPACE_REGEX = re.compile(r"\s+")
WORD_REGEX = re.compile(r"[^\W_]+", re.UNICODE)

ARABIC_INDIC_DIGITS = {
    "٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4",
    "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9",
    "۰": "0", "۱": "1", "۲": "2", "۳": "3", "۴": "4",
    "۵": "5", "۶": "6", "۷": "7", "۸": "8", "۹": "9",
}

# قاموس الفرانكو يُقرأ من مخرج Kotlin (D24) — لا تكرار يدوي لـ 300+ مدخل
FRANCO_LEXICON_PATH = Path(__file__).resolve().parents[1] / "knowledge" / "unit" / "franco-lexicon.json"
_FRANCO_CACHE: dict[str, Any] | None = None

COLLOQUIAL_MAP = [
    ("عاوز", "عايز"), ("محتاج", "عايز"), ("اريد", "عايز"), ("بدي", "عايز"),
    ("دلوقتي", "الان"), ("الحين", "الان"), ("هسه", "الان"),
    ("وبكره", "وغدا"), ("بكره", "غدا"), ("وامبارح", "وامس"), ("امبارح", "امس"),
]

ARABIC_STOPWORDS = {
    "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
    "هو", "هي", "هم", "هن", "نحن", "أنا", "أنت", "كان", "كانت", "يكون", "أن", "إن",
    "ما", "لا", "لم", "لن", "هل", "قد", "ثم", "أو", "بل", "لكن", "كل", "بعض", "يا",
}

EXTRA_STOPWORDS = {
    "ايه", "إيه", "يعني", "ازاي", "إزاي", "ازاى", "مين", "امتى", "فين", "ليه", "ليش", "ايش",
    "قولي", "قولى", "قول", "اشرح", "اشرحلي", "وضح", "وضحلي", "عرفني", "عرفنى", "عايز", "عاوز",
    "عايزه", "محتاج", "ممكن", "لو", "سمحت", "فضلك", "باختصار", "بسرعة", "كده", "ده", "دي", "دول",
    "تعريف", "معنى", "شرح", "مثال", "امثلة", "أمثلة", "فرق", "الفرق", "بين", "ولا",
    "ال", "el", "الـ",
    "what", "whats", "is", "are", "was", "the", "a", "an", "of", "to", "how", "why", "do", "does",
    "did", "i", "you", "me", "my", "we", "in", "on", "for", "and", "or", "it", "its", "that", "this",
    "with", "explain", "define", "meaning", "tell", "about", "please", "can", "should", "when", "where",
    "which", "who", "difference", "between", "vs", "versus", "example",
}

def _normalized_stopwords() -> set[str]:
    """مرآة `UnitTokenizer.STOPWORDS`: تطبيع كل كلمة وقف بنفس دالة التطبيع قبل المقارنة."""
    return {w for base in (ARABIC_STOPWORDS | EXTRA_STOPWORDS)
            for w in (base, normalize_arabic(base).lower())}


# تُملأ بعد تعريف normalize_arabic (تحت) لتفادي الاعتماد على ترتيب التعريفات

STEM_EXCEPTIONS = {
    "مش", "مفيش", "لا", "لم", "لن", "ما", "غير", "بدون", "دون",
    "الله", "الرحمن", "الرحيم", "القرآن", "الحديث", "الصلاة", "الزكاة", "الصيام", "الحج", "الإيمان", "الإسلام",
    "أنا", "أنت", "هو", "هي", "نحن", "هذا", "هذه", "ذلك", "تلك", "الذي", "التي",
    "كود", "برمجة", "دالة", "متغير", "حلقة", "بايثون", "جافا", "سكربت", "مكتبة", "واجهة", "كائن", "فئة",
    "البرمجة", "الذكاء", "الاصطناعي", "بيانات", "نظام", "تطبيق", "موقع",
}

MIN_TOKEN_LENGTH = 2
GRAM_SIZE = 3
LATIN_OR_DIGIT = re.compile(r"[A-Za-z0-9]")


def _franco_data() -> dict[str, Any]:
    global _FRANCO_CACHE
    if _FRANCO_CACHE is None:
        if FRANCO_LEXICON_PATH.exists():
            _FRANCO_CACHE = json.loads(FRANCO_LEXICON_PATH.read_text(encoding="utf-8"))
        else:
            _FRANCO_CACHE = {"phrases": [], "words": {}}
    return _FRANCO_CACHE


DIGIT_LETTER_RULES = [
    (re.compile(r"(?<=[a-z])3|3(?=[a-z])"), "ع"),
    (re.compile(r"(?<=[a-z])7|7(?=[a-z])"), "ح"),
    (re.compile(r"(?<=[a-z])2|2(?=[a-z])"), "ء"),
    (re.compile(r"(?<=[a-z])5|5(?=[a-z])"), "خ"),
    (re.compile(r"(?<=[a-z])8|8(?=[a-z])"), "غ"),
    (re.compile(r"(?<=[a-z])9|9(?=[a-z])"), "ق"),
]


def franco_is_present(text: str) -> bool:
    """مرآة مبسّطة لـ `francoSignals().isFranco`: نمط رقم-حرف أو مطابقة قاموس."""
    data = _franco_data()
    lowered = text.lower()
    if re.search(r"[a-zA-Z]*[235789][a-zA-Z]*|[235789]+", lowered):
        return True
    for word in data.get("words", {}):
        if re.search(r"\b" + re.escape(word) + r"\b", lowered, re.IGNORECASE):
            return True
    for phrase, _ in data.get("phrases", []):
        if re.search(r"\b" + re.escape(phrase) + r"\b", lowered, re.IGNORECASE):
            return True
    return False


def franco_to_arabic(text: str) -> str:
    """مرآة `ZahraNormalizer.francoToArabic`: عبارات (الأطول أولًا) ← مفردات ← حروف رقمية."""
    data = _franco_data()
    s = text.lower()
    for phrase, arabic in sorted(data.get("phrases", []), key=lambda p: -len(p[0])):
        s = re.sub(r"\b" + re.escape(phrase) + r"\b", arabic, s, flags=re.IGNORECASE)
    for word, arabic in sorted(data.get("words", {}).items(), key=lambda kv: -len(kv[0])):
        s = re.sub(r"\b" + re.escape(word) + r"\b", arabic, s, flags=re.IGNORECASE)
    for pattern, letter in DIGIT_LETTER_RULES:
        s = pattern.sub(letter, s)
    return s


def normalize_arabic(text: str) -> str:
    """مرآة `ZahraNormalizer.normalize` لمسار النص غير الفرانكو (نصوص الوحدات عربية/إنجليزية)."""
    if not text or not text.strip():
        return ""
    t = text.strip()
    if franco_is_present(t):
        t = franco_to_arabic(t)
    t = TASHKEEL_REGEX.sub("", t)
    t = re.sub(r"[أإآٱ]", "ا", t)
    t = re.sub(r"[ىئ]", "ي", t)
    t = t.replace("ة", "ه")
    t = "".join(ARABIC_INDIC_DIGITS.get(ch, ch) for ch in t)
    for src, dst in COLLOQUIAL_MAP:
        t = t.replace(src, dst)
    return WHITESPACE_REGEX.sub(" ", t).strip()



STOPWORDS = _normalized_stopwords()

def tokenize(text: str) -> list[str]:
    """مرآة `UnitTokenizer.tokenize`: تطبيع ← حروف صغيرة ← كلمات ← حذف كلمات الوقف."""
    normalized = normalize_arabic(text).lower()
    words = [w for w in WORD_REGEX.findall(normalized) if len(w) >= MIN_TOKEN_LENGTH]
    if not words:
        return []
    filtered = [w for w in words if w not in STOPWORDS]
    return filtered if filtered else words


def stem(word: str) -> str:
    """مرآة `ZahraNormalizer.stemLight` (تجذيع خفيف محافظ + استثناءات)."""
    if not word:
        return word
    if LATIN_OR_DIGIT.search(word):
        return word
    if len(word) <= 3 or word in STEM_EXCEPTIONS:
        return word
    s = word
    if s.startswith("ال") and len(s) > 4 and s[2:] not in STEM_EXCEPTIONS:
        s = s[2:]
    for prefix in ("و", "ب", "ك", "ل", "ف"):
        if s.startswith(prefix) and len(s) - len(prefix) >= 4 and s[len(prefix):] not in STEM_EXCEPTIONS:
            s = s[len(prefix):]
            break
    for suffix in ("يات", "ياتي", "ونات", "ون", "ين", "ات", "ية", "ها", "كم", "نا", "هم", "هن"):
        if s.endswith(suffix) and len(s) - len(suffix) >= 4 and s[: len(s) - len(suffix)] not in STEM_EXCEPTIONS:
            s = s[: len(s) - len(suffix)]
            break
    return s


def indexed_tokens(text: str) -> list[str]:
    return [stem(t) for t in tokenize(text)]


def char_grams(text: str) -> set[str]:
    tokens = tokenize(text)
    grams: set[str] = set()
    for token in tokens:
        padded = f"^{token}$"
        if len(padded) <= GRAM_SIZE:
            grams.add(padded)
        else:
            for i in range(len(padded) - GRAM_SIZE + 1):
                grams.add(padded[i:i + GRAM_SIZE])
    return grams


def jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    inter = len(a & b)
    union = len(a) + len(b) - inter
    return inter / union if union > 0 else 0.0


# ————————————————————————————————————————————————————————————————
# 2) نموذج الوحدة والتحقق
# ————————————————————————————————————————————————————————————————

ID_REGEX = re.compile(r"^[a-z0-9][a-z0-9-]{2,79}$")
DATE_REGEX = re.compile(r"^\d{4}-\d{2}-\d{2}$")
URL_REGEX = re.compile(r"^https?://[^\s/]+\.[^\s]+$")

ALLOWED_SAFETY = {"none", "medical_disclaimer", "religious_reference", "no_fatwa"}
# العقد المعلن في plans/03-semantic-mind.md يستخدم q/a؛ نقرأهما ونتقبّل الأسماء الطويلة للتوافق
REQUIRED_FIELDS = ("id", "title", "intent", "q", "a")

# أوزان الحقول (D24 / قرار المرحلة 3): سؤال×3، عنوان×2، وسوم×2، إجابة×1
FIELD_WEIGHTS = {"q": 3.0, "title": 2.0, "tags": 2.0, "a": 1.0}

BM25_K1 = 1.2
BM25_B = 0.75
BM25_DELTA = 1.0
HYBRID_WEIGHTS = {"bm25": 0.60, "char": 0.24, "tfidf": 0.16}
# مصدر الحقيقة للأوزان هو مخرج الضبط الفعلي، لا هذا الثابت. يُقرأ مرة عند البناء.
WEIGHTS_EVIDENCE = Path(__file__).resolve().parents[1] / "evidence" / "phase-03" / "weights.json"


def load_fitted_weights() -> tuple[dict[str, float], str]:
    """يقرأ الأوزان المضبوطة من `evidence/phase-03/weights.json` ويُتحقّق من أنها على مبسّط واحد.

    عند غياب الملف (بناء أول قبل الضبط) يعود إلى الهيئة الابتدائية ويُبلّغ المصدر صراحةً.
    """
    fallback = (dict(HYBRID_WEIGHTS), "default")
    try:
        payload = json.loads(WEIGHTS_EVIDENCE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return fallback
    selected = payload.get("selected") or {}
    try:
        weights = {k: float(selected[k]) for k in ("bm25", "char", "tfidf")}
    except (KeyError, TypeError, ValueError):
        return fallback
    if any(v < 0 for v in weights.values()) or abs(sum(weights.values()) - 1.0) > 1e-6:
        return fallback
    return {k: round(v, 4) for k, v in weights.items()}, "evidence/phase-03/weights.json"

MIN_QUESTION_VARIANTS = 4
MAX_UNIT_WEIGHT = 2.0
SHINGLE_SIZE = 5
DUPLICATE_JACCARD_LIMIT = 0.85


class ContentError(Exception):
    """خطأ محتوى يمنع البناء (ليس تحذيرًا)."""


@dataclass
class Unit:
    id: str
    pack: str
    title: str
    intent: str
    questions: list[str]
    answers: list[str]
    tags: list[str]
    source: dict[str, str] | None
    authored: bool
    author: str
    verified_at: str
    safety: str
    weight: float
    terms: dict[str, float] = field(default_factory=dict)
    doc_length: int = 0

    @property
    def primary_question(self) -> str:
        return self.questions[0] if self.questions else self.title

    def tf_idf_text(self) -> str:
        parts = [self.title, *self.questions, " ".join(self.tags), *self.answers]
        return "\n".join(p for p in parts if p)

    def shingles(self) -> set[str]:
        """بصمات 5 رموز (shingles) على النص الكامل — لمقارنة التكرار بين الوحدات.

        المقارنة **دقيقة** (Jaccard على المجموعات) لا تقديرية: عدد الوحدات في المستودع يتسع لـ O(n²)
        بمجموعات صغيرة، والدقة أهم من السرعة في مدقق بناء.
        """
        tokens = indexed_tokens(self.tf_idf_text())
        if len(tokens) < SHINGLE_SIZE:
            return {" ".join(tokens)} if tokens else set()
        return {" ".join(tokens[i:i + SHINGLE_SIZE]) for i in range(len(tokens) - SHINGLE_SIZE + 1)}

    def content_signature(self) -> set[str]:
        """بصمة المحتوى المعتمد فقط (بدون العنوان/الأسئلة) — تكشف الوحدتين بنفس الإجابة."""
        tokens = indexed_tokens(" ".join(self.answers))
        if len(tokens) < SHINGLE_SIZE:
            return {" ".join(tokens)} if tokens else set()
        return {" ".join(tokens[i:i + SHINGLE_SIZE]) for i in range(len(tokens) - SHINGLE_SIZE + 1)}


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ContentError(message)


def parse_unit(raw: dict[str, Any], pack_dir: str, source_file: str) -> Unit:
    where = f"{source_file}::{raw.get('id', '?')}"
    for key in REQUIRED_FIELDS:
        _require(key in raw, f"{where}: حقل مفقود «{key}»")

    unit_id = str(raw["id"]).strip()
    _require(bool(ID_REGEX.match(unit_id)), f"{where}: معرّف غير صالح «{unit_id}» (a-z0-9- فقط، 3–80 حرفًا)")
    _require(raw.get("pack", pack_dir) == pack_dir,
             f"{where}: الحقل pack «{raw.get('pack')}» لا يطابق المجلد «{pack_dir}»")

    title = str(raw["title"]).strip()
    _require(3 <= len(title) <= 300, f"{where}: العنوان يجب أن يكون بين 3 و300 حرفًا")

    intent = str(raw["intent"]).strip()
    _require(bool(intent), f"{where}: النية (intent) فارغة")

    questions = [str(q).strip() for q in (raw.get("q") or raw.get("questions") or [])]
    _require(len(questions) >= MIN_QUESTION_VARIANTS,
             f"{where}: عدد تنويعات السؤال {len(questions)} < {MIN_QUESTION_VARIANTS} (شرط المسار A)")
    _require(len(set(questions)) == len(questions), f"{where}: تنويعات سؤال مكررة")
    for q in questions:
        _require(len(q) >= 3, f"{where}: تنويع سؤال قصير جدًا «{q}»")

    answers = [str(a).strip() for a in (raw.get("a") or raw.get("answers") or [])]
    _require(len(answers) >= 1, f"{where}: لا توجد إجابة")
    _require(all(len(a) >= 10 for a in answers), f"{where}: إجابة قصيرة جدًا (< 10 أحرف)")

    tags = [str(t).strip() for t in raw.get("tags", [])]

    source_raw = raw.get("source")
    source: dict[str, str] | None = None
    if source_raw:
        _require(isinstance(source_raw, dict), f"{where}: source يجب أن يكون كائنًا")
        name = str(source_raw.get("name", "")).strip()
        url = str(source_raw.get("url", "")).strip()
        _require(bool(name), f"{where}: source.name مطلوب")
        if url:
            _require(bool(URL_REGEX.match(url)), f"{where}: رابط مصدر غير صالح «{url}»")
        source = {
            "name": name,
            "url": url,
            "license": str(source_raw.get("license", "")).strip(),
            "location": str(source_raw.get("location", "")).strip(),
        }

    authored = bool(raw.get("authored", False))
    author = str(raw.get("author", "")).strip()
    _require(source is not None or authored,
             f"{where}: الوحدة بلا `source` ولا `authored:true` — مرفوضة (لا معرفة بلا سند)")
    if authored:
        _require(bool(author), f"{where}: `authored:true` يلزمه `author` واضح")

    verified_at = str(raw.get("verifiedAt", "")).strip()
    _require(bool(DATE_REGEX.match(verified_at)), f"{where}: verifiedAt يجب أن يكون YYYY-MM-DD")

    safety = str(raw.get("safety", "none")).strip()
    _require(safety in ALLOWED_SAFETY, f"{where}: safety غير معروف «{safety}»")

    weight = float(raw.get("weight", 1.0))
    _require(0.0 < weight <= MAX_UNIT_WEIGHT, f"{where}: weight يجب أن يكون في (0, {MAX_UNIT_WEIGHT}]")

    if pack_dir.startswith("health"):
        _require(safety == "medical_disclaimer",
                 f"{where}: وحدات الصحة تلزمها safety=medical_disclaimer")
    if pack_dir.startswith("religion"):
        _require(source is not None, f"{where}: الوحدات الدينية تلزمها `source` موثّق")
    if pack_dir.startswith("programming"):
        has_code = any(("`" in a) or ("：" in a) for a in answers)
        _require(has_code,
                 f"{where}: وحدات البرمجة تلزمها تعريف + مثال كود بين علامات `…` (درس فشل المنافس)")

    return Unit(
        id=unit_id,
        pack=pack_dir,
        title=title,
        intent=intent,
        questions=questions,
        answers=answers,
        tags=tags,
        source=source,
        authored=authored,
        author=author,
        verified_at=verified_at,
        safety=safety,
        weight=weight,
    )


def load_packs(root: Path) -> tuple[list[Unit], list[dict[str, Any]], list[str]]:
    """يقرأ `packs/*/pack.json` ويعيد الوحدات + بيانات الحزم + التحذيرات."""
    packs_dir = root / "packs"
    units: list[Unit] = []
    pack_info: list[dict[str, Any]] = []
    warnings: list[str] = []
    if not packs_dir.exists():
        return units, pack_info, ["لا يوجد مجلد packs/ — الفهرس سيكون فارغًا"]

    for pack_json in sorted(packs_dir.glob("*/pack.json")):
        pack_dir = pack_json.parent.name
        data = json.loads(pack_json.read_text(encoding="utf-8"))
        declared = str(data.get("pack", pack_dir))
        _require(declared == pack_dir,
                 f"{pack_json.relative_to(root)}: pack «{declared}» لا يطابق اسم المجلد «{pack_dir}»")
        raw_units = data.get("units")
        _require(isinstance(raw_units, list), f"{pack_json.relative_to(root)}: units يجب أن تكون مصفوفة")
        pack_units = [parse_unit(u, pack_dir, str(pack_json.relative_to(root))) for u in raw_units]
        _require(len(pack_units) > 0, f"{pack_json.relative_to(root)}: حزمة بلا وحدات")
        units.extend(pack_units)
        pack_info.append({
            "pack": pack_dir,
            "version": str(data.get("version", "0.0.0")),
            "units": len(pack_units),
            "license": str(data.get("license", "")),
        })

        for raw in raw_units:
            blob = json.dumps(raw, ensure_ascii=False)
            if re.search(r"[A-Za-z][2357][A-Za-z]", blob):
                warnings.append(f"{pack_dir}::{raw.get('id')}: نص لاتيني يشبه الفرانكو — التقطيع يعتمد التطبيع العربي/الإنجليزي فقط")
    return units, pack_info, warnings


def check_duplicates(units: Iterable[Unit]) -> None:
    """يفشل البناء عند: معرّف مكرر، أو تكرار محتوى عالٍ (بصمات النص الكامل أو الإجابة)."""
    seen_ids: dict[str, str] = {}
    full: list[tuple[str, set[str]]] = []
    answers: list[tuple[str, set[str]]] = []
    for unit in units:
        _require(unit.id not in seen_ids,
                 f"معرّف مكرر «{unit.id}» في {seen_ids.get(unit.id)} و{unit.pack}")
        seen_ids[unit.id] = unit.pack

        shingles = unit.shingles()
        signature = unit.content_signature()
        for other_id, other in full:
            similarity = jaccard(shingles, other)
            _require(similarity < DUPLICATE_JACCARD_LIMIT,
                     f"تكرار محتوى بين «{other_id}» و«{unit.id}» (تشابه {similarity:.2f}) — يُدمج أو يُحذف")
        for other_id, other in answers:
            similarity = jaccard(signature, other)
            _require(similarity < DUPLICATE_JACCARD_LIMIT,
                     f"إجابتان شبه متطابقتين: «{other_id}» و«{unit.id}» (تشابه {similarity:.2f})")
        full.append((unit.id, shingles))
        answers.append((unit.id, signature))


# ————————————————————————————————————————————————————————————————
# 3) حساب الفهرس (زمن البناء)
# ————————————————————————————————————————————————————————————————

def compute_terms(unit: Unit) -> dict[str, float]:
    terms: dict[str, float] = {}
    fields = {
        "q": " ".join(unit.questions),
        "title": unit.title,
        "tags": " ".join(unit.tags),
        "a": " ".join(unit.answers),
    }
    for name, text in fields.items():
        weight = FIELD_WEIGHTS[name]
        if not text:
            continue
        for token in indexed_tokens(text):
            terms[token] = terms.get(token, 0.0) + weight
    return terms


def bm25_plus_raw(query_tokens: list[str], unit: Unit, df: dict[str, int], n_docs: int, avgdl: float) -> float:
    """BM25+ لكل وحدة (لحساب الدرجة المرجعية `bm25ref`)."""
    total = 0.0
    for token in set(query_tokens):
        tf = unit.terms.get(token)
        if not tf:
            continue
        d = df.get(token, 0)
        idf = math.log(1.0 + (n_docs - d + 0.5) / (d + 0.5))
        tf_norm = (tf * (BM25_K1 + 1.0)) / (
            tf + BM25_K1 * (1.0 - BM25_B + BM25_B * (unit.doc_length / avgdl))
        )
        total += idf * (tf_norm + BM25_DELTA)
    return total


def build_index(units: list[Unit], pack_info: list[dict[str, Any]], built_at: str) -> dict[str, Any]:
    tuned_weights, weights_source = load_fitted_weights()
    for unit in units:
        unit.terms = compute_terms(unit)
        unit.doc_length = max(1, int(round(sum(unit.terms.values()))))

    df: dict[str, int] = {}
    for unit in units:
        for term in unit.terms:
            df[term] = df.get(term, 0) + 1

    avgdl = sum(u.doc_length for u in units) / len(units) if units else 1.0

    # الدرجة المرجعية: مطابقة كل وحدة لسؤالها الأساسي، ثم الوسيط (D24).
    refs = sorted(
        bm25_plus_raw(indexed_tokens(u.primary_question), u, df, len(units), avgdl)
        for u in units
    ) if units else []
    if not refs:
        bm25_ref = 1.0
    elif len(refs) % 2 == 1:
        bm25_ref = refs[len(refs) // 2]
    else:
        bm25_ref = (refs[len(refs) // 2 - 1] + refs[len(refs) // 2]) / 2.0
    bm25_ref = max(bm25_ref, 1e-6)

    unit_payload = []
    for unit in sorted(units, key=lambda u: u.id):
        payload = {
            "id": unit.id,
            "pack": unit.pack,
            "title": unit.title,
            "intent": unit.intent,
            "q": unit.questions,
            "a": unit.answers,
            "tags": unit.tags,
            "source": unit.source,
            "authored": unit.authored,
            "author": unit.author,
            "verifiedAt": unit.verified_at,
            "safety": unit.safety,
            "weight": unit.weight,
            "docLength": unit.doc_length,
            "terms": {k: round(v, 3) for k, v in sorted(unit.terms.items())},
        }
        unit_payload.append(payload)

    content_hash = hashlib.sha256(
        json.dumps(unit_payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()

    return {
        "schemaVersion": SCHEMA_VERSION,
        "builtAt": built_at,
        "builderVersion": BUILDER_VERSION,
        "contentHash": content_hash,
        "params": {
            "k1": BM25_K1,
            "b": BM25_B,
            "delta": BM25_DELTA,
            "gramN": GRAM_SIZE,
            "weights": tuned_weights,
        },
        "stats": {
            "units": len(units),
            "avgDocLength": round(avgdl, 4),
            "bm25ref": round(bm25_ref, 6),
        },
        "sourcePacks": sorted(pack_info, key=lambda p: p["pack"]),
        "df": dict(sorted(df.items())),
        "units": unit_payload,
    }


def dump_index(index: dict[str, Any]) -> str:
    return json.dumps(index, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


# ————————————————————————————————————————————————————————————————
# 4) التوائم والتقارير
# ————————————————————————————————————————————————————————————————

def verify_fixtures(root: Path) -> tuple[int, list[str]]:
    """يفحص أن مرآة Python للتقطيع تساوي مخرجات Kotlin المحفوظة."""
    path = root / "knowledge" / "unit" / "tokenizer-fixtures.json"
    if not path.exists():
        return 0, [f"ملف التوائم مفقود: {path.relative_to(root)} — شغّل Kotlin: --emit-fixtures"]
    data = json.loads(path.read_text(encoding="utf-8"))
    problems: list[str] = []
    items = data.get("items", [])
    if not items:
        return 0, ["ملف التوائم فارغ"]
    for item in items:
        text = item["text"]
        want_tokens = item["tokens"]
        want_stems = item["stems"]
        got_tokens = tokenize(text)
        got_stems = [stem(t) for t in got_tokens]
        if got_tokens != want_tokens:
            problems.append(f"اختلاف tokenize «{text}»: Kotlin={want_tokens} Python={got_tokens}")
        if got_stems != want_stems:
            problems.append(f"اختلاف stem «{text}»: Kotlin={want_stems} Python={got_stems}")
    return len(items), problems
