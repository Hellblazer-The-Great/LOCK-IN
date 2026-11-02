# Data-driven refactor delivery

The Android game now starts from `assets/manifest.json`, resolves an immutable content pack, and instantiates the existing Java simulation with configured actors and Tiled geometry. Editing supported content requires no Java changes. See [CONTENT_EDITING.md](../CONTENT_EDITING.md) for the actual authoring workflow and examples.

## Implemented content and runtime

- Finite orthogonal Tiled JSON `.tmj` maps and external PNG-atlas `.tsj` tilesets: ordered/grouped tile layers, visibility, opacity, offsets, H/V flips, rectangular collision/platforms, ladder apertures, named navigation links, player/enemy spawn points, pickups, regions and exits. Tile `solid` properties create collision; stable object `tileId` references survive tileset GID changes.
- JSON entity, defense, attack, combo, animation and predefined melee-behavior definitions. References use stable IDs; duplicate/unknown IDs, invalid values, unsupported features, unsafe paths, missing images and invalid frame rectangles report filenames/properties.
- Per-instance collision dimensions, movement/acceleration/jump/climb/dash values, HP/posture/recovery, guard and anti-spam tuning. Shared definitions remain immutable; actors own health, posture, position, timers and hit consumption.
- Authoritative attack timelines with independent hit events, directional boxes, block/deflect transfers, cooldowns, dash cancel windows, hit interruption and optional buffered ATTACK follow-ups. Existing normal/double/heavy sequence is the default; seeded weighted selection is also configurable.
- JSON-driven sprite frames/rectangles, explicit sequences, per-frame durations/FPS, source facing, anchors, uniform scaling, looping/one-shot/next transitions and named presentation events. Combat phases sample configured preparation/strike/recovery clips; animation frames do not apply damage.
- Native Canvas rendering with shared PNG caching, explicit rectangle placeholders and a reusable twelve-chunk terrain pool (~12 MiB). Existing multitouch input, 120 Hz simulation, catch-up handling, camera, guard/deflection, posture breaks, deathblows, dash cancellation/i-frames, collision and normalized bars remain.
- Level switching preserves per-level enemies/rewards during a run; restart resets all levels using their definitions. Empty enemy lists work.

The migrated castle retains its 16 surfaces, 14 ladders, 36 navigation connections, five enemies, two upgrades and original coordinates. Map height rounds from 1080 to 1088 to fit 16-pixel Tiled cells. Existing DarkNinja, Samurai Fighter, castle terrain and Zen sky PNGs are used. Source art is untouched; no knight assets, Kotlin, new engine or runtime dependency was introduced. VersionCode is 8; versionName remains 0.3.2.

## Verification

- `./tools/test-core.sh --profile`: **2,272 checks passed**. This includes the historical suites, the castle traversal suite repeated through content loading, 199 content editing/runtime checks and 148 content renderer/cache checks.
- Editing demonstrations A–E pass: enemy JSON HP changes reach spawns/restart; Tiled spawn movement changes runtime placement; attack damage/timing edits change contact; platform movement/resizing changes swept collision; controls/defeat/reset remain functional.
- Additional coverage includes invalid JSON/reference/value errors, independent actors, all three attacks, separate double-hit events, heavy damage, guard boundaries, anti-spam recovery, dash cancellation/i-frame boundaries, posture breaks, deathblows, buffered combos, weighted behavior, edited collider/speed/acceleration, animation sequences/rectangles/events/completion, transitions/persistence/reset, empty enemy maps, tile solidity and fixed-rate combat from 5–120 render FPS.
- `./gradlew testContent assembleDebug lintDebug`: passed. Automatic `validateContent` also runs before APK packaging. Android lint: **0 errors, 3 existing manifest warnings** (fixed orientation, backup extraction rules and missing application icon).
- `git diff --check`: passed. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

No device/emulator gameplay, GPU rendering or actual Android multitouch testing was performed. Host renderer tests decode PNGs and inspect commands/transforms/cache use, not Android raster output. The optional desktop timing probe is not a phone performance claim.

## Deliberate limits

