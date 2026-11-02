> Historical design/asset notes. Current authoring and tuning use [CONTENT_EDITING.md](../CONTENT_EDITING.md); Java tuning locations below describe earlier releases.

# v0.3.2 — Continuous Shinobi Castle

VersionName **0.3.2**, versionCode **7**. Native Android Java/Canvas; no new engine, Kotlin, runtime dependency or downloaded artwork.

## World and routes

The production world is **2,880 × 1,080 logical pixels**, rendered through the existing **960 × 540** viewport. Ground is y=940. Training Grounds occupies x=0–960, Upper Courtyard x=960–1920, Castle Approach x=1920–2880. Crossing either regional boundary is ordinary movement: there are no loads, entrance spawns, timers or teleports. All five enemies belong to one persistent `AreaState` for the run, even off-screen.

There are 15 elevated floor/bridge sections and 14 ladders above a continuous lower route. Training rises through y=812, 668, 476 and 284. Courtyard rises through 796, 604, 412 and 220. Castle rises through 812, 620, 428 and 236. Wooden scaffolding, stone backing, pillars, banners and torches support the architecture. Bridges connect Training/Courtyard at y=668 and Courtyard/Castle at y=604 and 428. The latter meets a 16-pixel higher courtyard floor, reachable by a normal jump. Every required reward route can be climbed without a precision jump.

Initial enemy positions: Training x=670 on ground; Courtyard x=1450 at y=796; Castle x=2040 on ground, x=2460 at y=812, x=2660 at y=620. Health, posture, facing, AI, animations, move sequences and cooldowns stay independent. Death persists through backtracking; only RESET restores enemies. Reaching the far-right ground-level gate completes the run. Rewards and kills are optional; there is no fourth area.

Edit `WorldDefinition.java` to tune authored geometry, connections, spawns, world dimensions or reward positions. `GameWorld.WIDTH/HEIGHT` now describe the logical viewport and legacy test arena, not production world boundaries.

## Camera and coordinates

`Camera` is Android independent and read-only with respect to actors. It follows the player in both axes using horizontal/vertical dead-zone half-extents **120/65 pixels** and exponential smoothing `1-exp(-10*dt)`. Bounds are x=0–1920 and y=0–540. The ground and upper roofs remain inside the world at both clamps. Backtracking uses the same smoothing; only a new run resets the camera directly to spawn.

`GameScreen` applies camera translation only to world scenery, actors, collectibles, effects and debug bodies. It restores the transform before drawing HUD, notifications, RESET and touch buttons. Input conversion uses only viewport scale/letterbox offsets, never the camera. The camera advances from processed simulation time and freezes with the confirmation overlay.

## Solid floors and ladders

Every production floor is solid from above, below and the sides. Upper slabs are 32 pixels thick; the ground fills through the bottom world boundary. `AreaDefinition` splits slabs around explicit **112-pixel-wide ladder apertures**, and `SolidPhysics` sweeps horizontal and vertical AABBs against the resulting solid pieces. Upward contact stops at a ceiling; downward contact lands; dash still hits walls and floors. These collision bounds never depend on sprite padding. There are no production one-way floors or drop-through shortcuts. Legacy one-way behavior remains only in historical screen-world tests.

Functional ladder artwork is **80 pixels wide**, uniformly scaled from the committed ladder atlas, with clear rungs/rails and clipping at endpoints. Apertures fit both 36×64 player and 44×70 enemy collision bodies with clearance. No decorative ladder is presented as an interactable.

Release horizontal input and hold UP/DOWN near a ladder. Entry aligns gradually, with no frame-sized snap. Release vertical input to hold position. At the top, hold LEFT/RIGHT to pull over the lip onto solid flooring; this handoff holds feet on the floor plane while you cross the aperture. To descend, approach the opening and hold DOWN with horizontal input released. DOWN on an ordinary floor does nothing. JUMP or DASH can leave a ladder, retaining normal collision/gravity. Neither moving horizontally nor changing facing automatically drifts the character along a ladder.

## Ladder deadlock diagnosis and resolution

The previous system could clamp an enemy against another actor while its navigator kept the same committed traversal. The ground occupant had no instruction to clear the landing. There was no movement arbitration or expiry for that blocked commitment, so proximity could become a permanent mutual wait.

`LadderTraffic` now detects a climbing arrival near a ladder's bottom and gives a grounded occupant a deterministic safe yield target outside the shaft. The offset is **120 pixels** from the ladder center, left/right selected stably by instance order and validated against terrain and other occupants. Yielding is bounded to **1.5 seconds**, does not refresh every frame, and lets an already-active attack finish. Two-axis enemy body sweeps stop actual overlap. Navigation resumes after clearing the exit. An otherwise stalled committed route is replanned after **2.5 seconds**. Top exits choose their side from the next route connection, rather than blindly stepping toward the distant player and falling back through the same hole.

Surface-graph BFS supports ladder travel, connected walks and physical jumps. Aperture crossings on a shared floor use a real jump; terrain is never disabled for AI. Navigation jumps retain the existing -590 impulse and 250 px/s traversal speed; ordinary enemy pursuit remains 110 px/s. Detection remains configurable at 340 pixels by default. The exact two-enemy ground-occupant/descending-arrival scenario is tested for eventual descent, continued movement/combat and no overlap. Tests also route an enemy to every authored elevated floor.

This is bounded local arbitration, not advanced crowd planning or path reservation. A landing with no valid free yield space may still require actors/the player to move before passage is possible.

