#!/bin/sh
# Minimal Gradle wrapper launcher (Gradle 9.x wrapper jar).
# Usage: ./gradlew <tasks...>
# The wrapper jar reads gradle/wrapper/gradle-wrapper.properties for the
# distribution URL (services.gradle.org) and caches it under GRADLE_USER_HOME.

set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd)

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found." >&2
  exit 1
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
  -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
