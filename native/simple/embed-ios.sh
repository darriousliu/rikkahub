#!/bin/sh
set -eu

case "$PLATFORM_NAME" in
    iphonesimulator) simple_target=IosSimulatorArm64 ;;
    iphoneos) simple_target=IosArm64 ;;
    *) exit 0 ;;
esac

simple_source="$SRCROOT/../composeApp/build/simple/$simple_target/install/simple.framework"
simple_destination="$TARGET_BUILD_DIR/$FRAMEWORKS_FOLDER_PATH"
mkdir -p "$simple_destination"
rsync -a --delete "$simple_source/" "$simple_destination/simple.framework/"
if [ "${CODE_SIGNING_ALLOWED:-NO}" = YES ] && [ -n "${EXPANDED_CODE_SIGN_IDENTITY:-}" ]; then
    /usr/bin/codesign --force --sign "$EXPANDED_CODE_SIGN_IDENTITY" "$simple_destination/simple.framework"
fi
