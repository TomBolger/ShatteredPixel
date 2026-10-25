/*
 * Shattered PD on the watch - answering presses at once.
 * A press otherwise shows only when the phone's frames come back, a radio round trip later. The phone sends a plan
 * for the next press (Controls.plan, C_PREDICT): where the target box goes for each target, and the steps the hero
 * would walk to it. Down moves the box at once; Select starts the hero walking at once, the map following him.
 * The phone's own frames come later: while they are behind, the predicted place is shown; once they agree, they take
 * over again. If they disagree (a monster came into view and the hero stopped, the press did something else), the
 * picture slides back to the phone's in a moment.
 * Select (or a tap) on an enemy beside the hero swings at once: his attack animation plays from the press, turned to it;
 * what the blow does (a hit or a miss, the damage, the enemy's answer) only ever comes with the phone's frames.
 */
#pragma GCC optimize ("Os")   // (the app's code, data and bss must stay under 64 KB: small beats quick here, as in scene.c)
#include "predict.h"

enum { B_UP = 1, B_DOWN, B_SELECT, B_BACK, B_LONG = 16 };   // as main.c
#define CELL 128                 // one cell in the map's units x8 (16 px tiles)
#define EASE_MS GAME_MS(120)             // sliding back after a disagreement
#define WAIT_MS GAME_MS(2667)            // no sign of the phone doing it by then (game ms): give up
#define STILL_MS GAME_MS(133)            // the phone's frames this long after his last step ended (game ms of the phone's own time,
                                 // four of its frames) show him still, short of the planned end: he stopped there

static Plan s_plan;
static bool s_wplanoff, s_wdone; // since the press: the phone's plan went (its hero set off), and came back (he is done)
// the target box, shown at a predicted place
static bool s_box; static int32_t s_bx, s_by, s_rx, s_ry; static uint32_t s_bt; static uint8_t s_bpend;   // (s_rx/ry: the phone's last place; s_bpend: presses it hasn't answered)
// the hero, walking a predicted path
static bool s_walk; static uint16_t s_wid; static int32_t s_wx0, s_wy0; static uint32_t s_wt;
static uint8_t s_wn; static uint8_t s_wdir[PLAN_S]; static uint16_t s_wstep, s_wmarker = NONE; static bool s_wmoved;
// a press while he walks stops him (Controls.button, as Pebble PD): from then on he goes at most a step past the phone's
// frames (they end where the phone stopped him, and its word of where comes ahead of them: s_endp), never on through a
// radio hold-up
static bool s_wstop;
// sliding back
static uint16_t s_eid = NONE; static int32_t s_edx, s_edy; static uint32_t s_et;

static int dxof(int d) { return d % 3 - 1; }
static int dyof(int d) { return d / 3 - 1; }
static Obj *obj(uint16_t id) { return id < MAX_ID ? g_obj[id] : NULL; }
static int32_t iabs(int32_t v) { return v < 0 ? -v : v; }

static void walk_start(Obj *h, int k, const uint8_t *dir, uint32_t now);
static bool swing_start(Obj *h, int blow, uint32_t now);
static bool s_atk;               // the hero's swing shown (swing_start)
static bool s_alate;             // given up on: the phone's swing, if it comes after all, is shown standing
static bool s_aswung;            // a swing shown at the press just made: its number next (predict_sent)
static int s_aseq;               // the press's number
static bool s_aans, s_ain; static uint32_t s_aa0;   // the phone's answer to it: heard (from that frame), playing
static uint32_t s_hat;           // the frame just played: its pictures' time
void predict_reset(void) { s_plan.on = false; s_box = s_walk = s_atk = false; s_eid = NONE; }
bool predict_active(void) { return s_box || s_walk || s_atk || s_eid != NONE; }

bool predict_hero(void) { return s_walk; }
bool predict_planned(void) { return s_plan.on; }
bool predict_walking(uint32_t now, uint32_t *since) { if (!s_walk || now - s_wt > GAME_MS(2000)) return false; *since = s_wt; return true; }
void predict_touch(void) { s_plan.on = false; }

