#!/usr/bin/env bash

set -euo pipefail

FRONTEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHROME_BIN="${CHROME_BIN:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
BRAND_TMP_DIR="$(mktemp -d /private/tmp/kim-math-brand.XXXXXX)"
BRAND_LOG="$BRAND_TMP_DIR/chrome.log"
BRAND_SWIFT_CACHE="$BRAND_TMP_DIR/swift-module-cache"
mkdir -p "$BRAND_SWIFT_CACHE"

render_svg() {
  local source_path="$1"
  local output_path="$2"
  local width="$3"
  local height="$4"
  local background_arg="${5:-}"

  if ! "$CHROME_BIN" \
    --headless \
    --disable-gpu \
    --hide-scrollbars \
    --force-device-scale-factor=1 \
    ${background_arg:+"$background_arg"} \
    --window-size="$width,$height" \
    --screenshot="$output_path" \
    "file://$source_path" >"$BRAND_LOG" 2>&1; then
    cat "$BRAND_LOG"
    return 1
  fi
}

resize_png() {
  local source_path="$1"
  local output_path="$2"
  local width="$3"
  local height="$4"
  sips -z "$height" "$width" "$source_path" --out "$output_path" >/dev/null
}

FEATURE_SVG="$FRONTEND_DIR/store-assets/feature-graphic-source.svg"
SPLASH_PORTRAIT_SVG="$FRONTEND_DIR/store-assets/splash-portrait-source.svg"
SPLASH_LANDSCAPE_SVG="$FRONTEND_DIR/store-assets/splash-landscape-source.svg"

APP_ICON_SOURCE="$FRONTEND_DIR/store-assets/app-icon-source.png"
GRAY_ICON_SOURCE="$FRONTEND_DIR/store-assets/app-icon-grayscale-source.png"
ROUND_ICON_SOURCE="$FRONTEND_DIR/store-assets/app-icon-round-source.png"
# Prepared 108dp layers keep the approved card artwork inside the adaptive safe area.
FOREGROUND_SOURCE="$FRONTEND_DIR/store-assets/app-icon-foreground-source.png"
MONOCHROME_SOURCE="$FRONTEND_DIR/store-assets/app-icon-monochrome-source.png"

# The approved raster is the master; do not regenerate it from the retired logo.
resize_png "$APP_ICON_SOURCE" "$FRONTEND_DIR/store-assets/app-icon-1024.png" 1024 1024
resize_png "$APP_ICON_SOURCE" "$FRONTEND_DIR/store-assets/app-icon-512.png" 512 512
cp "$FRONTEND_DIR/store-assets/app-icon-512.png" "$FRONTEND_DIR/src/assets/app-icon.png"

resize_png "$GRAY_ICON_SOURCE" "$FRONTEND_DIR/store-assets/app-icon-grayscale-1024.png" 1024 1024
resize_png "$GRAY_ICON_SOURCE" "$FRONTEND_DIR/store-assets/app-icon-grayscale-512.png" 512 512

for density_and_size in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  density="${density_and_size%%:*}"
  size="${density_and_size##*:}"
  resource_dir="$FRONTEND_DIR/android/app/src/main/res/mipmap-$density"
  resize_png "$APP_ICON_SOURCE" "$resource_dir/ic_launcher.png" "$size" "$size"
  resize_png "$ROUND_ICON_SOURCE" "$resource_dir/ic_launcher_round.png" "$size" "$size"
done

for density_and_size in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
  density="${density_and_size%%:*}"
  size="${density_and_size##*:}"
  resource_dir="$FRONTEND_DIR/android/app/src/main/res/mipmap-$density"
  resize_png "$FOREGROUND_SOURCE" "$resource_dir/ic_launcher_foreground.png" "$size" "$size"
  resize_png "$MONOCHROME_SOURCE" "$resource_dir/ic_launcher_monochrome.png" "$size" "$size"
done

render_svg "$FEATURE_SVG" "$FRONTEND_DIR/store-assets/feature-graphic-source.png" 2048 1000
resize_png "$FRONTEND_DIR/store-assets/feature-graphic-source.png" "$FRONTEND_DIR/store-assets/feature-graphic-1024x500.png" 1024 500

render_svg "$SPLASH_PORTRAIT_SVG" "$FRONTEND_DIR/store-assets/splash-portrait-source.png" 1024 1536
render_svg "$SPLASH_LANDSCAPE_SVG" "$FRONTEND_DIR/store-assets/splash-landscape-source.png" 1536 1024

resize_png "$FRONTEND_DIR/store-assets/splash-portrait-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-port-mdpi/splash.png" 320 480
resize_png "$FRONTEND_DIR/store-assets/splash-portrait-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-port-hdpi/splash.png" 480 800
resize_png "$FRONTEND_DIR/store-assets/splash-portrait-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-port-xhdpi/splash.png" 720 1280
resize_png "$FRONTEND_DIR/store-assets/splash-portrait-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-port-xxhdpi/splash.png" 960 1600
resize_png "$FRONTEND_DIR/store-assets/splash-portrait-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-port-xxxhdpi/splash.png" 1280 1920

resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable/splash.png" 480 320
resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-land-mdpi/splash.png" 480 320
resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-land-hdpi/splash.png" 800 480
resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-land-xhdpi/splash.png" 1280 720
resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-land-xxhdpi/splash.png" 1600 960
resize_png "$FRONTEND_DIR/store-assets/splash-landscape-source.png" "$FRONTEND_DIR/android/app/src/main/res/drawable-land-xxxhdpi/splash.png" 1920 1280

resize_png "$APP_ICON_SOURCE" "$FRONTEND_DIR/public/favicon-32.png" 32 32
resize_png "$APP_ICON_SOURCE" "$FRONTEND_DIR/public/favicon-16.png" 16 16
resize_png "$APP_ICON_SOURCE" "$FRONTEND_DIR/public/apple-touch-icon.png" 180 180
cp "$FRONTEND_DIR/store-assets/feature-graphic-1024x500.png" "$FRONTEND_DIR/public/og-image.png"

SWIFT_MODULE_CACHE_PATH="$BRAND_SWIFT_CACHE" \
CLANG_MODULE_CACHE_PATH="$BRAND_SWIFT_CACHE" \
/usr/bin/swift "$FRONTEND_DIR/scripts/ensure-png-alpha.swift" \
  "$FRONTEND_DIR/store-assets/app-icon-1024.png" \
  "$FRONTEND_DIR/store-assets/app-icon-512.png" \
  "$FRONTEND_DIR/store-assets/app-icon-grayscale-1024.png" \
  "$FRONTEND_DIR/store-assets/app-icon-grayscale-512.png" \
  "$FRONTEND_DIR/public/apple-touch-icon.png" \
  "$FRONTEND_DIR/public/favicon-32.png" \
  "$FRONTEND_DIR/public/favicon-16.png"
node "$FRONTEND_DIR/scripts/write-ico.mjs" \
  "$FRONTEND_DIR/public/favicon.ico" \
  "$FRONTEND_DIR/public/favicon-16.png" \
  "$FRONTEND_DIR/public/favicon-32.png"

echo "Brand assets rendered from approved raster icon and SVG illustration sources."
