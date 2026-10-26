# Strategy-reference coverage audit (agent SR, 2026-10-25, main bbbdf3c)

Read: `docs/strategy-reference.md` (all of it) against `common/src/pebxel/{Assist,As*,Build,Brain,Rules}.java`, `common/src/pebxel/bot/*`, `server/src/pebxel/Bot*.java`. Source checks against `spd/core/.../shatteredpixeldungeon/` (S/).

File keys: A = Assist.java, AB = AsBasics, AI = AsItems, AR = AsArtifacts, AAb = AsAbilities, ABo = AsBosses, AQ = AsQuests, AC = AsChoices, AAl = AsAlchemy, ABl = AsBolts, AAs = AsAscent, Bu = Build, Ru = Rules, Br = Brain, UP = bot/UpgradePlan, GV = bot/GearValue, Tr = bot/Trade, BW = server/BotWindows.

Status: DONE / PARTLY / MISSING / N/A. "outdated?" = possibly outdated advice (flag for agent PN). Section 9 restates sections 2-5; its rows are merged into the earlier ones (only its new points get rows, marked §9).

## 1. The run's arc

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 1.1 | Heal to full before a locked boss floor (no regen mid-fight) | DONE | A:1832, Ru:107 | |
| 1.1 | Hunger stops on a locked floor (rest there costs no food) | DONE | AB:583 | |
| 1.2 | Pick up the Amulet, choose Exit, don't ascend | N/A | BW:149 (Stay), AAs | Superseded: Thomas counts a win as the full climb out. The bot climbs (AAs, Ru:93-95). Guide rule outdated for our goal. |
| 1.3 | Fixed drops (3 SoU, 2 PoS a region) as legal inference for planning | DONE | UP:52-60 (floorsLeft, Sg) | |
| 1.3 | One food a regular floor, in the food budget | DONE | AB:555-559, AB:629 | |
| 1.3 | Never wear gear more than 1 STR short | DONE | GV:53,66; A:1825 (legacy) | |
| 1.3 | Plan T3 by d10, T4 by d15, T5 by d20 | PARTLY | GV:211 (+2 STR look-ahead) | No tier plan; only "would it beat the worn one with 2 more STR". |
| 1.4 | No grinding (0 XP above maxLvl); keep moving | N/A | | The bot never grinds. |

