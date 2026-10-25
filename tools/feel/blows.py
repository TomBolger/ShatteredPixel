"""Blows in RadioTest's wfight, the phone's side against the watch's (BLOWS=1 prints each blow; see tools/feel/feel.sh for
the replay): blows.py radiotest.txt replay-log.txt [frames.txt]   (the log: LOG=1 replay trace.txt 2> log)
For each Select: did the watch swing at the press (predict: swing), did the phone's hero swing; a "miss" is a swing on the
watch the phone never made. With frames.txt (the replay's output): attack animations drawn a press (2+: one played twice)."""
import sys, re
ph = {}
for l in open(sys.argv[1]):
    m = re.match(r'\s+blow (\d+) at (\d+) \(pressed (\d+)\): (swung|NO SWING) \((.*)\); after: (.*)', l)
    if m: ph[int(m.group(3))] = (m.group(4) == 'swung', m.group(5), m.group(6))
sw = []
for l in open(sys.argv[2]):
    m = re.match(r'\s*([\d.]+) predict: swing(.*)', l)
    if not m: continue
    if not m.group(2).strip(): sw.append([float(m.group(1)), None])
    elif sw and sw[-1][1] is None: sw[-1][1] = m.group(2).strip().split(',')[0]
pred = {}
for t, o in sw:
    k = min(ph, key=lambda b: abs(b - t)) if ph else None
    if k is not None and abs(k - t) < 50: pred[k] = o
miss = [b for b in ph if b in pred and not ph[b][0]]
print('blows %d: watch swung %d, phone swung %d | misses (watch swung, phone not) %d | phone swung unforeseen %d | watch: %s' % (
    len(ph), len(pred), sum(1 for v in ph.values() if v[0]), len(miss), sum(1 for b in ph if b not in pred and ph[b][0]),
    {o: list(pred.values()).count(o) for o in set(pred.values())}))
for b in miss: print('  MISS pressed %d: %s; after %s' % (b, ph[b][1], ph[b][2]))
if len(sys.argv) > 3:
    H, B = [], []
    for l in open(sys.argv[3]):
        a = l.split()
        if a and a[0] == 'H': H.append((float(a[1]), int(a[2]), int(a[3]), int(a[4]), int(a[5])))
        elif a and a[0] == 'B': B.append(float(a[1]))
    atk = {h[1] for h in H if h[3] == 4 and h[4] == 0}   # (HeroSprite.attack: 4 frames, not looped)
    starts, last = [], None
    for t, an, fr, n, lp in H:
        if an in atk and (last is None or last[0] != an or fr < last[1]): starts.append(t)
        last = (an, fr)
    per = [sum(1 for s in starts if b <= s < (B[i + 1] if i + 1 < len(B) else 1e18)) for i, b in enumerate(B)]
    print('presses %d, swings drawn %d; a press with 2+: %d' % (len(B), len(starts), sum(1 for p in per if p > 1)))
