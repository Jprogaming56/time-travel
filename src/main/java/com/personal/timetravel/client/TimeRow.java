package com.personal.timetravel.client;

import com.personal.timetravel.TimeTravelController;
import com.personal.timetravel.TimeTravelNet;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Logic behind the row of buttons added to AIT's Monitor screen (kept out of the mixin on purpose). */
public final class TimeRow {
    private TimeRow() { }

    public static Component labelText(UUID id) {
        Integer t = ClientState.TARGETS.get(id);
        return Component.literal(t == null ? "Land at: off" : "Land at " + TimeTravelController.clock(t));
    }

    /** +/-: 1 hour, or 15 minutes while holding Shift. Starts from noon when the setting is off. */
    public static void step(UUID id, int dir) {
        int stepTicks = Screen.hasShiftDown() ? 250 : 1000;
        Integer cur = ClientState.TARGETS.get(id);
        int next = cur == null ? 6000 : Math.floorMod(cur + dir * stepTicks, 24000);
        ClientState.TARGETS.put(id, next);
        TimeTravelNet.CHANNEL.sendToServer(new TimeTravelNet.SetTarget(id, next));
    }

    public static void off(UUID id) {
        ClientState.TARGETS.remove(id);
        TimeTravelNet.CHANNEL.sendToServer(new TimeTravelNet.SetTarget(id, -1));
    }
}
