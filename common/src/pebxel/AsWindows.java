package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import java.util.*;

/**
 * The Assistant's answer to a window or a screen (agent DS): the button it would press, the item it would choose in
 * an item selector, or Back. The bot presses it (BotWindows, on top of its test-only extras); on the watch the frame
 * starts on it (Controls), so Select takes it and Up/Down choose otherwise. Only what the window shows and the skills'
 * own reasons are used: the talents (Build), the subclass and armour ability (Build), the quests' rewards (AsChoices),
 * the item windows (AsItems, AsArtifacts), the shop (buy what was walked to, sell AsEconomy's spares), the alchemy pot
 * (AsAlchemy), the Amulet (climb), questions (yes), and what only tells something (read, then closed).
 */
public final class AsWindows {
	private AsWindows() { }

	/** An answer: press g, or choose item in the selector, or (both null) Back. why: a few words for the hint. */
	public static final class Pick {
		public final Gizmo g; public final Item item; public final String why;
		Pick(Gizmo g, Item item, String why) { this.g = g; this.item = item; this.why = why; }
		public boolean back() { return g == null && item == null; }
		@Override public String toString() { return why + (g != null ? " [" + Ui.label((Group) g) + "]" : item != null ? " " + item.getClass().getSimpleName() : " (back)"); }
	}
	/** No view (the frame starts where it always did: the first button). */
	public static final Pick NONE = new Pick(null, null, "no view");
	static Pick press(Gizmo g, String why) { return new Pick(g, null, why); }
	static Pick back(String why) { return new Pick(null, null, why); }

	static final String[] NEGATIVE = {"cancel", "no", "back", "close", "leave", "not now", "nevermind", "never mind", "return", "i'll decide later", "go to patreon", "not yet"};
	public static boolean negative(String s) {
		String l = s.toLowerCase(Locale.ROOT).trim();
		for (String n : NEGATIVE) if (l.equals(n) || l.startsWith(n + " ") || l.startsWith(n + ",")) return true;
		return false;
	}
	public static List<Gizmo> positives(List<Ui.Part> bs) {
		List<Gizmo> out = new ArrayList<>();
		for (Ui.Part p : bs) if (!negative(p.text) && !p.text.trim().isEmpty() && !p.text.equals(p.g.getClass().getSimpleName()) && AsChoices.enabled(p.g)) out.add(p.g);
		return out;
	}
	public static Gizmo byText(List<Ui.Part> bs, String t) {
		for (Ui.Part p : bs) if (p.text.toLowerCase(Locale.ROOT).contains(t)) return p.g;
		return null;
	}
	/** A window's item selector: only a bag asks for an item (other windows keep one of their own for after a button). */
	public static WndBag.ItemSelector selector(Window w) {
		return w instanceof WndBag || w instanceof pebxel.ui.WndWatchBag ? Assist.selector(w) : null;
	}
	static String kind(Window w) { return w.getClass().getSimpleName().isEmpty() ? w.getClass().getSuperclass().getSimpleName() + "$" : w.getClass().getSimpleName(); }

	/** What only tells something (read, then Back). */
	static boolean reading(Window w, List<Ui.Part> bs) {
		String k = kind(w);
		return bs.isEmpty() || k.startsWith("WndInfo") || k.equals("WndMessage") || k.equals("WndStory") || k.equals("WndTitledMessage")
				|| k.equals("WndJournal") || k.equals("WndWatchHero") || k.equals("WndWatchRecords") || k.equals("WndKeyBindings")
				|| k.equals("WndWatchMenu") || k.equals("WndGame") || k.equals("WndSettings") || k.equals("WndChallenges") || k.equals("WndSupportPrompt");
	}

	// ---- the shop: sales this visit (a turn going by ends it) ----
	static int sales; static float salesAt = -1;
	static int sales() { if (Actor.now() != salesAt) { salesAt = Actor.now(); sales = 0; } return sales; }
	/** A sale's most the shop takes in a visit. */
	static final int SALES = 3;
	/** A spare for the shop: what agent O's Trade would sell (by worth), else a weapon or armour not worn. */
	static Item spare() {
		Hero h = Dungeon.hero; if (h == null) return null;
		if (!"0".equals(System.getenv("AS_OSHOP"))) return pebxel.bot.Trade.sellPick(h);
		for (Item i : h.belongings.backpack.items)
			if ((i instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon || i instanceof com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor)
					&& !i.isEquipped(h) && com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper.canSell(i)) return i;
		return null;
	}

