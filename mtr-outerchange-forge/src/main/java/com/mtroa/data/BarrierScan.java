package com.mtroa.data;

import com.mtroa.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.mtr.core.data.Station;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds every exit barrier inside one station.
 * <p>
 * MTR does not keep a list of the barriers that belong to a station, and the only thing a station
 * records about itself is the box the player drew for it, so the barriers are found by walking that
 * box. This is only ever done when the player asks for it (the "sync to the other barriers" button),
 * never while a barrier is being used, so the cost is paid once by the player who pressed it and not
 * by everyone passing through the station.
 * <p>
 * Two details of that box matter. A station area is stored with the smallest and the largest y a long
 * can hold, so vertically it covers the whole world: the walk is cut down to the height the world
 * really has, otherwise it would start far below the ground and burn its whole budget before ever
 * reaching a barrier. And only chunks that are already loaded are read, because asking for a block
 * anywhere else would drag the chunk in and generate it. No more than {@link #MAX_POSITIONS}
 * positions are inspected, so a station drawn absurdly large can never freeze the server: the scan
 * simply stops and returns what it found up to that point.
 */
public final class BarrierScan {

	private static final int MAX_POSITIONS = 4000000;

	private BarrierScan() {
	}

	/**
	 * @return the positions of every exit barrier inside the station's area, in no particular order
	 */
	public static List<BlockPos> collect(ServerLevel level, Station station) {
		final List<BlockPos> barriers = new ArrayList<>();
		if (level == null || station == null) {
			return barriers;
		}
		// MTR keeps the area as longs, a block position does not go that far.
		final int minX = (int) station.getMinX();
		final int maxX = (int) station.getMaxX();
		final int minZ = (int) station.getMinZ();
		final int maxZ = (int) station.getMaxZ();
		// A station is drawn on the horizontal plane only: its stored height spans every y a long can
		// hold, so the walk has to be limited to the height this world actually has.
		final int worldBottom = Compat.getBottomY(level);
		final int worldTop = Compat.getTopY(level) - 1;
		final long lowest = Math.max((long) worldBottom, Math.min((long) worldTop, station.getMinY()));
		final int minY = (int) lowest;
		final int maxY = (int) Math.max(lowest, Math.min((long) worldTop, station.getMaxY()));
		final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int inspected = 0;
		for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
			for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
				if (!Compat.isChunkLoaded(level, chunkX, chunkZ)) {
					continue;
				}
				final int startX = Math.max(minX, chunkX << 4);
				final int endX = Math.min(maxX, (chunkX << 4) + 15);
				final int startZ = Math.max(minZ, chunkZ << 4);
				final int endZ = Math.min(maxZ, (chunkZ << 4) + 15);
				for (int x = startX; x <= endX; x++) {
					for (int z = startZ; z <= endZ; z++) {
						for (int y = minY; y <= maxY; y++) {
							if (++inspected > MAX_POSITIONS) {
								return barriers;
							}
							final BlockState blockState = level.getBlockState(cursor.set(x, y, z));
							if (TicketBarriers.isExitBarrier(blockState.getBlock())) {
								barriers.add(new BlockPos(x, y, z));
							}
						}
					}
				}
			}
		}
		return barriers;
	}
}
