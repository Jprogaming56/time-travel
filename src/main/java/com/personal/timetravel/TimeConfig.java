package com.personal.timetravel;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings, in config/aittimetravel-common.toml the first time you run the mod.
 * Times in the file are in SECONDS (except the sky speed, which is game ticks of sky time per real tick).
 */
public final class TimeConfig {
    public static final ForgeConfigSpec COMMON;

    private static final ForgeConfigSpec.IntValue SKY_SPEED;
    private static final ForgeConfigSpec.DoubleValue MIN_FLIGHT;
    private static final ForgeConfigSpec.DoubleValue DOOR_TIMEOUT;
    private static final ForgeConfigSpec.BooleanValue CLEAR_WEATHER;
    private static final ForgeConfigSpec.ConfigValue<String> CONSOLE_BUTTON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("timeTravel");
        SKY_SPEED = b.comment(
                "How fast the sky races during the trip, in ticks of sky time per real tick.",
                "24000 ticks is one full day, so 120 spins through a day in 10 seconds. Higher = shorter trip.")
                .defineInRange("skySpeed", 120, 1, 2400);
        MIN_FLIGHT = b.comment("The TARDIS stays in the vortex at least this many seconds, so the take-off and landing sounds don't overlap.")
                .defineInRange("minFlightSeconds", 6.0, 0.0, 120.0);
        DOOR_TIMEOUT = b.comment("If the doors are open, they are shut first. How long to wait for them to finish closing, in seconds.")
                .defineInRange("doorCloseTimeoutSeconds", 5.0, 0.5, 60.0);
        CLEAR_WEATHER = b.comment("Clear any rain or thunder when the trip is over (like sleeping does).")
                .define("clearWeatherOnArrival", false);
        CONSOLE_BUTTON = b.comment(
                "Which console button becomes the time travel button. Right-click = next time of day,",
                "left-click = previous, sneak + right-click = go. Its normal job stops working while this is set.",
                "Use the control's id: ait:protocol_813 (Hail Mary), ait:fast_return, ait:protocol_3 (cloak), ...",
                "Leave empty to turn the console button off (the Time Dial block and /timetravel still work).")
                .define("consoleButton", "ait:protocol_813");
        b.pop();
        COMMON = b.build();
    }

    private TimeConfig() {}

    public static int skySpeed() { return SKY_SPEED.get(); }
    public static long minFlightTicks() { return Math.round(MIN_FLIGHT.get() * 20); }
    public static long doorTimeoutTicks() { return Math.round(DOOR_TIMEOUT.get() * 20); }
    public static boolean clearWeather() { return CLEAR_WEATHER.get(); }
    public static String consoleButton() { return CONSOLE_BUTTON.get().trim(); }
}
