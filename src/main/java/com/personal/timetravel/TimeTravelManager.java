package com.personal.timetravel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Runs the time trips.
 *
 *   1. shut the doors (AiT won't take off with them open)
 *   2. take off, staying over the same spot
 *   3. while in the vortex, spin the sky forward to the chosen time of day
 *   4. land again where we left (and put the old destination back)
 *
 * If the TARDIS is already in flight when you start, it only does step 3 and leaves the landing to you.
 * Trips live in memory only: if the server stops mid-trip the TARDIS simply stays in the vortex.
 */
@Mod.EventBusSubscriber(modid = TimeTravelMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TimeTravelManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Phase { DOORS, TAKEOFF, SKY, LANDING }

    private static final class Trip {
        UUID tardisId;
        ResourceKey<Level> interior;
        ResourceKey<Level> exterior;
        int target;
        long remaining;
        Phase phase;
        long phaseStart;
        long flightSeenAt = -1;
        boolean ownFlight;
        Object homePos;
        Object originalDestination;
        int landTries;
        long lastLandTry;
    }

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    private TimeTravelManager() {}

    /** Starts a trip for the TARDIS this interior belongs to. Returns a problem to show the player, or null if all is well. */
    public static String start(ServerLevel interior, ServerPlayer player, int target) throws Exception {
        UUID id = AitBridge.tardisIdOf(interior);
        if (id == null) return "The Time Dial only works inside a TARDIS.";
        if (TRIPS.containsKey(id)) return "This TARDIS is already travelling through time.";

        MinecraftServer server = interior.getServer();
        Object tardis = AitBridge.tardis(server, id);
        if (tardis == null) return "Couldn't find this TARDIS.";
        Object travel = AitBridge.travel(tardis);
        String state = AitBridge.stateName(travel);
        long now = server.getTickCount();

        Trip t = new Trip();
        t.tardisId = id;
        t.interior = interior.dimension();
        t.target = target;
        t.remaining = TimeUtil.ticksUntil(server.overworld().getDayTime(), target);
        t.phaseStart = now;
        Object posObj = AitBridge.position(travel);
        ServerLevel exterior = posObj == null ? null : AitBridge.worldOf(posObj);
        t.exterior = exterior == null ? Level.OVERWORLD : exterior.dimension();

        String from = TimeUtil.clockText(server.overworld().getDayTime());
        String to = TimeUtil.describe(target);

        if ("FLIGHT".equals(state)) {
            t.ownFlight = false;
            t.phase = Phase.SKY;
            t.flightSeenAt = now;
            TRIPS.put(id, t);
            tell(server, t, "Time travel: " + from + " -> " + to, false);
            return null;
        }
        if (!"LANDED".equals(state)) {
            return "Wait for the TARDIS to finish materialising or dematerialising first.";
        }
        if (exterior != null && exterior.dimensionType().hasFixedTime()) {
            return "There is no day or night where the TARDIS is parked, so there is no time of day to travel to.";
        }

        t.ownFlight = true;
        t.homePos = posObj;
        t.originalDestination = AitBridge.destination(travel);

        Object door = AitBridge.door(tardis);
        if (AitBridge.isDoorOpen(door)) {
            AitBridge.closeDoors(door);
            t.phase = Phase.DOORS;
            TRIPS.put(id, t);
            tell(server, t, "Closing the doors... then time travel to " + to, false);
            return null;
        }
        if (!takeOff(tardis, travel, t)) {
            return "The TARDIS wouldn't take off (no power or fuel, or something is blocking it).";
        }
        t.phase = Phase.TAKEOFF;
        TRIPS.put(id, t);
        tell(server, t, "Time travel: " + from + " -> " + to, false);
        return null;
    }

    /** Sets the destination to where we are and dematerialises. False if AiT refused. */
    private static boolean takeOff(Object tardis, Object travel, Trip t) throws Exception {
        if (t.homePos != null) {
            AitBridge.setDestination(travel, t.homePos);
        }
        AitBridge.dematerialize(travel);
        if ("LANDED".equals(AitBridge.stateName(travel))) {
            restoreDestination(travel, t);
            return false;
        }
        return true;
    }

    private static void restoreDestination(Object travel, Trip t) {
        if (!t.ownFlight || t.originalDestination == null) return;
        try {
            AitBridge.setDestination(travel, t.originalDestination);
        } catch (Exception ignored) {
            // not important
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRIPS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.getTickCount();
        for (UUID id : new ArrayList<>(TRIPS.keySet())) {
            Trip t = TRIPS.get(id);
            if (t == null) continue;
            try {
                process(server, id, t, now);
            } catch (Throwable e) {
                LOGGER.error("Time travel for TARDIS {} stopped because of an error", id, e);
                TRIPS.remove(id);
                tell(server, t, "Time travel stopped because of an error (see the log).", true);
            }
        }
    }

    private static void process(MinecraftServer server, UUID id, Trip t, long now) throws Exception {
        Object tardis = AitBridge.tardis(server, id);
        if (tardis == null) {
            TRIPS.remove(id);
            return;
        }
        Object travel = AitBridge.travel(tardis);
        String state = AitBridge.stateName(travel);

        switch (t.phase) {
            case DOORS -> {
                if (!"LANDED".equals(state)) {
                    TRIPS.remove(id); // took off some other way
                    return;
                }
                Object door = AitBridge.door(tardis);
                if (AitBridge.isDoorOpen(door)) {
                    AitBridge.closeDoors(door); // someone reopened them
                }
                if (AitBridge.isDoorClosed(door) || now - t.phaseStart >= TimeConfig.doorTimeoutTicks()) {
                    if (!takeOff(tardis, travel, t)) {
                        TRIPS.remove(id);
                        tell(server, t, "The TARDIS wouldn't take off (doors still open, no power or fuel, or something is blocking it).", true);
                        return;
                    }
                    t.phase = Phase.TAKEOFF;
                    t.phaseStart = now;
                }
            }
            case TAKEOFF -> {
                if ("FLIGHT".equals(state)) {
                    t.phase = Phase.SKY;
                    t.flightSeenAt = now;
                } else if ("LANDED".equals(state) && now - t.phaseStart > 10) {
                    TRIPS.remove(id);
                    restoreDestination(travel, t);
                    tell(server, t, "The TARDIS didn't take off.", true);
                } else if (now - t.phaseStart > 20 * 40) {
                    TRIPS.remove(id);
                    tell(server, t, "The TARDIS took too long to enter the vortex, so time travel was cancelled.", true);
                }
            }
            case SKY -> {
                if (!"FLIGHT".equals(state)) {
                    TRIPS.remove(id);
                    restoreDestination(travel, t);
                    tell(server, t, "The flight was interrupted before the sky reached " + TimeUtil.clockText(t.target) + ".", true);
                    return;
                }
                if (t.remaining > 0) {
                    long step = Math.min(TimeConfig.skySpeed(), t.remaining);
                    advanceSky(server, t, step);
                    t.remaining -= step;
                    if (now % 4 == 0) {
                        tell(server, t, "Racing through time...  " + TimeUtil.clockText(server.overworld().getDayTime()), false);
                    }
                }
                if (t.remaining == 0 && now - t.flightSeenAt >= TimeConfig.minFlightTicks()) {
                    if (t.ownFlight) {
                        AitBridge.rematerialize(travel);
                        t.phase = Phase.LANDING;
                        t.phaseStart = now;
                        t.lastLandTry = now;
                        t.landTries = 1;
                    } else {
                        TRIPS.remove(id);
                        arrived(server, t);
                    }
                }
            }
            case LANDING -> {
                if ("LANDED".equals(state)) {
                    TRIPS.remove(id);
                    restoreDestination(travel, t);
                    arrived(server, t);
                } else if ("FLIGHT".equals(state) && now - t.lastLandTry >= 100) {
                    if (t.landTries >= 5) {
                        TRIPS.remove(id);
                        tell(server, t, "The sky is at " + TimeUtil.clockText(t.target)
                                + " but the TARDIS wouldn't land by itself. Land it yourself.", true);
                    } else {
                        AitBridge.rematerialize(travel);
                        t.landTries++;
                        t.lastLandTry = now;
                    }
                } else if (now - t.phaseStart > 20 * 90) {
                    TRIPS.remove(id);
                    tell(server, t, "The TARDIS took too long to land. The sky is at " + TimeUtil.clockText(t.target) + ".", true);
                }
            }
        }
    }

    /** Moves the world's clock forward and shows the players every tick, so the sky visibly spins. */
    private static void advanceSky(MinecraftServer server, Trip t, long step) {
        ServerLevel overworld = server.overworld();
        long newTime = overworld.getDayTime() + step;
        overworld.setDayTime(newTime);
        ClientboundSetTimePacket packet = new ClientboundSetTimePacket(
                overworld.getGameTime(), newTime, overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        PlayerList players = server.getPlayerList();
        players.broadcastAll(packet, Level.OVERWORLD);
        if (t.exterior != null && !t.exterior.equals(Level.OVERWORLD)) {
            players.broadcastAll(packet, t.exterior);
        }
    }

    private static void arrived(MinecraftServer server, Trip t) {
        if (TimeConfig.clearWeather()) {
            server.overworld().setWeatherParameters(6000, 0, false, false);
        }
        tell(server, t, "Arrived: " + TimeUtil.describe(t.target), false);
    }

    /** Shows a message to everyone inside the TARDIS (action bar, or chat for things that must not be missed). */
    private static void tell(MinecraftServer server, Trip t, String text, boolean chat) {
        ServerLevel inside = server.getLevel(t.interior);
        if (inside == null) return;
        Component message = Component.literal(text);
        for (ServerPlayer p : inside.players()) {
            if (chat) {
                p.sendSystemMessage(message);
            } else {
                p.displayClientMessage(message, true);
            }
        }
    }
}
