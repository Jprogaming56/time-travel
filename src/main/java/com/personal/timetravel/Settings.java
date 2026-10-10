package com.personal.timetravel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny JSON settings file: config/timetravel.json */
public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("timetravel.json");

    /** How many day-ticks the clock advances per server tick while the TARDIS is in flight. */
    public int rampTicksPerTick = 150;
    /** Rematerialise by itself once the chosen time is reached (otherwise you land it yourself). */
    public boolean autoLand = false;

    private static Settings INSTANCE = new Settings();

    public static Settings get() { return INSTANCE; }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE)) {
                Settings s = GSON.fromJson(r, Settings.class);
                if (s != null) INSTANCE = s;
            } catch (Exception ignored) { }
        }
        if (INSTANCE.rampTicksPerTick < 1) INSTANCE.rampTicksPerTick = 150;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException ignored) { }
    }
}
