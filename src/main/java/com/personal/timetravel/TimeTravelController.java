package com.personal.timetravel;

import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.handler.travel.TravelHandler;
import dev.amble.ait.core.tardis.handler.travel.TravelHandlerBase;
import dev.amble.ait.core.tardis.manager.ServerTardisManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Monitor sets a "land at" time for a TARDIS. You fly it as normal with the throttle. While it is in
 * flight the day clock of the destination world races toward that time, and the moment it lands the clock
 * is set exactly to it. The setting is used once and then cleared.
 */
public final class TimeTravelController {
    private static final long DAY = 24000L;

    /** tardis id -> chosen day-tick (0..23999, 0 = 06:00). */
    private static final Map<UUID, Integer> TARGETS = new HashMap<>();
    /** tardis id -> player who set it (gets the arrival message). */
    private static final Map<UUID, UUID> SETTERS = new HashMap<>();
    /** TARDISes that have left the ground since their target was set. */
    private static final Set<UUID> AIRBORNE = new HashSet<>();
    private static final Set<UUID> LANDING = new HashSet<>();

    private TimeTravelController() { }

    static void setTarget(ServerPlayer player, UUID tardisId, int tick) {
        if (tick < 0) {
            forget(tardisId);
            player.sendSystemMessage(Component.literal("Time travel off for this TARDIS."));
            return;
        }
        TARGETS.put(tardisId, (int) Math.floorMod((long) tick, DAY));
        SETTERS.put(tardisId, player.getUUID());
    }

    private static void forget(UUID id) {
        TARGETS.remove(id);
        SETTERS.remove(id);
        AIRBORNE.remove(id);
        LANDING.remove(id);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TARGETS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Integer>> it = TARGETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            UUID id = e.getKey();
            boolean done;
            try {
                Tardis tardis = ServerTardisManager.getInstance().demandTardis(server, id);
                done = tardis == null || step(server, id, tardis, e.getValue());
            } catch (Throwable t) {
                done = true;
            }
            if (done) {
                it.remove();
                SETTERS.remove(id);
                AIRBORNE.remove(id);
                LANDING.remove(id);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TARGETS.clear();
        SETTERS.clear();
        AIRBORNE.clear();
        LANDING.clear();
    }

    /** @return true when the job is finished. */
    private static boolean step(MinecraftServer server, UUID id, Tardis tardis, int target) {
        TravelHandler travel = tardis.travel();
        TravelHandlerBase.State state = travel.getState();

        if (state != TravelHandlerBase.State.LANDED) AIRBORNE.add(id);
        if (!AIRBORNE.contains(id)) return false;

        ServerLevel level = levelFor(server, travel, state);
        long now = level.getDayTime();
        long delta = Math.floorMod(target - now, DAY);

        if (state == TravelHandlerBase.State.LANDED) {
            level.setDayTime(now + delta);
            ServerPlayer p = server.getPlayerList().getPlayer(SETTERS.getOrDefault(id, new UUID(0, 0)));
            if (p != null) p.sendSystemMessage(Component.literal("Arrived. It is now " + clock(target) + "."));
            return true;
        }

        if (state == TravelHandlerBase.State.FLIGHT) {
            if (delta > 0) {
                level.setDayTime(now + Math.min(delta, Settings.get().rampTicksPerTick));
            } else if (Settings.get().autoLand && LANDING.add(id)) {
                travel.rematerialize();
            }
        }
        return false;
    }

    private static ServerLevel levelFor(MinecraftServer server, TravelHandler travel, TravelHandlerBase.State state) {
        ServerLevel level = null;
        try {
            // Reflection on purpose: the position type extends a class from AmbleKit, which the build doesn't have.
            String method = state == TravelHandlerBase.State.LANDED ? "position" : "destination";
            Object pos = travel.getClass().getMethod(method).invoke(travel);
            Object world = pos.getClass().getMethod("getWorld").invoke(pos);
            if (world instanceof ServerLevel serverLevel) level = serverLevel;
        } catch (Throwable ignored) { }
        return level != null ? level : server.overworld();
    }

    /** Minecraft day-tick 0 is 06:00. */
    public static String clock(long tick) {
        long minutes = Math.floorMod(Math.round(tick * 60.0 / 1000.0) + 6 * 60, 24 * 60L);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
