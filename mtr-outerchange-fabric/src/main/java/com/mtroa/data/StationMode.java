package com.mtroa.data;

/**
 * How a whole station was configured at once from the dashboard.
 * <p>
 * The dashboard writes a station wide decision instead of touching every barrier: {@link #ALLOW} and
 * {@link #DENY} only record the decision, they never rewrite the individual barrier entries, so
 * switching back to {@link #CUSTOM} restores whatever each barrier was set to before. As soon as a
 * single barrier of the station is edited by hand while the station is in {@link #ALLOW} or
 * {@link #DENY}, the station drops back to {@link #CUSTOM} automatically.
 */
public enum StationMode {

	/** Every exit barrier of the station allows outerchange, regardless of its own setting. */
	ALLOW,
	/** No exit barrier of the station allows outerchange, regardless of its own setting. */
	DENY,
	/** Each barrier follows its own stored setting. */
	CUSTOM;

	public static StationMode byOrdinal(int ordinal) {
		final StationMode[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CUSTOM;
	}
}
