#!/usr/bin/env bash
# Downloads the native SDKs and model that bulltrack-core expects. Everything is pinned
# and checked against a SHA-256 before it is unpacked. Uses the same OpenCV/ncnn
# versions as BullTrack's scripts/fetch-native-deps.js plus the model-v1 release.
#
# Usage: scripts/fetch-bulltrack-deps.sh [--force]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/third_party/BullTrack/bulltrack-core"
CPP="$CORE/src/main/cpp"
ASSETS="$CORE/src/main/assets/ncnn"
CACHE="$ROOT/build/bulltrack-deps"
FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

OPENCV_URL="https://github.com/opencv/opencv/releases/download/4.12.0/opencv-4.12.0-android-sdk.zip"
OPENCV_SHA="fd7f2332331b4eb8b67e55137281cfb16823c9399d90deb9cfa3476783b99e35"
NCNN_URL="https://github.com/Tencent/ncnn/releases/download/20260113/ncnn-20260113-android-vulkan.zip"
NCNN_SHA="41944630a7b305b190e7caea4b405bc6d103c6383dc4988e9e85616058e3cccd"
MODEL_BIN_URL="https://github.com/Thomasff/BullTrack/releases/download/model-v1/model.ncnn.bin"
MODEL_BIN_SHA="bc04f7cc77d19471d8885ec350335819f250f44fb05977c91dc2dbbd20ebc913"
MODEL_PARAM_URL="https://github.com/Thomasff/BullTrack/releases/download/model-v1/model.ncnn.param"
MODEL_PARAM_SHA="454a5a2c040c966149dc7785d30731a213b7ff381812509ea5f46e1f7c87de8c"

if [[ ! -d "$CORE" ]]; then
  echo "BullTrack submodule is missing. Run: git submodule update --init --depth 1" >&2
  exit 1
fi

mkdir -p "$CACHE"

download() {
  local url="$1" file="$2" sha="$3"
  if [[ -f "$file" ]] && echo "$sha  $file" | sha256sum -c --status; then
    echo "  $(basename "$file"): cached"
    return
  fi
  echo "  downloading $url"
  rm -f "$file.part"
  curl -L --fail --retry 3 --progress-bar -o "$file.part" "$url"
  echo "$sha  $file.part" | sha256sum -c -
  mv "$file.part" "$file"
}

echo "OpenCV 4.12.0"
OPENCV_ZIP="$CACHE/opencv-4.12.0-android-sdk.zip"
download "$OPENCV_URL" "$OPENCV_ZIP" "$OPENCV_SHA"
if [[ "$FORCE" == "1" || ! -f "$CPP/opencv-sdk/sdk/native/jni/OpenCVConfig.cmake" ]]; then
  rm -rf "$CACHE/opencv-extract" "$CPP/opencv-sdk"
  mkdir -p "$CACHE/opencv-extract" "$CPP/opencv-sdk"
  unzip -q "$OPENCV_ZIP" -d "$CACHE/opencv-extract"
  mv "$CACHE/opencv-extract/OpenCV-android-sdk/sdk" "$CPP/opencv-sdk/sdk"
  rm -rf "$CACHE/opencv-extract"
  echo "  installed -> ${CPP#"$ROOT/"}/opencv-sdk/sdk"
else
  echo "  already installed"
fi

echo "ncnn 20260113 (android-vulkan)"
NCNN_ZIP="$CACHE/ncnn-20260113-android-vulkan.zip"
download "$NCNN_URL" "$NCNN_ZIP" "$NCNN_SHA"
if [[ "$FORCE" == "1" || ! -f "$CPP/third_party/ncnn-20260113-android-vulkan/arm64-v8a/lib/cmake/ncnn/ncnnConfig.cmake" ]]; then
  rm -rf "$CACHE/ncnn-extract" "$CPP/third_party/ncnn-20260113-android-vulkan"
  mkdir -p "$CACHE/ncnn-extract" "$CPP/third_party"
  unzip -q "$NCNN_ZIP" -d "$CACHE/ncnn-extract"
  mv "$CACHE/ncnn-extract/ncnn-20260113-android-vulkan" "$CPP/third_party/"
  rm -rf "$CACHE/ncnn-extract"
  echo "  installed -> ${CPP#"$ROOT/"}/third_party/ncnn-20260113-android-vulkan"
else
  echo "  already installed"
fi

echo "BullTrack model-v1 (YOLO11s, ncnn)"
mkdir -p "$ASSETS"
download "$MODEL_BIN_URL" "$CACHE/model.ncnn.bin" "$MODEL_BIN_SHA"
download "$MODEL_PARAM_URL" "$CACHE/model.ncnn.param" "$MODEL_PARAM_SHA"
if [[ "$FORCE" == "1" || ! -f "$ASSETS/model.ncnn.bin" || ! -f "$ASSETS/model.ncnn.param" ]]; then
  cp "$CACHE/model.ncnn.bin" "$CACHE/model.ncnn.param" "$ASSETS/"
  echo "  installed -> ${ASSETS#"$ROOT/"}/"
else
  echo "  already installed"
fi

echo "BullTrack native dependencies are ready."
