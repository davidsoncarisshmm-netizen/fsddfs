package com.sussyclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.sussyclient.ui.SussyScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class SussyClient implements ClientModInitializer {
    public static final String NAME = "Sussy Client";
    public static final String ID = "sussyclient";
    public static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));

        // Default bind: Right Shift (can still be changed in Options > Controls > Key Binds)
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sussyclient.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new SussyScreen());
            }
            SusChunkFinder.tick(mc);
            ActivityInspector.tick(mc);
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(SusChunkFinder::render);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ActivityInspector::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { SusChunkFinder.close(); ActivityInspector.close(); });
    }
}