#ifdef TRACE_WALK
static uint16_t s_thero = NONE;
#endif
// A new plan (C_PREDICT) is read straight into the one in use (with 32 steps a walk, a second copy cost 400 bytes
// of the heap): predict_plan() before, predict_set() after
static bool s_was_on; static uint16_t s_was_marker; static uint8_t s_was_n, s_was_cur;
static uint8_t s_cur_sent;       // the current target as the phone sent it (our own Downs move s_plan.cur)
static void plan_came(void);
Plan *predict_plan(void) {
  s_was_on = s_plan.on; s_was_marker = s_plan.marker; s_was_n = s_plan.n; s_was_cur = s_plan.cur;
  memset(&s_plan, 0, sizeof s_plan);
  return &s_plan;
}
void predict_set(void) { s_cur_sent = s_plan.cur; plan_came(); }
// (in a fight the plan goes while the hero acts and comes back the same: the phone says so in 2 bytes, the watch keeps it)
void predict_again(bool on) {
  s_was_on = s_plan.on; s_was_marker = s_plan.marker; s_was_n = s_plan.n; s_was_cur = s_plan.cur;
  s_plan.on = on;
  if (on) s_plan.cur = s_cur_sent;
  plan_came();
}
static void plan_came(void) {
  Plan *p = &s_plan;
#ifdef TRACE_WALK
  if (p->on) s_thero = p->hero;
#endif
  bool keep = s_box && s_was_on && p->on && p->marker == s_was_marker && p->n == s_was_n;
  if (keep) s_plan.cur = s_was_cur;   // our own presses aren't in it yet: keep counting from ours
  // While the phone's hero walks there is no plan (he isn't ready); the next one says he is ready again: the walk is
  // over, here (he arrived, or something stopped him), even if nothing else is sent after it
  if (s_walk) { if (!p->on) s_wplanoff = true; else if (s_wplanoff) s_wdone = true; }
}

// The hero walks at the phone's own pace (a step each s_wstep) from the press on, whatever the radio does: over
// real Bluetooth the phone's frames come a few at a time, a round trip apart, and the phone's hero, followed as he
// was, went in jumps of a cell or two. (His first step used to be drawn out until the phone's hero set off: every
// walk began at 40% of the pace, then sped up.) He is never shown behind the phone's hero, nor further ahead than
// the phone's frames are behind it (from the press until he set off, or the play-out's delay) and LEAD steps more,
// so if the phone stops him early (a monster came into view) he only slides back a little.
#define LEAD 512                 // steps x256
static int32_t s_p0; static uint32_t s_t0;   // shown at s_p0 (steps x256) at s_t0, going on at the walking pace
static uint32_t s_lat = GAME_MS(333);     // the usual delay before the phone's hero sets off (game ms)
static int32_t s_plag;           // how far behind the phone its frames play now (main.c, game ms)
static uint32_t s_heard;         // the newest of the phone's frames played: when it was due here (its time on the phone,
                                 // in the watch's: a backlog played at once after a radio hold-up is still from then)
static uint32_t s_heard_now;     // when the phone's last frame was played (watch time)
static void swing_tick(uint32_t now);
void predict_heard(uint32_t due, uint32_t now, uint32_t stamp, uint32_t at) {
  s_heard = due; s_heard_now = now; s_hat = at;
  if (s_aans && (int32_t)(stamp - s_aa0) >= 0) s_ain = true;
  if (s_atk) swing_tick(now);   // (frame by frame: what each one told)
}
void predict_lag(int32_t ms) { s_plag = ms < 0 ? 0 : ms; }
static int32_t raw_prog(int32_t rx, int32_t ry);
static int32_t s_endp = -1;      // the phone said its hero is ready again here (C_READY): the walk ends here
// The phone's hero is ready again at (x, y), as its message arrives (C_READY, at its head): the walk ends there. Seen
// only when its frames played (some 200 ms later) and his stop shown then, the hero walked on 3-4 cells past where a
// monster had stopped him and slid back. (The start of the walk is the last walk's end: a late word of that is no news.)
void predict_ready(int32_t x, int32_t y) {
  if (!s_walk) return;
  int32_t pr = raw_prog(x, y);
  if (pr > 0) s_endp = pr;
}

