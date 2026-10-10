package com.mtroa.client;

import com.mtroa.network.Networking;
import net.minecraft.client.Minecraft;

/**
 * Client only setup. It is reached through {@code DistExecutor}, so it is never loaded on a
 * dedicated server and may safely touch client classes.
 */
public final class ClientSetup {

	private ClientSetup() {
	}

	public static void register() {
		Networking.setScreenOpener((blockPos, barrierConfig) ->
				Minecraft.getInstance().setScreen(new OuterchangeScreen(blockPos, barrierConfig.outerchangeAllowed, barrierConfig.maxMinutes)));
		Networking.setStationModeReceiver(StationModeButton::acceptMode);
	}
}
