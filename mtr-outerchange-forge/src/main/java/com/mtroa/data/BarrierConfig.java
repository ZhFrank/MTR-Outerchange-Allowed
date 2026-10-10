package com.mtroa.data;

/**
 * Per exit barrier configuration.
 * <p>
 * <b>outerchangeAllowed</b>: when a player who holds an entry record passes through, the fare is not
 * deducted immediately. Instead an outerchange is recorded for that player, and the entry record is
 * kept so the journey continues when they enter again.<br>
 * <b>maxMinutes</b>: how long the player may stay outside the paid area before the fare that would
 * have been paid at this barrier is charged automatically.
 * <p>
 * Entrance barriers have no configuration: they simply look for an outerchange record left by an
 * exit barrier, so no per barrier setup is needed on the entrance side.
 */
public class BarrierConfig {

	public static final int MIN_MINUTES = 1;
	public static final int MAX_MINUTES = 120;
	public static final int DEFAULT_MINUTES = 10;

	public boolean outerchangeAllowed = false;
	public int maxMinutes = DEFAULT_MINUTES;

	public BarrierConfig() {
	}

	public BarrierConfig(boolean outerchangeAllowed, int maxMinutes) {
		this.outerchangeAllowed = outerchangeAllowed;
		this.maxMinutes = clampMinutes(maxMinutes);
	}

	public static int clampMinutes(int minutes) {
		return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
	}
}