static void cell_at(int j, int32_t *x, int32_t *y) {   // the j-th cell of the path
  int32_t px = s_wx0, py = s_wy0;
  for (int k = 0; k < j && k < s_wn; k++) { px += dxof(s_wdir[k]) * CELL; py += dyof(s_wdir[k]) * CELL; }
  *x = px; *y = py;
}
static void prog_at(int32_t prog, int32_t *x, int32_t *y) {   // a place along the path (steps x256)
  if (prog < 0) prog = 0;
  if (prog > s_wn * 256) prog = s_wn * 256;
  int j = prog >> 8, f = prog & 255;
  cell_at(j, x, y);
  if (j < s_wn) { *x += dxof(s_wdir[j]) * CELL * f / 256; *y += dyof(s_wdir[j]) * CELL * f / 256; }
}
// how far along the path the phone's hero is (steps x256), -1: he's off it
static int32_t raw_prog(int32_t rx, int32_t ry) {
  int32_t best = -1, cx = s_wx0, cy = s_wy0;
  for (int j = 0; j <= s_wn; j++) {
    int32_t ex = iabs(rx - cx), ey = iabs(ry - cy), d = ex > ey ? ex : ey;
    if (j < s_wn) {
      int32_t nx = cx + dxof(s_wdir[j]) * CELL, ny = cy + dyof(s_wdir[j]) * CELL;
      bool on = rx >= (cx < nx ? cx : nx) - 8 && rx <= (cx > nx ? cx : nx) + 8 && ry >= (cy < ny ? cy : ny) - 8 && ry <= (cy > ny ? cy : ny) + 8;
      if (on) { int32_t f = d * 256 / CELL; if (f > 256) f = 256; best = j * 256 + f; }
      cx = nx; cy = ny;
    } else if (d <= 8) best = s_wn * 256;
  }
  return best;
}
static int32_t s_plast;          // where he was last shown (steps x256)
static uint32_t s_plast_t;       // ... and when
#define QUIET_MS GAME_MS(267)            // no frame of the phone's played this long (game ms): the radio is held up
static int32_t shown_prog(uint32_t now) {
  int32_t p = s_p0 + (int32_t)((now - s_t0) * 256 / s_wstep);
  Obj *h = obj(s_wid);
  // (off the path, the phone's hero holds nothing back: he went elsewhere, and predict_tick slides back from here.
  // Held back to the start of the path instead, the hero leapt back to his second step at the end of every walk
  // longer than the plan)
  int32_t rp = h ? raw_prog(obj_dyn_raw(h, 0, now), obj_dyn_raw(h, 1, now)) : -1;
  if (rp >= 0) {
    uint32_t behind = (uint32_t)s_plag > s_lat ? (uint32_t)s_plag : s_lat;
    // (until the phone's hero sets off, a step less: if the phone does something else, less to take back)
    int32_t lead = s_wstop ? LEAD / 2 : (s_wmoved ? LEAD : LEAD / 2) + (int32_t)(behind * 256 / s_wstep);   // (stopped: a step at most, to stay smooth)
    // Held back where he is (never pulled back: the allowance shrinks as the delay does), the pace going on from
    // there without a jump later. Not while the radio is held up (a lost message stops everything for 3 s): he
    // walks on as planned, rather than standing 3 s and then leaping to wherever the phone's hero had got to.
    bool quiet = !s_wstop && now - s_heard_now > QUIET_MS;
    int32_t hold = rp + lead > s_plast ? rp + lead : s_plast;
    if (p > hold && !quiet) { p = hold; s_p0 = p; s_t0 = now; }
    else if (p < rp) { p = rp; s_p0 = p; s_t0 = now; }
  }
  p = p < 0 ? 0 : p > s_wn * 256 ? s_wn * 256 : p;
  if (s_endp >= 0 && p > s_endp) {
    // past where the phone's hero stopped: back to it, at twice the pace, and there he stays
    int32_t back = s_plast - (int32_t)((now - s_plast_t) * 512 / s_wstep);
    p = back > s_endp ? (back < p ? back : p) : s_endp;
    s_p0 = p; s_t0 = now;
  }
  s_plast = p; s_plast_t = now;
  return p;
}
static void walk_at(uint32_t now, int32_t *x, int32_t *y) { prog_at(shown_prog(now), x, y); }

bool predict_button(int code, uint32_t now) {
  bool down = (code & 15) == B_DOWN, select = code == B_SELECT;
  if (s_walk) s_wstop = true;
  if (code & 32) return false;   // (main.c's B_WALKING: the phone takes it as the stop of the walk shown, Controls.button)
  if (s_alate) s_atk = false;   // (a late swing of the phone's not looked for any more)
  if (!down && !select) { s_plan.on = false; return false; }
  if (!s_plan.on || s_walk || !s_plan.n) { s_plan.on = false; return false; }
  if (down) {
    int n = s_plan.n, c = (s_plan.cur + ((code & B_LONG) ? n - 1 : 1)) % n;
    PlanTarget *t = &s_plan.t[c];
    if (t->x == 0x7FFF || !obj(s_plan.marker)) { s_plan.on = false; return false; }
    s_plan.cur = c;
    Obj *m = obj(s_plan.marker);
    if (!s_box) { s_rx = obj_dyn_raw(m, 0, now); s_ry = obj_dyn_raw(m, 1, now); s_bpend = 0; }
    s_box = true; s_bx = t->x; s_by = t->y; s_bt = now; s_bpend++;
    APP_LOG(APP_LOG_LEVEL_INFO, "predict: box to target %d", c);
    return true;
  }
  PlanTarget *t = &s_plan.t[s_plan.cur < s_plan.n ? s_plan.cur : 0];
  Obj *h = obj(s_plan.hero);
  s_plan.on = false;                                     // (whatever Select does, the next plan comes from the phone)
  if (t->blow && h) return swing_start(h, t->blow, now);
  if (!t->k || !h) return false;
  walk_start(h, t->k, t->dir, now);
  return true;
}

