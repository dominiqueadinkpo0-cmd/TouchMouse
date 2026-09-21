#!/usr/bin/env bash
set -e
echo "=== TouchMouse Build ==="
if ! command -v java >/dev/null 2>&1; then
  echo "Java non trouvé. Installe openjdk-17 : pkg install openjdk-17  ou  sudo apt install openjdk-17-jdk"
  exit 1
fi
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  # tente de deviner
  if [ -d "$HOME/Android/Sdk" ]; then export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "/opt/android-sdk" ]; then export ANDROID_HOME="/opt/android-sdk"
  elif [ -d "/sdcard/Android/Sdk" ]; then export ANDROID_HOME="/sdcard/Android/Sdk"
  fi
fi
echo "ANDROID_HOME=$ANDROID_HOME"
if [ ! -d "$ANDROID_HOME/cmdline-tools" ]; then
  echo "SDK non trouvé, téléchargement..."
  mkdir -p /tmp/sdk && cd /tmp/sdk
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip
  unzip -q tools.zip
  mkdir -p "$HOME/Android/Sdk/cmdline-tools"
  rm -rf "$HOME/Android/Sdk/cmdline-tools/latest"
  mv cmdline-tools "$HOME/Android/Sdk/cmdline-tools/latest"
  export ANDROID_HOME="$HOME/Android/Sdk"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  yes | sdkmanager --licenses || true
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
fi
export ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$(dirname "$0")"
# wrapper si pas présent
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Téléchargement Gradle wrapper jar..."
  mkdir -p gradle/wrapper
  wget -q https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar -O gradle/wrapper/gradle-wrapper.jar || echo "wrapper jar à récupérer via gradle wrapper"
fi
chmod +x gradlew 2>/dev/null || true

if [ -f "./gradlew" ] && [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  ./gradlew assembleDebug
else
  if command -v gradle >/dev/null 2>&1; then
    gradle assembleDebug
  else
    echo "Installe gradle ou récupère le wrapper jar"
    exit 1
  fi
fi

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "✅ APK généré: $APK"
  ls -lh "$APK"
  cp "$APK" /sdcard/TouchMouse-debug.apk 2>/dev/null && echo "Copié vers /sdcard/TouchMouse-debug.apk" || true
  echo "Installe avec: adb install -r $APK  ou ouvre le fichier sur le téléphone"
else
  echo "❌ APK non trouvé, vérifie logs"
  exit 1
fi
