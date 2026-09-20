package com.mtroa.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minecraft 1.20.4 implementation of the few version dependent calls this mod needs.
 */
public final class Compat {

	private Compat() {
	}

	public static int getScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName) {
		final ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
		if (objective == null) {
			return 0;
		}
		final ReadableScoreboardScore score = scoreboard.getScore(ScoreHolder.fromName(playerName), objective);
		return score == null ? 0 : score.getScore();
	}

	public static void setScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final ScoreboardObjective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getOrCreateScore(ScoreHolder.fromName(playerName), objective).setScore(value);
		}
	}

	public static void addScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final ScoreboardObjective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getOrCreateScore(ScoreHolder.fromName(playerName), objective).incrementScore(value);
		}
	}

	private static ScoreboardObjective getOrCreate(Scoreboard scoreboard, String objectiveName, String displayName) {
		final ScoreboardObjective existing = scoreboard.getNullableObjective(objectiveName);
		if (existing != null) {
			return existing;
		}
		try {
			return scoreboard.addObjective(objectiveName, ScoreboardCriterion.DUMMY, Text.literal(displayName), ScoreboardCriterion.RenderType.INTEGER, false, null);
		} catch (Exception e) {
			return scoreboard.getNullableObjective(objectiveName);
		}
	}

	public static NbtCompound readCompressed(InputStream inputStream) throws IOException {
		return NbtIo.readCompressed(inputStream, NbtSizeTracker.ofUnlimitedBytes());
	}

	public static void writeCompressed(NbtCompound nbtCompound, OutputStream outputStream) throws IOException {
		NbtIo.writeCompressed(nbtCompound, outputStream);
	}
}
