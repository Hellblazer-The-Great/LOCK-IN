# Editing LOCK-IN content

The Android game starts from `app/src/main/assets/manifest.json`. It reads the Tiled/JSON pack once, validates references and PNG dimensions, then creates independent runtime actors. Content is bundled in the APK and works offline. **Save, validate, rebuild and reinstall to apply edits.** There is no on-phone import or hot reload.

Java implements the existing mechanics. The files below select and tune supported behavior; new mechanics or unsupported map features still require engine code. Historical Java constants/fixtures are retained for regression tests and do **not** configure the Android game.

## First edit: tougher enemy in a new position

1. Open `app/src/main/assets/levels/test_arena.tmj` in **Tiled**. Keep the map finite and orthogonal; save as JSON (`.tmj`), with uncompressed JSON tile-layer data.
2. Select **Gameplay objects → samurai_1**. Change **X from 670 to 720**, keeping Y at 940. Save. This point is the body's **left edge / feet**, not its center.
3. Open `app/src/main/assets/entities/enemy.json`. Change `"maxHealth": 100` to `"maxHealth": 400`. All spawns whose `entityId` is `samurai` now have 400 HP, including after restart.
4. From the repository root, run:

   ```sh
   ./gradlew validateContent testContent assembleDebug lintDebug
   ```

   On Windows, use `gradlew.bat` instead of `./gradlew`. JDK 17+ and the project's existing Android SDK setup are required. No new game engine or library is needed. Validation also runs automatically before APK builds.
5. Install `app/build/outputs/apk/debug/app-debug.apk`. RESET starts a fresh run with the edited definitions. The health bar stays a fixed width.

The same two edits are exercised by automated tests A and B; tests C and D edit actual attack and platform data, and E checks controls/defeat/reset.

## Which file controls what?

All paths below are relative to `app/src/main/assets/`.

| File | Editable content |
|---|---|
| `manifest.json` | Schema version, registered files, level IDs, starting level |
| `levels/test_arena.tmj` | Platforms, walls, ladders, navigation, enemy/player placement, regions, pickups, exits, tile layers |
| `levels/tilesets/castle.tsj` | PNG atlas layout, tile IDs, `solid` tile property |
| `entities/player.json` | Player HP, posture, body dimensions, movement/jump/gravity, dash/i-frames, attack and animation references |
| `entities/enemy.json` | Enemy HP, posture, body dimensions, movement and behavior reference |
| `combat/defense.json` | Deflect window, anti-spam, break/recovery timing, block/deflect posture multipliers, deathblow range |
| `combat/attacks.json` | Damage, posture transfers, hitbox timelines, recovery, cooldown, animation references and dash cancellation |
| `combat/combos.json` | Enemy move sequence; optional buffered ATTACK follow-ups |
| `animations/player.json`, `animations/enemy.json` | PNG sources, source rectangles/frame sequences, timing, facing, anchors, scaling, events and completion transitions |
| `behaviors/enemy.json` | Detection, engagement, chasing/facing, sequence or weighted selection, between-move and stagger recovery timing |

IDs are case-sensitive. Keep existing IDs when changing a definition. To add a new enemy variant, copy `entities/enemy.json`, change its `id` (for example `samurai_tough`), register the new file in `manifest.json.entities`, then use that ID on a spawn. Copies share immutable attacks/animations/behavior references; every spawn has separate health, posture, timers and AI state.

`maxPosture` and `postureRecovery` are per-entity values. `combatProfile: "shinobi_guard"` references the shared profile. Copy that profile under another ID if the new entity needs different break or defense timings. Enemy AI currently attacks and chases; it does not acquire a guard ability merely by referencing a defense profile.

## Coordinates and map objects

**One Tiled pixel = one simulation pixel.** Origin is the top left; X goes right and Y goes down. Tile cell size does not scale character physics. The camera affects rendering only. The migrated castle is 180 × 68 cells at 16 pixels, or **2880 × 1088** pixels. Its existing surfaces/spawns retain their coordinates; the canvas gained eight bottom pixels to align with the tile grid.

Use Tiled's object **Class/Type** field for the types below. Add explicit custom properties on each object; project-class inherited defaults/templates are not read. Give objects unique names. Solid bodies and attack hitboxes are separate.

| Object type | Geometry / custom properties |
|---|---|
| `platform` or `collision` | Unrotated rectangle; its full width/height is solid, including sides and underside. Optional `tileId` chooses repeated art. `collision` can be a tall wall. Its top can also serve as a navigation surface. |
| `ladder` | Rectangle spanning two platform tops; `bottom` and `top` are platform **names**. `openingWidth` cuts a real aperture in the upper floor; `entryRadius` controls alignment tolerance. Optional `tileId` draws the ladder. |
| `player_spawn` | Point at body-left X / feet Y; properties `entityId` and `facing` (`left` or `right`). Name is a stable spawn ID. |
| `enemy_spawn` | Same point convention and properties as player spawn. Add/delete these to change enemy count. |
| `navigation` | Point whose X is a launch/connection position; `from`, `to` reference surface names; `travel` is `walk`, `jump` or `drop`; `destinationX` gives the landing/connection X; `bidirectional` defaults true. |
| `pickup` | Contact rectangle; `kind` is `health` or `power`; positive integer `amount`. Health adds max HP and fully heals; power sets the minimum attack multiplier. Unique name, once per run. |
| `exit` | Contact rectangle; `levelId` and `spawnId` select a registered level/player spawn. An empty `levelId` completes the prototype. |
| `region` | Rectangle named for its HUD label; cosmetic, no loading boundary. |
| `decoration` | Rectangle with `tileId` and optional `tileSize` (default 32 world pixels); visual only. |

