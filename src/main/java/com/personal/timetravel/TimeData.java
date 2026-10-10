package com.personal.timetravel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers which time of day each TARDIS's console button is set to. */
public class TimeData extends SavedData {
    private static final String NAME = "aittimetravel_selection";
    public static final int DEFAULT = 2; // Noon

    private final Map<UUID, Integer> selected = new HashMap<>();

    public static TimeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TimeData::load, TimeData::new, NAME);
    }

    public static TimeData load(CompoundTag tag) {
        TimeData data = new TimeData();
        CompoundTag sel = tag.getCompound("selected");
        for (String key : sel.getAllKeys()) {
            try {
                data.selected.put(UUID.fromString(key), Math.floorMod(sel.getInt(key), TimeUtil.PRESET_NAMES.length));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag sel = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : selected.entrySet()) {
            sel.putInt(e.getKey().toString(), e.getValue());
        }
        tag.put("selected", sel);
        return tag;
    }

    public int get(UUID id) {
        return selected.getOrDefault(id, DEFAULT);
    }

    public void set(UUID id, int value) {
        selected.put(id, Math.floorMod(value, TimeUtil.PRESET_NAMES.length));
        setDirty();
    }
}
