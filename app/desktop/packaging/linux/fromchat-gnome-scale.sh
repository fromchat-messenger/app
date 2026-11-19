#!/bin/sh
# Mesa ARM64 only — HiDPI is Compose [LocalDensity] from monitors.xml (no GDK / uiScale pixel scaling).
APP_BIN="$(CDPATH= cd -- "$(dirname "$0")" && pwd)/FromChat"
if [ "$(uname -m)" = "aarch64" ] && [ -z "${MESA_GL_VERSION_OVERRIDE:-}" ]; then
  export MESA_GL_VERSION_OVERRIDE=3.0
fi
exec "$APP_BIN" "$@"