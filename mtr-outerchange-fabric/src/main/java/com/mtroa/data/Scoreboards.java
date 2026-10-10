package com.mtroa.data;

import com.mtroa.compat.Compat;
import net.minecraft.scoreboard.Scoreboard;

/**
 * Reads and writes the very same scoreboard objectives the Minecraft Transit Railway mod uses.
 */
public final class Scoreboards {

	private Scoreboards() {
	}

	public static int get(Scoreboard scoreboard, String objectiveName, String displayName, String playerName) {
		return Compat.getScore(scoreboard, objectiveName, displayName, playerName);
	}

	public static void set(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		Compat.setScore(scoreboard, objectiveName, displayName, playerName, value);
	}

	public static void increment(Scoreboard scoreboard, String objectiveName, String displayName, String playerName, int value) {
		Compat.addScore(scoreboard, objectiveName, displayName, playerName, value);
	}
}
