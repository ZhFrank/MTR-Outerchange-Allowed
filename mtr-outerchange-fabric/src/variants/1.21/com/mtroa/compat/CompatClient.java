package com.mtroa.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Minecraft 1.21.x (Fabric, Yarn) client helper. From this version on the screen background is drawn
 * with the mouse position.
 */
public final class CompatClient {

	private CompatClient() {
	}

	public static void renderBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
		screen.renderBackground(context, mouseX, mouseY, delta);
	}
}
