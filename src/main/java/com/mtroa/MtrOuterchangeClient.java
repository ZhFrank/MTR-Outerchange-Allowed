package com.mtroa;

import com.mtroa.client.OuterchangeScreen;
import com.mtroa.network.Networking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

public class MtrOuterchangeClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Networking.PACKET_OPEN_SCREEN, (client, handler, buf, responseSender) -> {
			long posLong = buf.readLong();
			boolean outerchangeAllowed = buf.readBoolean();
			int minutes = buf.readInt();
			client.execute(() -> client.setScreen(new OuterchangeScreen(BlockPos.fromLong(posLong), outerchangeAllowed, minutes)));
		});
	}
}
