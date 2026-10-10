package com.mtroa.handler;

import com.mtroa.data.BarrierConfig;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.StationLookup;
import com.mtroa.data.TicketBarriers;
import com.mtroa.network.Networking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.data.Station;

/**
 * Handles right clicking a ticket barrier with the MTR brush.
 */
public final class BrushClickHandler {

	private static final Identifier BRUSH = Identifier.of("mtr", "brush");

	private BrushClickHandler() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(BrushClickHandler::onUseBlock);
	}

	private static ActionResult onUseBlock(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		final ItemStack stack = player.getStackInHand(hand);
		if (stack.isEmpty() || !BRUSH.equals(Registries.ITEM.getId(stack.getItem()))) {
			return ActionResult.PASS;
		}
		final BlockPos blockPos = hitResult.getBlockPos();
		final BlockState blockState = world.getBlockState(blockPos);
		final Block block = blockState.getBlock();

		// Entrance barriers are never configured: they simply check whether the exit barrier
		// recorded an outerchange for the player, so only exit barriers need a screen.
		// The check is by block type rather than by one hard coded id, so the barriers added by
		// the Russian Metro Addon, the Joban Client Mod and Tianjin Metro work the same way.
		if (TicketBarriers.isExitBarrier(block)) {
			final String dimensionId = Networking.dimensionId(world);
			final BarrierConfig barrierConfig = OuterchangeData.getOrCreateBarrier(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			// Resolve the station this barrier sits in so the screen opens with the setting that
			// actually applies, including a station wide decision made from the editor.
			final Station station = world instanceof ServerWorld
					? StationLookup.stationAt((ServerWorld) world, blockPos.getX(), blockPos.getY(), blockPos.getZ())
					: null;
			if (station != null) {
				OuterchangeData.bindBarrierToStation(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
						OuterchangeData.stationKey(dimensionId, station.getId()));
			}
			Networking.sendOpenScreen((ServerPlayerEntity) player, blockPos,
					new BarrierConfig(OuterchangeData.isOuterchangeAllowed(
							dimensionId, station == null ? 0 : station.getId(),
							blockPos.getX(), blockPos.getY(), blockPos.getZ()),
							barrierConfig.maxMinutes));
			return ActionResult.SUCCESS;
		}

		return ActionResult.PASS;
	}
}
