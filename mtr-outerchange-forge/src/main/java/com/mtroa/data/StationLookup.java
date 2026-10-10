package com.mtroa.data;

import com.mtroa.MtrOuterchange;
import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Data;
import org.mtr.core.data.Station;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Resolves an MTR station from its id on the server.
 * <p>
 * The dashboard only ever knows a station id, while the change has to be applied to the real
 * {@link Station} object because that is what carries the area the barriers are looked up in. MTR keeps
 * one {@link Simulator} per dimension, and a simulator is a {@link Data}, which is where the stations
 * live. That registry is private, so it is read reflectively and cached; if a future MTR version moves
 * it, the lookup simply reports failure instead of breaking the game.
 */
public final class StationLookup {

	private static Field MAIN_FIELD;
	private static Field SIMULATORS_FIELD;
	private static boolean initialised;

	private StationLookup() {
	}

	/**
	 * Finds the station with the given id in the player's dimension.
	 *
	 * @return the station, or {@code null} when it cannot be found
	 */
	public static Station find(ServerLevel level, long stationId) {
		try {
			if (!initialise()) {
				return null;
			}
			final Object main = MAIN_FIELD.get(null);
			if (main == null) {
				return null;
			}
			final Object simulators = SIMULATORS_FIELD.get(main);
			if (!(simulators instanceof List)) {
				return null;
			}
			final String dimensionId = com.mtroa.network.Networking.dimensionId(level);
			for (Object entry : (List<?>) simulators) {
				if (!(entry instanceof Simulator)) {
					continue;
				}
				final Simulator simulator = (Simulator) entry;
				if (!matchesDimension(simulator, dimensionId)) {
					continue;
				}
				final Station station = byId(simulator, stationId);
				if (station != null) {
					return station;
				}
			}
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to look up station " + stationId, throwable);
		}
		return null;
	}

	/**
	 * Finds the station whose area contains a block position.
	 * <p>
	 * MTR does not record which blocks belong to a station, but every station carries the bounding box
	 * the player drew for it, so containment is enough. This is deliberately resolved on demand instead
	 * of being cached once when a station decision is written: a station may span chunks that are not
	 * loaded at that moment, and a decision has to keep working however the player reaches a barrier.
	 *
	 * @return the station, or {@code null} when the position is not inside any station
	 */
	public static Station stationAt(ServerLevel level, int x, int y, int z) {
		try {
			if (!initialise()) {
				return null;
			}
			final Object main = MAIN_FIELD.get(null);
			if (main == null) {
				return null;
			}
			final Object simulators = SIMULATORS_FIELD.get(main);
			if (!(simulators instanceof List)) {
				return null;
			}
			final String dimensionId = com.mtroa.network.Networking.dimensionId(level);
			for (Object entry : (List<?>) simulators) {
				if (!(entry instanceof Simulator)) {
					continue;
				}
				final Simulator simulator = (Simulator) entry;
				if (!matchesDimension(simulator, dimensionId)) {
					continue;
				}
				final Station station = containing(simulator, x, y, z);
				if (station != null) {
					return station;
				}
			}
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to look up the station at " + x + "," + y + "," + z, throwable);
		}
		return null;
	}

	private static Station containing(Data data, int x, int y, int z) {
		try {
			for (Station station : data.stations) {
				if (x >= station.getMinX() && x <= station.getMaxX()
						&& y >= station.getMinY() && y <= station.getMaxY()
						&& z >= station.getMinZ() && z <= station.getMaxZ()) {
					return station;
				}
			}
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to read the station areas", throwable);
		}
		return null;
	}

	/**
	 * A simulator records the dimension it belongs to, but the field layout differs between MTR builds,
	 * so both the field and the fallback of scanning every simulator are used.
	 */
	private static boolean matchesDimension(Simulator simulator, String dimensionId) {
		try {
			final String dimension = simulator.dimension;
			// MTR stores either "minecraft:overworld" or the bare path, depending on the version.
			return dimension != null && (dimension.equals(dimensionId) || dimensionId.endsWith("/" + dimension));
		} catch (Throwable throwable) {
			return true;
		}
	}

	private static Station byId(Data data, long stationId) {
		try {
			final Station station = data.stationIdMap.get(stationId);
			if (station != null) {
				return station;
			}
			for (Station candidate : data.stations) {
				if (candidate.getId() == stationId) {
					return candidate;
				}
			}
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to read the station map", throwable);
		}
		return null;
	}

	private static synchronized boolean initialise() {
		if (initialised) {
			return MAIN_FIELD != null && SIMULATORS_FIELD != null;
		}
		initialised = true;
		try {
			MAIN_FIELD = Init.class.getDeclaredField("main");
			MAIN_FIELD.setAccessible(true);
			SIMULATORS_FIELD = org.mtr.core.Main.class.getDeclaredField("simulators");
			SIMULATORS_FIELD.setAccessible(true);
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error(
					"MTR Outerchange: this MTR version does not expose its data registry, station wide configuration is unavailable",
					throwable);
			MAIN_FIELD = null;
			SIMULATORS_FIELD = null;
		}
		return MAIN_FIELD != null && SIMULATORS_FIELD != null;
	}
}
