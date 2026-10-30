#!/usr/bin/env bash
set -euo pipefail

if ! command -v create-dmg >/dev/null 2>&1; then
  brew install create-dmg
fi

DMG_DIR="app/desktop/dmg-background"
if [[ ! -f "$DMG_DIR/node_modules/playwright/package.json" ]]; then
  npm --prefix "$DMG_DIR" install --no-fund --no-audit
  npm --prefix "$DMG_DIR" exec playwright install chromium
fi
