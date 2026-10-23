package com.godvoice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While a god has a voice line open, their microphone is NOT played to nearby players.
 * It is sent only to the chosen target(s), at any distance, like the god is speaking inside their head.
 */
public final class VoiceLink implements VoicechatPlugin, VoiceBridge {
    private volatile VoicechatServerApi api;
    private final Map<UUID, StaticAudioChannel> channels = new ConcurrentHashMap<>();

    public static VoiceBridge register(JavaPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return null;
        VoiceLink link = new VoiceLink();
        service.registerPlugin(link);
        return link;
    }

    @Override
    public String getPluginId() {
        return "godvoice";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    private void onMic(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;
        StaticAudioChannel channel = channels.get(sender.getPlayer().getUuid());
        if (channel == null) return;
        event.cancel(); // don't play it to everyone nearby
        channel.send(event.getPacket().getOpusEncodedData());
    }

    @Override
    public int link(UUID god, Collection<UUID> targets) {
        VoicechatServerApi a = api;
        if (a == null) return 0;
        StaticAudioChannel channel = a.createStaticAudioChannel(UUID.randomUUID());
        if (channel == null) return 0;
        int reached = 0;
        for (UUID t : targets) {
            VoicechatConnection c = a.getConnectionOf(t);
            if (c != null) {
                channel.addTarget(c);
                reached++;
            }
        }
        if (reached == 0) return 0;
        channels.put(god, channel);
        return reached;
    }

    @Override
    public void unlink(UUID god) {
        channels.remove(god);
    }

    @Override
    public boolean isLinked(UUID god) {
        return channels.containsKey(god);
    }
}
