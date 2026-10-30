#!/usr/bin/env bash
# المرحلة 6 — ترجمة وتشغيل اختبارات معلومات الإصدار والقيود دون Gradle/Android SDK.
#
# ما يفعله: kotlinc فعلي + testkit المصغّر (Assert/TestRunner) + assertions حقيقية
#   1) يترجم MiniJson.kt (قارئ JSON النقي) وReleaseInfo.kt (نموذج الإصدار).
#   2) يشغّل كل دوال @Test في ReleaseInfoTest.kt.
# ما لا يفعله: لا Compose، لا Android assets، لا Robolectric، ولا Gradle.
#   صفحة ReleaseInfoDialog.kt لا تُترجم هنا لأنها تعتمد واجهة Compose الحقيقية.
#
# المتطلبات (مرة واحدة، بيئة بلا JDK):
#   python3 -m venv /tmp/jdkvenv && /tmp/jdkvenv/bin/pip install jdk4py
#   JDK4PY_PYTHON=/tmp/jdkvenv/bin/python ./tools/offline-typecheck/phase06/run-tests.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/zahra-phase06-tests}
PYTHON=${JDK4PY_PYTHON:-python3}
export JAVA_HOME=${JAVA_HOME:-$("$PYTHON" -c 'import jdk4py; print(jdk4py.JAVA_HOME)')}
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$KC_DIR/bin/kotlinc" ]; then
  echo "لم أجد kotlinc في $KC_DIR — شغّل: mkdir -p /tmp/kc && cd /tmp/kc && npm pack kotlin-compiler@2.4.21 && tar xzf kotlin-compiler-2.4.21.tgz" >&2
  exit 2
fi

rm -rf "$OUT"; mkdir -p "$OUT/src"
# `import org.junit.Assert.*` أسلوب Java ← `import org.junit.*` (دوال عليا في testkit).
sed 's/^import org\.junit\.Assert\.\*$/import org.junit.*/' \
  "$ROOT/app/src/test/java/com/example/zahra/diagnostics/ReleaseInfoTest.kt" > "$OUT/src/ReleaseInfoTest.kt"

"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT/classes" \
  "$ROOT"/app/src/main/java/com/example/zahra/tools/MiniJson.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/diagnostics/ReleaseInfo.kt \
  "$ROOT"/tools/offline-typecheck/phase04/testkit/Assert.kt \
  "$ROOT"/tools/offline-typecheck/phase04/testkit/TestRunner.kt \
  "$OUT"/src/ReleaseInfoTest.kt

LC_ALL=C.UTF-8 "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes:$KC_DIR/lib/kotlin-stdlib.jar" TestRunner \
  com.example.zahra.diagnostics.ReleaseInfoTest
