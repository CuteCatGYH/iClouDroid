#!/usr/bin/env bash
set -e

# Detect JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    fi
fi

# Detect ANDROID_HOME / ANDROID_SDK_ROOT
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    fi
fi

# Detect ANDROID_NDK_HOME
if [ -z "$ANDROID_NDK_HOME" ]; then
    NDK_LATEST=$(ls -td "$ANDROID_HOME/ndk/"* 2>/dev/null | head -n 1 || true)
    if [ -n "$NDK_LATEST" ]; then
        export ANDROID_NDK_HOME="$NDK_LATEST"
    fi
fi

# Ensure Homebrew and Go bin are on PATH
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$HOME/go/bin:$PATH"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$PROJECT_ROOT/app/libs"

echo "==> Building Go core to AAR with gomobile..."
echo "    JAVA_HOME: $JAVA_HOME"
echo "    ANDROID_HOME: $ANDROID_HOME"
echo "    ANDROID_NDK_HOME: $ANDROID_NDK_HOME"

cd "$PROJECT_ROOT/go-core"
gomobile bind \
    -target=android \
    -androidapi 29 \
    -javapkg=com.getup \
    -o "$PROJECT_ROOT/app/libs/icloudcore.aar" \
    .

echo "==> Successfully generated $PROJECT_ROOT/app/libs/icloudcore.aar"
