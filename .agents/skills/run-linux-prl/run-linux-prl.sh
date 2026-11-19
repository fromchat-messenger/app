#!/usr/bin/env bash
set -euo pipefail

NO_LAUNCH=0
PRL_CLEAN=0
for arg in "$@"; do
  case "$arg" in
    --no-launch) NO_LAUNCH=1 ;;
    --clean) PRL_CLEAN=1 ;;
    -h|--help)
      echo "Usage: FROMCHAT_PRL_SUDO_PASSWORD=… $0 [--no-launch] [--clean]"
      echo "  --clean  rm app/desktop/build and --rerun-tasks (slow; default is incremental)"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (only --no-launch is supported)" >&2
      exit 1
      ;;
  esac
done

[[ "$(uname -s)" == "Darwin" ]] || { echo "macOS + Parallels only." >&2; exit 1; }
command -v prlctl >/dev/null 2>&1 || { echo "prlctl not found." >&2; exit 1; }
[[ -n "${FROMCHAT_PRL_SUDO_PASSWORD:-}" ]] || {
  echo "Set FROMCHAT_PRL_SUDO_PASSWORD." >&2
  exit 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${REPO_ROOT}"

VM_NAME="${FROMCHAT_PRL_VM:-Ubuntu ARM64}"
GATEWAY="${FROMCHAT_PRL_GATEWAY:-10.211.55.2}"
HTTP_PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()')

GIT_SHA="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
DIRTY_HASH="$(git -C "${REPO_ROOT}" status --porcelain 2>/dev/null | shasum -a 256 | cut -c1-12)"
GITHUB_REPO="$(git -C "${REPO_ROOT}" remote get-url origin 2>/dev/null | sed -E 's#.*github.com[:/]([^/]+)/([^/.]+)(\.git)?$#\1/\2#')" || true
GITHUB_REPO="${GITHUB_REPO:-fromchat-messenger/app}"
VERSION_NAME="$(sed -n 's/.*getOrElse("\([^"]*\)").*/\1/p' "${REPO_ROOT}/build.gradle.kts" | head -1)"
VERSION_NAME="${VERSION_NAME:-1.3.0}"
SUDO_B64=$(printf '%s' "${FROMCHAT_PRL_SUDO_PASSWORD}" | base64 | tr -d '\n')

prlctl list -a | grep -Fq "${VM_NAME}" || { echo "VM not found: ${VM_NAME}" >&2; exit 1; }

CACHE_DIR="${REPO_ROOT}/.cache/run-linux-prl"
CACHE_TAR="${CACHE_DIR}/${GIT_SHA}-${DIRTY_HASH}.tar"
STAGING="$(mktemp -d)"
cleanup() { [[ -n "${HTTP_PID:-}" ]] && kill "${HTTP_PID}" 2>/dev/null || true; rm -rf "${STAGING}"; }
trap cleanup EXIT

mkdir -p "${CACHE_DIR}"
# Dirty-hash alone can reuse a tarball from an earlier edit batch in the same working tree.
if [[ -f "${CACHE_TAR}" ]] && find "${REPO_ROOT}/app" "${SCRIPT_DIR}" \
    -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.java' -o -name '*.sh' \) \
    -newer "${CACHE_TAR}" -print -quit | grep -q .; then
  rm -f "${CACHE_TAR}"
fi
echo "PRL_SRC_CACHE=$(basename "${CACHE_TAR}")"
if [[ -f "${CACHE_TAR}" ]]; then
  cp -f "${CACHE_TAR}" "${STAGING}/src.tar"
else
  COPYFILE_DISABLE=1 tar -cf "${CACHE_TAR}" \
    --exclude='.git' --exclude='build' --exclude='.gradle' --exclude='.kotlin' \
    --exclude='.idea' --exclude='dmg-background' --exclude='._*' --exclude='.cache' \
    -C "${REPO_ROOT}" .
  cp -f "${CACHE_TAR}" "${STAGING}/src.tar"
fi

sed \
  -e "s|__GATEWAY__|${GATEWAY}|g" \
  -e "s|__HTTP_PORT__|${HTTP_PORT}|g" \
  -e "s|__GIT_SHA__|${GIT_SHA}|g" \
  -e "s|__GITHUB_REPO__|${GITHUB_REPO}|g" \
  -e "s|__VERSION_NAME__|${VERSION_NAME}|g" \
  -e "s|__NO_LAUNCH__|${NO_LAUNCH}|g" \
  -e "s|__PRL_CLEAN__|${PRL_CLEAN}|g" \
  -e "s|__SUDO_B64__|${SUDO_B64}|g" \
  <"${SCRIPT_DIR}/vm/orchestrate.sh" >"${STAGING}/orchestrate.sh"
chmod 755 "${STAGING}/orchestrate.sh"

python3 -m http.server "${HTTP_PORT}" --bind 0.0.0.0 --directory "${STAGING}" >/dev/null 2>&1 &
HTTP_PID=$!
sleep 0.3

prl_vm() { prlctl exec "${VM_NAME}" --current-user "$@"; }

prl_vm mkdir -p /home/parallels/tools
for guest_sh in launch-only.sh menu-launch-test.sh focus-fromchat.sh fix-usr-share-desktop.sh read-scale-log.sh; do
  cp -f "${SCRIPT_DIR}/vm/${guest_sh}" "${STAGING}/${guest_sh}"
  chmod 755 "${STAGING}/${guest_sh}"
done

prl_vm wget -q -O /home/parallels/tools/orchestrate.sh "http://${GATEWAY}:${HTTP_PORT}/orchestrate.sh"
for guest_sh in launch-only.sh menu-launch-test.sh focus-fromchat.sh fix-usr-share-desktop.sh read-scale-log.sh; do
  prl_vm wget -q -O "/home/parallels/tools/${guest_sh}" "http://${GATEWAY}:${HTTP_PORT}/${guest_sh}"
  prl_vm chmod +x "/home/parallels/tools/${guest_sh}"
done
prl_vm /home/parallels/tools/orchestrate.sh