package com.mtroa.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minecraft 1.20.1 implementation of the few version dependent calls this mod needs.
 */
public final class Compat {

	private Compat() {
	}

	public static int getScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName) {
		final ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
		return objective == null ? 0 : scoreboard.getPlayerScore(playerName, objective).getScore();
	}

	public static void setScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final ScoreboardObjective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getPlayerScore(playerName, objective).setScore(value);
		}
	}

	public static void addScore(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		final ScoreboardObjective objective = getOrCreate(scoreboard, objectiveName, displayName);
		if (objective != null) {
			scoreboard.getPlayerScore(playerName, objective).incrementScore(value);
		}
	}

	private static ScoreboardObjective getOrCreate(Scoreboard scoreboard, String objectiveName, String displayName) {
		final ScoreboardObjective existing = scoreboard.getNullableObjective(objectiveName);
		if (existing != null) {
			return existing;
		}
		try {
			return scoreboard.addObjective(objectiveName, ScoreboardCriterion.DUMMY, Text.literal(displayName), ScoreboardCriterion.RenderType.INTEGER);
		} catch (Exception e) {
			return scoreboard.getNullableObjective(objectiveName);
		}
	}

	public static NbtCompound readCompressed(InputStream inputStream) throws IOException {
		return NbtIo.readCompressed(inputStream);
	}

	public static void writeCompressed(NbtCompound nbtCompound, OutputStream outputStream) throws IOException {
		NbtIo.writeCompressed(nbtCompound, outputStream);
	}
}
