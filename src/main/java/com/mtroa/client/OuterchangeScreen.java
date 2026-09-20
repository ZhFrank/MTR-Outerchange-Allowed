package com.mtroa.client;

import com.mtroa.compat.CompatClient;
import com.mtroa.data.BarrierConfig;
import com.mtroa.network.Networking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Configuration screen of an exit barrier.
 * <p>
 * Nothing is sent to the server while the screen is being edited: every change stays local until
 * the "Done" button is pressed, which saves the whole configuration in one go. Closing the screen
 * any other way (Esc / inventory key) simply discards the pending edits.
 */
public class OuterchangeScreen extends Screen {

	private final BlockPos blockPos;
	private final int initialMinutes;
	private boolean outerchangeAllowed;

	private ButtonWidget toggleButton;
	private MinuteSliderWidget slider;

	public OuterchangeScreen(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		super(Text.translatable("screen.mtr_outerchange.title"));
		this.blockPos = blockPos;
		this.outerchangeAllowed = outerchangeAllowed;
		this.initialMinutes = BarrierConfig.clampMinutes(minutes);
	}

	@Override
	protected void init() {
		final int centerX = this.width / 2;
		final int top = this.height / 2 - 50;

		toggleButton = ButtonWidget.builder(Text.empty(), button -> {
			outerchangeAllowed = !outerchangeAllowed;
			updateLabels();
		}).dimensions(centerX - 100, top, 200, 20).build();
		addDrawableChild(toggleButton);

		slider = new MinuteSliderWidget(centerX - 100, top + 30, 200, 20, initialMinutes);
		addDrawableChild(slider);

		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mtr_outerchange.done"), button -> {
			Networking.sendUpdate(blockPos, outerchangeAllowed, slider.getMinutes());
			close();
		}).dimensions(centerX - 60, top + 62, 120, 20).build());

		updateLabels();
	}

	private void updateLabels() {
		if (toggleButton != null) {
			toggleButton.setMessage(Text.translatable("screen.mtr_outerchange.allow")
					.append(Text.translatable(outerchangeAllowed ? "screen.mtr_outerchange.yes" : "screen.mtr_outerchange.no")));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		CompatClient.renderBackground(this, context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 78, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
