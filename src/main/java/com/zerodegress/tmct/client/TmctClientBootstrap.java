package com.zerodegress.tmct.client;

import com.zerodegress.tmct.TooManyCraftingTrees;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TooManyCraftingTrees.MODID, dist = Dist.CLIENT)
public final class TmctClientBootstrap {
    public TmctClientBootstrap(IEventBus modEventBus, ModContainer modContainer) {
        TooManyCraftingTrees.LOGGER.info("Initializing TMCT client bootstrap");
        modEventBus.addListener(TmctKeyMappings::register);
        NeoForge.EVENT_BUS.addListener(TmctTreeKeyEvents::onScreenKeyPressed);
    }
}
