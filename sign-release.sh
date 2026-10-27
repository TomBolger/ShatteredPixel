#!/bin/bash
# Signs the built phone app for release: ./sign-release.sh VERSION (after build-apk.sh and a pebble build).
# Needs the release key (kept privately by Thomas, NOT in this public repo): KS=path/to/pebxel-release.jks and
# KSPW=its password. Every release must use this same key, or phones refuse to update over the last version.
set -e
cd "$(dirname "$0")"
V=${1:?version}; : "${KS:?KS=path to pebxel-release.jks}" "${KSPW:?KSPW=keystore password}"; export KSPW
BT=$(ls -d ${ANDROID_HOME:-/opt/android-sdk}/build-tools/* | sort -V | tail -1)
mkdir -p dist/release
"$BT/zipalign" -f -p 4 dist/shattered-pebxel-debug.apk dist/release/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-key-alias pebxel --ks-pass env:KSPW --key-pass env:KSPW --out "dist/release/ShatteredPebxel-$V.apk" dist/release/aligned.apk
rm -f dist/release/aligned.apk dist/release/*.idsig
cp watch/build/watch.pbw "dist/release/ShatteredPebxel-$V.pbw"
ls -la dist/release
