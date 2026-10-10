package com.mtroa.handler;

import com.mtroa.data.BarrierConfig;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.StationLookup;
import com.mtroa.data.TicketBarriers;
import com.mtroa.network.Networking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.data.Station;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles right clicking a ticket barrier with the MTR brush.
 */
public final class BrushClickHandler {

	private static final ResourceLocation BRUSH = new ResourceLocation("mtr", "brush");

	private BrushClickHandler() {
	}

	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		final Level level = event.getLevel();
		if (level.isClientSide()) {
			return;
		}
		final ItemStack stack = event.getItemStack();
		if (stack.isEmpty() || !BRUSH.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
			return;
		}
		final BlockPos blockPos = event.getPos();
		final Block block = level.getBlockState(blockPos).getBlock();

		// Entrance barriers are never configured: they simply check whether the exit barrier
		// recorded an outerchange for the player, so only exit barriers need a screen.
		// The check is by block type rather than by one hard coded id, so the barriers added by
		// the Russian Metro Addon, the Joban Client Mod and Tianjin Metro work the same way.
		if (TicketBarriers.isExitBarrier(block)) {
			final String dimensionId = Networking.dimensionId(level);
			final BarrierConfig barrierConfig = OuterchangeData.getOrCreateBarrier(
					dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			// Resolve the station this barrier sits in so the screen opens with the setting that
			// actually applies, including a station wide decision made from the editor.
			final Station station = level instanceof ServerLevel
					? StationLookup.stationAt((ServerLevel) level, blockPos.getX(), blockPos.getY(), blockPos.getZ())
					: null;
			if (station != null) {
				OuterchangeData.bindBarrierToStation(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
						OuterchangeData.stationKey(dimensionId, station.getId()));
			}
			Networking.sendOpenScreen((ServerPlayer) event.getEntity(), blockPos,
					new BarrierConfig(OuterchangeData.isOuterchangeAllowed(
							dimensionId, station == null ? 0 : station.getId(),
							blockPos.getX(), blockPos.getY(), blockPos.getZ()),
							barrierConfig.maxMinutes));
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}
}

