#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_VARIANT="${1:-Debug}"

case "${BUILD_VARIANT}" in
  Debug|debug)
    TASK=":shared:linkDebugSharedOhosArm64"
    SOURCE_SO="${ROOT_DIR}/shared/build/bin/ohosArm64/debugShared/libmindweave.so"
    ;;
  Release|release)
    TASK=":shared:linkReleaseSharedOhosArm64"
    SOURCE_SO="${ROOT_DIR}/shared/build/bin/ohosArm64/releaseShared/libmindweave.so"
    ;;
  *)
    echo "Unsupported variant: ${BUILD_VARIANT}. Use Debug or Release."
    exit 1
    ;;
esac

DEST_DIR="${ROOT_DIR}/harmonyApp/entry/src/main/libs/arm64-v8a"
mkdir -p "${DEST_DIR}"

"${ROOT_DIR}/gradlew" -c "${ROOT_DIR}/settings.2.0.ohos.gradle.kts" "${TASK}"
cp "${SOURCE_SO}" "${DEST_DIR}/libmindweave.so"

echo "Synced libmindweave.so -> ${DEST_DIR}"
echo "Next: open harmonyApp in DevEco Studio and build the entry HAP."
