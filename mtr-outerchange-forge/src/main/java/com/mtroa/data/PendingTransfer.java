package com.mtroa.data;

/**
 * A journey that has been interrupted by an "outerchange" (out of station interchange) exit.
 * The fare is not paid yet - it is only paid when the player fails to enter again before
 * {@link #expiryMillis}.
 */
public class PendingTransfer {

	public static final String ENTRY_ZONE_1 = "mtr_entry_zone_1";
	public static final String ENTRY_ZONE_2 = "mtr_entry_zone_2";
	public static final String ENTRY_ZONE_3 = "mtr_entry_zone_3";
	public static final String BALANCE = "mtr_balance";

	public static final int BASE_FARE = 2;
	public static final int ZONE_FARE = 1;

	public final String playerName;
	public final long expiryMillis;
	/** Raw (encoded) entry zone values as stored inside the scoreboard when the player exited. */
	public final long entryZone1;
	public final long entryZone2;
	public final long entryZone3;
	/** Zone values of the station whose exit barrier was used. */
	public final long exitZone1;
	public final long exitZone2;
	public final long exitZone3;
	public final String stationName;
	public final boolean concessionary;

	public PendingTransfer(String playerName, long expiryMillis, long entryZone1, long entryZone2, long entryZone3, long exitZone1, long exitZone2, long exitZone3, String stationName, boolean concessionary) {
		this.playerName = playerName;
		this.expiryMillis = expiryMillis;
		this.entryZone1 = entryZone1;
		this.entryZone2 = entryZone2;
		this.entryZone3 = entryZone3;
		this.exitZone1 = exitZone1;
		this.exitZone2 = exitZone2;
		this.exitZone3 = exitZone3;
		this.stationName = stationName == null ? "" : stationName;
		this.concessionary = concessionary;
	}

	public long getFare() {
		final long zoneDifference = Math.abs(exitZone1 - decodeZone(entryZone1))
				+ Math.abs(exitZone2 - decodeZone(entryZone2))
				+ Math.abs(exitZone3 - decodeZone(entryZone3));
		final long fare = BASE_FARE + ZONE_FARE * zoneDifference;
		return concessionary ? (long) Math.ceil(fare / 2.0) : fare;
	}

	private static long decodeZone(long zone) {
		return zone > 0 ? zone - 1 : zone;
	}
}
