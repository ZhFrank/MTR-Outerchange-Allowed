package com.mtroa.mixin;

import com.mtroa.Messages;
import com.mtroa.MtrOuterchange;
import com.mtroa.data.BarrierConfig;
import com.mtroa.data.BarrierTracker;
import com.mtroa.data.OuterchangeData;
import com.mtroa.data.PendingTransfer;
import org.mtr.core.data.Station;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.Scoreboard;
import org.mtr.mapping.holder.ScoreboardObjective;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ScoreboardHelper;
import org.mtr.mod.data.TicketSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Implements the outerchange (out of station interchange) behaviour.
 * <p>
 * <b>Design note - why this hooks {@code onEnter} / {@code onExit} instead of cancelling
 * {@code passThrough}:</b><br>
 * {@code BlockTicketBarrier} sets the block state to PENDING before calling {@code passThrough}, and only
 * the {@code Consumer} callback handed to {@code passThrough} moves it on to OPEN or CLOSED. A cancelled
 * injection at the head of {@code passThrough} therefore has to re-issue its own asynchronous
 * {@code nearby_stations} request and open the barrier from inside that response. If that response never
 * arrives, or the handler throws, the callback is never invoked and the barrier stays PENDING forever -
 * and because {@code onEntityCollision} only fires again from CLOSED, that barrier is then unusable.
 * <p>
 * So this mixin never cancels {@code passThrough}. MTR still resolves the station, still plays the sound
 * and still drives the barrier state. We only override the two private methods that decide whether the
 * fare is paid, which is the only thing this mod actually needs to change.
 */
@Mixin(TicketSystem.class)
public class TicketSystemMixin {

	// ------------------------------------------------------------------ capturing the barrier

