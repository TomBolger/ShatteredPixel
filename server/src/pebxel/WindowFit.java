package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.*;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.GroupAccess;
import com.watabou.noosa.ui.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every kind of window, with long content, against the screen (Thomas: "several menus open taller than the whole
 * screen, giving them no top and no bottom"): one that fits lies inside the view; a taller one (it scrolls) keeps its
 * frame's top and bottom at the view's ends (pebxel.ui.Caps), at the top of its scroll, the foot and between, and a
 * tabbed one its tabs in sight, and each of a tall one's buttons framed whole at some press of Down. Called by
 * RefinementTest (Emery, and SCREEN=gabbro; FITONLY=1 runs only this). Prints a line per window.
 */
public final class WindowFit {
	static final String LONG = "The Dungeon lies right beneath the City, its upper levels actually constitute the City's sewer system. "
			+ "As dark energy has crept up from below the usually harmless sewer creatures have become more and more dangerous. "
			+ "The city sends guard patrols down here to try and maintain safety for those above, but they are slowly failing. "
			+ "This place is dangerous, but at least the evil magic at work here is weak, and the _treasures_ are many. "
			+ "Many adventurers have come before you; few came back, and none of them spoke of the deepest floors.";

	/** The longest talent descriptions at tiers 1 to 4. */
	static final com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent[] LONG_TALENTS = {
			com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.CACHED_RATIONS, com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.RECALL_INSCRIPTION,
			com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.AURA_OF_PROTECTION, com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.BEAMING_RAY};
	static boolean upgraded;
	static final WndInfoTalent.TalentButtonCallback UPGRADE = new WndInfoTalent.TalentButtonCallback() {
		@Override public String prompt() { return "Upgrade Talent"; }
		@Override public void call() { upgraded = true; }
	};

	public static void check(Runnable frame, Controls oc) {
		Item weapon = Dungeon.hero.belongings.weapon;
		com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TimekeepersHourglass longItem = new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TimekeepersHourglass();
		longItem.identify();
		LinkedHashMap<String, Supplier<Window>> wins = new LinkedHashMap<>();
		wins.put("bag", () -> new WndBag(Dungeon.hero.belongings.backpack));
		wins.put("item (long)", () -> new WndUseItem(null, longItem));
		wins.put("item info (long)", () -> new WndInfoItem(longItem));
		wins.put("hero sheet", WndHero::new);
		wins.put("talents", () -> { WndHero.lastIdx = 1; pebxel.ui.Swaps.nativeHero = true; return new WndHero(); });
		wins.put("menu", WndGame::new);
		wins.put("catalogue", pebxel.ui.WndWatchRecords::catalogue);
		wins.put("journal", pebxel.ui.WndWatchRecords::journal);
		wins.put("badges", () -> { WndJournal.last_index = 4; return new WndJournal(); });
		wins.put("guide", () -> { WndJournal.last_index = 1; return new WndJournal(); });
		wins.put("alchemy guide", () -> { WndJournal.last_index = 2; return new WndJournal(); });
		wins.put("settings", WndSettings::new);
		wins.put("challenges", () -> new WndChallenges(0, false));
		wins.put("hero info", () -> new WndHeroInfo(HeroClass.WARRIOR));
		wins.put("game in progress", () -> new WndGameInProgress(1));
		wins.put("story (long)", () -> new WndStory(LONG));
		wins.put("message (long)", () -> new WndMessage(LONG));
		wins.put("titled (long)", () -> new WndTitledMessage(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.get(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.JOURNAL), "A long title for a window", LONG + " " + LONG));
		wins.put("options (many)", () -> new WndOptions("A question", LONG, "One", "Two", "Three", "Four", "Five", "Six"));
		wins.put("mob", () -> new WndInfoMob(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo()));
		wins.put("quest", () -> new WndQuest(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost(), LONG));
		wins.put("ghost reward", () -> new WndSadGhost(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost(), 1));
		wins.put("wandmaker", () -> new WndWandmaker(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker(), new com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust()));
		wins.put("blacksmith", () -> new WndBlacksmith(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith(), Dungeon.hero));
		wins.put("shop item", () -> new WndTradeItem(longItem, null));
		// a talent's description with the Upgrade button under it (the longest at each tier: Thomas: "I can't spend tier 2
		// talents. The description takes up the whole screen and there's no button")
		for (int k = 0; k < LONG_TALENTS.length; k++) {
			com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent t = LONG_TALENTS[k];
			wins.put("talent t" + (k + 1) + " " + t.name().toLowerCase() + " (long)", () -> new WndInfoTalent(t, 0, UPGRADE));
		}
		// the Stone of Intuition's guess windows (Thomas: icons off the window, the guess button under them): a potion, a scroll, a ring
		for (Item guessed : new Item[]{new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength(), new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby(), new com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShroudingFog(), new com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfWealth()})
			wins.put("intuition guess " + guessed.getClass().getSimpleName(), () -> {
				if (guessed.isIdentified() || (guessed instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion && ((com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion) guessed).isKnown())
						|| (guessed instanceof com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll && ((com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll) guessed).isKnown())
						|| (guessed instanceof com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring && ((com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring) guessed).isKnown())) throw new IllegalStateException("already known");
				return new com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition().new WndGuess(guessed);
			});
		wins.put("subclass choice", () -> new WndChooseSubclass(new com.shatteredpixel.shatteredpixeldungeon.items.TengusMask(), Dungeon.hero));
		wins.put("subclass info", () -> new WndInfoSubclass(HeroClass.CLERIC, com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.PALADIN));
		wins.put("armor ability choice", () -> new WndChooseAbility(new com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown(), Dungeon.hero.belongings.armor, Dungeon.hero));
		wins.put("armor ability info", () -> new WndInfoArmorAbility(HeroClass.CLERIC, new com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.cleric.PowerOfMany()));
		wins.put("journal entry (long)", () -> new WndJournalItem(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.get(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.JOURNAL), "A journal entry", LONG + " " + LONG));
		wins.put("talent no button (long)", () -> new WndInfoTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.RECALL_INSCRIPTION, 1, null));
		more(wins, longItem);
		int tall = 0;
		java.util.List<String> fails = new java.util.ArrayList<>();
		Window prev = null; String prevName = null;
		for (Map.Entry<String, Supplier<Window>> e : wins.entrySet()) {
			if (System.getenv("FITWIN") != null && !e.getKey().contains(System.getenv("FITWIN"))) continue;
			if (prev != null) backTest(prevName, prev, oc, frame, fails);
			prev = null;
			for (int i = 0; i < 20 && Ui.topWindow() != null; i++) { Ui.topWindow().hide(); frame.run(); }
			try {
			Window made;
			try { made = e.getValue().get(); } catch (Throwable t) { System.out.println("  fit: " + e.getKey() + ": not made here (" + t + ")"); continue; }
			GameScene.show(made);
			pebxel.ui.Swaps.nativeHero = false;
			for (int i = 0; i < 6; i++) frame.run();
			Window w = Ui.topWindow();
			check(w != null, e.getKey() + ": shown");
			prev = w; prevName = e.getKey();
			String name = e.getKey() + " (" + w.getClass().getSimpleName() + ")";
			if (w instanceof pebxel.ui.WatchWindow && ((pebxel.ui.WatchWindow) w).fixed) { System.out.println("  fit: " + name + ": fills the screen by design"); continue; }
			Camera c = w.camera;
			int vt = Controls.viewTop(w), vb = Controls.viewBottom(w);
			String shots = System.getenv("WINSHOTS");   // (a folder: a picture of each window, at the top and foot of its scroll)
			String file = e.getKey().replaceAll("[^a-z]+", "_");
			if (shots != null) try { RefinementTest.pic(shots, file); } catch (Exception x) { throw new RuntimeException(x); }
			edges(w, name, oc);
			if (w instanceof pebxel.ui.WndWatchTalent) talent((pebxel.ui.WndWatchTalent) w, name, oc, frame, vt, vb);
			if (!Controls.tall(w)) {
				check(pebxel.ui.Caps.clip(c) == null, name + ": fits, no caps");
				check(c.y >= vt - 2 && c.y + c.screenHeight() <= vb + 2, name + ": fits, inside the view (" + c.y + ".." + (c.y + c.screenHeight()) + " of " + vt + ".." + vb + ")");
				usable(w, name, oc, frame);
				System.out.println("  fit: " + name + ": fits (" + (int) (c.screenHeight() / 2) + " px tall, zoom " + c.zoom + ", " + (int) (c.screenWidth() / 2) + " wide)");
				continue;
			}
			tall++;
			for (int end : new int[]{100000, -60, -100000}) {   // its top, a little down, its foot
				Controls.scroll(w, end); frame.run(); frame.run();
				if (shots != null && end == -100000) try { RefinementTest.pic(shots, file + "_foot"); } catch (Exception x) { throw new RuntimeException(x); }
				pebxel.ui.Caps caps = null;
				for (Gizmo g : GroupAccess.members(w)) if (g instanceof pebxel.ui.Caps) caps = (pebxel.ui.Caps) g;
				if (Screen.round && w instanceof pebxel.ui.WatchWindow) { check(false, name + ": a tall window of ours on a round screen has no caps"); }
				check(caps != null && caps.visible, name + ": tall, its frame's ends held on the screen");
				float[] ends = caps.ends();   // (phone px)
				check(Math.abs(ends[0] - vt) <= c.zoom && Math.abs(ends[1] - vb) <= c.zoom, name + ": caps at the view's ends (" + ends[0] + ".." + ends[1] + " of " + vt + ".." + vb + ")");
				edges(w, name + " scrolled " + end, oc);
				if (w instanceof WndTabbed) for (Component t : tabs(w)) {
					float ty = c.y + (t.top() - c.scroll.y) * c.zoom, tb = c.y + (t.bottom() - c.scroll.y) * c.zoom;
					check(ty >= vt && tb <= vb + 1, name + ": its tabs in sight (" + ty + ".." + tb + ")");
				}
			}
			int n = reach(w, name, oc, frame);
			System.out.println("  fit: " + name + ": " + (int) (c.screenHeight() / 2) + " px tall, scrolls inside its frame, " + n + " buttons reached with Down");
			} catch (AssertionError | RuntimeException x) { fails.add(e.getKey() + ": " + x.getMessage()); System.out.println("  FAIL " + e.getKey() + ": " + x.getMessage()); }
		}
		if (prev != null) backTest(prevName, prev, oc, frame, fails);
		while (Ui.topWindow() != null) { Ui.topWindow().hide(); frame.run(); }
		if (!fails.isEmpty()) throw new AssertionError(fails.size() + " windows fail: " + fails);
		System.out.println("windows fit the screen or scroll inside a whole frame: ok (" + wins.size() + " kinds, " + tall + " tall, " + Screen.w + "px)");
	}


	/** The rest: every other window a player can meet (questgivers with their choices, shops, endings, notices, scroll and stone windows). */
	static void more(LinkedHashMap<String, Supplier<Window>> wins, Item longItem) {
		String P = "com.shatteredpixel.shatteredpixeldungeon.";
		Item potion = new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing();
		Item wand = new com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast();
		wand.identify();
		wins.put("resurrect", () -> new WndResurrect(new com.shatteredpixel.shatteredpixeldungeon.items.Ankh()));
		wins.put("info cell", () -> new WndInfoCell(Dungeon.hero.pos));
		wins.put("info trap", () -> new WndInfoTrap(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap().set(Dungeon.hero.pos)));
		wins.put("info plant", () -> new WndInfoPlant(new com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom()));
		wins.put("info buff", () -> new WndInfoBuff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(Dungeon.hero, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger.class)));
		wins.put("badge", () -> new WndBadge(com.shatteredpixel.shatteredpixeldungeon.Badges.Badge.MONSTERS_SLAIN_1, true));
		wins.put("score", WndScoreBreakdown::new);
		wins.put("list", () -> new WndList(new String[]{"One thing to know", LONG}));
		wins.put("upgrade", () -> new WndUpgrade(new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade(), Dungeon.hero.belongings.weapon, false));
		wins.put("upgrade (long)", () -> new WndUpgrade(new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade(), longItem, true));
		wins.put("shop buy", () -> { com.shatteredpixel.shatteredpixeldungeon.items.Heap h = new com.shatteredpixel.shatteredpixeldungeon.items.Heap(); h.type = com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.FOR_SALE; h.drop(wand); return new WndTradeItem(h); });
		wins.put("shop buy (long)", () -> { com.shatteredpixel.shatteredpixeldungeon.items.Heap h = new com.shatteredpixel.shatteredpixeldungeon.items.Heap(); h.type = com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.FOR_SALE; h.drop(longItem); return new WndTradeItem(h); });
		wins.put("shop sell", () -> new WndTradeItem(potion, null));
		wins.put("shop sell (long)", () -> new WndTradeItem(longItem, null));
		wins.put("energize", () -> new WndEnergizeItem(potion, null));
		wins.put("hard notice", () -> new WndHardNotification(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.get(com.shatteredpixel.shatteredpixeldungeon.ui.Icons.WARNING), "A notice", LONG, "Fine", 3));
		wins.put("error", () -> new WndError(LONG));
		wins.put("options condensed", () -> new WndOptionsCondensed("A question", LONG, "One", "Two", "Three", "Four", "Five", "Six"));
		wins.put("options (long labels)", () -> new WndOptions("A question", "Pick", "A fairly long choice that wraps over lines", "Another fairly long choice that wraps", "Short"));
		wins.put("victory", WndVictoryCongrats::new);
		wins.put("dailies", WndDailies::new);
		wins.put("quick bag", () -> new WndQuickBag(Dungeon.hero.belongings.backpack));
		wins.put("document", () -> new WndDocument(com.shatteredpixel.shatteredpixeldungeon.journal.Document.ADVENTURERS_GUIDE));
		wins.put("imp reward", () -> new WndImpOld(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp(), new com.shatteredpixel.shatteredpixeldungeon.items.quest.DwarfToken()));
		wins.put("imp talk", () -> new WndQuest(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp(), LONG));
		wins.put("ghost talk", () -> new WndQuest(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost(), "Short."));
		wins.put("wandmaker (long)", () -> new WndWandmaker(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker(), new com.shatteredpixel.shatteredpixeldungeon.items.quest.Embers()));
		wins.put("blacksmith (rich)", () -> {
			com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith.Quest.favor = 2000;
			com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith.Quest.freePickaxe = true;
			com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith.Quest.smithRewards = null;
			return new WndBlacksmith(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith(), Dungeon.hero);
		});
		wins.put("blacksmith smith", () -> {
			com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith.Quest.generateRewards(false);
			return new WndBlacksmith.WndSmith(new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith(), Dungeon.hero);
		});
		wins.put("augment", () -> new com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAugmentation().new WndAugment(Dungeon.hero.belongings.weapon));
		wins.put("metamorph choose", () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMetamorphosis.WndMetamorphChoose());
		wins.put("enchant select", () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfEnchantment.WndEnchantSelect((com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon) Dungeon.hero.belongings.weapon, com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon.Enchantment.randomCommon(), com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon.Enchantment.randomUncommon(), com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon.Enchantment.randomRare()));
		wins.put("alchemize item", () -> new com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize.WndAlchemizeItem(potion, null));
		wins.put("catalyst", () -> new com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst.WndTrinket(new com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst()));
		// a Cleric with every spell (all talents, a subclass), the casting window and its info page
		for (boolean info : new boolean[]{false, true})
			wins.put("cleric spells" + (info ? " info" : ""), () -> {
				com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero c = new com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero();
				c.heroClass = HeroClass.CLERIC; c.lvl = 30;
				com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.initClassTalents(c);
				for (com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent t : com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.values()) for (java.util.LinkedHashMap<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> m : c.talents) m.put(t, 1);
				c.subClass = com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.PRIEST;
				return new WndClericSpells(new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome(), c, info);
			});
		// (the Scroll of Metamorphosis' replace choice, the Divination result, the Dried Rose's ghost, the Trinity's item choice, the
		// enchanting scroll's glyph choice and cancel confirm, a scroll's cancel confirm, the journal's note choices)
		wins.put("metamorph replace talent", () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMetamorphosis.WndMetamorphReplace(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.RECALL_INSCRIPTION, 2));
		wins.put("divination", () -> {
			java.util.ArrayList<Item> ided = new java.util.ArrayList<>();
			ided.add(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing()); ided.add(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength());
			ided.add(new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify()); ided.add(new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby()); ided.add(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation());
			return (Window) make("items.scrolls.exotic.ScrollOfDivination$WndDivination", new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfDivination(), ided);
		});
		wins.put("ghost hero", () -> { com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose r = new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose(); r.identify(); return (Window) make("items.artifacts.DriedRose$WndGhostHero", null, r); });
		wins.put("trinity item type", () -> (Window) make("actors.hero.abilities.cleric.Trinity$WndItemtypeSelect", null, new com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome(), com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.RecallInscription.INSTANCE));
		wins.put("glyph select", () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfEnchantment.WndGlyphSelect((com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor) Dungeon.hero.belongings.armor, new com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Viscosity(), new com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Obfuscation(), new com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Thorns()));
		wins.put("enchant cancel", () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfEnchantment.WndConfirmCancel());
		wins.put("scroll cancel", () -> (Window) make("items.scrolls.InventoryScroll$WndConfirmCancel", new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify()));
		wins.put("note type", () -> (Window) make("ui.CustomNoteButton$WndNoteTypeSelect", new com.shatteredpixel.shatteredpixeldungeon.ui.CustomNoteButton()));
		wins.put("note depth", () -> (Window) make("ui.CustomNoteButton$WndDepthSelect", new com.shatteredpixel.shatteredpixeldungeon.ui.CustomNoteButton()));
		wins.put("note item", () -> (Window) make("ui.CustomNoteButton$WndItemtypeSelect", null));
		wins.put("monk abilities", () -> new WndMonkAbilities(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.affect(Dungeon.hero, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MonkEnergy.class)));
	}


	/** A window of a private or inner class, by reflection: its constructor takes the outer object first when there is one. */
	static Object make(String cls, Object outer, Object... args) {
		try {
			Class<?> c = Class.forName("com.shatteredpixel.shatteredpixeldungeon." + cls);
			for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
				Class<?>[] pt = k.getParameterTypes();
				int off = outer != null ? 1 : 0;
				if (pt.length != args.length + off) continue;
				boolean ok = true;
				for (int i = 0; i < args.length; i++) if (!pt[i + off].isInstance(args[i])) ok = false;
				if (!ok) continue;
				k.setAccessible(true);
				Object[] all = new Object[pt.length];
				if (outer != null) all[0] = outer;
				System.arraycopy(args, 0, all, off, args.length);
				return k.newInstance(all);
			}
			throw new IllegalStateException("no constructor for " + cls);
		} catch (java.lang.reflect.InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
		catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
	}

	/** Back closes the window (or, for a few that must be answered, does not: listed). */
	static final java.util.Set<String> MUST_ANSWER = new java.util.HashSet<>(java.util.Arrays.asList("hard notice", "resurrect", "victory", "blacksmith smith", "enchant select", "catalyst", "augment", "glyph select", "enchant cancel", "scroll cancel", "metamorph replace talent"));
	static void backTest(String key, Window w, Controls oc, Runnable frame, java.util.List<String> fails) {
		if (Ui.topWindow() != w || MUST_ANSWER.contains(key)) return;
		try { oc.button(Controls.BACK); for (int i = 0; i < 4; i++) frame.run(); } catch (RuntimeException x) { System.out.println("  fit: " + key + ": its Back runs the game's own cancel (" + x + ")"); }
		if (Ui.topWindow() == w) { fails.add(key + ": Back does not close it"); System.out.println("  FAIL " + key + ": Back does not close it"); }
	}

	/** A window that fits: each of its buttons gets the focus by Down (and is whole on the screen, inside the circle on a round one, with a focus box drawn) and Up goes back. */
	static void usable(Window w, String name, Controls oc, Runnable frame) {
		if (w instanceof pebxel.ui.WatchWindow) return;   // (our own windows have their own tests)
		java.util.List<Component> want = new java.util.ArrayList<>();
		for (Ui.Part p : Controls.buttons(w)) if (p.g.camera() == w.camera) want.add((Component) p.g);
		if (want.isEmpty()) return;
		java.util.Set<Component> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int i = 0; i <= want.size() + 1 && Ui.topWindow() == w; i++) {
			frame.run();
			Component f = oc.focused();
			if (f != null && want.contains(f)) { seen.add(f); geom(w, f, name, oc, i == 0); }
			oc.button(Controls.DOWN);
		}
		check(seen.size() == want.size(), name + ": every button gets the focus with Down (" + seen.size() + " of " + want.size() + ")");
	}

	/** The focused button inside the screen (a round one's circle), whole, its focus box drawn and inside too. */
	static void geom(Window w, Component k, String name, Controls oc, boolean first) {
		Camera c = w.camera;
		float[] r = {(c.x + (k.left() - c.scroll.x) * c.zoom) / 2, (c.y + (k.top() - c.scroll.y) * c.zoom) / 2, 0, 0};
		r[2] = r[0] + k.width() * c.zoom / 2; r[3] = r[1] + k.height() * c.zoom / 2;
		inside(r, name + ": button " + label(k));
		Object[] hl = oc.highlight();
		if (k instanceof pebxel.ui.Focusable) return;   // (draws its own focus)
		if (hl == null && first) return;   // (the Assistant would close it: no box until Up or Down, Controls.closeFirst)
		check(hl != null && hl[0] == w, name + ": the focus box is drawn round " + label(k) + " (" + (hl == null ? "none" : hl[0].getClass().getSimpleName()) + ", focused " + (oc.focused() == k) + ")");
		float[] q = (float[]) hl[1];
		float[] b = {(c.x + (q[0] - c.scroll.x) * c.zoom) / 2, (c.y + (q[1] - c.scroll.y) * c.zoom) / 2, 0, 0};
		b[2] = b[0] + q[2] * c.zoom / 2; b[3] = b[1] + q[3] * c.zoom / 2;
		inside(b, name + ": focus box on " + label(k));
	}

	/** A rectangle (watch px) inside the screen, and on a round one inside its circle (a pixel's grace). */
	static void inside(float[] r, String what) {
		check(r[0] >= -1 && r[1] >= -1 && r[2] <= Screen.w + 1 && r[3] <= Screen.h + 1, what + " inside the screen (" + r[0] + "," + r[1] + ".." + r[2] + "," + r[3] + ")");
		if (!Screen.round) return;
		float cx = Screen.w / 2f, cy = Screen.h / 2f, rad = Screen.w / 2f + 3;
		for (float x : new float[]{r[0], r[2]}) for (float y : new float[]{r[1], r[3]})
			check((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rad * rad, what + " inside the circle (corner " + x + "," + y + ")");
	}

	/** A tall window's buttons (on its own camera) each framed whole on the screen at some press of Down from its top,
	 *  then Select's (Thomas: "there's no button to apply the talent point or way to scroll"). Returns how many. */
	static int reach(Window w, String name, Controls oc, Runnable frame) {
		java.util.List<Component> want = new java.util.ArrayList<>();
		java.util.List<Component> tabs = w instanceof WndTabbed ? tabs(w) : java.util.Collections.emptyList();   // (held at the frame's foot)
		for (Ui.Part p : Controls.buttons(w)) if (p.g.camera() == w.camera && !tabs.contains(p.g)) want.add((Component) p.g);
		Controls.scroll(w, 100000); frame.run();
		Component first = oc.focused();
		if (first != null && Controls.reach(w, first) <= (Controls.viewBottom(w) - Controls.viewTop(w)) / 4f) Controls.keepInView(w, first);   // (as it opens: Controls.frame)
		java.util.Set<Component> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int i = 0; i < 80 && seen.size() < want.size() && Ui.topWindow() == w; i++) {
			frame.run();
			Component f = oc.focused();
			if (f != null && want.contains(f) && Controls.visible(w, f)) { if (seen.add(f)) geom(w, f, name, oc, i == 0); }
			if (System.getenv("FITDBG") != null) System.out.println("    reach " + name + " i " + i + " focused " + (f == null ? null : label(f)) + " at " + (f == null ? 0 : w.camera.y + (f.top() - w.camera.scroll.y) * w.camera.zoom) + " cam " + w.camera.y + " visible " + (f != null && Controls.visible(w, f)) + " why " + Controls.windowWhy);
			oc.button(Controls.DOWN);
		}
		if (seen.size() < want.size()) {
			StringBuilder why = new StringBuilder();
			int[] a = Controls.area(w);
			for (Component k : want) if (!seen.contains(k)) why.append(" ").append(label(k)).append(" at ").append(w.camera.y + (k.top() - w.camera.scroll.y) * w.camera.zoom).append("..").append(w.camera.y + (k.bottom() - w.camera.scroll.y) * w.camera.zoom);
			check(false, name + ": every button framed whole on the screen with Down (" + seen.size() + " of " + want.size() + "; area " + a[0] + ".." + a[1] + ";" + why + ")");
		}
		return seen.size();
	}

	/** The watch's talent window: inside the view, its button whole at the foot and Select's, the text scrolled to its
	 *  end with Down (and back with Up). */
	static void talent(pebxel.ui.WndWatchTalent w, String name, Controls oc, Runnable frame, int vt, int vb) {
		Camera c = w.camera;
		Component b = w.focused();
		if (b != null) {
			float by = c.y + (b.bottom() - c.scroll.y) * c.zoom;
			check(by <= vb && c.y + (b.top() - c.scroll.y) * c.zoom >= vt, name + ": its button whole on the screen (" + by + " of " + vb + ")");
			frame.run();
			check(oc.focused() == b, name + ": Select presses its button");
		}
		int[] s = w.scrolled();
		if (s[2] <= s[1]) return;
		for (int i = 0; i < 20 && w.scrolled()[0] + s[1] < s[2]; i++) { oc.button(Controls.DOWN); frame.run(); }
		check(w.scrolled()[0] + s[1] == s[2], name + ": Down reads to the last line (" + java.util.Arrays.toString(w.scrolled()) + ")");
		for (int i = 0; i < 20 && w.scrolled()[0] > 0; i++) { oc.button(Controls.UP); frame.run(); }
		check(w.scrolled()[0] == 0, name + ": Up back to the first line");
		System.out.println("  fit: " + name + ": " + s[2] + " lines, " + s[1] + " at a time");
	}

	/**
	 * Nothing pressable, nor the yellow box round the framed button, is drawn over the window's frame (Thomas: "Continue
	 * and Delete and the exit button are all intersecting with the border, as is the highlight box"): each one as drawn
	 * (cut to where its camera draws: a tall window's between its frame's held ends) clear of the frame's edges.
	 */
	static void edges(Window w, String name, Controls oc) {
		float[] in = Controls.innerRect(w);   // (watch px)
		if (in == null) return;   // (a round screen's own windows: the frame is drawn by shapes)
		float l = in[0], t = in[1], r = in[2], b = in[3], x1 = in[4], x0 = in[5], y1 = in[6], y0 = in[7];
		float[][] border = {{x0, y0, x1, t}, {x0, b, x1, y1}, {x0, t, l, b}, {r, t, x1, b}};
		java.util.List<Object[]> drawn = new java.util.ArrayList<>();
		for (Ui.Part p : Controls.buttons(w)) if (p.g.camera() == w.camera && !inPane(p.g)) drawn.add(new Object[]{"button " + label(p.g), p.g, null});   // (a button inside a scroll pane is cut by the pane's own view)
		for (Gizmo g : GroupAccess.members(w)) if (g instanceof Controls.CloseTag && g.visible) drawn.add(new Object[]{"exit tag", g, null});
		Object[] hl = oc.highlight();
		if (hl != null && hl[0] == w) drawn.add(new Object[]{"highlight box", null, hl[1]});
		Camera c = w.camera;
		int[] cut = pebxel.ui.Caps.clip(c);
		float ct = cut != null ? cut[0] / 2f : -1e9f, cb = cut != null ? cut[1] / 2f : 1e9f;
		if (cut != null && w instanceof WndTabbed) cb = Math.min(cb, b);   // (a tabbed window's tabs hang under the frame; the foot's backing covers what is under its edge)
		java.util.List<Component> tabs = w instanceof WndTabbed ? tabs(w) : java.util.Collections.emptyList();
		for (Object[] d : drawn) {
			float[] q;
			if (d[1] != null) { Component k = (Component) d[1]; if (tabs.contains(k)) continue; q = new float[]{k.left(), k.top(), k.width(), k.height()}; }
			else q = (float[]) d[2];
			float qx0 = (c.x + (q[0] - c.scroll.x) * c.zoom) / 2, qy0 = (c.y + (q[1] - c.scroll.y) * c.zoom) / 2;
			float qx1 = qx0 + q[2] * c.zoom / 2, qy1 = qy0 + q[3] * c.zoom / 2;
			qy0 = Math.max(qy0, ct); qy1 = Math.min(qy1, cb);
			if (qy1 - qy0 < 0.5f || qx1 - qx0 < 0.5f) continue;   // (scrolled out of sight)
			if (d[1] != null && qy0 >= b - 2 && qy1 > y1) continue;   // (a tab hanging under the frame, joined to it by design)
			for (float[] e : border) {
				boolean hit = Math.min(qx1, e[2]) - Math.max(qx0, e[0]) > 0.5f && Math.min(qy1, e[3]) - Math.max(qy0, e[1]) > 0.5f;
				check(!hit, name + ": the " + d[0] + " (" + qx0 + "," + qy0 + ".." + qx1 + "," + qy1 + ") over the frame's edge (" + e[0] + "," + e[1] + ".." + e[2] + "," + e[3] + ")");
			}
		}
	}

	static boolean inPane(Gizmo g) { for (Gizmo a = g.parent; a != null; a = a.parent) if (a instanceof com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane) return true; return false; }

	static String label(Gizmo g) {
		for (Gizmo m : GroupAccess.members((com.watabou.noosa.Group) g)) if (m instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock && !((com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock) m).text().isEmpty()) return "'" + ((com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock) m).text() + "'";
		return g.getClass().getSimpleName().isEmpty() ? g.getClass().getName() : g.getClass().getSimpleName();
	}

	@SuppressWarnings("unchecked")
	static java.util.List<Component> tabs(Window w) {
		try { java.lang.reflect.Field f = WndTabbed.class.getDeclaredField("tabs"); f.setAccessible(true); return (java.util.List<Component>) f.get(w); }
		catch (Exception e) { throw new RuntimeException(e); }
	}

	static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
