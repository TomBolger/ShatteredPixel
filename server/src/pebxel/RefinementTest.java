package pebxel;

import com.badlogic.gdx.Files;
import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.scenes.*;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import java.lang.reflect.*;
import java.util.*;

/** Regression checks against the real game: safe food/rest/gear, complete drags and delayed menu touches.
 * Run from spd/core/src/main/assets with the same classpath as MenuTest. Fails with a nonzero exit status. */
public final class RefinementTest {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static Assist.Target hands() throws Exception {
        Method setup = Assist.class.getDeclaredMethod("setup", java.util.function.IntPredicate.class);
        setup.setAccessible(true); setup.invoke(null, new Object[]{null});
        Method hands = Assist.class.getDeclaredMethod("hands", Assist.Target.class);
        hands.setAccessible(true); return (Assist.Target) hands.invoke(null, new Object[]{null});
    }
    static void show(Object w, int page) throws Exception {
        Method m = w.getClass().getDeclaredMethod("show", int.class); m.setAccessible(true); m.invoke(w, page);
    }
    static int seq;
    static void touch(Touches touches, Link link, Controls controls, int kind, int x, int y) {
        touches.add(new byte[]{(byte)kind, (byte)++seq, (byte)255, 0, (byte)x, 0, (byte)y, 0, 0, 0, 0, 0});
        touches.frame(link, controls, 1f/30); PointerEvent.processPointerEvents();
    }
    // ---- the Assistant obeys: what the player passes over isn't pressed on him again (Pebble PD's pass_hands) ----
    static Controls oc;
    @SuppressWarnings("unchecked")
    static java.util.List<Assist.Target> list() throws Exception { return (java.util.List<Assist.Target>) get(oc, "plan"); }
    static void frames(WatchApp app, int n) { for (int i = 0; i < n; i++) { step(app); oc.frame(); } }
    /** One frame in lockstep with the game's actor thread (as the bot plays: Lockstep): after the frame the actor
     *  thread runs until it waits again, so what a press set going has happened by the next check however loaded the
     *  machine is. Before, the thread ran on its own and checks that waited a fixed number of frames failed now and then. */
    static void step(WatchApp app) { Lockstep.frame(app); }
    /** Frames until `ok` holds, at most `cap` (generous: what is waited for takes a few); whether it came. */
    static boolean until(WatchApp app, int cap, java.util.function.BooleanSupplier ok) {
        for (int f = 0; f < cap && !ok.getAsBoolean(); f++) frames(app, 1);
        return ok.getAsBoolean();
    }
    /** Frames until the hero is ready with a list made since game time `after` (or his place changed from `from`). */
    static Assist.Target planA(WatchApp app, float after, int from) throws Exception {
        for (int f = 0; f < 3000; f++) {
            frames(app, 1);
            Hero h = Dungeon.hero;
            if (h.ready && Ui.topWindow() == null && (com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now() > after || h.pos != from) && !list().isEmpty()
                    && !(Boolean) get(oc, "pending")) { frames(app, 2); return list().isEmpty() ? null : list().get(0); }
        }
        return null;
    }
    static float now() { return com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now(); }
    static void descend(WatchApp app) throws Exception {
        int d0 = Dungeon.depth;
        StairsTest.jump(StairsTest.stairs(false));   // (the real way down: the exit's transition)
        for (int f = 0; f < 5000; f++) {
            step(app); if (Game.scene() instanceof InterlevelScene) HandoffTest.pressOn();
            if (Dungeon.depth > d0 && Game.scene() instanceof GameScene && Dungeon.hero != null && Dungeon.hero.ready) break;
        }
        check(Dungeon.depth > d0, "down a floor");
        while (Ui.topWindow() != null) Ui.topWindow().hide();
        Gallery.calm();
    }
    static void obedience(WatchApp app) throws Exception {
        Hero h = Dungeon.hero; oc = new Controls(); h.lvl = 1; h.exp = 0;   // (no talent points to offer first)
        WatchSettings.assist(1); WatchSettings.itemAssist(true); Assist.reset();
        h.belongings.weapon = null; h.belongings.armor = new ClothArmor(); h.belongings.armor.identify();
        ScrollOfUpgrade up = new ScrollOfUpgrade(); up.identify(); h.belongings.backpack.items.add(up);
        Assist.Target a = planA(app, -1, -1);
        check(a != null && a.item == up, "the upgrade scroll is offered first: " + a);
        // skipped with Down, the next one chosen: not first again
        float t0 = now(); int p0 = h.pos;
        oc.button(Controls.DOWN); oc.button(Controls.SELECT);
        a = planA(app, t0, p0);
        check(a != null && a.item != up, "an upgrade passed over with Down is no longer first: " + a);
        for (Assist.Target e : list()) check(e.item != up, "an upgrade passed over is not offered again on this floor: " + list());
        // an objective to explore (or grass) passed over: not offered first again at once
        a = list().get(0);
        check(a.kind == Assist.GO || a.kind == Assist.PICKUP, "plan A walks somewhere: " + a);
        int o = a.obj >= 0 ? a.obj : a.cell; boolean grass = Dungeon.level.map[o] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.HIGH_GRASS;
        t0 = now(); p0 = h.pos;
        oc.button(Controls.DOWN);   // (to one that is no more tall grass, nor nearer plan A's: choosing that is no pass)
        Field tgF = Controls.class.getDeclaredField("target"); tgF.setAccessible(true);
        for (int i = 0; i < 6; i++) { Assist.Target e = list().get((Integer) tgF.get(oc)); int eo = objOf(e);
            if (e.kind == Assist.ENEMY || eo < 0 || (Dungeon.level.map[eo] != com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.HIGH_GRASS && Dungeon.level.distance(e.cell, o) >= Dungeon.level.distance(h.pos, o))) break;
            oc.button(Controls.DOWN); }
        oc.button(Controls.SELECT);
        a = planA(app, t0, p0);
        check(a != null && (a.obj >= 0 ? a.obj : a.cell) != o, "a place passed over is not first again at once: " + o + " vs " + a);
        final boolean grassF = grass; final int oF = o;
        until(app, 300, () -> { try { return grassF ? (Boolean) get(null, "grassSkip", Assist.class) : Assist.demoted(oF); } catch (Exception e) { return false; } });
        check(grass ? (Boolean) get(null, "grassSkip", Assist.class) : Assist.demoted(o), "a place passed over is held back: " + o + " now " + a + " list " + list());
        // a new floor: things stand afresh, the upgrade first again
        descend(app); h = Dungeon.hero;
        a = planA(app, -1, -1);
        check(a != null && a.item == up, "a new floor offers the upgrade again: " + a);
        // ignored: something else done instead (the toolbar's search, held) passes it over too
        t0 = now(); p0 = h.pos;
        Object hud0 = get(oc, "hud"); Field hlF = pebxel.ui.WatchHud.class.getField("highlight");
        for (int i = 0; i < 8 && (Integer) hlF.get(hud0) != pebxel.ui.WatchHud.TB_SEARCH; i++) oc.button(Controls.BACK);   // (Item assist: Back walks the Assistant's order)
        check((Integer) hlF.get(hud0) == pebxel.ui.WatchHud.TB_SEARCH, "Back reaches the search button");
        oc.button(Controls.SELECT | Controls.LONG);
        a = planA(app, t0, p0);
        check(a == null || a.item != up, "an upgrade ignored for something else is no longer first: " + a);
        // ignored for a walk tapped on the map: passed over too, as with the buttons (owner: "tapping movement is still not overriding item suggestions while buttons do")
        Assist.reset(); Field st0 = Controls.class.getDeclaredField("targetsStale"); st0.setAccessible(true); st0.set(oc, true);
        a = planA(app, -1, h.pos);
        check(a != null && a.item == up, "after a reset the upgrade is offered again: " + a);
        {
            int[] wd = walks(Dungeon.level, h.pos); int tc = -1;
            for (int c = 0; c < wd.length; c++) if (wd[c] >= 3 && wd[c] <= 4 && Controls.onScreen(c) && Dungeon.level.heaps.get(c) == null && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null && Dungeon.level.map[c] != com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR) { tc = c; break; }
            check(tc >= 0, "a cell to tap");
            Link tl = new Link(new Assets()); Touches tt = new Touches();
            frames(app, 30);   // (the camera at rest: the tap lands on the cell aimed at)
            com.watabou.utils.Point sp = com.watabou.noosa.Camera.main.cameraToScreen(com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.tileCenterToWorld(tc).x, com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.tileCenterToWorld(tc).y);
            t0 = now(); p0 = h.pos;
            touch(tt, tl, oc, 1, sp.x / 2, sp.y / 2);
            for (int i = 0; i < 4; i++) { tt.frame(tl, oc, 1f/30); PointerEvent.processPointerEvents(); frames(app, 1); }
            a = planA(app, t0, p0);
            check(h.pos != p0, "the tap walked the hero (" + p0 + " -> " + h.pos + ", tapped " + tc + ")");
            check(a == null || a.item != up, "an upgrade ignored for a tapped walk is no longer first: " + a);
        }
        // chosen, it is honoured (the bag opens on the armour for the player to confirm)
        Assist.reset(); Field st = Controls.class.getDeclaredField("targetsStale"); st.setAccessible(true); st.set(oc, true);
        a = planA(app, -1, h.pos);
        check(a != null && a.item == up, "after a reset the upgrade is offered: " + a);
        oc.button(Controls.SELECT); frames(app, 5);
        check(Ui.topWindow() != null, "Select on the upgrade opens its choice for the player");
        while (Ui.topWindow() != null) Ui.topWindow().hide();
        h.belongings.backpack.items.remove(up);
        // the camera: every target Down shows lies whole in the play area (never under the HUD), Near and Far
        for (int mode = 1; mode <= 2; mode++) {
            WatchSettings.assist(mode); Assist.reset(); st.set(oc, true);
            planA(app, -1, h.pos);
            int n = list().size();
            check(n > 1, "a list to cycle (" + mode + ")");
            for (int i = 0; i <= n; i++) {
                frames(app, 90);   // (the camera settles)
                Method ct = Controls.class.getDeclaredMethod("currentTarget"); ct.setAccessible(true);
                int c = (Integer) ct.invoke(oc);
                if (c >= 0) check(Controls.onScreen(c), "target " + c + " shows whole, clear of the HUD (assist " + mode + ", entry " + i + ")");
                oc.button(Controls.DOWN);
            }
        }
        // the map dragged away from the target: the HUD points to it from the edge, the box on the map hidden
        com.watabou.noosa.Camera.main.shift(new com.watabou.utils.PointF(400, 0)); frames(app, 3);
        Object hud = get(oc, "hud"), edge = get(hud, "edge", pebxel.ui.WatchHud.class), mk = get(oc, "marker");
        check(((com.watabou.noosa.Group) edge).visible, "a target off the view is pointed to from the edge");
        com.watabou.noosa.Image mki = (com.watabou.noosa.Image) get(mk, "img", mk.getClass());
        check(!mki.visible || mki.alpha() == 0, "its box on the map is hidden meanwhile");   // (see-through: Controls.Marker.hide)
        oc.button(Controls.DOWN); frames(app, 90);
        check(!((com.watabou.noosa.Group) edge).visible, "a Down brings the view back to show the target");
        // the hero too: after each Down the hero and the target both show whole in the play area (Near and Far)
        for (int mode = 1; mode <= 2; mode++) {
            WatchSettings.assist(mode); Assist.reset(); st.set(oc, true);
            planA(app, -1, h.pos);
            for (int i = 0; i < list().size(); i++) {
                oc.button(Controls.DOWN); frames(app, 90);
                Method ct = Controls.class.getDeclaredMethod("currentTarget"); ct.setAccessible(true);
                int c = (Integer) ct.invoke(oc);
                if (c >= 0) check(Controls.onScreen(c) && Controls.onScreen(h.pos), "after Down the hero and target " + c + " both show whole (assist " + mode + ", entry " + i + ")");
            }
        }
        // Far (Pebble PD's cam_to): a far target zooms out as far as it takes to show both; a Down to a near one doesn't
        // zoom (no churn); the list made again with a near target first zooms back in to the player's zoom
        com.watabou.noosa.Camera cam = com.watabou.noosa.Camera.main;
        Field uz = Controls.class.getDeclaredField("userZoom"); uz.setAccessible(true); uz.set(oc, 1);   // (16 px tiles)
        int W = Dungeon.level.width(), hp = h.pos, far = -1, near = -1;
        for (int d = 14; d >= 12 && far < 0; d--) for (int sgn : new int[]{1, -1}) { int x = hp % W + sgn * d; if (far < 0 && x > 0 && x < W - 1) far = hp + sgn * d; }
        for (int n : new int[]{1, -1, W, -W}) if (near < 0 && n + hp >= 0 && n + hp < Dungeon.level.length()) near = hp + n;
        check(far >= 0 && near >= 0, "cells for the zoom check");
        final int fFar = far, fNear = near;
        Controls.legacy = aim -> new ArrayList<>(Arrays.asList(fNear, fFar));
        WatchSettings.assist(2); st.set(oc, true); frames(app, 90);
        check(cam.zoom == 2, "Far, a near target: the player's zoom (" + cam.zoom + ")");
        oc.button(Controls.DOWN); frames(app, 90);
        check(cam.zoom < 2 && Controls.onScreen(far) && Controls.onScreen(hp), "Far, a far target: zoomed out (" + cam.zoom + "), the hero and the target both whole");
        oc.button(Controls.DOWN); frames(app, 90);
        check(cam.zoom < 2 && Controls.onScreen(near) && Controls.onScreen(hp), "a Down to a near target doesn't zoom back and forth (" + cam.zoom + ")");
        st.set(oc, true); frames(app, 90);
        check(cam.zoom == 2 && Controls.onScreen(near) && Controls.onScreen(hp), "the list made again, a near target: zoomed back in (" + cam.zoom + ")");
        // Up keeps the player's zoom this turn (the target in the middle); long Up locks the zoom on the hero
        oc.button(Controls.DOWN); frames(app, 90); float zf = cam.zoom;
        oc.button(Controls.UP | Controls.LONG); frames(app, 30);
        check(Controls.zlock && cam.zoom == zf, "long Up in Far: the zoom locked where it is (" + cam.zoom + ")");
        oc.button(Controls.UP | Controls.LONG); frames(app, 30);
        check(!Controls.zlock, "long Up again: unlocked");
        check((Integer) uz.get(oc) == 0, "the zoom locked became the player's (Pebble PD 1.0)"); uz.set(oc, 1);
        // the pointers: the map dragged away, the target's and the hero's framed at the edge; a tap on the target's
        // brings the view to it, on the hero's back to him
        cam.shift(new com.watabou.utils.PointF(600, 0)); frames(app, 3);
        Object heroEdge = get(hud, "heroEdge", pebxel.ui.WatchHud.class);
        check(((com.watabou.noosa.Group) edge).visible && ((com.watabou.noosa.Group) heroEdge).visible, "dragged away: both pointers framed at the edge");
        {   // never on top of each other (Thomas: "the off screen indicators shouldn't be able to overlap"), their taps where they're drawn
            com.watabou.noosa.ui.Component tb = (com.watabou.noosa.ui.Component) get(hud, "targetBtn", pebxel.ui.WatchHud.class), hb = (com.watabou.noosa.ui.Component) get(hud, "heroBtn", pebxel.ui.WatchHud.class);
            int g = pebxel.ui.WatchHud.pointerGap();
            check(Math.abs(tb.left() - hb.left()) >= g || Math.abs(tb.top() - hb.top()) >= g, "dragged away: the pointers side by side (" + tb.left() + "," + tb.top() + " " + hb.left() + "," + hb.top() + ")");
            int n = 0;
            for (int hx = -200; hx <= 400; hx += 20) for (int hy = -200; hy <= 400; hy += 20) for (int k = 0; k < 40; k++) {
                float tx = hx + (k % 8 - 4) * 7, ty = hy + (k / 8 - 2) * 9;
                int[] ha = pebxel.ui.WatchHud.pointerAt(hx, hy), ta = pebxel.ui.WatchHud.apart(ha, pebxel.ui.WatchHud.pointerAt(tx, ty));
                check(Math.abs(ta[0] - ha[0]) >= g || Math.abs(ta[1] - ha[1]) >= g, "pointers apart: hero " + ha[0] + "," + ha[1] + " target " + ta[0] + "," + ta[1]);
                int[] back = pebxel.ui.WatchHud.pointerAt(ta[0], ta[1]);
                check(back[0] == ta[0] && back[1] == ta[1], "a slid pointer stays on the screen's edge band: " + ta[0] + "," + ta[1]);
                n++;
            }
        }
        Object hooks = get(oc, "hooks"); Method tag = hooks.getClass().getMethod("tag", int.class, boolean.class); tag.setAccessible(true);
        tag.invoke(hooks, 5, false); frames(app, 90);
        check(!((com.watabou.noosa.Group) edge).visible && !((com.watabou.noosa.Group) heroEdge).visible, "a tap on the target's pointer: the view on the target and the hero");
        cam.shift(new com.watabou.utils.PointF(600, 0)); frames(app, 3);
        tag.invoke(hooks, 6, false); frames(app, 90);
        check(!((com.watabou.noosa.Group) heroEdge).visible && cam.zoom == 2, "a tap on the hero's pointer: back on him at the player's zoom (" + cam.zoom + " " + ((com.watabou.noosa.Group) heroEdge).visible + " " + cam.followTarget() + ")");
        Controls.legacy = null;
        WatchSettings.assist(2); oc = null;
        System.out.println("obedience: passed over with Down, ignored, a new floor, chosen; camera: targets clear of the HUD, hero and target in view, Far's zoom out and back in, lock, pointers (never overlapping): ok");
    }
    // ---- Pebble PD's buttons: Back resets the list and walks the toolbar in the Assistant's order; Select with nothing
    // listed: the camera back, else what lies underfoot, else a search; any button stops a walk; a blow buzzes ----
    static void buttons(WatchApp app) throws Exception {
        Hero h = Dungeon.hero; oc = new Controls();
        WatchSettings.assist(1); WatchSettings.itemAssist(true); Assist.reset();
        planA(app, -1, -1); frames(app, 3);
        Field tf = Controls.class.getDeclaredField("target"); tf.setAccessible(true);
        Object hud = get(oc, "hud"); Field hlF = pebxel.ui.WatchHud.class.getField("highlight");
        if (list().size() > 1) {
            oc.button(Controls.DOWN); check((Integer) tf.get(oc) == 1, "Down: the second suggestion");
            oc.button(Controls.BACK); check((Integer) tf.get(oc) == 0, "Back: the list back to its first suggestion");
        } else oc.button(Controls.BACK);
        int[] order = (int[]) get(oc, "hlOrder");
        check((Integer) hlF.get(hud) == order[0], "Item assist: Back starts on the Assistant's first button " + Arrays.toString(order));
        Set<Integer> seen = new HashSet<>(); seen.add(order[0]);
        for (int i = 1; i < 8; i++) { oc.button(Controls.BACK); seen.add((Integer) hlF.get(hud)); check((Integer) hlF.get(hud) == order[i], "Back walks the ranked order"); }
        check(seen.size() == 8, "every button once: " + seen);
        oc.button(Controls.BACK); check((Integer) hlF.get(hud) == -1, "then none");
        // Select with nothing listed
        Controls.legacy = aim -> new ArrayList<>();
        Field st = Controls.class.getDeclaredField("targetsStale"); st.setAccessible(true); st.set(oc, true); frames(app, 3);
        com.watabou.noosa.Camera cam = com.watabou.noosa.Camera.main;
        cam.shift(new com.watabou.utils.PointF(300, 0)); frames(app, 3);
        float t0 = now(); oc.button(Controls.SELECT); frames(app, 60);
        check(now() == t0 && Math.abs(cam.scroll.x + cam.width / 2f - h.sprite.center().x) < 40, "Select, nothing listed, the map dragged off: the camera back on the hero, no turn");
        Item it = new com.shatteredpixel.shatteredpixeldungeon.items.food.Food(); Dungeon.level.drop(it, h.pos);
        oc.button(Controls.SELECT); final Hero hF = h;
        until(app, 600, () -> Dungeon.level.heaps.get(hF.pos) == null && hF.belongings.contains(it));
        check(Dungeon.level.heaps.get(h.pos) == null && h.belongings.contains(it), "Select, nothing listed: what lies underfoot picked up");
        while (Ui.topWindow() != null) Ui.topWindow().hide();
        t0 = now(); int p0 = h.pos; oc.button(Controls.SELECT);
        final float t0F = t0; until(app, 600, () -> now() > t0F);   // (the game thread takes the turn when it gets to it)
        check(now() > t0 && h.pos == p0, "Select, nothing listed, nothing underfoot: a search");
        Controls.legacy = null; st.set(oc, true);
        // any button stops a walk (the step under way ends)
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L0 = Dungeon.level; Arrays.fill(L0.visited, true);
        int[] d = walks(L0, h.pos); int far = -1;
        for (int c = 0; c < d.length; c++) if (d[c] >= 4 && (L0.visited[c] || L0.mapped[c]) && (far < 0 || d[c] > d[far])) far = c;
        check(far >= 0, "somewhere to walk: reach " + Arrays.stream(d).max().getAsInt());
        GameScene.handleCell(far);
        for (int f = 0; f < 300 && h.pos == p0; f++) frames(app, 1);
        int p1 = h.pos; oc.button(Controls.UP);
        for (int f = 0; f < 120 && !h.ready; f++) frames(app, 1);
        check(h.ready && Dungeon.level.distance(h.pos, p1) <= 1 && h.pos != far, "Up stopped the walk where he was (" + p1 + " -> " + h.pos + ", going to " + far + ")");
        // Assist off: the walk cut short can be resumed from its tag (Pebble PD's ResumeButton); Down's tag with one target
        WatchSettings.assist(0); st.set(oc, true);
        Object[] tagBs = (Object[]) get(hud, "tagButtons", pebxel.ui.WatchHud.class);
        until(app, 300, () -> hF.lastAction != null && ((com.watabou.noosa.Gizmo) tagBs[4]).visible);
        check(h.lastAction != null && ((com.watabou.noosa.Gizmo) tagBs[4]).visible, "Off, a walk cut short: the Resume tag shows");
        Object hooks = get(oc, "hooks"); Method tag = hooks.getClass().getMethod("tag", int.class, boolean.class); tag.setAccessible(true);
        int p2 = h.pos; tag.invoke(hooks, 4, false);
        for (int f = 0; f < 300 && h.pos == p2; f++) frames(app, 1);
        check(h.pos != p2, "the Resume tag walks on");
        for (int f = 0; f < 600 && !h.ready; f++) frames(app, 1);
        final int one = h.pos + 1; Controls.legacy = aim -> new ArrayList<>(Arrays.asList(one)); st.set(oc, true); frames(app, 5);
        check(((com.watabou.noosa.Gizmo) tagBs[2]).visible, "one target: Down's tag shows (Pebble PD)");
        Controls.legacy = null;
        // (PICS=folder: pictures of the new HUD pieces, as the watch shows them)
        String pics = System.getenv("PICS");
        if (pics != null) {
            h.lastAction = new com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction.Move(one); st.set(oc, true); frames(app, 5); pic(pics, "resume");
            h.lastAction = null;
            com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone stone = new com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone();
            stone.quantity(3).collect(); Dungeon.quickslot.setSlot(0, stone);
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat rat = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat();
            Dungeon.level.updateFieldOfView(h, Dungeon.level.heroFOV);
            int rc = -1; for (int c = 0; c < Dungeon.level.length(); c++) { int dd = Dungeon.level.distance(c, h.pos); if (dd >= 2 && dd <= 4 && Dungeon.level.passable[c] && Dungeon.level.heroFOV[c] && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null) { rc = c; break; } }
            check(rc >= 0, "a cell in sight for the rat");
            rat.pos = rc; rat.state = rat.SLEEPING; GameScene.add(rat); Dungeon.level.updateFieldOfView(h, Dungeon.level.heroFOV); frames(app, 5);
            com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton.lastTarget = rat;
            tag.invoke(hooks, 1, false);   // (nothing: just so the HUD settles)
            Method press = hooks.getClass().getMethod("press", int.class, boolean.class); press.setAccessible(true);
            for (int f = 0; f < 600 && !h.ready; f++) frames(app, 1);
            stone.execute(h); com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton.useTargeting(0); frames(app, 5);   // (as the slot pressed)
            check(Controls.aiming() && (Integer) hooks.getClass().getMethod("aimSlot").invoke(hooks) == 0, "aiming from the first quickslot at the rat: its crosshair (aiming " + Controls.aiming() + " slot " + com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton.targetingSlot + " target " + com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton.lastTarget + " rat at " + rc + " fov " + (rc >= 0 && Dungeon.level.heroFOV[rc]) + " in chars " + com.shatteredpixel.shatteredpixeldungeon.actors.Actor.chars().contains(rat) + ")");
            pic(pics, "aim");
            GameScene.cancelCellSelector(); frames(app, 3);
            rat.die(null); frames(app, 3);
        }
        WatchSettings.assist(1); st.set(oc, true);
        // a blow: a short buzz and the light (C_EVENT); death: the long one
        Feedback.take(); h.HP -= 1; frames(app, 1);
        check((Feedback.take() & (Feedback.HURT | Feedback.LIGHT)) == (Feedback.HURT | Feedback.LIGHT), "hurt: a buzz and the light");
        WatchSettings.assist(2); oc = null;
        System.out.println("buttons: Back resets the list and walks the ranked toolbar; Select with nothing listed re-centres, picks up, searches; a press stops a walk; a blow buzzes: ok");
    }
    /** A chest is opened from the cell beside it (Hero.actOpenChest): the walk the watch shows at once (Controls.plan,
     *  predict.c) never puts the hero on it (Thomas: "I step into it, then teleport back after it opens"). */
    static void chestFromBeside() {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero hh = Dungeon.hero;
        int[] d = walks(L, hh.pos); int n = 0;
        for (int c = 0; c < L.length() && n < 6; c++) {
            if (d[c] < 1 || d[c] > 4 || L.heaps.get(c) != null || com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) != null || !L.visited[c]) continue;
            java.util.List<Integer> plain = Controls.steps(hh, c);
            if (plain == null) continue;
            for (com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type ty : new com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type[]{
                    com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.CHEST, com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.TOMB, com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.SKELETON}) {
                com.shatteredpixel.shatteredpixeldungeon.items.Heap hp = new com.shatteredpixel.shatteredpixeldungeon.items.Heap();
                hp.type = ty; hp.pos = c; hp.drop(new com.shatteredpixel.shatteredpixeldungeon.items.Gold(5)); L.heaps.put(c, hp);
                java.util.List<Integer> st = Controls.steps(hh, c);
                L.heaps.remove(c);
                if (L.adjacent(hh.pos, c)) check(st == null, "a " + ty + " next to the hero: opened from where he stands, no walk shown");
                else check(st != null && !st.contains(c) && L.adjacent(st.get(st.size() - 1), c) && st.size() == plain.size() - 1,
                        "a " + ty + " " + d[c] + " steps off: the walk shown stops beside it (" + st + " of " + plain + ")");
            }
            check(plain.get(plain.size() - 1) == c, "a plain heap: walked onto, to pick up");
            n++;
        }
        check(n > 0, "chests tried");
        System.out.println("chests: opened from beside, the hero never shown on one (" + n + " places): ok");
    }

    static void pic(String dir, String name) throws Exception {
        Link l = new Link(new Assets()); WatchSim sim = new WatchSim(new java.io.File("."), new Assets());
        l.frame(1f/30); for (byte[] m : l.take(1000000)) sim.apply(m);
        sim.render(); new java.io.File(dir).mkdirs(); sim.save(new java.io.File(dir, name + "-" + Screen.w + ".png"), 2);
    }
    // ---- the Assistant's choice of places: near before far, keys, a held key's door, no stairs with the floor not done ----
    static int[] walks(com.shatteredpixel.shatteredpixeldungeon.levels.Level L, int from) {
        int[] d = new int[L.length()]; Arrays.fill(d, -1); ArrayDeque<Integer> q = new ArrayDeque<>(); d[from] = 0; q.add(from);
        while (!q.isEmpty()) { int c = q.poll(); for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k;
            if (n < 0 || n >= d.length || d[n] >= 0 || !L.passable[n] || L.avoid[n] || Math.abs(n % L.width() - c % L.width()) > 1) continue; d[n] = d[c] + 1; q.add(n); } }
        return d;
    }
    /** A floor cell at about walk w (the nearest above it), not the stairs, none of `not`. */
    static int at(com.shatteredpixel.shatteredpixeldungeon.levels.Level L, int[] d, int w, int... not) {
        int best = -1;
        outer: for (int c = 0; c < d.length; c++) {
            if (d[c] < w || c == L.exit() || c == L.entrance() || !(L.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY || L.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY_DECO || L.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.GRASS) || L.heaps.get(c) != null || L.traps.get(c) != null) continue;
            for (int x : not) if (L.distance(c, x) <= 2) continue outer;
            if (best < 0 || d[c] < d[best]) best = c; }
        return best;
    }
    static Assist.Target first() { java.util.List<Assist.Target> l = Assist.plan(false, null); return l.isEmpty() ? null : l.get(0); }
    static int objOf(Assist.Target t) { return t == null ? -1 : t.obj >= 0 ? t.obj : t.cell; }
    static void places() throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero; int dep = Dungeon.depth;
        WatchSettings.assist(2); WatchSettings.itemAssist(false); Assist.reset(); Gallery.calm();
        L.heaps.clear(); h.buff(Hunger.class).satisfy(1000);
        int pos0 = h.pos, bestN = -1;   // (the hero set down where most of the floor is a walk away: the room he arrived in may be shut off)
        for (int c = 0; c < L.length(); c += 7) if (L.passable[c] && !L.avoid[c] && L.map[c] != com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR && c != L.exit() && c != L.entrance()) {
            long n = Arrays.stream(walks(L, c)).filter(x -> x >= 0).count(); if (n > bestN) { bestN = (int) n; h.pos = c; } }
        int[] d = walks(L, h.pos);
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true);
        int near = at(L, d, 5), far = at(L, d, 22, near);
        check(near >= 0 && far >= 0, "a floor to set the scenes on: " + near + " " + far + " hero " + h.pos + " reach " + Arrays.stream(d).filter(x -> x >= 0).count() + " max " + Arrays.stream(d).max().getAsInt() + " " + L.getClass().getSimpleName());
        // 1. a held iron key's door far off, unexplored ground a few steps away: the near ground first
        com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(far, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.LOCKED_DOOR);
        com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey iron = new com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey(dep);
        com.shatteredpixel.shatteredpixeldungeon.journal.Notes.add(iron);
        L.visited[near] = L.mapped[near] = false;
        Assist.Target a = first(); int o = objOf(a);
        check(o >= 0 && o != far && d[o] >= 0 && d[o] <= 6, "unexplored ground nearby before a far door (" + d[far] + " steps): " + a);
        // 2. explored: the held key makes its door the objective, before the stairs
        L.visited[near] = L.mapped[near] = true; Assist.reset();
        a = first(); check(objOf(a) == far, "a held iron key's locked door is plan A once the floor is explored, not the stairs: " + a);
        com.shatteredpixel.shatteredpixeldungeon.journal.Notes.remove(iron);
        com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(far, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY);
        // 3. a key lying about: picked up before the stairs; passed over, it is held back a while, not forgotten
        int kc = at(L, d, 10, near);
        L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey(dep), kc).seen = true; Assist.reset();
        a = first(); check(objOf(a) == kc, "a seen key is plan A before the stairs: " + a);
        Assist.passed(a, h.pos, h.pos);
        check(Assist.demoted(kc), "a key passed over is held back a while");
        boolean still = false; for (Assist.Target t : Assist.plan(false, null)) still |= objOf(t) == kc;
        check(still, "a key passed over is still in the list (not dropped for the floor)");
        L.heaps.clear(); Assist.reset();
        // 4. unexplored ground left only far off: that, never the stairs
        L.visited[far] = L.mapped[far] = false;
        a = first(); check(objOf(a) != L.exit() && objOf(a) != L.entrance(), "no stairs while reachable unexplored ground remains: " + a);
        // 5. a tap steers: the next plan follows the cell tapped
        Assist.reset(); L.visited[near] = L.mapped[near] = false;
        first(); Assist.tapped(far);   // (after a list: a new floor forgets the player's way)
        a = first(); check(objOf(a) == far, "a tap on the map: its way is plan A: " + a);
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); Assist.reset(); WatchSettings.itemAssist(true);
        h.pos = pos0;
        System.out.println("places: near ground before a far door, the held key's door before the stairs, keys before the stairs and kept, no stairs with ground left, a tap steers: ok");
    }
    // ---- several options at all times: a room with doors and grass offers them all (owner: "only 2 tiles") ----
    static java.util.List<Assist.Target> screenPlan() throws Exception {
        java.util.List<Assist.Target> l = new ArrayList<>(Assist.plan(false, Controls::onScreenFromHero));
        Method once = Controls.class.getDeclaredMethod("once", java.util.List.class); once.setAccessible(true); once.invoke(null, l);
        return l;
    }
    static void options() throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero; int pos0 = h.pos, W = L.width();
        final int GR = com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.HIGH_GRASS, DOOR = com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR;
        Field rf = com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel.class.getDeclaredField("rooms"); rf.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room> rooms = (java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room>) rf.get(L);
        com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room room = null; int doors = 0;
        for (com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room r : rooms) { int nd = 0; for (Object d : r.connected.values()) if (d != null) nd++;
            if (nd >= 2 && r.width() >= 7 && r.height() >= 6 && !r.inside(L.cellToPoint(L.entrance())) && !r.inside(L.cellToPoint(L.exit())) && nd > doors) { room = r; doors = nd; } }
        check(room != null, "a room with two doors or more");
        // the room alone known (its doors lead on to the unknown), tall grass in two corners, the hero in a third
        L.heaps.clear(); L.mobs.clear(); Arrays.fill(L.visited, false); Arrays.fill(L.mapped, false);
        for (int y = room.top; y <= room.bottom; y++) for (int x = room.left; x <= room.right; x++) { int c = x + y * W;
            if (x > room.left && x < room.right && y > room.top && y < room.bottom) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(c, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY);
            L.visited[c] = true; }
        for (int y = room.top + 1; y <= room.top + 2; y++) for (int x = room.right - 2; x < room.right; x++) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(x + y * W, GR);
        for (int x = room.left + 1; x <= room.left + 2; x++) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(x + (room.top + 1) * W, GR);
        h.pos = (room.right - 2) + (room.bottom - 1) * W;
        int nDoors = 0; for (int y = room.top; y <= room.bottom; y++) for (int x = room.left; x <= room.right; x++) if (L.map[x + y * W] == DOOR || L.map[x + y * W] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.OPEN_DOOR) nDoors++;
        int want = Math.min(4, nDoors + 2);   // (each door, each patch of grass)
        for (int mode = 1; mode <= 2; mode++) {
            WatchSettings.assist(mode); Assist.reset();
            java.util.List<Assist.Target> l = screenPlan();
            System.out.println("options: a room with " + nDoors + " doors and two patches of grass, assist " + mode + ": " + l);
            check(l.size() >= want, "several options: " + nDoors + " doors and two patches of grass give " + want + " entries or more (assist " + mode + "): " + l);
            int grass = 0; for (Assist.Target t : l) if (t.cell >= 0 && L.map[objOf(t)] == GR) grass++;
            check(grass >= 2, "each patch of grass is offered (assist " + mode + "): " + l);
        }
        // passed over: further down the list, not gone
        WatchSettings.assist(1); Assist.reset();
        java.util.List<Assist.Target> l = screenPlan(); Assist.Target a = l.get(0);
        int o = objOf(a); boolean gr = L.map[o] == GR;
        Assist.passed(a, h.pos, h.pos);
        l = screenPlan(); int at = -1; for (int i = 0; i < l.size(); i++) if (objOf(l.get(i)) == o || (gr && L.map[objOf(l.get(i))] == GR)) at = i;
        check(at > 0, "plan A passed over (" + a + ") is still in the list, further down: " + l);
        Assist.Target b = l.get(0);
        check(b.kind == Assist.GO && objOf(b) != o, "and another place is plan A: " + l);
        Assist.passed(b, h.pos, h.pos);
        java.util.List<Assist.Target> l2 = screenPlan(); boolean still = false; for (Assist.Target t : l2) still |= objOf(t) == objOf(b) || (L.map[objOf(b)] == GR && L.map[objOf(t)] == GR);
        check(still && objOf(l2.get(0)) != objOf(b), "the next one passed over is still listed, not first: " + l2 + " first " + a + " then " + l + " bad " + get(null, "bad", Assist.class) + " demoted " + get(null, "demoted", Assist.class) + " hero " + h.pos);
        System.out.println("options: after two passed over: " + l2);
        // an item passed over (not walked towards): never plan A again, still last in the list
        int ic = -1; for (int y = room.top + 1; y < room.bottom && ic < 0; y++) for (int x = room.left + 1; x < room.right && ic < 0; x++) { int c = x + y * W; if (c != h.pos && L.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY && L.distance(c, h.pos) >= 2) ic = c; }
        L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.food.Food(), ic).seen = true; Assist.reset();
        l = screenPlan(); a = l.get(0);
        check(objOf(a) == ic, "an item in the room is plan A: " + l);
        Assist.passed(a, h.pos, h.pos);
        l = screenPlan(); at = -1; for (int i = 0; i < l.size(); i++) if (objOf(l.get(i)) == ic) at = i;
        check(at > 0 && objOf(l.get(0)) != ic, "an item passed over is not plan A again, but still listed: " + l);
        L.heaps.clear(); Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); Assist.reset(); WatchSettings.assist(2);
        h.pos = pos0;
        System.out.println("options: doors and each patch of grass offered, passed over goes down the list (places and items): ok");
    }
    static Object get(Object o, String name, Class<?> c) throws Exception { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }

    // ---- fights: what is passed over in a fight comes back after it; a blow is no pass; foes seen coming are waited for ----
    static Field nowF;
    static void setNow(float t) throws Exception { if (nowF == null) { nowF = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.class.getDeclaredField("now"); nowF.setAccessible(true); } nowF.setFloat(null, t); }
    static com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat rat(int c, boolean hunting) throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat r = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat();
        r.pos = c; r.state = r.HUNTING; GameScene.add(r);
        Dungeon.level.updateFieldOfView(Dungeon.hero, Dungeon.level.heroFOV);
        @SuppressWarnings("unchecked") Map<Integer, Boolean> hm = (Map<Integer, Boolean>) get(null, "huntMem", Assist.class); hm.put(r.id(), hunting);
        return r;
    }
    static void gone(com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob m) { m.destroy(); if (m.sprite != null) m.sprite.killAndErase(); Dungeon.level.updateFieldOfView(Dungeon.hero, Dungeon.level.heroFOV); }
    static Assist.Target entry(java.util.List<Assist.Target> l, int kind, int obj) { for (Assist.Target t : l) if (t.kind == kind && objOf(t) == obj) return t; return null; }
    /** Watch mode: a melee blow's outcome (Hero.onAttackComplete) is worked out as the swing starts, while its animation
     *  still plays (CharSprite.pebxelStrikeAtStart: the watch gets it with its own swing); off, as Shattered: after. */
    static void strikeAtStart(WatchApp app) throws Exception {
        Hero h = Dungeon.hero; com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level;
        Field cur = com.watabou.noosa.MovieClip.class.getDeclaredField("curAnim"); cur.setAccessible(true);
        Field atk = com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.class.getDeclaredField("attack"); atk.setAccessible(true);
        for (boolean early : new boolean[]{true, false}) {
            com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.pebxelStrikeAtStart = early;
            for (int f = 0; f < 600 && !h.ready; f++) step(app);
            int c = -1;
            for (int d : com.watabou.utils.PathFinder.NEIGHBOURS8) if (L.passable[h.pos + d] && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(h.pos + d) == null) { c = h.pos + d; break; }
            check(c >= 0, "a cell beside the hero for the blow");
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll g = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll();
            g.pos = c; g.HP = g.HT = 1000; GameScene.add(g); g.state = g.HUNTING;
            Dungeon.observe(); h.checkVisibleMobs();
            float t0 = now();
            check(h.ready && h.handle(c), "the hero takes the blow");
            h.next();
            boolean swung = false, during = false, after = false;
            for (int f = 0; f < 900; f++) {
                step(app);
                boolean a = cur.get(h.sprite) == atk.get(h.sprite);
                swung |= a;
                if (swung && h.attackTarget() == null) { if (a) during = true; else after = true; break; }
            }
            check(swung, "the hero swung (early " + early + ", ready " + h.ready + ", action " + h.curAction + ", time " + t0 + " -> " + now() + ")");
            check(early ? during : after && !during, early ? "watch mode: the blow's outcome comes while the swing still plays" : "Shattered's way: the outcome as the swing ends");
            for (int f = 0; f < 600 && !h.ready; f++) step(app);
            g.destroy(); g.sprite.killAndErase(); Dungeon.observe(); h.checkVisibleMobs();
        }
        com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.pebxelStrikeAtStart = true;
        System.out.println("strike at start: a blow's outcome as the swing starts in watch mode, as it ends without: ok");
    }
    static void fights(WatchApp app) throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero; int pos0 = h.pos, W = L.width();
        float t00 = now();
        Field rf = com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel.class.getDeclaredField("rooms"); rf.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room> rooms = (java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room>) rf.get(L);
        com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room room = null; com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room.Door door = null;
        for (com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room r : rooms) {
            if (r.width() < 7 || r.height() < 7 || r.inside(L.cellToPoint(L.entrance())) || r.inside(L.cellToPoint(L.exit()))) continue;
            for (com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room.Door d : r.connected.values()) if (d != null && door == null) { room = r; door = d; }
            if (room != null) break;
        }
        check(room != null, "a room with a door for the fights");
        // a dead end: the room's other doors walled up, the room alone known, the floor plain
        L.heaps.clear(); L.mobs.clear(); Arrays.fill(L.visited, false); Arrays.fill(L.mapped, false);
        for (com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room.Door d : room.connected.values()) if (d != null && d != door) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(d.x + d.y * W, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.WALL);
        int dc = door.x + door.y * W; com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(dc, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR);
        for (int y = room.top; y <= room.bottom; y++) for (int x = room.left; x <= room.right; x++) { int c = x + y * W;
            if (x > room.left && x < room.right && y > room.top && y < room.bottom) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(c, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY);
            L.visited[c] = true; }
        com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(dc, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR);
        // the hero in the corner farthest from the door
        int far = -1; for (int y = room.top + 1; y < room.bottom; y++) for (int x = room.left + 1; x < room.right; x++) { int c = x + y * W; if (far < 0 || L.distance(c, dc) > L.distance(far, dc)) far = c; }
        h.pos = far; L.updateFieldOfView(h, L.heroFOV);
        WatchSettings.assist(2); WatchSettings.itemAssist(false); Assist.reset();
        // 1. gold passed over in a fight is held back only till it's over (a kill clearing the threat, or calm a while)
        int gc = -1, rc = -1;
        for (int y = room.top + 1; y < room.bottom; y++) for (int x = room.left + 1; x < room.right; x++) { int c = x + y * W; int d = L.distance(c, h.pos);
            if (gc < 0 && d == 2) gc = c; }
        for (int y = room.top + 1; y < room.bottom; y++) for (int x = room.left + 1; x < room.right; x++) { int c = x + y * W;
            if (rc < 0 && L.distance(c, h.pos) == 4 && L.distance(c, gc) >= 2 && L.heroFOV[c]) rc = c; }
        check(gc >= 0 && rc >= 0, "cells for the gold and the gnoll");
        L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.Gold(20), gc).seen = true;
        for (int round = 0; round < 2; round++) {
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat r = rat(rc, round == 0);   // (the second wanders off: not coming)
            java.util.List<Assist.Target> l = Assist.plan(false, null); Assist.Target g = entry(l, Assist.PICKUP, gc);
            check(g != null, "the gold is listed in the fight: " + l);
            Assist.passed(g, h.pos, h.pos);   // (something else done: a step back, a wait)
            check(Assist.heldInFight(gc) && !Assist.skipped(gc), "gold passed over in a fight is held back for the fight, not for the floor");
            if (round == 0) gone(r);   // (the kill: Mob.destroy counts it)
            else { Dungeon.level.mobs.remove(r); com.shatteredpixel.shatteredpixeldungeon.actors.Actor.remove(r); r.sprite.killAndErase(); Dungeon.level.updateFieldOfView(h, Dungeon.level.heroFOV); }   // (gone off, no kill)
            if (round == 1) { Assist.Target a = first(); check(objOf(a) != gc, "the gnoll just gone from sight: the gold still held back: " + a); setNow(now() + Assist.CALM); }
            Assist.Target a = first();
            check(objOf(a) == gc && !Assist.heldInFight(gc), "the fight over (" + (round == 0 ? "a kill" : "calm") + "): the gold is plan A again: " + a);
        }
        // 2. a blow struck is no pass: chosen from the list past the gold, or a tap on the foe beside him
        int ac = -1; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = h.pos + k; if (ac < 0 && L.passable[c] && L.distance(c, gc) >= 1 && c != gc && room.inside(L.cellToPoint(c))) ac = c; }
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat r = rat(ac, true);
        java.util.List<Assist.Target> l = Assist.plan(false, null); Assist.Target g = entry(l, Assist.PICKUP, gc), e = entry(l, Assist.ENEMY, ac);
        check(g != null && e != null, "the gold and the gnoll beside him listed: " + l);
        Assist.chose(e, l.get(0)); Assist.passed(g, e.cell, h.pos);   // (as Controls.select: the entries cycled past on the way)
        check(!Assist.heldInFight(gc) && !Assist.skipped(gc), "attacking from the list is no pass over the gold");
        Assist.passed(g, h.pos, h.pos);   // (a tap on the foe: he stays put and strikes)
        check(!Assist.heldInFight(gc) && !Assist.skipped(gc), "a blow struck by a tap is no pass over the gold");
        gone(r); L.heaps.clear(); setNow(now() + 5); Assist.reset();
        // 3. a dead end: foes seen coming from the door's way, now out of sight: wait for them, don't walk out to them
        L.updateFieldOfView(h, L.heroFOV);
        Assist.Target a0 = first();
        check(a0 != null && a0.kind == Assist.GO, "the dead end alone: plan A walks out to explore: " + a0);
        int out = -1; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = dc + k; if (out < 0 && L.passable[c] && !room.inside(L.cellToPoint(c)) && L.map[c] != com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.DOOR) out = c; }
        check(out >= 0 && !L.heroFOV[out], "a cell past the door, out of sight");
        r = rat(dc, true); first();   // (seen at the door, coming)
        r.pos = out; L.updateFieldOfView(h, L.heroFOV); check(!L.heroFOV[out], "the gnoll out of sight past the door");
        setNow(now() + 1);
        Assist.Target a = first();
        check(Assist.threats() > 0 && a != null && a.kind == Assist.WAIT && a.why.startsWith("hold") && Assist.WAIT_ACT.equals(a.action), "foes seen coming beyond the dead end's door: plan A waits, not out: " + a + " list " + Assist.plan(false, null));
        boolean also = false; for (Assist.Target t : Assist.plan(false, null)) also |= t.kind == Assist.GO;
        check(also, "the way out is still offered further down");
        setNow(now() + L.distance(h.pos, dc) + 8);   // (it had time to come and didn't)
        a = first();
        check(a != null && !a.why.startsWith("hold"), "the threat had time to come and didn't: plan A explores again: " + a);
        gone(r);
        setNow(t00); Assist.reset();
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); WatchSettings.itemAssist(true);
        h.pos = pos0; L.updateFieldOfView(h, L.heroFOV);
        // 4. the hands' item flies to the Select tag: from the backpack for a bag item, from its quickslot for a slot item
        oc = new Controls(); WatchSettings.assist(1); Assist.reset(); h.HP = h.HT; h.buff(Hunger.class).satisfy(1000);
        com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength str = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength(); str.identify();
        h.belongings.backpack.items.add(str);
        int f0 = pebxel.ui.WatchHud.flights;
        a = planA(app, -1, -1); frames(app, 3);
        check(a != null && a.item == str, "a known Strength potion is suggested: " + a);
        check(pebxel.ui.WatchHud.flights > f0 && pebxel.ui.WatchHud.flyFrom == pebxel.ui.WatchHud.TB_BAG, "a bag item's suggestion flies from the backpack to the Select tag (" + pebxel.ui.WatchHud.flights + " from " + pebxel.ui.WatchHud.flyFrom + ")");
        h.belongings.backpack.items.remove(str);
        com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience xp = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience(); xp.identify();
        h.belongings.backpack.items.add(xp); Dungeon.quickslot.setSlot(0, xp);
        Field st = Controls.class.getDeclaredField("targetsStale"); st.setAccessible(true); st.set(oc, true);
        f0 = pebxel.ui.WatchHud.flights; frames(app, 5);
        check(oc != null && list().size() > 0 && list().get(0).item == xp, "a quickslot's potion is suggested: " + list());
        check(pebxel.ui.WatchHud.flights > f0 && pebxel.ui.WatchHud.flyFrom == pebxel.ui.WatchHud.TB_Q[0], "a slot item's suggestion flies from its quickslot (" + pebxel.ui.WatchHud.flyFrom + ")");
        h.belongings.backpack.items.remove(xp); Dungeon.quickslot.clearSlot(0); st.set(oc, true); frames(app, 3);
        WatchSettings.assist(2); oc = null; Assist.reset();
        System.out.println("fights: gold passed over in a fight back after it (a kill, calm), a blow is no pass, foes seen coming past a dead end's door waited for (till they had time to come), the hands' item flies from the bag / its slot: ok");
    }

    /** A tier-2 point spent with the watch's buttons alone (Thomas: "I can't spend tier 2 talents. The description takes
     *  up the whole screen and there's no button to apply the talent point or way to scroll"): the talent page, Down to
     *  a tier-2 talent with a long description, Select opens it, Down reads it, Select upgrades it by one. */
    static void talentSpent(WatchApp app) throws Exception {
        Hero h = Dungeon.hero; oc = new Controls();
        int lvl = h.lvl; h.lvl = 9; h.exp = 0;
        com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent t = com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.RECALL_INSCRIPTION;
        LinkedHashMap<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> was = h.talents.get(1), tier2 = new LinkedHashMap<>();
        tier2.put(t, 0); for (Map.Entry<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> e : was.entrySet()) if (tier2.size() < was.size()) tier2.put(e.getKey(), e.getValue());
        h.talents.set(1, tier2);
        while (Ui.topWindow() != null) { Ui.topWindow().hide(); frames(app, 1); }
        pebxel.ui.WndWatchHero.openTalents(); frames(app, 3);
        Window page = Ui.topWindow();
        Field tf = com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton.class.getDeclaredField("talent"); tf.setAccessible(true);
        for (int i = 0; i < 20 && !(oc.focused() instanceof com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton && tf.get(oc.focused()) == t); i++) { oc.button(Controls.DOWN); frames(app, 1); }
        check(oc.focused() instanceof com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton && tf.get(oc.focused()) == t, "Down reaches the tier-2 talent");
        oc.button(Controls.SELECT); frames(app, 3);
        check(Ui.topWindow() instanceof pebxel.ui.WndWatchTalent, "Select opens the watch's talent window: " + Ui.topWindow());
        pebxel.ui.WndWatchTalent w = (pebxel.ui.WndWatchTalent) Ui.topWindow();
        check(w.scrolled()[2] > w.scrolled()[1], "a long description (it scrolls)");
        for (int i = 0; i < 3; i++) { oc.button(Controls.DOWN); frames(app, 1); }
        check(w.scrolled()[0] > 0 && Ui.topWindow() == w, "Down scrolls the description, the window stays");
        oc.button(Controls.SELECT); frames(app, 3);
        check(h.pointsInTalent(t) == 1, "Select upgrades the talent by one: " + h.pointsInTalent(t));
        check(Ui.topWindow() == page, "back on the talent page");
        // the same by finger: a tap opens it (at the top, its button in sight), a drag scrolls it, a tap on the button spends
        com.watabou.noosa.Gizmo tb = null; for (Ui.Part p : Ui.parts(page)) if (p.g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton && tf.get(p.g) == t) tb = p.g;
        oc.touched(); Ui.press(tb); frames(app, 3);
        w = (pebxel.ui.WndWatchTalent) Ui.topWindow();
        w.scrollBy(-200); check(w.scrolled()[0] > 0, "a drag scrolls the description");
        Ui.press(w.focused()); frames(app, 3);
        check(h.pointsInTalent(t) == 2, "a tap on the button upgrades it again: " + h.pointsInTalent(t));
        while (Ui.topWindow() != null) { Ui.topWindow().hide(); frames(app, 1); }
        h.talents.set(1, was); h.lvl = lvl; oc = null;
        System.out.println("talents: a tier-2 point spent with Down and Select (and by finger), the description scrolled: ok");
    }

    /** A strong foe (a gnoll with 1000 health, hunting) at c, in sight. */
    static com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll brute(int c) throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll g = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll();
        g.pos = c; g.HP = g.HT = 1000; g.state = g.HUNTING; GameScene.add(g);
        Dungeon.level.updateFieldOfView(Dungeon.hero, Dungeon.level.heroFOV);
        @SuppressWarnings("unchecked") Map<Integer, Boolean> hm = (Map<Integer, Boolean>) get(null, "huntMem", Assist.class); hm.put(g.id(), true);
        return g;
    }
    static Assist.Target ab(boolean urgent) throws Exception {
        Method setup = Assist.class.getDeclaredMethod("setup", java.util.function.IntPredicate.class);
        setup.setAccessible(true); setup.invoke(null, new Object[]{null});
        AsAbilities.prepAt = -1; AsAbilities.ban.clear();
        return urgent ? AsAbilities.urgent(null) : AsAbilities.suggest(null);
    }

    static Assist.Target it(boolean urgent) throws Exception {
        Method setup = Assist.class.getDeclaredMethod("setup", java.util.function.IntPredicate.class);
        setup.setAccessible(true); setup.invoke(null, new Object[]{null});
        AsAbilities.prepAt = -1; AsAbilities.ban.clear(); AsItems.prepAt = -1; AsItems.ban.clear(); AsItems.tries.clear();
        return urgent ? AsItems.urgent0(null) : AsItems.suggest0(null);
    }
    /** The consumables (AsItems): the staff imbued with a better known-uncursed wand only, an unknown potion never used
     *  by its kind, Corruption only when its sum says it takes, a bomb on held strong foes out of the blast, trap ranks,
     *  no fire beside grass. */
    static void items() throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero;
        Gallery.calm(); h.HP = h.HT = 100; WatchSettings.assist(2); WatchSettings.itemAssist(true); Assist.reset();
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); L.updateFieldOfView(h, L.heroFOV);
        h.belongings.backpack.items.clear();
        com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon w0 = h.belongings.weapon;
        // 1. the staff: a known-uncursed Lightning goes in; one whose curse isn't known doesn't
        MagesStaff st = new MagesStaff(new com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile()); st.identify(); h.belongings.weapon = st;
        com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning wl = new com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning();
        wl.cursed = false; wl.cursedKnown = false; h.belongings.backpack.items.add(wl);
        Assist.Target t = it(false);
        check(t == null || t.item != st, "a wand whose curse isn't known is not imbued: " + t);
        wl.cursedKnown = true; t = it(false);
        check(t != null && t.item == st && MagesStaff.AC_IMBUE.equals(t.action) && t.on == wl, "the staff imbued with a known-uncursed Lightning: " + t);
        h.belongings.backpack.items.clear(); h.belongings.weapon = w0;
        // 2. an unknown potion is no known one
        com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation lv = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation();
        h.belongings.backpack.items.add(lv); AsItems.prep();
        check(lv.isKnown() ? AsItems.has(lv.getClass()) == lv : AsItems.has(lv.getClass()) == null, "an unknown potion is never picked by its kind");
        h.belongings.backpack.items.clear();
        // 3. Corruption's sum: a rat at full health resists a +0 wand, nearly dead it doesn't
        int[] wd = walks(L, h.pos); int far = -1, far2 = -1;
        for (int c = 0; c < wd.length; c++) if (com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null && L.heroFOV[c] && L.passable[c] && wd[c] >= 4 && wd[c] <= 6 && L.distance(c, h.pos) >= 4 && Assist.line(h.pos, c)) {
            if (far < 0) far = c; else if (far2 < 0 && L.distance(c, far) == 1 && Assist.line(h.pos, c)) far2 = c; }
        check(far >= 0, "a cell for the foes");
        com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorruption wc = new com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorruption(); wc.identify();
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat r = rat(far, true);
        check(!AsItems.corruptSure(r, wc), "a rat at full health resists Corruption +0");
        r.HP = 1; check(AsItems.corruptSure(r, wc), "a rat at 1 health is corrupted for sure");
        gone(r);
        // 4. a bomb: two strong foes rooted together, far off: thrown lit; next to the hero: not
        if (far2 >= 0) {
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll a = brute(far), b = brute(far2);
            com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(a, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Roots.class, 10f);
            com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(b, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Roots.class, 10f);
            com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb bomb = new com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb(); h.belongings.backpack.items.add(bomb);
            t = it(true);
            check(t != null && t.item == bomb && "LIGHTTHROW".equals(t.action) && L.distance(t.cell, h.pos) >= 3, "a lit bomb on two rooted strong foes: " + t + " (near " + AsItems.near.size() + " hard " + AsItems.hard + ")");
            gone(a); gone(b); h.belongings.backpack.items.clear();
        } else System.out.println("items: no two cells side by side for the bomb check (skipped)");
        // 5. traps by danger; fire by grass
        check(AsItems.trapRank(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap()) > AsItems.trapRank(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap())
                && AsItems.trapRank(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap()) > AsItems.trapRank(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.AlarmTrap()), "trap ranks: Grim worse than Toxic worse than Alarm");
        int m0 = L.map[h.pos]; com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(h.pos, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.GRASS);
        check(!AsItems.fireOk(), "no Fireblast standing on grass");
        com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(h.pos, m0);
        Gallery.calm(); Assist.reset();
        System.out.println("items: the staff imbued (known uncursed only), unknown potions kept unknown, Corruption's sum, a bomb on held foes, trap ranks, no fire on grass: ok");
    }

    /** The clues (agent ID: Clues, Identify): a bat's potion is Healing and drunk as one; a pool room on the floor makes
     *  a potion colour lying there likelier Invisibility; no unknown scroll read with nothing to gain (one is, with a
     *  cursed armour on); a prize past a chasm taken with the potion believed Levitation. */
    @SuppressWarnings("unchecked")
    static void clues() throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero;
        Gallery.calm(); h.HP = h.HT = 100; WatchSettings.assist(2); WatchSettings.itemAssist(true); Assist.reset();
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); L.updateFieldOfView(h, L.heroFOV);
        h.belongings.backpack.items.clear();
        Field hf = com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.class.getDeclaredField("handler"); hf.setAccessible(true);
        Field sf = com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.class.getDeclaredField("handler"); sf.setAccessible(true);
        Set<Class<?>> pk = (Set<Class<?>>) ((com.shatteredpixel.shatteredpixeldungeon.items.ItemStatusHandler<?>) hf.get(null)).known();
        Set<Class<?>> sk = (Set<Class<?>>) ((com.shatteredpixel.shatteredpixeldungeon.items.ItemStatusHandler<?>) sf.get(null)).known();
        Set<Class<?>> pk0 = new HashSet<>(pk), sk0 = new HashSet<>(sk);
        Class<?> HEAL = com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class, INV = com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility.class,
                MV = com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfMindVision.class, LEV = com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation.class;
        pk.remove(HEAL); pk.remove(INV); pk.remove(MV); pk.remove(LEV);
        try {
            int[] wd = walks(L, h.pos); int c1 = -1;
            for (int c = 0; c < wd.length; c++) if (wd[c] == 1 && L.heaps.get(c) == null && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null) { c1 = c; break; }
            check(c1 >= 0, "a cell beside the hero");
            // 1. a bat killed beside the hero drops a potion: Healing, drunk as one when low
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat bat = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat(); bat.pos = c1; bat.state = bat.HUNTING; GameScene.add(bat);
            L.updateFieldOfView(h, L.heroFOV);
            hands();
            gone(bat);
            com.shatteredpixel.shatteredpixeldungeon.items.Heap hp = L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing(), c1); hp.seen = true;
            hands();
            double ph = pebxel.bot.Identify.p(HEAL, HEAL);
            check(ph >= 0.9, "a bat's potion is believed Healing: " + ph);
            hp.destroy();
            Item mine = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing(); h.belongings.backpack.items.add(mine);
            check(Clues.healing() == mine && Clues.about(mine).contains("bat"), "the bat's potion is the Healing to drink, the why says so: " + Clues.about(mine));
            h.HP = Math.max(2, h.HT / 6); com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll r = brute(c1);
            Assist.Target t = hands();
            check(t != null && t.item == mine && t.why.contains("bat"), "low beside a strong gnoll: the bat's potion drunk as Healing: " + t);
            gone(r); h.HP = 100; h.belongings.backpack.items.clear();
            // 2. a pool room seen: a colour lying on this floor likelier Invisibility, one never seen less
            Assist.reset(); hands();
            double i0 = pebxel.bot.Identify.p(INV, INV), m0 = pebxel.bot.Identify.p(MV, INV);
            int c2 = -1; for (int c = 0; c < wd.length; c++) if (wd[c] >= 2 && wd[c] <= 5 && L.heaps.get(c) == null && L.heroFOV[c]) { c2 = c; break; }
            check(c2 >= 0, "a cell in sight for a potion");
            com.shatteredpixel.shatteredpixeldungeon.items.Heap hi = L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility(), c2); hi.seen = true;
            hands();
            Clues.room(new com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.PoolRoom()); Clues.explored(false);
            double i1 = pebxel.bot.Identify.p(INV, INV), m1 = pebxel.bot.Identify.p(MV, INV);
            check(i1 > i0 * 1.5 && i1 > 0.5 && m1 < m0, String.format("a pool room on the floor: the colour lying there Invisibility %.2f -> %.2f, one not seen %.2f -> %.2f", i0, i1, m0, m1));
            hi.destroy();
            // 3. an unknown scroll: not read with nothing to gain; read with a cursed armour worn (Remove Curse)
            sk.add(com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade.class);
            sk.remove(com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse.class); sk.remove(com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby.class);
            for (Item i : h.belongings) i.identify();
            com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby lu = new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby(); h.belongings.backpack.items.add(lu);
            Assist.reset(); t = hands();
            check(t == null || t.item != lu, "an unknown scroll isn't read with nothing to gain: " + t);
            Armor a0 = h.belongings.armor; Armor ca = new LeatherArmor(); ca.cursed = true; ca.cursedKnown = true; h.belongings.armor = ca;
            t = hands();
            check(t != null && t.item == lu && Scroll().equals(t.action), "a cursed armour worn: the unknown scroll is read (it may be Remove Curse): " + t);
            h.belongings.armor = a0; h.belongings.backpack.items.clear();
            // 4. a prize past a chasm, the potion believed Levitation (no known one): drunk for it
            int c3 = -1;
            for (int c = 0; c < wd.length && c3 < 0; c++) if (wd[c] == 3 && L.heroFOV[c] && L.heaps.get(c) == null) { boolean ok = true;
                for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k; if (n < 0 || n >= L.length() || !L.passable[n] || L.heaps.get(n) != null || n == h.pos || wd[n] < 1 || L.map[n] != L.map[c]) ok = false; }
                if (ok) c3 = c; }
            if (c3 >= 0) {
                int[] keep = new int[8]; int j = 0;
                for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { keep[j++] = L.map[c3 + k]; com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(c3 + k, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.CHASM); GameScene.updateMap(c3 + k); }
                com.shatteredpixel.shatteredpixeldungeon.items.Heap hz = L.drop(new com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy(), c3); hz.seen = true;
                Item lp = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation(); h.belongings.backpack.items.add(lp);
                double[] like = new double[12]; Arrays.fill(like, 0.02); like[pebxel.bot.Identify.pos(pebxel.bot.Identify.POT, LEV)] = 1; pebxel.bot.Identify.like(LEV, like, "the traps room on this floor");
                L.updateFieldOfView(h, L.heroFOV);
                t = it(false);
                check(t != null && t.item == lp && t.why.contains("prize past the traps") && t.why.contains("likely Levitation"), "a prize past a chasm: the believed Levitation drunk for it: " + t);
                hz.destroy(); j = 0; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(c3 + k, keep[j++]); GameScene.updateMap(c3 + k); }
                AsItems.cross = null; h.belongings.backpack.items.clear();
            } else System.out.println("clues: no open 3x3 for the chasm check (skipped)");
        } finally { pk.clear(); pk.addAll(pk0); sk.clear(); sk.addAll(sk0); }
        Gallery.calm(); Assist.reset();
        System.out.println("clues: a bat's potion drunk as Healing, a pool room's Invisibility, no scroll read for nothing (one for a curse), a believed Levitation for a prize: ok");
    }
    static String Scroll() { return com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ; }

    /** The talents and abilities (AsAbilities): a meal talent eats before a fight, a Duelist's charged ability on a
     *  strong foe, Berserk when low beside one, the King's Crown put on. */
    static void abilities() throws Exception {
        com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; Hero h = Dungeon.hero;
        Gallery.calm(); h.HP = h.HT = 100; WatchSettings.assist(2); WatchSettings.itemAssist(true); Assist.reset();
        Arrays.fill(L.visited, true); Arrays.fill(L.mapped, true); L.updateFieldOfView(h, L.heroFOV);
        h.belongings.backpack.items.clear();
        com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass cls0 = h.heroClass; com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass sub0 = h.subClass;
        {   // (a room big enough for a foe 5 steps off in sight)
            Field rf = com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel.class.getDeclaredField("rooms"); rf.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room> rooms = (java.util.List<com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room>) rf.get(L);
            int W = L.width();
            for (com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room rm : rooms) if (rm.width() >= 9 && rm.height() >= 4) {
                for (int y = rm.top + 1; y < rm.bottom; y++) for (int x = rm.left + 1; x < rm.right; x++) { int c = x + y * W; if (L.map[c] != com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.WALL) com.shatteredpixel.shatteredpixeldungeon.levels.Level.set(c, com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY); }
                h.pos = rm.left + 1 + (rm.top + 2) * W; break;
            }
            L.updateFieldOfView(h, L.heroFOV);
        }
        int[] wd = walks(L, h.pos); int far = -1, near = -1;
        for (int c = 0; c < wd.length; c++) {
            if (com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) != null || !L.heroFOV[c] || !L.passable[c]) continue;
            if (far < 0 && wd[c] >= 4 && wd[c] <= 6 && L.distance(c, h.pos) >= 4 && Assist.line(h.pos, c)) far = c;
            if (near < 0 && wd[c] == 1) near = c;
        }
        if (far < 0) for (int c = 0; c < wd.length; c++) if (far < 0 && wd[c] >= 4 && wd[c] <= 8 && L.passable[c] && L.heroFOV[c] && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null) far = c;
        int fov = 0; for (boolean b : L.heroFOV) if (b) fov++;
        check(far >= 0 && near >= 0, "cells for the foes (far " + far + " near " + near + ", in sight " + fov + ", hero " + h.pos + ")");
        // 1. Strengthening Meal (the Duelist's, given the Warrior here), hungry, a strong foe coming: eat first
        LinkedHashMap<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> t1 = h.talents.get(0);
        t1.put(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.STRENGTHENING_MEAL, 2);
        h.buff(Hunger.class).satisfy(1000); h.buff(Hunger.class).affectHunger(-Hunger.HUNGRY);
        Food ration = new Food(); h.belongings.backpack.items.add(ration);
        Assist.Target t = ab(true);
        check(t == null, "no fight coming: no meal for the talent: " + t);
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll g = brute(far);
        t = ab(true);
        check(AsAbilities.mealWhy() != null && t != null && t.item == ration && Food.AC_EAT.equals(t.action), "a meal talent, a strong foe coming: eat before the fight: " + t + " (why " + AsAbilities.mealWhy() + ", foe walk " + Assist.walk(far) + " near " + AsAbilities.near + " strong " + AsAbilities.anyStrong + " hunger " + Assist.hunger() + ")");
        h.buff(Hunger.class).satisfy(1000);
        t = ab(true);
        check(t == null || t.item != ration, "not hungry: the ration isn't wasted on it: " + t);
        com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty horn = new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty();
        Field hc = com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact.class.getDeclaredField("charge"); hc.setAccessible(true); hc.setInt(horn, 3);
        h.belongings.artifact = horn; horn.activate(h);
        t = ab(true);
        check(t != null && t.item == horn && com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty.AC_SNACK.equals(t.action), "with the Horn of Plenty: a snack before the fight: " + t);
        check(AsAbilities.mealWorth() > 0, "the Horn makes the meal talent worth more: " + AsAbilities.mealWorth());
        double withHorn = Build.boost(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.HEARTY_MEAL, h);
        h.belongings.artifact = null; Build.ctxKey = null;
        double noHorn = Build.boost(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.HEARTY_MEAL, h);
        check(withHorn > noHorn, "the planner: a Horn of Plenty raises the meal talents (" + withHorn + " vs " + noHorn + ")");
        {   // (with a point to spend: the guide's Iron Will first, with the Horn the meal first)
            int lvl0 = h.lvl; h.lvl = 2; LinkedHashMap<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> keep = new LinkedHashMap<>(t1); for (com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent k : t1.keySet()) t1.put(k, 0);
            t1.remove(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.STRENGTHENING_MEAL);
            com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent a1 = Build.nextTalent(h);
            h.belongings.artifact = horn; Build.ctxKey = null;
            com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent a2 = Build.nextTalent(h);
            check(a1 == com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.IRON_WILL && a2 == com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.HEARTY_MEAL, "the planner: Iron Will first, with a Horn Hearty Meal first (" + a1 + ", " + a2 + ")");
            t1.clear(); t1.putAll(keep); h.lvl = lvl0;
        }
        h.belongings.artifact = null; t1.remove(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.STRENGTHENING_MEAL); h.belongings.backpack.items.clear();
        gone(g);
        // 2. the Duelist with a charge, a strong foe beside her: the weapon's ability
        h.heroClass = com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass.DUELIST; h.STR = 18;
        com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword sw = new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword(); sw.identify();
        h.belongings.weapon = sw;
        com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon.Charger ch = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(h, com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon.Charger.class);
        ch.charges = 2;
        g = brute(near);
        t = ab(true);
        check(t != null && t.item == sw && com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon.AC_ABILITY.equals(t.action) && t.cell == near, "a Duelist with charge, a strong foe beside: the weapon's ability on it: " + t);
        ch.charges = 0; ch.partialCharge = 0;
        t = ab(true);
        check(t == null || !com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon.AC_ABILITY.equals(t.action), "no charge: no ability: " + t);
        ch.detach(); h.heroClass = cls0;
        // 3. the Berserker at full rage, low, a strong foe beside: Berserk; at full health against a weak one: not
        com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor arm = new ClothArmor(); arm.affixSeal(new com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal()); h.belongings.armor = arm;
        com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield ws = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(h, com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield.class); ws.setArmor(arm);
        h.subClass = com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.BERSERKER;
        com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk bz = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(h, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk.class);
        Field pw = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk.class.getDeclaredField("power"); pw.setAccessible(true); pw.setFloat(bz, 1f);
        h.HP = 30;
        t = ab(true);
        check(t != null && "ab:berserk".equals(t.action), "full rage, low, a strong foe beside: Berserk: " + t);
        check(Assist.act(t), "Berserk done");
        check(!"NORMAL".equals(get(bz, "state").toString()), "berserking now");
        gone(g); bz.detach(); ws.detach(); h.subClass = sub0; h.HP = 100;
        com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk b2 = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(h, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk.class); pw.setFloat(b2, 1f);
        h.subClass = com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.BERSERKER; ws = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(h, com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield.class); ws.setArmor(arm);
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat r = rat(near, true);
        t = ab(true);
        check(t == null || !"ab:berserk".equals(t.action), "full health, a rat beside: no Berserk yet: " + t);
        gone(r); b2.detach(); ws.detach(); h.subClass = sub0;
        // 4. the King's Crown, nothing about: put on
        com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown crown = new com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown(); h.belongings.backpack.items.add(crown);
        t = ab(false);
        check(t != null && t.item == crown && "WEAR".equals(t.action), "the King's Crown is put on: " + t);
        h.belongings.backpack.items.clear(); h.belongings.weapon = null; h.belongings.armor = new ClothArmor();
        Gallery.calm(); Assist.reset();
        System.out.println("abilities: a meal before a fight (a ration when hungry, the Horn's snack), a Duelist's ability on a strong foe, Berserk when low (not at full health), the Crown put on: ok");
    }

    static Assist.Target arts() throws Exception {
        Method setup = Assist.class.getDeclaredMethod("setup", java.util.function.IntPredicate.class);
        setup.setAccessible(true); setup.invoke(null, new Object[]{null});
        Assist.Target u = AsArtifacts.urgent(null); return u != null ? u : AsArtifacts.suggest(null);
    }
    /** AsArtifacts: an identified useful ring is put on, an unknown one that may be cursed isn't; the Chalice isn't
     *  pricked hurt (and is at full health); the Horn is eaten from when hungry. */
    static void artifacts(Hero h) throws Exception {
        WatchSettings.assist(2); Assist.reset();
        h.HP = h.HT = 100; h.buff(Hunger.class).satisfy(1000);
        com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring might = new com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight();
        might.identify(); h.belongings.backpack.items.add(might);
        Assist.Target t = arts();
        check(t != null && t.item == might && com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem.AC_EQUIP.equals(t.action), "an identified Ring of Might is put on: " + t);
        if (System.getenv("RINGS") != null) for (Class<?> c : com.shatteredpixel.shatteredpixeldungeon.items.Generator.Category.RING.classes) {   // (calibration: each kind's worth here)
            com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring r = (com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring) c.getDeclaredConstructor().newInstance(); r.identify();
            System.out.println("ring worth " + c.getSimpleName() + " " + AsArtifacts.worth(r)); }
        h.belongings.backpack.items.clear();
        com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring unk = new com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion();
        h.belongings.backpack.items.add(unk);
        t = arts(); check(t == null || t.item != unk, "an unknown ring that may be cursed isn't put on without a Remove Curse: " + t);
        h.belongings.backpack.items.clear();
        com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood ch = new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood();
        ch.identify(); h.belongings.misc = ch; ch.activate(h);
        h.HP = 30; t = arts();
        check(t == null || t.item != ch, "the Chalice isn't pricked hurt: " + t);
        h.HP = 100; t = arts();
        check(t != null && t.item == ch && "PRICK".equals(t.action), "the Chalice is pricked at full health, safe: " + t);
        ch.doUnequip(h, false); h.belongings.misc = null;
        com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty horn = new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty();
        horn.identify(); h.belongings.misc = horn; horn.activate(h); horn.charge(h, 12);
        t = arts(); check(t == null || t.item != horn || !"EAT".equals(t.action), "not hungry: no eating from the horn: " + t);
        h.buff(Hunger.class).affectHunger(-Hunger.HUNGRY - 10);
        t = arts(); check(t != null && t.item == horn && ("EAT".equals(t.action) || "SNACK".equals(t.action)), "hungry: eat from the horn: " + t);
        horn.doUnequip(h, false); h.belongings.misc = null;
        h.buff(Hunger.class).satisfy(1000); h.belongings.backpack.items.clear(); h.HP = h.HT;
    }
    public static void main(String[] args) {
        try { run(); } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception {
        Game.version = "4.0.2"; Game.versionCode = 922;
        Screen.set(System.getenv().getOrDefault("SCREEN", "emery"));
        String dir = ".pebxel-refinement-" + ProcessHandle.current().pid();
        WatchGame game = new WatchGame(); game.drawAlways = true;
        WatchApp app = new WatchApp(game, dir, true);
        SPDSettings.set(app.getPreferences(SPDSettings.DEFAULT_PREFS_FILE));
        FileUtils.setDefaultFileProperties(Files.FileType.External, dir + "/");
        app.create(); app.graphics.soft.raster = false;
        SPDSettings.intro(false); GamesInProgress.selectedClass = HeroClass.WARRIOR; GamesInProgress.curSlot = 1;
        Dungeon.hero = null; Dungeon.daily = Dungeon.dailyReplay = false; Dungeon.initSeed(); Dungeon.seed = 42;
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND; Game.switchScene(InterlevelScene.class);
        for (int f = 0; f < 5000; f++) {
            step(app); if (Game.scene() instanceof InterlevelScene) HandoffTest.pressOn();
            if (Game.scene() instanceof GameScene && Dungeon.hero != null && Dungeon.hero.ready) break;
        }
        check(Game.scene() instanceof GameScene, "game ready");
        while (Ui.topWindow() != null) Ui.topWindow().hide();
        if (System.getenv("FITONLY") != null) { Gallery.calm(); oc = new Controls(); WindowFit.check(() -> frames(app, 1), oc); talentSpent(app); System.exit(0); }   // (quick: the windows only)
        Gallery.calm(); Hero h = Dungeon.hero; h.belongings.backpack.items.clear();
        h.HP = 40; h.HT = 100; h.buff(Hunger.class).satisfy(1000);
        WatchSettings.assist(2); Assist.reset();
        check(Assist.REST_ACT.equals(hands().action), "hurt hero rests on an unlocked floor");
        Dungeon.level.locked = true;
        Assist.Target t = hands(); check(t == null || !Assist.REST_ACT.equals(t.action), "never rest in a locked fight");
        Dungeon.level.locked = false; h.HP = 100;
        h.buff(Hunger.class).affectHunger(-Hunger.STARVING);
        Item raw = new MysteryMeat(), cooked = new ChargrilledMeat();
        h.belongings.backpack.items.add(raw); h.belongings.backpack.items.add(cooked);
        check(hands().item == cooked, "starving: other food before raw meat");
        h.buff(Hunger.class).satisfy(1000); h.belongings.backpack.items.clear();
        h.STR = 13; Armor hidden = new MailArmor(); hidden.upgrade(); hidden.levelKnown = false;
        h.belongings.backpack.items.add(hidden);
        t = hands(); check(t == null || t.item != hidden, "unknown upgrade must not reveal lower STR requirement");
        hidden.levelKnown = true; check(hands().item == hidden, "known fitting mail is offered");
        h.belongings.backpack.items.clear();
        h.belongings.weapon = new MagesStaff(); h.belongings.weapon.identify();
        ScrollOfUpgrade up = new ScrollOfUpgrade(); up.identify(); h.belongings.backpack.items.add(up);
        t = hands(); check(t != null && t.item == up && t.on == h.belongings.weapon, "Mage invests in the staff first");
        h.belongings.backpack.items.clear();
        artifacts(h);
        obedience(app);
        buttons(app);
        places();
        options();
        fights(app);
        strikeAtStart(app);
        oc = new Controls(); WindowFit.check(() -> frames(app, 1), oc); oc = null;
        talentSpent(app);
        chestFromBeside();
        abilities();
        items();
        clues();

        Link link = new Link(new Assets()); Controls controls = new Controls(); Touches touches = new Touches();
        pebxel.ui.WndWatchMenu menu = new pebxel.ui.WndWatchMenu(); GameScene.show(menu);
        controls.frame(); link.frame(1f/30); link.take(7984);
        int stamp = link.nowMs(), cam = link.camIdOf(menu.camera);
        check(link.remap(cam, stamp, 100, 100) != null, "current-page touch accepted");
        Object token = menu.touchPage(); show(menu, 1);
        check(menu.touchPage() != token && link.remap(cam, stamp, 100, 100) == null, "old-page touch rejected");
        token = menu.touchPage(); Controls.fingerLast = true;
        java.util.List<Ui.Part> buttons = Controls.buttons(menu);
        Ui.press(buttons.get(buttons.size() - 2).g);
        check(menu.touchPage() == token, "tapping the active menu tab keeps its pictures");
        touches = new Touches();
        touch(touches, link, controls, 3, 30, 50);
        check((Boolean)get(touches, "dragDown"), "interface drag starts");
        touch(touches, link, controls, 4, 35, 60);
        check((Integer)get(touches, "dragY") == 121, "interface drag moves");
        touch(touches, link, controls, 5, 35, 60);
        check(!(Boolean)get(touches, "dragDown"), "interface drag releases");
        Field active = PointerEvent.class.getDeclaredField("activePointers"); active.setAccessible(true);
        check(((Map<?,?>)active.get(null)).isEmpty(), "no pointer left pressed after release");
        while (Ui.topWindow() != null) Ui.topWindow().hide();
        menu = new pebxel.ui.WndWatchMenu(); GameScene.show(menu);
        touch(touches, link, controls, 3, 30, 50); show(menu, 2);
        touches.frame(link, controls, 1f/30); PointerEvent.processPointerEvents();
        check(!(Boolean)get(touches, "dragDown") && ((Map<?,?>)active.get(null)).isEmpty(), "page change cancels drag");
        Object sound = null;
        for (Ui.Part p : Ui.parts(menu)) if (p.button && p.text.startsWith("Sound:")) sound = p.g;
        int listenerCount = MenuTest.listeners();
        for (Ui.Part p : Ui.parts(menu)) if (p.button && p.text.startsWith("Music:")) { Ui.press(p.g); break; }
        Object soundAfter = null;
        for (Ui.Part p : Ui.parts(menu)) if (p.button && p.text.startsWith("Sound:")) soundAfter = p.g;
        check(sound != null && sound == soundAfter, "music changes preserve the other settings buttons");
        check(MenuTest.listeners() == listenerCount, "settings rebuild frees old touch listeners");
        touches.add(new byte[]{1, (byte)++seq, (byte)255, 1, 30, 0, 50, 0, 0, 0, 0, 0});
        touches.frame(link, controls, 1f/30); PointerEvent.processPointerEvents();
        show(menu, 1); touches.frame(link, controls, 1f/30); PointerEvent.processPointerEvents();
        check(((Map<?,?>)active.get(null)).isEmpty(), "a pending long tap is cancelled on page change");
        menu.hide();
        System.out.println("PASS: assistant policy, menu page stamps, drag move/release/cancel (" + Screen.w + "px)");
        System.exit(0);
    }
}
