#!/usr/bin/env bash
# تحقق تنفيذي حقيقي لمنطق المرحلة 3 (بلا Gradle، بلا Android SDK، بلا شبكة).
#
#   ./tools/offline-typecheck/phase03/run.sh                  # فحوصات التقطيع/الفهرس/الاسترجاع/البوابة
#   ./tools/offline-typecheck/phase03/run.sh --emit-fixtures  # توليد توائم التقطيع من Kotlin
#
# المتطلبات (مرة واحدة):
#   pip install --break-system-packages jdk4py
#   npm pack kotlin-compiler@2.4.21 && tar xzf kotlin-compiler-2.4.21.tgz -C /tmp/kc
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/verify-phase03}
export JAVA_HOME=${JAVA_HOME:-$(python3 -c "import jdk4py;print(jdk4py.JAVA_HOME)")}
export PATH="$JAVA_HOME/bin:$PATH"
export LC_ALL=${LC_ALL:-C.UTF-8}
export LANG=${LANG:-C.UTF-8}

MAIN_CLASS="SelfCheckKt"
MAIN_FILE="SelfCheck.kt"
if [ "${1:-}" = "--emit-fixtures" ] || [ "${1:-}" = "--emit-lexicon" ]; then
  MAIN_CLASS="FixturesKt"
  MAIN_FILE="Fixtures.kt"
fi

rm -rf "$OUT"; mkdir -p "$OUT"
"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT" \
  "$ROOT"/app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/tools/MiniJson.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/SemanticSearchEngine.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/UnitTokenizer.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/HybridWeights.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/KnowledgeUnit.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/HybridRetriever.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/UnitRetrievalGate.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/knowledge/unit/UnitKnowledgeService.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/routing/ConfidenceGatePolicy.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/routing/GateOutcome.kt \
  "$ROOT"/tools/offline-typecheck/phase03/"$MAIN_FILE"

EXTRA=("$@")

"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
  -Dsun.jnu.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -cp "$OUT:$KC_DIR/lib/kotlin-stdlib.jar" "$MAIN_CLASS" "$ROOT" "${EXTRA[@]}"
