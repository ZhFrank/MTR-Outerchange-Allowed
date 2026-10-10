package com.mtroa;

import com.mtroa.client.OuterchangeScreen;
import com.mtroa.network.Networking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

public class MtrOuterchangeClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Networking.OpenScreenPayload.ID, (payload, context) -> {
			final long posLong = payload.posLong();
			final boolean outerchangeAllowed = payload.outerchangeAllowed();
			final int minutes = payload.minutes();
			context.client().execute(() -> context.client().setScreen(
					new OuterchangeScreen(BlockPos.fromLong(posLong), outerchangeAllowed, minutes)));
		});
	}
}
