#!/bin/bash
set -e
cd "$(dirname "$0")"

APP_NAME="Mandala Generator"
BUNDLE_NAME="MandalaGenerator"
APP="$APP_NAME.app"

echo "🔨 Building release binary..."
swift build -c release

echo "📦 Packaging .app bundle..."
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
mkdir -p "$APP/Contents/Resources"

cp .build/release/$BUNDLE_NAME "$APP/Contents/MacOS/$BUNDLE_NAME"
cp Info.plist "$APP/Contents/Info.plist"
cp AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"

echo "✅ Done: $APP"
echo "👉 Double-click '$APP' to launch, or:"
echo "   open \"$APP\""