// ---- a blow: the hero's swing from the press (his attack animation, as CharSprite.attack: turned to the enemy) ----
// The phone's frames bring the rest. Its own swing, a radio round trip later, isn't shown again. The swing shown stops
// at its blow (the frame before the last: the weapon out) until the phone's swing has begun (at most HOLD_MS more):
// the phone works out the blow's outcome as its swing starts (CharSprite.pebxelStrikeAtStart), so the enemy's flash
// and the damage number come with it, and the swing here goes on as they do; then the last frame (he stands as before)
// until the phone's swing is over, and the phone's frames have him. If the phone does something else (the enemy had
// gone: he walks there), the swing stops at once and the phone's frames show that. If the phone's swing never came in
// time, the swing here is over, and one that comes later (before the next press) isn't played again: he stands.
#define HOLD_MS GAME_MS(250)
static uint16_t s_aid, s_aanim; static uint32_t s_at; static int32_t s_ax0, s_ay0; static bool s_aflip, s_aseen;
static bool s_arel;              // the blow's frame let go: the phone's swing began, or HOLD_MS went by
// where a sprite comes to rest: its motion's end (a step still sliding on here isn't something else done)
static int32_t rest(const Obj *o, int i) { return obj_dyn_raw(o, i, o->until ? o->at + o->until : o->at); }
static bool swing_start(Obj *h, int blow, uint32_t now) {
  Anim *a = scene_anim(s_plan.atk);
  if (!a || !a->n || !(h->flags & F_ANIM)) return false;
  int d = blow & 15, dx = d % 3 - 1;
  s_atk = s_aswung = true; s_aseq = -1; s_aid = s_plan.hero; s_aanim = s_plan.atk; s_at = now; s_aseen = s_arel = s_alate = s_aans = s_ain = false;
  s_ax0 = rest(h, 0); s_ay0 = rest(h, 1);
  s_aflip = dx < 0 ? true : dx > 0 ? false : (h->flags & F_FLIPH) != 0;   // (CharSprite.turnTo: by the columns)
  APP_LOG(APP_LOG_LEVEL_INFO, "predict: swing");
#ifdef TRACE_FIGHT   // (fight timing on the emulator)
  { extern uint32_t trace_ms(void); APP_LOG(APP_LOG_LEVEL_INFO, "TF %lu swing", (unsigned long)trace_ms()); }
#endif
  return true;
}
void predict_sent(int seq) { if (s_aswung) { s_aswung = false; s_aseq = seq; } }
// the answer to the press swung at, by its number (Link.pressed)
void predict_answer(uint32_t a0, int seq) { if (!s_aans && seq == s_aseq) { s_aans = true; s_aa0 = a0; } }
bool predict_anim(const Obj *o, uint32_t now, int *anim, int *frame, bool *fliph) {
  if (!s_atk || o != obj(s_aid) || (s_alate && !s_aseen)) return false;
  Anim *a = scene_anim(s_aanim);
  if (!a || !a->n) return false;
  int fr = a->delay_ms ? (int)((now - s_at) / a->delay_ms) : 0, last = a->n - 1;
  if (!s_arel && a->delay_ms && now - s_at >= (uint32_t)a->delay_ms * last + HOLD_MS) s_arel = true;
  if (!s_arel && last > 1 && fr > last - 1) fr = last - 1;
  *anim = s_aanim; *frame = fr < last && !s_alate ? fr : last; *fliph = s_aflip;
  return true;
}
static void swing_tick(uint32_t now) {
  Obj *h = obj(s_aid);
  const char *why = NULL;
  if (!h) why = "gone";
  else {
    // the phone's own swing plays: told in a frame of the phone's answer to the press (an earlier swing, still coming
    // or told again, isn't it)
    bool phone = h->tx == s_aanim && (h->flags & F_ANIM) && (s_aseen || (s_ain && h->at == s_hat));
    if (phone) {
      s_aseen = true;
      // (where the phone's swing is: a frame on (its blow struck, the damage number with it), the blow here is let go)
      Anim *a = scene_anim(h->tx);
      if (a && a->delay_ms && (uint32_t)h->frame * a->delay_ms + (uint32_t)h->timer * a->delay_ms / 256 + (now - h->at) >= a->delay_ms) s_arel = true;
    }
    else if (s_aseen) why = "over";   // (over there too: its frames from here)
    // (it did something else: he walks. Not his animation changing: the phone's last swing, still playing at the
    // press, ending isn't that)
    else if (iabs(rest(h, 0) - s_ax0) > 8 || iabs(rest(h, 1) - s_ay0) > 8) why = "cut short";
    // never swung there: its frames from well after the press come without it (at the latest after WAIT_MS)
    else if (!s_alate && ((s_heard - s_at < 0x80000000u && s_heard - s_at > (uint32_t)s_plag + GAME_MS(400)) || now - s_at > WAIT_MS)) { s_alate = true; why = "not confirmed"; }
    if (!why && now - s_at > 2 * WAIT_MS) why = "too long";
  }
  // (not confirmed: still looked out for, late)
  if (why) { s_atk = why[0] == 'n'; APP_LOG(APP_LOG_LEVEL_INFO, "predict: swing %s, %d ms after the press", why, (int)(now - s_at)); }
}

