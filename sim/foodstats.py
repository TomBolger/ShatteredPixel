#!/usr/bin/env python3
"""foodstats.py report.txt... : where the turns and the food went (FoodStats' FLOOR lines), and what the deaths were
(DEATH lines): turns per floor by activity, food found / eaten / left behind per floor, starving games against the
rest, and the deaths by depth band with the foes, the health when the fight began and what was still held."""
import re, sys, collections

CATS = [("hold", r"^(hold|doorway  (hold|let)|ambush  wait|piranha  (hurt  )?wait|stay|let it)"),
        ("quest", r"^(quest|ghost|blacksmith|mine|mining|wandmaker|imp|the ghost|dust)"),
        ("rest", r"^(hurt  rest|rest|hurt  dew|wait  rest|low  rest)"),
        ("search", r"^(search|dead end|search spot|search here)"),
        ("fight", r"^(fight|enemy|zap|throw|tactic|stalled|ambush|step back|the crab|away from the crab|off the crab|piranha|spawner|boss|sleeper|sacrificial|kite|ab  )"),
        ("escape", r"^(escape|low|dying|losing|burning|flee|run|out of)"),
        ("items", r"^(drink|read|upgrade|identify|art|eat|starving|hungry|better gear|wear|equip|alchemy|plant|sungrass|to water|transmute|enchant)"),
        ("stairs", r"^(stairs|short of food|up$|stairs down)"),
        ("explore", r"^(explore|unexplored|resume|pick up|unlock|look|grass|talk|your choice|buy|sell|over the trap|the key)"),
        ]
def cat(w):
    for c, rx in CATS:
        if re.match(rx, w): return c
    return "other"

def games(path):
    out, cur = [], None
    for line in open(path, errors="replace"):
        if line.startswith("== game "):
            m = re.match(r"== game (\d+) class (\d+) seed (\d+)", line)
            if m: cur = {"seed": int(m[3]), "floors": [], "death": None, "end": "?", "max": 0}; out.append(cur)
            continue
        if cur is None: continue
        if line.startswith("FLOOR "):
            head, _, rest = line.partition(" |")
            t = head.split()
            f = {"d": t[1]}
            for i in range(2, len(t) - 1, 2):
                if t[i] in ("left",): f[t[i]] = t[i + 1]
                else:
                    try: f[t[i]] = int(t[i + 1])
                    except ValueError: pass
            f["why"] = collections.Counter()
            for kv in rest.strip().split(","):
                kv = kv.strip()
                if ":" in kv: k, v = kv.rsplit(":", 1); f["why"][k.strip()] += int(v)
            cur["floors"].append(f)
        elif line.startswith("DEATH "): cur["death"] = line.strip()
        elif line.startswith("RESULT"):
            m = re.search(r": (.*?) depth (\d+) \(max (\d+)\)", line)
            if m: cur["end"], cur["max"] = m[1], int(m[3])
    return out

def summary(path):
    gs = games(path)
    if not gs: return
    starved = [g for g in gs if g["death"] and "starv" in g["death"].split(" killer ")[1].split()[0].lower()]
    starvingAtDeath = [g for g in gs if g["death"] and " starving true" in g["death"]]
    print(f"== {path}: {len(gs)} games, mean max {sum(g['max'] for g in gs)/len(gs):.2f}; starved to death {len(starved)}, died while starving {len(starvingAtDeath)}")
    allf = [f for g in gs for f in g["floors"] if f.get("turns", 0) > 0]
    def table(fl, label):
        if not fl: return
        n = len(fl); c = collections.Counter()
        for f in fl:
            for w, v in f["why"].items(): c[cat(w)] += v
        tt = sum(f["turns"] for f in fl)
        print(f"  {label}: {n} floors, turns/floor {tt/n:.0f} (locked {sum(f['locked'] for f in fl)/n:.0f}), starving {sum(f['starve'] for f in fl)/n:.0f} (with food {sum(f.get('starveFed',0) for f in fl)/n:.0f}), "
              f"food found {sum(f['found'] for f in fl)/n:.0f} eaten {sum(f['eaten'] for f in fl)/n:.0f}, steps {sum(f['steps'] for f in fl)/n:.0f} cells {sum(f['cells'] for f in fl)/n:.0f}")
        print("     by activity: " + ", ".join(f"{k} {v/n:.0f}" for k, v in c.most_common()))
    table(allf, "all floors")
    bands = collections.defaultdict(list)
    for f in allf:
        d = int(re.match(r"d(\d+)", f["d"])[1]); bands[(d - 1) // 5].append(f)
    for b in sorted(bands): table(bands[b], f"depth {b*5+1}-{b*5+5}")
    sf = [f for g in starved for f in g["floors"] if f.get("turns", 0) > 0]
    table(sf, "starved games' floors")
    ok = [f for g in gs if g not in starved for f in g["floors"] if f.get("turns", 0) > 0]
    table(ok, "other games' floors")
    # food left behind: seen / unseen
    seen = unseen = 0
    for f in allf:
        l = f.get("left", "0/0").split("/"); seen += int(l[0]); unseen += int(re.match(r"\d+", l[1])[0])
    print(f"  food left behind per floor: seen {seen/len(allf):.0f}, never seen {unseen/len(allf):.0f}")
    top = collections.Counter()
    for g in starved:
        for f in g["floors"]:
            for w, v in f["why"].items(): top[w] += v
    if starved: print("  starved games, top reasons (turns/game): " + ", ".join(f"{w} {v/len(starved):.0f}" for w, v in top.most_common(18)))
    # deaths
    ds = [g for g in gs if g["death"]]
    for lo, hi in ((1, 10), (11, 15), (16, 20), (21, 26)):
        b = [g for g in ds if lo <= int(re.search(r"DEATH depth (\d+)", g["death"])[1]) <= hi]
        if not b: continue
        kc = collections.Counter(); fc = collections.Counter(); hp0 = []; heal = esc = unk = 0
        for g in b:
            d = g["death"]; kc[d.split(" killer ")[1].split()[0]] += 1
            for x in re.search(r" foes ?(\S*) heal", d)[1].split(","):
                if x: fc[x] += 1
            hp0.append(int(re.search(r" hp0 (-?\d+)", d)[1]))
            heal += int(re.search(r" heal (\d+)", d)[1]) > 0; esc += int(re.search(r" esc (\d+)", d)[1]) > 0
            unk += int(re.search(r" unkP (\d+)", d)[1]) + int(re.search(r" unkS (\d+)", d)[1]) > 0
        hp0.sort()
        print(f"  deaths at {lo}-{hi}: {len(b)}; health at the fight's start median {hp0[len(hp0)//2]}%, under 60% {sum(1 for x in hp0 if 0 <= x < 60)}; held Healing {heal}, an escape {esc}, unknowns {unk}")
        print("     killers: " + ", ".join(f"{k} {v}" for k, v in kc.most_common(10)))
        print("     foes in the fight: " + ", ".join(f"{k} {v}" for k, v in fc.most_common(12)))

for p in sys.argv[1:]: summary(p)
