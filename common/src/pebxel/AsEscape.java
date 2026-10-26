package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRetribution;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import static pebxel.Assist.*;

/**
 * Use the way out before it's too late (agent HE, floors 1-10). Forensics (HE-b1, 288 honest games): 90 of 287 deaths
 * held a known way out: Teleportation 28, Mirror Image 27, Terror 24, Retribution 18, Lullaby 15, Invisibility 10; 31
 * of 87 sewer deaths and 15 of 28 at Goo. The rules' "dying: escape" waits for a quarter of health with a foe beside
 * the hero and the fight model at 30% to die, and leaves Retribution out; by then the hero has often drunk his unknown
 * potions as a gamble first. Here, as a last pass over plan A: in a fight, at ESCPCT of health or less, with the fight
 * model giving ESCPD or more to die and no known Healing to drink, the scroll or potion that fits:
 *  - Retribution, at 40% of health or less (it hits everything in sight for a tenth of its health plus up to 90% of
 *    what it has left, more the less the hero has; ScrollOfRetribution.doRead), the foes in sight;
 *  - Lullaby (all in sight fall asleep: walk away, or a sure blow on a sleeper), Terror (they run), Teleportation
 *    (off a boss floor), Invisibility, Mirror Image (the images draw the blows).
 * Each is a suggestion with its why, for the assistant as for the bot. AS_ESCAPE=0 turns it off.
 */
final class AsEscape {
	private AsEscape() { }

	static final boolean ON = opt("ESCAPE", true);
	static final int PCT = optInt("ESCPCT", 40), PD = optInt("ESCPD", 15), MAXD = optInt("ESCMAXD", 10);
	static int used;

	static void after(List<Target> out) {
		try {
			if (!ON || out.isEmpty()) return;
			Hero H = Dungeon.hero;
			if (Dungeon.depth > MAXD || bag(Amulet.class) != null || H.HP * 100 > H.HT * PCT) return;
			Target a = out.get(0);
			if (a.why != null && (a.why.startsWith("escape") || a.why.startsWith("rescue") || a.why.contains("heal") || a.why.contains("rather than die") || a.why.startsWith("dying: escape") || a.why.startsWith("Goo pumps"))) return;
			if (a.kind == USE && a.item instanceof Potion && known(a.item)) return;   // (a known potion already chosen)
			PotionOfHealing ph = bag(PotionOfHealing.class); if (ph != null && known(ph)) return;   // (the rules drink it)
			List<Mob> fs = oFightList(); if (fs.isEmpty()) return;
			int near = 0; for (Mob m : fs) if (distance(m.pos, H.pos) <= 2) near++;
			if (near == 0) return;
			FightModel.Result r = FightModel.engaged(H, fs);
			if (r == null || r.pDeath * 100 < PD) return;
			Target t = pick(H, fs, r);
			if (t != null) { out.add(0, t); used++; }
		} catch (Throwable e) { lastError = e; }
	}

	static Item have(Class<? extends Item> c, boolean scroll) {
		Item i = bag(c);
		if (i == null || !known(i) || declined(c, scroll ? Scroll.AC_READ : Potion.AC_DRINK)) return null;
		if (scroll && blind()) return null;
		return i;
	}

	static Target pick(Hero H, List<Mob> fs, FightModel.Result r) {
		String w = " (" + H.HP + " of " + H.HT + " left, " + Math.round(r.pDeath * 100) + "% to die)";
		Item i;
		if (H.HP * 10 <= H.HT * 4 && (i = have(ScrollOfRetribution.class, true)) != null) return use(i, Scroll.AC_READ, -1, "way out: Retribution, it hits harder the less health is left" + w);
		if ((i = have(ScrollOfLullaby.class, true)) != null) return use(i, Scroll.AC_READ, -1, "way out: Lullaby, they fall asleep" + w);
		if ((i = have(ScrollOfTerror.class, true)) != null) return use(i, Scroll.AC_READ, -1, "way out: Terror, they run" + w);
		if (!Dungeon.bossLevel() && (i = have(ScrollOfTeleportation.class, true)) != null) return use(i, Scroll.AC_READ, -1, "way out: Teleportation, away from them" + w);
		if (H.invisible <= 0 && (i = have(PotionOfInvisibility.class, false)) != null) return use(i, Potion.AC_DRINK, -1, "way out: Invisibility, they lose you" + w);
		if ((i = have(ScrollOfMirrorImage.class, true)) != null) return use(i, Scroll.AC_READ, -1, "way out: Mirror Image, the images draw the blows" + w);
		return null;
	}
}
