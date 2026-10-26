#!/usr/bin/env python3
"""survival.py report.txt... : survival per floor, the share of games that reached each depth (max depth >= d), one
row per run; for head-start runs (STARTDEPTH) the floors cleared from the start too; the regions' ends (where games
stopped: died / timeout / loop) by region. Rows: run name (the file's name)."""
import re, sys, os, collections
for p in sys.argv[1:]:
    g = []
    for line in open(p, errors="replace"):
        m = re.match(r"RESULT class (\w+) seed (\d+): (.*?) depth (\d+) \(max (\d+)\)", line)
        if m: g.append((int(m[5]), m[3].split(" (")[0].split(" at ")[0][:14]))
    if not g: continue
    n = len(g); lo = min(d for d, _ in g); hi = max(d for d, _ in g)
    hm = re.search(r'head(\d+)', os.path.basename(p)); start = int(hm[1]) if hm else 1
    curve = " ".join(f"{d}:{sum(1 for x, _ in g if x >= d) / n:.2f}" for d in range(start, hi + 1))
    print(f"{os.path.basename(p)[:-4]:14} n {n:4d} mean {sum(d for d, _ in g) / n:5.2f} cleared {sum(d - start for d, _ in g) / n:5.2f} | {curve}")
    ends = collections.Counter(((d - 1) // 5 + 1, e) for d, e in g)
    print("   ends by region:", ", ".join(f"R{r} {e} {c}" for (r, e), c in sorted(ends.items())))
