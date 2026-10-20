# Zahra Phase 5: Scope Reconciliation Report

**Project:** Zahra (زهرة) — Privacy-First Offline AI Assistant  
**Core Principle:** Truth Before Intelligence (الحقيقة قبل الذكاء)  
**Date:** October 2026  
**Status:** PROPOSED & BOUNDED (Aligned with Canonical Roadmap & Repository Architecture)

---

## 1. Baseline Audit & Precedence Analysis

1. **Repository Documents Audited:**
   - `ZAHRA_SPECIFICATION_V2.md`: Outlines MVP foundation (Phase 1: 6 layers, 25 intents, Arabic normalizer, Naive Bayes classifier, TextRank summarizer, Curated KBs, Confidence gate, Liquid Glass UI) and Phase 2 (Reliable Memory & Scheduling: Workstreams A–E).
   - `PHASE_3_LOCAL_KNOWLEDGE_ENGINE.md` & `PHASE_3_COMPLETION_REPORT.md`: Details Phase 3 implementation (KnowledgeRecord schema, LocalKnowledgeStore, OfflineKnowledgeIndex BM25, ArabicKnowledgeNormalizer, OfflineSynonymDictionary, Epistemic retrieval gating).
   - `PHASE_4_COMPLETION_REPORT.md`: Details Phase 4 implementation (DialectAndColloquialAdapter, AmbiguityAndClarificationDetector, ContextAndCorrectionTracker, Bounded Deterministic ReasoningEngine, ContradictionDetector, EpistemicGroundingPipeline, SafeActionExecutor, Ethical/Islamic Knowledge Safeguards).
   - Test Suite Baseline: **156 tests passing, 0 failures, 0 errors, 0 regressions** across 10 test suites.

2. **Official Phase 5 Specification Status:**
   - The repository markdown files documented Phases 1 through 4. No pre-existing `PHASE_5_SPECIFICATION.md` was committed in the repository.
   - The current prompt provides the authoritative direction: **Phase 5: Lightweight Local Intelligence Engine**.
   - In accordance with the prompt's instruction ("produce a concise scope-reconciliation report, propose a bounded Phase 5 aligned with the objectives below, and proceed only if the repository's existing roadmap or documented implementation decisions support that scope"), this document defines the bounded scope.

---

## 2. Identified Deficiencies in Existing Intelligence Architecture

1. **Monolithic Single-Step Execution:**
   - Zahra currently assumes an input maps to exactly one intent.
   - Compound or sequenced requests (e.g., "احسب 25 * 4 ثم احفظ الناتج في ملاحظة" or "لخص النص وضعه في مهمة للغد") cannot be planned, composed, or executed.
2. **Uncalibrated Intent Confidence & Heuristic Spikes:**
   - Confidence scores in `ZahraRouter` are either hardcoded constants or uncalibrated unigram Naive Bayes softmax probabilities that tend toward extreme overconfidence on longer inputs or arbitrary values on short inputs.
   - No feature extraction exists for character n-grams, Arabic morphological affixes, or syntactic signals.
   - No entropy or margin-of-confidence evaluation exists to detect distribution dispersion or close competitors.
3. **Absence of Parameter Binding & DAG Task Planning:**
   - Intermediate outputs (e.g. calculation results or text summaries) cannot be piped as arguments into subsequent operations (e.g. tasks or notes).
   - There is no cycle-checked Directed Acyclic Graph (DAG) planner for compound user objectives.
4. **Lack of Transactional Plan Execution & Rollback:**
   - If a multi-step operation fails midway, partial mutations remain unmanaged.

---

## 3. Approved Bounded Phase 5 Scope

To advance Zahra toward genuine, useful intelligence while strictly preserving the invariants (Zero-LLM, Zero-Transformer, Zero-Network, Zero-Telemetry, offline execution, < 50MB app, determinism, and *Truth Before Intelligence*), Phase 5 implements:

1. **`NlpFeatureExtractor`** (`com.example.zahra.intelligence.features`):
   - Non-Transformer feature extraction producing sparse representations from Arabic and English text.
   - Features: Word unigrams/bigrams, Character 3/4-grams, Morphological affixes (`ال`, `و`, `ف`, `ب`, `ل`, `ـهم`, `ـها`, `ـكم`, `ـنا`), lexical cue flags, length and script ratios.
2. **`CalibratedClassifier`** (`com.example.zahra.intelligence.classifier`):
   - Multi-feature statistical classification in log-space.
   - Temperature scaling ($T$) and Platt-style isotonic probability calibration.
   - Shannon prediction entropy ($H = -\sum p \ln p$) and confidence margin ($M = p_{(1)} - p_{(2)}$).
   - Calibrated epistemic abstention gate: honest abstention when entropy is high or margin is narrow.
3. **`StructuredTaskPlanner` & `CompoundRequestAnalyzer`** (`com.example.zahra.intelligence.planner`):
   - Compound request decomposition using Arabic/English conjunctions with quote protection.
   - Formal `TaskPlan` with `PlannedStep`, dependencies (`dependsOnStepIds`), preconditions, and argument bindings.
   - Directed Acyclic Graph (DAG) cycle validation and topological sort.
   - `PlanArgumentBinder` for piping outputs between dependent steps.
4. **`PlanExecutor`** (`com.example.zahra.intelligence.execution`):
   - Controlled, step-by-step transactional execution of plans with precondition validation.
   - Postcondition verification and transactional rollback if a step fails.
5. **Integration with `ZahraRouter` & Interactive UI**:
   - Seamless routing for compound requests into plan execution.
   - `PlanExecutionCard` in Compose UI displaying steps, statuses, intermediate outputs, and rollback states.
6. **Testing & Empirical Benchmark Suite**:
   - Comprehensive test suite covering feature extraction, calibration, abstention, compound planning, argument binding, execution, and rollback.
   - Empirical latency, cold start, and memory footprint benchmarks.

---

## 4. Architectural Invariants Maintained

- 100% Offline execution, Zero external APIs, Zero network dependencies.
- Zero Large Language Models, Zero Transformer architectures.
- Strict backward compatibility with Phases 1–4 (all 156 existing tests must continue to pass).
- Deterministic and explainable execution traces.
- Honest abstention (*Truth Before Intelligence*).
