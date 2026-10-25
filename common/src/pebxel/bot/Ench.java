package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;

/**
 * An enchantment's or glyph's worth as flat factors on a fight's numbers (the procs are rolls on every blow; on average
 * they come to about this). Only what is shown counts: a curse once it is known (Weapon.name / Armor.name). Rough
 * figures from the procs' code (items/weapon/enchantments, items/armor/glyphs; proc chances near (lvl+1)/(lvl+3) to
 * (lvl+1)/(lvl+5) at low levels); the order of the gear matters more than these decimals.
 */
public final class Ench {
	/** Damage dealt and accuracy (weapon), damage taken (armour or a weapon that slows / dazes foes); evasion turned off
	 *  (the Stone glyph: no evasion, more armour); a curse. */
	public final double dmg, acc, taken; public final boolean stone, curse;
	public final String name;

	Ench(String name, double dmg, double acc, double taken, boolean stone, boolean curse) { this.name = name; this.dmg = dmg; this.acc = acc; this.taken = taken; this.stone = stone; this.curse = curse; }

	public static final Ench NONE = new Ench("none", 1, 1, 1, false, false);

	/** The shown enchantment / glyph of i (NONE for a hidden curse or none). */
	public static Ench of(Item i) {
		Object e = null; boolean shown = true;
		if (i instanceof Weapon) { Weapon w = (Weapon) i; e = w.enchantment; shown = e == null || i.cursedKnown || !w.enchantment.curse(); }
		else if (i instanceof Armor) { Armor a = (Armor) i; e = a.glyph; shown = e == null || i.cursedKnown || !a.glyph.curse(); }
		return e == null || !shown ? NONE : byName(e.getClass().getSimpleName());
	}

	public static Ench byName(String n) {
		switch (n) {
			// weapon enchantments (common 10% each, uncommon 5%, rare 2.5% of the enchanted)
			case "Blazing": return new Ench(n, 1.25, 1, 1, false, false);
			case "Chilling": return new Ench(n, 1.05, 1, 0.9, false, false);
			case "Kinetic": return new Ench(n, 1.15, 1, 1, false, false);
			case "Shocking": return new Ench(n, 1.2, 1, 1, false, false);
			case "Venomous": return new Ench(n, 1.2, 1, 1, false, false);
			case "Blocking": return new Ench(n, 1, 1, 0.88, false, false);
			case "Blooming": return new Ench(n, 1, 1, 0.97, false, false);
			case "Eldritch": return new Ench(n, 1, 1, 0.9, false, false);
			case "Elastic": return new Ench(n, 0.95, 1, 0.88, false, false);
			case "Lucky": return new Ench(n, 1.05, 1, 1, false, false);
			case "Projecting": return new Ench(n, 1.02, 1, 1, false, false);
			case "Unstable": return new Ench(n, 1.12, 1, 0.97, false, false);
			case "Vorpal": return new Ench(n, 1.2, 1, 1, false, false);
			case "Corrupting": return new Ench(n, 1.15, 1, 0.95, false, false);
			case "Grim": return new Ench(n, 1.2, 1, 1, false, false);
			case "Vampiric": return new Ench(n, 1, 1, 0.85, false, false);
			// weapon curses (items/weapon/curses)
			case "Annoying": return new Ench(n, 1, 1, 1.05, false, true);
			case "Displacing": return new Ench(n, 0.9, 1, 1, false, true);
			case "Dazzling": return new Ench(n, 1, 1, 1.08, false, true);
			case "Explosive": return new Ench(n, 1.05, 1, 1.15, false, true);
			case "Friendly": return new Ench(n, 0.85, 1, 1, false, true);
			case "Polarized": return new Ench(n, 0.75, 1, 1, false, true);
			case "Pressurized": return new Ench(n, 1, 1, 1.1, false, true);
			case "Sacrificial": return new Ench(n, 1, 1, 1.12, false, true);
			case "Wayward": return new Ench(n, 1, 0.5, 1, false, true);
			case "Wondrous": return new Ench(n, 1, 1, 1.02, false, true);
			// glyphs (common 12.5%, uncommon 6.67%, rare 3.33% of the inscribed)
			case "Obfuscation": return new Ench(n, 1, 1, 0.97, false, false);
			case "Swiftness": return new Ench(n, 1, 1, 0.97, false, false);
			case "Viscosity": return new Ench(n, 1, 1, 0.82, false, false);
			case "Potential": return new Ench(n, 1, 1, 0.98, false, false);
			case "Brimstone": return new Ench(n, 1, 1, 0.97, false, false);
			case "Stone": return new Ench(n, 1, 1, 0.85, true, false);
			case "Entanglement": return new Ench(n, 1, 1, 0.9, false, false);
			case "Repulsion": return new Ench(n, 1, 1, 0.88, false, false);
			case "Camouflage": return new Ench(n, 1, 1, 0.95, false, false);
			case "Flow": return new Ench(n, 1, 1, 0.98, false, false);
			case "Affection": return new Ench(n, 1, 1, 0.85, false, false);
			case "AntiMagic": return new Ench(n, 1, 1, 0.95, false, false);
			case "Thorns": return new Ench(n, 1.05, 1, 0.92, false, false);
			// armour curses (items/armor/curses)
			case "AntiEntropy": return new Ench(n, 1, 1, 1.15, false, true);
			case "Corrosion": return new Ench(n, 1, 1, 1.1, false, true);
			case "Displacement": return new Ench(n, 1, 1, 1.02, false, true);
			case "Metabolism": return new Ench(n, 1, 1, 1.12, false, true);
			case "Multiplicity": return new Ench(n, 1, 1, 1.2, false, true);
			case "Stench": return new Ench(n, 1, 1, 1.08, false, true);
			case "Overgrowth": return new Ench(n, 1, 1, 1.02, false, true);
			case "Bulk": return new Ench(n, 1, 1, 1.08, false, true);
		}
		return NONE;
	}

	/** The curses a random weapon / armour may carry (each as likely: Enchantment.randomCurse / Glyph.randomCurse). */
	public static final String[] WEAPON_CURSES = { "Annoying", "Displacing", "Dazzling", "Explosive", "Friendly", "Polarized", "Pressurized", "Sacrificial", "Wayward", "Wondrous" };
	public static final String[] ARMOR_CURSES = { "AntiEntropy", "Corrosion", "Displacement", "Metabolism", "Multiplicity", "Stench", "Overgrowth", "Bulk" };
}