Content is bundled offline and requires rebuild/reinstall; no phone importer/hot reload. The behavior engine exposes validated melee choices, not arbitrary scripts. New mechanics still require Java. Rectangular solid platforms are supported; slopes, polygon collision, one-way floors, infinite/compressed maps and unsupported Tiled features fail validation. Navigation beyond ladders needs authored links. Current enemy behavior does not include guarding. Named presentation events are queryable signals, not automatic handlers for arbitrary new effects.

Historical constructors/visual classes remain for regression fixtures. The Android entry point exclusively uses `Game(Content)`, `ContentAnimations` and `ContentPainter`; changing legacy Java tuning constants does not configure the APK. Detailed coordinate conventions, bounds and supported/unsupported map features are in the editing guide.

## Files created

- `CONTENT_EDITING.md`
- `app/src/main/assets/animations/enemy.json`
- `app/src/main/assets/animations/player.json`
- `app/src/main/assets/behaviors/enemy.json`
- `app/src/main/assets/combat/attacks.json`
- `app/src/main/assets/combat/combos.json`
- `app/src/main/assets/combat/defense.json`
- `app/src/main/assets/entities/enemy.json`
- `app/src/main/assets/entities/player.json`
- `app/src/main/assets/levels/test_arena.tmj`
- `app/src/main/assets/levels/tilesets/castle.tsj`
- `app/src/main/assets/manifest.json`
- `app/src/main/java/com/lockin/game/AttackRun.java`
- `app/src/main/java/com/lockin/game/ContentAnimationClock.java`
- `app/src/main/java/com/lockin/game/ContentAnimations.java`
- `app/src/main/java/com/lockin/game/ContentPainter.java`
- `app/src/main/java/com/lockin/game/FixtureContent.java`
- `app/src/main/java/com/lockin/game/content/AnimationDef.java`
- `app/src/main/java/com/lockin/game/content/AttackDef.java`
- `app/src/main/java/com/lockin/game/content/BehaviorDef.java`
- `app/src/main/java/com/lockin/game/content/ComboDef.java`
- `app/src/main/java/com/lockin/game/content/Content.java`
- `app/src/main/java/com/lockin/game/content/ContentSource.java`
- `app/src/main/java/com/lockin/game/content/DefenseDef.java`
- `app/src/main/java/com/lockin/game/content/EntityDef.java`
- `app/src/main/java/com/lockin/game/content/Json.java`
- `app/src/main/java/com/lockin/game/content/LevelDef.java`
- `app/src/main/java/com/lockin/game/content/TiledLoader.java`
- `docs/DATA_REFACTOR.md`
- `tools/ContentChecks.java`
- `tools/ContentRenderChecks.java`
- `tools/ValidateContent.java`
- `tools/test-content.sh`
- `tools/validate-content.sh`

## Files modified

- `README.md`
- `app/build.gradle`
- `app/src/main/java/com/lockin/game/AreaDefinition.java`
- `app/src/main/java/com/lockin/game/AreaState.java`
- `app/src/main/java/com/lockin/game/Camera.java`
- `app/src/main/java/com/lockin/game/CollisionManager.java`
- `app/src/main/java/com/lockin/game/Enemy.java`
- `app/src/main/java/com/lockin/game/EnemyNavigator.java`
- `app/src/main/java/com/lockin/game/Game.java`
- `app/src/main/java/com/lockin/game/GameScreen.java`
- `app/src/main/java/com/lockin/game/LadderTraffic.java`
- `app/src/main/java/com/lockin/game/Player.java`
- `app/src/main/java/com/lockin/game/SolidPhysics.java`
- `app/src/main/java/com/lockin/game/TerrainPhysics.java`
- `app/src/main/java/com/lockin/game/WorldDefinition.java`
- `app/src/main/java/com/lockin/game/WorldNavigator.java`
- `build.gradle`
- `docs/CASTLE.md`
- `docs/SPRITES.md`
- `tools/CastleChecks.java`
- `tools/render-support/android/graphics/Bitmap.java`
- `tools/test-core.sh`
- `tools/test-render.sh`
