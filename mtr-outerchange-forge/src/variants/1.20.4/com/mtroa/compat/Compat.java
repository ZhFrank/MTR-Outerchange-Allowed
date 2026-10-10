package com.mtroa.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minecraft 1.20.4 (Forge, official mappings) implementation of the few version dependent calls this
 * mod needs. Compared with 1.20.1, scores are addressed through a {@link ScoreHolder} and the
 * returned {@link ScoreAccess} is a plain int accessor, and compressed NBT requires an accounter.
 */
public final class Compat {

	private Compat() {
	}

	public static int getScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName) {
		final Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return 0;
		}
		final ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName), objective);
		return scoreAccess == null ? 0 : scoreAccess.get();
	}

	public static void setScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final Objective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName), objective).set(value);
		}
	}

	public static void addScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final Objective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName), objective).add(value);
		}
	}

	private static Objective getOrCreate(Scoreboard scoreboard, String objectiveName, String displayName) {
		final Objective existing = scoreboard.getObjective(objectiveName);
		if (existing != null) {
			return existing;
		}
		try {
			return scoreboard.addObjective(objectiveName, ObjectiveCriteria.DUMMY, Component.literal(displayName),
					ObjectiveCriteria.RenderType.INTEGER, false, null);
		} catch (Exception e) {
			return scoreboard.getObjective(objectiveName);
		}
	}

	/**
	 * Whether a chunk is already loaded. A scan of a station area uses this to skip everything that
	 * is not loaded, because asking for a block elsewhere would drag the chunk in and generate it.
	 */
	public static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		return level.getChunkSource().hasChunk(chunkX, chunkZ);
	}

	/**
	 * Lowest and highest buildable y of the world. A station area records its corners with the
	 * smallest and largest y a long can hold, so a scan has to cut the area down to the height the
	 * world really has before walking it, otherwise it would start far below the world and never
	 * reach the barriers.
	 */
	public static int getBottomY(ServerLevel level) {
		return level.getMinBuildHeight();
	}

	public static int getTopY(ServerLevel level) {
		return level.getMaxBuildHeight();
	}

	public static CompoundTag readCompressed(InputStream inputStream) throws IOException {
		return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
	}

	public static void writeCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
		NbtIo.writeCompressed(tag, outputStream);
	}
}
