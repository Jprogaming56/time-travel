# AIT Time Travel (Forge 1.20.1) - Monitor edition

Set a "land at" time of day on AIT's Monitor, then fly as normal with the throttle.

- Open the Monitor. Under it there is a row: `[-]  Land at HH:MM  [+]  [Off]`.
  `+` / `-` change the time by 1 hour (hold Shift for 15 minutes). `Off` clears it.
- Take off with the throttle as usual. While the TARDIS is in flight, the day clock of the destination
  world speeds toward the chosen time. When you land, the clock is set exactly to it.
- The setting is used once, then cleared.
- `config/timetravel.json`: `rampTicksPerTick` (how fast the sky races) and `autoLand`
  (true = the TARDIS lands by itself once the time is reached; default false).

## Build
1. JDK 17 and Gradle 8.x.
2. Put these in `libs/` (compile-only, never bundled):
   - From your `aitforger-1_0_4_1_2_12.jar` (open as a zip): `META-INF/jarjar/remapped.jar` -> `libs/ait-remapped.jar`
   - If javac complains about missing `dev.amble.lib...` / Fabric classes, also the AmbleKit jar and any
     Fabric shim jars from your mods folder.
3. `gradle build` -> `build/libs/time-travel-1.0.0.jar` -> your Prism instance's `mods` folder.
