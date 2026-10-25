package pebxel;

import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Puts our own classes' static fields back as they were before the first game, so several bot games can share one JVM
 * (warm JIT, no start-up each game) and each plays as it would alone. Every class of ours (package pebxel and below,
 * less a few test programs) is loaded and initialised up front, then its statics are recorded: values; copies of
 * arrays; the contents of collections and maps (put back into the same object); a serialised copy of anything else
 * serialisable (a java.util.Random), else the reference itself. Final fields are left alone except collections, maps
 * and arrays, whose contents come back. New statics added to the bot later are covered without anyone listing them.
 */
final class Statics {
	private Statics() { }

	private static final Set<String> SKIP = Set.of("Gallery", "RadioTest", "Server", "Traffic", "LagTest", "Tour", "Shots", "MenuTest", "Buttons", "Statics", "DeterminismTest");
	private static final List<Saved> saved = new ArrayList<>();
	static int classes;

	private static final class Saved {
		final Field f; final Object v; final int kind;   // 0 value or reference, 1 array, 2 collection, 3 map, 4 serialised
		Saved(Field f, Object v, int kind) { this.f = f; this.v = v; this.kind = kind; }
	}

	/** Loads, initialises and records every class of ours. Call once, after the app is set up, before the first game. */
	static void snapshot() throws Exception {
		// Shattered's own classes too, all initialised now in one fixed order: a few draw a random number as they load
		// (WindParticle's wind), and a class first used in the 3rd game of a JVM shifted that game's numbers alone
		for (String n : names(com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon.class, "com/")) {
			try { Class.forName(n, true, Statics.class.getClassLoader()); } catch (Throwable e) { }
		}
		for (String n : names(Statics.class, "pebxel/")) {
			Class<?> c;
			try { c = Class.forName(n, true, Statics.class.getClassLoader()); } catch (Throwable e) { continue; }
			classes++;
			for (Field f : c.getDeclaredFields()) {
				int m = f.getModifiers();
				if (!Modifier.isStatic(m) || f.isSynthetic() || f.getType().isPrimitive() && Modifier.isFinal(m)) continue;
				try { f.setAccessible(true); } catch (Throwable e) { continue; }
				Object v = f.get(null);
				boolean fin = Modifier.isFinal(m);
				if (v != null && v.getClass().isArray()) saved.add(new Saved(f, copyArray(v), 1));
				else if (v instanceof Collection) saved.add(new Saved(f, new Object[]{v, new ArrayList<>((Collection<?>) v)}, 2));
				else if (v instanceof Map) saved.add(new Saved(f, new Object[]{v, new LinkedHashMap<>((Map<?, ?>) v)}, 3));
				else if (fin) continue;
				else if (v instanceof Serializable && !(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean) && !(v instanceof Enum) && !(v instanceof Character)) {
					byte[] b = serialise(v); saved.add(b != null ? new Saved(f, b, 4) : new Saved(f, v, 0));
				}
				else saved.add(new Saved(f, v, 0));
			}
		}
	}

