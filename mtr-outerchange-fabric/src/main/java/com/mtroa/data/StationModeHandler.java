package com.mtroa.data;

import com.mtroa.MtrOuterchange;
import net.minecraft.server.network.ServerPlayerEntity;
import org.mtr.core.data.Station;

/**
 * Server side handling of the station wide outerchange decision, shared by every version of the mod.
 * <p>
 * Keeping this out of the networking classes means the packets, which differ only in how they are
 * delivered, do not have to duplicate the same rules.
 */
public final class StationModeHandler {

	private StationModeHandler() {
	}

	/**
	 * Applies a station wide decision and confirms it back to the dashboard button.
	 * <p>
	 * Only the station's own decision is stored. The barriers themselves are never rewritten, which is
	 * what makes switching back to "custom" restore the previous per barrier configuration, and it is
	 * why no barrier scan is needed here: whenever a player later passes a barrier MTR tells us which
	 * station that barrier belongs to and the decision is applied there.
	 * <p>
	 * No chat message is sent - the button label is the confirmation.
	 */
	public static void apply(ServerPlayerEntity player, Station station, StationMode mode) {
		if (station == null) {
			MtrOuterchange.LOGGER.warn("MTR Outerchange: ignored a station decision, the station could not be resolved");
			return;
		}
		try {
			final String dimensionId = com.mtroa.network.Networking.dimensionId(player.getWorld());
			OuterchangeData.setStationMode(OuterchangeData.stationKey(dimensionId, station.getId()), mode);
			MtrOuterchange.LOGGER.info("MTR Outerchange: station {} set to {}", station.getName(), mode);
			// The reply updates the button label, so the player sees the state they just chose.
			BRIDGE.sendStationMode(player, station.getId(), mode);
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to apply the station decision", throwable);
		}
	}

	/**
	 * Sends the player the decision currently stored for a station, so the dashboard label is correct
	 * as soon as a station is selected.
	 */
	public static void queryAndSend(ServerPlayerEntity player, long stationId) {
		final String stationKey = OuterchangeData.stationKey(
				com.mtroa.network.Networking.dimensionId(player.getWorld()), stationId);
		final StationConfig stationConfig = OuterchangeData.getStation(stationKey);
		final StationMode mode = stationConfig == null ? StationMode.CUSTOM : stationConfig.getMode();
		BRIDGE.sendStationMode(player, stationId, mode);
	}

	/** Indirect call into the loader specific networking, installed once at startup. */
	public static void setBridge(NetworkingBridge bridge) {
		BRIDGE = bridge;
	}

	private static NetworkingBridge BRIDGE;

	public interface NetworkingBridge {
		void sendStationMode(ServerPlayerEntity player, long stationId, StationMode mode);
	}
}
