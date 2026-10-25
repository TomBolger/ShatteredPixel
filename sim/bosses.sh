#!/bin/bash
# sim/bosses.sh TAG N [ENV=v...]: the boss check (agent K3). N games on each boss's floor (HEAD=1 STARTDEPTH=5/10/15/20/25,
# MILD=1, TALENTS=human; MAXDEPTH = the boss's depth, so "depth limit" = the boss beaten), two JVMs at a time, from a
# snapshot of build/server (rebuilding meanwhile is safe). Results: sim/out/bosses/TAG-dD.txt and TAG-sum.txt, e.g.
#   d10: 21/24 past;  21 : depth limit  3 : died depth
# Compare with the skills off: AS_BOSSES=0 AS_BLOOPS=0 AS_BFOOD=0 AS_QUESTS=0 AS_ALCHEMY=0.
set -e
T=$1; N=$2; shift 2
H=$(cd "$(dirname "$0")/.." && pwd); O=$H/sim/out/bosses; mkdir -p $O
rm -rf $O/b-$T; cp -r $H/build/server $O/b-$T
run() { local d=$1; shift; (cd $H/spd/core/src/main/assets && env SIMSEED=1 HEAD=1 STARTDEPTH=$d MILD=1 TALENTS=human SEEDBASE=3000 MAXDEPTH=$d "$@" timeout 3000 java -Xmx600m -XX:+UseSerialGC -XX:+UnlockExperimentalVMOptions -XX:hashCode=3 -XX:TieredStopAtLevel=1 -cp "$O/b-$T:$H/build/spd:$H/lib/*" pebxel.AssistBot --games 0 $N 2>&1 | grep -v JAVA_TOOL > $O/$T-d$d.txt); }
for d in 5 10 15 20 25; do run $d "$@" & [ $d = 10 ] || [ $d = 20 ] || [ $d = 25 ] && wait; done; wait
for d in 5 10 15 20 25; do f=$O/$T-d$d.txt; echo "d$d: $(grep -c '^RESULT.*depth limit' $f)/$(grep -c '^RESULT' $f) past; $(grep '^RESULT' $f | grep -o ': [a-z]* [a-z(]*' | sort | uniq -c | tr '\n' ' ')"; done | tee $O/$T-sum.txt
