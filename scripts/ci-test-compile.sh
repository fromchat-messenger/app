#!/usr/bin/env bash
set -euo pipefail

./gradlew \
  :app:shared:compileKotlinJvm \
  :utils:shared:compileKotlinJvm \
  :app:desktop:compileKotlin \
  --no-daemon \
  --console=plain
