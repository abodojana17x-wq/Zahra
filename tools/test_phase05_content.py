#!/usr/bin/env python3
"""Structural and coverage tests for the phase-5 content now in product packs.

These tests do not prove factual accuracy, independent legal review, or runtime answer quality.
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from validate_packs import audit_pack_metadata, valid_url  # noqa: E402
from knowledge_units import ContentError, check_duplicates, load_packs  # noqa: E402


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


class Phase05PackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.program = read_json(ROOT / "packs/seed-programming/pack.json")
        cls.daily = read_json(ROOT / "packs/daily-life/pack.json")
        cls.program_units = {unit["id"]: unit for unit in cls.program["units"]}
        cls.questions = {q for u in cls.program["units"] for q in u.get("q", [])}
        cls.daily_questions = {q for u in cls.daily["units"] for q in u.get("q", [])}

    def test_all_frozen_eval_programming_questions_are_represented(self):
        cases = [json.loads(line) for line in (ROOT / "eval/cases.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
        coding = [case for case in cases if case.get("domain") == "coding"]
        missing = [case["id"] for case in coding if case["question"] not in self.questions]
        self.assertEqual([], missing, f"Questions not represented: {missing}")

    def test_all_documented_competitor_failures_have_specific_units(self):
        programming_failures = {
            "اشرح الجافاسكريبت", "ايه هو HTML", "ازاي اوسّط div في CSS", "يعني ايه API",
            "اشرح لي React", "ايه هي قاعدة البيانات", "ازاي اصلح bug", "اكتبلي دالة debounce",
            "اكتبلي كود بايثون يقرأ ملف", "اكتبلي دالة تجمع رقمين", "ايه الفرق بين SQL و NoSQL",
            "ما هو git", "ازاي اتعلم الذكاء الاصطناعي", "ازاي اعمل موقع",
        }
        represented = self.questions | self.daily_questions
        self.assertEqual(set(), programming_failures - represented)
        emotion_failures = {"مش قادر اذاكر", "حاسس اني وحيد", "اتخانقت مع صاحبي", "انا فرحان النهارده", "انا زهقان", "هل تحبني"}
        self.assertTrue(emotion_failures.issubset(self.daily_questions))

    def test_programming_answers_have_structured_examples_and_expected_results(self):
        self.assertEqual(46, len(self.program_units))
        for unit_id, unit in self.program_units.items():
            with self.subTest(unit=unit_id):
                self.assertGreaterEqual(len(set(unit["q"])), 4)
                self.assertTrue(any("`" in answer or "```" in answer for answer in unit["a"]))
                self.assertTrue(unit.get("examples"))
                for example in unit["examples"]:
                    self.assertTrue(all(str(example.get(k, "")).strip() for k in ("lang", "code", "expected")))

    def test_d31_provenance_and_no_human_review_are_explicit(self):
        self.assertEqual("owner-waived-no-human-review", self.program["reviewStatus"])
        self.assertEqual("CC-BY-SA-4.0", self.daily["license"])
        self.assertEqual("owner-waived-no-human-review", self.daily["reviewStatus"])
        for unit in self.daily["units"]:
            self.assertEqual("Zahra AI / Arena", unit["generatedBy"])
            self.assertEqual("owner-waived-no-human-review", unit["reviewStatus"])
        self.assertFalse((ROOT / "evidence/phase-05/content-drafts").exists())

    def test_expected_domain_packs_and_safety_contracts_exist(self):
        expected = {"study": 5, "money": 5, "health": 4, "facts": 4, "code-snippets": 6, "religion": 21}
        for pack_name, count in expected.items():
            pack = read_json(ROOT / "packs" / pack_name / "pack.json")
            self.assertEqual(count, len(pack["units"]), pack_name)
            self.assertEqual("owner-waived-no-human-review", pack["reviewStatus"])
            self.assertTrue(all(unit.get("generatedBy") == "Zahra AI / Arena" for unit in pack["units"]))
            self.assertTrue(all(unit.get("contentLicense") == "CC-BY-SA-4.0" for unit in pack["units"]))
            self.assertTrue(all("facts not checked" in unit.get("verifiedBy", "") for unit in pack["units"]))
        health = read_json(ROOT / "packs/health/pack.json")["units"]
        self.assertTrue(all(unit["safety"] == "medical_disclaimer" for unit in health))
        self.assertTrue(all(any("تنبيه صحي" in answer for answer in unit["a"]) for unit in health))
        religion = read_json(ROOT / "packs/religion/pack.json")["units"]
        self.assertTrue(all(unit["source"].get("reference") for unit in religion))
        self.assertTrue(all(unit["safety"] == "religious_reference" for unit in religion))
        self.assertTrue(all(any("ليست فتوى" in answer for answer in unit["a"]) for unit in religion))

    def test_license_audit_discloses_d31_and_mixed_legacy_rights(self):
        report = (ROOT / "evidence/phase-05/license-audit.md").read_text(encoding="utf-8")
        self.assertIn("D31", report)
        self.assertIn("CC BY-SA 4.0", report)
        self.assertIn("owner-waived-no-human-review", report)
        self.assertIn("مصدر ترخيصه مجهول", report)
        self.assertIn("لم تُجرَ طلبات HTTP", report)

    def test_validator_checks_pack_schema_and_url_without_network(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pack_dir = root / "packs/programming"
            pack_dir.mkdir(parents=True)
            unit = {
                "id": "test-unit", "pack": "programming", "title": "Test definition",
                "intent": "define_word", "q": ["what is test", "ما هو الاختبار؟", "اشرح الاختبار", "test ya3ni eh"],
                "a": ["An authored definition. `print(1)`"], "tags": ["test"],
                "authored": True, "author": "test author", "verifiedAt": "2026-10-29",
                "safety": "none", "weight": 1.0,
                "examples": [{"lang": "python", "code": "print(1)", "expected": "1"}],
            }
            document = {"pack": "programming", "version": "1.0.0", "license": "CC-BY-SA-4.0",
                        "authors": ["test"], "sources": [], "generatedAt": "2026-10-29", "units": [unit]}
            target = pack_dir / "pack.json"
            target.write_text(json.dumps(document), encoding="utf-8")
            errors, warnings, _ = audit_pack_metadata(root, False)
            units, _, _ = load_packs(root)
            check_duplicates(units)
            self.assertEqual([], errors)
            self.assertEqual([], warnings)
            self.assertEqual(1, len(units))
            self.assertTrue(valid_url("https://example.org/reference"))
            self.assertFalse(valid_url("file:///tmp/content"))

            document["sources"] = [{"name": "bad source", "url": "javascript:alert(1)", "license": ""}]
            target.write_text(json.dumps(document), encoding="utf-8")
            errors, warnings, _ = audit_pack_metadata(root, False)
            self.assertTrue(any("رابط مصدر غير صالح" in error for error in errors))
            self.assertTrue(any("ترخيص pack-source غير مثبت" in warning for warning in warnings))

    def test_unit_contract_rejects_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "packs"
            for pack_name in ("study", "daily-life"):
                folder = root / pack_name
                folder.mkdir(parents=True)
                unit = {
                    "id": "same-id", "pack": pack_name, "title": "Unique title " + pack_name,
                    "intent": "query_knowledge", "q": ["one question", "two question", "three question", "four question"],
                    "a": ["A sufficiently long and distinct answer for this test unit."],
                    "tags": [pack_name], "authored": True, "author": "test", "verifiedAt": "2026-10-29",
                    "safety": "none", "weight": 1.0,
                }
                (folder / "pack.json").write_text(json.dumps({
                    "pack": pack_name, "version": "1.0.0", "authors": ["test"], "sources": [],
                    "generatedAt": "2026-10-29", "units": [unit],
                }), encoding="utf-8")
            loaded, _, _ = load_packs(Path(temporary))
            with self.assertRaises(ContentError):
                check_duplicates(loaded)


if __name__ == "__main__":
    unittest.main(verbosity=2)
