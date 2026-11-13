#!/usr/bin/env bash
# Run only after compilation; never downloads a model or wipes the AVD.
set -euo pipefail
cd "$(dirname "$0")/.."
exec 9>/tmp/buildai-validation.lock
flock -n 9 || { echo 'Another BuildAI build or emulator check is running.' >&2; exit 1; }
sdk="${ANDROID_HOME:-/home/tar_guest/Android/Sdk}"
adb="$sdk/platform-tools/adb"
serial=emulator-5556
out="${BUILDAI_TEST_OUTPUT:-/tmp/buildai-emulator-checks}"
mkdir -p "$out"
ram_mb="${BUILDAI_EMULATOR_RAM_MB:-2048}"
test_seconds="${BUILDAI_TEST_TIMEOUT_SECONDS:-600}"
emulator_seconds="${BUILDAI_EMULATOR_TIMEOUT_SECONDS:-900}"
window_seconds="${BUILDAI_NATIVE_BUILDER_WINDOW_SECONDS:-420}"
model_id="${BUILDAI_NATIVE_MODEL_ID:-qwen-06}"
for value in "$ram_mb" "$test_seconds" "$emulator_seconds" "$window_seconds"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo 'Resource bounds must be positive decimal integers.' >&2; exit 1; }
done
((ram_mb >= 1536 && ram_mb <= 3072 && test_seconds >= 60 && test_seconds <= 1200 && emulator_seconds <= 1500 && emulator_seconds >= test_seconds + 240 && window_seconds >= 30 && window_seconds <= 900)) || { echo 'Requested resources exceed the bounded development policy.' >&2; exit 1; }
available_kb=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
((available_kb >= (ram_mb + 4096) * 1024)) || { echo 'Not enough available host RAM for a safe emulator run.' >&2; exit 1; }
case "$model_id" in
  qwen-06) fixture_name=test.gguf; fixture_sha=da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4 ;;
  qwen-17) fixture_name=test-qwen-17.gguf; fixture_sha=061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a ;;
  *) echo 'This harness supports only the two pinned development fixtures.' >&2; exit 1 ;;
esac
if "$adb" devices | rg -q '^emulator-'; then
  echo 'An emulator is already running; stop it before this check.' >&2
  exit 1
fi
cpus=$(python3 -c 'import os; print(",".join(map(str, sorted(os.sched_getaffinity(0))[:2])))')
emulator_pid=''
previous_storage_threshold=''
declare -A prior_animation_scales=()
cleanup() {
  for setting in "${!prior_animation_scales[@]}"; do
    if [[ "${prior_animation_scales[$setting]}" == null ]]; then "$adb" -s "$serial" shell settings delete global "$setting" >/dev/null 2>&1 || true
    else "$adb" -s "$serial" shell settings put global "$setting" "${prior_animation_scales[$setting]}" >/dev/null 2>&1 || true; fi
  done
  if [[ -n "$previous_storage_threshold" ]]; then
    if [[ "$previous_storage_threshold" == null ]]; then "$adb" -s "$serial" shell settings delete global sys_storage_threshold_max_bytes >/dev/null 2>&1 || true
    else "$adb" -s "$serial" shell settings put global sys_storage_threshold_max_bytes "$previous_storage_threshold" >/dev/null 2>&1 || true; fi
  fi
  if [[ "${BUILDAI_NATIVE_SIZE:-false}" != true ]]; then
    "$adb" -s "$serial" shell wm size reset >/dev/null 2>&1 || true
    "$adb" -s "$serial" shell wm density reset >/dev/null 2>&1 || true
  fi
  "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
  if [[ -n "$emulator_pid" ]]; then
    kill "$emulator_pid" 2>/dev/null || true
    wait "$emulator_pid" 2>/dev/null || true
  fi
  echo 'Emulator shutdown requested.'
}
trap cleanup EXIT INT TERM
start=$SECONDS
timeout --signal=TERM --kill-after=10s "${emulator_seconds}s" nice -n 19 taskset -c "$cpus" \
  "$sdk/emulator/emulator" -avd "${BUILDAI_AVD:-Medium_Phone_API_35}" \
  -port 5556 -cores 2 -memory "$ram_mb" -no-window -no-audio -no-snapshot \
  -gpu swiftshader -feature -Vulkan > "$out/emulator.log" 2>&1 &
