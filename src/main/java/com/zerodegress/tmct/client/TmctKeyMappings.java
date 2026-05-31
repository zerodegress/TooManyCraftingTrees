package com.zerodegress.tmct.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerodegress.tmct.TooManyCraftingTrees;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class TmctKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(TooManyCraftingTrees.MODID, "tree")
    );
    public static final KeyMapping OPEN_TREE = new KeyMapping(
        "key.tmct.open_tree",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_Y,
        CATEGORY
    );

    private TmctKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        TooManyCraftingTrees.LOGGER.info("Registering TMCT key mapping {}", OPEN_TREE.getName());
        event.register(OPEN_TREE);
    }
}
