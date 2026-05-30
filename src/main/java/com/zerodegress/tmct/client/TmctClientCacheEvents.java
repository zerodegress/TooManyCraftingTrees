package com.zerodegress.tmct.client;

import com.zerodegress.tmct.TooManyCraftingTrees;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@EventBusSubscriber(modid = TooManyCraftingTrees.MODID, value = Dist.CLIENT)
public final class TmctClientCacheEvents {
    private static final TmctClientRecipeService RECIPE_SERVICE = TmctClientRecipeService.getInstance();

    private TmctClientCacheEvents() {
    }

    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        RECIPE_SERVICE.invalidateCache();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent.ClientPacketReceived event) {
        RECIPE_SERVICE.invalidateCache();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RECIPE_SERVICE.invalidateCache();
    }
}
