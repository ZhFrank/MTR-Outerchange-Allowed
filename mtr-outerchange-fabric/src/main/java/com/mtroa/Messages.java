package com.mtroa;

import com.mtroa.data.OuterchangeData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Sends a translated message to a player by name.
 * <p>
 * {@link Text#translatable} keeps the translation key, so the text is resolved by the receiving
 * client using the language selected in its own game settings.
 */
public final class Messages {

	private Messages() {
	}

	public static void send(String playerName, boolean overlay, String key, Object... args) {
		final MinecraftServer server = OuterchangeData.getServer();
		if (server == null) {
			return;
		}
		final ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
		if (player != null) {
			player.sendMessage(Text.translatable(key, args), overlay);
		}
	}
}
