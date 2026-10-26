# Shattered PD v4.0.2: strategy deduced from the patch notes and the source

Agent PN, 2026-10-25. A companion to `strategy-reference.md`: that file holds the guide lore; this one says what is true in **v4.0.2** and what a bot should do about it. Every rule is "do X when Y, because Z (source)".

**Win condition (Thomas):** climb back out to the surface with the Amulet. The "Let's call it a day" exit on depth 26 is **not** a win for us.

## Conventions

* `S/` = `spd/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` (the same tree the reference calls `shattered/spd/...`).
* `M/` = `spd/core/src/main/assets/messages/`.
* `CL1`..`CL4` = `S/ui/changelist/v1_X_Changes.java` .. `v4_X_Changes.java` (the in-game patch notes). A `CL` cite is the reason; the `S/` cite is what the code does now.
* Numbers are from the code unless marked "CL".

---

## 1. The 15 rules that matter most, ranked

1. **Plan the whole run for the climb, not for Yog.** The ascent multiplies every enemy's HP, damage, accuracy, evasion **and armour** (rats ×10 down to scorpios ×1.1). A sewer crab on the way up has 120 HP, hits for 8-56, has 96 accuracy and blocks 0-32. You need late-game armour to survive floors 4 to 1. (§3; `S/actors/buffs/AscensionChallenge.java:72-103`, `S/actors/mobs/Mob.java:148`, `S/actors/Char.java:390,453,653,669`)
2. **On the climb, leave every floor with the curse at 1 or less.** Each new floor adds 2 stacks, each boosted kill removes 1, and stacks never go below 0. Two kills a floor holds it flat. At 4 stacks wandering enemies move at 2× speed, at 6 you're slowed to half speed, and at 8 you take damage every turn. (§3.2; `AscensionChallenge.java:128-159,191-197,259,329-340`)
3. **Put Scrolls of Upgrade into armour first.** Each SoU adds `tier` to armour's max block and 1 to its min, about +3 block per hit on plate. That block applies to every multiplied hit on the climb. Aim for plate at +8 or better by depth 25. (§5; `S/items/armor/Armor.java:384-405`)
4. **Explore the Demon Halls fully.** Floors 21-24 hold that region's 3 SoU, 2 Potions of Strength, an Arcane Stylus and 4+ food. **Kill every demon spawner:** alive, it adds Ripper Demons to Yog's fight, and on the climb it spawns a ripper every 20 turns or less. (`S/Dungeon.java:529-563`, `S/actors/mobs/DemonSpawner.java:90-92`, `S/actors/mobs/YogDzewa.java:129-149`)
5. **Choose "I'm not done yet" on the Amulet screen, then "Continue!" at Yog's stairs, and only when ready.** Once you're ascending, END THE GAME is gone and you can't teleport between floors. The surface is the only way out. Before you start, have full HP, a full Waterskin, 2+ Healing, a blessed Ankh and 2 rations. (`S/items/Amulet.java:51-58`, `S/Dungeon.java:455-460`, `S/levels/HallsBossLevel.java:336-352`)
6. **Use the boss floors (20, 15, 10, 5) as the climb's rest stops.** On first arrival you get a full heal (20 HP a turn) and your hunger is reset to 0. Nothing spawns there and no stacks are added. (`AscensionChallenge.java:255-257`)
7. **Kill on the climb with bombs, wands and surprise hits.** These are the tools that ignore the multiplied enemy armour. On the climb, bombs scale with depth 26: 30-90 damage, minus only the unmultiplied armour. Wand damage isn't reduced by armour. A surprise hit always lands, even on a snake whose ×9 evasion is 225. Save bombs for the climb. (`S/items/bombs/Bomb.java:194`, `S/Dungeon.java:448-453`, `Char.java:390`)
8. **Pick the trinket by this order:** Parchment Scrap (if the energy for +2/+3 is in reach), then Salt Cube, Cracked Spyglass, Thirteen-Leaf Clover, Mossy Clump. Avoid Shard of Oblivion, Rat Skull, Mimic Tooth, Chaotic Censer, Sundial, Eye of Newt and Exotic Crystals. (§6)
9. **Pace each floor at about 350 turns on the way down.** A regular floor gives about 330 food on average, and natural regen is 1 HP every 10 turns, so every HP you rest back costs 10 food. (§4; `S/levels/Level.java:226`, `S/items/Generator.java:541`, `S/actors/buffs/Regeneration.java:44`)
10. **Finish every quest on the way down.** The Ghost, Wandmaker, Blacksmith and Imp all vanish once you're ascending, and the vault can't be entered. (`S/actors/mobs/npcs/Ghost.java:91-95`, `Imp.java:72-75`, `Wandmaker.java:76`, `Blacksmith.java:68`, `S/levels/CityLevel.java:145`)
11. **Keep gold for the Imp's shop on depth 20.** It's the only shop open on the climb; every other shopkeeper leaves once you're above floor 20. Buy an Ankh and Healing there. (`AscensionChallenge.java:279-284`, `S/actors/mobs/npcs/ImpShopkeeper.java:41-45`)
12. **When a climb fight goes wrong, go back down the stairs.** A floor you've already climbed adds no stacks, and only allies follow you. (`AscensionChallenge.java:252`)
13. **Never wear a Crystal-enchanted (v4) main weapon down to "cracking".** At 0 durability it **shatters and is deleted**. Its durability runs out after about 50 attacks at +0 and about 30 at +6. On the "cracking" message, swap weapons until it reports repaired. (`S/items/weapon/enchantments/Crystal.java`; CL4:105-110)
14. **Don't throw Stones of Aggression at bosses (v4).** They no longer affect a boss directly; use them on minions. Ring of Haste is now ×1.15 a level. Barkskin no longer comes from furrowed grass. Hold Fast needs the seal. Feint costs 35 and Challenge 50. (CL4:338-373)
15. **Treat a hazard room as a clue to its floor's unknown potions.** Each one spawns its own solution potion somewhere on the same floor: pool room → Invisibility, trap corridor → Levitation, magical fire → Frost, toxic gas → Purity, sentry room → Haste. The room's type is visible, so this is legal inference. (`S/levels/rooms/special/PoolRoom.java:91`, `TrapsRoom.java:122`, `MagicalFireRoom.java:112`, `ToxicGasRoom.java:107`, `SentryRoom.java:161`)