	/** The item the next item selector should get, set by the suggestion that opens it (an alchemy slot's ingredient). */
	public static Item want; static float wantAt = -1;
	/** The item the next selector should get (this turn only). */
	public static void want(Item i) { want = i; wantAt = Actor.now(); }

	/** The item for a selector: the one the suggestion meant (want), the scroll's own target (Assist.choose: the armour
	 *  plan's upgrade, identify, remove curse), else the worn things first, then the pack. */
	public static Item selectorPick(WndBag.ItemSelector sel) {
		Hero h = Dungeon.hero; if (h == null || sel == null) return null;
		try {
			if (want != null && wantAt == Actor.now() && sel.itemSelectable(want) && h.belongings.contains(want)) return want;   // (kept till the next step: it may sit in another bag, reached by its tab)
			Item src = Assist.actItem;
			if (src != null) { Item c = Assist.choose(sel, src); if (c != null) return c; }
			List<Item> worn = new ArrayList<>();
			for (Item i : new Item[]{h.belongings.weapon(), h.belongings.armor(), h.belongings.artifact(), h.belongings.ring(), h.belongings.misc()}) if (i != null) worn.add(i);
			for (Item i : worn) if (sel.itemSelectable(i)) return i;
			for (Item i : h.belongings.backpack) if (!(i instanceof Bag) && sel.itemSelectable(i)) return i;
			for (Item i : h.belongings) if (sel.itemSelectable(i)) return i;
		} catch (Throwable e) { Assist.lastError = e; }
		return null;
	}

	/** The answer for window w (never null; never throws). */
	public static Pick decide(Window w) {
		try { return decide0(w); } catch (Throwable e) { Assist.lastError = e; return back("?"); }
	}

