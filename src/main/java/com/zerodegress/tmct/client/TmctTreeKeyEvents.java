package com.zerodegress.tmct.client;

import com.zerodegress.tmct.TooManyCraftingTrees;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class TmctTreeKeyEvents {
    private TmctTreeKeyEvents() {
    }

    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft.player == null || minecraft.level == null || screen == null) {
            return;
        }

        KeyEvent keyEvent = new KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        if (!TmctKeyMappings.OPEN_TREE.matches(keyEvent)) {
            return;
        }

        TooManyCraftingTrees.LOGGER.info("TMCT open-tree hotkey pressed on screen {}", screen.getClass().getName());
        TmctTreeOpenService.getInstance().openFromHoveredIngredient();
    }
}
