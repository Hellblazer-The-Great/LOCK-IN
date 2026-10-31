# LOCK IN — v0.2.4: Dance of Blades

A minimal Java Android action prototype. One fixed arena, one player and one enemy with basic melee AI. Rendering uses `SurfaceView` / `Canvas`; no Kotlin, game engine, or third-party runtime libraries.

## Open, build, and run

1. Open this directory in Android Studio Meerkat (2024.3.1) or newer, with support for Android Gradle Plugin 8.9.2.
2. Use a JDK 17 or 21 for Gradle (Android Studio's bundled JDK 21 works). Install Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools through SDK Manager; accept their licenses.
3. Allow Gradle sync. Android Studio creates ignored `local.properties` with your SDK path. For command-line use, set `ANDROID_HOME` to your SDK directory instead.
4. Select an Android 6.0 / API 23 or newer device or emulator and press Run. Landscape is requested; the world also letterboxes in other window shapes.

```sh
./gradlew assembleDebug lintDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug
adb shell am start -n com.lockin.game/.MainActivity
```

On Windows use `gradlew.bat`. The Gradle 8.13 wrapper includes a distribution SHA-256 check. First sync needs access to Gradle, Google Maven, and Maven Central.

## Play and verify

Hold LEFT or RIGHT to move; tap JUMP or ATTACK; press/hold the dedicated GUARD button; tap DASH with a held direction, or without a direction to dash backward. Separate fingers can hold movement while tapping either action. Actions trigger on a new press, not continuously while held. Dragging between buttons updates the held state. Releasing/cancelling touches or pausing clears inputs. Opposing movement buttons cancel each other.

Walk toward the enemy and face it. A gold rectangle shows the active strike area. Each strike deals 25 damage at most once, lasts 0.16 seconds, and has a 0.45-second cooldown. Four ordinary hits defeat the default 100 HP enemy; higher maximum health takes more hits. A hit requires both horizontal and vertical overlap. The enemy detects the player within a configurable 340-world-pixel center radius, approaches at 110 pixels/second, and stops at a 30-pixel body gap. Detection includes vertical distance. It faces the player while idle/chasing and commits its facing throughout each move. A lethal player strike resolves first and cancels an enemy strike in that simulation step.

## Dash

DASH is a dedicated button between RIGHT and GUARD. A held horizontal direction chooses the dash direction; neutral input dashes backward without turning the player. The movement is a **180 ms** burst at **950 world pixels/second** (171 pixels unobstructed), with a **450 ms cooldown from initiation**. Invulnerability is centered: **[40 ms, 140 ms)** from dash initiation, inclusive entry and exclusive exit, sampled by the existing fixed 120 Hz combat clock. World/ground/body collision remains active, even during i-frames. Jumping and aerial dashing remain possible; crossing an enemy requires clearing its body height.

Dash cancels an ordinary attack's hitbox and recovery, but never refunds health or posture already committed. Attacks pressed during movement are discarded, not buffered or reactivated. GUARD cannot raise during the movement burst; continuing to hold GUARD resumes regular blocking afterward, with a fresh press required for a new deflect window. Dash can end a posture-break stagger without healing or clearing the posture meter. It grants the existing post-break posture grace once, does not reset dash cooldown, and cannot restart/extend an active dash.

A deathblow has a **300 ms execution lock** and cannot be cancelled by dash, attack, guard, movement or jump. An already-eligible deathblow takes priority over a simultaneous ATTACK/DASH press.

## Fixed enemy moveset

The enemy repeats **Normal Slash → Double Slash → Heavy Overhead**, without random or adaptive selection. Escaping or briefly leaving detection does not reset the sequence. A completed miss still advances the move; a posture break resolves an interrupted move once and cancels remaining strikes. Death/restart resets to normal slash. No attack teleports or snaps the enemy into range.

| Move | Windup | Active | Recovery | Forward reach | Damage |
|---|---|---|---|---|---|
| Normal slash | 500 ms | 180 ms | 700 ms | 64 px | 1× |
| Double slash | 400 ms | 140 ms per strike | 650 ms after strike 2 | 54 px | 1× per strike |
| Heavy overhead | 850 ms | 220 ms | 1000 ms | 48 px | 2× |

Double slash separates its two active events with a **280 ms windup**; each can independently hit, miss, be blocked, be deflected, or be dodged. Moves have an additional **120 ms between-move pause**. Slash hitboxes span enemy-relative y=10..62; overhead spans y=-32..70 and reaches higher targets. All hitboxes are forward-facing and independent of physical bodies.

Unblocked enemy strikes apply baseline **20 HP + 25 posture**; heavy applies **40 HP + 50 posture**. Blocks prevent HP damage and add **25 posture**, or **50** for heavy. Deflections prevent HP damage and add **35 attacker posture**, or **70** for heavy, with no defender posture damage. Ordinary player strikes apply **25 HP + 25 enemy posture**. Both components of a living hit are committed even when health damage is lethal; later contacts on corpses do nothing. Each individual strike resolves once. A dash-invulnerable contact consumes that strike with no health, posture, block or deflect effect, so a lingering hitbox cannot catch the same dodge as i-frames expire.

Enemy indicators: idle pink, chase orange, windup yellow, active red, recovery purple. Heavy windup uses a taller orange marker; double slash labels strike 1/2 separately. Dash uses a cyan trail and a white body during i-frames. Exact enemy hitboxes can be enabled with `GameScreen.DEBUG_HITBOXES`; they are hidden by default. The HUD labels the current state and shows both health bars. Dead enemies disappear and stop attacking. `Enemy.detectionRadius` controls detection; zero disables it for stationary practice/regression tests.

## Guard, deflection, and posture

GUARD raises immediately on its press edge. Enemy hitbox contact within the initial **250 ms (inclusive)** deflects: no health damage, +35 enemy posture. Holding longer blocks: no health damage, +25 player posture. Releasing lowers guard. A held button never refreshes timing. Missing the body or merely entering the active state produces no impact; each swing can resolve at most once.

Unsuccessful short taps reduce the next window by **50 ms**, down to **50 ms**. Success resets the penalty and restores the full window without resetting the original press timestamp. The penalty clears after **0.8 seconds** without another unsuccessful tap. A hold lasting at least 0.8 seconds is not classified as a failed tap. There is no guard input cooldown or activation delay. Successful rapid deflections remain unpenalized. Input snapshots count every press, even several completed taps between rendered frames.

Guard remains independent of facing and can be held while moving, jumping, or attacking, except while staggered, dashing, or executing a deathblow. The dedicated GUARD button is left of JUMP; other button positions are unchanged. Any remaining finger on GUARD keeps it held. Pointer lifts, drags, cancellation and lifecycle clearing do not create extra press edges.

Both posture meters have capacity **100**, recovering **12 units/second** after **one second** without combat contact. Blocks and unblocked enemy hits add player posture; deflections and unblocked player hits add enemy posture. A successful deflection never adds defender posture. At full posture:

- The player staggers for **one second**: guard, movement, jump and attacks are suppressed, while gravity and incoming health/posture damage remain active. Additional impacts cannot extend this timer. Holding GUARD through recovery resumes a regular block, without granting a fresh deflect window. DASH can escape this stagger while retaining committed HP/posture and its own cooldown.
- The enemy stops moving and attacking for **one second**, with a pale body, red marker and shrinking opportunity bar. A fresh **ATTACK** press executes a deathblow when facing the enemy, within a **90-pixel body gap**, with vertical body overlap. This defeats the standard enemy regardless of remaining HP and overrides ordinary attack cooldown. Existing active attacks do not automatically become deathblows. If the window expires, the enemy resumes through attack recovery.

After either break, posture resets to zero and **one second of posture immunity** prevents immediate break loops; this does not grant health invulnerability. Ordinary health-based enemy defeat still works. Restart clears guard, penalties, feedback, posture, stagger, immunity, dash/execution timers, queued actions, enemy sequence and the fixed-step accumulator.

`CombatConfig` exposes the initial/minimum windows, penalty step, tap recovery time, posture capacity/recovery, block/deflect posture damage, quiet delay, break duration, immunity and deathblow range. Its original seven-argument constructor remains supported; the extended constructor provides all tuning. `new Game(config, playerMaxHealth, enemyMaxHealth)` sets health maxima, or edit the actors' `DEFAULT_MAX_HEALTH` constants for default gameplay. Maxima are reused on spawn and every restart, not hardcoded to 100. Default maximum health remains 100 for both actors.

Every health bar uses `clamp(current / maximum, 0, 1)` and a fixed width: 200 virtual pixels in the HUD and 44 above the enemy. Posture bars also use normalized fractions. Invalid/zero maxima and non-finite values render empty; negative HP renders empty and overhealing renders full. HP text remains numeric.

Feedback: DEFLECT is yellow with contact rays; BLOCK cyan; HIT red; player POSTURE_BREAK magenta with a persistent STAGGERED label; DEATHBLOW orange-red with a longer burst. The HUD prompts `ATTACK: DEATHBLOW!` only when eligible. Existing enemy windup/active/recovery colors remain unchanged.

## Tuning locations

- `Player.java`: `DAMAGE`, `POSTURE_DAMAGE`, `DEFAULT_MAX_HEALTH`, movement/jump/attack constants, `DASH_DURATION`, `DASH_IFRAMES`, `DASH_COOLDOWN`, `DASH_SPEED`, `DEATHBLOW_DURATION`. I-frame start/end derive from duration and iframe length.
- `Enemy.java`: `DAMAGE`, `POSTURE_DAMAGE`, `DEFAULT_MAX_HEALTH`, detection/chase/engagement constants. Its baseline attack constants feed normal slash.
- `EnemyMove.java`: move windup/active/recovery, strike count, multiplier, between-strike delay, reach/top/height and `BETWEEN_MOVES`.
- `CombatConfig.java`: regular block/deflect posture transfers, posture/recovery/break tuning, deathblow range, and anti-spam values.

## Solid body collision

Living bodies are separate from attack hitboxes. Player movement sweeps against the stationary enemy, then enemy pursuit sweeps against the player's resolved position without pushing the player. Vertical sweep intervals prevent diagonal tunneling; fixed 120 Hz ticks and the 50 ms stall cap are retained. An initial overlap or descent into a body places the player on the nearest valid side within world bounds. Jumping completely above the enemy permits crossing and landing on the other side; the enemy is not a standable platform. Corpses do not obstruct. Touching does not change facing or prevent melee contact.

Manual device checklist (requires a real device or emulator):

- Hold movement and tap jump/attack with another finger; lift either finger first and verify no stuck movement.
- Hold GUARD plus movement, jump, and attack with separate fingers. Lift guard first, then repeat lifting other fingers first; verify no stuck guard or refreshed window. Drag off GUARD, cancel touches, and pause/resume.
- Test DASH while holding LEFT/RIGHT/GUARD with other fingers; lift each finger first in separate trials. Verify neutral backdash, cooldown, attack/stagger cancellation, no body clipping and no deathblow cancellation.
- Observe normal → double → heavy sequence. Deflect both double strikes, block heavy, escape a windup and check a natural miss, then leave/re-enter range to check sequence continuity.
- Spam short GUARD taps and verify a shrinking deflect window but immediate block. Land rapid deflections to clear the penalty; stop tapping to recover.
- Break both posture meters: verify player stagger/grace, enemy attack cancellation, range/facing deathblow prompt, opportunity expiry, and ATTACK execution. Verify only one effect per swing.
- Increase actor maxima to 400 and 10,000; check HUD and overhead bars at full/half health, death, and restart.
- Walk into both enemy sides, move away, jump over it, and walk through its corpse; check no overlap, pushing, jitter or clipping.
- Jump lands on the platform, cannot double jump, and movement stops at both arena edges.
- Attacks miss when out of range, facing away, or above the target; one swing never causes multiple damage ticks.
- Verify both enemy facing directions, avoid an attack by moving out of range or jumping during windup, observe recovery, lose all health, and restart. Confirm a held JUMP cannot auto-restart.
- Defeat the enemy, then background/foreground and lock/unlock during movement. Verify paused simulation, cleared input, and one resumed rendering loop.
- Check a wide phone and tablet/windowed display: proportional shapes, letterboxing, safe controls clear of system bars/cutouts, unchanged world speeds.

At zero player health, combat freezes and a defeat overlay appears. Release JUMP and tap its relabeled RESTART button to reset both characters, attack timers, and enemy state. This uses the existing queued press edge; holding JUMP does not automatically restart. Force-stop and relaunch also resets the prototype. State survives pause/resume within the same Activity, but is intentionally not persisted across Activity/process recreation.

## Automated core checks

With a full JDK 17+ installed:

```sh
./tools/test-core.sh
```

This compiles the production simulation directly and checks boundaries, jump/landing, air-jump rejection, frame-rate consistency, range/direction/height misses, per-strike damage, cooldown, defeat, health clamping, and invalid delta time. It also runs deterministic enemy checks for configurable detection, both chase directions, stop distance, attack durations, locked facing, vertical/range misses, one-hit damage, recovery cooldown, lethal cancellation, player defeat, and restart. The original 15 Phase 1 checks run with detection disabled to isolate player regressions, and the boundary-only scenario removes the enemy so the new solid body does not obstruct it. Guard checks cover exact timing boundaries, held/released guards, contact-time misses, one-effect-per-swing, tuning, posture accumulation/recovery/break, reset, and frame-rate consistency. Refinement checks cover tap penalties/recovery, rapid successful deflections, stagger/grace timing, deathblow eligibility/expiry/input edges, normalized health at 100/400/10,000 maxima and restart. Collision checks cover both sides, enemy pursuit, overlap at walls, jump-over/diagonal sweeps, corpses, touching melee, spikes and multiple render rates. Control checks cover simultaneous buttons and queued edges. The actual `InputHandler` adapter is also tested with a minimal JVM-only `MotionEvent` test double for pointer lifts, multiple guard fingers, drags, cancellation, and coordinate transforms. The test double is outside the app source tree and is not packaged. Dash/moveset checks additionally cover exact i-frame boundaries, cancellation/committed damage, stagger escapes, cooldown exploits, air/body collision, independent double events, heavy multipliers, misses/death and fixed sequence/FPS behavior. No Android SDK is required for these checks; actual Android touch delivery and rendering still need a device.

## Architecture and units

- `Game`: simulation update and combat coordination; clamps stall recovery to 50 ms and advances fixed 1/120-second ticks, retaining action edges across shorter render frames.
- `Player`: position, velocity, grounded/facing state, health, attack timing and per-strike hit state.
- `Enemy` / `EnemyMove`: Android-independent IDLE / CHASE / ATTACK_WINDUP / ATTACK_ACTIVE / ATTACK_RECOVERY / POSTURE_BROKEN / DEAD state machine, movement, facing, attack timing and damage.
- `GameWorld`: fixed 960 × 540 logical world; origin top left, +x right, +y down, distances in logical pixels and speeds in logical pixels/second. Ground is y=380.
- `CollisionManager`: world/ground resolution and axis-aligned hitbox overlap. Solid body sweeps are independent of attack hitboxes; living actors cannot pass through each other at overlapping heights.
- `CombatConfig` / `Posture`: configurable, Android-independent posture and recovery/break timers.
- `Controls`: synchronized held-button masks and queued press edges; shared render/input button bounds.
- `InputHandler`: Android MotionEvent adapter, aggregating all non-lifted pointers into `Controls`. Button coordinates share the world transform.
- `GameScreen`: drawing, touch-coordinate conversion, Surface lifecycle and a paced rendering thread capped around 60 FPS. Updates precede rendering; simulation and drawing share one thread.
- `MainActivity`: entry point, safe system-bar insets and lifecycle forwarding.

Simulation, posture, and button-state logic have no Android dependencies. There is no inheritance-based entity framework. The renderer scales uniformly into the available safe area, adding black letterboxing. Gravity and combat use world coordinates and seconds, independent of display density.

## Limits and next steps

This is a placeholder prototype: no sprites, audio, scrolling, menus, persistence, or additional levels. It supports one enemy, directional melee and dashing; it does not use stamina or adaptive AI. There is no knockback or enemy body platform. Enemies do not jump or navigate platforms. Very long frame stalls drop elapsed simulation time to prevent tunnelling or sudden jumps. Rendering is paced with sleep rather than display-vsync scheduling.

## Verification in this cloud workspace

Run `./tools/test-core.sh` and `./gradlew assembleDebug lintDebug` for current results. The cloud machine uses `/workspace/toolchains/jdk-21.0.6+7` as `JAVA_HOME`, `/workspace/android-sdk` as `ANDROID_HOME`, and `/workspace/.gradle` as `GRADLE_USER_HOME`. Source `/workspace/setup/lock-in-env.sh` to activate its tooling, writable Android state, proxy and system trust settings. These are local tooling paths, not required project settings.

No Android device/emulator is connected. On-device gameplay, multitouch, state colors, restart interaction, and lifecycle checks require the manual device checklist above.
