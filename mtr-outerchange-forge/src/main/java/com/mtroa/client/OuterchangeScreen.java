package com.mtroa.client;

import com.mtroa.compat.CompatClient;
import com.mtroa.data.BarrierConfig;
import com.mtroa.network.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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

	private Button toggleButton;
	private MinuteSliderWidget slider;

	public OuterchangeScreen(BlockPos blockPos, boolean outerchangeAllowed, int minutes) {
		super(Component.translatable("screen.mtr_outerchange.title"));
		this.blockPos = blockPos;
		this.outerchangeAllowed = outerchangeAllowed;
		this.initialMinutes = BarrierConfig.clampMinutes(minutes);
	}

	@Override
	protected void init() {
		final int centerX = this.width / 2;
		final int top = this.height / 2 - 50;

		toggleButton = Button.builder(Component.empty(), button -> {
			outerchangeAllowed = !outerchangeAllowed;
			updateLabels();
		}).pos(centerX - 100, top).size(200, 20).build();
		addRenderableWidget(toggleButton);

		slider = new MinuteSliderWidget(centerX - 100, top + 30, 200, 20, initialMinutes);
		addRenderableWidget(slider);

		addRenderableWidget(Button.builder(Component.translatable("screen.mtr_outerchange.done"), button -> {
			Networking.sendUpdate(blockPos, outerchangeAllowed, slider.getMinutes());
			onClose();
		}).pos(centerX - 60, top + 62).size(120, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("screen.mtr_outerchange.sync"), button -> {
			Networking.sendSync(blockPos, outerchangeAllowed, slider.getMinutes());
			onClose();
		}).pos(centerX - 100, top + 88).size(200, 20).build());

		updateLabels();
	}

	private void updateLabels() {
		if (toggleButton != null) {
			toggleButton.setMessage(Component.translatable("screen.mtr_outerchange.allow")
					.copy().append(Component.translatable(outerchangeAllowed ? "screen.mtr_outerchange.yes" : "screen.mtr_outerchange.no")));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		CompatClient.renderBackground(this, graphics, mouseX, mouseY, delta);
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 78, 0xFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