	/** Every recorded static back as it was. */
	static void restore() throws Exception { restore(c -> true); }
	/** The recorded statics of the classes chosen put back as they were. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static void restore(java.util.function.Predicate<Class<?>> which) throws Exception {
		for (Saved s : saved) {
			Field f = s.f; boolean fin = Modifier.isFinal(f.getModifiers());
			if (!which.test(f.getDeclaringClass())) continue;
			switch (s.kind) {
				case 0: f.set(null, s.v); break;
				case 1: { Object cur = f.get(null);
					if (fin && cur != null && Array.getLength(cur) == Array.getLength(s.v)) System.arraycopy(s.v, 0, cur, 0, Array.getLength(cur));
					else if (!fin) f.set(null, copyArray(s.v));
					break; }
				case 2: { Object[] p = (Object[]) s.v; Collection c = (Collection) p[0]; c.clear(); c.addAll((Collection) p[1]); if (!fin) f.set(null, c); break; }
				case 3: { Object[] p = (Object[]) s.v; Map mp = (Map) p[0]; mp.clear(); mp.putAll((Map) p[1]); if (!fin) f.set(null, mp); break; }
				case 4: f.set(null, deserialise((byte[]) s.v)); break;
			}
		}
	}

	/**
	 * The chosen classes' statics now, as far as they can be written to a file (Checkpoint): values, and the contents of
	 * arrays, collections and maps, each thing kept only if it serialises alone. Game objects (an Item, a Mob, a Level)
	 * don't, so they are left out: after a reload they would be stale anyway. {@link #load} puts them back after
	 * {@link #restore}: what was left out stays as before the first game.
	 */
	static byte[] dump(java.util.function.Predicate<Class<?>> which) throws Exception {
		LinkedHashMap<String, Object[]> out = new LinkedHashMap<>();
		for (Saved s : saved) {
			Field f = s.f;
			if (!which.test(f.getDeclaringClass())) continue;
			String k = f.getDeclaringClass().getName() + "#" + f.getName();
			Object v = f.get(null);
			if (v == null) { out.put(k, new Object[]{"V", null}); continue; }
			if (v.getClass().isArray()) {
				Object c = copyArray(v);
				if (!v.getClass().getComponentType().isPrimitive()) for (int i = 0; i < Array.getLength(c); i++) { Object e = Array.get(c, i); if (e != null && serialise(e) == null) Array.set(c, i, null); }
				if (serialise(c) != null) out.put(k, new Object[]{"A", c});
			} else if (v instanceof Collection) {
				ArrayList<Object> l = new ArrayList<>();
				for (Object e : (Collection<?>) v) if (e == null || serialise(e) != null) l.add(e);
				out.put(k, new Object[]{"C", l, v.getClass().getName()});
			} else if (v instanceof Map) {
				ArrayList<Object> l = new ArrayList<>();
				for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) if ((e.getKey() == null || serialise(e.getKey()) != null) && (e.getValue() == null || serialise(e.getValue()) != null)) { l.add(e.getKey()); l.add(e.getValue()); }
				out.put(k, new Object[]{"M", l, v.getClass().getName()});
			} else if (!Modifier.isFinal(f.getModifiers()) && serialise(v) != null) out.put(k, new Object[]{"V", v});
		}
		byte[] b = serialise(out);
		if (b == null) throw new IOException("statics would not serialise");
		return b;
	}

	/** What {@link #dump} wrote, put back (after {@link #restore} of the same classes). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static void load(byte[] b) throws Exception {
		Map<String, Object[]> in = (Map<String, Object[]>) deserialise(b);
		for (Saved s : saved) {
			Field f = s.f; boolean fin = Modifier.isFinal(f.getModifiers());
			Object[] w = in.get(f.getDeclaringClass().getName() + "#" + f.getName());
			if (w == null) continue;
			Object cur = f.get(null);
			switch ((String) w[0]) {
				case "V": if (!fin) f.set(null, w[1]); break;
				case "A": if (cur != null && cur.getClass() == w[1].getClass() && Array.getLength(cur) == Array.getLength(w[1])) System.arraycopy(w[1], 0, cur, 0, Array.getLength(cur));
					else if (!fin) f.set(null, w[1]); break;
				case "C": case "M": {
					if (cur == null && !fin) { try { cur = Class.forName((String) w[2]).getDeclaredConstructor().newInstance(); f.set(null, cur); } catch (Throwable e) { break; } }
					if (cur == null) break;
					List<Object> l = (List<Object>) w[1];
					if (w[0].equals("C")) { ((Collection) cur).clear(); ((Collection) cur).addAll(l); }
					else { ((Map) cur).clear(); for (int i = 0; i + 1 < l.size(); i += 2) ((Map) cur).put(l.get(i), l.get(i + 1)); }
					break;
				}
			}
		}
	}

	private static Object copyArray(Object a) {
		int n = Array.getLength(a);
		Object c = Array.newInstance(a.getClass().getComponentType(), n);
		System.arraycopy(a, 0, c, 0, n);
		return c;
	}
	private static byte[] serialise(Object v) {
		try { ByteArrayOutputStream b = new ByteArrayOutputStream(); try (ObjectOutputStream o = new ObjectOutputStream(b)) { o.writeObject(v); } return b.toByteArray(); }
		catch (Throwable e) { return null; }
	}
	private static Object deserialise(byte[] b) throws Exception {
		try (ObjectInputStream o = new ObjectInputStream(new ByteArrayInputStream(b))) { return o.readObject(); }
	}

	/** The names of the classes under a folder (pebxel/, com/), from the folder or jar class c was loaded from. */
	private static List<String> names(Class<?> c, String under) throws Exception {
		URI where = c.getProtectionDomain().getCodeSource().getLocation().toURI();
		List<String> out = new ArrayList<>();
		Path p = Paths.get(where);
		if (Files.isDirectory(p)) { if (Files.isDirectory(p.resolve(under))) try (Stream<Path> s = Files.walk(p.resolve(under))) { s.forEach(f -> add(out, p.relativize(f).toString(), under)); } }
		else try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(p.toFile())) { z.stream().forEach(e -> add(out, e.getName(), under)); }
		Collections.sort(out);
		return out;
	}
	private static void add(List<String> out, String path, String under) {
		path = path.replace('\\', '/');
		if (!path.startsWith(under) || !path.endsWith(".class")) return;
		String n = path.substring(0, path.length() - 6).replace('/', '.');
		String top = n.substring(n.lastIndexOf('.') + 1); int d = top.indexOf('$'); if (d >= 0) top = top.substring(0, d);
		if (n.startsWith("pebxel.android") || under.equals("pebxel/") && SKIP.contains(top) || n.contains(".android.") || n.contains(".desktop.") || n.contains(".ios.")) return;
		out.add(n);
	}
}
