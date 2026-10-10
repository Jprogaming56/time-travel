package com.personal.timetravel;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-of-day maths. Minecraft's day is 24000 ticks and tick 0 is 06:00, so noon is 6000 and midnight is 18000.
 * Deliberately has no Minecraft imports so it can be tested on its own.
 */
public final class TimeUtil {
    public static final int DAY = 24000;

    /** The Time Dial's eight settings, in the order the block cycles through them. */
    public static final String[] PRESET_NAMES = {
            "Sunrise", "Morning", "Noon", "Afternoon", "Sunset", "Evening", "Midnight", "Pre-dawn"};
    public static final int[] PRESET_HOURS = {6, 9, 12, 15, 18, 21, 0, 3};

    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$");
    private static final Pattern TICKS = Pattern.compile("^(?:t|ticks?\\s*)(\\d{1,5})$|^(\\d{1,5})\\s*t$");

    private TimeUtil() {}

    public static int presetTicks(int index) {
        return clockToTicks(PRESET_HOURS[Math.floorMod(index, PRESET_HOURS.length)], 0);
    }

    public static int clockToTicks(int hour, int minute) {
        int ticks = hour * 1000 + (int) Math.round(minute * 1000 / 60.0) + 18000;
        return Math.floorMod(ticks, DAY);
    }

    /** "18:00" style text for a tick value. */
    public static String clockText(long ticks) {
        long total = Math.floorMod(ticks + 6000L, (long) DAY);
        long hour = total / 1000;
        long minute = Math.round((total % 1000) * 60 / 1000.0);
        if (minute == 60) {
            minute = 0;
            hour = (hour + 1) % 24;
        }
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /** "18:00 (Sunset)" for dial settings, plain "18:00" otherwise. */
    public static String describe(int ticks) {
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            if (presetTicks(i) == ticks) return clockText(ticks) + " (" + PRESET_NAMES[i] + ")";
        }
        return clockText(ticks);
    }

    /**
     * Understands "18:30", "6pm", "6:15 am", "noon", "sunset", "t12000" and "12000t".
     * Returns the tick of day (0-23999), or -1 if the text isn't a time.
     */
    public static int parse(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        if (s.isEmpty()) return -1;

        switch (s) {
            case "dawn": return clockToTicks(5, 0);
            case "sunrise": return clockToTicks(6, 0);
            case "morning": return clockToTicks(9, 0);
            case "noon": case "midday": return clockToTicks(12, 0);
            case "afternoon": return clockToTicks(15, 0);
            case "sunset": return clockToTicks(18, 0);
            case "dusk": return clockToTicks(18, 30);
            case "evening": return clockToTicks(21, 0);
            case "night": return clockToTicks(22, 0);
            case "midnight": return clockToTicks(0, 0);
            case "pre dawn": case "predawn": return clockToTicks(3, 0);
            default: break;
        }

        Matcher t = TICKS.matcher(s);
        if (t.matches()) {
            String digits = t.group(1) != null ? t.group(1) : t.group(2);
            int v = Integer.parseInt(digits);
            return v < DAY ? v : -1;
        }

        Matcher m = CLOCK.matcher(s);
        if (!m.matches()) return -1;
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ampm = m.group(3);
        if (minute > 59) return -1;
        if (ampm != null) {
            if (hour < 1 || hour > 12) return -1;
            hour = hour % 12 + (ampm.equals("pm") ? 12 : 0);
        } else if (hour > 23) {
            return -1;
        }
        return clockToTicks(hour, minute);
    }

    /** How many ticks forward from `currentDayTime` until the clock next shows `target` (0 if it already does). */
    public static long ticksUntil(long currentDayTime, int target) {
        return Math.floorMod(target - currentDayTime, (long) DAY);
    }
}
