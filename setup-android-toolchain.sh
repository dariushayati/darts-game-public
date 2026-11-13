#!/usr/bin/env bash
#
# Installs the Android build toolchain for the Darts project.
#
#   ./setup-android-toolchain.sh           # system-wide in /opt (uses sudo)
#   ./setup-android-toolchain.sh --local   # no sudo, everything under $HOME
#
# Installs JDK 21, Android command-line tools, platform-tools,
# platforms;android-35 and build-tools;35.0.0, then accepts SDK licenses.
#
set -euo pipefail

MODE="${1:-system}"
JDK_VERSION="21"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
COMPILE_SDK="35"
BUILD_TOOLS="35.0.0"
ENV_MARKER="# >>> darts-android-toolchain >>>"

log() { printf '\n==> %s\n' "$*"; }

case "$MODE" in
  --local|local)
    MODE="local"
    ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
    JDK_HOME="$HOME/.local/opt/jdk$JDK_VERSION"
    ;;
  --system|system)
    MODE="system"
    ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
    JDK_HOME=""
    ;;
  *)
    echo "usage: $0 [--local|--system]" >&2
    exit 2
    ;;
esac

# ---------------------------------------------------------------- JDK
log "Installing JDK $JDK_VERSION ($MODE mode)"
if [ "$MODE" = "system" ]; then
  sudo apt-get update
  sudo apt-get install -y "openjdk-${JDK_VERSION}-jdk" unzip curl
else
  if [ ! -x "$JDK_HOME/bin/java" ]; then
    mkdir -p "$JDK_HOME"
    log "Downloading Temurin JDK $JDK_VERSION"
    curl -sSL -o /tmp/jdk.tar.gz \
      "https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
    tar -xzf /tmp/jdk.tar.gz -C "$JDK_HOME" --strip-components=1
  fi
  export JAVA_HOME="$JDK_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# ------------------------------------------------------- command-line tools
log "Installing Android command-line tools into $ANDROID_HOME"
if [ "$MODE" = "system" ]; then
  sudo mkdir -p "$ANDROID_HOME/cmdline-tools"
  sudo curl -sSL -o /tmp/clt.zip "$CMDLINE_TOOLS_URL"
  sudo rm -rf /tmp/clt
  sudo mkdir -p /tmp/clt
  sudo unzip -q -o /tmp/clt.zip -d /tmp/clt
  sudo rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  sudo mv /tmp/clt/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  sudo chown -R "$(id -un)" "$ANDROID_HOME"
else
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sSL -o /tmp/clt.zip "$CMDLINE_TOOLS_URL"
  rm -rf /tmp/clt
  mkdir -p /tmp/clt
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o /tmp/clt.zip -d /tmp/clt
  else
    python3 -c "import zipfile; zipfile.ZipFile('/tmp/clt.zip').extractall('/tmp/clt')"
  fi
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv /tmp/clt/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  # Python's extractall() drops the exec bit; unzip preserves it.
  chmod -R u+x "$ANDROID_HOME/cmdline-tools/latest/bin"
fi

export ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ------------------------------------------------------------ SDK packages
log "Accepting SDK licenses"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

log "Installing SDK packages"
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${BUILD_TOOLS}"

# ------------------------------------------------------------------ verify
log "Verifying installation"
"${JDK_HOME:+$JDK_HOME/bin/}java" -version 2>&1 | head -1
sdkmanager --version
adb version | head -1

# --------------------------------------------------------------- persist env
if ! grep -qF "$ENV_MARKER" "$HOME/.bashrc" 2>/dev/null; then
  {
    echo
    echo "$ENV_MARKER"
    if [ "$MODE" = "local" ]; then
      echo "export JAVA_HOME=\"$JDK_HOME\""
    fi
    echo "export ANDROID_HOME=\"$ANDROID_HOME\""
    echo "export PATH=\"${JDK_HOME:+$JDK_HOME/bin:}\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
    echo "# <<< darts-android-toolchain <<<"
  } >> "$HOME/.bashrc"
  log "Added toolchain paths to ~/.bashrc (open a new shell, or 'source ~/.bashrc')"
fi

log "Done. ANDROID_HOME=$ANDROID_HOME"
