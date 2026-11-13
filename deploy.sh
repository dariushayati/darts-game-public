#!/usr/bin/env bash
#
# Builds the debug APK and either installs it over adb or serves it for download.
#
#   ./deploy.sh              # install over adb if attached, else serve on a free port
#   PORT=9000 ./deploy.sh    # force a port
#
set -euo pipefail

cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:assembleDebug --console=plain

APK_DIR="$(pwd)/app/build/outputs/apk/debug"
APK="$APK_DIR/app-debug.apk"

if [ ! -f "$APK" ]; then
  echo "Build succeeded but $APK is missing" >&2
  exit 1
fi

if adb get-state >/dev/null 2>&1; then
  echo "Installing $APK"
  adb install -r "$APK"
  adb shell am start -n com.dartsapp.dartytime/com.darts.app.MainActivity >/dev/null 2>&1 || true
  exit 0
fi

find_port() {
  python3 - "$1" <<'PY'
import socket, sys
for port in range(int(sys.argv[1]), int(sys.argv[1]) + 50):
    with socket.socket() as sock:
        try:
            sock.bind(("0.0.0.0", port))
        except OSError:
            continue
    print(port)
    break
else:
    sys.exit("no free port found")
PY
}

PORT="${PORT:-$(find_port 8099)}"

echo
echo "Serving $APK on port $PORT:"
for ip in $(hostname -I); do
  case "$ip" in *:*) continue ;; esac
  echo "  http://$ip:$PORT/app-debug.apk"
done
echo "Allow 'install unknown apps' for your browser, then open one of the URLs above."
echo
exec python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$APK_DIR"
