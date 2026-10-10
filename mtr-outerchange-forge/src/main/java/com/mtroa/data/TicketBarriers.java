package com.mtroa.data;

import net.minecraft.world.level.block.Block;
import org.mtr.mod.block.BlockTicketBarrier;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Recognises every ticket barrier block in the game, not just MTR's own two.
 * <p>
 * MTR ships {@code ticket_barrier_entrance_1} and {@code ticket_barrier_exit_1}, but several popular
 * addons register their own barrier blocks that are still real ticket barriers: the Russian Metro
 * Addon has four (Moscow old and new, entrance and exit), and the Joban Client Mod has three (Thales,
 * bare metal, entrance and exit). All of them extend {@link BlockTicketBarrier} and call the same
 * {@code TicketSystem}, so they behave exactly like MTR's own barriers and must be configurable too.
 * <p>
 * Which side a barrier is on is read from {@code BlockTicketBarrier#isEntrance}. That field is private
 * and is redeclared by the addons that need it, so the lookup is cached and falls back gracefully: a
 * class whose layout cannot be read is simply not treated as a configurable barrier rather than
 * breaking the game.
 * <p>
 * The answer is cached per <b>block</b>, never per class: MTR registers the entrance and the exit as two
 * instances of one and the same class, so a cache keyed by class would answer for both of them with
 * whichever was looked at first and would happily open the configuration screen on an entrance barrier.
 */
public final class TicketBarriers {

	/** MTR's own exit barrier, kept as a fast path because it needs no reflection at all. */
	public static final net.minecraft.resources.ResourceLocation MTR_EXIT =
			new net.minecraft.resources.ResourceLocation("mtr", "ticket_barrier_exit_1");

	private static final Field IS_ENTRANCE = findIsEntranceField();
	private static final Map<Block, Boolean> ENTRANCE_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
	/** Blocks that could not be classified, so the (failed) reflection is never repeated every click. */
	private static final Set<Class<?>> UNRESOLVED = Collections.newSetFromMap(new IdentityHashMap<>());

	private TicketBarriers() {
	}

	private static Field findIsEntranceField() {
		try {
			final Field field = BlockTicketBarrier.class.getDeclaredField("isEntrance");
			field.setAccessible(true);
			return field;
		} catch (Throwable throwable) {
			return null;
		}
	}

	/**
	 * Whether the block is an exit ticket barrier, i.e. one that this mod can configure and that can
	 * start an outerchange. Addon barriers are included because they extend the same MTR class.
	 */
	public static boolean isExitBarrier(Block block) {
		if (block == null) {
			return false;
		}
		if (MTR_EXIT.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block))) {
			return true;
		}
		if (!(block instanceof BlockTicketBarrier)) {
			return false;
		}
		final Class<?> blockClass = block.getClass();
		final Boolean cached = ENTRANCE_CACHE.get(block);
		if (cached != null) {
			return !cached;
		}
		if (UNRESOLVED.contains(blockClass)) {
			return false;
		}
		final Boolean isEntrance = readIsEntrance(block);
		if (isEntrance == null) {
			UNRESOLVED.add(blockClass);
			return false;
		}
		ENTRANCE_CACHE.put(block, isEntrance);
		return !isEntrance;
	}

	/**
	 * Reads the {@code isEntrance} flag of a barrier. Addons that redeclare the field hide the parent's
	 * copy behind a fresh field of their own, so the declaring class is searched as well.
	 */
	private static Boolean readIsEntrance(Block block) {
		if (IS_ENTRANCE == null) {
			return null;
		}
		for (Class<?> type = block.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			try {
				final Field field = type.getDeclaredField("isEntrance");
				field.setAccessible(true);
				return field.getBoolean(block);
			} catch (NoSuchFieldException e) {
				// The addon redeclares the field further up the hierarchy, or does not keep it at all.
			} catch (Throwable throwable) {
				return null;
			}
		}
		return null;
	}
}
