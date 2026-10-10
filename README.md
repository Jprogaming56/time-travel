# AiT Time Travel
An Adventures in Time add-on (Minecraft 1.20.1, Forge). Pick a time of day, take off, and the sky races
to that time before the TARDIS lands again where it left.

## Time Dial block
Place it inside your TARDIS.

- **Right-click**: next time of day. The block changes colour and the action bar shows the time.
  Sunrise 06:00, Morning 09:00, Noon 12:00, Afternoon 15:00, Sunset 18:00, Evening 21:00, Midnight 00:00, Pre-dawn 03:00.
- **Sneak + right-click**: go. The doors shut, the TARDIS takes off, the sky spins forward to the chosen time, and it lands again.

Recipe: gold ingots, redstone, a clock and an ender pearl (shaped, in the redstone tab).

## Console button
One of the console's own buttons becomes the time travel button (no block needed). Default is **Protocol 813** (Hail Mary),
which is on every AiT console. While it is set, that button no longer does its old job.

- **Right-click**: next time of day. **Left-click**: previous time of day.
- **Sneak + right-click**: go.

Pick a different button with `consoleButton` in `config/aittimetravel-common.toml` (for example `ait:fast_return`),
or leave it empty to turn this off. Each TARDIS remembers its own setting. AiT's repair tool and control editor still work on it.

## Exact times
`/timetravel 18:30`, also `6pm`, `6:15 am`, `noon`, `sunset`, `midnight`, `t12000` (raw ticks).
Run it from inside the TARDIS.

## Notes
- Time only moves forward: choosing 09:00 at 10:00 spins through the night to the next morning.
- If the TARDIS is already in flight, it only spins the sky and leaves the landing to you.
- The destination you had set before is put back after landing.
- Doesn't work where the dimension has fixed time (Nether, End).
- Settings are in `config/aittimetravel-common.toml`: which console button, sky speed, minimum time in the vortex, door timeout, clear weather.
- If the server stops mid-trip the TARDIS just stays in the vortex.
