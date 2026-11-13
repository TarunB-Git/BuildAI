#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec 9>/tmp/buildai-validation.lock
flock -n 9 || { echo 'Another BuildAI build or emulator check is running.' >&2; exit 1; }
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/home/tar_guest/Android/Sdk}"
export CMAKE_BUILD_PARALLEL_LEVEL=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:ActiveProcessorCount=2"
nice -n 10 ./gradlew --no-daemon --max-workers=1 "$@"
