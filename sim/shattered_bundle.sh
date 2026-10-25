#!/bin/bash
# shattered_bundle.sh NAME: packs the Shattered PD bot (build/spd + build/server classes, lib jars, English text and
# the art the headless game loads; no music) into sim/out/NAME-<stamp>.tgz for the PebxelSim runner (sim/runner.sh).
# Run ./build-server.sh first. The bundle's program is ./simbot (see sim/simbot). Submit it with the PixelDungeon- repo's
# sim/submit.sh (the Monolith runner and its job branches live there).
set -e
cd "$(dirname "$0")/.."
V=$1-$(date +%m%d%H%M%S); OUT=sim/out; mkdir -p "$OUT"
T=$(mktemp -d)
jar cf "$T/game.jar" -C build/spd . -C build/server .
mkdir -p "$T/lib" "$T/assets" "$T/common"; cp lib/*.jar "$T/lib/"
(cd spd/core/src/main/assets && find . -path ./music -prune -o -type f -print | grep -vE '_[a-z]{2}(_[A-Z]{2})?\.properties$' > "$T/files.txt"; tar cf - -T "$T/files.txt" | tar xf - -C "$T/assets"; rm "$T/files.txt")
cp -r common/assets/. "$T/common/"
cp sim/simbot "$T/simbot"; chmod +x "$T/simbot"; touch "$T/needs-java"
tar czf "$OUT/$V.tgz" -C "$T" .; rm -rf "$T"
echo "$OUT/$V.tgz $(du -h "$OUT/$V.tgz" | cut -f1)"