Actual examples in the castle:

- `deck_1`: `(256, 812)`, width 384, height 32; `tileId = castle:158`.
- `ladder_0`: `(400, 812)`, width 80, height 128; `bottom = ground`, `top = deck_1`, `openingWidth = 112`, `entryRadius = 30`, `tileId = castle:135`.
- `samurai_1`: point `(670, 940)`; `entityId = samurai`, `facing = left`.
- `route_3`: launch X 1570; `from = deck_7`, `to = deck_15`, `travel = jump`, `destinationX = 1660`.

To resize/move a platform, edit its rectangle. Collision **and its repeated texture** follow it. If it has ladders, also move/resize the ladder endpoints to meet the new surface tops. Keep aperture widths larger than the bodies that must climb them. Keep spawn bodies outside solid rectangles and inside the world. If a route needs a jump, author a navigation object; the engine does not generate arbitrary platforming paths automatically. Ladder links are generated from the ladder references.

The `groundSurface` map property names the first platform/collision object in object order. Keep `ground` first; do not delete it without providing another root surface. Other map properties are `name`, `playerEntity`, `startSpawn` and optional `backgroundImage` (a file path relative to the map).

## Tiles, visibility and references

Paint decorative tile layers normally. Multiple layers and nested groups preserve order, offsets, visibility and opacity. PNG atlas tilesets must be external `.tsj` files. Tile-layer tile dimensions must match the map grid. Horizontal/vertical flips work; diagonal rotations are rejected. Map decorations can scale atlas tiles independently.

`tileId` is `<tileset name>:<zero-based tile index>`, such as `castle:158`. It stays stable when Tiled changes a tileset's first GID. Keep tileset names unique. Legacy numeric `tileGid` is also accepted, but use `tileId` for object art. Native tile-layer GIDs remain managed by Tiled.

A tileset tile with boolean custom property **`solid = true`** adds a solid cell wherever it is painted on a visible tile layer. Tile 0 in `castle.tsj` is the working example. Other painted tiles are decorative. Use named rectangle objects for floors needing ladder/navigation references. Avoid painting extra solid tiles across ladder apertures.

Hiding a tile layer disables its art and tile solidity. Hiding an object or object layer hides its visual decoration **while gameplay objects still exist**—useful for invisible collision outlines. Delete an enemy spawn to remove that enemy. Object-layer opacity does not change gameplay or generated platform/ladder art.

## Attacks, defense and combos

**All gameplay times are seconds**, distances are world pixels, damage/posture values are units. For example, 250 ms is `0.25`. Dash i-frames use `[iframeStart, iframeEnd)`, measured from dash start; cooldown is also measured from initiation. `acceleration: 0` retains the original instant horizontal response. Positive acceleration is pixels/second². `airSpeed` and `climbSpeed` are independent of walking speed.

The actual `normal_slash` definition has:

```json
"damage": 20,
"postureDamage": 25,
"blockDamage": 25,
"deflectDamage": 35,
"duration": 1.38,
"cooldown": 0,
"hits": [{
  "id": "slash", "start": 0.5, "end": 0.68,
  "forward": 0, "top": 10, "width": 64, "height": 52,
  "windupAnimation": "samurai_prepare",
  "activeAnimation": "samurai_strike"
}],
"recoveryAnimation": "samurai_recover"
```

This prepares for 0.5 s, enables one hitbox for `[0.5, 0.68)`, then recovers until 1.38 s. A second ordered hit entry creates a separate event, as in `double_slash`. Each event can contact once, including contact during dash invulnerability. Hit intervals must be at least one 120 Hz tick (1/120 s). Boundaries are evaluated on simulation ticks; render FPS does not alter them.

`forward` is the offset from the facing body edge; `top` is relative to body top. The whole box mirrors when `directional` is true. With `directional: false`, it uses the right-edge coordinate basis regardless of facing; a negative `forward` can place a box across/behind the actor. Sprite padding never changes this box.

Unblocked hits apply both `damage` and `postureDamage`. Blocking transfers `blockDamage` to the defender, multiplied by its profile's `blockPostureMultiplier`; deflection transfers `deflectDamage` to the attacker, multiplied by the defender's `deflectPostureMultiplier`. Deflection does not add defender posture. Heavy values are explicitly doubled in their definition, so `style: "heavy"` is a visual telegraph category, not a hidden damage multiplier.

