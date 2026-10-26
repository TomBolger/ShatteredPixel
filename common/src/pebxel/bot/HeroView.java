package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barkskin;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.DamageWand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;

/**
 * The hero's side of a fight from what the player can see (Thomas's rule): an unidentified piece counts at its shown
 * level (+0) and without a hidden curse; enchantments and glyphs count when shown (Weapon.name: a curse only once
 * known). The formulas are Shattered's own (actors/hero/Hero.java attackSkill / defenseSkill / drRoll / damageRoll /
 * attackDelay; items/weapon/Weapon.java accuracyFactor / baseDelay; MeleeWeapon.damageRoll; Armor.DRMin / DRMax /
 * evasionFactor / speedFactor), re-derived so that no game method with side effects is called and no roll is made.
 * Left out: rings (their levels are hidden till known), Duelist abilities, Berserk, Combo, Momentum, enchant procs
 * beyond a flat factor ({@link Ench}).
 */
public final class HeroView {
	private HeroView() { }

	/** The level shown for an item: its own once known, else +0 (plus a shown Degrade). */
	public static int visLevel(Item i) {
		if (i == null) return 0;
		int bonus = i.buffedLvl() - i.level();
		return (i.levelKnown ? i.level() : 0) + bonus;
	}

	/** The hero's base accuracy and evasion (Hero.attackSkill / defenseSkill fields: 10 / 5, +1 a level). */
	public static int baseAcc(Hero h) { Object v = field(h, "attackSkill"); return v instanceof Integer ? (Integer) v : 9 + h.lvl; }
	public static int baseEva(Hero h) { Object v = field(h, "defenseSkill"); return v instanceof Integer ? (Integer) v : 4 + h.lvl; }

	/** The hero as now. */
	public static Fighter of(Hero h) {
		Fighter f = of(h, h.belongings.weapon(), h.belongings.armor(), 0, 0, -1);
		f.slots = Tune.SLOTS ? slots(h.pos) : 8;
		return f;
	}

