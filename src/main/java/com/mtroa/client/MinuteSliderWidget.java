package com.mtroa.client;

import com.mtroa.data.BarrierConfig;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Slider that picks a whole number of minutes between {@link BarrierConfig#MIN_MINUTES} and
 * {@link BarrierConfig#MAX_MINUTES}.
 * <p>
 * Do <b>not</b> declare a field called {@code value} here: {@link SliderWidget} already has a
 * {@code protected double value} holding the raw 0..1 progress and shadowing it makes
 * {@link #applyValue()} read the integer minutes as if they were a progress, which is what
 * produced nonsense numbers such as 1191 or 140329 on the button.
 * <p>
 * The slider does not report anything while being dragged either - the owning screen simply reads
 * {@link #getMinutes()} when it is time to save.
 */
public class MinuteSliderWidget extends SliderWidget {

	public MinuteSliderWidget(int x, int y, int width, int height, int initialMinutes) {
		super(x, y, width, height, Text.empty(), minutesToProgress(initialMinutes));
		updateMessage();
	}

	private static double minutesToProgress(int minutes) {
		final int clamped = BarrierConfig.clampMinutes(minutes);
		return (double) (clamped - BarrierConfig.MIN_MINUTES) / (double) (BarrierConfig.MAX_MINUTES - BarrierConfig.MIN_MINUTES);
	}

	/** Whole minute count currently represented by the slider handle. */
	public int getMinutes() {
		final int raw = BarrierConfig.MIN_MINUTES + (int) Math.round(this.value * (BarrierConfig.MAX_MINUTES - BarrierConfig.MIN_MINUTES));
		return BarrierConfig.clampMinutes(raw);
	}

	@Override
	protected void updateMessage() {
		setMessage(Text.translatable("screen.mtr_outerchange.minutes", String.valueOf(getMinutes())));
	}

	@Override
	protected void applyValue() {
		// Snap the handle onto a whole minute so the picture matches the number.
		this.value = minutesToProgress(getMinutes());
		updateMessage();
	}
}
