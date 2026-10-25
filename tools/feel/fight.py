"""Fights as the watch shows them (see feel.sh; replay.c's output, scenario wfight): for each Select at an enemy,
real ms from the press to the hero's swing shown (his animation turning to the attack one), to the first damage
number shown, and the radio bytes from the press to the next one; and the longest gap between frames drawn while
the fight goes on.  fight.py frames.txt [more...]"""
import sys

def pct(v, p):
    s = sorted(v); return s[min(len(s) - 1, int(len(s) * p))] if s else 0

def main():
    rows = []
    for path in sys.argv[1:]:
        B, H, D, O, F, S, R = [], [], [], [], [], [], []
        for line in open(path):
            a = line.split()
            if not a: continue
            k = a[0]
            if k == 'B': B.append((float(a[1]), int(a[2])))
            elif k == 'H': H.append((float(a[1]), int(a[2]), int(a[3]), int(a[4]), int(a[5]), int(a[7])))
            elif k == 'D': D.append((float(a[1]), int(a[2])))
            elif k == 'O': O.append((float(a[1]), int(a[5])))
            elif k == 'F': F.append(float(a[1]))
            elif k == 'S': S.append((float(a[1]), a[2]))
            elif k == 'R': R.append((float(a[1]), int(a[2])))
        fight = [t for t, n in S if n == 'wfight']
        if not fight: continue
        t0 = fight[0]; t1 = next((t for t, n in S if t > t0), 1e18)
        sel = [t for t, c in B if c in (3, 5) and t0 <= t < t1]   # (Select, or a tap)
        # the attack animation: the one not looped, 4 frames (HeroSprite.attack)
        atk = {h[1] for h in H if h[3] == 4 and h[4] == 0}
        for i, tb in enumerate(sel):
            nx = sel[i + 1] if i + 1 < len(sel) else t1
            sw = next((h[0] for h in H if tb <= h[0] < nx and h[1] in atk), None)
            # (the swing over: its last frame, or another animation)
            se = next((h[0] for h in H if sw is not None and sw < h[0] < nx and (h[1] not in atk or h[2] == h[3] - 1)), None)
            dm = next((t for t, n in D if tb <= t < nx), None)
            by = sum(n for t, n in O if tb <= t < nx)
            fr = [t for t in F if tb <= t < nx]
            gap = max([b - a for a, b in zip(fr, fr[1:])] or [0])
            rd = next((t for t, on in R if tb < t < nx + 0.001 and on), None)
            rows.append((sw - tb if sw is not None else None, dm - tb if dm is not None else None, by, gap, rd - tb if rd is not None else None, dm - se if dm is not None and se is not None else None))
    if not rows: print('no fight'); return
    sw = [r[0] for r in rows if r[0] is not None]; dm = [r[1] for r in rows if r[1] is not None]
    by = [r[2] for r in rows]; gp = [r[3] for r in rows]; rd = [r[4] for r in rows if r[4] is not None]; sg = [r[5] for r in rows if r[5] is not None]
    m = lambda v: sum(v) / len(v) if v else 0
    print('fight %d presses | press->swing mean %4.0f p90 %4.0f (%d never) | press->damage mean %4.0f p90 %4.0f (%d none) | swing end->damage %4.0f | press->ready again %4.0f | bytes an exchange %4.0f | frame gap max mean %3.0f worst %3.0f'
          % (len(rows), m(sw), pct(sw, .9), len(rows) - len(sw), m(dm), pct(dm, .9), len(rows) - len(dm), m(sg), m(rd), m(by), m(gp), max(gp)))
    if '-v' in sys.argv[0:1] or __import__('os').environ.get('DETAIL'):
        for r in rows: print('   ', r)

main()
