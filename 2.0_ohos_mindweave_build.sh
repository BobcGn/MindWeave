#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_VARIANT="${1:-Debug}"
OHOS_TOOLCHAIN="${2:-${MINDWEAVE_OHOS_TOOLCHAIN:-standard}}"
JDK_21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"

if [[ -n "${JDK_21_HOME}" ]]; then
  export JAVA_HOME="${JDK_21_HOME}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

case "${BUILD_VARIANT}" in
  Debug|debug)
    TASK="publishDebugBinariesToHarmonyApp"
    ;;
  Release|release)
    TASK="publishReleaseBinariesToHarmonyApp"
    ;;
  *)
    echo "Unsupported variant: ${BUILD_VARIANT}. Use Debug or Release."
    exit 1
    ;;
esac

case "${OHOS_TOOLCHAIN}" in
  standard|kba)
    ;;
  *)
    echo "Unsupported OHOS toolchain: ${OHOS_TOOLCHAIN}. Use standard or kba."
    exit 1
    ;;
esac

if ! BUILD_OUTPUT="$("${ROOT_DIR}/gradlew" \
  -c "${ROOT_DIR}/settings.2.0.ohos.gradle.kts" \
  "-Pmindweave.ohos.toolchain=${OHOS_TOOLCHAIN}" \
  "${TASK}" 2>&1)"; then
  echo "${BUILD_OUTPUT}"
  exit 1
fi

echo "${BUILD_OUTPUT}"

MODE_FILE="${ROOT_DIR}/harmonyApp/entry/src/main/libs/arm64-v8a/mindweave_bridge_mode.txt"
BRIDGE_MODE="auto"

if [[ -f "${MODE_FILE}" ]]; then
  BRIDGE_MODE="$(tr -d '[:space:]' < "${MODE_FILE}")"
fi

echo
echo "OHOS Gradle profile finished. toolchain=${OHOS_TOOLCHAIN}, mode=${BRIDGE_MODE}"
echo "Next: open harmonyApp in DevEco Studio and build the entry HAP."
