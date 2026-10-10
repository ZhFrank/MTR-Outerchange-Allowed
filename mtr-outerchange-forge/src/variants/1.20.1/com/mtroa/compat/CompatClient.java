package com.mtroa.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Minecraft 1.20.x (Forge, official mappings) client helper.
 */
public final class CompatClient {

	private CompatClient() {
	}

	public static void renderBackground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		screen.renderBackground(graphics);
	}
}
