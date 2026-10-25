#!/usr/bin/env python3
"""sweep.py DIR [base] : a parameter sweep's runs (DIR/<run>.txt, the same seeds each) against the base run, paired by
seed: mean max depth, the change, deeper / shallower games, a sign test, per class, and how many games ended in a loop,
a timeout or a crash (the sanity check). Rows sorted by the change."""
import os, re, sys, math, collections

CLS = ["WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC"]

def load(p):
    g = {}
    for line in open(p, errors="replace"):
        if line.startswith("RESULT class"):
            m = re.match(r"RESULT class (\w+) seed (\d+): (.*?) depth (\d+) \(max (\d+)\)", line)
            if m: g[int(m[2])] = (m[1], int(m[5]), m[3])
            else:
                m = re.match(r"RESULT class (\w+) seed (\d+): (.*)", line)
                if m: g[int(m[2])] = (m[1], 0, m[3])
    return g

def sign_p(a, b):
    n = a + b
    if n == 0: return 1.0
    k = min(a, b); s = sum(math.comb(n, i) for i in range(k + 1)) / 2 ** n
    return min(1.0, 2 * s)

d = sys.argv[1]; base = sys.argv[2] if len(sys.argv) > 2 else "base"
runs = {f[:-4]: load(os.path.join(d, f)) for f in os.listdir(d) if f.endswith(".txt") and f != "progress.txt"}
B = runs[base]
def bad(g): return sum(1 for v in g.values() if not (v[2].startswith("died") or v[2].startswith("WIN") or v[2].startswith("depth limit") or v[2].startswith("turn limit")))
print(f"base {base}: {len(B)} games, mean max depth {sum(v[1] for v in B.values()) / len(B):.2f}, not died/won {bad(B)}")
print("  " + " ".join(f"{c[:4]} {sum(v[1] for v in B.values() if v[0] == c) / max(1, sum(1 for v in B.values() if v[0] == c)):.2f}" for c in CLS))
rows = []
for r, g in runs.items():
    if r == base: continue
    s = [k for k in g if k in B]
    if not s: continue
    dd = [g[k][1] - B[k][1] for k in s]
    up = sum(1 for x in dd if x > 0); dn = sum(1 for x in dd if x < 0)
    per = []
    for c in CLS:
        cs = [g[k][1] - B[k][1] for k in s if B[k][0] == c]
        per.append(f"{c[:4]} {sum(cs) / max(1, len(cs)):+.2f}")
    rows.append((sum(dd) / len(dd), r, len(s), sum(g[k][1] for k in s) / len(s), up, dn, sign_p(up, dn), bad(g), " ".join(per)))
rows.sort(reverse=True)
print(f"{'run':<12} {'n':>4} {'depth':>6} {'change':>7} {'+':>4} {'-':>4} {'p':>6} {'bad':>4}  per class")
for ch, r, n, m, up, dn, p, b, per in rows:
    print(f"{r:<12} {n:4d} {m:6.2f} {ch:+7.2f} {up:4d} {dn:4d} {p:6.3f} {b:4d}  {per}")
