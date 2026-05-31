package com.zerodegress.tmct.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class TmctTreeKeyEvents {
    private TmctTreeKeyEvents() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (TmctKeyMappings.OPEN_TREE.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("TMCT key triggered"));
            }
            TmctTreeOpenService.getInstance().openFromHoveredIngredient();
        }
    }
}
