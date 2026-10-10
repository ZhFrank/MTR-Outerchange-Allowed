package com.mtroa;

import com.mtroa.data.OuterchangeData;
import com.mtroa.handler.BrushClickHandler;
import com.mtroa.network.Networking;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MtrOuterchange.MOD_ID)
public class MtrOuterchangeForge {

	public MtrOuterchangeForge(FMLJavaModLoadingContext context) {
		Networking.register();

		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> OuterchangeData.load(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> OuterchangeData.save());
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}
			if (++OuterchangeData.TICK_COUNTER >= 20) {
				OuterchangeData.TICK_COUNTER = 0;
				OuterchangeData.tick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener(BrushClickHandler::onRightClickBlock);

		DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
				() -> com.mtroa.client.ClientSetup::register);

		MtrOuterchange.LOGGER.info("MTR Outerchange Allowed (Forge) loaded");
	}
}
