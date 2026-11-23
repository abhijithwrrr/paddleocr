#!/usr/bin/env bash
# Download the prebuilt Paddle-Lite runtime, OpenCV Android SDK, PP-OCRv5
# models and dictionary used by the app. Safe to re-run; downloads are cached.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="$ROOT/.deps-cache"
ASSETS="$ROOT/app/src/main/assets/ocr"
PADDLE_DIR="$ROOT/app/PaddleLite"
OPENCV_DIR="$ROOT/app/OpenCV"

mkdir -p "$CACHE" "$ASSETS" "$PADDLE_DIR"

fetch() {
  local url="$1"
  local file="$CACHE/$(basename "$url")"
  if [ ! -f "$file" ]; then
    echo "Downloading $(basename "$url")"
    curl -L --fail --retry 3 -o "$file" "$url"
  fi
  echo "$file"
}

# Paddle-Lite 2.14rc CPU runtime (arm64-v8a + armeabi-v7a)
LITE_TAR="$(fetch https://paddlelite-demo.bj.bcebos.com/paddle-x/libs/android/paddle_lite_libs_v2_14_rc.tar.gz)"
LITE_TMP="$CACHE/paddle-lite"
rm -rf "$LITE_TMP"
mkdir -p "$LITE_TMP"
tar -xzf "$LITE_TAR" -C "$LITE_TMP"
rm -rf "$PADDLE_DIR/include" "$PADDLE_DIR/libs"
cp -R "$LITE_TMP/cxx/include" "$PADDLE_DIR/include"
cp -R "$LITE_TMP/cxx/libs" "$PADDLE_DIR/libs"

# OpenCV 4.2.0 Android SDK, matching the official Paddle-Lite OCR demo
OPENCV_TAR="$(fetch https://paddlelite-demo.bj.bcebos.com/libs/android/opencv-4.2.0-android-sdk.tar.gz)"
echo "Extracting OpenCV"
rm -rf "$OPENCV_DIR"
mkdir -p "$OPENCV_DIR"
tar -xzf "$OPENCV_TAR" -C "$OPENCV_DIR"

# PP-OCRv5 mobile detection, recognition and text-line orientation models
for model in PP-OCRv5_mobile_det PP-OCRv5_mobile_rec PP-LCNet_x0_25_textline_ori; do
  MODEL_TAR="$(fetch "https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models/${model}.tar.gz")"
  tar -xzf "$MODEL_TAR" -C "$ASSETS"
done

# Dictionary and runtime config
LABELS_TAR="$(fetch https://paddlelite-demo.bj.bcebos.com/demo/ocr/labels/labels.tar.gz)"
LABELS_TMP="$CACHE/labels"
rm -rf "$LABELS_TMP"
mkdir -p "$LABELS_TMP"
tar -xzf "$LABELS_TAR" -C "$LABELS_TMP"
cp "$LABELS_TMP/ppocr_keys_ocrv5.txt" "$ASSETS/ppocr_keys_ocrv5.txt"

CONFIG_TAR="$(fetch https://paddlelite-demo.bj.bcebos.com/demo/ocr/config.tar.gz)"
CONFIG_TMP="$CACHE/config"
rm -rf "$CONFIG_TMP"
mkdir -p "$CONFIG_TMP"
tar -xzf "$CONFIG_TAR" -C "$CONFIG_TMP"
cp "$CONFIG_TMP/config.txt" "$ASSETS/config.txt"

echo
echo "Dependencies are ready."
echo "  Paddle-Lite: $PADDLE_DIR"
echo "  OpenCV:      $OPENCV_DIR"
echo "  Models:      $ASSETS"
echo
echo "Use Android NDK 21.4.7075529 for the native build (see README)."
