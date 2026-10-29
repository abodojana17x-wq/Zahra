#!/usr/bin/env bash
# تحقق نوعي لحزمة الذاكرة + التشفير مقابل stubs محلية (لا Android SDK).
# يثبت أن الكود يُترجم؛ لا يشغّل SharedPreferences ولا AndroidKeyStore ولا Robolectric.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/zahra-phase04-memory}
export JAVA_HOME=${JAVA_HOME:-$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')}
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf "$OUT"; mkdir -p "$OUT"

"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT" \
  "$ROOT"/tools/offline-typecheck/phase04/stubs/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/security/ZahraCrypto.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/memory/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/model/ZahraModels.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/intelligence/planner/TaskPlanModels.kt

echo "memory + crypto: type-check OK (stubs فقط، ليس تشغيلًا على Android)"
