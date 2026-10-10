package com.mtroa.network;

import com.mtroa.MtrOuterchange;
import com.mtroa.data.BarrierConfig;
import com.mtroa.data.BarrierScan;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.StationLookup;
import com.mtroa.data.StationMode;
import com.mtroa.data.StationModeHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.data.Station;

import java.util.List;
import java.util.function.BiConsumer;

public final class Networking {

	public static final Identifier PACKET_OPEN_SCREEN = new Identifier(MtrOuterchange.MOD_ID, "open_screen");
	public static final Identifier PACKET_UPDATE_CONFIG = new Identifier(MtrOuterchange.MOD_ID, "update_config");
	public static final Identifier PACKET_STATION_MODE = new Identifier(MtrOuterchange.MOD_ID, "station_mode");
	public static final Identifier PACKET_QUERY_STATION_MODE = new Identifier(MtrOuterchange.MOD_ID, "query_station_mode");
	public static final Identifier PACKET_STATION_MODE_STATE = new Identifier(MtrOuterchange.MOD_ID, "station_mode_state");
	public static final Identifier PACKET_SYNC_BARRIER = new Identifier(MtrOuterchange.MOD_ID, "sync_barrier");

	private static BiConsumer<Long, StationMode> stationModeReceiver;

	private Networking() {
	}

	public static void setStationModeReceiver(BiConsumer<Long, StationMode> receiver) {
		stationModeReceiver = receiver;
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PACKET_UPDATE_CONFIG, (server, player, handler, buf, responseSender) -> {
			final long posLong = buf.readLong();
			final boolean outerchangeAllowed = buf.readBoolean();
			final int minutes = buf.readInt();
			server.execute(() -> {
				final BlockPos blockPos = BlockPos.fromLong(posLong);
				// Resolve the station first so that this manual edit can drop the station back to
				// "custom" instead of being silently overridden by the station wide decision.
				final ServerWorld level = (ServerWorld) player.getWorld();
				final String dimensionId = dimensionId(level);
				final Station station = StationLookup.stationAt(level, blockPos.getX(), blockPos.getY(), blockPos.getZ());
				OuterchangeData.setBarrier(dimensionId,
						station == null ? null : OuterchangeData.stationKey(dimensionId, station.getId()),
						blockPos.getX(), blockPos.getY(), blockPos.getZ(),
						outerchangeAllowed, minutes);
				player.sendMessage(Text.translatable("message.mtr_outerchange.saved",
						String.valueOf(BarrierConfig.clampMinutes(minutes))), false);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_SYNC_BARRIER, (server, player, handler, buf, responseSender) -> {
			final long posLong = buf.readLong();
			final boolean allowed = buf.readBoolean();
			final int minutes = buf.readInt();
			server.execute(() -> syncToStation(player, BlockPos.fromLong(posLong), allowed, minutes));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_STATION_MODE, (server, player, handler, buf, responseSender) -> {
			final long stationId = buf.readLong();
			final int modeOrdinal = buf.readInt();
			server.execute(() -> StationModeHandler.apply(player,
					StationLookup.find((ServerWorld) player.getWorld(), stationId), StationMode.byOrdinal(modeOrdinal)));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_QUERY_STATION_MODE, (server, player, handler, buf, responseSender) -> {
			final long stationId = buf.readLong();
			server.execute(() -> StationModeHandler.queryAndSend(player, stationId));
		});

		StationModeHandler.setBridge(Networking::sendStationMode);
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
	 * Copies the configuration of one barrier onto every other exit barrier of the same station.
	 * <p>
	 * The station is resolved from the barrier itself, and writing the barriers binds them to that
	 * station, which is exactly what a manual edit does: a station that was forcing "yes" or "no"
	 * drops back to "custom" so the copied values take effect.
	 */
	public static void sendSync(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(blockPos.asLong());
		buf.writeBoolean(outerchangeAllowed);
		buf.writeInt(minutes);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_SYNC_BARRIER, buf);
	}

	private static void syncToStation(ServerPlayerEntity player, BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		final ServerWorld level = (ServerWorld) player.getWorld();
		final String dimensionId = dimensionId(level);
		final Station station = StationLookup.stationAt(level, blockPos.getX(), blockPos.getY(), blockPos.getZ());
		if (station == null) {
			player.sendMessage(Text.translatable("message.mtr_outerchange.sync_no_station"), false);
			return;
		}
		final String stationKey = OuterchangeData.stationKey(dimensionId, station.getId());
		final List<BlockPos> barriers = BarrierScan.collect(level, station);
		if (!barriers.contains(blockPos)) {
			// The barrier being edited is always part of the result, even if the block scan missed it.
			barriers.add(blockPos);
		}
		// Both halves of the configuration are copied: whether an outerchange is allowed and how long
		// the player may stay outside the paid area, so every barrier ends up with exactly what this
		// screen is showing.
		final int appliedMinutes = BarrierConfig.clampMinutes(minutes);
		for (BlockPos target : barriers) {
			OuterchangeData.setBarrier(dimensionId, stationKey, target.getX(), target.getY(), target.getZ(),
					outerchangeAllowed, appliedMinutes);
		}
		player.sendMessage(Text.translatable("message.mtr_outerchange.synced",
				String.valueOf(barriers.size()), String.valueOf(appliedMinutes)), false);
	}

	// ------------------------------------------------------------------ station wide

	public static void requestStationMode(long stationId) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(stationId);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_QUERY_STATION_MODE, buf);
	}

	public static void sendStationMode(long stationId, StationMode mode) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(stationId);
		buf.writeInt(mode.ordinal());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_STATION_MODE, buf);
	}

	public static void acceptStationMode(long stationId, StationMode mode) {
		if (stationModeReceiver != null) {
			stationModeReceiver.accept(stationId, mode);
		}
	}

	private static void sendStationMode(ServerPlayerEntity player, long stationId, StationMode mode) {
		if (!ServerPlayNetworking.canSend(player, PACKET_STATION_MODE_STATE)) {
			return;
		}
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(stationId);
		buf.writeInt(mode.ordinal());
		ServerPlayNetworking.send(player, PACKET_STATION_MODE_STATE, buf);
	}

	/**
	 * Must produce exactly the same string as {@code org.mtr.mod.Init#getWorldId}.
	 */
	public static String dimensionId(World world) {
		final Identifier identifier = world.getRegistryKey().getValue();
		return identifier.getNamespace() + "/" + identifier.getPath();
	}
}
