package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.DarkGold;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import java.util.*;

/**
 * The quests' choices (agent K3; strategy-reference chapter 5), for the Assistant to suggest and the bot to make: the
 * Ghost's reward (armour or weapon), the Wandmaker's wand, the Blacksmith's favours (upgrade, smith, cash out) and
 * the item they go on, leaving the mine. {@link #choose} names the button of a window to press (or null: no view);
 * {@link #select} the item an item selector of theirs should get. Only what the windows show is used.
 */
public final class AsChoices {
	private AsChoices() {}

	/** Why the last choice (for the log and the Assistant's hint). */
	public static String why = "";

	/** The button of w to press, from its buttons (Controls.buttons order), or null. */
	public static Ui.Part choose(Window w, List<Ui.Part> bs) {
		try {
			String n = w.getClass().getName();
			if (n.endsWith("WndSadGhost")) return pickItem(bs, AsChoices::ghostScore, "the Ghost's reward");
			if (n.endsWith("WndWandmaker")) return pickItem(bs, AsChoices::wandScore, "the Wandmaker's wand");
			if (n.endsWith("WndBlacksmith$WndSmith")) return pickItem(bs, AsChoices::smithScore, "the smith's piece");
			if (n.endsWith("WndBlacksmith")) {
				String want = smithWants();
				for (Ui.Part p : bs) if (want != null && p.text.toLowerCase(Locale.ROOT).startsWith(want) && enabled(p.g)) { why = "the Blacksmith: " + want; return p; }
				return null;
			}
			if (n.contains("MiningLevel")) {   // leaving the mine: only when done (else no)
				boolean done = Assist.L == Dungeon.level ? AsQuests.mineDone() : AsQuests.gold() >= AsQuests.GOLD;
				for (Ui.Part p : bs) { boolean neg = negative(p.text); if (done != neg) { why = done ? "the mine: done" : "the mine: not done yet"; return p; } }
			}
		} catch (Throwable e) { Assist.lastError = e; }
		return null;
	}

	/** The item an item selector of these windows should get, or null (no view). */
	public static Item select(WndBag.ItemSelector sel) {
		try {
			String n = sel.getClass().getName();
			if (n.contains("WndBlacksmith")) { Item i = upgradeTarget(); if (i != null && sel.itemSelectable(i)) { why = "the Blacksmith's upgrade"; return i; } }
		} catch (Throwable e) { Assist.lastError = e; }
		return null;
	}

	static boolean negative(String s) { String l = s.toLowerCase(Locale.ROOT).trim(); for (String x : new String[]{"no", "not", "cancel", "back", "leave", "stay", "keep"}) if (l.equals(x) || l.startsWith(x + " ") || l.startsWith(x + ",") || l.startsWith(x + "'")) return true; return false; }

	static boolean enabled(Gizmo g) { return !(g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) || ((com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) g).active; }

	interface Score { int of(Item i); }
	static Ui.Part pickItem(List<Ui.Part> bs, Score s, String what) {
		Ui.Part best = null; int bv = Integer.MIN_VALUE;
		for (Ui.Part p : bs) { Item i = itemOf(p.g); if (i == null) continue; int v = s.of(i); if (v > bv) { bv = v; best = p; } }
		if (best != null) why = what + ": " + itemOf(best.g).getClass().getSimpleName();
		return best;
	}
	static Item itemOf(Gizmo g) { for (Group p = g instanceof Group ? (Group) g : g.parent; p != null; p = p.parent) if (p instanceof ItemButton) return ((ItemButton) p).item(); return null; }

	// ---- the Ghost (strategy-reference 5.1): the armour, unless the weapon is 2 tiers up and the armour no better ----
	static int tier(Item i) { return i instanceof Armor ? ((Armor) i).tier : i instanceof MeleeWeapon ? ((MeleeWeapon) i).tier : 0; }
	static int lvl(Item i) { return i.levelKnown ? i.level() : 0; }
	static int strNeed(Item i) { return i instanceof Armor ? ((Armor) i).STRReq() : i instanceof Weapon ? ((Weapon) i).STRReq() : 0; }
	static int ghostScore(Item i) {
		Hero h = Dungeon.hero;
		Item wa = h.belongings.armor(), ww = h.belongings.weapon();
		int fit = strNeed(i) > h.STR() + 1 ? -20 : 0;
		if (i instanceof Armor) return 10 + fit + 2 * (tier(i) - (wa == null ? 0 : tier(wa))) + lvl(i);
		boolean duel = h.heroClass == HeroClass.DUELIST;
		return fit + 2 * (tier(i) - (ww instanceof MeleeWeapon ? tier(ww) : 1)) + lvl(i) + (duel ? 8 : 0) - (h.heroClass == HeroClass.MAGE ? 10 : 0);
	}

	// ---- the Wandmaker (strategy-reference 5.2): wands the bot zaps to good effect first ----
	static final List<Class<?>> WANDS = Arrays.asList(WandOfLightning.class, WandOfDisintegration.class, WandOfFrost.class, WandOfCorrosion.class, WandOfBlastWave.class,
			WandOfFireblast.class, WandOfPrismaticLight.class, WandOfMagicMissile.class, WandOfCorruption.class, WandOfTransfusion.class, WandOfLivingEarth.class, WandOfRegrowth.class, WandOfWarding.class);
	static int wandScore(Item i) {
		int k = WANDS.indexOf(i.getClass()); int v = k < 0 ? 0 : 100 - k * 5;
		Hero h = Dungeon.hero;
		for (Item o : h.belongings) if (o != i && o.getClass() == i.getClass()) v -= 30;   // (one already: the other)
		return v;
	}

	// ---- the Blacksmith (strategy-reference 5.3) ----
	/** What his favour should buy next ("upgrade", "smith", "cash"), or null: nothing. */
	public static String smithWants() {
		int f = Blacksmith.Quest.favor;
		int up = 1000 + 1000 * Blacksmith.Quest.upgrades;
		if (f >= up && upgradeTarget() != null) return "upgrade";
		if (f >= 2000) return "smith";
		if (f > 0 && f < 500) return "cash";
		if (f > 0 && upgradeTarget() == null) return "cash";
		return f > 0 && f < up ? "cash" : null;
	}
	/** His upgrade takes an identified, uncursed item at +0 or +1: the worn armour, else the weapon. */
	static Item upgradeTarget() {
		Hero h = Dungeon.hero;
		for (Item i : new Item[]{h.belongings.armor(), h.belongings.weapon()})
			if (i != null && i.isIdentified() && !i.cursed && i.level() <= 1 && i.isUpgradable()) return i;
		return null;
	}
	static int smithScore(Item i) {
		Hero h = Dungeon.hero;
		int fit = strNeed(i) > h.STR() + 1 ? -20 : 0;
		Item cur = i instanceof Armor ? h.belongings.armor() : h.belongings.weapon();
		int gain = 2 * (tier(i) - (cur == null ? 0 : tier(cur))) + lvl(i) - (cur == null ? 0 : lvl(cur));
		return fit + gain + (i instanceof Armor ? 2 : 0);
	}
}
