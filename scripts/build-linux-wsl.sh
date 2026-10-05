#!/usr/bin/env bash
set -euo pipefail

# Gradle 9 daemon needs JDK 21+ (see gradle/gradle-daemon-jvm.properties).
WIN_USER="${WIN_USER:-$(cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r')}"
WIN_JDK="/mnt/c/Users/${WIN_USER}/tools/jdk21-linux"

if [ -x "$WIN_JDK/bin/java" ]; then
  GRADLE_JDK="$WIN_JDK"
elif [ -x "$HOME/tools/jdk21/bin/java" ]; then
  GRADLE_JDK="$HOME/tools/jdk21"
else
  GRADLE_JDK="${JAVA_HOME:-$HOME/tools/jdk17}"
fi

# jpackage for .deb / AppImage (Linux JDK with bin/jpackage).
if [ -x "$HOME/tools/jdk17/bin/jpackage" ]; then
  PACKAGING_JDK="$HOME/tools/jdk17"
elif [ -x "$GRADLE_JDK/bin/jpackage" ]; then
  PACKAGING_JDK="$GRADLE_JDK"
else
  PACKAGING_JDK="$GRADLE_JDK"
fi

export JAVA_HOME="$GRADLE_JDK"
export GRADLE_JAVA_HOME="$GRADLE_JDK"
export PATH="$JAVA_HOME/bin:$PATH"
export FROMCHAT_PACKAGING_JDK="$PACKAGING_JDK"
export APPIMAGETOOL="${APPIMAGETOOL:-$HOME/tools/appimagetool.AppImage}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "JAVA_HOME=$JAVA_HOME"
echo "FROMCHAT_PACKAGING_JDK=$FROMCHAT_PACKAGING_JDK"
java -version

# gradle-daemon-jvm.properties pins JetBrains 21 via foojay; WSL often cannot reach it.
DAEMON_JVM_PROPS="$ROOT/gradle/gradle-daemon-jvm.properties"
DAEMON_JVM_BACKUP=""
if [ -f "$DAEMON_JVM_PROPS" ]; then
  DAEMON_JVM_BACKUP="$(mktemp)"
  cp "$DAEMON_JVM_PROPS" "$DAEMON_JVM_BACKUP"
  rm -f "$DAEMON_JVM_PROPS"
fi
restore_daemon_jvm_props() {
  if [ -n "$DAEMON_JVM_BACKUP" ] && [ -f "$DAEMON_JVM_BACKUP" ]; then
    mv -f "$DAEMON_JVM_BACKUP" "$DAEMON_JVM_PROPS"
  fi
}
trap restore_daemon_jvm_props EXIT

LOG="$ROOT/build-linux-wsl.log"
TASKS="${GRADLE_TASKS:-:app:desktop:packageReleaseDeb :app:desktop:packageLinuxAppImage}"
./gradlew $TASKS --no-daemon "$@" 2>&1 | tee "$LOG"
restore_daemon_jvm_props
trap - EXIT

echo ""
echo "Artifacts:"
find app/desktop/build/distributions app/desktop/build/compose/binaries -type f \( -name '*.deb' -o -name '*.AppImage' -o -name '*.rpm' \) -exec ls -lah {} \;
