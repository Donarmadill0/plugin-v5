package com.godvoice.client;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class GodVoiceMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(DialoguePayload.ID, DialoguePayload.CODEC);
    }
}