// the hero sets off along the steps given, now
static void walk_start(Obj *h, int k, const uint8_t *dir, uint32_t now) {
  s_wid = s_plan.hero; s_wmarker = s_plan.marker; s_box = false; s_wx0 = obj_dyn(h, 0, now); s_wy0 = obj_dyn(h, 1, now);
  s_wt = now; s_wn = k; memcpy(s_wdir, dir, s_wn); s_wstep = s_plan.step_ms ? s_plan.step_ms : 100;
  s_wmoved = false; s_wstop = false; s_walk = true; s_p0 = 0; s_t0 = now; s_plast = 0; s_plast_t = now; s_wplanoff = s_wdone = false; s_endp = -1;
  APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk %d steps", s_wn);
  // the map follows him closely while he walks, as the phone's camera will (HeroSprite.move: panFollow at 20 a
  // second). Left at its resting pace until the phone's frames said so, the view lagged behind the hero for the
  // first half second of every walk, then caught up with a lurch.
  // A map standing still (SPD's zoom stops its camera) or moved over to show a target (Controls.view) follows him
  // from the press too, as the phone's will (Select gives its camera back to the hero): it stood still under the
  // walk shown ahead, the hero walking off towards the edge till the phone's frames came.
  if (g_map_cam >= 0) {
    Cam *c = &g_cam[g_map_cam];
    if (c->mode == 1 && c->follow == s_wid) c->intensity = 255;
    else if (c->mode != 1) { c->mode = 1; c->follow = s_wid; c->ofx = c->ofy = 0; c->dead = 0; c->intensity = 255; c->cx = c->cy = 0; }
  }
  if (s_eid == s_wid) s_eid = NONE;
}

// ---- a tap on the map: the walk worked out here, as the phone's game will (Controls.tapPlan) ----
// Over a radio round trip and the play-out delay, a tapped walk only began when the phone's frames came (a third of a
// second and more); a Select began at the press, as the phone had planned its steps. A tap can be anywhere, so the
// phone sends the ground he may walk (C_PASS, what changed, kept here: a bit a cell) and the cells where a tap does
// something else (the plan), and the watch finds his way as Shattered does (PathFinder.find: the distances from the
// cell tapped, then from him down them, the first lower neighbour in its order), so it is the same way, step for step.
static uint8_t *s_pass; static uint8_t s_pw, s_ph;   // the ground, a bit a cell, row by row
void predict_pass(int fresh, int w, int h, const uint8_t *d, int n) {
  if (fresh || w != s_pw || h != s_ph || !s_pass) {
    free(s_pass); s_pass = NULL; s_pw = w; s_ph = h;
    if (w * h > 0) s_pass = calloc((w * h + 7) / 8, 1);
    if (!s_pass) { s_pw = s_ph = 0; return; }
  }
  const uint8_t *e = d + n;
  int i = 0, len = w * h;
  while (d < e && i < len) {
    int same = 0, sh = 0, c; do { c = d < e ? *d++ : 0; same |= (c & 127) << sh; sh += 7; } while ((c & 128) && sh < 28);
    int flip = 0; sh = 0; do { c = d < e ? *d++ : 0; flip |= (c & 127) << sh; sh += 7; } while ((c & 128) && sh < 28);
    i += same;
    for (int k = 0; k < flip && i < len; k++, i++) s_pass[i >> 3] ^= 1 << (i & 7);
  }
}
static bool ground(int c) { return s_pass[c >> 3] & (1 << (c & 7)); }

