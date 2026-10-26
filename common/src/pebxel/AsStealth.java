package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AscensionChallenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import pebxel.Assist.Target;
import pebxel.Brain.Cand;
import pebxel.bot.Fighter;
import static pebxel.Assist.*;

/**
 * Getting away (agent ST, for Rules' table). Why the Rogue won 12-14 of 16 climbs against about 4-11 for the others
 * (BASE1, CB-8): not fewer fights (the same turns and fights a floor as the others) but his Cloak, used 76 times a
 * climb ("cloak: a sure first blow"): unseen, an eye can't charge and a caster can't bolt (both need to see him), the
 * first blow can't miss (72 surprise blows a game against about 14), and foes lose him. The others have the same
 * tools in their packs and die holding them (CB-8: a known Teleportation in 30 of 85 deaths from 16, 29 of 83 from 21;
 * Invisibility in 17; Healing in 17-24). The lookahead's numbers leave the climb's curse out (Tune.ASCEND off), so the
 * "drink rather than die" row never saw a DM-100's 4.5-times bolt coming.
 *
 *  - away rather than die: the next round's worst (the curse counted) could kill and no Healing is at work or to
 *    hand: a known Teleportation, else Invisibility (nobody can strike or bolt what they can't see), else Terror;
 *  - drink rather than die, the curse counted: the same row as Rules', with the climb's foes at their real strength.
 * AS_STEALTH=0 turns them off.
 */
final class AsStealth {
	private AsStealth() {}

	static final boolean ON = opt("STEALTH", true), ESC = opt("STESC", true), DRINK = opt("STDRINK", true);
	static final int MARGINP = optInt("STMARGIN", 100);   // % of the worst round that health must stay above

	/** The worst the next round could take (Rules.worstRound), each foe at its strength on the climb. */
	static double worst() {
		double w = pebxel.bot.HeroView.dot(Dungeon.hero) / 3;
		if (!Brain.fight || Brain.me == null) return w;
		int dr = Brain.me.dr.lo;
		for (int i = 0; i < Brain.fs.size() && i < Brain.foes.size(); i++) {
			Fighter f = Brain.fs.get(i); Mob m = Brain.foes.get(i);
			if (f.asleep) continue;
			double k = pebxel.bot.Tune.ASCEND ? 1 : AscensionChallenge.statModifier(m);
			double x = 0;
			if (f.dist <= 1) x = Math.max(f.zapDmg != null && "Eye".equals(f.name) ? f.zapDmg.hi() : 0, Math.max(0, (f.rageHp == 0 && f.rageDmg != null ? f.rageDmg : f.dmg).hi() * k - dr) * Math.max(1, Math.round(1 / Math.max(0.25, f.delay))) / k);
			else if (f.zapDmg != null) x = f.zapDmg.hi();
			else if (f.burstDmg != null && "Eye".equals(f.name)) x = f.burstDmg.hi();
			else if (f.dist <= 1 + f.speed && !f.ranged) x = Math.max(0, f.dmg.hi() * k - dr) * 0.5 / k;
			w += x * k;
		}
		return w;
	}
	static boolean dying() { Hero h = Dungeon.hero; return worst() * MARGINP / 100.0 >= h.HP; }

	/** PROPOSE: the next round could kill, no Healing at work or to hand: a known way out. */
	static boolean awayWhen(Cand c) {
		if (!ON || !ESC || c != Rules.planA || c.role == Brain.Role.ESCAPE || c.role == Brain.Role.HEAL) return false;
		if (Brain.healing > 0 || Rules.healPot() != null || !Brain.fight) return false;
		return way() != null && dying();
	}
	static Item way() {
		Hero h = Dungeon.hero;
		if (!blind()) { ScrollOfTeleportation t = bag(ScrollOfTeleportation.class); if (t != null && known(t) && !declined(ScrollOfTeleportation.class, Scroll.AC_READ) && !Dungeon.bossLevel()) return t; }
		PotionOfInvisibility p = bag(PotionOfInvisibility.class); if (p != null && known(p) && !declined(PotionOfInvisibility.class, Potion.AC_DRINK) && h.invisible <= 0) return p;
		if (!blind()) { ScrollOfTerror s = bag(ScrollOfTerror.class); if (s != null && known(s) && !declined(ScrollOfTerror.class, Scroll.AC_READ)) return s; }
		return null;
	}
	static Target awayTarget() {
		Item i = way(); if (i == null) return null;
		Target t = use(i, i instanceof Potion ? Potion.AC_DRINK : Scroll.AC_READ, -1, "stealth: away rather than die (" + Math.round(worst()) + " could come, " + Dungeon.hero.HP + " left)");
		t.hint = Math.max(1, Brain.R) * 0.6;
		return t;
	}

	/** VETO (the climb): a shot or zap at a cursed caster from in its line (CB-8: a Huntress on 9 shot a DM-100 five times,
	 *  140 health to 4: its bolt is 13-45 there, her arrow far less; the lookahead, the curse left out, called it even),
	 *  with a cell out of every line a few steps off (AsFire: out of its line, it comes round to be met beside him). */
	static final boolean TRADE = opt("STTRADE", true);
	static boolean tradeVeto(Cand c) {
		if (!ON || !TRADE) return false;
		Target t = c.t; if (t == null || t.kind != USE || t.cell < 0 || t.action == null) return false;
		if (!(t.action.equals(com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow.AC_SHOOT) || t.action.equals(com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand.AC_ZAP)
				|| t.action.equals(com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff.AC_ZAP) || t.action.equals(Item.AC_THROW))) return false;
		if (t.item instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb || t.item instanceof Potion) return false;   // (a bomb or a potion: one blow that ends it)
		com.shatteredpixel.shatteredpixeldungeon.actors.Char ch = seenChar(t.cell);
		if (!(ch instanceof Mob) || !AsFire.shooter((Mob) ch) || AscensionChallenge.statModifier(ch) < 2) return false;
		int hp = Dungeon.hero.pos; if (distance(ch.pos, hp) <= 1 || Dungeon.hero.rooted) return false;
		AsFire.note(); if (AsFire.fireAt(hp, false) <= 0) return false;
		return AsFire.outTarget() != null;
	}

	/** PROPOSE: Rules' "drink rather than die" with the climb's curse counted (it isn't in the lookahead's numbers). */
	static boolean drinkWhen(Cand c) {
		if (!ON || !DRINK || c != Rules.planA || c.role == Brain.Role.HEAL || Rules.healPot() == null || Brain.healing > 0) return false;
		return Brain.fight && Rules.worstRound() < Dungeon.hero.HP && dying();   // (where Rules' own row doesn't see it)
	}
	static Target drinkTarget() {
		Target t = use(Rules.healPot(), Potion.AC_DRINK, -1, "stealth: drink rather than die (the curse counted)");
		t.hint = Math.max(1, Brain.R) * 0.5;
		return t;
	}
}
