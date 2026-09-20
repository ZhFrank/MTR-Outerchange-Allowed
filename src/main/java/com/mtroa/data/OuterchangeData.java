package com.mtroa.data;

import com.mtroa.MtrOuterchange;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mtroa.compat.Compat;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every piece of data this mod needs to persist:
 * the configuration of every edited ticket barrier and every not yet settled outerchange.
 */
public final class OuterchangeData {

	private static final Map<String, BarrierConfig> BARRIERS = new HashMap<>();
	private static final Map<String, PendingTransfer> PENDING = new HashMap<>();

	private static Path savePath;
	private static MinecraftServer currentServer;
	private static int tickCounter;
	private static boolean dirty;

	private OuterchangeData() {
	}

	/** The currently running server, used to look up players and the scoreboard. */
	public static MinecraftServer getServer() {
		return currentServer;
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter >= 20) {
				tickCounter = 0;
				tick(server);
			}
		});
	}

	// ------------------------------------------------------------------ barriers

	public static BarrierConfig getBarrier(String dimensionId, int x, int y, int z) {
		return BARRIERS.get(barrierKey(dimensionId, x, y, z));
	}

	public static BarrierConfig getOrCreateBarrier(String dimensionId, int x, int y, int z) {
		return BARRIERS.computeIfAbsent(barrierKey(dimensionId, x, y, z), key -> {
			dirty = true;
			return new BarrierConfig();
		});
	}

	public static void removeBarrier(String dimensionId, int x, int y, int z) {
		if (BARRIERS.remove(barrierKey(dimensionId, x, y, z)) != null) {
			dirty = true;
		}
	}

	private static String barrierKey(String dimensionId, int x, int y, int z) {
		return dimensionId + "@" + x + "," + y + "," + z;
	}

	// ------------------------------------------------------------------ pending

	public static PendingTransfer getPending(String playerName) {
		return PENDING.get(playerName);
	}

	public static void addPending(PendingTransfer pendingTransfer) {
		PENDING.put(pendingTransfer.playerName, pendingTransfer);
		dirty = true;
	}

	public static void removePending(String playerName) {
		if (PENDING.remove(playerName) != null) {
			dirty = true;
		}
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		final long now = System.currentTimeMillis();
		final List<PendingTransfer> expired = new ArrayList<>();
		for (PendingTransfer pendingTransfer : PENDING.values()) {
			if (now >= pendingTransfer.expiryMillis) {
				expired.add(pendingTransfer);
			}
		}
		for (PendingTransfer pendingTransfer : expired) {
			removePending(pendingTransfer.playerName);
			settle(server, pendingTransfer);
		}
	}

	/**
	 * Charges the fare that would have been paid at the exit barrier and clears the entry record.
	 */
	public static void settle(MinecraftServer server, PendingTransfer pendingTransfer) {
		final long fare = pendingTransfer.getFare();
		final Scoreboard scoreboard = server.getScoreboard();
		Scoreboards.set(scoreboard, PendingTransfer.ENTRY_ZONE_1, "Entry Zone 1", pendingTransfer.playerName, 0);
		Scoreboards.set(scoreboard, PendingTransfer.ENTRY_ZONE_2, "Entry Zone 2", pendingTransfer.playerName, 0);
		Scoreboards.set(scoreboard, PendingTransfer.ENTRY_ZONE_3, "Entry Zone 3", pendingTransfer.playerName, 0);
		Scoreboards.increment(scoreboard, PendingTransfer.BALANCE, "Balance", pendingTransfer.playerName, (int) -fare);
		final int balance = Scoreboards.get(scoreboard, PendingTransfer.BALANCE, "Balance", pendingTransfer.playerName);
		final ServerPlayerEntity player = server.getPlayerManager().getPlayer(pendingTransfer.playerName);
		if (player != null) {
			player.sendMessage(Text.translatable("message.mtr_outerchange.expired",
					String.valueOf(fare),
					pendingTransfer.stationName,
					String.valueOf(balance)
			), false);
		}
	}

	// ------------------------------------------------------------------ saving

	public static void markDirty() {
		dirty = true;
	}

	public static void load(MinecraftServer server) {
		BARRIERS.clear();
		PENDING.clear();
		currentServer = server;
		savePath = server.getSavePath(WorldSavePath.ROOT).resolve("mtr_outerchange.dat");
		dirty = false;
		if (!Files.exists(savePath)) {
			return;
		}
		try (InputStream inputStream = Files.newInputStream(savePath)) {
			read(Compat.readCompressed(inputStream));
		} catch (Exception e) {
			MtrOuterchange.LOGGER.error("Failed to read MTR Outerchange data", e);
		}
		dirty = false;
	}

	public static void save() {
		if (savePath == null) {
			return;
		}
		try {
			final NbtCompound nbtCompound = write();
			final Path parent = savePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (OutputStream outputStream = Files.newOutputStream(savePath)) {
				Compat.writeCompressed(nbtCompound, outputStream);
			}
			dirty = false;
		} catch (IOException e) {
			MtrOuterchange.LOGGER.error("Failed to save MTR Outerchange data", e);
		}
	}

	public static boolean isDirty() {
		return dirty;
	}

	private static NbtCompound write() {
		final NbtCompound root = new NbtCompound();
		final NbtList barriers = new NbtList();
		for (Map.Entry<String, BarrierConfig> entry : BARRIERS.entrySet()) {
			final NbtCompound barrierTag = new NbtCompound();
			barrierTag.putString("Key", entry.getKey());
			barrierTag.putBoolean("Outerchange", entry.getValue().outerchangeAllowed);
			barrierTag.putInt("Minutes", entry.getValue().maxMinutes);
			barriers.add(barrierTag);
		}
		root.put("Barriers", barriers);

		final NbtList pending = new NbtList();
		for (PendingTransfer pendingTransfer : PENDING.values()) {
			final NbtCompound pendingTag = new NbtCompound();
			pendingTag.putString("Player", pendingTransfer.playerName);
			pendingTag.putLong("Expiry", pendingTransfer.expiryMillis);
			pendingTag.putLong("EntryZone1", pendingTransfer.entryZone1);
			pendingTag.putLong("EntryZone2", pendingTransfer.entryZone2);
			pendingTag.putLong("EntryZone3", pendingTransfer.entryZone3);
			pendingTag.putLong("ExitZone1", pendingTransfer.exitZone1);
			pendingTag.putLong("ExitZone2", pendingTransfer.exitZone2);
			pendingTag.putLong("ExitZone3", pendingTransfer.exitZone3);
			pendingTag.putString("Station", pendingTransfer.stationName);
			pendingTag.putBoolean("Concessionary", pendingTransfer.concessionary);
			pending.add(pendingTag);
		}
		root.put("Pending", pending);
		return root;
	}

	private static void read(NbtCompound root) {
		final NbtList barriers = root.getList("Barriers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < barriers.size(); i++) {
			final NbtCompound barrierTag = barriers.getCompound(i);
			final BarrierConfig barrierConfig = new BarrierConfig(
					barrierTag.getBoolean("Outerchange"),
					barrierTag.getInt("Minutes")
			);
			BARRIERS.put(barrierTag.getString("Key"), barrierConfig);
		}
		final NbtList pending = root.getList("Pending", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < pending.size(); i++) {
			final NbtCompound pendingTag = pending.getCompound(i);
			final PendingTransfer pendingTransfer = new PendingTransfer(
					pendingTag.getString("Player"),
					pendingTag.getLong("Expiry"),
					pendingTag.getLong("EntryZone1"),
					pendingTag.getLong("EntryZone2"),
					pendingTag.getLong("EntryZone3"),
					pendingTag.getLong("ExitZone1"),
					pendingTag.getLong("ExitZone2"),
					pendingTag.getLong("ExitZone3"),
					pendingTag.getString("Station"),
					pendingTag.getBoolean("Concessionary")
			);
			PENDING.put(pendingTransfer.playerName, pendingTransfer);
		}
	}
}
