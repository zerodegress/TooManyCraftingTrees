package com.zerodegress.tmct;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(TooManyCraftingTrees.MODID)
public class TooManyCraftingTrees {
    public static final String MODID = "too_many_crafting_trees";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TooManyCraftingTrees(IEventBus modEventBus, ModContainer modContainer) {
    }
}
