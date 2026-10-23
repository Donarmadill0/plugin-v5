package com.godvoice.client;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server plugin -> client: one line of god dialogue. Channel id must match DialogueManager.CHANNEL in the plugin. */
public record DialoguePayload(String title, int color, String message) implements CustomPayload {
    public static final CustomPayload.Id<DialoguePayload> ID =
            new CustomPayload.Id<>(Identifier.of("godvoice", "dialogue"));

    public static final PacketCodec<RegistryByteBuf, DialoguePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DialoguePayload::title,
            PacketCodecs.INTEGER, DialoguePayload::color,
            PacketCodecs.STRING, DialoguePayload::message,
            DialoguePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
