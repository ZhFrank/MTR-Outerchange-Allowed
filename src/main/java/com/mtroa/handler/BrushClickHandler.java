package com.mtroa.handler;

import com.mtroa.data.BarrierConfig;
import com.mtroa.data.OuterchangeData;
import com.mtroa.network.Networking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Handles right clicking a ticket barrier with the MTR brush.
 */
public final class BrushClickHandler {

	private static final Identifier BRUSH = new Identifier("mtr", "brush");
	private static final Identifier TICKET_BARRIER_EXIT = new Identifier("mtr", "ticket_barrier_exit_1");

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
		final Identifier blockId = Registries.BLOCK.getId(block);
		if (blockId == null) {
			return ActionResult.PASS;
		}

		// Entrance barriers are never configured: they simply check whether the exit barrier
		// recorded an outerchange for the player, so only exit barriers need a screen.
		if (TICKET_BARRIER_EXIT.equals(blockId)) {
			final String dimensionId = Networking.dimensionId(world);
			final BarrierConfig barrierConfig = OuterchangeData.getOrCreateBarrier(dimensionId, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			Networking.sendOpenScreen((ServerPlayerEntity) player, blockPos, barrierConfig);
			return ActionResult.SUCCESS;
		}

		return ActionResult.PASS;
	}
}
