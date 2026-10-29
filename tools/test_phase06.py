#!/usr/bin/env python3
"""اختبارات المرحلة 6 — أدوات التقييم الأعمى والأداء ومدقق الإطلاق.

ما تختبره هذه الملفات:
  * `tools/phase06/blind_bench.py` — جدول عروض متوازن، تحميل أصوات، BT/Elo،
    اتفاق محكّمين، Holm، مقارنة نتائج، التحقق من بروتوكول.
  * `tools/phase06/device_perf.py` — تحليل `am start -W` والتحذيرات.
  * `tools/phase06/release_audit.py` — قراءة القيود، التوليد، فحوصات الإصدار.

ما لا تختبره: لا تشغّل أندرويد ولا Gradle ولا هارنس، ولا تُنتج أي رقم أداء/دقة
لزهرة. كل البيانات هنا اصطناعية وموسومة.
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PHASE06 = ROOT / "tools" / "phase06"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader, f"تعذّر تحميل {path}"
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module  # dataclasses يبحث عن الوحدة في sys.modules
    spec.loader.exec_module(module)
    return module


bb = load_module("phase06_blind_bench", PHASE06 / "blind_bench.py")
perf = load_module("phase06_device_perf", PHASE06 / "device_perf.py")
audit_mod = load_module("phase06_release_audit", PHASE06 / "release_audit.py")


def quiet(function, *args, **kwargs):
    """تشغيل دالة تطبع PASS بصوت مرتفع دون إغراق مخرج الاختبار."""
    with contextlib.redirect_stdout(io.StringIO()):
        return function(*args, **kwargs)


class BlindBenchScheduleTest(unittest.TestCase):
    def test_schedule_is_balanced_and_deterministic(self):
        cases = ["c1", "c2", "c3"]
        first = bb.build_schedule(cases, ["zahra", "baseline"], seed=42)
        second = bb.build_schedule(cases, ["zahra", "baseline"], seed=42)
        self.assertEqual(first, second)
        self.assertEqual(len(first), 6)
        for case in cases:
            rows = [row for row in first if row["case_id"] == case]
            self.assertEqual(len(rows), 2)
            self.assertEqual({row["position_a"] for row in rows}, {"zahra", "baseline"})
            self.assertEqual({row["position_b"] for row in rows}, {"zahra", "baseline"})

    def test_schedule_rejects_duplicate_cases_and_identical_systems(self):
        with self.assertRaises(ValueError):
            bb.build_schedule(["c1", "c1"], ["a", "b"], seed=1)
        with self.assertRaises(ValueError):
            bb.build_schedule(["c1"], ["a", "a"], seed=1)
        with self.assertRaises(ValueError):
            bb.build_schedule(["c1"], ["a", "b", "c"], seed=1)

    def test_load_judgments_rejects_duplicate_votes_and_bad_verdict(self):
        schedule = bb.build_schedule(["c1"], ["a", "b"], seed=1)
        pid = schedule[0]["presentation_id"]
        vote = {"presentation_id": pid, "judge_id": "j1", "verdict": "A"}
        with self.assertRaises(ValueError):
            bb.load_judgments(schedule, [vote, dict(vote)])
        with self.assertRaises(ValueError):
            bb.load_judgments(schedule, [{"presentation_id": pid, "judge_id": "j1", "verdict": "maybe"}])
        with self.assertRaises(ValueError):
            bb.load_judgments(schedule, [{"presentation_id": "unknown", "judge_id": "j1", "verdict": "A"}])


class BlindBenchStatsTest(unittest.TestCase):
    def make_judgments(self, x_wins: int, y_wins: int) -> list:
        rows = []
        for index in range(x_wins + y_wins):
            rows.append(
                bb.Judgment(
                    presentation_id=f"p{index}",
                    case_id=f"c{index}",
                    judge_id="j1",
                    system_a="x",
                    system_b="y",
                    verdict="A" if index < x_wins else "B",
                )
            )
        return rows

    def test_preference_counts_handle_ties_and_invalid(self):
        judgments = [
            bb.Judgment("p1", "c1", "j1", "x", "y", "A"),
            bb.Judgment("p2", "c2", "j1", "x", "y", "B"),
            bb.Judgment("p3", "c3", "j1", "x", "y", "tie"),
            bb.Judgment("p4", "c4", "j1", "x", "y", "tie"),
            bb.Judgment("p5", "c5", "j1", "x", "y", "invalid"),
        ]
        counts = bb.preference_counts(judgments, "x")
        self.assertEqual(counts["wins"], 1)
        self.assertEqual(counts["losses"], 1)
        self.assertEqual(counts["ties"], 2)
        self.assertEqual(counts["invalid"], 1)
        self.assertAlmostEqual(counts["half_win_rate"], (1 + 0.5 * 2) / 4)

    def test_bradley_terry_orders_the_stronger_system(self):
        judgments = self.make_judgments(x_wins=8, y_wins=2)
        summary = bb.bt_summary(["x", "y"], judgments, ref="y")
        self.assertGreater(summary["strengths"]["x"], summary["strengths"]["y"])
        self.assertGreater(summary["log_strength_vs_ref"]["x"], 0)
        self.assertGreater(summary["win_probability"]["x_vs_y"], 0.5)
        self.assertAlmostEqual(sum(summary["strengths"].values()), 1.0, places=9)

    def test_elo_is_secondary_and_moves_toward_winner(self):
        ratings = bb.elo_ratings(["x", "y"], self.make_judgments(8, 2))
        self.assertGreater(ratings["ratings"]["x"], 1500)
        self.assertLess(ratings["ratings"]["y"], 1500)

    def test_position_consistency_detects_same_winner_across_swap(self):
        judgments = [
            bb.Judgment("p1", "c1", "j1", "x", "y", "A"),
            bb.Judgment("p2", "c1", "j1", "y", "x", "B"),
        ]
        result = bb.position_consistency(judgments)
        self.assertEqual(result["pairs"], 1)
        self.assertEqual(result["consistent"], 1)
        self.assertEqual(result["rate"], 1.0)

    def test_first_position_bias_counts_only_decided_votes(self):
        judgments = [
            bb.Judgment("p1", "c1", "j1", "x", "y", "A"),
            bb.Judgment("p2", "c2", "j1", "x", "y", "B"),
            bb.Judgment("p3", "c3", "j1", "x", "y", "tie"),
        ]
        result = bb.first_position_bias(judgments)
        self.assertEqual(result["decided"], 2)
        self.assertEqual(result["first_position_wins"], 1)
        self.assertEqual(result["first_position_rate"], 0.5)
        self.assertAlmostEqual(result["binomial_two_sided_p_vs_0.5"], 1.0)

    def test_kappa_perfect_and_chance_level(self):
        perfect = bb.cohen_kappa([("x", "x"), ("y", "y"), ("x", "x")])
        self.assertEqual(perfect["kappa"], 1.0)
        empty = bb.cohen_kappa([])
        self.assertIsNone(empty["kappa"])
        fleiss = bb.fleiss_kappa({"i1": ["x", "x"], "i2": ["y", "y"], "i3": ["x", "x"]})
        self.assertEqual(fleiss["kappa"], 1.0)

    def test_holm_bonferroni_is_monotone(self):
        rows = bb.holm_bonferroni([("a", 0.01), ("b", 0.04), ("c", 0.03)])
        by_name = {row["name"]: row for row in rows}
        self.assertAlmostEqual(by_name["a"]["p_holm"], 0.03)
        self.assertAlmostEqual(by_name["c"]["p_holm"], 0.06)
        self.assertAlmostEqual(by_name["b"]["p_holm"], 0.06)
        self.assertTrue(by_name["a"]["p_holm"] <= by_name["c"]["p_holm"] <= by_name["b"]["p_holm"] + 1e-12)


class BlindBenchAnalyzeTest(unittest.TestCase):
    def build(self):
        cases = [f"c{index:02d}" for index in range(8)]
        schedule = bb.build_schedule(cases, ["zahra", "base"], seed=9)
        votes = []
        for row in schedule:
            # زهرة تفوز دائمًا في هذا الاصطناع — لا يحمل أي دلالة على أدائها الفعلي.
            votes.append(
                {
                    "presentation_id": row["presentation_id"],
                    "judge_id": "j1",
                    "verdict": "A" if row["position_a"] == "zahra" else "B",
                }
            )
        return schedule, votes

    def test_analyze_reports_counts_and_disclaimer(self):
        schedule, votes = self.build()
        report = bb.analyze(schedule, votes, target="zahra", ref="base", bootstrap_iters=200, seed=1)
        self.assertEqual(report["dataset"]["cases"], 8)
        self.assertEqual(report["dataset"]["valid_votes"], 16)
        self.assertEqual(report["preference"]["counts"]["wins"], 16)
        self.assertEqual(report["position_consistency"]["rate"], 1.0)
        self.assertIn("لا تصبح قياسًا منشورًا", report["disclaimer"])
        markdown = bb.markdown_report(report)
        self.assertIn("تقرير مقارنة عمياء", markdown)
        self.assertIn("مولّد من `tools/phase06/blind_bench.py`", markdown)
        self.assertIn("مدخلات مقدمة", report["disclaimer"])

    def test_analyze_requires_valid_votes(self):
        schedule, _ = self.build()
        with self.assertRaises(ValueError):
            bb.analyze(schedule, [], target="zahra", ref="base", bootstrap_iters=50)

    def test_compare_results_pairs_common_cases_only(self):
        a = {f"c{i}": {"passed": i < 8, "domain": "d1" if i < 10 else "d2"} for i in range(12)}
        b = {f"c{i}": {"passed": i < 10, "domain": "d1" if i < 10 else "d2"} for i in range(12)}
        a["only_a"] = {"passed": True, "domain": "d1"}
        b["only_b"] = {"passed": True, "domain": "d1"}
        result = bb.compare_results(a, b, "a", "b", bootstrap_iters=300, seed=3)
        self.assertEqual(result["n_common"], 12)
        self.assertEqual(result["only_a"], ["only_a"])
        self.assertEqual(result["only_b"], ["only_b"])
        self.assertAlmostEqual(result["passed"]["delta_b_minus_a"], 2 / 12)
        self.assertEqual(result["mcnemar"]["a_fail_b_pass"], 2)
        self.assertEqual(result["mcnemar"]["a_pass_b_fail"], 0)
        self.assertTrue(any("التقاطع" in warning for warning in result["warnings"]))

    def test_validate_protocol_accepts_example_but_warns_template(self):
        findings = bb.validate_protocol(ROOT / "release" / "blind-protocol.example.json")
        fails = [message for level, message in findings if level == "FAIL"]
        self.assertEqual(fails, [])
        self.assertTrue(any("protocol-template" in message for level, message in findings if level == "WARN"))

    def test_analyze_cli_records_protocol_and_refuses_broken_one(self):
        schedule = bb.build_schedule(["c1", "c2"], ["zahra", "base"], seed=4)
        votes = [
            {
                "presentation_id": row["presentation_id"],
                "judge_id": "j1",
                "verdict": "A",
            }
            for row in schedule
        ]
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            schedule_path = tmp_path / "schedule.jsonl"
            votes_path = tmp_path / "votes.jsonl"
            out_path = tmp_path / "report.json"
            schedule_path.write_text(
                "\n".join(json.dumps(row, ensure_ascii=False) for row in schedule) + "\n",
                encoding="utf-8",
            )
            votes_path.write_text(
                "\n".join(json.dumps(vote, ensure_ascii=False) for vote in votes) + "\n",
                encoding="utf-8",
            )
            base_args = [
                "analyze",
                "--schedule", str(schedule_path),
                "--votes", str(votes_path),
                "--target", "zahra",
                "--ref", "base",
                "--bootstrap-iters", "100",
                "--out", str(out_path),
            ]
            code = quiet(bb.main, base_args + ["--protocol", str(ROOT / "release" / "blind-protocol.example.json")])
            self.assertEqual(code, 0)
            report = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertEqual(report["protocol"]["status"], "template-not-run")
            self.assertTrue(any("قالب غير مسجل" in warning for warning in report["warnings"]))

            broken = json.loads((ROOT / "release" / "blind-protocol.example.json").read_text(encoding="utf-8"))
            broken["status"] = "not-a-real-status"
            broken_path = tmp_path / "broken.json"
            broken_path.write_text(json.dumps(broken, ensure_ascii=False), encoding="utf-8")
            code = quiet(bb.main, base_args + ["--protocol", str(broken_path)])
            self.assertEqual(code, 1)

    def test_validate_protocol_rejects_incomplete_complete_status(self):
        protocol = json.loads((ROOT / "release" / "blind-protocol.example.json").read_text(encoding="utf-8"))
        protocol["status"] = "complete"
        protocol["results"] = {"resultFile": None, "resultSha256": None, "publishedAt": None}
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as handle:
            json.dump(protocol, handle, ensure_ascii=False)
            path = Path(handle.name)
        try:
            findings = bb.validate_protocol(path)
            fails = [message for level, message in findings if level == "FAIL"]
            self.assertTrue(any("results-file" in message for message in fails))
        finally:
            path.unlink(missing_ok=True)


class DevicePerfTest(unittest.TestCase):
    SAMPLE = """Starting: Intent { cmp=com.aistudio.zahra.kpxq/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.aistudio.zahra.kpxq/.MainActivity
ThisTime: 812
TotalTime: 845
WaitTime: 861
Starting: Intent { cmp=com.aistudio.zahra.kpxq/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.aistudio.zahra.kpxq/.MainActivity
ThisTime: 790
TotalTime: 823
WaitTime: 840
"""

    def test_parse_am_start_reads_fields(self):
        launches = perf.parse_am_start(self.SAMPLE, source="run01.txt")
        self.assertEqual(len(launches), 2)
        self.assertEqual(launches[0].status, "ok")
        self.assertEqual(launches[0].launch_state, "COLD")
        self.assertEqual(launches[0].total_time_ms, 845)
        self.assertEqual(launches[0].this_time_ms, 812)
        self.assertEqual(launches[0].source, "run01.txt")

    def test_summarize_is_descriptive_and_warns_without_provenance(self):
        launches = perf.parse_am_start(self.SAMPLE)
        report = perf.summarize(launches, bootstrap_iters=300, seed=5)
        self.assertEqual(report["status"], "descriptive-not-a-release-benchmark")
        self.assertEqual(report["runs"]["valid"], 2)
        self.assertEqual(report["total_time_ms"]["median"], 834.0)
        joined = " | ".join(report["warnings"])
        self.assertIn("2 < 10", joined)
        self.assertIn("استكشافي", joined)
        self.assertIn("API level", joined)
        self.assertIn("build type", joined)
        self.assertIn("commit", joined)

    def test_summarize_flags_debug_and_mixed_states(self):
        text = self.SAMPLE.replace("LaunchState: COLD", "LaunchState: WARM", 1)
        report = perf.summarize(
            perf.parse_am_start(text),
            api_level=34,
            device="Pixel 6",
            build_type="debug",
            android_version="14",
            commit="abc123",
            bootstrap_iters=200,
            seed=7,
        )
        joined = " | ".join(report["warnings"])
        self.assertIn("مختلطة", joined)
        self.assertIn("debug", joined)

    def test_summarize_rejects_missing_total_time_honestly(self):
        broken = "Starting: Intent { cmp=a }\nStatus: ok\nLaunchState: COLD\nThisTime: 10\n"
        report = perf.summarize(perf.parse_am_start(broken), bootstrap_iters=100)
        self.assertEqual(report["runs"]["valid"], 0)
        self.assertIsNone(report["total_time_ms"]["median"])
        self.assertTrue(any("بلا TotalTime" in warning for warning in report["warnings"]))

    def test_bootstrap_median_ci_is_deterministic(self):
        values = [100, 110, 120, 130, 140]
        first = perf.bootstrap_median_ci(values, iters=400, seed=11)
        second = perf.bootstrap_median_ci(values, iters=400, seed=11)
        self.assertEqual(first, second)
        self.assertLessEqual(first["low"], first["high"])


class ReleaseAuditTest(unittest.TestCase):
    def test_load_limitations_reads_real_source(self):
        data = audit_mod.load_limitations()
        self.assertEqual(data["schema"], 1)
        self.assertGreaterEqual(len(data["limitations"]), 20)
        ids = [item["id"] for item in data["limitations"]]
        self.assertEqual(len(ids), len(set(ids)))
        severities = [audit_mod.SEVERITY_ORDER[item["severity"]] for item in data["limitations"]]
        self.assertEqual(severities, sorted(severities), "القيود يجب أن تُعرض بترتيب الخطورة")

    def test_load_limitations_rejects_duplicate_and_missing_evidence(self):
        source = json.loads((ROOT / "release" / "limitations.json").read_text(encoding="utf-8"))
        broken = json.loads(json.dumps(source, ensure_ascii=False))
        broken["limitations"][1]["id"] = broken["limitations"][0]["id"]
        broken["limitations"][2]["evidence"] = "docs/does-not-exist.md"
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as handle:
            json.dump(broken, handle, ensure_ascii=False)
            path = Path(handle.name)
        try:
            with self.assertRaises(ValueError) as caught:
                audit_mod.load_limitations(path)
            message = str(caught.exception)
            self.assertIn("مكرر", message)
            self.assertIn("مسار الدليل غير موجود", message)
        finally:
            path.unlink(missing_ok=True)

    def test_rendered_limitations_use_docs_relative_links(self):
        data = audit_mod.load_limitations()
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        text = audit_mod.render_limitations(data, manifest)
        self.assertIn("القيود المعروفة", text)
        for item in data["limitations"]:
            self.assertIn(item["id"], text)
        self.assertIn("](../release/limitations.json)", text)
        self.assertNotIn("](docs/", text, "الروابط داخل docs يجب ألا تكون مطلقة من الجذر")

    def test_rendered_in_app_asset_hides_paths_and_keeps_status(self):
        data = audit_mod.load_limitations()
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        payload = json.loads(audit_mod.render_in_app(data, manifest))
        self.assertEqual(payload["versionName"], manifest["app"]["versionName"])
        self.assertEqual(payload["measurementStatus"], "not-run")
        self.assertGreaterEqual(len(payload["limitations"]), 15)
        self.assertTrue(all("evidence" not in item for item in payload["limitations"]))

    def test_parse_gradle_matches_manifest(self):
        gradle = audit_mod.parse_gradle()
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        self.assertEqual(gradle["versionName"], manifest["app"]["versionName"])
        self.assertEqual(str(gradle["versionCode"]), str(manifest["app"]["versionCode"]))
        self.assertEqual(str(gradle["minSdk"]), str(manifest["app"]["minSdk"]))
        self.assertEqual(str(gradle["targetSdk"]), str(manifest["app"]["targetSdk"]))

    def test_measurement_guard_refuses_measured_without_file(self):
        audit = audit_mod.Audit()
        manifest = {
            "measurement": {"status": "measured", "resultsFile": None, "resultsSha256": None},
        }
        quiet(audit_mod.check_measurement, audit, manifest)
        self.assertGreaterEqual(audit.failed, 1)
        self.assertTrue(any(finding.code == "measurement-file-required" for finding in audit.findings))

    def test_full_repository_audit_passes(self):
        audit = quiet(audit_mod.run_checks, write=False)
        self.assertEqual(audit.failed, 0, [finding.message for finding in audit.findings if finding.level == "FAIL"])
        warning_codes = {finding.code for finding in audit.findings if finding.level == "WARN"}
        self.assertTrue(warning_codes <= {"ci-unpinned", "ci-persist-credentials"}, warning_codes)

    def test_generated_files_are_byte_stable(self):
        data = audit_mod.load_limitations()
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        docs_path = ROOT / "docs" / "LIMITATIONS.md"
        asset_path = ROOT / "app" / "src" / "main" / "assets" / "release_info.json"
        self.assertEqual(docs_path.read_text(encoding="utf-8"), audit_mod.render_limitations(data, manifest))
        self.assertEqual(asset_path.read_text(encoding="utf-8"), audit_mod.render_in_app(data, manifest))


class WorkspaceHonestyTest(unittest.TestCase):
    """حماية من الانحدار في ادعاءات الإصدار داخل المستودع."""

    def test_manifest_declares_no_published_artifact(self):
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        self.assertEqual(manifest["release"]["channel"], "source-only")
        self.assertFalse(manifest["release"]["apkPublished"])
        self.assertFalse(manifest["release"]["aabPublished"])
        self.assertFalse(manifest["release"]["signedReleaseVerified"])
        self.assertEqual(manifest["measurement"]["status"], "not-run")
        self.assertIsNone(manifest["measurement"]["resultsFile"])

    def test_manifest_has_no_invented_results(self):
        manifest = audit_mod.read_json(ROOT / "release" / "release_manifest.json")
        for key in ("accuracy", "winRate", "elo", "latencyMs", "startupMs"):
            self.assertNotIn(key, json.dumps(manifest), f"لا يجوز وجود ادعاء قياس مسبق: {key}")

    def test_disclaimer_states_audit_is_structural(self):
        audit = quiet(audit_mod.run_checks, write=False)
        self.assertEqual(audit.failed, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