#define TAP_R 20                 // the way is worked out in the square this far round him (Controls.TAP_R): any way of
                                 // up to TAP_R steps lies in it, so it is the same as on the whole floor
#define FAR 255
bool predict_tap(int x, int y, uint32_t now) {
  bool on = s_plan.on; s_plan.on = false;
  if (!on || !s_plan.tap || s_walk || !s_pass || g_map_cam < 0) return false;
  Obj *h = obj(s_plan.hero);
  Cam *c = &g_cam[g_map_cam];
  if (!h || !c->used || c->zoom == 0) return false;
  // where on the map (units x8), as the phone reads it: the middle of the watch's pixel (Touches: x * 2 + 1)
  int64_t ux = (((int64_t)(x * 8 + 4 - c->x) << 16) / c->zoom + c->sx), uy = (((int64_t)(y * 8 + 4 - c->y) << 16) / c->zoom + c->sy);
  if (ux < 0 || uy < 0) return false;
  int32_t mx = (int32_t)(ux >> 8), my = (int32_t)(uy >> 8);
  int cx = mx / CELL, cy = my / CELL, fx = mx % CELL, fy = my % CELL;
  // (near a cell's edge, the phone's camera, a little off the watch's, may read the next cell: left to the phone)
  int m = 4096 / c->zoom; if (m < 6) m = 6;
  if (cx >= s_pw || cy >= s_ph || fx < m || fx > CELL - m || fy < m || fy > CELL - m) return false;
  int to = cx + cy * s_pw, from = s_plan.hcell, hx = from % s_pw, hy = from / s_pw;
  // an enemy beside him tapped: the blow (as Select's), unless another creature's or an item's sprite near the tap
  // takes it (CellSelector.onClick: a creature's within 12 of its cell's middle)
  for (int i = 0; i < s_plan.ntb; i++) {
    int b = s_plan.tb[i], d = b & 15;
    if (!b || hx + d % 3 - 1 != cx || hy + d / 3 - 1 != cy) continue;
    for (int j = 0; j < s_plan.nhot; j++) {
      int hc = s_plan.hot[j] & 0x7FFF, ax = (hc % s_pw) * CELL + CELL / 2, ay = (hc / s_pw) * CELL + CELL / 2;
      if (hc != to && hc != from && iabs(mx - ax) <= 96 + m && iabs(my - ay) <= 96 + m) return false;
    }
    return swing_start(h, b, now);
  }
  if (!ground(to) || iabs(cx - hx) > TAP_R || iabs(cy - hy) > TAP_R) return false;
  // a creature's or an item's sprite takes a tap within 12 of its cell's middle (CellSelector.onClick); stairs, a pot
  for (int i = 0; i < s_plan.nhot; i++) {
    int hc = s_plan.hot[i] & 0x7FFF, ax = (hc % s_pw) * CELL + CELL / 2, ay = (hc / s_pw) * CELL + CELL / 2;
    if (hc == to || (iabs(mx - ax) <= 96 + m && iabs(my - ay) <= 96 + m)) return false;
  }
  // the distances from the cell tapped (Shattered's buildDistanceMap: the cell itself whatever it is, then the ground
  // and the hero's own cell), level by level until they reach him
  enum { S = 2 * TAP_R + 1 };
  uint8_t *dist = malloc(S * S);
  if (!dist) return false;
  int ox = hx - TAP_R, oy = hy - TAP_R;
  for (int wy = 0; wy < S; wy++) for (int wx = 0; wx < S; wx++) {
    int lx = ox + wx, ly = oy + wy;
    dist[wx + wy * S] = lx >= 0 && ly >= 0 && lx < s_pw && ly < s_ph && ground(lx + ly * s_pw) ? FAR : FAR - 1;   // (FAR - 1: no way)
  }
  for (int i = 0; i < s_plan.nhot; i++) if (s_plan.hot[i] & 0x8000) {
    int hc = s_plan.hot[i] & 0x7FFF, wx = hc % s_pw - ox, wy = hc / s_pw - oy;
    if (wx >= 0 && wy >= 0 && wx < S && wy < S) dist[wx + wy * S] = FAR - 1;
  }
  int wf = TAP_R + TAP_R * S, wt = (cx - ox) + (cy - oy) * S;
  dist[wf] = FAR; dist[wt] = 0;
  bool found = false;
  for (int d = 0; d < FAR - 2 && !found; d++) {
    bool any = false;
    for (int wy = 0; wy < S; wy++) for (int wx = 0; wx < S; wx++) {
      if (dist[wx + wy * S] != d) continue;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        int nx = wx + dx, ny = wy + dy;
        if ((dx || dy) && nx >= 0 && ny >= 0 && nx < S && ny < S && dist[nx + ny * S] == FAR) { dist[nx + ny * S] = d + 1; any = true; if (nx + ny * S == wf) found = true; }
      }
    }
    if (!any) break;
  }
  int len = found ? dist[wf] : 0;
  uint8_t dirs[PLAN_S]; int k = 0;
  if (found && len <= TAP_R) {
    // from him down the distances: the first lower neighbour in PathFinder's order (dir: -1, +1, -w, +w, -w-1, -w+1, +w-1, +w+1)
    static const int8_t DX[8] = { -1, 1, 0, 0, -1, 1, -1, 1 }, DY[8] = { 0, 0, -1, 1, -1, -1, 1, 1 };
    int sx = TAP_R, sy = TAP_R;
    for (int step = 0; step < len && k < PLAN_S; step++) {
      int best = -1, bd = dist[sx + sy * S];
      for (int i = 0; i < 8; i++) {
        int nx = sx + DX[i], ny = sy + DY[i];
        if (nx < 0 || ny < 0 || nx >= S || ny >= S) continue;
        int v = dist[nx + ny * S]; if (v >= FAR - 1) v = FAR;
        if (v < bd) { bd = v; best = i; }
      }
      if (best < 0) { k = 0; break; }
      dirs[k++] = (DX[best] + 1) + 3 * (DY[best] + 1);
      sx += DX[best]; sy += DY[best];
    }
  }
  free(dist);
  if (!k) return false;
  APP_LOG(APP_LOG_LEVEL_INFO, "predict: tap to %d,%d, %d steps", cx, cy, len);
  walk_start(h, k, dirs, now);
  return true;
}

