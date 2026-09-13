#!/usr/bin/env bash
set -euo pipefail

./gradlew \
  :app:shared:compileKotlinJvm \
  :utils:shared:compileKotlinJvm \
  :app:desktop:compileKotlin \
  :app:shared:compileAndroidMain \
  :app:android:compileReleaseKotlin \
  --no-daemon \
  --console=plain
