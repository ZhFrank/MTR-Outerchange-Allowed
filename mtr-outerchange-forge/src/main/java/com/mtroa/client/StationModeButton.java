package com.mtroa.client;

import com.mtroa.data.StationMode;
import com.mtroa.network.Networking;
import net.minecraft.client.Minecraft;
import org.mtr.mapping.holder.ButtonWidget;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.PressAction;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;

/**
 * The station wide outerchange button inside MTR's station editor
 * ({@code EditStationScreen}, opened from the dashboard with the pencil tool).
 * <p>
 * It sits directly below the "station color" and "fare zone" fields of the top
 * row and cycles through "yes", "no" and "custom" for the station being edited.
 * Choosing "yes" or "no" tells the server to force that decision onto every exit
 * barrier of the station without touching their individual settings, so cycling
 * back to "custom" restores exactly what each barrier had before. If the player
 * then edits one barrier by hand, the server drops the station back to "custom"
 * on its own and the label follows.
 * <p>
 * While an exit is being added or edited (the lower panels), the button hides
 * itself so it never overlaps MTR's own controls; it comes back once the exit
 * editing state is left. The state is tracked locally only so the label can be
 * drawn without a round trip; the server stays the only authority and answers
 * with the real state whenever the editor opens.
 */
public final class StationModeButton {

	/** Same height as the other widgets of the editor, so the button lines up with them. */
	private static final int HEIGHT = 20;
	private static final int TOP_ROW_Y = 22;
	private static final int TOP_ROW_HEIGHT = 20;
	/** Gap between the top row fields and this button, and between the button and the exit panels below. */
	private static final int GAP = 2;
	/** The margin MTR itself leaves at the left and right edge of a row. */
	private static final int MARGIN = 2;

	private static ButtonWidgetExtension button;
	private static long stationId;
	private static StationMode currentMode = StationMode.CUSTOM;
	private static boolean serverKnown;

	private StationModeButton() {
	}

	/**
	 * Builds the button and attaches it to the freshly initialized editor screen.
	 * Called from the mixin at the tail of {@code init2}, which runs again on every
	 * window resize, so the button is rebuilt each time with fresh coordinates.
	 */
	public static void create(ScreenExtension screen, long id) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		stationId = id;
		currentMode = StationMode.CUSTOM;
		serverKnown = false;

		final int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		// Covers the same half of the top row as the station colour and fare zone fields, so the
		// button lines up with the fields it belongs to instead of spanning the whole screen. Being
		// as tall as the other widgets means it reaches into the row where MTR draws its "exits"
		// captions at y = 56; those captions only appear while an exit is being edited, which is
		// exactly when this button hides itself.
		final int x = screenWidth / 2 + MARGIN;
		final int width = screenWidth - MARGIN - x;
		button = new ButtonWidgetExtension(
				x,
				TOP_ROW_Y + TOP_ROW_HEIGHT + GAP,
				width,
				HEIGHT,
				TextHelper.translatable("screen.mtr_outerchange.station_loading"),
				// An anonymous class instead of a lambda: on Forge the MTR jar keeps the
				// SRG name for its default OnPress bridge (m_93750_), so in a deobfuscated
				// compile environment that bridge does not override the mojmap onPress and
				// PressAction ends up looking like it has two abstract methods. Both entry
				// points are implemented, so whichever one the runtime uses cycles the mode.
				new PressAction() {
					@Override
					public void onPress2(ButtonWidget pressed) {
						cycle();
					}

					@Override
					public void onPress(net.minecraft.client.gui.components.Button pressed) {
						cycle();
					}
				}
		);
		screen.addChild(new ClickableWidget(button));

		Networking.requestStationMode(stationId);
		updateLabel(null);
	}

	/**
	 * Shows or hides the button. Hidden while an exit parent or exit destination is
	 * being edited, visible in the plain editor state.
	 */
	public static void setVisible(boolean visible) {
		if (button != null) {
			button.setVisibleMapped(visible);
			button.setActiveMapped(visible);
		}
	}

	/**
	 * Applies the state the server just reported for the station being edited.
	 */
	public static void acceptMode(long id, StationMode mode) {
		if (button != null && id == stationId) {
			currentMode = mode;
			serverKnown = true;
			updateLabel(mode);
		}
	}

	private static void updateLabel(StationMode mode) {
		if (button == null) {
			return;
		}
		// "Outerchange: yes / no / custom" - the button is the only place the state is shown, so the
		// two parts are appended rather than nested as a translation argument.
		final MutableText label = mode == null
				? TextHelper.translatable("screen.mtr_outerchange.station_loading")
				: TextHelper.append(TextHelper.translatable("screen.mtr_outerchange.station"),
				TextHelper.translatable("screen.mtr_outerchange.station_" + mode.name().toLowerCase()));
		button.setMessage2(new Text(label.data));
	}

	private static void cycle() {
		if (button == null) {
			return;
		}
		// Show the new state right away; the server confirms it and the label is corrected if it differs.
		final StationMode next = currentMode == StationMode.ALLOW ? StationMode.DENY
				: currentMode == StationMode.DENY ? StationMode.CUSTOM
				: StationMode.ALLOW;
		currentMode = next;
		serverKnown = true;
		updateLabel(next);
		Networking.sendStationMode(stationId, next);
	}
}