## Run upgrades

| Reward | Position (center x, platform feet y) | Effect |
| --- | --- | --- |
| Training glow | 300, 284 | Base maximum HP +50; immediately heal to the new maximum |
| Courtyard glow | 1530, 220 | Attack multiplier becomes exactly 2 |

Each pickup has its own run-owned collected flag, disappears immediately and shows a three-second notification. Repeated overlap and backtracking cannot stack or heal again. The HP calculation uses the constructor's configured base maximum, not a hardcoded 100. Example: base 400 → 450, RESET → 400.

The power multiplier applies **once** to normal player weapon-hit health damage **and** posture damage: baseline 25/25 → 50/50. `Player.tryHit` reads `healthDamage()`/`postureDamage()` once for its existing committed single-target hit. Deflection posture transfer remains 35 (70 against heavy); enemy damage, regular-block posture, heavy multipliers and deathblow execution are unchanged. The bonus does not multiply an enemy attack or defensive transfer. A successful deathblow still defeats the eligible enemy directly.

Upgrades last through regional movement/backtracking until the run ends. Completion freezes combat; RESET clears both flags, restores original maximum HP/attack power, restores collectibles and all five enemies, resets movement/guard/posture/timers/notifications, and resets the camera. CANCEL preserves the run. Existing touch isolation behind the modal remains active.

## Combat and model tuning

- **Player movement: 250 → 180 px/s** (28% reduction). The elapsed-time probe still advances 10 simulation seconds for 10 seconds at simulated 5–60 FPS. No FPS/tick manipulation is used to slow movement.
- **Deflection window: 250 → 320 ms**, inclusive boundary. Failed short taps still subtract 50 ms down to a 50 ms floor, recover after 0.8 seconds, and reset their penalty on success. Holding GUARD never refreshes its press timestamp and continues to block reliably. Global simulation rate and enemy attack timing are unchanged.
- Dash remains 950 px/s for 180 ms, 100 ms centered invulnerability, 450 ms initiation cooldown. Existing attack/stagger cancellation and deathblow lock remain.
- **Ninja uniform render scale: 3.4**; **Samurai: 8.2**. The inspected idle stance/arm bands are 24 and 10 source pixels wide, yielding approximately **81.6/82 pixels**. The ninja's extended sword makes its whole opaque frame wider; matching that weapon-inclusive bounding box would leave the body too narrow. Uniform scaling preserves proportions and feet anchors. The previous enlarged art is retained; no source files were changed.
- Physical bodies remain 36×64/44×70, and attack reach/hitboxes are unchanged. Sword trails and art can extend beyond physics; optional body/hitbox overlays expose the distinction. Placeholder clips still reuse Run for climbing and Hurt for stagger.

Tuning locations: `Player.java`, `CombatConfig.DEFAULT`, `SpriteClip.NINJA_RENDER_SCALE/SAMURAI_RENDER_SCALE`, and `WorldDefinition.LADDER_WIDTH/OPENING_WIDTH`.

## Rendering and timing

The hotfix's fixed **120 Hz** clock, **32-tick catch-up budget**, **500 ms maximum backlog**, explicit overflow counter and debug timing logs remain. `adb logcat -s LOCK-IN-Timing` reports actual frame/update/draw+Surface costs and simulation/wall ratio in a debug APK.

The world uses **12 cached 480×540 ARGB chunks**, totalling **12,441,600 bytes (~11.86 MiB)**. No full-world bitmap is allocated. A viewport intersects at most six chunks; with five parallax pieces, background/scenery costs at most **11 draw commands** in host instrumentation. Only visible chunks and nearby actor/effect sprites are rendered. All five AI states still simulate deterministically off-screen. Scrolling and RESET decode no PNGs and create no bitmaps.

Art uses the original DarkNinja, Samurai Fighter/Zen Island, and committed Pixel Platformer Vol.3 atlas. Original assets and license files are untouched. Vol.3 permits game use; its standalone redistribution/resale and AI-training restrictions remain. No replacement pack was downloaded.

## Validation and limits

Run `./tools/test-core.sh` or `./tools/test-core.sh --profile`, then `./gradlew --no-daemon assembleDebug lintDebug`. The tests include historical combat/screens and the production continuous-world suite. Historical screen transitions are available only through the `Game.screenWorld` fixture; Android always uses the continuous constructor.

**1,737 checks pass**, including continuous traversal without teleports, both reward routes and controlled tower descents, upgrade persistence/reset, actual doubled hits, unchanged defensive/heavy transfers, new guard boundaries/anti-spam, 5–60 FPS wall-time behavior, floor ceilings/sides/fast sweeps/dash, ladder openings, every elevated AI destination, the exact ladder deadlock, camera clamps/backtracking, model stance width, cached drawing and completion. Build/lint pass with zero errors and the three existing manifest warnings.

A host steady-state five-enemy benchmark completed 120,000 ticks in ~0.11 seconds (~0.9 µs/tick) in one measurement. This is not an Android/Redmi 12 benchmark. Host renderer doubles instrument commands and transforms, not Android GPU pixels. Physical-device rendering, crowd game feel, touch comfort and frame-time logs remain unverified. There is no disk save, inventory, new enemy type, audio or advanced crowd/path planning. Scene rendering remains Canvas/SurfaceView with sleep pacing; severe overload beyond the fixed catch-up bounds is still reported rather than hidden.