`blockable`, `deflectable`, and `interruptOnHit` control supported interactions. Posture break/death always interrupt. `dashCancel` lists `{ "start": 0, "end": 0.45 }` windows for the player's existing slash. Empty windows prevent ordinary attack cancellation; stagger escape and non-cancellable deathblows retain their existing rules.

`cooldown` runs from attack initiation. Enemy `betweenMoves` adds a pause after resolution. The shared defense profile controls anti-spam and break immunity; holding GUARD never refreshes its deflect window. Successful deflections reset the tap penalty.

`samurai_sequence` in `combos.json` repeats normal → double → heavy. The two slashes are hit events inside one move. Enemy behavior may select `sequence` or deterministic seeded `weighted` selection; weights correspond to that sequence and zero disables an entry. Keep its validated transition list; detection/engagement thresholds, chase/facing flags and recovery durations configure the supported melee state machine, not an arbitrary script.

`player_chain.followups` is empty to preserve the original single attack. To enable a tested ATTACK follow-up, add:

```json
{"from":"player_slash","to":"player_slash","open":0.08,"close":0.15,"executeAt":0.2}
```

A press during that window buffers the follow-up until 0.2 s. It starts a new hit event; it does not replay the previous hit. One follow-up per source attack is supported. `executeAt` must be between `close` and the source attack's `duration`.

## Animations

The pack still uses DarkNinja and Samurai assets. No knight assets are referenced. Frame lists may contain grid indices (including skips/reordering), or objects such as:

```json
{"asset":"../sprites/DarkNinja/frames/Idle/01.png","index":0,"duration":0.125}
```

This is the same separate-PNG form used by `ninja_idle`. A sheet clip uses `asset`, `frameWidth`, `frameHeight`, `columns`, and a `frames` list, such as `samurai_strike`'s `[4,5,6,7]`. A frame object can instead specify `x`, `y`, `width`, `height` for an explicit source rectangle. Its `duration` overrides `1 / fps`.

`anchorX`/`anchorY` locate the feet/body center in source pixels; `scale` is uniform. `facesRight` describes the original artwork. The actor's facing mirrors it. Entity `animations` map supported state names to clip IDs; attack hit entries and `recoveryAnimation` select phase clips. Add a clip ID and reference it without adding Java source.

- Idle/run and other free playback follow FPS/per-frame duration and `loop`.
- Attack preparation/active/recovery clips are sampled across the **authoritative combat phase**. Changing visual FPS never changes damage timing. Per-frame durations control how the sampled phase is distributed across frames.
- `events: [{"name":"footstep","at":0.1}]` defines queryable presentation events; they cannot apply damage. Combat exposes `hitbox_on`, `hitbox_off`, and `complete` from the attack timeline. No sound/effect handler is implied by inventing a new event name.
- A non-looping clip's optional `next` references another clip on completion during free playback. Combat-phase sampling and deathblow sampling remain authoritative.
- `placeholder: true` explicitly draws a rectangle and skips that clip's asset validation. Missing required art otherwise produces a filename-specific error; it never silently switches to another animation.

## New levels and validation

Duplicate `test_arena.tmj`, edit the map, add `{ "id": "annex", "file": "levels/annex.tmj" }` to `manifest.json.levels`, and give an exit `levelId = annex`, `spawnId = start`. Give the return exit the original level ID (`castle`) and a player-spawn name. Connected levels must use the same `playerEntity`. Enemy/reward state persists across transitions; RESET resets the whole run. Empty enemy lists are supported.

Run `./gradlew validateContent` after edits. It checks JSON syntax, duplicates, IDs, type/range errors, durations, paths, image/frame bounds, tile GIDs, ladder endpoints, spawns and exit references. Example error: `entities/enemy.json.maxHealth: must be in [1.0, 1.0E9]`. Startup displays/logs errors under `LOCK-IN-Content`.

For all regression and host rendering checks, with a JDK on PATH:

```sh
./tools/test-core.sh
./gradlew testContent assembleDebug lintDebug
```

`./tools/validate-content.sh` and `./tools/test-content.sh` also work without an Android SDK (JDK 17+, a POSIX shell and `rg`). Gradle tasks are the cross-platform editing route. Host renderer tests check commands, images, caching and transforms; they do not claim device/GPU/touch testing.

Supported scope: finite orthogonal maps, JSON array tile data, PNG atlas `.tsj`, rectangular/point objects, grouped layers, layer offsets, H/V flips and the documented properties. Infinite/compressed maps, isometric maps, polygons/slopes/ellipses, rotated/tile/template objects, diagonal tile flips, image layers, animated/collection tiles, tileset offsets and per-layer parallax are rejected with errors. One-way platforms are intentionally unsupported in the solid castle model. Use explicit navigation links for routes beyond ladder connections.

Bounds: map dimensions 960×540 through 32768×32768 world pixels, at most 1024 cells per axis, 262144 cells per tile layer / 1048576 per pack, 128 enemies per level, 32 hit events per attack, and 64 MiB total referenced decoded PNG pixels. The renderer separately reuses a ~12 MiB terrain cache. These checks limit malformed/oversized content; practical phone performance still needs local testing.
