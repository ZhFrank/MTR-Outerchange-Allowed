package com.mtroa.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Minecraft 1.20.4 specific client helper.
 */
public final class CompatClient {

	private CompatClient() {
	}

	public static void renderBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
		screen.renderBackground(context, mouseX, mouseY, delta);
	}
}
