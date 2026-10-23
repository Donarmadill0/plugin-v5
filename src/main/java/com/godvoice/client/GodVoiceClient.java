package com.godvoice.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;

public class GodVoiceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayload.ID,
                (payload, context) -> DialogueOverlay.push(payload));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("godvoice", "dialogue"), DialogueOverlay::render);
    }
}