---

## 2. Guide claims that are wrong or outdated for v4.0.2

All of these are fixed in place in `strategy-reference.md`, each marked "(v4 correction, see strategy-v4.md)".

| # | Reference § | Claim | v4.0.2 truth | Source |
|---|---|---|---|---|
| 1 | 1.2 | "Bot rule: pick up the Amulet, choose Exit. Do not ascend" | Our win is the ascent. Choose Stay ("I'm not done yet"), then "Continue!" at the depth-25 stairs. | Thomas; `S/levels/SewerLevel.java:146-170` |
| 2 | 1.2 | "At 4+ stacks enemies move fast" | Only enemies that are **not hunting or fleeing** move at 2× speed. Also missing from the reference: +2 stacks a floor, −1 a kill, floor of 0, beckoning at 2+. | `AscensionChallenge.java:128-159` |
| 3 | 1.4 | "Monsters give 0 XP above their max level; grinding is pointless" | True on the way down. On the climb, every boosted kill gives 10 × spawning weight XP until level 30. | `S/actors/mobs/Mob.java:993-997` |
| 4 | 6.6 | "The Cloak, Rose and Tome have 0 weight" | Only the Cloak and Tome have weight 0. The **Dried Rose has weight 1**, so 11 artifacts can be generated. Once they're all used up, an artifact drop becomes a ring. | `S/items/Generator.java:560-576,706-710` |
| 5 | 2.10, 9.19 | "Demon Halls are usually best rushed" | Floors 21-24 carry the 5th region's 3 SoU, 2 PoS and stylus, plus food. Explore them with Magic Mapping to save turns. Spawners also matter for the climb. | `S/Dungeon.java:529-563`, `DemonSpawner.java:90-92` |
| 6 | 6.3 | Stone of Intuition: "correct = identified" | Since v2.5 every stone gets 2 uses, even if the first guess is wrong. | CL2:120 |
| 7 | 8.3 | Garden and magic well are free loot | Since v3.3 both are **locked** and need the floor's iron key. | `S/levels/rooms/special/GardenRoom.java:42-43`, `MagicWellRoom.java:59-60`; CL3:112 |
| 8 | 6.8 | Bombs "destroy some terrain and items" | Since v2.5 explosions never destroy unique, upgradable or equipment items. Damage is 4+d to 12+3d with no falloff, alchemy bombs cover 5×5, and on the climb d is 26. | `S/items/Heap.java:287`, `Bomb.java:194`; CL2:124-137 |
| 9 | 6.10 | "Bot-safe: Clover, Salt Cube, Mossy Clump; avoid Spyglass" | Ranking redone with numbers in §6. Parchment Scrap at +3 removes curses from random weapons and armour. The Spyglass's hidden items are in the heap list (drawn at 10% alpha). Mossy Clump's dew was halved in v2.5, and its floors are 1/3 grass, 2/3 water since v3.0. | `S/items/trinkets/*`; CL2:306-310; CL3:889-892 |
| 10 | 3.1 | Hold Fast (Warrior T3) | v4: the Warrior gets it only while the seal's shield buff is on (the seal attached to his armour), and its armour bonus is capped at 6. Other heroes who get it through metamorphosis are capped at armour tier + level. | `S/actors/hero/Hero.java:1507-1511`, `S/actors/buffs/HoldFast.java:54-66`; CL4:372 |
| 11 | 6.6 | Chalice: "prick at full HP" | Still right. Since v3.3 the chalice shows its damage (83-117% of a fixed value) and the exact chance of death, and all damage reduction applies. | CL3:96-101 |

