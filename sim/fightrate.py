#!/usr/bin/env python3
"""fightrate.py report.txt...: FightModel's damage rate against real fights (FIGHTRATE lines, AssistBot FIGHTCAL=1): over
the next 5 turns of a fight, the model's mean health lost against the health really lost, by region and by the kind of
foe beside the hero (alone), and the short-horizon death odds against deaths."""
import re, sys, collections
R = re.compile(r"FIGHTRATE depth (\d+) hp (\d+) pred ([\d.]+) pd ([\d.]+) real (-?\d+) mobs (.*)")
rows = [m.groups() for p in sys.argv[1:] for line in open(p, errors="replace") for m in [R.search(line)] if m]
rows = [(int(d), int(h), float(p), float(pd), int(r), ms) for d, h, p, pd, r, ms in rows]
def show(title, key):
    g = collections.defaultdict(list)
    for x in rows: k = key(x); g[k].append(x) if k is not None else None
    print(f"== {title}"); print(f"  {'':22} {'n':>6} {'pred':>7} {'real':>7} {'ratio':>6}")
    for k in sorted(g, key=lambda k: (-len(g[k]), str(k)))[:25]:
        v = g[k]; p = sum(x[2] for x in v) / len(v); r = sum(max(0, x[4]) for x in v) / len(v)
        print(f"  {str(k):22} {len(v):6d} {p:7.1f} {r:7.1f} {r / p if p > 0 else 0:6.2f}")
show("by region", lambda x: (x[0] - 1) // 5 + 1)
show("one foe beside, by kind", lambda x: x[5].split("@")[0] if x[5].count("@") == 1 and "@1," in x[5] else None)
bins = [0, 0.01, 0.05, 0.2, 0.5, 1.01]
print("== 5-turn death odds"); 
for a, b in zip(bins, bins[1:]):
    v = [x for x in rows if a <= x[3] < b]
    if v: print(f"  {a:.2f}-{b:.2f} n {len(v):6d} pred {sum(x[3] for x in v)/len(v):.3f} real {sum(1 for x in v if x[4] >= x[1])/len(v):.3f}")
