#!/usr/bin/env bash
# تحقق نوعي/تشغيلي بدون Gradle ولا Android SDK (يُستخدم عندما تكون البيئة بلا JDK/SDK
# أو بلا وصول إلى Maven/Gradle، كما في جلسات الوكلاء المعزولة).
#
# ما يفعله:
#   1) يُترجم ملفات كود القياس (app/src/test/java/com/example/eval/*.kt) + DiagnosticsEngine.kt
#      مقابل **stubs** تحاكي توقيعات Gson/JUnit/Robolectric/Android/zahra.
#   2) يُشغّل Main.kt فيقارن أرقام EvalStats.kt (Kotlin) بالأرقام المرجعية في
#      scripts/test_stats.py (Python).
#
# ما لا يفعله: لا يُشغّل Robolectric ولا Harness ولا أي شيء يحتاج android.jar حقيقي.
# الحقيقة الكاملة تبقى في CI (./gradlew :app:testDebugUnitTest).
#
# المتطلبات (تُجلب مرة واحدة):
#   pip install jdk4py                # JDK
#   npm pack kotlin-compiler@2.4.21   # kotlinc
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/verify/out}

if [ ! -x "$KC_DIR/bin/kotlinc" ]; then
  echo "لم أجد kotlinc في $KC_DIR — راجع المتطلبات أعلى الملف." >&2
  exit 2
fi
export JAVA_HOME=${JAVA_HOME:-$(python3 -c "import jdk4py;print(jdk4py.JAVA_HOME)")}

rm -rf "$OUT"; mkdir -p "$OUT"
"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT" \
  "$ROOT"/tools/offline-typecheck/stubs/*.kt \
  "$ROOT"/app/src/test/java/com/example/eval/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/diagnostics/DiagnosticsEngine.kt \
  "$ROOT"/tools/offline-typecheck/Main.kt

"$JAVA_HOME/bin/java" -cp "$OUT:$KC_DIR/lib/kotlin-stdlib.jar" MainKt
