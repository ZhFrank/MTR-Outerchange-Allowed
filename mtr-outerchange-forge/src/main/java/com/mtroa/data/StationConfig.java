package com.mtroa.data;

/**
 * Station wide outerchange decision, as written from the MTR dashboard.
 * <p>
 * Only {@link #mode} is stored here. The individual barrier settings are deliberately left alone so
 * that a station can be flipped to {@link StationMode#ALLOW} or {@link StationMode#DENY} for a while
 * and still fall back to exactly the previous per barrier configuration afterwards.
 */
public class StationConfig {

	private StationMode mode = StationMode.CUSTOM;

	public StationMode getMode() {
		return mode;
	}

	public void setMode(StationMode mode) {
		this.mode = mode == null ? StationMode.CUSTOM : mode;
	}

	/**
	 * Whether this station currently overrides its barriers. A station in override mode reports
	 * {@code true} for every barrier, which is what makes a later manual edit of a single barrier
	 * fall back to {@link StationMode#CUSTOM}.
	 */
	public boolean isOverride() {
		return mode != StationMode.CUSTOM;
	}

	/**
	 * The decision this station forces onto a barrier, or {@code null} when the barrier keeps its own
	 * setting.
	 */
	public Boolean forcedValue() {
		switch (mode) {
			case ALLOW:
				return Boolean.TRUE;
			case DENY:
				return Boolean.FALSE;
			default:
				return null;
		}
	}
}