## 2. General principles

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 2.1 | Upgrades into one armour and one weapon | DONE | UP:61-75 | |
| 2.1 | Armour first (to about +3..+5), then the weapon | DONE | UP:96 (ARMB), Tune | |
| 2.1 | No upgrades into rings or minor wands early | DONE | UP:63-71 (not candidates) | |
| 2.1 | Hold SoU for the final piece, but not past when they'd save you; §9: never hold more than 1-2 | PARTLY | UP:116 (hold), Ru:109 (read before boss) | Hold test is "1% gain"; no cap on scrolls held. |
| 2.1 | Past +3 on an enchanted piece: Magical Infusion | PARTLY | AI:959 (cast if held), UP:103 (loss priced) | Infusion is never brewed (SoU + 12 energy). |
| 2.1 | Mage: first 2-3 SoU into the staff | DONE | UP:59, A:1779 | |
| 2.1 | Warrior seal carries one upgrade | N/A | | |
| 2.1 | Huntress: the bow is an SoU target | N/A | UP:30 | Guide error (see guide-errors). |
| 2.1 | An upgrade frees a cursed worn piece | DONE | GV:84-96 (liftable), UP:30 | |
| 2.1 | Read an SoU at once when HP is lost faster than food allows | MISSING | | No link between the food budget (AB) and upgrade timing. |
| 2.2 | All identify-by-use rules (deck odds, drop-source clues, drink/read protocols, curse odds, prize rooms never cursed, crypt armour) | ID covers | | Not checked. |
| 2.2 | Skeleton/remains heaps (haunted) only at good health | DONE | A:1107 | |
| 2.2 | Wear an unknown higher-tier piece when Remove Curse or a spare SoU is in hand | DONE | GV:84-96,134 | (curse odds side: ID) |
| 2.3 | Surprise attacks: the ambush out of the foe's last view | DONE | A:1245-1248, A:1359-1386 | |
| 2.3 | Never fight 2+ enemies in the open | DONE | A:1647-1653, A:1390 | |
| 2.3 | Don't melee ranged/kiting foes; break LoS | DONE | A:1681-1686, AB:379-417, ABl | |
| 2.3 | Skip animated statues until gear outclasses them | PARTLY | A:1206 | Never fights a statue, even when it outclasses it: the guaranteed enchanted weapon is never taken. |
| 2.3 | Mimics: open chests only at good HP | DONE | A:1290 | |
| 2.3 | Escape tools in order (Teleport, Invis, Haste, Blink, Fadeleaf, cloak, stairs) | PARTLY | A:1751, AI:307-353, AI:449, A:1630 | Rogue's cloak never used as an escape (only as an opener, A:1760). |
| 2.3 | Stairs are a real escape | DONE | A:1630-1640, Br:217 | |
| 2.3 | Expect the foes near the stairs when you come back | PARTLY | Ru:87 | Rests before going back; no plan for the welcome party. |
| 2.3 | Drink Healing at ≤30-35% in a fight you must win | DONE | A:1746 (HEALPCT 40, FightModel.healAt), Ru:80 | |
| 2.4 | One step inside a corridor behind the door; hold the doorway | DONE | A:1390-1394, A:1249-1253 | |
| 2.4 | Wait beside the opening; strike as it arrives | DONE | A:1245-1251 | |
| 2.4 | Open doors to attack unaware enemies (creep on sleepers) | PARTLY | AB:284 (BSNEAK off), AAb:421 | Off by default (tested worse). |
| 2.4 | Circle tall grass for repeated surprise hits (works on Goo) | MISSING | | |
| 2.4 | Snakes (evasion 25): surprise-attack or skip | PARTLY | ambush only | No snake rule (skip one awake in the open; zap it: magic 2x accuracy). |
| 2.5 | A deliberate search finds everything in radius: a searched spot is done | DONE | A:373-380, AB:509-536 | Wide Search's +1 radius not counted (A:378). |
| 2.5 | Search dead ends, walls that should connect, blank regions | DONE | A:1047-1083 | |
| 2.5 | Search only when the floor is otherwise done (6 hunger each) | DONE | A:1320, A:1350, AB:650 | |
| 2.5 | Burnt floor often hides traps | MISSING | | |
| 2.5 | Magic Mapping when the exit is hidden | DONE | A:1799 | |
| 2.6 | Avoid chip damage; HP is paid for in food | DONE | ambush/doorway; AB:577 | |
| 2.6 | Dew and Healing instead of long rests when food is short | DONE | A:1833, AB:577-587 | |
| 2.6 | Eat at Hungry or just before resting; never starving | PARTLY | A:1729 (rations only at Starving), AR:375 (Horn at Hungry) | Rations wait for Starving (regen stops there); no "eat before a long rest". K8's area. |
| 2.6 | Mystery meat: cook it (fire, frost, pot) | PARTLY | AAl:46 (stew), A:1738 (raw in water) | No burn/freeze cooking. |
| 2.7 | Buy bags first | PARTLY | Tr:45, A:1405 | Ranked by worth/√price; usually after Ankh and Healing. |
| 2.7 | Buy an Ankh | DONE | Tr:40 | |
| 2.7 | Buy Healing | DONE | Tr:41 | |
| 2.7 | Buy Magic Mapping (for the Halls) | DONE | Tr:49 | |
| 2.7 | Buy food if low | DONE | Tr:43 | |
| 2.7 | Buy Torches before the Halls | N/A | | Only the Imp's depth-20 shop sells torches (S/levels/rooms/special/ShopRoom.java:247-253); the bot skips the vault, so no Imp shop. Guide doesn't say so. |
| 2.7 | Bless the Ankh with a full Waterskin | MISSING | | No `Ankh.AC_BLESS` anywhere; an unblessed Ankh loses the pack. |
| 2.7 | Never hit a shopkeeper with area effects | MISSING | AI:228 (allies only) | Bombs, gas, fire and sprung traps check allies, not NPCs: one throw in a shop loses the shop. |
| 2.7 | Sell junk | DONE | Tr:69-93, BW sell | |
| 2.8 | 3 seeds → potion | DONE | AAl:41-46 | |
| 2.8 | Energize junk potions/items for energy | MISSING | | AAl uses only seeds, meat, the catalyst. |
| 2.8 | Scroll → 2 runestones (Blink, Deep Sleep, Augmentation) | MISSING | | |
| 2.8 | Potion → exotic | MISSING | | |
| 2.8 | Brew Magical Infusion for enchanted gear | MISSING | | |
| 2.8 | Elixirs for boss immunities (Dragon's Blood vs Tengu fire, Toxic Essence vs DM-300 gas) | PARTLY | AI:421-424 (drunk if held) | Never brewed. |
| 2.8 | Tip darts with seeds (no pot) | MISSING | AI:564 (thrown only) | |
| 2.8 | Meat pie | MISSING | | low |
| 2.8 | Trinket Catalyst into a trinket | DONE | AAl:43 | (the choice: 6.10) |
| 2.9 | Keep the Waterskin topped up (dew from grass) | DONE | A:1322, A:1550-1563 | |
| 2.9 | Drink it in emergencies | DONE | A:1749 | |
| 2.9 | Drink it, or keep it full, before a boss | PARTLY | A:1832 (rests instead) | |
| 2.10 | Explore every regular floor fully before descending | DONE | A:1132-1138, A:1324-1329 | |
| 2.10 | Near rooms first, then outward | DONE | A:1275, A:1423-1430 | |
| 2.10 | Locked, trap and special rooms last, with the right tool | PARTLY | AI:754-777 | Done when the potion is known; not deliberately deferred. |
| 2.10 | Don't dawdle: respawns every ~50 turns | PARTLY | AB:650-662 | Only when short of food. |
| 2.10 | Rush the Demon Halls; save Magic Mapping for them | MISSING | A:1799 | MM read at any depth after 250 turns; Halls explored like any floor. |
| 2.10 | Kill the demon spawners | DONE | AB:213-232 | |
| 2.10 | Pick up everything that isn't a known hazard | DONE | A:1269-1294 | |
| 2.10 | Level feelings (dark, large, traps, secrets) change the plan | MISSING | | No code reads `Level.feeling`. |

## 3. Classes

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 3.0 | Subclass per class (Berserker, Warlock, Assassin, Sniper, Champion, Paladin) | DONE | Bu:56-70 | outdated? (pre-v3.2 picks; PN to confirm) |
| 3.0 | Wear Tengu's mask at once | DONE | A:1773 | |
| 3.0 | Use the King's Crown | DONE | AAb:620 | |
| 3.0 | Armour abilities (Leap, Elemental Blast, Smoke Bomb, Ascended Form) | DONE | Bu:155-170 | |
| 3.1 | Warrior talents (Iron Will 2, Hearty 2, Provoked 1; Lethal Momentum, Iron Stomach, Liquid Willpower 2; Strongman 3; Deathless Fury 3) | DONE | Bu:23-27 | |
| 3.1 | Throw stones at range early | DONE | A:1894, AI:594 | |
| 3.1 | Berserk at full rage; Gladiator combo moves | DONE | AAb:331, AAb:349 | |
| 3.1 | "Berserker: armour to +10, then weapon" | DONE | UP (armour first) | outdated? (v3.2 rage nerf) |
| 3.2 | Mage talents (Backup Barrier 2, Scholar's 2; Shield Battery 2, Energizing 2) | PARTLY | Bu:28-32 | Shield Battery gets 1 point, last in T2. |
| 3.2 | Staff zaps; point blank | DONE | A:1884-1901 | |
| 3.2 | Staff wand: Lightning, Disintegration, Blast Wave, Frost | DONE | AI:832-856 | |
| 3.2 | Lightning: don't zap in water next to the target | MISSING | A:1850 | Distance ≥3 only; no water test. |
| 3.2 | Shield Battery self-zap | DONE | AAb:463 | |
| 3.2 | Warlock: open with a zap, then melee | DONE | A:1878-1911 | |
| 3.3 | Rogue talents (Cached Rations 2, Protective Shadows 2, Mystical Meal 2, Silent Steps 2, Light Cloak, Enhanced Lethality 3) | DONE | Bu:33-37 | |
| 3.3 | Use the cloak often | DONE | A:1760-1766 | |
| 3.3 | Assassin preparation; Freerunner | DONE | AAb:396, AAb:412 | |
| 3.4 | Huntress talents (Nature's Bounty 2, Followup 2, Heightened Senses 2, Durable 2, Point Blank 3, Shared Upgrades) | DONE | Bu:38-42 | |
| 3.4 | Break LoS with ranged foes rather than trading shots | DONE | A:1890, A:1899 | |
| 3.4 | Point Blank; Sniper special shot | DONE | AAb:437, AAb:451 | |
| 3.5 | Duelist talents (Patient 2, Aggressive Barrier 2, Focused Meal 2, Lethal Haste 2, Precise Assault, Twin Upgrades 3) | DONE | Bu:43-47 | |
| 3.5 | Weapon abilities; Monk abilities | DONE | AAb:484, AAb:553 | |
| 3.5 | Champion: a utility second weapon (shield, spear) | MISSING | | No second-weapon equip or swap; Twin Upgrades wasted. |
| 3.6 | Cleric talents (Holy Intuition 1, Searing 2, Shield of Light 2; Sunray 2, Bless 2, Enlightening 2; Cleanse; Lay on Hands 3) | PARTLY | Bu:48-52 | Satiated Spells first; Shield of Light, Bless and Sunray get 1 each. |
| 3.6 | Guiding Light; Bless, Sunray, Cleanse, Lay on Hands, Holy Lance | DONE | A:1868, AAb:573-617 | |
| 3.x | Armour abilities used in fights | DONE | AAb:629-670 | |

## 4. Bosses

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 4.0 | Arrive at full HP | DONE | A:1832, Ru:107 | |
| 4.0 | Bring 2+ Healing, a full Waterskin, a blessed Ankh | PARTLY | Ru:109 | Nothing gathers Healing, fills the skin or blesses the Ankh before the boss stairs. |
| 4.0 | Region's SoU and PoS used before the boss | DONE | Ru:109, A:1770 | |
| 4.0 | Can't teleport on boss floors | N/A | | Guide error (see guide-errors). |
| 4.1 | Goo pumps: 3+ away or out of LoS | DONE | A:1671-1679, A:1693-1699 | |
| 4.1 | Hit and run round corners/doors | PARTLY | ambush | |
| 4.1 | Fight on dry floor; keep Goo off water (it heals there) | MISSING | | |
| 4.1 | Water only to clear Ooze | DONE | A:1615 | |
| 4.1 | Fire on Goo | PARTLY | A:1906 (generic potion throw) | |
| 4.1 | Heal earlier against a boss | DONE | A:215 (HEALBOSS 55) | |
| 4.1 | No slow weapons (Goo gets double turns) | PARTLY | GV (delay in the fight model) | No boss-specific swap. |
| 4.2 | Tengu: stand next to him (accuracy 10 vs 20) | MISSING | A:1000 | He counts as "ranged" (no doorway wait), but nothing closes in; thrown/bow skip him (AI:599, A:1890). |
| 4.2 | Remember the flashed dart-trap cells | DONE | A:616-625, ABo:102-105 | |
| 4.2 | Out of the bomb's 2-tile radius by "1..." | DONE | ABo:110-113 | |
| 4.2 | Out of the shocker's pattern; the fire wave | DONE | ABo:114-123 | |
| 4.2 | Frost/water puts out burning | DONE | AI:291-296, A:1595 | |
| 4.2 | Save Invisibility for Tengu; Blink to close in | MISSING | | |
| 4.3 | DM-300: fight off the INACTIVE_TRAP tiles (they give it a barrier) | MISSING | | S/actors/mobs/DM300.java:327-343 |
| 4.3 | Supercharged: run to the lit pylon over safe tiles | DONE | ABo:293-317, ABo:64-67 | |
| 4.3 | Rockfall: step to the safe cell | DONE | ABo:89-94, ABo:147-163 | |
| 4.3 | Purity / Toxic Essence for the gas | DONE | AI:287-290, AI:421-424 | |
| 4.4 | Dwarf King: kill minions first | PARTLY | ABo:347-357 | Only in phase 2 (immune king); phase 1 hits the king while warlocks/monks build up. |
| 4.4 | Keep minions between you and his warlocks | MISSING | | |
| 4.4 | Phase 2: fight at a chokepoint | PARTLY | A:1647 (generic) | |
| 4.4 | Never end a turn on a summoning circle | DONE | ABo:69 | |
| 4.4 | Area damage; Aggression stones on minions, not the boss | DONE | AI:526-544, AI:411-413 | |
| 4.5 | Lure each fist far from Yog | DONE | ABo:237-274 | |
| 4.5 | Yog untouchable while a fist lives; no wasted shots | DONE | ABo:213, ABo:282-286 | |
| 4.5 | Dodge the telegraphed beams; use pillars | DONE | ABo:70-73, ABo:147-163 | Pillars not sought out (dodge only). |
| 4.5 | Rotting fist off water | DONE | ABo:268 | |
| 4.5 | Bring Healing, Invisibility, Mind Vision, MM, burst items | PARTLY | ABo:193 (early Healing) | No pre-Yog kit. |
| 4.6 | Fetid Rat: ranged, or in water outside its gas; never in a dead end | PARTLY | A:1018 (StenchGas avoided), A:1615 | |
| 4.6 | Gnoll Trickster: corner it, break LoS | DONE | A:1673 (SHOOTERS) | |
| 4.6 | Great Crab: surprise only | DONE | A:1232-1244, AB:100-160 | |
| 4.6 | Newborn fire elemental: step off the targeted cell | MISSING | | Not in ABo.danger. |
| 4.6 | Rot Heart: never use fire | PARTLY | AQ:285-293 (melee) | Assist.ranged's Liquid Flame and AI's firebombs have no Rot Heart guard (fire loses the seed). |
| 4.6 | Crystal Spire / Geomancer for +1000 favor | MISSING | | optional |

## 5. Quests

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 5.1 | Always do the Ghost | DONE | AQ:156-170 | |
| 5.1 | Reward: armour unless the weapon is ≥2 tiers up; Duelist weapon; STR | DONE | AC:76-84 | |
| 5.2 | Do the Wandmaker (all three variants) | DONE | AQ:178-293 | |
| 5.2 | Corpse dust: walk straight back | DONE | AQ:74-80 | |
| 5.2 | Embers: freeze/chill item on the elemental | MISSING | | low |
| 5.2 | Wand choice; Mage takes the wand wanted in the staff | PARTLY | AC:90-96 | wandScore ignores staffRank (AI:832) for the Mage. |
| 5.3 | Do the Blacksmith: mine 40 ore | DONE | AQ:312-333 | |
| 5.3 | One trip: clear pack space first | MISSING | | |
| 5.3 | Favor: Reforge with duplicates, then Upgrade, then Smith | PARTLY | AC:99-107 | Never reforges or hardens. |
| 5.3 | Don't wake the guardians mining crystals | MISSING | | low |
| 5.3 | Hunger from digging | DONE | AB:641-647 | |
| 5.4 | Skip the Imp's vault | DONE | AQ:36 | A:1163 still walks to the Imp to talk. |

## 6. Items

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 6.1 | Drink Strength and Experience at once | DONE | A:1770-1772 | |
| 6.1 | Mind Vision to scout a new floor / the Halls | DONE | AI:872-877 | |
| 6.1 | Frost: throw to freeze, or to put out fire | DONE | A:1906, AI:295 | |
| 6.1 | Liquid Flame at groups, Goo, Tengu; burn barricades | DONE | A:1906, AI:797-817 | |
| 6.1 | Toxic Gas at a group in a room you then leave | PARTLY | A:1906 (≥4 away) | Doesn't leave the room. |
| 6.1 | Paralytic Gas from range | DONE | A:1906 | |
| 6.1 | Haste to escape | DONE | AI:340-353 | |
| 6.1 | Invisibility: escape; a sure surprise hit | PARTLY | A:1751 | Escape only. |
| 6.1 | Levitation over traps and chasms | DONE | AI:665, AI:754 | |
| 6.1 | Purity against gas | DONE | AI:287 | |
| 6.1 | Exotics (Shielding, Earthen Armor, Snap Freeze, Corrosive, Dragon's Breath, Shrouding Fog, Mastery, Divine Inspiration, Might) | DONE | AI:325, AI:419, AI:551-559, AI:865-867 | |
| 6.1 | Brews thrown | DONE | AI:553-556 | |
| 6.2 | Keep one Remove Curse spare | PARTLY | AR:540, AI:970 | Not reserved in general. |
| 6.2 | Transmutation never on main gear | DONE | AI:917 | |
| 6.2 | Mirror Image, Recharging, Rage+AoE, Retribution, Terror, Lullaby, Teleport | DONE | A:1751, AI:312, AI:367, AI:383-393 | |
| 6.2 | Exotic scrolls (Divination, Foresight, Mystical Energy, Enchantment, Siren's Song, Dread, Psionic Blast, Prismatic Image, Passage, Anti-Magic) | DONE | AI:321-330, AI:383-396, AI:474, AI:898-903 | Challenge, Metamorphosis unused (low). |
| 6.3 | Augmentation, Enchantment stones | DONE | AI:925-928, AI:992-997 | |
| 6.3 | Blink: escape; Tengu gap close | PARTLY | AI:314 | Escape only. |
| 6.3 | Deep Sleep, Shock, Fear, Blast, Clairvoyance, Aggression | DONE | AI:318-319, AI:374-376, AI:411-415, AI:541, AI:932 | |
| 6.3 | Flock: block a corridor | MISSING | AI:642 (thrown as junk) | low |
| 6.3 | Intuition, Detect Magic | ID covers | | |
| 6.4 | Fireblast burns scrolls/loot | DONE | A:1851, AI:232-239 | |
| 6.4 | Corruption, Transfusion, Warding, Living Earth, Regrowth, Blast Wave knockback | DONE | AI:361-366, AI:400-409, AI:323 | |
| 6.4 | Prismatic Light on demons/undead in the Halls | PARTLY | A:1852 | Zapped as plain damage. |
| 6.5 | Ring values | DONE | AR:241-301 | outdated? (Wealth "scroll dumping") |
| 6.6 | Chalice: prick at full HP, safe | DONE | AR:393-404 | |
| 6.6 | Dried Rose, Chains, Horn, Armband, Sandals, Skeleton Key, Talisman, Spellbook, Toolkit | DONE | AR:368-612 | |
| 6.6 | Hourglass: escape; buy sand bags | PARTLY | AR:151-170 | No sand bags bought (Tr). |
| 6.7 | Sungrass: plant, step on, wait (before bosses) | PARTLY | A:1805, AI:442-455 | Only when hurt; not before boss stairs. |
| 6.7 | Earthroot, Fadeleaf, Firebloom, Icecap, Blindweed, Mageroyal, Starflower | DONE | AI:433-463, AI:580-591 | |
| 6.7 | Throw an item onto a plant to set it off from range | MISSING | | low |
| 6.8 | Bombs on groups/immobile; Shrapnel only from cover | DONE | AI:526-544 | |
| 6.8 | Thrown weapons at range, not adjacent | DONE | AI:594-606 | |
| 6.8 | Pick thrown weapons back up | DONE | A:1269-1294 | |
| 6.8 | Tipped darts for their jobs | DONE | AI:564-577 | |
| 6.9 | Phase Shift, Beacon, Telekinetic Grab, Summon Elemental, Reclaim Trap, Wild Energy, Infusion | DONE | AI:315, AI:331, AI:369, AI:398, AI:820, AI:959-964 | |
| 6.9 | Alchemize junk into gold/energy | MISSING | | |
| 6.9 | Recycle, Curse Infusion, Unstable Spell | MISSING | | low |
| 6.10 | Pick a bot-safe trinket (Clover, Salt Cube, Mossy Clump); avoid Mimic Tooth, Cracked Spyglass | MISSING | | WndTrinket gets BW's default (first button). |
| 6.10 | Upgrade the trinket to +3 with energy | MISSING | | |

## 7. Monsters

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 7.1 | Swarm (splits): fight in a corridor | PARTLY | A:1647 | Only after 2+ are adjacent. |
| 7.2 | Skeleton explodes on death: not beside it at low HP | PARTLY | bot/Look.java:18 (priced) | No positioning or finish-from-range rule. |
| 7.2 | Thief: kill it at range / in a corridor before it flees | MISSING | | It steals an unequipped item (often a potion). |
| 7.2 | DM-100: close in or break LoS | DONE | AB:359-417 | |
| 7.2 | Necromancer first | DONE | A:1212 | |
| 7.3 | Vampire bat: corridor | PARTLY | A:1647 | |
| 7.3 | Brute enrages once | DONE | bot/FoeView (rageHp), Look | |
| 7.3 | Shaman: break LoS, ambush | DONE | AB:359-417 | |
| 7.3 | DM-200: fight outside its gas | DONE | A:637 (hazards) | |
| 7.4 | Ghouls: kill pairs together / finish the partner | MISSING | | |
| 7.4 | Elementals: water or frost against fire ones | MISSING | | low |
| 7.4 | Warlock: break LoS | DONE | AB:359-417 | |
| 7.4 | Monk: surprise attacks | PARTLY | ambush | |
| 7.5 | Succubus: hallway with a corner; charm | PARTLY | A:1201 | |
| 7.5 | Eye: break LoS while it charges | DONE | ABl:93-121 | |
| 7.5 | Scorpio: fight it at a door | DONE | ABl:180-186, A:1673 | |
| 7.5 | Ripper leap: off the marked cell | DONE | ABo:80-85 | |
| 7.6 | Piranhas: never fight or swim in their water | DONE | A:1009, AB:171-202 | |

## 8. Traps, hazards, rooms

| § | Advice | Status | Ref | Missing / note |
|---|---|---|---|---|
| 8.1 | Never step on a known trap | DONE | A:640 (L.avoid) | |
| 8.1 | Set a trap off from range with a thrown item; spring it on foes | DONE | AI:648-683 | |
| 8.1 | The mildest crossing | DONE | AI:612-626 | |
| 8.1 | Trap-feeling floors: walk known cells | MISSING | | (feelings, 2.10) |
| 8.1 | Levitation over traps | DONE | AI:665 | |
| 8.2 | Fire: water stops it; out of its reach | DONE | A:1595-1606 | |
| 8.2 | Leave gas the way it isn't spreading | PARTLY | A:1607-1611 | Nearest clean cell. |
| 8.2 | Paralytic gas near enemies is deadly | DONE | A:1017 | |
| 8.2 | Confusion: don't fight near chasms | MISSING | | low |
| 8.2 | A chasm is a legal shortcut when the floor is done and HP high | PARTLY | AB:241-252, AB:531-535 | Only to escape or when stuck. |
| 8.3 | Laboratory; locked rooms with their key | DONE | AAl, A:1274-1276, A:1310 | |
| 8.3 | Traps / sentry / pool / magical fire / toxic gas rooms | DONE | AI:754-793, A:631-634 | |
| 8.3 | Storage barricade: burn it | DONE | AI:797-817 | |
| 8.3 | Pit room: Feather Fall, or jump at high HP | MISSING | | |
| 8.3 | Weak floor room: Levitation | MISSING | | |
| 8.3 | Tomb only with level to spare | DONE | A:1106 | |
| 8.3 | Sacrifice room | DONE | AB:261-278 | |
| 8.3 | Well of Health for an emergency (it also uncurses worn items); Awareness | PARTLY | A:1410-1415 | The uncursing isn't counted. |
| 8.3 | Secret rooms only when cheap | DONE | A:1320, A:1350 | |
| §9.7 | Rest only as much as the next fight needs | DONE | A:1834 (FightModel.restNeed) | |
| §9.7 | Prefer sustain sources (Warlock, Chalice, Horn, Sungrass) | DONE | Bu:63, AR:393, AR:368, A:1805 | |

## ID covers (not checked: agent ID)

§2.2 deck base rates; class starting knowledge; drop-source clues (flies, bats, necromancers, spawners always Healing; scorpio never Healing/Strength; succubus never ID/SoU; a burnt scroll isn't SoU); brew 3 same seeds to identify; energizing identifies; Stone of Intuition use; harmful-potion throw test; drink-ID protocol (full HP, nothing in view, on water, off grass, bandolier); read-ID protocol (targets ready: upgrade, identify, remove curse, transmute; Rage/Teleport in a cleared area); 30% curse odds; cursed gear stuck; prize rooms, statues and quest rewards never cursed; crypt/sacrifice armour cursed unless glyphed; §6.3 Detect Magic before equipping; §6.2 Identify on worn gear first; §9.2 last resort: drink the likeliest-Healing unknown; §6.4 wands identify after 10 uses.

## Counts

Rows: 225 checked (plus 2 "ID covers" rows). **DONE 138, PARTLY 43, MISSING 38, N/A 6.** By weight the MISSING rows are mostly mid/late-game (bosses 15+, alchemy, rooms) and per-monster rules.

## Ranked gaps (MISSING and PARTLY), by likely impact

Baseline for the ranking (BF-smoke4, MILD): mean max depth 7.87, deepest 14; 10 of 11 floor timeouts at Tengu; deaths 13 melee, 10 bolts, 9 poison/bleed/fire, 7 starving.

| # | Gap | Status | Why it ranks here |
|---|---|---|---|
| 1 | Tengu: fight from the adjacent cell (his accuracy halves), close in (Blink, walk through), Invisibility saved for him | MISSING | The depth-10 wall: 10 of 11 timeouts. |
| 2 | Bless the Ankh with a full Waterskin | MISSING | The bot already buys Ankhs; unblessed it costs the whole pack. A second life at 25% in place. |
| 3 | Boss kit before the boss stairs: Waterskin full, Healing counted, Sungrass planted, Ankh blessed | PARTLY | Every boss; rest and upgrades already done (Ru:107-109). |
| 4 | Read an SoU now when HP loss outruns food; never hold more than 1-2 | PARTLY/MISSING | Mid-game melee deaths; scrolls held by a 1% test. |
| 5 | Trinket choice: Clover / Salt Cube / Mossy Clump, never Mimic Tooth / Spyglass; upgrade it to +3 | MISSING | First button today; Salt Cube targets the starving deaths; a bad pick hurts the whole run. |
| 6 | Shopkeeper guard on every area effect (bombs, gas, fire, sprung traps, Assist.ranged's potions) | MISSING | One throw loses the 11/16 shop. |
| 7 | Thief: kill it from range or in a corridor before it flees | MISSING | Prison; it steals unequipped items (potions, Healing). |
| 8 | Alchemy: energize junk, scrolls into stones, potions into exotics, Magical Infusion, elixirs for bosses, tip darts | MISSING | Energy and spare consumables wasted all run. |
| 9 | Eat before a long rest / at Hungry, not only at Starving | PARTLY | 7 of 60 starving deaths (K8's area). |
| 10 | Fight a statue once gear outclasses it (guaranteed enchanted weapon) | PARTLY | Free strong weapon skipped every time. |
| 11 | Goo: keep it off water, fight on dry floor, circle grass | MISSING | Goo heals in water; 1 death in 60 now, but it's the first killer in the guide. |
| 12 | Lightning: no zap in water next to the target | MISSING | Mage self-damage. |
| 13 | DM-300 off the INACTIVE_TRAP tiles | MISSING | Barrier 30+ per step; matters once runs reach 15. |
| 14 | Dwarf King phase 1: minions first, minions between you and warlocks | PARTLY/MISSING | Matters once runs reach 20. |
| 15 | Champion's second weapon (Twin Upgrades wasted) | MISSING | Every Champion run. |
| 16 | Skeleton explosion: step off / finish from range at low HP | PARTLY | Prison melee deaths. |
| 17 | Swarm and bats: pull back into a corridor before they surround | PARTLY | Sewers/caves chip damage. |
| 18 | Ghouls: finish the partner, kill pairs together | MISSING | City. |
| 19 | Rot Heart: veto fire (Assist.ranged's Liquid Flame, firebombs) | PARTLY | Quest failure. |
| 20 | Level feelings (traps: known cells; secrets: search more; dark) and burnt floor | MISSING | Trap damage, missed rooms. |
| 21 | Rush the Halls, save Magic Mapping for them | MISSING | Late game. |
| 22 | Blacksmith: reforge/harden, clear pack space for the one trip; Mage's Wandmaker wand by staffRank | PARTLY | One upgrade's worth. |
| 23 | Mage and Cleric talent orders (Shield Battery 2; Shield of Light, Bless, Sunray 2) | PARTLY | Honest default only (training uses TALENTS=human). |
| 24 | Rogue's cloak and Invisibility as escapes/openers; Blink to close gaps | PARTLY | Escape breadth. |
| 25 | Pit room, weak floor room, newborn elemental's cell, snake rule, Flock, Alchemize, plants set off from range, confusion by chasms | MISSING | Low each. |

## Work packages (file ownership avoids ID, K8 and M2)

ID: `bot/Identify.java` and the ID parts of the hands. K8: `Assist.java`, `AsBasics.java` (core loops, food). M2: `common/src/pebxel/bot/*`, `Brain.java`, `Rules.java`.

- **WP-A Bosses (owns `AsBosses.java`, `AsQuests.java`):** gaps 1, 3, 11, 13, 14, 19, newborn elemental. Tengu adjacency and the Invisibility/Blink holds in `AsBosses.tactics`; the boss kit through `AsBosses.suggest` on a depth-4/9/14/19/24 floor that's done (fill the skin, plant Sungrass, keep Invisibility); Rot Heart fire veto in `AsQuests.veto` (the hands' hook already exists). Also the Blacksmith pack-space step (gap 22, part).
- **WP-B Items and economy (owns `AsItems.java`, `AsAlchemy.java`, `AsChoices.java`, `server/.../BotAlchemy.java`, `BotWindows.java`):** gaps 2, 5, 6, 8, 22 (reforge, harden, Mage's wand), 12 as a veto. Ankh bless in `AsItems.suggest`; shopkeeper guard inside AsItems' throws plus `AsItems.veto(hk)` called from one line in `AsQuests.veto` (coordinate with WP-A); trinket choice in `AsChoices.choose` (WndTrinket) and its energy upgrade; alchemy recipes in `AsAlchemy.worth/plan`.
- **WP-C Foes and rooms (new `AsFoes.java`, hooked by one line in `AsQuests.suggest0`'s chain):** gaps 7, 10, 16, 17, 18, 20, and the low ones in 25 (pit, weak floor, snake, elemental fire to water). Statue attack proposed as a pinned ENEMY entry (no edit to Assist.first's skip).
- **WP-D Class play (owns `AsAbilities.java`, `Build.java`):** gaps 15, 23, 24 (cloak as escape, Invisibility opener).
- **For the owners (not packaged):** gap 4 to M2 (`UpgradePlan` hold cap, food-linked reading); gap 9 and 21 to K8 (`Assist.java:1729` eating, `Assist.java:1799` Magic Mapping).

## Guide errors and possibly outdated advice (checked against v4.0.2 source)

1. **Dried Rose weight** is 1, not 0 (`S/items/Generator.java:575`, index 3). Known from the earlier audit. (§6.6)
2. **The Spirit Bow takes no Scroll of Upgrade.** `SpiritBow.isUpgradable()` returns false (`S/items/weapon/SpiritBow.java:285-287`); its level is hero level / 5 (`:272-276`). §2.1 ("the spirit bow" as the Huntress's upgrade target), §3.4 ("plus SoU levels", "The bow itself is an SoU target") are wrong. UpgradePlan already says so (`bot/UpgradePlan.java:30`).
3. **Teleportation works on locked and boss floors.** On a locked or non-regular floor it calls `teleportInNonRegularLevel` (`S/items/scrolls/ScrollOfTeleportation.java:100-102`, `:221-281`), which moves the hero to a random reachable cell. §2.3, §4.0 ("you can't teleport on locked boss floors") and §6.2 are wrong; only between-floor moves are blocked. The code's escape list (A:1751) is right to read it there.
4. **Mimic odds wording.** §2.3 says "4 in 20 chests are real"; the source rolls a chest 4/20 and a mimic 1/20 of heaps on depth > 1 (`S/levels/RegularLevel.java:405-421`), so 1 chest-looking heap in 5 is a mimic, as §7.6 says.
5. **Torches** (§2.7 "Torches before the Halls"): only the depth-20 shop stocks them (`S/levels/rooms/special/ShopRoom.java:247-253`), which needs the Imp's vault (skipped). Not in §2.7's stock list.
6. **The win** (§1.2 "choose Exit, don't ascend"): outdated for us. Thomas counts a win as the full climb out; the bot already answers Stay (`server/src/pebxel/BotWindows.java:149`).
7. Possibly outdated (for PN): the subclass picks and "Berserker: armour to +10" (pre-v3.2 rage nerf), Ring of Wealth "scroll dumping", the Reddit wand tier list (Jan 2025, fine for v4 but community). Checked and correct: Tengu accuracy 10/20 (`Tengu.java:106-112`), DM-300 trap-tile barrier (`DM300.java:327-343`), Goo water heal (`Goo.java:109-110`), enchant loss +4..+8 and harden from +6 (`Weapon.java:384-396`), Healing 0.8·HT+14 (`PotionOfHealing.java:64`), search 6 hunger and Rogue/Wide Search radius (`Hero.java:2496-2498,2615-2617`), Blacksmith costs (`WndBlacksmith.java:113-165`), Ghost odds (`Ghost.java:304,322`), Imp shop condition (`Imp.java:397-399`).