Claims checked and **still right** in v4.0.2:
* SoU and PoS counts and placement; Hold Fast 50/75/100% in code; Ring of Haste 1.15.
* The Imp's shop on depth 20 needs a vault score over 2000.
* Seal shield 3+2·tier.
* Natural regen 1 HP / 10 turns.
* The potion and scroll deck counts (6/4/3/3/3/2/2/2/2/2/1 per 30).
* The Trinket Catalyst comes on floors 1-3 in a locked chest.

---

## 3. The climb (ascension challenge), from the code

### 3.1 The rules

| Rule | Value | Source |
|---|---|---|
| Start | The depth-25 up-stairs with the Amulet: "Continue!" adds the buff, with highestAscent = 25 | `HallsBossLevel.java:336-352` |
| Stacks | **+2** on first arrival at each non-boss floor. **−1** per boosted enemy killed (−0.5 for a ghoul or ripper), by anyone: allies, traps, conversion. Never below 0. | `AscensionChallenge.java:191-197,251-259`; `S/actors/buffs/AllyBuff.java:71` |
| 2+ stacks | Every enemy more than 8 tiles away is beckoned to you each turn | `:128-136` |
| 4+ stacks | Enemies not hunting or fleeing move at 2× speed | `:140-149` |
| 6+ stacks | Hero speed halved and capped at 1× (haste no longer helps) | `:152-159` |
| 8+ stacks | (stacks−4)/4 damage a turn off boss floors: 1/turn at 8, 2/turn at 12 | `:329-340` |
| Boss floors | First arrival: hunger to 0 and a heal of max HP at 20 HP a turn; no stacks, no spawns | `:255-257` |
| Arriving on a floor | Every mob removed except mimics, statues, spawners and piranhas; **one** new mob placed 12+ tiles away | `:268-275`; `reset()` overrides |
| Respawn | 5 turns while ≤1 mob, then 10/15/20/25 by count. Depth 1: 2 turns per living mob, limit 10. | `S/levels/Level.java:752-767`; `S/levels/RegularLevel.java:206-217` |
| Mob limit | 3 + depth%5 + 0-2 (floors 4, 9, 14, 19, 24 are the most crowded) | `RegularLevel.java:212` |
| Multipliers | HP, damage, accuracy, evasion and armour all multiplied | `Mob.java:148`; `Char.java:390,453,653,669` |
| Hazard scaling | `scalingDepth()` = 26: traps, bombs, burning, ooze, toxic gas everywhere | `Dungeon.java:448-453` (71 call sites) |
| Teleport | No interfloor teleport with the Amulet (no Passage, Beacon or Warp Beacon); teleport within a floor is fine | `Dungeon.java:455-460` |
| Shops | All shopkeepers flee once highestAscent < 20. The Imp's shop on 20 stays. | `:279-284`; `ImpShopkeeper.java:41-45` |
| NPCs | Ghost, Wandmaker, Blacksmith and Imp die at once | NPC `act()`s |
| Spawners | Spawn cooldown capped at 20 turns | `DemonSpawner.java:90-92` |
| Win | Taking the depth-1 surface exit with the Amulet calls `Dungeon.win`: the "happy end" | `SewerLevel.java:146-170` |

### 3.2 What the multipliers make of each region's foes

Base stats are from the reference §7.

| Region | Multiplier | Worst foes on the way up (HP / damage / accuracy / evasion / armour) |
|---|---|---|
| Halls 24-21 | ×1.1-1.2 | About as on the way down. Rippers come from live spawners. |
| City 19-16 | ×1.33-1.67 | Elemental 100/33-42; monk 105/18-38 with evasion 45; golem 160/33-40. Ghouls count only ½. |
| Caves 14-11 | ×2-2.5 | **Brute 90 HP, 34-90**; bat 75/12-45; spinner 100/20-40; DM-200 160/20-50; shaman bolts ×2.25 |
| Prison 9-6 | ×4-5 | Guard 160/16-48; skeleton 125/10-50 with a **death blast of 30-60** (`Skeleton.java:82`); DM-100 zap 9-36; thief 100 HP |
| Sewers 4-1 | ×8-10 | Rat 80/10-40/acc 80/DR 0-10; gnoll 108/9-54/acc 90; **crab 120/8-56/acc 96/DR 0-32, fast**; snake evasion 225; swarm 425 HP (it splits) |

**Deductions:**
* **Only armour block saves you in the sewers and prison.** Enemy accuracy of 80-96 beats any hero evasion.
* **Physical damage struggles against ×8 armour.** Use high-damage-per-hit weapons, wands, bombs and surprise attacks there. Multi-hit weapons such as gloves and sai suffer most.
* **Snakes:** never melee one out in the open; walk past, or use a wand or a surprise hit.
* **Skeletons:** never be next to one when it dies at low HP.
* **DM-100s:** break line of sight.

### 3.3 Climb protocol (rules)

