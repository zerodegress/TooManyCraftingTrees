package com.zerodegress.tmct.jei;

import com.zerodegress.tmct.TooManyCraftingTrees;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class TmctJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(TooManyCraftingTrees.MODID, "recipe_scanner");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeScanner.setRuntime(jeiRuntime);
        TooManyCraftingTrees.LOGGER.info("JEI runtime is available for recipe scanning.");
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRecipeScanner.clearRuntime();
        TooManyCraftingTrees.LOGGER.info("JEI runtime is no longer available for recipe scanning.");
    }
}
