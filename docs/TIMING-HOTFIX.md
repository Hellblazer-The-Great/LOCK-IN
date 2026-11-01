# v0.3.1 hotfix — real-time simulation and larger characters

Base: v0.3.1 commit `15c3064` (merged main `a27fa03`). APK keeps versionName **0.3.1**, increments versionCode to **6**.

## Confirmed cause

`Game.update` previously used `accumulator += min(dt, .05)`. At less than 20 rendered frames/second, every frame discarded part of its elapsed wall time. The fixed-step loop itself used correct 1/120-second units, but never received enough elapsed time. `CharacterAnimations` independently applied the same 50 ms clamp, slowing free-running animations too. This affects movement, gravity, attack phases, guard/posture, cooldowns and animation together, matching whole-game slow motion.

The loop already uses monotonic `System.nanoTime()`, seconds, one lifecycle-owned render thread, and sleeps only for the remainder of a 60 Hz frame. Those were inspected and retained. Movement speed, jump impulse, gravity, dash/i-frame durations, enemy damage, attack timing and deflection values were not multiplied or rebalanced.

The terrain renderer also issued hundreds of static tile draws every frame, including an old ground layer immediately covered by the new floor. This is a verified workload increase, **not a measured diagnosis of a particular phone's bottleneck**. No Android device/emulator is available here. On-device frame/render time must be collected using the new debug telemetry below.

## Before/after measurements

Run `./tools/test-core.sh --profile` to reproduce the current measurements and host benchmark.

A deterministic host probe supplied 10 seconds of elapsed render-frame time through the production update path. Movement-derived simulated seconds are total unobstructed distance divided by the unchanged 250 px/s speed; the actor is repositioned between samples solely to avoid reaching the world boundary. These are simulated rendering rates, not device FPS measurements.

| Supplied render FPS | Before: sim seconds / 10 seconds | After | Before ticks/second | After |
| --- | ---: | ---: | ---: | ---: |
| 60 | 10.000 | 10.000 | 120 | 120 |
| 30 | 10.000 | 10.000 | 120 | 120 |
| 20 | 10.000 | 10.000 | 120 | 120 |
| 15 | 7.500 | 10.000 | 90 | 120 |
| 10 | 5.000 | 10.000 | 60 | 120 |
| 5 | 2.500 | 10.000 | 30 | 120 |

Idle-animation sampling after 10 seconds was also wrong at slow rates (before frames 2/4/6 at 15/10/5 FPS); afterward all rates end at frame 0, the correct result for 120 frames on an 8-frame loop. Focused tests separately check actual phase progress, damage, cooldowns, jump landing, dash distance and free-running animation across all six rates.

Instrumenting actual `SpritePainter` commands with host Android API doubles gives:

| Area | Background/terrain draw commands before | After |
| --- | ---: | ---: |
| Training Grounds | 204 | 6 |
| Upper Courtyard | 300 | 6 |
| Castle Approach | 458 | 6 |

Static scenery is composited once into three 960×540 ARGB bitmaps (~5.93 MiB total). Each frame draws the five original parallax background pieces and one cached scenery layer. Terrain, ladder and platform geometry/appearance are retained. PNG decoding and bitmap creation do not occur on drawing or area transitions. Host command doubles count calls and validate transforms; they do **not** emulate Android raster/GPU timing.

A warmed desktop JVM steady-state three-enemy benchmark processed 120,000 fixed ticks in roughly 0.05–0.08 seconds (~0.4–0.6 µs/tick), both before and after. This identifies no host simulation overload and is not a phone-performance claim or a general worst-case benchmark.

## Clock and overload handling

- Simulation remains fixed at 120 Hz. All normal frame elapsed time enters the accumulator.
- Each update executes at most **32 ticks** (~267 ms) to bound work per rendered frame. Unfinished work remains in the accumulator, including through area transitions.
- Backlog is bounded to **500 ms**. Only overflow from pathological stalls is discarded; `Game.droppedSeconds()` explicitly counts it. Ordinary simulated 5–60 FPS workloads discard nothing. This is not a guarantee under arbitrarily slow rendering or CPU overload.
- Lifecycle resume starts a fresh monotonic frame origin; confirmation pauses, cancellation and confirmed reset do not replay paused elapsed time. Reset clears counters/backlog.
- Rendering advances free-running animations by the simulation time actually processed. Combat animations still sample authoritative phase progress. Death/completion presentation may finish its one-shot after simulation stops. Animation controllers no longer each re-update the same player for every enemy.

Debug APKs log a five-second window under **LOCK-IN-Timing**:

```sh
adb logcat -s LOCK-IN-Timing
```

Fields: measured FPS, mean/worst frame milliseconds, fixed ticks/second, simulated/wall-time ratio, update cost, draw plus Surface lock/post cost, remaining backlog and discarded time. In normal running expect ~120 ticks/s and sim/real ~1.000, within fractional-tick rounding. Long draw+surface cost indicates rendering/Surface blocking; high update cost indicates simulation work. Paused modal/defeat/completion windows are excluded. Release builds do not emit timing logs. The logging string is created only once per reporting window, not every frame.

## Render sizing

`SpriteClip.RENDER_SCALE_MULTIPLIER` is **1.5**. Final DarkNinja source-pixel scale is **2.4** (was 1.6); final Samurai scale is **7.5** (was 5). All clips, including return slash and heavy, use uniform x/y scaling with nearest filtering. Source frame dimensions/counts, feet anchors, horizontal flipping and phase selection are unchanged. Sprite padding still never defines collision.

Player body remains 36×64, enemy body 44×70; attack reach/hitboxes and balance remain unchanged. Enlarged art can extend beyond physical bodies, especially sword trails; optional body/hitbox debug drawing makes that distinction visible. Overhead enemy meters now sit above the enlarged model. High-platform windup cues move beside the model below the top HUD, preserving readable double/heavy telegraphs.

## Validation and remaining checks

**1,516 checks pass** via `./tools/test-core.sh`, including 156 new real-time/scale/telemetry checks and 117 renderer command/cache/anchor checks. The full prior world, reset, combat and animation tests remain enabled. `tools/test-render.sh` can also run independently; its test doubles are outside app sources and are never packaged.

`./gradlew --no-daemon assembleDebug lintDebug` passes; lint reports zero errors and the three existing manifest warnings. No Kotlin, game engine or runtime dependency was added. No original art was replaced.

No physical Android device was tested. GPU/software Canvas throughput, control comfort, visual overlap at all poses and actual phone frame-time logs still require local testing. Severe sustained overload can exceed the bounded catch-up budget; diagnostics expose that rather than promising unlimited recovery. The cached terrain trades ~6 MiB of memory for fewer draw commands.
