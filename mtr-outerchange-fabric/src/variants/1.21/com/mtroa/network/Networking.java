package com.mtroa.network;

import com.mtroa.MtrOuterchange;
import com.mtroa.data.BarrierConfig;
import com.mtroa.data.BarrierScan;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.StationLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.data.Station;

import java.util.List;

/**
 * Fabric networking for Minecraft 1.21.x. Raw {@code PacketByteBuf} channels were replaced by typed
 * {@link CustomPayload} records, so each message now carries its own id and codec.
 */
public final class Networking {

	private Networking() {
	}

	public static void registerServerReceivers() {
		PayloadTypeRegistry.playS2C().register(OpenScreenPayload.ID, OpenScreenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateConfigPayload.ID, UpdateConfigPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncConfigPayload.ID, SyncConfigPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateConfigPayload.ID, (payload, context) -> {
			final ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				final BlockPos blockPos = BlockPos.fromLong(payload.posLong());
				final String dimensionId = dimensionId(player.getWorld());
				final BarrierConfig barrierConfig = OuterchangeData.getOrCreateBarrier(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ());
				barrierConfig.outerchangeAllowed = payload.outerchangeAllowed();
				barrierConfig.maxMinutes = BarrierConfig.clampMinutes(payload.minutes());
				OuterchangeData.markDirty();
				player.sendMessage(Text.translatable("message.mtr_outerchange.saved", String.valueOf(barrierConfig.maxMinutes)), false);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (payload, context) -> {
			final ServerPlayerEntity player = context.player();
			context.server().execute(() -> syncToStation(player,
					BlockPos.fromLong(payload.posLong()), payload.outerchangeAllowed(), payload.minutes()));
		});
	}

	/**
	 * Copies the configuration of one barrier onto every other exit barrier of the same station.
	 * <p>
	 * The station is resolved from the barrier itself, and writing the barriers binds them to that
	 * station, which is exactly what a manual edit does: a station that was forcing "yes" or "no"
	 * drops back to "custom" so the copied values take effect.
	 */
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

	public static void sendOpenScreen(ServerPlayerEntity player, BlockPos blockPos, BarrierConfig barrierConfig) {
		ServerPlayNetworking.send(player, new OpenScreenPayload(blockPos.asLong(), barrierConfig.outerchangeAllowed, barrierConfig.maxMinutes));
	}

	public static void sendUpdate(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new UpdateConfigPayload(blockPos.asLong(), outerchangeAllowed, minutes));
	}

	public static void sendSync(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new SyncConfigPayload(blockPos.asLong(), outerchangeAllowed, minutes));
	}

	/**
	 * Must produce exactly the same string as MTR uses to identify a dimension.
	 */
	public static String dimensionId(World world) {
		final Identifier identifier = world.getRegistryKey().getValue();
		return identifier.getNamespace() + "/" + identifier.getPath();
	}

	// ------------------------------------------------------------------ payloads

	public record OpenScreenPayload(long posLong, boolean outerchangeAllowed, int minutes) implements CustomPayload {

		public static final CustomPayload.Id<OpenScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MtrOuterchange.MOD_ID, "open_screen"));

		public static final PacketCodec<RegistryByteBuf, OpenScreenPayload> CODEC = new PacketCodec<>() {
			@Override
			public OpenScreenPayload decode(RegistryByteBuf buf) {
				return new OpenScreenPayload(buf.readLong(), buf.readBoolean(), buf.readInt());
			}

			@Override
			public void encode(RegistryByteBuf buf, OpenScreenPayload payload) {
				buf.writeLong(payload.posLong());
				buf.writeBoolean(payload.outerchangeAllowed());
				buf.writeInt(payload.minutes());
			}
		};

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record SyncConfigPayload(long posLong, boolean outerchangeAllowed, int minutes) implements CustomPayload {

		public static final CustomPayload.Id<SyncConfigPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MtrOuterchange.MOD_ID, "sync_barrier"));

		public static final PacketCodec<RegistryByteBuf, SyncConfigPayload> CODEC = new PacketCodec<>() {
			@Override
			public SyncConfigPayload decode(RegistryByteBuf buf) {
				return new SyncConfigPayload(buf.readLong(), buf.readBoolean(), buf.readInt());
			}

			@Override
			public void encode(RegistryByteBuf buf, SyncConfigPayload payload) {
				buf.writeLong(payload.posLong());
				buf.writeBoolean(payload.outerchangeAllowed());
				buf.writeInt(payload.minutes());
			}
		};

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record UpdateConfigPayload(long posLong, boolean outerchangeAllowed, int minutes) implements CustomPayload {

		public static final CustomPayload.Id<UpdateConfigPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MtrOuterchange.MOD_ID, "update_config"));

		public static final PacketCodec<RegistryByteBuf, UpdateConfigPayload> CODEC = new PacketCodec<>() {
			@Override
			public UpdateConfigPayload decode(RegistryByteBuf buf) {
				return new UpdateConfigPayload(buf.readLong(), buf.readBoolean(), buf.readInt());
			}

			@Override
			public void encode(RegistryByteBuf buf, UpdateConfigPayload payload) {
				buf.writeLong(payload.posLong());
				buf.writeBoolean(payload.outerchangeAllowed());
				buf.writeInt(payload.minutes());
			}
		};

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