1. **Prepare on 26 → 25.** Eat to full, fill the Waterskin, bless the Ankh, and carry 2+ Healing, all bombs, 1+ Teleportation and 1+ Invisibility. Then go up. (Rules 5, 7.)
2. **On arrival at a floor:** you're at its down-stairs and the map is known. Plan the known shortest path to the up-stairs, and pick a choke point (a door or corridor) near the start of it.
3. **Beckoned foes come to you.** At 2+ stacks you never need to hunt. Wait at the choke, and kill until stacks are **≤1** (two kills a floor), then walk. Beckoned foes arrive "wandering", so the first hit at a corner is often a surprise.
4. **Leave rule:** take the up-stairs when stacks ≤ 1 and HP ≥ 60%, or stacks ≤ 2 when the next floor is a boss floor.
   * Leaving floor 2 at s means arriving on floor 1 at s+2. Keep that **under 6** so you aren't slowed on the floor with the fastest respawns.
   * On floor 1, don't fight: walk out.
5. **Don't dawdle.** The respawner adds a mob every 5 turns while ≤1 is alive. Resting on a regular floor brings fights, so rest on the stairs (escape down) or on a boss floor.
6. **Escape = down.** If a fight's lookahead is lost, step down the stairs. Your stacks don't change, and enemies stay behind.
7. **Pick kills by value.** Boosted kinds only: statues, mimics and NPCs don't count. Ghouls and rippers count ½. In the City, kill elementals, monks, warlocks and golems for full credit.
8. **Traps are depth-26 traps now.** Walk only on cells you know are safe, and avoid any known trap even if it was harmless in the sewers. Lure foes over traps on purpose.
9. **Food:** after each boss floor, hunger is 0 and you have 450 turns before starving. Four floors at ≤110 turns each need no food at all. Carry 2 rations for slowdowns. (The first segment, 24-21, starts at whatever hunger you had after Yog: eat before.)
10. **Allies make it safer.** Their kills count, and they absorb hits: the Dried Rose ghost (v4: 40+10·level HP, damage scales with STR), Wand of Warding wards, Spirit Hawk, Shadow Clone, Power of Many. Corrupting a foe also counts as a kill, but on the climb corruption resistance is max(13, EXP). (`AscensionChallenge.java:214-240`)
11. **XP:** each boosted kill gives about 10 XP until level 30, so the climb reaches level 30 (T4 talent points 21-30). Spend them on survival.

### 3.4 Why "race to the stairs" alone fails

AsAscent's current doctrine is "no holding a doorway for foes that never stop coming". The numbers say holding briefly is right:
* Without kills, the climb adds 40 stacks over 20 floors. You'd be slowed by floor 22 and burning by floor 21.
* With 2 kills a floor the curse stays at 0-2 all the way.

The foes do keep coming, but each one that dies is −1. The right stop rule is **"stacks ≤ 1, then go"**, not "go now".

---

## 4. Food, regen and pace

| Fact | Value | Source |
|---|---|---|
| Hunger | 1 a turn. Hungry at 300 (a warning only), starving at 450 (HT/1000 a turn, no regen). Frozen on locked floors, while Well Fed, and in a Challenge arena. | `S/actors/buffs/Hunger.java:40-104` |
| Regen | 1 HP / 10 turns at every level. Chalice: 8.67 at +0 down to 2 turns at +10. Cursed chalice ×1.5. | `Regeneration.java:44-83` |
| Food per regular floor | 1 random food: ration 300 (4/5) or pasty 450 (1/5) = **330 average**. Large floors get a second one. None on boss or branch floors. | `Level.java:224-226,276-278`; `Generator.java:537-541` |
| Other food | 2 small rations (150 each) per shop; mystery meat (150, cook it); Cached Rations, Nature's Bounty, Horn of Plenty | `ShopRoom.java:291-292` |

**Deductions:**
* **The descent budget is about 7,300 turns before starving:** 20 regular floors × 330, plus the starting ration, plus the 450 grace. Boss fights are free (locked floors). That is about **350-370 turns per regular floor, resting included.**
* **Rule:** past ~350 turns on a floor with fewer than 2 rations in the pack, stop optional searching and secret-hunting, and go down.
* **Rule:** rest only as far as the next fight needs. Every HP rested costs 10 food. Dew, Healing and the Chalice are cheaper in food.
* **Salt Cube +3** halves hunger and multiplies regen by 0.6. Food per HP regenerated drops from 10 to 8.3, and turns per floor double (~700). The regen penalty switches off on locked boss floors.
  * It is a net gain in food, and a loss only in time-to-full-HP (more respawn encounters while resting).
  * The baseline lost 7 of 60 games to starvation; Salt Cube removes that cause. (`SaltCube.java:58-82`; CL3:790)
* **Mines (Blacksmith):** digging a wall costs about 4 hunger. Budget about 200 extra turns for the gold.
* **The climb needs almost no food** (§3.3 rule 9). Carry 2 rations; don't hoard 10.

---

## 5. Upgrade targets in v4.0.2

