#!/usr/bin/env bash
set -e
TOOLS="${TOOLS:-/home/user/.tools}"
mkdir -p "$TOOLS"
if [ ! -x "$TOOLS/jdk/bin/java" ]; then
  cd "$TOOLS"
  curl -sSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf jdk.tar.gz && mv jdk-17* jdk && rm jdk.tar.gz
fi
if [ ! -d "$TOOLS/sdk/platforms/android-35" ]; then
  mkdir -p "$TOOLS/sdk" && cd "$TOOLS/sdk"
  curl -sSL -o c.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q c.zip && mkdir -p cmdline-tools/latest && mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
  rm -f c.zip
  export JAVA_HOME="$TOOLS/jdk"
  yes | "$TOOLS/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$TOOLS/sdk" --licenses >/dev/null 2>&1 || true
  "$TOOLS/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$TOOLS/sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1
fi
cd /home/user/Masarify
printf 'sdk.dir=%s/sdk\n' "$TOOLS" > local.properties
echo "setup done"