	@Inject(method = "passThrough", at = @At("HEAD"), remap = false)
	private static void mtrOuterchange$rememberBarrier(
			World world,
			BlockPos blockPos,
			PlayerEntity player,
			boolean isEntrance,
			boolean isExit,
			SoundEvent entrySound,
			SoundEvent entrySoundConcessionary,
			SoundEvent exitSound,
			SoundEvent exitSoundConcessionary,
			SoundEvent failSound,
			boolean remindIfNoRecord,
			Consumer<TicketSystem.EnumTicketBarrierOpen> callback,
			CallbackInfo callbackInfo
	) {
		try {
			if (world.isClient()) {
				return;
			}
			final String playerName = player.getGameProfile().getName();
			BarrierTracker.remember(world, playerName, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			MtrOuterchange.LOGGER.info("MTR Outerchange: {} stepped into the {} barrier at {},{},{}",
					playerName,
					isEntrance ? "entrance" : "exit",
					blockPos.getX(), blockPos.getY(), blockPos.getZ());
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to record the barrier position", throwable);
		}
	}

	// ------------------------------------------------------------------ exit barrier

	/**
	 * Runs instead of MTR's own exit handling when the barrier allows outerchanges: the fare is not
	 * deducted yet and the entry record is left untouched, so the journey continues on the next entry.
	 */
	@Inject(method = "onExit", at = @At("HEAD"), cancellable = true, remap = false)
	private static void mtrOuterchange$onExit(
			World world,
			Station station,
			PlayerEntity player,
			boolean remindIfNoRecord,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		try {
			if (world.isClient() || station == null) {
				return;
			}
			final String playerName = player.getGameProfile().getName();
			final PendingTransfer existing = OuterchangeData.getPending(playerName);

			if (hasEntryRecord(world, playerName)) {
				final BarrierTracker.Ref barrierRef = BarrierTracker.consume(world, playerName);
				if (barrierRef != null) {
					final BarrierConfig barrierConfig = OuterchangeData.getBarrier(
							barrierRef.getDimensionId(), barrierRef.getX(), barrierRef.getY(), barrierRef.getZ()
					);
					if (barrierConfig != null && barrierConfig.outerchangeAllowed) {
						if (existing != null) {
							OuterchangeData.removePending(playerName);
						}
						final int maxMinutes = BarrierConfig.clampMinutes(barrierConfig.maxMinutes);
						OuterchangeData.addPending(new PendingTransfer(
								playerName,
								System.currentTimeMillis() + maxMinutes * 60000L,
								getScore(world, playerName, PendingTransfer.ENTRY_ZONE_1),
								getScore(world, playerName, PendingTransfer.ENTRY_ZONE_2),
								getScore(world, playerName, PendingTransfer.ENTRY_ZONE_3),
								station.getZone1(),
								station.getZone2(),
								station.getZone3(),
								station.getName(),
								player.isCreative()
						));
						Messages.send(playerName, true, "message.mtr_outerchange.exit",
								String.valueOf(maxMinutes),
								String.valueOf(getScore(world, playerName, PendingTransfer.BALANCE)));
						MtrOuterchange.LOGGER.info("MTR Outerchange: {} left {} through an outerchange, {} minutes allowed",
								playerName, station.getName(), maxMinutes);
						// Open the barrier without charging - MTR plays the exit sound for us.
						callbackInfo.setReturnValue(true);
						return;
					}
				}
			}

			// Normal MTR exit handling below: the journey ends here, so drop any stale outerchange.
			if (existing != null) {
				OuterchangeData.removePending(playerName);
			}
			MtrOuterchange.LOGGER.info("MTR Outerchange: {} ended the journey at {} with the normal fare",
					playerName, station.getName());
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to handle an exit barrier", throwable);
		}
	}

	// ------------------------------------------------------------------ entrance barrier

	/**
	 * Runs instead of MTR's own entry handling when the player is mid outerchange. MTR would otherwise
	 * fine them 500 and rewrite the entry station, which throws away the part of the journey already
	 * travelled. No configuration is needed on the entrance side - the record left by the exit barrier is
	 * the only proof required.
	 */
	@Inject(method = "onEnter", at = @At("HEAD"), cancellable = true, remap = false)
	private static void mtrOuterchange$onEnter(
			World world,
			Station station,
			PlayerEntity player,
			boolean remindIfNoRecord,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		try {
			if (world.isClient()) {
				return;
			}
			final String playerName = player.getGameProfile().getName();
			if (OuterchangeData.getPending(playerName) == null) {
				return;
			}
			if (!hasEntryRecord(world, playerName)) {
				OuterchangeData.removePending(playerName);
				return;
			}
			// Let MTR itself refuse the entry when the balance is negative, so the player sees why.
			if (getScore(world, playerName, PendingTransfer.BALANCE) < 0) {
				return;
			}
			OuterchangeData.removePending(playerName);
			Messages.send(playerName, true, "message.mtr_outerchange.pass_entrance");
			MtrOuterchange.LOGGER.info("MTR Outerchange: {} continued the journey at {} without paying again",
					playerName, station.getName());
			// Open the barrier, keep the original entry station and do not fine - MTR plays the sound.
			callbackInfo.setReturnValue(true);
		} catch (Throwable throwable) {
			MtrOuterchange.LOGGER.error("MTR Outerchange: failed to handle an entrance barrier", throwable);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static boolean hasEntryRecord(World world, String playerName) {
		return getScore(world, playerName, PendingTransfer.ENTRY_ZONE_1) != 0
				&& getScore(world, playerName, PendingTransfer.ENTRY_ZONE_2) != 0
				&& getScore(world, playerName, PendingTransfer.ENTRY_ZONE_3) != 0;
	}

	private static int getScore(World world, String playerName, String objectiveName) {
		final Scoreboard scoreboard = world.getScoreboard();
		final ScoreboardObjective scoreboardObjective = ScoreboardHelper.getScoreboardObjective(scoreboard, objectiveName);
		return scoreboardObjective == null ? 0 : ScoreboardHelper.getPlayerScore(scoreboard, playerName, scoreboardObjective);
	}
}