	static Pick decide0(Window w) {
		List<Ui.Part> bs = Controls.buttons(w);
		String k = kind(w), n = w.getClass().getName();
		Hero h = Dungeon.hero;
		{ String want = AsArtifacts.windowAnswer(w); Gizmo b = want == null ? null : byText(bs, want); if (b != null) return press(b, "artifact: " + want); }   // (a swap's slot, the hourglass's freeze, the chalice's yes or no)
		// item selectors (a scroll, the alchemy, a quest, the shop's sale)
		WndBag.ItemSelector sel = selector(w);
		if (sel == null && (w instanceof pebxel.ui.WndWatchBag || w instanceof pebxel.ui.WndWatchItem || w instanceof WndBag)) return NONE;   // (the player's own backpack and its items: his)
		if (sel != null) {
			String prompt = ""; try { prompt = String.valueOf(sel.textPrompt()); } catch (Throwable e) { }
			if (prompt.toLowerCase(Locale.ROOT).contains("sell")) {   // (the shop asks again after each sale: a few spares, then Back)
				Item it = sales() < SALES ? spare() : null;
				return it != null ? new Pick(null, it, "sell a spare") : back("nothing more to sell");
			}
			if (sel == com.shatteredpixel.shatteredpixeldungeon.windows.WndEnergizeItem.selector) {   // (the pot's "+": AsAlchemy's junk, never the worn weapon)
				Item e = AsAlchemy.energizePick();
				return e != null ? new Pick(null, e, "energize") : back("nothing to energize");
			}
			Item ch = AsChoices.select(sel);
			if (ch != null) return new Pick(null, ch, AsChoices.why);
			Item it = selectorPick(sel);
			return it != null ? new Pick(null, it, "select") : back("nothing to select");
		}
		// the talents page the Assistant opened: the build's next talent (Build), else back to the game
		if (n.endsWith("windows.WndHero") && h != null) {
			Talent t = Build.nextTalent(h);
			if (t == null) return back("no points");
			for (Ui.Part p : bs) if (talentOf(p.g) == t) return press(p.g, "talent: " + t.title());
			return back("talent not shown");
		}
		if (n.endsWith("WndWatchTalent") || k.equals("WndInfoTalent")) {   // a talent's page: Upgrade when it's the build's or the player's own
			Gizmo up = byText(bs, "upgrade");
			if (up != null && AsChoices.enabled(up)) return press(up, "upgrade it");
		}
		if (reading(w, bs)) return back("read");
		// the shop: sell (from the sale's selector), buy what was walked to
		if (k.equals("WndTradeItem")) {
			Gizmo s = byText(bs, "sell");
			if (s != null) { if (sales() < SALES) { sales++; return press(s, "sell"); } return back("sold enough"); }
			Gizmo b = byText(bs, "buy");
			return b != null && AsChoices.enabled(b) ? press(b, "buy") : back("can't buy");
		}
		if (n.endsWith("StoneOfIntuition$WndGuess")) return guess(w, bs);
		Ui.Part qc = AsChoices.choose(w, bs);   // (the Ghost's reward, the Wandmaker's wand, the Blacksmith's favours, the mine)
		if (qc != null) return press(qc.g, AsChoices.why);
		Ui.Part ic = AsItems.choose(w, bs);   // (an item's own window: the Augmentation, the Beacon, the Intuition guess)
		if (ic != null) return press(ic.g, "item choice");
		if (h != null && k.equals("WndChooseSubclass")) {
			Gizmo b = byText(bs, Build.subclass(h.heroClass).shortDesc().toLowerCase(Locale.ROOT));
			if (b != null) return press(b, "the build's subclass");
		}
		if (h != null && k.equals("WndChooseAbility")) {
			Gizmo b = byText(bs, Build.ability(h.heroClass).shortDesc().toLowerCase(Locale.ROOT));
			if (b != null) return press(b, "the build's armour ability");
		}
		if (k.equals("WndChooseSubclass") || k.equals("WndChooseAbility") || n.contains("WndSadGhost") || n.contains("WndWandmaker") || n.contains("WndImp") || n.contains("WndBlacksmith")) {
			List<Gizmo> pos = positives(bs);
			if (pos.isEmpty()) for (Ui.Part p : bs) if (!negative(p.text) && AsChoices.enabled(p.g)) pos.add(p.g);   // (item pictures: no words on them)
			return pos.isEmpty() ? back("no choice") : press(pos.get(0), "choose");
		}
		// questions (WndOptions and the like): the first positive answer
		List<Gizmo> pos = positives(bs);
		return pos.isEmpty() ? back("only no") : press(pos.get(0), "yes");
	}

	/** The Intuition stone's guess, as a player makes it: the likeliest kind's picture (Identify's odds), then the guess
	 *  button it shows. (Before, the bot set the guess itself and pressed the hidden button: on the watch Select never
	 *  got past the first picture.) */
	static Pick guess(Window w, List<Ui.Part> bs) {
		Gizmo go = null; Item item = null;
		for (Object g : new ArrayList<>(com.watabou.noosa.GroupAccess.members(w))) if (g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) { Object it = Assist.field(g, "val$item"); if (it instanceof Item) { go = (Gizmo) g; item = (Item) it; } }
		if (item == null) return back("nothing to guess");
		AsItems.H = Dungeon.hero;
		Class<?> k = AsItems.likeliest(item); if (k == null) return back("no guess");
		Class<?> want = item instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion ? com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion.regToExo.get(k)
				: item instanceof com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll ? com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll.regToExo.get(k) : k;
		Object cur = staticField(com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition.class, "curGuess");
		if (cur != null && go.visible && AsChoices.enabled(go)) {   // (a picture chosen, the player's or ours: its guess)
			if (cur != want) return press(go, "guess the one chosen");
			if (!(item instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion) && !(item instanceof com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll)) Clues.guess(item.getClass(), k);   // (wrong, it rules the kind out)
			return press(go, "guess " + k.getSimpleName().replaceAll("(Potion|Scroll|Ring)Of", ""));
		}
		for (Ui.Part p : bs) if (Assist.field(p.g, "val$i") == want) return press(p.g, "likeliest: " + k.getSimpleName().replaceAll("(Potion|Scroll|Ring)Of", ""));
		return back("guess not shown");
	}

