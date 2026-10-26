#!/usr/bin/env python3
"""odiff.py report.txt...: where the fight model and the rules chose differently (AssistBot ODIFF=1 lines), by kind and
side, and how the game went after: died within 20 / 100 turns, died on that floor. One event per kind, side and 10 turns
(a choice repeats each plan)."""
import re, sys, collections
E = collections.defaultdict(lambda: [0, 0, 0, 0, 0.0])
for p in sys.argv[1:]:
    txt = open(p, errors="replace").read()
    for g in re.split(r"\n(?=== game \d+ class)", txt)[1:]:
        m = re.search(r"RESULT class \w+ seed \d+: (\w+) depth (\d+) \(max \d+\) turns (\d+)", g)
        if not m: continue
        died = m[1] == "died"; dd = int(m[2]); T = int(m[3]); last = {}
        for e in re.finditer(r"ODIFF t (\d+) d (\d+) hp (\d+)/(\d+) (\w+) model (\d) rule (\d) pd ([\d.]+)", g):
            t, d, hp, ht, k, mo, ru, pd = int(e[1]), int(e[2]), int(e[3]), int(e[4]), e[5], e[6], e[7], float(e[8])
            key = (k, "model yes, rules no" if mo == "1" else "rules yes, model no")
            if key in last and t - last[key] < 10: continue
            last[key] = t
            r = E[key]; r[0] += 1; r[1] += died and T - t <= 20; r[2] += died and T - t <= 100; r[3] += died and d == dd; r[4] += hp / ht
for (k, side), r in sorted(E.items()):
    n = r[0]; print(f"{k:6} {side:22} n {n:5d}  hp {r[4]/n:.2f}  died<20t {r[1]/n:.3f}  <100t {r[2]/n:.3f}  on floor {r[3]/n:.3f}")
