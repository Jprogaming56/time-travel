# AIT Time Travel (Forge 1.20.1) - Monitor edition

Set a "land at" time of day on AIT's Monitor, then fly as normal with the throttle.

- Open the Monitor. Under it there is a row: `[-]  Land at HH:MM  [+]  [Off]`.
  `+` / `-` change the time by 1 hour (hold Shift for 15 minutes). `Off` clears it.
- Take off with the throttle as usual. While the TARDIS is in flight, the day clock of the destination
  world speeds toward the chosen time. When you land, the clock is set exactly to it.
- The setting is used once, then cleared.
- `config/timetravel.json`: `rampTicksPerTick` (how fast the sky races) and `autoLand`
  (true = the TARDIS lands by itself once the time is reached; default false).

## Plants keep growing
Skipping the clock doesn't run any game ticks, so normally crops and trees would not grow. This mod gives every
growing block (crops, stems, saplings, sugar cane, cactus, kelp, vines, bamboo, cocoa, nether wart, berries,
mushrooms, chorus) the random ticks it would have had over the skipped time.

- Chunks that are loaded during the trip are caught up while the sky spins.
- Chunks that are not loaded (e.g. the world around a TARDIS that is in the vortex) are caught up the next time they
  load. Each saved chunk is stamped with the total skipped time; on load the difference is applied.
- `config/timetravel.json`: `growPlants` (default true) and `catchUpUnloadedChunks` (default true).
- Growth rules still apply at catch-up time (crops need light, saplings need room, ...).

## Build
1. JDK 17 and Gradle 8.x.
2. Put these in `libs/` (compile-only, never bundled):
   - From your `aitforger-1_0_4_1_2_12.jar` (open as a zip): `META-INF/jarjar/remapped.jar` -> `libs/ait-remapped.jar`
   - If javac complains about missing `dev.amble.lib...` / Fabric classes, also the AmbleKit jar and any
     Fabric shim jars from your mods folder.
3. `gradle build` -> `build/libs/time-travel-1.0.0.jar` -> your Prism instance's `mods` folder.