bool predict_offset(uint16_t id, uint32_t now, int32_t *dx, int32_t *dy) {
  Obj *o = obj(id);
  if (!o || !s_walk || id != s_wid) return false;
  int32_t x, y; walk_at(now, &x, &y);
  *dx = x - obj_dyn_raw(o, 0, now); *dy = y - obj_dyn_raw(o, 1, now);
  return true;
}

bool predict_pos(const Obj *o, int i, uint32_t now, int32_t *v) {
  if (i == 2) { if (s_walk && o == obj(s_wmarker)) { *v = 0; return true; } return false; }   // the box goes while he walks (as on the phone)
  if (s_walk && o == obj(s_wid)) { int32_t x, y; walk_at(now, &x, &y); *v = i ? y : x; return true; }
  if (s_box && o == obj(s_plan.marker)) { *v = i ? s_by : s_bx; return true; }
  if (s_eid != NONE && o == obj(s_eid)) {
    uint32_t el = now - s_et;
    if (el >= EASE_MS) return false;
    *v = obj_dyn_raw(o, i, now) + (i ? s_edy : s_edx) * (int32_t)(EASE_MS - el) / EASE_MS;
    return true;
  }
  return false;
}

static void slide_back(uint16_t id, int32_t px, int32_t py, uint32_t now) {
  Obj *o = obj(id);
  if (!o) { s_eid = NONE; return; }
  s_eid = id; s_et = now; s_edx = px - obj_dyn_raw(o, 0, now); s_edy = py - obj_dyn_raw(o, 1, now);
}

#ifdef TRACE_WALK
// (timing tests) the hero's place as drawn, the phone's, and the map's scroll, whenever one changes
static void trace(uint32_t now) {
  static int32_t lx, ly, lsx; Obj *h = obj(s_thero);
  if (!h) return;
  int32_t x = obj_dyn(h, 0, now) / 8, y = obj_dyn(h, 1, now) / 8, sx = g_map_cam >= 0 ? g_cam[g_map_cam].sx / 2048 : 0;
  if (x == lx && y == ly && sx == lsx) return;
  lx = x; ly = y; lsx = sx;
  APP_LOG(APP_LOG_LEVEL_INFO, "TW %lu %ld %ld raw %ld %ld cam %ld walk %d", (unsigned long)now, (long)x, (long)y, (long)(obj_dyn_raw(h, 0, now) / 8), (long)(obj_dyn_raw(h, 1, now) / 8), (long)sx, s_walk);
}
#endif

