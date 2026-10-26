#!/usr/bin/env python3
"""fightcal.py report.txt... : FightModel's calibration from FIGHTCAL lines (AssistBot FIGHTCAL=1). Fights grouped by
the predicted P(death): predicted against real death rates; by predicted mean loss (as a share of health): predicted
against real loss. Fights where a Healing potion was drunk are counted apart (the model's prediction assumed none)."""
import re, sys

R = re.compile(r"FIGHTCAL depth (\d+) hp0 (\d+) pdeath ([\d.]+) loss ([\d.]+) p90 (\d+) \| real loss (-?\d+) died (\d) healed (\d) mobs (.*)")
rows = []
for p in sys.argv[1:]:
    for line in open(p, errors="replace"):
        m = R.search(line)
        if m:
            d, hp0, pd, pl, p90, rl, died, healed, mobs = m.groups()
            rows.append(dict(d=int(d), hp0=int(hp0), pd=float(pd), pl=float(pl), rl=int(rl), died=int(died), healed=int(healed), mobs=mobs))

def table(rs, title):
    print(f"== {title}: {len(rs)} fights, {sum(r['died'] for r in rs)} deaths")
    bins = [0, 0.005, 0.02, 0.05, 0.1, 0.2, 0.35, 0.5, 0.75, 1.01]
    print(f"  {'P(death) bin':>14} {'n':>6} {'pred':>7} {'real':>7}   {'pred loss':>9} {'real loss':>9}")
    for a, b in zip(bins, bins[1:]):
        g = [r for r in rs if a <= r["pd"] < b]
        if not g: continue
        n = len(g)
        print(f"  {a:6.3f}-{b:<6.3f} {n:6d} {sum(r['pd'] for r in g)/n:7.3f} {sum(r['died'] for r in g)/n:7.3f}   {sum(r['pl'] for r in g)/n:9.1f} {sum(r['rl'] for r in g)/n:9.1f}")
    lb = [0, 0.1, 0.25, 0.5, 0.75, 1.0, 10]
    print(f"  {'loss/hp bin':>14} {'n':>6} {'pred':>7} {'real':>7}  (as a share of hp0)")
    for a, b in zip(lb, lb[1:]):
        g = [r for r in rs if r["hp0"] > 0 and a <= r["pl"] / r["hp0"] < b]
        if not g: continue
        n = len(g)
        print(f"  {a:6.2f}-{b:<6.2f} {n:6d} {sum(r['pl']/r['hp0'] for r in g)/n:7.3f} {sum(min(r['rl'], r['hp0'])/r['hp0'] for r in g)/n:7.3f}")
    if rs:
        # Brier score of the death prediction and of the base rate
        base = sum(r["died"] for r in rs) / len(rs)
        bs = sum((r["pd"] - r["died"]) ** 2 for r in rs) / len(rs); b0 = sum((base - r["died"]) ** 2 for r in rs) / len(rs)
        print(f"  Brier {bs:.4f} (base rate {base:.3f}: {b0:.4f}); skill {1 - bs / b0 if b0 else 0:.2f}")

table([r for r in rows if not r["healed"]], "no potion drunk")
table([r for r in rows if r["healed"]], "a Healing drunk in the fight")
