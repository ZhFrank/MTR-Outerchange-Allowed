package com.mtroa.network;

import com.mtroa.MtrOuterchange;
import com.mtroa.data.BarrierConfig;
import com.mtroa.data.BarrierScan;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.StationLookup;
import com.mtroa.data.StationMode;
import com.mtroa.data.StationModeHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.mtr.core.data.Station;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Forge networking for Minecraft 1.20.4. Forge replaced the {@code SimpleChannel} registry with the
 * {@link ChannelBuilder} API and hands handlers a {@link CustomPayloadEvent.Context} instead of a
 * {@code NetworkEvent} supplier.
 * <p>
 * The configuration screen is opened through {@link #screenOpener}, which is only installed on the
 * client, so this class never refers to client only classes.
 */
public final class Networking {

	private static final int PROTOCOL_VERSION = 1;

	public static final SimpleChannel CHANNEL = ChannelBuilder
			.named(new ResourceLocation(MtrOuterchange.MOD_ID, "main"))
			.networkProtocolVersion(PROTOCOL_VERSION)
			.clientAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
			.serverAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
			.simpleChannel();

	private static BiConsumer<BlockPos, BarrierConfig> screenOpener;

	private Networking() {
	}

	public static void setScreenOpener(BiConsumer<BlockPos, BarrierConfig> opener) {
		screenOpener = opener;
	}

	public static void register() {
		CHANNEL.messageBuilder(OpenScreenMessage.class, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(OpenScreenMessage::encode)
				.decoder(OpenScreenMessage::decode)
				.consumerMainThread(OpenScreenMessage::handle)
				.add();
		CHANNEL.messageBuilder(UpdateConfigMessage.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(UpdateConfigMessage::encode)
				.decoder(UpdateConfigMessage::decode)
				.consumerMainThread(UpdateConfigMessage::handle)
				.add();
		CHANNEL.messageBuilder(StationModeMessage.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(StationModeMessage::encode)
				.decoder(StationModeMessage::decode)
				.consumerMainThread(StationModeMessage::handle)
				.add();
		CHANNEL.messageBuilder(QueryStationModeMessage.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(QueryStationModeMessage::encode)
				.decoder(QueryStationModeMessage::decode)
				.consumerMainThread(QueryStationModeMessage::handle)
				.add();
		CHANNEL.messageBuilder(SyncBarrierMessage.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(SyncBarrierMessage::encode)
				.decoder(SyncBarrierMessage::decode)
				.consumerMainThread(SyncBarrierMessage::handle)
				.add();
		CHANNEL.messageBuilder(StationModeStateMessage.class, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(StationModeStateMessage::encode)
				.decoder(StationModeStateMessage::decode)
				.consumerMainThread(StationModeStateMessage::handle)
				.add();

		StationModeHandler.setBridge(Networking::sendStationMode);
	}

	/**
	 * Must produce exactly the same string as {@code org.mtr.mod.Init#getWorldId}.
	 */
	public static String dimensionId(Level level) {
		final ResourceLocation identifier = level.dimension().location();
		return identifier.getNamespace() + "/" + identifier.getPath();
	}

	public static void sendOpenScreen(ServerPlayer player, BlockPos blockPos, BarrierConfig barrierConfig) {
		CHANNEL.send(new OpenScreenMessage(blockPos.asLong(), barrierConfig.outerchangeAllowed, barrierConfig.maxMinutes),
				PacketDistributor.PLAYER.with(player));
	}

	public static void sendUpdate(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		CHANNEL.send(new UpdateConfigMessage(blockPos.asLong(), outerchangeAllowed, minutes),
				PacketDistributor.SERVER.noArg());
	}

	/**
	 * Copies the configuration of one barrier onto every other exit barrier of the same station.
	 * <p>
	 * The station is resolved from the barrier itself, and writing the barriers binds them to that
	 * station, which is exactly what a manual edit does: a station that was forcing "yes" or "no"
	 * drops back to "custom" so the copied values take effect.
	 */
	public static void sendSync(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		CHANNEL.send(new SyncBarrierMessage(blockPos.asLong(), outerchangeAllowed, minutes),
				PacketDistributor.SERVER.noArg());
	}

	private static void syncToStation(ServerPlayer player, BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		final ServerLevel level = player.serverLevel();
		final String dimensionId = dimensionId(level);
		final Station station = StationLookup.stationAt(level, blockPos.getX(), blockPos.getY(), blockPos.getZ());
		if (station == null) {
			player.sendSystemMessage(Component.translatable("message.mtr_outerchange.sync_no_station"));
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
		player.sendSystemMessage(Component.translatable("message.mtr_outerchange.synced",
				String.valueOf(barriers.size()), String.valueOf(appliedMinutes)));
	}

	// ------------------------------------------------------------------ station wide

	private static BiConsumer<Long, StationMode> stationModeReceiver;

	public static void setStationModeReceiver(BiConsumer<Long, StationMode> receiver) {
		stationModeReceiver = receiver;
	}

	public static void requestStationMode(long stationId) {
		CHANNEL.send(new QueryStationModeMessage(stationId), PacketDistributor.SERVER.noArg());
	}

	public static void sendStationMode(long stationId, StationMode mode) {
		CHANNEL.send(new StationModeMessage(stationId, mode.ordinal()), PacketDistributor.SERVER.noArg());
	}

	private static void sendStationMode(ServerPlayer player, long stationId, StationMode mode) {
		CHANNEL.send(new StationModeStateMessage(stationId, mode.ordinal()),
				PacketDistributor.PLAYER.with(player));
	}

	// ------------------------------------------------------------------ messages

	public static final class OpenScreenMessage {

		private final long posLong;
		private final boolean outerchangeAllowed;
		private final int minutes;

		OpenScreenMessage(long posLong, boolean outerchangeAllowed, int minutes) {
			this.posLong = posLong;
			this.outerchangeAllowed = outerchangeAllowed;
			this.minutes = minutes;
		}

		static void encode(OpenScreenMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.posLong);
			buf.writeBoolean(message.outerchangeAllowed);
			buf.writeInt(message.minutes);
		}

		static OpenScreenMessage decode(FriendlyByteBuf buf) {
			return new OpenScreenMessage(buf.readLong(), buf.readBoolean(), buf.readInt());
		}

		static void handle(OpenScreenMessage message, CustomPayloadEvent.Context context) {
			context.enqueueWork(() -> {
				if (screenOpener != null) {
					screenOpener.accept(BlockPos.of(message.posLong), new BarrierConfig(message.outerchangeAllowed, message.minutes));
				}
			});
			context.setPacketHandled(true);
		}
	}

	public static final class UpdateConfigMessage {

		private final long posLong;
		private final boolean outerchangeAllowed;
		private final int minutes;

		UpdateConfigMessage(long posLong, boolean outerchangeAllowed, int minutes) {
			this.posLong = posLong;
			this.outerchangeAllowed = outerchangeAllowed;
			this.minutes = minutes;
		}

		static void encode(UpdateConfigMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.posLong);
			buf.writeBoolean(message.outerchangeAllowed);
			buf.writeInt(message.minutes);
		}

		static UpdateConfigMessage decode(FriendlyByteBuf buf) {
			return new UpdateConfigMessage(buf.readLong(), buf.readBoolean(), buf.readInt());
		}

		static void handle(UpdateConfigMessage message, CustomPayloadEvent.Context context) {
			final ServerPlayer player = context.getSender();
			context.enqueueWork(() -> {
				if (player == null) {
					return;
				}
				final BlockPos blockPos = BlockPos.of(message.posLong);
				// Resolve the station first so that this manual edit can drop the station back to
				// "custom" instead of being silently overridden by the station wide decision.
				final ServerLevel level = (ServerLevel) player.level();
				final String dimensionId = dimensionId(level);
				final Station station = StationLookup.stationAt(level, blockPos.getX(), blockPos.getY(), blockPos.getZ());
				OuterchangeData.setBarrier(dimensionId,
						station == null ? null : OuterchangeData.stationKey(dimensionId, station.getId()),
						blockPos.getX(), blockPos.getY(), blockPos.getZ(),
						message.outerchangeAllowed, message.minutes);
				player.sendSystemMessage(Component.translatable("message.mtr_outerchange.saved",
						String.valueOf(BarrierConfig.clampMinutes(message.minutes))));
			});
			context.setPacketHandled(true);
		}
	}

	/** Client to server: copies one barrier's configuration onto every barrier of its station. */
	public static final class SyncBarrierMessage {

		private final long posLong;
		private final boolean outerchangeAllowed;
		private final int minutes;

		SyncBarrierMessage(long posLong, boolean outerchangeAllowed, int minutes) {
			this.posLong = posLong;
			this.outerchangeAllowed = outerchangeAllowed;
			this.minutes = minutes;
		}

		static void encode(SyncBarrierMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.posLong);
			buf.writeBoolean(message.outerchangeAllowed);
			buf.writeInt(message.minutes);
		}

		static SyncBarrierMessage decode(FriendlyByteBuf buf) {
			return new SyncBarrierMessage(buf.readLong(), buf.readBoolean(), buf.readInt());
		}

		static void handle(SyncBarrierMessage message, CustomPayloadEvent.Context context) {
			final ServerPlayer player = context.getSender();
			context.enqueueWork(() -> {
				if (player != null) {
					syncToStation(player, BlockPos.of(message.posLong), message.outerchangeAllowed, message.minutes);
				}
			});
			context.setPacketHandled(true);
		}
	}

	// ------------------------------------------------------------------ station messages

	/** Client to server: sets the station wide decision. */
	public static final class StationModeMessage {

		private final long stationId;
		private final int modeOrdinal;

		StationModeMessage(long stationId, int modeOrdinal) {
			this.stationId = stationId;
			this.modeOrdinal = modeOrdinal;
		}

		static void encode(StationModeMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.stationId);
			buf.writeInt(message.modeOrdinal);
		}

		static StationModeMessage decode(FriendlyByteBuf buf) {
			return new StationModeMessage(buf.readLong(), buf.readInt());
		}

		static void handle(StationModeMessage message, CustomPayloadEvent.Context context) {
			final ServerPlayer player = context.getSender();
			context.enqueueWork(() -> {
				if (player == null) {
					return;
				}
				StationModeHandler.apply(player, StationLookup.find(player.serverLevel(), message.stationId),
						StationMode.byOrdinal(message.modeOrdinal));
			});
			context.setPacketHandled(true);
		}
	}

	/** Client to server: asks for the decision currently stored for a station. */
	public static final class QueryStationModeMessage {

		private final long stationId;

		QueryStationModeMessage(long stationId) {
			this.stationId = stationId;
		}

		static void encode(QueryStationModeMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.stationId);
		}

		static QueryStationModeMessage decode(FriendlyByteBuf buf) {
			return new QueryStationModeMessage(buf.readLong());
		}

		static void handle(QueryStationModeMessage message, CustomPayloadEvent.Context context) {
			final ServerPlayer player = context.getSender();
			context.enqueueWork(() -> {
				if (player != null) {
					StationModeHandler.queryAndSend(player, message.stationId);
				}
			});
			context.setPacketHandled(true);
		}
	}

	/** Server to client: the decision currently stored for a station. */
	public static final class StationModeStateMessage {

		private final long stationId;
		private final int modeOrdinal;

		StationModeStateMessage(long stationId, int modeOrdinal) {
			this.stationId = stationId;
			this.modeOrdinal = modeOrdinal;
		}

		static void encode(StationModeStateMessage message, FriendlyByteBuf buf) {
			buf.writeLong(message.stationId);
			buf.writeInt(message.modeOrdinal);
		}

		static StationModeStateMessage decode(FriendlyByteBuf buf) {
			return new StationModeStateMessage(buf.readLong(), buf.readInt());
		}

		static void handle(StationModeStateMessage message, CustomPayloadEvent.Context context) {
			context.enqueueWork(() -> {
				if (stationModeReceiver != null) {
					stationModeReceiver.accept(message.stationId, StationMode.byOrdinal(message.modeOrdinal));
				}
			});
			context.setPacketHandled(true);
		}
	}
}
