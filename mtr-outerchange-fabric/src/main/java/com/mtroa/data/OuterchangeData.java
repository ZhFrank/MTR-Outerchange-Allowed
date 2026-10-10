package com.mtroa.data;

import com.mtroa.MtrOuterchange;
import com.mtroa.compat.Compat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
	/** Station wide decisions written from the dashboard, keyed by dimension and station id. */
	private static final Map<String, StationConfig> STATIONS = new HashMap<>();
	/** Which station each configured barrier belongs to, so a manual edit can find its station again. */
	private static final Map<String, String> BARRIER_STATION = new HashMap<>();

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
		ServerLifecycleEvents.SERVER_STARTED.register(OuterchangeData::load);
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

	// ------------------------------------------------------------------ stations

	public static StationConfig getStation(String stationKey) {
		return STATIONS.get(stationKey);
	}

	public static StationConfig getOrCreateStation(String stationKey) {
		return STATIONS.computeIfAbsent(stationKey, key -> {
			dirty = true;
			return new StationConfig();
		});
	}

	public static String stationKey(String dimensionId, long stationId) {
		return dimensionId + "#" + stationId;
	}

	/**
	 * Remembers which station a barrier belongs to. Called while the dashboard writes a station, and
	 * while a barrier is configured by hand, so that a later manual edit can always find the station
	 * whose decision has to be dropped.
	 */
	public static void bindBarrierToStation(String dimensionId, int x, int y, int z, String stationKey) {
		BARRIER_STATION.put(barrierKey(dimensionId, x, y, z), stationKey);
		dirty = true;
	}

	public static String stationOfBarrier(String dimensionId, int x, int y, int z) {
		return BARRIER_STATION.get(barrierKey(dimensionId, x, y, z));
	}

	/**
	 * Applies a station wide decision from the dashboard.
	 * <p>
	 * Choosing "yes" or "no" only records the decision and never rewrites the individual barriers, so
	 * switching back to "custom" restores whatever each barrier was set to before. Choosing "custom"
	 * simply drops the decision.
	 */
	public static void setStationMode(String stationKey, StationMode mode) {
		getOrCreateStation(stationKey).setMode(mode);
		dirty = true;
	}

	/**
	 * The decision that applies to a barrier whose station is known.
	 * <p>
	 * MTR hands the resolved {@code Station} to every barrier pass, so this is the normal path: when
	 * that station overrides its barriers the station decision wins, otherwise the barrier keeps using
	 * its own setting. Passing {@code 0} means "the station is unknown", which behaves exactly like the
	 * coordinate only variant below.
	 */
	public static boolean isOuterchangeAllowed(String dimensionId, long stationId, int x, int y, int z) {
		if (stationId != 0) {
			final StationConfig stationConfig = STATIONS.get(stationKey(dimensionId, stationId));
			if (stationConfig != null && stationConfig.isOverride()) {
				final Boolean forced = stationConfig.forcedValue();
				if (forced != null) {
					return forced;
				}
			}
		}
		return isOuterchangeAllowed(dimensionId, x, y, z);
	}

	/**
	 * The decision that actually applies to a barrier: the station wide one when the station overrides
	 * its barriers, otherwise the barrier's own setting.
	 */
	public static boolean isOuterchangeAllowed(String dimensionId, int x, int y, int z) {
		final String stationKey = BARRIER_STATION.get(barrierKey(dimensionId, x, y, z));
		if (stationKey != null) {
			final StationConfig stationConfig = STATIONS.get(stationKey);
			if (stationConfig != null && stationConfig.isOverride()) {
				final Boolean forced = stationConfig.forcedValue();
				if (forced != null) {
					return forced;
				}
			}
		}
		final BarrierConfig barrierConfig = BARRIERS.get(barrierKey(dimensionId, x, y, z));
		return barrierConfig != null && barrierConfig.outerchangeAllowed;
	}

	/**
	 * Stores a manual barrier edit, remembering which station the barrier belongs to first so that the
	 * edit can drop that station back to "custom".
	 */
	public static void setBarrier(String dimensionId, String stationKey, int x, int y, int z, boolean outerchangeAllowed, int minutes) {
		if (stationKey != null) {
			bindBarrierToStation(dimensionId, x, y, z, stationKey);
		}
		setBarrier(dimensionId, x, y, z, outerchangeAllowed, minutes);
	}

	/**
	 * Stores a manual barrier edit. When the station is currently forcing a decision, the edit also
	 * drops the station back to "custom", which is what the dashboard then shows.
	 */
	public static void setBarrier(String dimensionId, int x, int y, int z, boolean outerchangeAllowed, int minutes) {
		final String key = barrierKey(dimensionId, x, y, z);
		final BarrierConfig barrierConfig = BARRIERS.computeIfAbsent(key, k -> {
			dirty = true;
			return new BarrierConfig();
		});
		barrierConfig.outerchangeAllowed = outerchangeAllowed;
		barrierConfig.maxMinutes = BarrierConfig.clampMinutes(minutes);

		final String stationKey = BARRIER_STATION.get(key);
		if (stationKey != null) {
			final StationConfig stationConfig = STATIONS.get(stationKey);
			if (stationConfig != null && stationConfig.isOverride()) {
				stationConfig.setMode(StationMode.CUSTOM);
			}
		}
		dirty = true;
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
		STATIONS.clear();
		BARRIER_STATION.clear();
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
			final NbtCompound root = write();
			final Path parent = savePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (OutputStream outputStream = Files.newOutputStream(savePath)) {
				Compat.writeCompressed(root, outputStream);
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

		final NbtList stations = new NbtList();
		for (Map.Entry<String, StationConfig> entry : STATIONS.entrySet()) {
			final NbtCompound stationTag = new NbtCompound();
			stationTag.putString("Key", entry.getKey());
			stationTag.putInt("Mode", entry.getValue().getMode().ordinal());
			stations.add(stationTag);
		}
		root.put("Stations", stations);

		final NbtList bindings = new NbtList();
		for (Map.Entry<String, String> entry : BARRIER_STATION.entrySet()) {
			final NbtCompound bindingTag = new NbtCompound();
			bindingTag.putString("Key", entry.getKey());
			bindingTag.putString("Station", entry.getValue());
			bindings.add(bindingTag);
		}
		root.put("BarrierStations", bindings);

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
			BARRIERS.put(barrierTag.getString("Key"), new BarrierConfig(
					barrierTag.getBoolean("Outerchange"),
					barrierTag.getInt("Minutes")
			));
		}
		final NbtList stations = root.getList("Stations", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < stations.size(); i++) {
			final NbtCompound stationTag = stations.getCompound(i);
			final StationConfig stationConfig = new StationConfig();
			stationConfig.setMode(StationMode.byOrdinal(stationTag.getInt("Mode")));
			STATIONS.put(stationTag.getString("Key"), stationConfig);
		}
		// The bindings are read after the stations so a station is always present when a barrier points at it.
		final NbtList bindings = root.getList("BarrierStations", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bindings.size(); i++) {
			final NbtCompound bindingTag = bindings.getCompound(i);
			BARRIER_STATION.put(bindingTag.getString("Key"), bindingTag.getString("Station"));
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
