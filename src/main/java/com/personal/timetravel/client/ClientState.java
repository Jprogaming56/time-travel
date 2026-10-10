package com.personal.timetravel.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What this client last asked for, per TARDIS, so the Monitor can show it. -1 / missing = off. */
public final class ClientState {
    public static final Map<UUID, Integer> TARGETS = new HashMap<>();

    private ClientState() { }
}