emulator_pid=$!
booted=0
for ((attempt=0; attempt<90; attempt++)); do
  if [[ $("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]]; then booted=1; break; fi
  kill -0 "$emulator_pid" 2>/dev/null || break
  sleep 2
done
[[ $booted == 1 ]] || { echo 'Emulator missed its 180-second boot deadline.' >&2; exit 1; }
echo "Boot: $((SECONDS-start)) seconds; two guest CPUs, $ram_mb MB, two bounded host cores."
"$adb" -s "$serial" shell input keyevent KEYCODE_WAKEUP
"$adb" -s "$serial" shell wm dismiss-keyguard
"$adb" -s "$serial" shell svc power stayon true
if [[ "${BUILDAI_DISABLE_ANIMATIONS:-true}" == true ]]; then
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    prior_animation_scales[$setting]=$("$adb" -s "$serial" shell settings get global "$setting" | tr -d '\r')
    "$adb" -s "$serial" shell settings put global "$setting" 0
  done
fi
if [[ "${BUILDAI_NATIVE_SIZE:-false}" != true ]]; then
  "$adb" -s "$serial" shell wm size 540x1200
  "$adb" -s "$serial" shell wm density 210
fi
if [[ "${BUILDAI_LOW_STORAGE_INSTALL:-false}" == true ]]; then
  previous_storage_threshold=$("$adb" -s "$serial" shell settings get global sys_storage_threshold_max_bytes | tr -d '\r')
  "$adb" -s "$serial" shell settings put global sys_storage_threshold_max_bytes 33554432
  # Reclaim only Android-managed disposable caches; retain app data and model files.
  "$adb" -s "$serial" shell pm trim-caches 512M internal
fi
if [[ "${BUILDAI_STORAGE_DIAGNOSTIC:-false}" == true ]]; then
  "$adb" -s "$serial" shell df -h /data
  "$adb" -s "$serial" shell run-as com.buildai.app.debug du -h files cache 2>/dev/null || true
  exit 0
fi
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [[ -n "${BUILDAI_MODEL_FIXTURE:-}" ]]; then
  echo "$fixture_sha  $BUILDAI_MODEL_FIXTURE" | sha256sum -c -
  fixture_size=$(stat -c %s "$BUILDAI_MODEL_FIXTURE")
  disk_available=$(df --output=avail -B1 "$out" | tail -n 1 | tr -d ' ')
  ((disk_available >= fixture_size + 1073741824)) || { echo 'Keep at least 1 GiB free after the guest fixture copy.' >&2; exit 1; }
  "$adb" -s "$serial" shell run-as com.buildai.app.debug mkdir -p files/models
  # Stream straight into test-only private storage; do not allocate a duplicate /data/local/tmp copy.
  "$adb" -s "$serial" shell -T "run-as com.buildai.app.debug sh -c 'cat > files/models/$fixture_name'" < "$BUILDAI_MODEL_FIXTURE"

fi
classes="${1:-com.buildai.app.ProjectRecoveryTest,com.buildai.app.LocalInferenceTest}"
selection_grammar="${BUILDAI_SELECTION_GRAMMAR:-true}"
[[ "$selection_grammar" == true || "$selection_grammar" == false ]] || { echo 'Selection grammar diagnostic must be true or false.' >&2; exit 1; }
start=$SECONDS
timeout --signal=TERM --kill-after=5s "${test_seconds}s" "$adb" -s "$serial" shell am instrument -w -r \
  -e class "$classes" -e nativeModelId "$model_id" -e nativeBuilderWindowSeconds "$window_seconds" -e selectionGrammarEnabled "$selection_grammar" com.buildai.app.debug.test/androidx.test.runner.AndroidJUnitRunner \
  > "$out/tests.log" 2>&1
echo "Instrumentation: $((SECONDS-start)) seconds."
cat "$out/tests.log"
if [[ "${BUILDAI_PROCESS_RECOVERY:-false}" == true ]]; then
  for phase in seed cold; do
    if [[ "$phase" == cold ]]; then
      "$adb" -s "$serial" shell am force-stop com.buildai.app.debug
    fi
    timeout --signal=TERM --kill-after=5s 60s "$adb" -s "$serial" shell am instrument -w -r \
      -e class com.buildai.app.ProcessRecoveryTest -e recoveryPhase "$phase" \
      com.buildai.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "$out/recovery-$phase.log" 2>&1
    cat "$out/recovery-$phase.log"
    rg -q 'OK \(2 tests\)' "$out/recovery-$phase.log"
    # One intentional phase filter skip; require the other test to execute and pass.
    [[ $(rg -c '^INSTRUMENTATION_STATUS_CODE: 0$' "$out/recovery-$phase.log") == 1 ]]
    if rg -q 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=Process crashed' "$out/recovery-$phase.log"; then exit 1; fi
  done
fi
mkdir -p "$out/failures"
for name in $("$adb" -s "$serial" shell run-as com.buildai.app.debug ls cache 2>/dev/null | tr -d '\r' | rg '^(workflow|revision)-[A-Za-z0-9_-]+\.(png|txt)$' || true); do
  "$adb" -s "$serial" exec-out run-as com.buildai.app.debug cat "cache/$name" > "$out/failures/$name"
done
for name in $("$adb" -s "$serial" shell run-as com.buildai.app.debug ls cache 2>/dev/null | tr -d '\r' | rg '^native-builder-real-[A-Za-z0-9_-]+\.json$' || true); do
  "$adb" -s "$serial" exec-out run-as com.buildai.app.debug cat "cache/$name" > "$out/$name"
done
"$adb" -s "$serial" shell getprop ro.build.version.sdk > "$out/api.txt"
"$adb" -s "$serial" shell dumpsys activity lastanr > "$out/last-anr.txt"
"$adb" -s "$serial" exec-out screencap -p > "$out/screen.png"
rg -q 'OK \([0-9]+ tests?\)' "$out/tests.log"
if rg -q 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=Process crashed' "$out/tests.log"; then exit 1; fi
