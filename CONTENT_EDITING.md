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


## v0.4.0 work in progress: per-hit transfers

The combat foundation branch adds optional `damage` (integer 0..1e9),
`postureDamage`, `blockDamage`, and `deflectDamage` (numbers 0..1e9) to
individual `attacks[].hits[]` entries. Omission inherits the corresponding
attack-level value; explicit zero is a real zero, not inheritance. Attack-level
fields remain required for compatibility. Hit damage/posture receives the
player's power multiplier exactly once. Block/deflect values instead receive
the player defender's profile multiplier, just as before.

`combat/flurry.json` registers `samurai_flurry`, an opt-in two-hit attack using
existing Samurai art. Its first hit has 12 HP / 18 posture, block20 / deflect22;
its second has 28 HP / 36 posture, block40 / deflect50. To try it, copy a combo
and behavior under new IDs, place `samurai_flurry` in the combo sequence, and
reference that behavior from a copied enemy entity. Existing enemies and maps
are unchanged; registering the move does not change their move pools.

This is an incremental foundation, not the completed v0.4.0 equipment/boss
release. See `docs/V0_4_0_STATUS.md` for the remaining scope.

## v0.4.0 inventory and equipment milestone

`manifest.items` is an optional array of item-registry JSON paths. Each registry
has `{ "items": [...] }`. An omitted registry preserves old content behavior.
`manifest.inventory` optionally defines `accessorySlots` (0..8, default1),
`quickSlots` (0..8, default2), and `initialItems` entries `{id,quantity}`. Main
weapon and armor each have one slot. Starting grants are owned, never equipped
automatically. RESET restores grants and clears equipment; there is no disk save.

The bundled `items/foundation.json` grants a training blade, long blade, padded
outfit, posture charm and three healing flasks. This changes available inventory,
not the player's initial combat stats. Existing maps and enemies are unchanged.

Each item has required `id`, `name`, `description`, `category` and `stackLimit`
(integer1..999). Categories are `weapon`, `armor`, `accessory`, `consumable`, and
also determine slot compatibility. `icon` is an optional PNG path relative to
its registry file; empty/omitted uses a drawn square. Weapons require `attack`
and optionally `combo` (empty means no followups). Optional `animations` maps
existing player state names to registered clips. Attack phase visuals/reach/
timing come from the referenced attack, not from inventory UI code.

Equipment `modifiers` accepts only `damageMultiplier`, `postureMultiplier`,
`incomingDamageMultiplier` (each0..10, default1), `maxHealthBonus` (integer0..1e6,
default0). These are passive equipped effects: multipliers multiply across
slots, HP bonuses sum. Recalculation starts from base values on every change;
repeat equip cannot stack. HP increases do not heal; removing gear clamps HP.
Run health/power pickups remain independent. Player damage/posture is modified
once; armor scales incoming HP damage, not posture or deflect transfers.

Consumable `effects` accepts `heal` (integer0..1e6) and `postureRestore`
(number0..1e9), default0. At least one must be positive; consumables cannot
carry equipped modifiers. Use consumes one item even at full HP/posture.
Posture restoration cannot cancel an active break. Gear cannot declare use
effects. Weapons alone can declare attack/combo/animation overrides.

For an item pickup, create an ordinary named pickup rectangle with `kind=item`,
`itemId=<registered ID>`, and `amount` (positive integer, <=stackLimit). A full
stack leaves the pickup in the world; contact is all-or-nothing. Old health/
power pickups retain their behavior. Runtime rewards may also call
`player.inventory.acquire(id,quantity)`, which returns the quantity accepted.

Touch **ITEMS** to open the paused menu. Select a row to inspect its description,
choose **SLOT +**, then **EQUIP** (also assigns consumables) or **UNEQUIP**.
**USE SLOT** consumes the selected consumable slot; **USE Q1** outside the menu
uses quick slot1. PREV/NEXT paginate five items. CLOSE resumes with inputs and
queued simulation time cleared. Opening during an attack permits inspection,
but equipment/use is rejected until the action finishes after resuming.
Enemy simulation, posture and action timers stop while the menu is open.

All inventory/equipment mutations run on the simulation thread. The Android
input adapter queues commands rather than modifying inventory directly. No
enemy inventory, guarding or defensive AI is introduced.

## v0.4.1 boss encounter milestone

Optional `manifest.bosses` lists JSON registry files with root `bosses` array.
See `bosses/trial.json` for a complete two-phase example. It references the named
`samurai_1` enemy spawn in the separate `boss_demo` level. Set startLevel to
`boss_demo` to play it, or author a normal exit to that level/start spawn.
The default castle and its existing spawns/movesets are unchanged.

Copy the encounter under a new unique id. Required fields: id, name (HUD), level,
spawn, trigger (x/y/width/height in world pixels), phases and rewards. The spawn
must be an enemy in that level; it can belong to only one encounter. The trigger
must fit the level. Until player-body overlap, the actor is locked and protected.

Each phase requires healthBelow (0..1, first exactly1, strictly decreasing),
behavior (existing melee behavior), maxPosture (1..1e9), transitionDuration
(0..60 seconds). Optional speedMultiplier and damageMultiplier are0..10/default1;
invulnerable defaultsfalse, animation defaults empty, event defaults empty.
Speed scales enemy chase/navigation speed. Damage scales outgoing HP/posture/
block/deflect transfers; player defender profile still controls defensive scaling.
Transition entry cancels attacks, replaces behavior, resets posture and begins
an action lock. Optional animation is sampled across the lock. No HP refill or
maximum-health change occurs: the encounter uses its entity's HP maximum.

After the current transition ends, next phase triggers on health fraction <=
healthBelow OR its explicit event. Runtime producers call
`game.signalEncounter(encounterId,eventName)`; this is a fixed event latch, not
a script. Events for later phases latch until eligible; only the next phase can
advance, so large health loss cannot skip transition locks. Defeat takes priority
over phase changes. No generic animation event dispatcher emits encounter events.

Rewards are `{item,quantity}` entries referencing item IDs; quantity must fit that
item's stackLimit. Each unit is granted once. Full inventory retains outstanding
units and retries while this level is active; it never discards rewards silently.
Completion is independent of reward collection. `Game.completedEncounters()`
exposes stable encounter IDs for future saves; flags and encounter state persist
across level travel and clear on RESET. Durable storage belongs to v0.5.0.

Single-phase encounters work with a one-element phases array. Ordinary enemies
need no boss fields. New boss identities/movesets use existing entities, behaviors,
combos and animations; no boss-specific Java class is required. Arena boundary
locking, projectiles and additional attack effects are not yet supported here.

### Attack motion (v0.4.1 milestone)

Optional AttackDef `motion` is a closed object with required `start`, `end`
(seconds in attack duration, end>=start+1/120) and `distance` (-2000..2000 world
pixels, positive forward). Displacement is distributed evenly over that interval,
mirrored by current facing and swept through existing terrain/body collision.
Cancelled attacks stop displacing; blocked movement is not refunded later.

Optional `windupMultiplier`, `activeMultiplier`, `recoveryMultiplier` are0..5,
default1, scaling player-directed horizontal movement in each attack phase.
Enemy attack motion uses distance only: enemies already stop ordinary pursuit
while attacking. These multipliers do not speed up animation, timers or lunges.
The `lunging_slash` sample advances90px over .2.. .5s and is used in phase2 of the
opt-in sample boss. Existing enemy movesets and weapons are unchanged.

Knockback, hit stun, armor, attack immunity, timed buffs, hitstop, camera shake,
projectiles and arena locking are not implemented in this milestone. Do not add
unsupported effect fields; strict validation rejects them.