	/** The passable cells beside c (what the map shows: walls, doors, chasms are seen terrain). */
	public static int slots(int c) {
		com.shatteredpixel.shatteredpixeldungeon.levels.Level l = com.shatteredpixel.shatteredpixeldungeon.Dungeon.level;
		if (l == null || c < 0 || c >= l.length()) return 8;
		int n = 0; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int x = c + k; if (x >= 0 && x < l.length() && l.passable[x]) n++; }
		return Math.max(1, n);
	}

	/**
	 * The hero with weapon w and armour a (either may be null: fists / none), STR + strPlus, wLvl / aLvl: the levels to
	 * count for them (-1: as shown; GearValue passes its own guesses and projections through these).
	 */
	public static Fighter of(Hero h, KindOfWeapon w, Armor a, int strPlus, int wPlus, int aPlus) {
		return of(h, w, a, h.STR() + strPlus, w == null ? 0 : visLevel(w) + wPlus, a == null ? 0 : visLevel(a) + Math.max(0, aPlus), 0, Ench.of(w), Ench.of(a));
	}

	/** The full form: str, the levels, extra hero levels (projections), the enchant / glyph factors. */
	public static Fighter of(Hero h, KindOfWeapon w, Armor a, int str, int wl, int al, int lvlPlus, Ench we, Ench ag) {
		Fighter f = new Fighter();
		f.name = "hero";
		f.hp = h.HP; f.ht = h.HT; f.shield = h.shielding();
		if (lvlPlus > 0) { f.ht += 5 * lvlPlus; f.hp = f.ht; f.shield = 0; }
		else f.hp = Math.max(1, f.hp - (int) Math.round(dot(h)));   // (poison, bleeding, fire still to come: shown as buffs)
		double acc = baseAcc(h) + lvlPlus, eva = baseEva(h) + lvlPlus;
		if (h.heroClass != HeroClass.DUELIST && h.hasTalent(Talent.PRECISE_ASSAULT)) acc *= 1 + 0.1 * h.pointsInTalent(Talent.PRECISE_ASSAULT);
		double acc0 = acc;   // (thrown and shot: their own accuracy factors)
		// the weapon (Weapon.accuracyFactor, baseDelay; MeleeWeapon.damageRoll: the augment, then 0..excess STR)
		if (w instanceof Weapon && !(w instanceof MissileWeapon)) {
			Weapon wp = (Weapon) w;
			int req = wp.STRReq(wl), enc = req - str;
			double fa = wp.ACC; if (enc > 0) fa /= Math.pow(1.5, enc);
			acc *= fa * we.acc;
			Dist d = Dist.normal(w.min(wl), w.max(wl));
			d = d.scale(augDmg(wp));
			if (str - req > 0) d = d.plus(Dist.normal(0, str - req));
			f.dmg = d.scale(we.dmg);
			f.delay = wp.DLY * augDly(wp) * (enc > 0 ? Math.pow(1.2, enc) : 1);
			int df = 0; try { df = w.levelKnown || !(w instanceof MeleeWeapon) ? w.defenseFactor(h) : 0; } catch (Throwable e) { }
			if (df > 0) { Dist wd = Dist.normal(0, df).shift(enc > 0 ? -2 * enc : 0).atLeast(0); f.dr = f.dr.plus(wd); }
		} else if (w == null) {   // fists (RingOfForce.damageRoll without the ring)
			f.dmg = Dist.normal(1, Math.max(str - 8, 1));
		} else {
			f.dmg = Dist.normal(w.min(wl), w.max(wl));
		}
		// the armour (Hero.defenseSkill, drRoll; Armor.evasionFactor, speedFactor)
		if (a != null) {
			int enc = a.STRReq(al) - str;
			if (enc > 0) eva /= Math.pow(1.5, enc);
			eva += Math.round((2 + al) * augEva(a));
			if (ag.stone) eva = 0;
			Dist ad = Dist.normal(a.DRMin(al), a.DRMax(al));
			if (enc > 0) ad = ad.shift(-2 * enc);
			f.dr = f.dr.plus(ad.atLeast(0));
			if (enc > 0) f.speed /= Math.pow(1.2, enc);
		}
		int bark = 0; try { bark = Barkskin.currentLevel(h); } catch (Throwable e) { }
		if (bark > 0) f.dr = f.dr.plus(Dist.normal(0, bark));
		f.acc = Math.max(1, Math.round(acc)); f.eva = Math.max(1, Math.round(eva));   // (Hero.attackSkill / defenseSkill round)
		f.taken = we.taken * ag.taken;   // (MT: this had been inside the comment above: glyphs and curses never counted)
		// shots before contact: the spirit bow (MissileWeapon.adjacentAccFactor: 1.5 at range), a charged attack wand
		SpiritBow bow = h.belongings.getItem(SpiritBow.class);
		if (bow != null) {
			int req = bow.STRReq(), ex = str - req;
			Dist d = Dist.normal(bow.min(0), bow.max(0)).scale(augDmg(bow));
			if (ex > 0) d = d.plus(Dist.normal(0, ex));
			f.rangedAcc = acc0 * 1.5 * (req > str ? Math.pow(1.5, str - req) : 1); f.rangedDmg = d; f.rangedDelay = bow.DLY * augDly(bow); f.rangedShots = 99;
		} else {
			MagesStaff st = h.belongings.getItem(MagesStaff.class);
			Object wd = st == null ? null : field(st, "wand");
			Wand wn = wd instanceof Wand ? (Wand) wd : null;
			if (wn instanceof DamageWand && st.levelKnown) {
				int l = visLevel(st);
				f.rangedDmg = Dist.normal(((DamageWand) wn).min(l), ((DamageWand) wn).max(l)); f.rangedSure = true; f.rangedDelay = 1;
				f.rangedShots = wn.curChargeKnown ? wn.curCharges : 1;
			} else {
				int n = 0; MissileWeapon mw = null;
				for (Item i : h.belongings.backpack) if (i instanceof MissileWeapon && !(i instanceof SpiritBow.SpiritArrow)) { n += i.quantity(); if (mw == null) mw = (MissileWeapon) i; }
				if (mw != null) {
					int l = visLevel(mw);
					f.rangedDmg = Dist.normal(mw.min(l), mw.max(l)); f.rangedAcc = acc0 * 1.5 * mw.ACC; f.rangedShots = Math.min(n, 3); f.rangedDelay = mw.DLY;
				}
			}
		}
		com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing hb = h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing.class);
		if (hb != null && lvlPlus == 0) { Object hl = field(hb, "healingLeft"); if (hl instanceof Integer) f.healLeft = (Integer) hl; }   // (shown: the buff's icon and its description)
		PotionOfHealing heal = h.belongings.getItem(PotionOfHealing.class);
		if (heal != null && heal.isKnown()) { f.heals = heal.quantity(); f.healAmount = Math.round(0.8f * f.ht + 14); }
		return f;
	}

	/** The damage still to come from the hero's poison (Poison.act: left / 3 + 1 a turn), bleeding (each turn its level,
	 *  then 0.5-1 of it) and fire (Burning: 1 .. 3 + depth / 4 a turn for its turns left). */
	public static double dot(Hero h) {
		double d = 0;
		for (com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff b : h.buffs()) {
			String n = b.getClass().getSimpleName();
			if (n.equals("Poison")) { Object l = field(b, "left"); if (l instanceof Float) { double L = (Float) l; d += L * L / 6 + L; } }
			else if (n.equals("Bleeding")) { Object l = field(b, "level"); if (l instanceof Float) d += 4 * (Float) l; }
			else if (n.equals("Ooze")) {   // (Ooze.act: 1 + depth / 5 a turn below the sewers, 1 at Goo, else 1 every other turn; its turns are shown)
				Object l = field(b, "left"); int dd = com.shatteredpixel.shatteredpixeldungeon.Dungeon.scalingDepth();
				if (l instanceof Float) d += (Float) l * (dd > 5 ? 1 + dd / 5 : dd == 5 ? 1 : 0.5); }
			else if (n.equals("Corrosion")) {   // (Corrosion.act: its damage a turn, +1 a turn up to depth / 2 + 2, then +0.5)
				Object l = field(b, "left"), dm = field(b, "damage"); int cap = com.shatteredpixel.shatteredpixeldungeon.Dungeon.scalingDepth() / 2 + 2;
				if (l instanceof Float && dm instanceof Float) { float x = (Float) dm; for (int k = 0; k < (Float) l && k < 60; k++) { d += (int) x; x += x < cap ? 1 : 0.5f; } } }
			else if (n.equals("Burning")) { Object l = field(b, "left"); if (l instanceof Float) d += (Float) l * (4 + com.shatteredpixel.shatteredpixeldungeon.Dungeon.depth / 4.0) / 2; }
		}
		return d;
	}

	static double augDmg(Weapon w) { return w.augment == Weapon.Augment.SPEED ? 0.7 : w.augment == Weapon.Augment.DAMAGE ? 1.5 : 1; }
	static double augDly(Weapon w) { return w.augment == Weapon.Augment.SPEED ? 2 / 3.0 : w.augment == Weapon.Augment.DAMAGE ? 5 / 3.0 : 1; }
	static double augEva(Armor a) { return a.augment == Armor.Augment.EVASION ? 2 : a.augment == Armor.Augment.DEFENSE ? -2 : 0; }

	static Object field(Object o, String name) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try { java.lang.reflect.Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
			catch (NoSuchFieldException e) { } catch (Exception e) { return null; }
		}
		return null;
	}
}
