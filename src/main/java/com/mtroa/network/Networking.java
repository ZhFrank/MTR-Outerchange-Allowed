package com.mtroa.network;

import com.mtroa.MtrOuterchange;
import com.mtroa.data.BarrierConfig;
import com.mtroa.data.OuterchangeData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class Networking {

	public static final Identifier PACKET_OPEN_SCREEN = new Identifier(MtrOuterchange.MOD_ID, "open_screen");
	public static final Identifier PACKET_UPDATE_CONFIG = new Identifier(MtrOuterchange.MOD_ID, "update_config");

	private Networking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PACKET_UPDATE_CONFIG, (server, player, handler, buf, responseSender) -> {
			final long posLong = buf.readLong();
			final boolean outerchangeAllowed = buf.readBoolean();
			final int minutes = buf.readInt();
			server.execute(() -> {
				final BlockPos blockPos = BlockPos.fromLong(posLong);
				final String dimensionId = dimensionId(player.getWorld());
				final BarrierConfig barrierConfig = OuterchangeData.getOrCreateBarrier(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ());
				barrierConfig.outerchangeAllowed = outerchangeAllowed;
				barrierConfig.maxMinutes = BarrierConfig.clampMinutes(minutes);
				OuterchangeData.markDirty();
				player.sendMessage(Text.translatable("message.mtr_outerchange.saved", String.valueOf(barrierConfig.maxMinutes)), false);
			});
		});
	}

	public static void sendOpenScreen(ServerPlayerEntity player, BlockPos blockPos, BarrierConfig barrierConfig) {
		if (!ServerPlayNetworking.canSend(player, PACKET_OPEN_SCREEN)) {
			return;
		}
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(blockPos.asLong());
		buf.writeBoolean(barrierConfig.outerchangeAllowed);
		buf.writeInt(barrierConfig.maxMinutes);
		ServerPlayNetworking.send(player, PACKET_OPEN_SCREEN, buf);
	}

	public static void sendUpdate(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(blockPos.asLong());
		buf.writeBoolean(outerchangeAllowed);
		buf.writeInt(minutes);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_UPDATE_CONFIG, buf);
	}

	/**
	 * Must produce exactly the same string as {@code org.mtr.mod.Init#getWorldId}.
	 */
	public static String dimensionId(World world) {
		final Identifier identifier = world.getRegistryKey().getValue();
		return identifier.getNamespace() + "/" + identifier.getPath();
	}
}
