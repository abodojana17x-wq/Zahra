#!/usr/bin/env bash
# يشغّل اختبارات الحوار/السياق الخالصة (منطق Kotlin) دون Gradle أو Android SDK.
# ما يفعله: kotlinc فعلي + assertions حقيقية + تشغيل كل دوال @Test.
# ما لا يفعله: لا Robolectric، لا Compose، لا SharedPreferences، لا AndroidKeyStore.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/zahra-phase04-tests}
export JAVA_HOME=${JAVA_HOME:-$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')}
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf "$OUT"; mkdir -p "$OUT/src"

# نسخة مؤقتة من الاختبارات: `import org.junit.Assert.*` (أسلوب Java) ←
# `import org.junit.*` (دوال عليا في testkit). لا يُعدَّل أي ملف في المستودع.
for test in DialogueStateTest DialogueMultiTurnTest DialogueContextUnificationTest \
            DialogueReferenceFormsTest DialogueFinalSafetyTest; do
  sed 's/^import org\.junit\.Assert\.\*$/import org.junit.*/' \
    "$ROOT/app/src/test/java/com/example/zahra/dialogue/$test.kt" > "$OUT/src/$test.kt"
done

"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT/classes" \
  "$ROOT"/app/src/main/java/com/example/zahra/dialogue/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/understanding/ContextAndCorrectionTracker.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/context/ConversationContext.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/model/ZahraModels.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/intelligence/planner/TaskPlanModels.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/intelligence/mood/MoodAnalyzer.kt \
  "$ROOT"/tools/offline-typecheck/phase04/testkit/Assert.kt \
  "$ROOT"/tools/offline-typecheck/phase04/testkit/TestRunner.kt \
  "$OUT"/src/*.kt

"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT/classes:$KC_DIR/lib/kotlin-stdlib.jar" TestRunner \
  com.example.zahra.dialogue.DialogueStateTest \
  com.example.zahra.dialogue.DialogueMultiTurnTest \
  com.example.zahra.dialogue.DialogueContextUnificationTest \
  com.example.zahra.dialogue.DialogueReferenceFormsTest \
  com.example.zahra.dialogue.DialogueFinalSafetyTest
