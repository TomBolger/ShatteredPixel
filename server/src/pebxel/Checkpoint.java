package pebxel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Floor checkpoints (agent SS), for replaying one seed from a floor in seconds: sim/seed.sh --from DEPTH.
 * <p>
 * With CKPT set (1, or a folder to keep them in), every floor is entered twice: on the first ready turn of each floor
 * visit the game is saved (Shattered's own save files, its journal and badges, and the bot's statics that serialise:
 * {@link Statics#dump}) and then reloaded from those files at once, as a player's "continue" would. So a game resumed
 * from a kept checkpoint (FROM=folder) and the game played through are the same game from there on, by construction:
 * both play on from the same files. The random numbers are seeded afresh at each checkpoint from the floor and the
 * turn (with SIMSEED and the dungeon seed), and Actor numbering starts again, the same either way.
 * <p>
 * The bot forgets at each floor what can't be written down (things it held by reference: an Item, a Mob, a Level),
 * as the assistant would after the phone app restarted; what it keeps is in Shattered's save (identified kinds,
 * quests) or plain values (counters, kinds tried, random generators). A game with CKPT plays differently from one
 * without (a different random stream from each floor on), so compare CKPT games with CKPT games: seed.sh and
 * regress.sh always set it, and a batch run with CKPT=1 replays locally the same.
 */
final class Checkpoint {
	private Checkpoint() { }

	static final String KEEP = keep();
	static final String FROM = System.getenv("FROM");
	static final boolean ON = System.getenv("CKPT") != null && !System.getenv("CKPT").isEmpty() && !"0".equals(System.getenv("CKPT")) || FROM != null;
	static final String FILE = "pebxel-ckpt.dat";
	/** Checkpoints taken this game (the folders' numbering). */
	static int taken;
	/** The bot's classes whose statics go with a checkpoint (the rest, harness and watch code, are left alone). */
	static boolean bot(Class<?> c) {
		String n = c.getName(); if (n.startsWith("pebxel.bot.")) return true;
		String top = c.getSimpleName(); Class<?> o = c; while (o.getEnclosingClass() != null) o = o.getEnclosingClass(); top = o.getSimpleName();
		return top.startsWith("As") || top.equals("Build") || top.equals("Explorer") || top.startsWith("Bot") || top.equals("Cheat") || top.equals("HumanTalents")
				|| top.equals("Rescue") || top.equals("FightCal") || top.equals("Coverage") || top.equals("Sense");
	}
	static String keep() { String k = System.getenv("CKPT"); return k == null || k.isEmpty() || k.equals("0") || k.equals("1") ? null : k; }

	/** The game's folder here (its save slot), and the profile's own folder. */
	static FileHandle profile() { return Gdx.files.external(AssistBot.saveDir); }

	/**
	 * At the first ready turn of a floor visit: saves, keeps a copy if CKPT names a folder, and reloads. loop: the bot
	 * loop's own state (AssistBot.play), written with the rest.
	 */
	static void take(Map<String, Object> loop) throws Exception {
		Dungeon.saveAll(); Badges.saveGlobal(true); Journal.saveGlobal(true);
		loop.put("depth", Dungeon.depth); loop.put("branch", Dungeon.branch); loop.put("turn", Math.round(Statistics.duration)); loop.put("taken", ++taken);
		loop.put("statics", Statics.dump(Checkpoint::bot));
		writeObj(profile().child(FILE).file(), loop);
		if (KEEP != null) {
			Path to = Paths.get(KEEP, String.format("%02d-d%d%s", taken, Dungeon.depth, Dungeon.branch == 0 ? "" : "b" + Dungeon.branch));
			copyTree(profile().file().toPath(), to);
		}
		reload();
	}

	/** FROM: the checkpoint's files into this game's fresh profile, then the reload. Returns the bot loop's state. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> resume() throws Exception {
		Path from = Paths.get(FROM);
		if (!Files.isRegularFile(from.resolve(FILE))) throw new FileNotFoundException("no checkpoint in " + from);
		copyTree(from, profile().file().toPath());
		Map<String, Object> loop = reload();
		taken = (Integer) loop.get("taken");
		return loop;
	}

	/** Both ways in: everything from the files in the profile folder, then Shattered's "continue". */
	@SuppressWarnings("unchecked")
	static Map<String, Object> reload() throws Exception {
		Map<String, Object> loop = (Map<String, Object>) readObj(profile().child(FILE).file());
		// a blank scene first (the floor's scene ended, its actor thread gone), as between games
		Game.switchScene(com.watabou.noosa.Scene.class); for (int i = 0; i < 3; i++) AssistBot.step();
		AssistBot.transients();
		AssistBot.globals(true);   // (the journal and badges read back from the files)
		Statics.restore(Checkpoint::bot); Statics.load((byte[]) loop.get("statics"));
		GamesInProgress.setUnknown(GamesInProgress.curSlot);
		AssistBot.setStatic(Actor.class, "pebxelHashes", 0);
		String s = System.getenv("SIMSEED");
		if (s != null && !s.isEmpty()) {
			long k = Long.parseLong(s) * 1_000_003L + Dungeon.seed * 31 + (Integer) loop.get("depth") * 7919L + (Integer) loop.get("branch") * 104729L + (Integer) loop.get("turn") * 1_299_709L;
			com.watabou.utils.Random.pebxelVisualReset();
			com.watabou.utils.Random.pebxelSource = Cheat.mixed(k);
			com.watabou.utils.Random.resetGenerators();
		}
		InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;
		Game.switchScene(InterlevelScene.class);
		return loop;
	}

	static void writeObj(File f, Object o) throws IOException { try (ObjectOutputStream w = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) { w.writeObject(o); } }
	static Object readObj(File f) throws Exception { try (ObjectInputStream r = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))) { return r.readObject(); } }
	static void copyTree(Path from, Path to) throws IOException {
		try (java.util.stream.Stream<Path> s = Files.walk(from)) {
			for (Path p : (Iterable<Path>) s::iterator) {
				Path t = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
