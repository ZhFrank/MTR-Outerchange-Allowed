package com.mtroa;

import com.mtroa.data.OuterchangeData;
import com.mtroa.handler.BrushClickHandler;
import com.mtroa.network.Networking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MtrOuterchange implements ModInitializer {

	public static final String MOD_ID = "mtr_outerchange";
	public static final Logger LOGGER = LoggerFactory.getLogger("MTR Outerchange Allowed");

	@Override
	public void onInitialize() {
		OuterchangeData.registerEvents();
		Networking.registerServerReceivers();
		BrushClickHandler.register();
		LOGGER.info("MTR Outerchange Allowed loaded");
	}
}