| Target | Per SoU | Source | Verdict |
|---|---|---|---|
| Armour | DR max +tier, min +1. Plate: +3 average block per hit taken. STR requirement −1 at +1/+3/+6/+10. | `Armor.java:384-405` | **First**: to about +4 by the caves, ≥+8 by Yog. Every multiplied climb hit meets it. |
| Melee weapon | Min +1, max +(tier+1). T5: +3.5 average per hit. | `S/items/weapon/melee/MeleeWeapon.java:250-258` | Second. Prefer high damage per hit (the climb's ×armour). |
| Thrown set | Max +tier on **all 3**, full repair, durability ×1.5 (v3.2) | `MissileWeapon.java:125-127`; CL3:268-281 | Good for Huntress/Sniper; otherwise one SoU at most |
| Wand / staff | +1 charge, more damage, ignores armour | `Wand.java` | Mage's staff first (levels persist through re-imbues). A climb tool for everyone. |
| Enchanted item past +4 | 10-100% chance to lose the enchantment | `Weapon.java:394-396` | Use Magical Infusion (12 energy) instead |
| Crystal-enchanted weapon | Durability drops faster at higher levels (30 attacks at +6) and the weapon **breaks** at 0 | `Crystal.java` | Don't make it the SoU sink unless the bot rotates weapons |
| Blacksmith "upgrade" | Only items at +0 or +1 | `WndBlacksmith.java:422-427` | Spend favour on a second item (spare armour or the thrown set) before it gets SoU |

**Parchment Scrap synergy:** at +3 every random weapon and armour is uncursed and enchanted. Picking the best-tier armour found and pouring SoU into it is then safe without ID.

---

## 6. Trinkets (v2.4+) for a bot

How they work:
* One Trinket Catalyst comes on floors 1-3 in a locked chest; its golden key is elsewhere on the floor. (`RegularLevel.java:452-458`; `Dungeon.java:584-587`)
* Brew it at an alchemy pot for a choice of 3 trinkets plus a "random" 4th. (`TrinketCatalyst.java:161-178`)
* Upgrading costs energy. Most trinkets cost **6+2·level** (6/8/10, **24 to +3**). Mossy Clump, Parchment Scrap and Wondrous Resin cost **10+5·level** (10/15/20, **45 to +3**). (`S/items/trinkets/*.java upgradeEnergyCost`)
* A trinket energizes for 5. It works only while carried. (`Trinket.java:47-58,71`)
* Energy comes from 5 crystals per lab (floors 3-4), 6 per energized potion or scroll, more for high-value ones. Energizing identifies the item (v2.5).

| Trinket | +0 → +3 effect | Bot verdict |
|---|---|---|
| **Parchment Scrap** | Random weapons, armour and thrown sets: curse chance ×1.5 / ×2 / ×1 / **×0**, enchant chance ×2 / ×4 / ×7 / **×10**. So at +2: 30% cursed, else always enchanted; at +3: never cursed, always enchanted. (`ParchmentScrap.java:52-84`, `Weapon.java:442-446`, `Armor.java:674-678`) | **1st, if 25-45 energy is reachable by about floor 8.** At +0/+1 it raises curses: don't wear unknown gear until +2. |
| **Salt Cube** | Hunger ×0.8 → ×0.5; regen ×0.84 → ×0.6 (regen penalty off on locked floors) | **1st if energy is short.** Kills starvation (§4). |
| **Cracked Spyglass** | +0.375 → +1.5 extra random items a floor, `hidden` (drawn at 10% alpha) | **Strong**, but needs a ruling (§10): is a faint heap in view legal? |
| Thirteen-Leaf Clover | 25-100% of hero damage rolls become max (60%) or min (40%): about +10% average damage at +3 | Good, simple |
| Mossy Clump | 25-100% of feature-less floors become grass (⅓) or water (⅔). The dev calls it the strongest (CL2:306, CL3:889). | Good: grass is line-of-sight cover and seeds. Costs 45. |
| Petrified Seed | More grass loot, 25-80% stones instead of seeds | Mild |
| Vial of Blood | Big heals ×1.125 → ×1.5 but capped at 4+15%HT → 1+5%HT a turn | **Avoid:** emergency heals become slow |
| Ferret Tuft | Everyone's evasion ×1.125 → ×1.5 (foes too) | Avoid: the bot fights many at once |
| Exotic Crystals | 20-80% of potions/scrolls are exotic (v4: 20%/level) | Avoid until ID handles exotics |
| Eye of Newt | Vision ×0.875 → ×0.5, mind vision radius 2-5 | Avoid |
| Dimensional Sundial | By real clock: day spawns ×0.95 → ×0.8, night (8pm-8am) ×1.25 → ×2 | Avoid (depends on the wall clock) |
| Trap Mechanism | More trap/chasm floors; 10-40% of traps shown | Avoid |
| Rat Skull | Rare enemies ×2 → ×5 | Avoid |
| Mimic Tooth | Mimics ×1.5 → ×3, ebony mimics | Avoid |
| Wondrous Resin | Cursed-wand effects 25-100% positive | Niche |
| Chaotic Censer | Random gas when foes are near | Avoid |
| Shard of Oblivion | +20% drops per unidentified worn item; **turns off passive ID** | Avoid |

**Rule:**
* Pick by the order above.
* Take the 4th "random" option only if all three shown are "Avoid".
* Upgrade the chosen trinket before brewing anything optional.

---

## 7. New systems (v2.0-v4.0.2) and how to play them

### 7.1 v4.0: the Imp's vault, enchantments, AI

* **The vault quest** (agent K3's area; reference §5.4):
  * All gear is stored and you go in with cloth armour and an Escape Crystal. The vault is the size of three floors, uses stealth AI and has 3 static hazards.
  * You keep 1 item. The shop on 20 needs a score over 2000.
  * v4.0.1 eased it: a Healing potion near the entrance, no awake caves+ enemies near the entrance, slower scanning sentry, fewer laser zaps, and a 1-tile safe border in the fire room (CL4:120-130).
  * **The stealth AI is vault-only:**
    * You "hear" nearby foes through walls.
    * Sleepers only notice you at short range.
    * Patrollers barely see behind them.
    * An "investigating" foe follows you but doesn't attack, and loses you behind a door or corner.
    * A foe you attack skips straight to fighting. (CL4:229-238)
  * **Rule:** in the vault, never attack first unless it kills; break sight at every corner.
* **New enchantments** (`Weapon.java:518-540`):
  * common: Venomous (stacking delayed poison);
  * uncommon: Eldritch (fears nearby foes except the target; 25%), Vorpal (damage into bleed);
  * rare: Crystal (+33% magic damage, but the weapon can shatter; see rule 13).
  * New curses: Pressurized (a geyser pushes you too), Wondrous (cursed-wand effects).
  * v4.0.1 buffs: Crystal; Eldritch, Corrupting 25%; Blooming 1.5 grass.
  * Kinetic, Corrupting and Grim now combine properly with Smite and Holy Weapon.
* **Health bars darken** to show incoming damage over time. **Targeted-cell warnings** persist until the attack lands (CL4:260-261). That is visible information: the bot may read pending damage-over-time and telegraphed cells.
* v4.0 fix: you now arrive on the floor's exit when ascending (CL4:318).

### 7.2 v3.x: the Cleric, the Warrior rework, thrown sets

* **Cleric (v3.0):** the reference §3.6 is right. Since v3.1-v3.2:
  * Guiding Light 2-8; the Priest's free cast every 50 turns.
  * Holy Weapon, Holy Ward and Divine Sense are instant.
  * Lay on Hands 15/20/25; Aura of Protection 20/30/40%.
  * Every spell illuminates its direct target.
  * Nerfed: Searing Light +3/+5, Cleanse is no longer instant, Hallowed Ground barrier capped at 30. (CL3:423-429,665-705)
  * **Bot:** Paladin is still the low-micro pick; the buffed Priest is good with wands.
* **Warrior (v3.1):** the seal now gives a burst of 3+2·tier (+Iron Will) at ≤50% HP, with a 150-turn cooldown; unused shield refunds up to 50%.
  * v3.2: Hold Fast slows shield and combo decay; Berserker −25% rage and an enrage shield of 8+2·level; Gladiator combo lasts 15 turns after a kill. (CL3:432-437,476-480,500-522)
* **Thrown weapons (v3.2-v3.3):**
  * They come in sets of 3; one SoU upgrades and repairs all of them; durability ×1.5 per upgrade.
  * A throw not aimed at a target costs 1 turn.
  * Telekinetic Grab is instant for clubs and hammers.
  * Projectile Momentum 15/30/45%, Durable Projectiles +50/75%, Point Blank +25/50/75% accuracy.
  * Bolas 4-9; Boomerang returns after 5 turns. (CL3:223-248,268-281)
* **New rare sewer foes (v3.1):**
  * The Gnoll Exile (24 HP, **passive until provoked**, drops extra loot; `S/actors/mobs/GnollExile.java:44-56`). **Rule:** leave it alone unless you're strong; never wake it with area effects.
  * The Hermit Crab (25 HP, slow, drops armour 50%).
* **v3.3:**
  * The Skeleton Key artifact (opens and locks doors, makes walls); Goo's key renamed the Worn Key.
  * Gardens and wells locked.
  * Albino rat 12 HP with bleed 2-3; Tengu waits 1 turn before attacking.
  * Champion rate 1/8 → 1/6 by floor 21.
  * Necromancers teleport their skeletons.
  * Shocking enchant: a flat 1/3 chance at 50% damage. (CL3:85-152)
* **v3.1 rooms:** 5 new standard rooms with decorative terrain, 10 new entrance/exit variants, and no more plain empty rooms (CL3:524-532).
* **Score (v3.1):** exploration score is now per-room. Doesn't matter for winning.

### 7.3 v2.x: the Duelist, the mines, trinkets, alchemy

* **Duelist (v2.0, tuned v2.1-v2.5, v4):**
  * Charges cap at 2-8, regaining 1 per 60−1.5·(cap−charges) turns. The Champion shares the charges with a higher cap and faster recharge.
  * The v2.4-2.5 buffs made Cleave instant on a kill, Heavy Blow always hit (only surprise hits get the bonus damage), Sneak a 6/5/4-tile blink, and Lunge, Spike and Retribution hit harder.
  * v4: Combo Strike lasts 15 turns after a kill; Feint costs 35, Challenge 50. (`MeleeWeapon.java:423-500`; CL2:377-387,506-521; CL4:347-348,369)
  * **Bot:** Champion with Cleave or Lunge on the main weapon, a shield or spear second.
* **Blacksmith mines (v2.2-v2.3):**
  * You go down **once**; 40 dark gold = 2000 favour, the boss +1000.
  * Since v3.3 reaching 40 gold always needs exactly 1 of 2 secret rooms: **search for it**. (CL3:111)
  * Smith offers 2 melee weapons, a thrown set and an armour; 30% enchanted (Parchment Scrap multiplies this; `Blacksmith.java:408`).
* **Trinkets (v2.4):** §6.
* **Alchemy:**
  * v2.4 removed catalysts (those recipes cost 8-9 more energy). Brews and elixirs got cheaper (see the reference §2.8 table, which is current).
  * v2.5: energizing a potion or scroll identifies it; a trinket energizes for 5.
  * v3.3: Honeyed Healing costs 2. (CL2:117,146,390-396,524-541; CL3:124)
  * **Rule:** turn known junk (spare Lullaby, Rage, unknown non-Healing duplicates) into energy for the trinket.
* **Inventory (v2.5):**
  * The Potion Bandolier holds the Waterskin; the Scroll Holder holds styli.
  * Armour takes 1 turn to equip.
  * Burning and Ooze end the moment you enter water (at least 1 turn of damage).
  * Dark floors give no torch and vision ×5/8.
  * Explosions spare equipment. (CL2:114-147,170; `Level.java:272-274`)
* **Journal (v2.5, v3.1, v3.3):**
  * The catalogue tabs include a bestiary and landmark notes (wells, shops, altars, lost backpack, beacon). Items can carry custom notes.
  * **For the assistant:** the landmark list is legal memory to show on the watch. Custom notes are a place to store our clue-based ID guesses for the player.
* **Ascension changes:**
  * v1.3 made it a real challenge.
  * v2.0: 2 kills a floor, harder region multipliers.
  * v2.1: max HP instead of damage resistance; XP to level 30.
  * v2.2: prison and caves numbers reverted.
  * v3.2: you're told when the curse first weakens. (CL1:301-302; CL2:937-939,987-991,1222-1234)

### 7.4 Boss changes since v1 (the reworks themselves are v0.8, in the reference §4)

* **Goo:** the pump always gives 1 action to react (v1.4).
* **Tengu:** 200 HP (v0.9); waits 1 turn before attacking (v3.3); switches targets (v3.1).
* **DM-300:**
  * no ability right after a supercharge (v2.5, v3.3 fix);
  * rockfall 6-12 (v3.2.5);
  * no gas at inorganic allies;
  * Levitation avoids the floor electricity (v2.0.1).
* **Dwarf King:** killing him cleanses Degrade (v3.1). Stones of Aggression no longer work on him (v4).
* **Yog:** fists are immune to sleep (v0.9); the Soiled fist is immune to burning, the Burning fist to freezing, and the Rotting and Rusted fists resist Retribution, Grim and Psionic Blast (v1.2). Aggression no longer works on Yog or the fists directly (v4).
* **Boss regen** limits were tightened (v2.1): heal before the fight.

---

## 8. Balance shifts for the build planner (v3.0 → v4.0.2)

**Stronger now:**
* **Items:**
  * Force Cube 10-30;
  * Dried Rose ghost 40+10·level HP, STR-scaled damage;
  * Footwear of Nature and Master Thieves' Armband charge up to +50% at +10;
  * Crystal, Eldritch, Corrupting, Blooming (v4.0.1);
  * Kunai 8 durability;
  * Telekinetic Grab;
  * Mage's staff on-hit effects for Battlemage: Fireblast, Lightning and Blast Wave reworked (v3.2).
* **Talents:**
  * Projectile Momentum, Durable Projectiles, Point Blank (v3.3);
  * Guiding Light, Lay on Hands, Aura of Protection, Divine Sense, Holy Lance (cooldown 30);
  * Cleave (Gladiator) 30/45/60;
  * Hearty Meal 4/6 at ≤33%;
  * Provoked Anger +3/+5;
  * Liquid Willpower 6.5/10% max HP.
* **Abilities:** Combo Strike (on-kill duration), Feint (35).

**Weaker now:**
* **Items:**
  * Stone of Aggression on bosses (none);
  * Ring of Haste ×1.15;
  * Spyglass items 10% visible;
  * Bolas 4-9;
  * Boomerang 5-turn return;
  * Shocking enchant at high levels;
  * Swiftness glyph (needs 2 tiles of space);
  * Mossy Clump dew;
  * Shared Upgrades (scales with thrown tier).
* **Subclasses and talents:**
  * Berserker −25% rage, enrage shield 8+2·level;
  * Barkskin (Warden) 33% of level and no furrowed grass;
  * Hold Fast needs the seal;
  * Monk loses energy on revive and on entering the vault;
  * Searing Light +3/+5;
  * Enlightening Meal 0.67/1;
  * Cleanse not instant.
* **Abilities:** Challenge costs 50.

**Unchanged and still bot-best:**
* Warrior: Berserker with Deathless Fury.
* Rogue: Assassin.
* Huntress: Sniper (the Warden was nerfed).
* Duelist: Champion with Twin Upgrades.
* Cleric: Paladin with Lay on Hands.
* Mage: Warlock (sustain).

For the climb specifically, raise the value of:
* anything that adds allies (Dried Rose, Warding, Spirit Hawk, Power of Many, Shadow Clone);
* bombs and area damage;
* armour talents and glyphs.

---

## 9. Challenges in v4 (for later)

| Challenge | What it does in v4.0.2 | How the bot adapts |
|---|---|---|
| On Diet | Food and the Horn give ⅓ satiety; other satiety unaffected (`S/items/food/Food.java:116`) | Salt Cube is mandatory. Each floor budget is ~110 turns: no resting for HP, so rely on dew, Healing and the Chalice. Nature's Bounty and Cached Rations go up in value. |
| Faith is my Armor | Armour DR max = 1+tier+level, min 0 (`Armor.java:380-398`) | Armour SoU are worthless: weapon first. Evasion (Ring of Evasion, Ferret Tuft becomes usable) and allies. The climb gets much harder. |
| Pharmacophobia | Healing (and its recipes, sunfruit) poisons the hero | Healing is a weapon to throw or energize. Dew, Chalice, Lay on Hands, Shielding and regen become the heals. |
| Barren Land | No dew, plants or tall-grass seeds take root | No Sungrass or Waterskin. Food and regen matter more. Mossy Clump loses value. |
| Swarm Intelligence (v4 rework) | Each enemy that sees you attracts others within 2 tiles, +2 a turn up to 12. **Any** break in sight resets it, even briefly at a door. (CL4:220-227; `S/actors/buffs/SwarmIntelTracker.java`) | Doorway and corner fighting becomes mandatory; close doors between fights. Never fight in the open for more than 2-3 turns. |
| Into Darkness | View distance 2 (normally 8), a torch per floor, other light sources ⅕ as effective (`Level.java:158`) | Torches first, Mind Vision and Magic Mapping saved, Eye of Newt never |
| Forbidden Runes | Every 2nd SoU removed (8 of 15) (`Level.java:233-240`) | The upgrade plan halves: armour only; Blacksmith upgrades and reforges count double |
| Hostile Champions | 1/8 of foes are champions on floor 1, rising to 1/6 by floor 21. Blazing, Projecting (+3 reach), Antimagic (−50% damage, magic immune), Giant (−80% damage, +1 reach, can't enter tunnels), Blessed (4× accuracy and evasion), Growing. (`M/misc/misc.properties:231`) | You're warned when one spawns. Retreat into a 1-wide tunnel from a Giant; use melee on Antimagic; surprise attacks on Blessed. |
| Badder Bosses | Goo +20% HP, heals 3/turn, 1-turn pump; Tengu +25%; DM-300 +33% with 3 pylons; Dwarf King +50%; Yog summons 2 fists at a time with +60% laser (`M/misc/misc.properties:233`) | Each boss protocol (agent K3) needs a variant. Goo: never let it stand in water. |

The order to push challenges once the base win rate is about 90%: Badder Bosses (the protocols already exist), then Champions, then Swarm (doorway logic), then Forbidden Runes, then On Diet. Last: Faith, Pharmacophobia, Barren Land, Darkness.

---

## 10. Work this implies, by area

* **Items and alchemy (K3 alchemy, items):**
  * There's no trinket preference today (`BotWindows` takes the first option) and no trinket upgrading. Add the §6 ranking, and spend energy on the chosen trinket to +3.
  * In `GearValue`, give the Dried Rose weight in the artifact odds.
  * Don't make a Crystal-enchanted weapon the SoU target.
  * Save bombs for the climb.
* **Talents and Build:** Hold Fast without the seal; Barkskin 33%; Feint 35 / Challenge 50; boost ally and armour picks for the climb.
* **Bosses (K3):** no Aggression on bosses; Tengu's 1-turn delay; the Badder Bosses variants (later).
* **Ascent (K7):**
  * Change the stop rule to "stacks ≤ 1, then go" (§3.3-3.4), with a choke point near the arrival stairs.
  * Treat boss floors as rest stops.
  * Escape down the stairs.
  * Use the Imp's shop on 20.
  * Treat traps at depth-26 damage.
  * Finish quests before ascending.
* **Models and Brain (O, BF):** FightModel and MobTable must multiply foe HP, damage, accuracy, evasion **and armour** by `AscensionChallenge.statModifier`. Model 2× speed for non-hunting foes at 4+ stacks, the halved hero speed at 6+, and the damage-over-time at 8+.
* **Identification (ID):**
  * Hazard rooms → their solution potion on that floor (rule 15).
  * Stone of Intuition gets 2 uses.
  * Energizing identifies.
  * With Parchment Scrap at +3, random weapons and armour are known uncursed.
  * Shard of Oblivion disables passive ID.
* **Policy for Thomas:** may the bot and assistant use Cracked Spyglass items once they're in view? They're drawn at 10% alpha, so on the watch they may be invisible.
