package com.mtroa.data;

import org.mtr.mapping.holder.World;
import org.mtr.mod.Init;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers which ticket barrier each player most recently stepped into.
 * <p>
 * MTR resolves the station asynchronously and only then calls its private {@code onEnter} / {@code onExit}
 * handlers, and those handlers do not receive the barrier position. The position is needed to look up the
 * per barrier outerchange configuration, so it is remembered here while {@code passThrough} still has it.
 */
public final class BarrierTracker {

	private static final Map<String, Ref> CURRENT = new HashMap<>();
	/** A reference older than this is never used, so a stale entry can never leak into a later journey. */
	private static final long MAX_AGE_MILLIS = 10000L;
	private static final int MAX_SIZE = 64;

	private BarrierTracker() {
	}

	public static void remember(World world, String playerName, int x, int y, int z) {
		if (CURRENT.size() > MAX_SIZE) {
			final long now = System.currentTimeMillis();
			final Iterator<Map.Entry<String, Ref>> iterator = CURRENT.entrySet().iterator();
			while (iterator.hasNext()) {
				if (now - iterator.next().getValue().createdMillis > MAX_AGE_MILLIS) {
					iterator.remove();
				}
			}
		}
		CURRENT.put(playerName, new Ref(Init.getWorldId(world), x, y, z));
	}

	/**
	 * Consumes and returns the remembered barrier for this player, or {@code null} if there is none or if
	 * it no longer belongs to the world the player is in.
	 */
	public static Ref consume(World world, String playerName) {
		final Ref ref = CURRENT.remove(playerName);
		if (ref == null) {
			return null;
		}
		return ref.matches(world) ? ref : null;
	}

	public static final class Ref {

		private final String dimensionId;
		private final int x;
		private final int y;
		private final int z;
		private final long createdMillis = System.currentTimeMillis();

		private Ref(String dimensionId, int x, int y, int z) {
			this.dimensionId = dimensionId;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public String getDimensionId() {
			return dimensionId;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getZ() {
			return z;
		}

		private boolean matches(World world) {
			return System.currentTimeMillis() - createdMillis <= MAX_AGE_MILLIS
					&& dimensionId.equals(Init.getWorldId(world));
		}
	}
}
