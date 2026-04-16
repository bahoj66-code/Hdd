#!/bin/sh
# Gradle wrapper launcher script

APP_NAME="Gradle"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

SCRIPT_DIR=$(dirname "$0")
GRADLE_HOME="${SCRIPT_DIR}/.gradle"

exec java $DEFAULT_JVM_OPTS -jar "${SCRIPT_DIR}/gradle/wrapper/gradle-wrapper.jar" "$@"