void predict_tick(uint32_t now) {
#ifdef TRACE_WALK
  trace(now);
#endif
  if (s_eid != NONE && (now - s_et >= EASE_MS || !obj(s_eid))) s_eid = NONE;
  if (s_atk) swing_tick(now);
  if (s_box) {
    Obj *m = obj(s_plan.marker);
    if (!m) s_box = false;
    else {
      int32_t rx = obj_dyn_raw(m, 0, now), ry = obj_dyn_raw(m, 1, now);
      if (rx != s_rx || ry != s_ry) { s_rx = rx; s_ry = ry; if (s_bpend) s_bpend--; }   // the phone has answered a press
      if ((iabs(rx - s_bx) <= 4 && iabs(ry - s_by) <= 4) || !s_bpend) {   // it agrees, or it answered them all (its place stands)
        s_box = false; APP_LOG(APP_LOG_LEVEL_INFO, "predict: box %s after %d ms", s_bpend ? "confirmed" : "answered", (int)(now - s_bt)); }
    }
    if (s_box && now - s_bt > WAIT_MS) { s_box = false; APP_LOG(APP_LOG_LEVEL_INFO, "predict: box not confirmed"); }   // it never did: its place stands
  }
  if (s_walk) {
    Obj *h = obj(s_wid);
    if (!h) { s_walk = false; return; }
    int32_t rx = obj_dyn_raw(h, 0, now), ry = obj_dyn_raw(h, 1, now), px, py, fx = s_wx0, fy = s_wy0;
    walk_at(now, &px, &py);
    if (s_endp >= 0) prog_at(s_endp, &fx, &fy);   // (where the phone said he stopped)
    else for (int j = 0; j < s_wn; j++) { fx += dxof(s_wdir[j]) * CELL; fy += dyof(s_wdir[j]) * CELL; }
    if (!s_wmoved && (iabs(rx - s_wx0) > 8 || iabs(ry - s_wy0) > 8)) {   // the phone's hero has set off
      s_wmoved = true;
      uint32_t l = now - s_wt; if (l > GAME_MS(1000)) l = GAME_MS(1000);
      s_lat = (3 * s_lat + l) / 4;
    }
    // the phone's hero is ready again (predict_set): he is where he stays. (Waiting for frames to show him still was
    // slow, and when the phone then had nothing more to send, the hero stood a cell or two too far for seconds.)
    if (s_wdone) {
      s_walk = false; slide_back(s_wid, px, py, now);
      APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk over, %d ms after the press, back %d,%d", (int)(now - s_wt), (int)((px - rx) / 8), (int)((py - ry) / 8));
      return;
    }
    if (s_wmoved && raw_prog(rx, ry) < 0) { s_walk = false; slide_back(s_wid, px, py, now); APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk went elsewhere"); return; }
    // (over a slow radio his frames come late, already past: he goes from place to place without ever moving; any
    // change of place counts as moving)
    static int32_t s_lrx, s_lry;
    bool moved = rx != s_lrx || ry != s_lry; s_lrx = rx; s_lry = ry;
    if (s_wmoved && !obj_moving(h, now) && !moved) {
      if (iabs(rx - fx) <= 16 && iabs(ry - fy) <= 16) {   // arrived: the same place
        s_walk = false; slide_back(s_wid, px, py, now); APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk confirmed, %d ms after the press", (int)(now - s_wt)); return; }
      // it stopped short: the phone's frames from well after his last step ended still show him there. (Counted in
      // the phone's own time, its frames as played: counted in the watch's, as it was, a late message played at once
      // looked like a long time still, and real stops were seen only 400 ms on, the hero shown walking on past)
      uint32_t end = h->at + h->until;
      if (s_heard - end < 0x80000000u && s_heard - end > STILL_MS) {
        s_walk = false; slide_back(s_wid, px, py, now); APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk stopped short, back %d,%d", (int)((px - rx) / 8), (int)((py - ry) / 8)); return; }
    }
    // never set off: the phone did something else with the press (a monster came into view at once, say). Seen when
    // its frames from well after the press come with him still. (Not merely when nothing comes: a message lost on the
    // radio holds everything up 3 s, and giving up then took him back to the start, to leap on again when the
    // phone's frames came.) At the latest after 2 WAIT_MS
    bool idle = !s_wmoved && s_heard - s_wt < 0x80000000u && s_heard - s_wt > (uint32_t)s_plag + GAME_MS(400);
    if ((!s_wmoved && now - s_wt > 2 * WAIT_MS) || idle || now - s_wt > (uint32_t)s_wn * s_wstep + GAME_MS(3333) + 3 * WAIT_MS) {
      s_walk = false; slide_back(s_wid, px, py, now); APP_LOG(APP_LOG_LEVEL_INFO, "predict: walk not confirmed (moved %d)", s_wmoved); }
  }
}
