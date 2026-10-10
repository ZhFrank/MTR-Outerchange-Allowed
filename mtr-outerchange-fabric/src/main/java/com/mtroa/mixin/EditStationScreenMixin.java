package com.mtroa.mixin;

import com.mtroa.client.StationModeButton;
import org.mtr.core.data.Station;
import org.mtr.core.data.StationExit;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.screen.EditStationScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the station wide outerchange button to MTR's station editor screen.
 * <p>
 * The station id is captured from the constructor (the editor stores it in a
 * private field of its generic base class, which is awkward to shadow safely),
 * the button is built at the tail of {@code init2} so it re-inits correctly on
 * window resizes, and {@code changeEditingExit} drives visibility: MTR routes
 * every enter/leave of its exit editing panels through that one method, so
 * hooking its tail keeps the button hidden exactly while the exit panels use
 * the space below the fare zone field.
 */
@Mixin(value = EditStationScreen.class, remap = false)
public abstract class EditStationScreenMixin {

	@Unique
	private static long mtroa$stationId;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mtroa$captureStation(Station station, ScreenExtension previousScreenExtension, CallbackInfo ci) {
		mtroa$stationId = station.getId();
	}

	@Inject(method = "init2", at = @At("TAIL"))
	private void mtroa$addStationModeButton(CallbackInfo ci) {
		StationModeButton.create((EditStationScreen) (Object) this, mtroa$stationId);
	}

	@Inject(method = "changeEditingExit", at = @At("TAIL"))
	private void mtroa$onEditingExitChanged(StationExit editingExit, int editingDestinationIndex, CallbackInfo ci) {
		StationModeButton.setVisible(editingExit == null && editingDestinationIndex < 0);
	}
}
