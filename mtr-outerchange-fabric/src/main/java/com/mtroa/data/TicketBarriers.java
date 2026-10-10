package com.mtroa.data;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
 * Addon has four (Moscow old and new, entrance and exit), the Joban Client Mod has three (Thales,
 * bare metal, entrance and exit), and Tianjin Metro has two. All of them are {@code BlockTicketBarrier}
 * instances and all of them call the same {@code TicketSystem}, so they behave exactly like MTR's own
 * barriers and must be configurable too.
 * <p>
 * On Fabric, MTR's jar is compiled against intermediary names, so its barrier class cannot be named in
 * this source: doing so would drag the whole Minecraft type hierarchy in under intermediary names. The
 * class is therefore looked up by name and used reflectively, which behaves exactly the same and keeps
 * the two loaders' behaviour identical.
 * <p>
 * Which side a barrier is on is read from {@code isEntrance}. That field is private and is redeclared by
 * the addons that need it, so the lookup walks the class hierarchy and caches the answer. A class whose
 * layout cannot be read is remembered as unresolvable and is simply not treated as a configurable
 * barrier, rather than breaking the game.
 * <p>
 * The answer is cached per <b>block</b>, never per class: MTR registers the entrance and the exit as two
 * instances of one and the same class, so a cache keyed by class would answer for both of them with
 * whichever was looked at first and would happily open the configuration screen on an entrance barrier.
 */
public final class TicketBarriers {

	/** MTR's own exit barrier, kept as a fast path because it needs no reflection at all. */
	public static final Identifier MTR_EXIT = new Identifier("mtr", "ticket_barrier_exit_1");

	private static final String BARRIER_CLASS = "org.mtr.mod.block.BlockTicketBarrier";
	private static final Class<?> BARRIER_TYPE = loadBarrierClass();

	private static final Map<Block, Boolean> ENTRANCE_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
	/** Blocks that could not be classified, so the (failed) reflection is never repeated every click. */
	private static final Set<Class<?>> UNRESOLVED = Collections.newSetFromMap(new IdentityHashMap<>());

	private TicketBarriers() {
	}

	private static Class<?> loadBarrierClass() {
		try {
			return Class.forName(BARRIER_CLASS);
		} catch (Throwable throwable) {
			return null;
		}
	}

	/**
	 * Whether the block is an exit ticket barrier, i.e. one that this mod can configure and that can
	 * start an outerchange. Addon barriers are included because they are the same MTR class.
	 */
	public static boolean isExitBarrier(Block block) {
		if (block == null || BARRIER_TYPE == null) {
			return false;
		}
		if (MTR_EXIT.equals(Registries.BLOCK.getId(block))) {
			return true;
		}
		if (!BARRIER_TYPE.isInstance(block)) {
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