	static Object talentOf(Gizmo g) { return g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton ? Assist.field(g, "talent") : null; }

	/** A screen other than the dungeon's: the Amulet's (Stay, to climb back out with it; ASCEND=0, a test's: the win
	 *  there), the alchemy pot's (AsAlchemy's recipe), else its first positive button. */
	public static Pick decideScene(com.watabou.noosa.Scene sc) {
		try {
			List<Ui.Part> bs = Controls.buttons(sc);
			String k = sc.getClass().getSimpleName();
			if (k.equals("AlchemyScene")) return alchemy(sc);
			if (k.equals("AmuletScene")) {
				Gizmo b = byText(bs, "0".equals(System.getenv("ASCEND")) ? "call it a day" : "not done");
				if (b != null) return press(b, "the Amulet: climb out with it");
			}
			List<Gizmo> pos = positives(bs);
			return pos.isEmpty() ? back("back") : press(pos.get(0), "first");
		} catch (Throwable e) { Assist.lastError = e; return back("?"); }
	}

	// ---- the alchemy screen (AsAlchemy's plan, step by step: junk energized, the ingredients in, the recipe) ----
	static List<Item> aPlan; static int aDepth = -1, aIdx, aMakes, aEnergizes; static String aOut = ""; static Object aScene;
	/** Visits to the pot with a recipe made, and what was made (the bot's report). */
	public static int alchemyVisits; public static final Set<String> made = new TreeSet<>();
	public static void newGame() { aPlan = null; aDepth = -1; aScene = null; want = null; alchemyVisits = 0; made.clear(); sales = 0; salesAt = -1; }

	static Pick alchemy(com.watabou.noosa.Scene sc) {
		AsAlchemy.used(); want = null;
		Object[] inputs = (Object[]) staticField(com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class, "inputs");
		Object[] combines = (Object[]) staticField(com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class, "combines");
		if (inputs == null || combines == null) return back("alchemy: can't see its slots");
		if (sc != aScene) { aScene = sc; aMakes = 0; aPlan = null; aDepth = -1; aEnergizes = 0; }
		int filled = 0; for (Object in : inputs) if (in != null && Assist.field(in, "item") != null) filled++;
		// known junk energized first (the "+" by the energy: a bag, the item's window, then back here)
		if (aEnergizes < 8 && aPlan == null && filled == 0 && AsAlchemy.energizePick() != null) {
			Object add = Assist.field(sc, "energyAdd");
			if (add instanceof Gizmo && ((Gizmo) add).visible) { aEnergizes++; return press((Gizmo) add, "alchemy: energize " + AsAlchemy.energizePick().getClass().getSimpleName()); }
		}
		if (aPlan == null && aDepth != Dungeon.depth) { aPlan = AsAlchemy.plan(); aIdx = AsAlchemy.planIdx; aOut = AsAlchemy.planOut; aDepth = Dungeon.depth; if (aPlan != null) alchemyVisits++; }
		if (aPlan == null) return back("alchemy: nothing to make");
		if (filled < aPlan.size()) {
			for (Object in : inputs) if (in != null && Assist.field(in, "item") == null) {
				want(aPlan.get(filled));
				Object slot = Assist.field(in, "slot");
				if (slot instanceof Gizmo) return press((Gizmo) slot, "alchemy: put in " + want.getClass().getSimpleName());
			}
		}
		Object b = Assist.field(combines[Math.max(0, Math.min(aIdx, combines.length - 1))], "button");
		if (b instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton && ((com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) b).active) {
			made.add(aOut); AsEconomy.brewed(aOut); aPlan = null; aDepth = -2;   // (made: next time, a new plan from what's left)
			if (++aMakes >= 8) aDepth = Dungeon.depth;   // (eight a visit at most)
			return press((Gizmo) b, "alchemy: make " + aOut);
		}
		aPlan = null;
		return back("alchemy: no recipe after all");
	}

	static Object staticField(Class<?> c, String name) {
		try { java.lang.reflect.Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(null); } catch (Exception e) { return null; }
	}
}
