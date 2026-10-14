#!/usr/bin/env bash
# Run only in an isolated development checkout. Dependencies and JDK 21 must already be available.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a complete JDK 21 installation}"
test -x "$JAVA_HOME/bin/javac"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -z "${DISPLAY:-}" ]; then
  xvfb_command="${SKYSIGNS_XVFB:-Xvfb}"
  display_file=$(mktemp)
  xvfb_log=$(mktemp)
  "$xvfb_command" -displayfd 3 -screen 0 1280x720x24 -nolisten tcp -ac \
    3>"$display_file" >"$xvfb_log" 2>&1 &
  xvfb_pid=$!
  trap 'kill "$xvfb_pid" 2>/dev/null || true; rm -f "$display_file" "$xvfb_log"' EXIT
  for attempt in {1..50}; do
    [ -s "$display_file" ] && break
    if ! kill -0 "$xvfb_pid" 2>/dev/null; then cat "$xvfb_log"; exit 1; fi
    sleep 0.1
  done
  if [ ! -s "$display_file" ]; then cat "$xvfb_log"; exit 1; fi
  export DISPLAY=":$(cat "$display_file")"
fi
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
"$JAVA_HOME/bin/java" -version
git rev-parse HEAD
bash ./gradlew runClientGameTest --no-daemon --max-workers=4 "$@"
