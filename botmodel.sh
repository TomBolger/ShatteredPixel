#!/bin/bash
# The bot's decision models (common/src/pebxel/bot) checked against the game: BotModelTest. Arg: duels per set-up.
cd "$(dirname "$0")"
D=$PWD
cd spd/core/src/main/assets && java -Xmx800m -XX:+UseSerialGC -Dpebxel.common="$D/common/assets/" -cp "$D/build/spd:$D/build/server:$D/lib/*" pebxel.BotModelTest "$@" 2>&1 | grep -v '^Picked up JAVA_TOOL\|^\[GAME\]'
r=${PIPESTATUS[0]}; rm -rf .pebxel-modeltest-*; exit $r
