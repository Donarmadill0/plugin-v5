package com.godvoice;

import java.util.Collection;
import java.util.UUID;

/** Plain interface so the main plugin never touches Simple Voice Chat classes directly. */
public interface VoiceBridge {
    /** Route the god's microphone only to these players. Returns how many were reachable. */
    int link(UUID god, Collection<UUID> targets);

    void unlink(UUID god);

    boolean isLinked(UUID god);
}
