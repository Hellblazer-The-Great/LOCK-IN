# LOCK-IN content reference — historical implementation audit

**Historical audit only:** This document describes the pre-data-driven revision below. Remote `main` now includes PR #10 (`2c600f5`), which adds JSON/Tiled content loading and `CONTENT_EDITING.md`. Statements of absent features below apply only to the audited revision; this is not a current content-authoring manual. The JSON/Tiled implementation requires a separate updated audit before this document can serve as the current reference.

Audited revision: `6a29b5cc55c91409d144afbe882749fb0330f601` (checkout branch `work`). Date: 2026-11-02.

## 1. Read this before editing

**This checkout is not the data-driven version described in the assignment.** There is no gameplay JSON loader, content registry, Tiled importer, entity ID system, defensive profile registry, combo schema, or knight configuration. The Android game constructs `new Game()` in `GameScreen`; that constructor instantiates a Java-authored continuous castle. Adding JSON files cannot change that game.

This manual documents the actual implementation and identifies unavailable workflows explicitly. Java tuning below is a reference for understanding the current design, not a claim that these fields are editable through content files. No gameplay code, existing assets, or mechanics were changed for this audit. `CONTENT_EDITING.md` does not exist in this checkout and has not been created or replaced.

### Evidence and notation

Source references link to repository files. Method names identify the exact consumers, including where declarations and consumers differ. All Java names below are case-sensitive. Units are logical world pixels (px), seconds (s), HP, posture units, or dimensionless multipliers unless stated otherwise. Origin is top-left; x increases right and y down. Art pixels are separate from world pixels. Frames are zero-based; exported Ninja image filenames are one-based.

“Unsupported” means the inspected runtime has no implementation. **UNVERIFIED** marks facts that source inspection/host checks cannot establish, such as physical-device rendering. A missing schema has no accepted fields, optional defaults, or validator; hypothetical property names must not be treated as accepted input.

### Contents

1. Repository/content inventory
2. Only JSON file: atlas metadata
3. Entity tuning and combat configuration
4. Combat formulas, timing, AI and deathblows
5. Animation and PNG integration
6. World geometry and Tiled support
7. Designer workflows and knight feasibility
8. Misleading settings, verification and audit totals

## 2. Complete structured-content inventory

The asset tree contains exactly **one `.json`**, **zero `.tmj`**, and **zero `.tsj`** files. A repository search excluding Git, Gradle caches and generated app build output finds the same single JSON. There are no registered gameplay content files.

| Requested content | Actual status / implementation |
|---|---|
| `manifest.json` | Absent. The phrase “sprite manifest” in source refers to the Java `SpriteClip` enum. Android's `app/src/main/AndroidManifest.xml` configures the application, not game content. |
| `entities/player.json`, `entities/enemy.json` | Absent. `Player.java`, `Enemy.java`, `AreaState.java` construct actors. |
| `entities/knight.json` / any additional entity definition | Absent. Only Player and Enemy implementations exist. |
| `combat/defense.json` | Absent. `CombatConfig` constructor supplies shared tuning. |
| `combat/attacks.json`, `combat/combos.json` | Absent. Player constants and `EnemyMove` enum define attacks; fixed enum order replaces combo authoring. |
| `animations/player.json`, `animations/enemy.json`, additional animation definitions | Absent. `SpriteClip`, `CharacterAnimations`, `AnimationClock` select and play clips. |
| `behaviors/enemy.json` | Absent. `Enemy`, `EnemyNavigator`, `WorldNavigator`, `LadderTraffic` implement AI. |
| All Tiled maps/tilesets | None. `WorldDefinition` and `AreaDefinition` contain authored geometry. |
| `sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/spritesheet.json` | Present asset metadata; **not read by gameplay or renderer**. Full schema below. |

Paths in this table are relative to `app/src/main/assets` when describing prospective content. All 30 production Java files were included in the source search for loaders and consumers. Only `SpritePainter.decode` opens runtime asset streams; it decodes PNGs. No JSON parser/importer appears in production sources.

Other asset files: **460 PNG, 34 Aseprite, 13 GIF, 2 TXT, 1 MD**. These are binary art/source animation exports and pack documentation, not additional supported configuration schemas. Aseprite/GIF metadata and filenames are not imported. Unused pack images do not become actors, hazards, pickups or animations automatically. Selected runtime PNGs are verified by the existing host checks; this audit does not claim to visually inspect every unused binary export.

Primary implementation sources: [Game](app/src/main/java/com/lockin/game/Game.java), [SpritePainter](app/src/main/java/com/lockin/game/SpritePainter.java), [SpriteClip](app/src/main/java/com/lockin/game/SpriteClip.java), [WorldDefinition](app/src/main/java/com/lockin/game/WorldDefinition.java).

## 3. `spritesheet.json`: every actual JSON field

File: `app/src/main/assets/sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/spritesheet.json`.

No Java parser, validator, defaulting logic, or runtime consumer exists for this file. Every field below affects **neither actor nor world at runtime**. The types are observed file types, not an enforced schema. Missing, renamed or altered metadata does not change collision, navigation, pickups, sprite selection or frame timing. The renderer instead uses `TerrainArt` integer rectangles and its constant `SHEET` PNG path. This absence is established by searching production sources for JSON handling and tracing `SpritePainter` asset loading.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|

| `meta` | Pack metadata object | object | None | No effect | Observed object; no runtime requirements/default |

| `meta.name` | Human pack title | string | None | No effect | Observed string; unvalidated |

| `meta.tile_size` | Nominal source tile size, currently 16 | art px | None | No effect | Observed integer; does not set world tile size |

| `meta.image` | Atlas filename, currently spritesheet.png | path string | None | No effect | Not resolved by runtime; TerrainArt.SHEET is authoritative |

| `meta.count` | Declared entry count, currently 151 | count | None | No effect | Not checked against frame entries |

| `frames` | Dictionary keyed by arbitrary art names | object | None | No effect | Keys are atlas labels, not gameplay IDs |

| `frames.<name>.x` | Source rectangle left | art px | None | No effect | Observed integer; no runtime bounds check here |

| `frames.<name>.y` | Source rectangle top | art px | None | No effect | Observed integer; y grows downward |

| `frames.<name>.w` | Source width | art px | None | No effect | Observed integer; generally 16 or door width 32 |

| `frames.<name>.h` | Source height | art px | None | No effect | Observed integer; generally 16 or door height 32 |

| `frames.<name>.category` | Pack organizational category | string | None | No effect | hazards/climb/collectibles/etc. do not create mechanics |

The `frames` dictionary actually has **150 entries**; `meta.count` is 151. All entries share the five-field shape above. For example `door_open` is `(32,0,32,32,"doors")`; `spikes_up` is `(48,48,16,16,"hazards")`. A spikes image has no damage behavior. Categories present: `background`, `castle`, `climb`, `collectibles`, `desert`, `doors`, `gadgets`, `hazards`, `ice`, `liquid`, `moving_platform`, `platforms`, `swamp`, `terrain`.

Evidence: [TerrainArt](app/src/main/java/com/lockin/game/TerrainArt.java) enum constants and `SHEET`; [SpritePainter](app/src/main/java/com/lockin/game/SpritePainter.java) constructor, `tile`, `tilePart`, `worldScenery`. These read Java rectangles and PNGs, never the JSON. No fields in this file are player-only or enemy-only: all are unused by the shipped game.

## 4. Entity settings: actual Java implementation

There are no entity JSON fields or string references. All values below require Java changes to tune; no parser supplies defaults. Named constants are compiled defaults. `Player(config,maxHealth)` and `Enemy(config,maxHealth)` clamp supplied integer maximum HP to at least zero; zero starts defeated. `Game(config,p,e)` passes one `CombatConfig` to player and all enemy posture instances, and the same enemy HP maximum to every spawn. There is no per-type stats table.

Evidence: [Player](app/src/main/java/com/lockin/game/Player.java) constructors, `update`, `tryHit`, `healthUpgrade`, `powerUpgrade`; [Enemy](app/src/main/java/com/lockin/game/Enemy.java) constructors, `detects`, `update`, `healthDamage`; [AreaState](app/src/main/java/com/lockin/game/AreaState.java) constructor/reset; [Game](app/src/main/java/com/lockin/game/Game.java) constructor.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|

| `Player.WIDTH`, `HEIGHT` | Body size 36 × 64 | px | Player collision/targets | Larger collision body; affects center/feet calculations | Compiled floats; not inferred from PNG alpha |

| `Player.SPEED` | Ordinary horizontal speed 180 | px/s | Player | Faster movement per simulation second | Input clamped to -1..1 by Game; source constant |

| `Player.DAMAGE` | Base health damage 25 | HP/contact | Enemy target | More HP lost per ordinary hit | Integer; health subtraction clamps nonnegative amounts |

| `Player.POSTURE_DAMAGE` | Base ordinary-hit posture 25 | posture/contact | Enemy target | Earlier target break | Double; positive finite amounts accepted by Posture.add |

| `Player.ATTACK_RANGE` | Forward hitbox width 68 | px | Enemy target | Reaches farther without changing body | No configurable vertical rectangle: y+10 to y+HEIGHT-8 |

| `Player.ATTACK_DURATION` | Active duration .16 | s | Player attack | Longer opportunity to overlap a target | Not a windup; attack is active immediately |

| `Player.COOLDOWN` | Cooldown .45 from attack start | s | Player | Lower attack frequency | No attack buffer/cancel-window schema |

| `Player.DEFAULT_MAX_HEALTH` | Default initial maximum 100 | HP | Player | More survivable hits | Constructor override; baseMaxHealth retained across restart |

| `Player.DASH_DURATION` | Movement burst .180 | s | Player | Longer dash; centered iframe bounds derive from it | Compiled double; cannot restart an active dash |

| `Player.DASH_IFRAMES` | Centered invulnerability .100 | s | Player | Longer immunity within dash | START=(duration-iframes)/2; END=START+iframes |

| `Player.DASH_COOLDOWN` | Initiation cooldown .450 | s | Player | Less frequent dashes | Cooldown continues through stagger escape |

| `Player.DASH_SPEED` | Burst speed 950 | px/s | Player | More travel; collision still applies | Neutral input backdashes; directional input turns |

| `Player.DEATHBLOW_DURATION` | Execution lock .30 | s | Player performer | Longer action lock | Enemy dies immediately at acceptance, not animation end |

| `Enemy.WIDTH`, `HEIGHT` | Body size 44 × 70 | px | Enemy collision/targets | Larger collision body | Compiled floats; all enemies share dimensions |

| `Enemy.SPEED` | Chase speed 110 | px/s | Enemy | Faster approach and some navigation traversal | Airborne navigation can use separate hardcoded 250 |

| `Enemy.DEFAULT_MAX_HEALTH` | Default maximum 100 | HP | Enemy | More hits needed | Same Game constructor maximum for every enemy |

| `Enemy.detectionRadius` | Default 340 center-to-center radius | px | Enemy awareness | Detects farther in 2D including vertical separation | Mutable Java float; <=0 disables detection; no JSON setter |

| `Enemy.MELEE_DISTANCE` | Body gap threshold 30 to initiate attack | px | Enemy | Starts windup farther away | Requires height overlap, grounded, not climbing/navigating |

| `Enemy.DAMAGE` | Base unblocked health damage 20 | HP/contact | Player target | More player health lost | Multiplied by current EnemyMove.damageMultiplier |

| `Enemy.POSTURE_DAMAGE` | Base unblocked posture damage 25 | posture/contact | Player target | Earlier player break | Block uses config.blockDamage instead |

| `Enemy.WINDUP`, `ACTIVE`, `RECOVERY`, `ATTACK_RANGE` | Normal move defaults .5/.18/.7/64 | s / px | Enemy attack and player target | Longer preparation/activity/recovery or reach respectively | Copied into NORMAL_SLASH; RECOVERY also used after posture break |

Player jump velocity is hardcoded `-590` in `Player.update`; gravity is `GameWorld.GRAVITY=1500 px/s²`. More negative initial velocity jumps higher. Climbing speed is `TerrainPhysics.CLIMB_SPEED=140 px/s`. None is a content property. `healthUpgrade()` sets max to base+50 (saturating at Integer.MAX_VALUE) and fully heals; it does not repeatedly add 50. `powerUpgrade()` sets multiplier to exactly 2; ordinary health/posture hits become 50/50. It does not multiply defensive transfers or deathblows.

## 5. Defense tuning: `CombatConfig`, not `combat/defense.json`

[CombatConfig](app/src/main/java/com/lockin/game/CombatConfig.java) is immutable Java constructor input. Every parameter must be finite and >=0. Capacity and broken duration must be >0; spamRecovery and breakImmunity must be >0; minimumDeflectWindow cannot exceed deflectWindow. Invalid constructor input throws `IllegalArgumentException`. All 12 parameters are required in the extended constructor. The seven-argument constructor supplies spamStep=.05, minimum=min(.05,deflectWindow), spamRecovery=.8, breakImmunity=1, deathblowRange=90. `DEFAULT` uses the seven-argument constructor.

Consumers: [Player](app/src/main/java/com/lockin/game/Player.java) `setGuard`, `effectiveDeflectWindow`, `receiveAttack`, `canDeathblow`; [Posture](app/src/main/java/com/lockin/game/Posture.java) `add`, `update`, `cancelBreak`, `fraction`.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|

| `deflectWindow` | Initial window .320 | s after press | Player defender | Easier/longer deflection eligibility | Inclusive comparison with 1e-7 tolerance |

| `postureCapacity` | Break threshold 100 | posture | Owner of each Posture instance | More received posture needed to break | >0; also bar denominator |

| `postureRecovery` | Quiet decay rate 12 | posture/s | Posture owner | Faster meter decay | >=0; zero disables decay |

| `blockDamage` | Posture added when player blocks, 25 | posture/contact | Player defender | Player breaks sooner while blocking | Uses player config × enemy move multiplier |

| `deflectDamage` | Posture returned on player deflect, 35 | posture/contact | Enemy attacker | Attacker breaks sooner on deflected swings | Uses PLAYER config × enemy move multiplier |

| `recoveryDelay` | Quiet wait before decay, 1 | s | Posture owner | Meter persists longer after pressure | >=0; pressure resets quietTime |

| `brokenDuration` | Break/stagger duration 1 | s | Posture owner | Longer player lock/enemy deathblow opportunity | >0; incoming pressure cannot extend it |

| `spamStep` | Window reduction per failed tap .05 | s/failure | Player defender | Spamming loses eligibility faster | >=0; effective window floored at minimum |

| `minimumDeflectWindow` | Window floor .05 | s | Player defender | More forgiving worst-case deflection | 0..deflectWindow |

| `spamRecovery` | Penalty quiet reset and short-tap cutoff .8 | s | Player defender | Penalty persists longer; more holds count short | >0; two distinct uses of same setting |

| `breakImmunity` | Post-break posture grace 1 | s | Posture owner | Longer prevention of new posture accumulation | >0; never health invulnerability |

| `deathblowRange` | Maximum eligible body gap 90 | px | Player performer / enemy recipient | Execute from farther away | >=0; needs facing, vertical overlap and broken living enemy |

### Which actor owns the consulted configuration?

There is **no `combatProfile` field**. In `Player.receiveAttack(attacker)`, block and deflect amounts come from the receiving **player's private config**, while the multiplier comes from the **enemy's current move**. Thus `deflectDamage` is configured on the defender but damages attacker posture. Enemy constructor config determines its own posture capacity, decay, break and grace through its `Posture`; it is not consulted to choose the outgoing deflect transfer. `Game` normally shares one config so this distinction is easy to miss. Ordinary enemy HP/posture damage comes from Enemy constants/current move; ordinary player damage comes from Player constants/power upgrade. No per-actor posture multiplier exists.

## 6. Attack definitions, combos and AI

### `EnemyMove` fields

Evidence: [EnemyMove](app/src/main/java/com/lockin/game/EnemyMove.java) enum constructor; [Enemy](app/src/main/java/com/lockin/game/Enemy.java) `update`, `resolveMove`, `attackLeft/Right/Top/Bottom`, `tryHit`; [CharacterAnimations](app/src/main/java/com/lockin/game/CharacterAnimations.java) `updateEnemy`.

All fields are required Java enum constructor arguments. No parser validates positive ranges or supplies missing values. Designer-safe assumptions are positive phase durations, positive reach/height, strikes>=1, positive multiplier; these are recommendations, not an implemented validator.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|

| `strikes` | Number of independently resolving contacts | integer | Enemy attack / player target | More strike phases in one move | Normal 1, double 2, heavy 1 |

| `damageMultiplier` | Multiplier for outgoing HP/posture and defensive transfers | multiplier | Both actors by outcome | More HP on hit, player posture on block, enemy posture on deflect | Normal/double 1; heavy 2; not a defender resistance |

| `windup` | First preparation duration | s | Enemy | More time for player response | Normal .50; double .40; heavy .85 |

| `active` | Duration of each strike hitbox | s | Enemy / player target | Longer overlap opportunity | Normal .18; double .14; heavy .22 |

| `recovery` | Follow-through after final strike | s | Enemy | Longer delay before next move | Normal .70; double .65; heavy 1.0 |

| `betweenStrikes` | Next windup between active strikes | s | Enemy | More time between double contacts | Normal/heavy 0 unused; double .28 |

| `reach` | Forward hitbox width | px | Player target | Hits farther forward | 64 / 54 / 48 for normal/double/heavy |

| `topOffset` | Hitbox top relative to enemy y | px | Player target | Positive moves box downward; negative upward | 10 / 10 / -32 |

| `hitboxHeight` | Vertical hitbox span | px | Player target | Hits a taller vertical range | 52 / 52 / 102 |

| `BETWEEN_MOVES` | Pause after completed recovery .12 | s | Enemy | Slower sequence repetition | Static global constant, not per-move |

### Actual scheduling and damage pseudocode

Derived from `Game.update`, `Player.tryHit/receiveAttack`, `Enemy.tryHit/receiveHit`, `Posture.add`:

```text
simulation tick = 1/120 s
update player and enemy posture timers
try fresh ATTACK deathblow before DASH
apply player movement/actions; resolve physical bodies
try fresh ATTACK deathblow again (after movement)
collect upgrades
resolve ordinary player hit first
update each enemy navigation/state, terrain/body collision, then its hit
```

Player ordinary hit requires an active .16 s attack, a living target, strict AABB overlap and `!hitThisAttack`. It applies HP=`min(INT_MAX,25*power)` and posture=`25*power`, where power=1 or 2. The enemy commits posture and health even if health is lethal. The player attack is then consumed; iterating enemies means one ordinary swing hits at most one target, not every enemy in the arc. Hitbox x is `[x+WIDTH,x+WIDTH+68]` facing right or `[x-68,x]` left; y is `[y+10,y+HEIGHT-8]`. Touching edges alone is not overlap.

```text
on overlapping, unconsumed enemy active strike:
    if player dash-invulnerable: consume strike; no pressure or damage
    otherwise reset both actors' posture quiet timers
    m = enemy.currentMove.damageMultiplier
    if guarding and guardAge <= guardWindow + epsilon:
        player HP += 0; player posture += 0
        enemy posture += player.config.deflectDamage * m
        successful guard; reset failed taps; restore full window
    else if guarding:
        player HP += 0
        player posture += player.config.blockDamage * m
    else:
        player HP -= Enemy.DAMAGE * m
        player posture += Enemy.POSTURE_DAMAGE * m
    consume this strike regardless of hit/block/deflect/dodge outcome
```

At defaults normal/double contact gives 20 HP+25 posture unguarded; heavy gives 40+50. A block gives 25 player posture (heavy 50); a deflect gives 35 enemy posture (heavy 70). A successful deflect adds zero player posture. Health clamps at zero; positive posture increments cap at capacity. Corpse contacts are ignored. Enemy attack hitboxes are relative to committed facing: right begins at body right, left ends at body left. Slash spans y+10..62; heavy y-32..70. Visual swords do not extend them.

A strike may miss initially and hit later during its active interval. Windup alone cannot damage. A dash dodge consumes the contact, so expiry of invulnerability cannot make that same strike hit again. Double slash creates a new unconsumed strike after its .28 s inter-strike windup. A lethal player hit resolves first and prevents that enemy's hit in the same tick. Enemy phase transitions discard phase overshoot and advance at most one phase per update; authored times are sampled by fixed ticks, not arbitrary animation frames.

### Player cooldown, cancellation and follow-ups

Ordinary attacks activate immediately and start .45 s cooldown at initiation. An ATTACK pressed during cooldown is discarded after the tick, not stored until recovery. No follow-up window, branching combo, cancel-window array, frame hit event or attack ID exists. Dash clears the active attack and marks it consumed but cannot refund damage already applied. Its path does not reset an existing attack cooldown. Dash suppresses attacks during the burst; it can escape stagger, but cannot cancel execution. Guard may coexist with ordinary attacking/moving/jumping, except while staggered, dashing, defeated or executing. Deathblow ignores ordinary attack cooldown and sets cooldown to .45 s. An eligible fresh ATTACK wins a simultaneous DASH input.

### Anti-block-spam algorithm

`Player.finishGuardAttempt`, `failTaps`, `setGuard`, `updateDefense` implement:

```text
on ending an unsuccessful guard whose age < spamRecovery:
    failedTaps = min(1_000_000, failedTaps + 1)
    tapQuietTime = 0
on a new held guard press:
    guardWindow = max(minimumDeflectWindow, deflectWindow - failedTaps*spamStep)
    guardAge = 0
on deflection:
    failedTaps = 0; guardWindow = deflectWindow; tapQuietTime = 0
    // guardAge is not reset
on tapQuietTime >= spamRecovery (with numeric tolerance):
    failedTaps = 0
```

Input counts presses, including completed taps between render frames. Multiple presses can contribute failures; merely holding GUARD does not generate edges. Blocking does not mark a guard successful; a short guard that blocks but never deflects can still count as an unsuccessful tap on release. Holds >=.8 s avoid that failure classification. Penalty recovery resets the failure count; an already latched `guardWindow` is not automatically refreshed. Raising a held guard after dash or through stagger resumes ordinary blocking with effectively infinite guardAge; a fresh press is needed to deflect. Guard is independent of facing.

### Posture recovery, breaks and immunity

`Posture.pressure()` resets `quietTime=0`. A positive finite `add(amount)` calls pressure even if currently broken/immune, but adds no meter during either state. Otherwise `value=min(capacity,value+amount)` and reaching capacity sets the break timer. Invalid/nonpositive amounts do nothing. During ordinary updates:

```text
recoverySeconds = max(0,quietTime+dt-recoveryDelay) - max(0,quietTime-recoveryDelay)
value = max(0,value-postureRecovery*recoverySeconds)
quietTime += dt
```

At natural break expiry, value and quiet time reset to zero; grace starts for breakImmunity minus any timestep leftover. Additional hits cannot extend the break timer. Health damage still applies during stagger and posture grace. Player stagger suppresses movement/jump/attack/guard; gravity and collision still run. Enemy break cancels remaining strikes and advances an in-progress sequence once. After expiry it enters `Enemy.RECOVERY` (.7 s), even if interrupted heavy normally has 1.0 s recovery. `cancelBreak()` (dash escape) instead ends stagger, grants grace and retains the existing meter; it does not reset posture to zero.

### Deathblow actor, range and duration

Only **Player performs** `tryDeathblow(Enemy)`; only **Enemy receives** it. Eligibility requires both living, enemy posture broken, player not staggered/dashing/executing, facing toward the enemy center (strict sign of dx), strict vertical body overlap, and horizontal body gap <= player's config.deathblowRange. Gap=`abs(enemyCenterX-playerCenterX)-(Player.WIDTH+Enemy.WIDTH)/2`. At acceptance the enemy loses all remaining health immediately. The player then has a .30 s execution lock with guard, attacks, dash, movement and jump suppressed. No delayed animation event delivers the kill; no execution HP threshold, animation-specific distance, or enemy deathblow action exists. A previously active ordinary attack does not transform into a deathblow; a fresh ATTACK edge is necessary.

### AI capabilities and state machine

`Enemy.update` repeats `NORMAL_SLASH → DOUBLE_SLASH → HEAVY_OVERHEAD` by enum order. Leaving detection does not reset sequence. Completed misses advance it; interrupted posture breaks resolve an in-progress move once; death/reset restores normal. States are IDLE, CHASE, ATTACK_WINDUP, ATTACK_ACTIVE, ATTACK_RECOVERY, POSTURE_BROKEN, DEAD. Facing tracks the player outside attack phases and is committed through preparation/strike/recovery. No teleports or snapping into melee range occur.

Enemies **can jump and climb** through `WorldNavigator`/`EnemyNavigator`. They cannot block, deflect, dash or execute deathblows. They have no input-driven guard implementation, independent behavior IDs, weighted/random attacks, retreat logic or configurable action list. Navigation can commit a route after detection and continue it beyond the initial radius. Same-surface aperture jumping is generated automatically in continuous-world navigation. Breath-first surface routing uses explicit directed links; it is not a navmesh or tile-derived pathfinder. A route with no reachable target can suppress ordinary chase. Stuck traversal replans after 2.5 s. Ladder traffic offers bounded 1.5 s yielding toward a safe floor position; it does not guarantee all crowds can pass.

Evidence: [EnemyNavigator](app/src/main/java/com/lockin/game/EnemyNavigator.java) `update`; [WorldNavigator](app/src/main/java/com/lockin/game/WorldNavigator.java) `update`, `yieldTo`, `pathLaunch`; [LadderTraffic](app/src/main/java/com/lockin/game/LadderTraffic.java) `update`, `safe`.

## 7. Animation integration and sprite layout

### Schema boundary

There is no animation JSON schema, sprite-pack registration file, entity animation ID, animation event list, directional clip map, or knight animation definition. `SpriteClip` is the compiled registration list; `CharacterAnimations` explicitly selects each enum clip and inclusive frame range. A completely new independently selectable enemy sprite pack **cannot be integrated without Java changes** in this revision. An asset replacement that exactly matches existing paths/layouts can reskin existing enemies, but cannot add a new enemy type or unique attacks.

Evidence: [SpriteClip](app/src/main/java/com/lockin/game/SpriteClip.java) constructor and all enum entries; [SpritePainter](app/src/main/java/com/lockin/game/SpritePainter.java) constructor, `load`, `decode`, `character`; [AnimationClock](app/src/main/java/com/lockin/game/AnimationClock.java) `play`, `advance`, `sample`; [CharacterAnimations](app/src/main/java/com/lockin/game/CharacterAnimations.java) `updatePlayer`, `updateEnemy`.

### Descriptor fields (Java, not JSON)

All are required positional enum constructor arguments; there are no optional parser defaults. The constructor parameter `width` becomes field `frameWidth`, `height` becomes `frameHeight`. Constructor scale behavior is unusual and documented below.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `asset` | Asset-relative path, automatically prefixed `sprites/` | path string | Registered visual clip | No numeric ordering; selects image | Exact case-sensitive path; `%02d` means numbered PNG sequence |
| `frameWidth` | Cell/source image width | art px | Visual clip | Wider drawn source, layout expectation and visual footprint | Sheet width must equal columns×frameWidth; numbered PNG width must match exactly |
| `frameHeight` | Cell/source image height | art px | Visual clip | Taller drawn source/layout expectation | Sheet height divisible by it; numbered PNG height must match exactly |
| `frames` | Number of valid cells, excluding blank tail | integer count | Visual clip | More frames available, not automatically selected | Sheet capacity >= frames; AnimationClock.play validates selected range |
| `columns` | Row-major cells per sheet row | integer count | Visual clip | Changes source rectangle mapping and expected sheet width | Numbered sequence packed horizontally; existing sequences use columns=frames |
| `anchorX` | Art-space horizontal body-center/feet reference | art px | Visual clip | Right-facing art moves left relative to body center | Mirroring occurs around world anchor, so side reverses when flipped |
| `anchorY` | Art-space vertical feet reference | art px | Visual clip | Art moves upward relative to feet | Not a collision-body height or automatic alpha-bound anchor |
| `scale` | Final uniform source-to-world scale | world px/art px | Visual clip | Larger art footprint, unchanged body/hitboxes | Constructor argument is ignored; see constants below |
| `facesRight` | Native visual orientation | boolean | Visual clip | Not numeric; toggling reverses flip decision | flip = actor.facingRight != clip.facesRight |
| `NINJA_RENDER_SCALE` | Effective scale for path starting `DarkNinja`, 3.4 | multiplier | Player pack visuals | Larger Ninja art | Selected from unprefixed constructor path |
| `SAMURAI_RENDER_SCALE` | Effective scale for all other paths, 8.2 | multiplier | Enemy pack visuals | Larger Samurai/other art | A new path would also receive this scale unless Java changed |

The constructor accepts historical scale arguments 1.6/5 but replaces them based on path prefix. Changing those per-entry numbers has **no effect**. No arbitrary source rectangle, rotated frame, per-frame anchor, offset list, per-entity scale or collision-body definition can be authored in an external file.

### Registered PNG clips and exact layout

Paths below are relative to `app/src/main/assets/sprites/`. `NINJA_` entries use separate PNGs; `%02d` resolves 01 through frame count. All other entries use a single sheet. Full sheet width must be `columns*frameWidth`; its height must be a cell-height multiple with enough cells, not necessarily just the minimum rows.

| Enum clip | PNG path | Cell W×H | Frames / columns | Anchor X,Y | Native facesRight |
|---|---|---|---|---|---|
| `NINJA_IDLE` | `DarkNinja/frames/Idle/%02d.png` | 64×64 | 8 / 8 | 30,56 | true |
| `NINJA_RUN` | `DarkNinja/frames/Move/%02d.png` | 64×64 | 8 / 8 | 30,56 | true |
| `NINJA_JUMP` | `DarkNinja/frames/Jump/%02d.png` | 64×64 | 12 / 12 | 30,56 | true |
| `NINJA_ATTACK` | `DarkNinja/frames/Attack1/%02d.png` | 96×64 | 6 / 6 | 30,56 | true |
| `NINJA_EXECUTION` | `DarkNinja/frames/Attack2/%02d.png` | 96×64 | 6 / 6 | 30,56 | true |
| `NINJA_GUARD` | `DarkNinja/frames/Defend/%02d.png` | 64×64 | 8 / 8 | 30,56 | true |
| `NINJA_HURT` | `DarkNinja/frames/Hurt/%02d.png` | 64×64 | 3 / 3 | 30,56 | true |
| `SAMURAI_IDLE` | `Samurai_Bundle/Samurai/Idle/Idle_1.png` | 16×16 | 3 / 2 | 8,14 | false |
| `SAMURAI_RUN` | `Samurai_Bundle/Samurai/Run/Samurai_Run.png` | 16×16 | 6 / 3 | 8,14 | false |
| `SAMURAI_SLASH` | `Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png` | 48×48 | 18 / 5 | 21,32 | false |
| `SAMURAI_RETURN_SLASH` | Same Attack_1 sheet | 48×48 | 18 / 5 | 21,32 | true |
| `SAMURAI_HEAVY` | `Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png` | 48×32 | 11 / 4 | 24,13 | false |
| `SAMURAI_HURT` | `Samurai_Bundle/Samurai/Hit/Samurai_Hit.png` | 16×16 | 2 / 2 | 8,16 | false |
| `SAMURAI_DEATH` | `Samurai_Bundle/Samurai/Death/Samurai_Death_1.png` | 32×16 | 7 / 3 | 8,16 | false |

There are 14 descriptors, seven numbered Ninja atlases generated from 51 PNGs, and six distinct Samurai PNG sheets. Return slash shares the slash bitmap but has opposite orientation metadata. Sheet sampling is row-major:

```text
sourceX = (frame % columns) * frameWidth
sourceY = floor(frame / columns) * frameHeight
worldAnchor = (actor.x + bodyWidth/2, actor.y + bodyHeight)
localRect = (-anchorX,-anchorY, frameWidth-anchorX,frameHeight-anchorY)
transform = translate(worldAnchor) then scale(flip ? -scale : scale, scale)
```

`SpritePainter` loads once, disables density scaling/filtering/dithering, caches shared sheets, verifies dimensions and creates numbered-frame atlases. Layout mismatch, missing/invalid PNG or decode failure throws IOException; partially loaded cached bitmaps are recycled. This is image validation, not a general content validator. Selected art can be wider/taller than the body, including transparent padding/sword effects. The body stays 36×64 or 44×70. Camera/display scaling does not change physical reach.

Additional directly loaded environment PNGs: `Samurai_Bundle/Map/Zen_Island/Background_1.png` through `Background_5.png`, `Samurai_Bundle/Map/Zen_Island/Tileset_1.png`, and `TerrainArt.SHEET` (`sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/spritesheet.png`, relative to assets). The Vol.3 sheet must be exactly **512×96**. No background layer JSON is consulted. No PNG directory scanning registers extra moves.

### Playback fields and validation

These are `AnimationClock.play` arguments, supplied by Java calls in `CharacterAnimations`.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `next` | SpriteClip to play | enum reference | Visual actor | Different clip | Must reference an existing enum clip; no string lookup |
| `start` | First selected frame, inclusive | frame index | Visual actor | Starts later in sheet | >=0, <=end |
| `end` | Last selected frame, inclusive | frame index | Visual actor | Larger playback range | <next.frames; invalid ranges throw IllegalArgumentException |
| `speed` | Free-running playback speed (`fps` internally) | frames/s | Visual actor | Faster visual animation | Finite >0; phase-sampled attacks override time advancement |
| `looping` | Repeat or hold final frame | boolean | Visual actor | Not numeric | Loops modulo count/fps, otherwise clamps to last |
| `stateKey` | Token for restarting same clip on new state/strike | integer | Visual actor | Different value restarts playback | Pose ordinal for player; pose×10+strike for enemy |

`play` resets elapsed time only when clip, state key, range, FPS or loop flag changes. `advance(dt)` ignores nonfinite/nonpositive dt, then uses `floor(elapsed*fps+1e-8)`; loop wraps, one-shot holds last. `sample(progress)` clamps finite progress into [0,1], substitutes zero for nonfinite values, and selects `start+min(end-start,floor(progress*(end-start+1)))`. Completion does not dispatch an event or transition the simulation: the next visual state comes from actor state on the next update.

### Every active animation mapping

Evidence: `CharacterAnimations.updatePlayer/updateEnemy`. Ranges are inclusive; “sample” means actual simulation progress chooses the frame regardless of listed nominal FPS.

| Player condition (priority order) | Clip/range | Timing |
|---|---|---|
| Defeated | NINJA_HURT 0..2 | 9 FPS, holds |
| Deathblowing | NINJA_EXECUTION 3..5 | sample deathblowProgress; nominal 12 |
| Dashing | NINJA_RUN 4..7 | 24 FPS, loops |
| Staggered | NINJA_HURT 0..2 | 9 FPS, holds |
| Attacking | NINJA_ATTACK 3..5 | sample attackProgress; nominal 12 |
| DEFLECT feedback | NINJA_GUARD 0..3 | sample impactProgress; nominal 12 |
| HIT / POSTURE_BREAK feedback | NINJA_HURT 0..2 | sample impactProgress; nominal 12 |
| Guarding | NINJA_GUARD 0..7 | 12 FPS, holds |
| Climbing | NINJA_RUN 0..7 | 8 FPS, loops |
| Airborne rising | NINJA_JUMP 0..5 | 12 FPS, holds |
| Airborne falling | NINJA_JUMP 8..11 | 12 FPS, holds |
| Attack recovering and not moving | NINJA_ATTACK 5..5 | holds |
| Moving | NINJA_RUN 0..7 | 12 FPS, loops |
| Otherwise | NINJA_IDLE 0..7 | 12 FPS, loops |

Enemy priority: death → broken posture → attack phases → HP-loss hurt feedback → movement/climbing/airborne → idle. Death uses SAMURAI_DEATH 0..6 at 10 FPS holding corpse; broken posture uses SAMURAI_HURT 0..0 held. HP decrease triggers .18 s hurt feedback with SAMURAI_HURT 0..1 at 12 FPS, unless a higher-priority state masks it. Movement uses SAMURAI_RUN 0..5 at 12 FPS looping; idle SAMURAI_IDLE 0..2 at 8 FPS looping.

| Enemy attack phase | Normal / double first | Double second | Heavy |
|---|---|---|---|
| Windup | SLASH 0..3 | RETURN_SLASH 8..9 | HEAVY 0..1 |
| Active | SLASH 4..7 | RETURN_SLASH 10..13 | HEAVY 2..5 |
| Recovery | SLASH 8..9 | RETURN_SLASH 14..17 | HEAVY 6..10 |

All enemy combat phases sample `phaseProgress()`, nominal FPS=12, nonlooping. Different strike numbers restart playback. Therefore increasing windup slows presentation across that windup range; changing FPS alone does not move the damage event. Player attack progress similarly tracks its .16 s active timer and starts in a striking pose. No animation-frame callbacks or hit events exist. A sword graphic overlapping an actor is insufficient to deal damage; only simulation AABBs matter. A sprite animation reaching its final frame cannot activate the next attack.

### Practical knight art example using only existing support

A **global Samurai-to-knight reskin** can be prepared without changing Java by replacing the six existing Samurai PNGs at exactly the registered paths, preserving the grids, cell sizes, frame counts, anchors and native facing listed above. For example, export a knight normal/double sheet as 240×192 (5 columns ×4 rows of 48×48), place meaningful frames 0..17 in the existing Attack_1 path, and leave tail cells unused. First-swing art should face the native direction of SLASH (`facesRight=false`), while return-swing ranges 8..17 must suit RETURN_SLASH (`facesRight=true`). Export the remaining idle/run/heavy/hurt/death files to their existing layouts as well. Feet/body centers must agree with each registered anchor; visual spacing is authored into the PNG because external offsets are unsupported.

This would visually change **every** enemy that uses these shared clips. It would retain Samurai stats, hitboxes, timing, AI and fixed sequence. It cannot produce a separately selectable `knight`, unique defensive profile, new animation ID, larger physics body or new moves. No asset replacement was performed during this documentation task. If a new pack's natural frame layout differs, re-exporting art to the existing layout is the only asset-only option; arbitrary layouts require changing registration/mapping Java.

## 8. Tiled and world integration

### Supported Tiled schema: none

There are no `.tmj`, `.tsj`, `.tmx`, or `.tsx` game maps/tilesets and no production Tiled reader. Consequently **no Tiled map-level property, layer property, tile property, custom property or gameplay object type is accepted**. There are no recognized spellings/defaults to list. This includes rectangle collision objects, player/enemy spawns, ladders, apertures, nav links, pickups, exits, regions and decorations. Authoring an object named `enemy` or a property named `solid` would have no effect; these are examples of unavailable concepts, not schema names.

| Tiled concept requested | Actual implementation / consequence |
|---|---|
| Decorative vs solid tiles | Visual tiles are draw calls. Collision comes from Java Solid rectangles; images/categories do not determine solidity. |
| Rectangle collision objects | No import. `AreaDefinition.Solid` rectangles are generated from Java platforms. |
| Platforms/navigation surfaces | Explicit Platform array indexed by graph links. Not discovered from tile pixels. |
| Player spawn | Hardcoded x=180, feet=WorldDefinition.GROUND for continuous game. |
| Enemy spawns | `WorldDefinition` Spawn array; identical Enemy class/config and constructor HP. |
| Ladders | Explicit Java Ladder array; top/bottom surface indexes. |
| Floor apertures | Continuous AreaDefinition constructor cuts gaps around ladders' top surfaces automatically. |
| Navigation links | Explicit Java directed Link array; ladder links generated in WorldDefinition.create. |
| Pickups | Two hardcoded Game.upgrades overlaps, not tile/object properties. |
| Exits/level transitions | Position/ground/input logic in Game.exits; decorative doors are not triggers. |
| Regions | Three names from player x/960, not arbitrary rectangles. |
| Decorations | SpritePainter.worldScenery hardcoded coordinates/draw order; no editor objects. |
| Tileset references/GIDs/tile IDs | Not resolved. TerrainArt enum stores PNG pixel rectangles, not GIDs or Tiled IDs. |
| Layer visibility/collision | No imported layers or visibility flags. Hiding/changing art does not remove solid collision. |
| Tile animation/rotation/flip flags | No Tiled animation, transforms or GID flags; character flipping is separate. |
| Infinite/chunked maps, compression, groups, image/object layers | Unsupported import formats. Render cache chunks are an unrelated Java optimization. |
| Polygon/ellipse/polyline collision, object templates, external TSJ | Unsupported. No fallback interpretation. |

### Actual world definition and field reference

Evidence: [WorldDefinition](app/src/main/java/com/lockin/game/WorldDefinition.java) `create`, `connect`; [AreaDefinition](app/src/main/java/com/lockin/game/AreaDefinition.java) records, constructors, `surface`; [AreaState](app/src/main/java/com/lockin/game/AreaState.java) `reset`; [SolidPhysics](app/src/main/java/com/lockin/game/SolidPhysics.java) `player`, `enemy`, `horizontal`, `vertical`, `ladderAt`; [TerrainPhysics](app/src/main/java/com/lockin/game/TerrainPhysics.java) legacy `landing`, `player`; [WorldNavigator](app/src/main/java/com/lockin/game/WorldNavigator.java) `update`.

These are Java constructor fields; no external parser enforces geometry correctness. Arrays are cloned, but record ranges, surface references and reachability are not semantically validated. Authoring valid finite ordered geometry/indexes is required to avoid bad runtime behavior. The five-argument AreaDefinition constructor defaults width=960,height=540,groundY=380,continuous=false; the extended constructor requires all arguments.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `AreaDefinition.name` | Area display name | string | HUD in legacy screens | Different text | Continuous HUD uses WorldDefinition.REGIONS instead |
| `width` | World horizontal bound | px | World/actors | More horizontal room | Continuous completion/render caches also use WorldDefinition constants |
| `height` | World vertical bound / ground solid bottom | px | World/actors | More vertical room | Render cache dimensions are separate compiled constants |
| `groundY` | Stored ground coordinate | px | Area metadata | No direct physics effect from changing this field alone | Current continuous consumers use platform geometry/WorldDefinition.GROUND; legacy uses GameWorld.GROUND_Y |
| `continuous` | Select solid-world vs legacy surface traversal | boolean | World/actors | Not numeric | Also controls aperture generation; Game has its own continuous mode flag |
| `platforms` | Ordered navigation/collision surfaces | Platform[] | World/actors | More authored decks | Index 0 special floor; links/ladders/spawns use array indexes |
| `ladders` | Explicit climb connections | Ladder[] | Both actors | More potential climb routes/gaps | Must reference existing platforms; not auto-detected from art |
| `spawns` | Enemy placement records | Spawn[] | Enemies | More enemies/navigators instantiated | No entity type/profile/animation field |
| `links` | Directed surface navigation graph | Link[] | Enemy AI | More possible routes | No auto-validation of physical reachability |
| `Platform.left` | Deck left bound | px | World/actors | Moves left edge right; narrower if right fixed | Use left<right; no constructor enforcement |
| `Platform.right` | Deck right bound | px | World/actors | Wider deck if left fixed | Within intended world bounds |
| `Platform.top` | Floor plane | px | Both actors/world | Lower floor plane | Elevated solid bottom is top+32 |
| `Platform.oneWay` | Legacy descending/drop-through flag | boolean | Player in legacy fixtures | true enables drop-through when dropping | Continuous Solid generation ignores this field |
| `Ladder.x` | Ladder center line | px | Both actors/world | Moves climb path/hole right | World aperture centered here |
| `Ladder.bottom` | Lower platform array index | index | Both actors/AI | Selects a different lower endpoint | Not a y coordinate; index must exist |
| `Ladder.top` | Upper platform array index | index | Both actors/AI/world | Selects different upper endpoint and aperture | Top floor ordinarily has smaller world y |
| `Spawn.x` | Enemy body left, not center | px | Enemy | Moves spawn right | AreaState places y from surface.top-Enemy.HEIGHT |
| `Spawn.surface` | Platform index for initial enemy feet | index | Enemy | Selects another platform | Does not itself add a navigation link |
| `Link.from` | Source platform index | index | Enemy AI | Different route source | Directed edge; reverse must be authored/generated |
| `Link.to` | Destination platform index | index | Enemy AI | Different route destination | Must exist; surface graph is breadth-first |
| `Link.travel` | Traversal operation | enum | Enemy AI | Not numeric | LADDER, JUMP, DROP, WALK; legacy handling differs |
| `Link.launch` | Traversal launch/ladder alignment center x | px | Enemy AI | Move launch right | For ladders corresponds to ladder.x |
| `Link.destination` | Destination center x | px | Enemy AI | Move landing/walk goal right | Used for continuous WALK/JUMP; not general destination y |
| `Solid.left`, `right`, `top`, `bottom` | Generated axis-aligned collision bounds | px | Both actors/world | Moves an edge; may expand or shrink rectangle | Generated from platforms, not independently imported objects |
| `WorldDefinition.WIDTH`, `HEIGHT`, `GROUND` | Continuous 2880×1080, ground feet y=940 | px | World/actors | Larger bounds/lower floor depending constant | Keep geometry/cache/render/completion consistent |
| `LADDER_WIDTH` | Ladder artwork width 80 | px | World visual | Wider drawn ladder | Does not set entry tolerance or hole width |
| `OPENING_WIDTH` | Real aperture width 112 | px | World/actors/navigation | Wider floor gaps, larger pullout distances | Body widths and navigation offsets matter |
| `ENTRY_RADIUS` | Ladder alignment/entry tolerance 30 | px | Both actors/navigation | More forgiving entry/approach | Additional player top-entry test uses openingWidth/2+4 |
| `HP_X`, `HP_FEET` | HP reward center x=300, feet=284 | px | Player/world | Moves reward right/down | Trigger rectangle x±16, feet-40..feet-8 |
| `POWER_X`, `POWER_FEET` | Power reward center x=1530, feet=220 | px | Player/world | Moves reward right/down | Same trigger size; one collection per run |
| `REGIONS` | Three region names | String[] | HUD | Different text / additional entries not automatically used | Index clamps to 0..2 from floor(player.x/960) |

`groundY` is assigned but not read as a terrain/completion authority in the inspected production runtime. `Solid` bounds are generated state, included here to explain collision rather than suggest a separate authoring schema. Record accessor names include parentheses in Java (`left()`, etc.); the field names in the table are their exact record component names.

### Exact continuous castle content

`WorldDefinition.create()` authors these 16 surfaces. Platform index must remain consistent with ladders, spawn surfaces and graph links if a developer rearranges them.

| Index | Left..right | Top y |
|---|---|---|
| 0 | 0..2880 | 940 |
| 1 | 256..640 | 812 |
| 2 | 320..704 | 668 |
| 3 | 192..576 | 476 |
| 4 | 224..608 | 284 |
| 5 | 1056..1536 | 796 |
| 6 | 1248..1696 | 604 |
| 7 | 1088..1600 | 412 |
| 8 | 1120..1632 | 220 |
| 9 | 2112..2560 | 812 |
| 10 | 2304..2784 | 620 |
| 11 | 2112..2688 | 428 |
| 12 | 2176..2720 | 236 |
| 13 | 640..1152 | 668 |
| 14 | 1664..2272 | 604 |
| 15 | 1568..2176 | 428 |

Ladders `(x,bottom,top)` are `(440,0,1)`, `(560,1,2)`, `(384,2,3)`, `(480,3,4)`, `(1160,0,5)`, `(1392,5,6)`, `(1488,6,7)`, `(1280,7,8)`, `(2240,0,9)`, `(2448,9,10)`, `(2576,10,11)`, `(2352,11,12)`, `(1088,5,13)`, `(2160,9,14)`.

Each ladder generates two directed LADDER links (28 total). `connect` adds bidirectional WALK pairs 2↔13 (centers 672↔736), 6↔14 (1680↔1740), 11↔15 (2140↔2080), and JUMP 7↔15 (1570↔1660): **36 authored directed links total**. Additional same-surface aperture jumps are generated by navigation when needed. Adding a non-ladder surface alone does not generate general walk/jump connectivity.

Enemy spawns `(bodyLeftX,surface)` are `(670,0)`, `(1450,5)`, `(2040,0)`, `(2460,9)`, `(2660,10)`. The player starts at `(180,940-64)` and does not have a Spawn record. All five enemies simulate offscreen and remain dead after defeat until reset. The run has no disk save.

### Automatic geometry versus explicit authoring

For each platform the constructor produces Solid pieces, cutting sorted holes of width 112 around ladders whose `top` equals that platform index in continuous mode. Surface 0 solids extend to world height; elevated solids are 32 px thick. Thus continuous floors block sides and ceilings as well as downward movement. `oneWay` is ignored by this path. Physics resolves axis-separated swept rectangles and bounds; bodies of living actors also block one another independently from terrain and attacks. Corpses do not obstruct; enemies are not player-standing platforms.

The renderer uses these generated solids to draw floor art, which makes the holes visible automatically. Ladders are drawn from the explicit ladder array. Background walls, columns, banners, torches and trees are separate decorative draw calls and do not create collision. Layer visibility is not an authoring control. Changing artwork/metadata cannot create or remove terrain solidity.

Player ladder traversal requires vertical input near an authored ladder. At the top, horizontal input pulls over the opening lip; DOWN on ordinary continuous solid floors does not drop through. Enemy navigation can climb, pull out, jump apertures and follow the directed graph. Climb speed is 140 px/s; jump launch velocity is -590 px/s; some airborne navigation uses 250 px/s. LADDER_WIDTH=80 controls art; OPENING_WIDTH=112 controls real gaps; neither is a Tiled tile size.

### Legacy areas and completion

`AreaDefinition.AREAS` retains three 960×540 screens used by `Game.screenWorld` fixtures. Their elevated `oneWay=true` surfaces use descending landing tests; DOWN away from a ladder initiates a .25 s drop-through. They differ from the production continuous castle and should not be described as its current floor behavior. `Game.combatArena` removes player terrain for historical combat checks. Legacy EnemyNavigator's generic non-LADDER/non-JUMP branch walks toward launch; it is not equivalent to continuous WALK destination handling.

Continuous completion occurs while moving right at x>=2880-Player.WIDTH-1, grounded with feet>=940-.1. It does not require defeating every enemy or touching a door sprite. It freezes simulation. Region transitions are continuous scrolling, with names based on x/960; there is no entrance teleport. Legacy fixtures instead transition at 960-wide boundaries, place the player at a 48 px inset and apply a .4 s transition lock. Neither mode consults an exit object/property.

World coordinates and camera coordinates are separate. `GameWorld` defines the 960×540 logical viewport; Camera follows with DEAD_X=120, DEAD_Y=65 and exponential factor `1-exp(-10*dt)`, clamped to world bounds. Larger dead zones allow more player motion before following. World terrain is cached in 12 480×540 chunks (not Tiled chunks). No map file sets zoom, cache dimensions, culling or parallax.

### Terrain atlas fields (compiled)

Evidence: [TerrainArt](app/src/main/java/com/lockin/game/TerrainArt.java) constructor; `SpritePainter.tile/tilePart/worldScenery`.

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `TerrainArt.SHEET` | Exact atlas PNG path | asset path | World visuals | Different image selected | Must decode as 512×96; no JSON reference resolution |
| `TerrainArt.x`, `y` | Source top-left | art px | World visuals | Selects pixels farther right/down | Java constants; no mapping from JSON labels |
| `TerrainArt.width`, `height` | Source rectangle size | art px | World visuals | Larger source sample, draw target supplied separately | Does not increase collision or world tile size |

Registered rectangles `(x,y,w,h)` are GRASS(336,0,16,16), DIRT(384,0,16,16), LOG(480,64,16,16), TORCH(240,64,16,16), DOOR(32,0,32,32), TOP(112,0,16,16), FILL(160,0,16,16), PLATFORM_LEFT(240,0,16,16), PLATFORM(256,0,16,16), PLATFORM_RIGHT(272,0,16,16), WALL(480,32,16,16), LADDER_TOP(96,64,16,16), LADDER(112,64,16,16), EXIT(160,64,16,16), RED_BANNER(208,64,16,16), BLUE_BANNER(224,64,16,16). Draw targets usually scale 16 px art to 32 world px; ladders/banners/doors have explicit targets. This is not a global configurable tile-size system.

## 9. Designer workflows: what this revision can support

These examples distinguish currently possible operations from work that requires a different game implementation. They do not introduce an invented JSON schema or propose gameplay changes as part of this audit.

| Designer goal | Actual procedure / feasibility |
|---|---|
| Create a new independently selectable knight enemy type | **Unavailable through content.** No knight/entity factory/type ID exists. Requires implementation work or the intended data-driven repository revision. |
| Give knight independent stats | **Unavailable through files.** Java constructors accept max HP; AreaState currently supplies the same HP maximum to every enemy. `new Enemy(config,250)` illustrates the existing constructor API only, not a content entry or implemented type. Other combat/body/movement defaults are shared constants. |
| Assign a defensive profile | **Unavailable through files.** `new CombatConfig(...)` is Java constructor tuning; Enemy uses it for its own Posture. It does not grant enemy block/deflect behavior or define a named profile. |
| Assign unique attacks/combos | **Unavailable through files.** EnemyMove is a shared enum and Enemy cycles all its values; no per-enemy selection or combo registry. |
| Assign unique AI | **Unavailable through files.** Every enemy uses EnemyNavigator plus Enemy state machine, with WorldNavigator in continuous worlds. |
| Integrate a new sprite sheet | Exact-layout global PNG reskin described in section 7 is possible. Independent pack paths/layouts/mappings require Java changes. |
| Spawn knight in Tiled | **Unavailable.** No Tiled importer or supported spawn object/property. Java `new AreaDefinition.Spawn(900,0)` is an example of the existing geometry record, not an entity-type selector. It would create the same Enemy as other spawns if a developer added it to the authored array. |
| Tune HP without changing constants | A Java caller may construct `new Game(CombatConfig.DEFAULT,400,250)` for player 400/all enemies 250. Production GameScreen uses `new Game()`; no file supplies these values. |
| Change tile appearance | Replace pixels in the registered PNG at existing rectangles; metadata labels do not rebind TerrainArt. |
| Change only `spritesheet.json` values | No runtime effect, including changing coordinates, category or image path. |

For an asset-only knight reskin, a human can (1) back up the existing six Samurai PNGs, (2) export the exact compatible grids/anchors/facing, (3) replace those registered PNG paths, (4) run the host validation below, (5) inspect both facing directions, phase transitions, ground contact, corpse pose and visual/body separation on device. That procedure changes appearance globally and preserves the existing mechanics. To author a real independent knight using JSON/Tiled, obtain the revision that implements those schemas first. Neither its schema nor its behavior can be verified from this checkout; any claims about that missing revision are **UNVERIFIED**.

## 10. Misleading, unused and constrained settings

| Setting / assumption | Verified implementation / practical consequence |
|---|---|
| Any field in `spritesheet.json` | No runtime parser/consumer; all 11 field paths are unused by gameplay/rendering. Pack metadata is descriptive only. |
| `meta.count` | Declared count does not validate/register content. See actual entry count in section 3. |
| `category="hazards"`, `"collectibles"`, `"climb"` | Creates no damage, reward or climbing. Those mechanics are separate Java code. |
| SpriteClip constructor `scale` parameter | Parsed as a Java argument but ignored; actual scale is chosen by the path prefix and global constants. There are no “parsed but unused gameplay JSON settings” because no gameplay JSON parser exists. |
| `AreaDefinition.groundY` | Stored constructor value, not the runtime authority for floor physics/completion. Change actual platform geometry and relevant compiled constants only through future authorized implementation work. |
| `Platform.oneWay` | Effective for legacy drop-through/landing; ignored by continuous Solid generation. Setting true in the continuous castle does not produce a one-way floor. |
| `AreaDefinition` header comment “others are one-way decks” | Describes legacy behavior; production continuous floors are fully solid. |
| `TerrainPhysics` one-way documentation | Its public entry dispatches continuous player movement to SolidPhysics; not the production collision contract. |
| `EnemyMove.betweenStrikes` | Has no effect for single-strike normal/heavy moves; relevant only when another strike remains. |
| `EnemyMove.damageMultiplier` | Multiplies both enemy outgoing damage and posture transferred by player block/deflect. Not a target resistance. Heavy is punished with twice the attacker posture on deflect. |
| `CombatConfig.deflectDamage` | Read from player defender config; damages enemy attacker posture. Not defender posture damage. |
| `CombatConfig.blockDamage` | Player-only block transfer; enemy AI cannot block. |
| `deflectWindow`, spam tuning, `deathblowRange` | Read by Player; placing a different config on Enemy does not give it those actions. |
| `postureCapacity`, recovery/delay/break/immunity | Used by both actors through their own Posture instances. |
| `spamRecovery` | Both penalty quiet timeout and unsuccessful hold-length cutoff; changing one affects the other. |
| `failedTaps` reset | Does not restart a held guard press or automatically rewrite its latched window. A successful deflect restores the full window without resetting guardAge. |
| `breakImmunity` | Protects posture accumulation, not HP; does not stop hits resetting quiet timers. |
| `cancelBreak` | Dash retains full/committed posture while ending stagger; differs from natural break expiry's meter reset. |
| `powerUpgrade` | Sets ordinary player hit multiplier to 2; does not boost deflections, blocks or execution damage. |
| Enemy detection radius | Gates new detection/planning, not necessarily an already committed route; zero is a practice/test switch. |
| `Enemy.ATTACK_RANGE` vs every move | Supplies normal slash; double/heavy have independent reach values in EnemyMove. |
| `Enemy.RECOVERY` | Also hardcoded recovery after break expiry, even if the interrupted move was heavy. |
| Animation FPS for attacks | Nominal play argument; actual phase progress sampling determines combat frames. No frame damage events. |
| Animation end / loop setting | Presentation only; cannot drive state transitions, hit timing or combo follow-ups. |
| Sprite size/alpha/anchors | Does not change physical body, attack bounds or eligibility range. Sword art may extend outside the actual hitbox. |
| SpriteClip facesRight | Native art orientation, not initial actor facing; return slash shares an image with opposite metadata. |
| `LADDER_WIDTH` | Visual width only; aperture and entry radius are distinct settings. |
| Tile artwork/door artwork | Does not grant solidity or exit trigger. Completion checks position, ground and direction. |
| Additional image files, GIF/Aseprite exports | Not auto-registered; PNGs are loaded by explicit compiled paths. |
| Region names | Three fixed x-bands rather than designer-defined regions. |
| Legacy screens | Test fixtures retained; GameScreen's normal constructor selects continuous world. |
| Existing documentation scale claims | Early SPRITES.md says Ninja 1.6/Samurai 5; its v0.3.1 note mentions a RENDER_SCALE_MULTIPLIER=1.5 that no longer exists. Current final values are 3.4/8.2. Later v0.3.2 note and CASTLE.md agree with source. |
| Existing documentation “no scrolling camera” | Early SPRITES.md describes an older release; Camera and continuous world are now implemented. |
| “Manifest” references in docs/errors | Java descriptor list, not an actual manifest.json. |

Player-only actions/settings: guard/anti-spam, dash timers/iframes, execution duration/range eligibility, ordinary attack input/cooldown, run upgrades. Enemy-only actions/settings: detection, engagement gap, fixed EnemyMove sequence, navigation/yield behavior. Shared: posture implementation, collision and normalized health/posture bars; art mappings remain separately hardcoded. Settings with no active relevant action have no effect: block/deflect tuning on an enemy does not enable defense; betweenStrikes on a one-strike move does not create extra hits; playback FPS cannot alter a phase-sampled attack.

## 11. Validation and limits

### Checks for future authorized content/art changes

From repository root, with JDK 17 or later on PATH:

```sh
./tools/test-core.sh
# Includes ./tools/test-render.sh; a separate repeat is unnecessary.
```

The script compiles production Java into a temporary directory and runs existing deterministic simulation, input, animation/asset, geometry/navigation and renderer checks. It requires no APK build. It verifies registered PNG layouts/source sampling and animation behavior, but is not a schema validator for hypothetical JSON/Tiled content.

This audit ran the script with the provided JDK 21. Results: **1,737 checks passed** (15 core, 49 enemy, 73 guard/posture, 18 controls, 30 touch adapter, 115 refinement, 28 body collision, 87 dash, 178 moveset, 526 animation/assets, 130 world, 156 timing, 144 renderer, 188 castle). Renderer instrumentation reported a maximum of 11 continuous-world background/scenery draw commands and 12,441,600 cache bytes. The total agrees with the existing README/CASTLE.md reports. No APK build or lint build was run for this documentation-only task.

For a human art test, verify idle/run and jump/fall; both facing directions; ordinary player attack contact immediately; normal/double/heavy preparation and each active phase; guard/deflect; posture break/expiry/dash escape; deathblow kill versus execution pose; hurt/death/corpse; art feet versus body boxes; ladder lip pullout; pickup/reset; and camera clipping. `GameScreen.DEBUG_VISUALS` is currently false; enabling it is a Java change, not an asset property, and was not done here.

**UNVERIFIED:** physical-device appearance, touch comfort, GPU performance, gameplay feel, any missing data-driven branch's schemas, and ability to export a particular external knight pack into compatible layouts. Host rendering doubles check commands/transforms rather than actual Android GPU pixels. PNG compatibility is not proof that sword/feet artwork looks right in motion.

### Reproducible read-only audit scope

- Searched production Java and repository assets for JSON/Tiled/manifest/entity/knight loaders and their consumers.
- Read the only structured asset file and traced the actual PNG loading path.
- Traced Player/Enemy/EnemyMove/CombatConfig/Posture/Game for interaction ownership, formulas and timing.
- Traced SpriteClip/AnimationClock/CharacterAnimations/SpritePainter/GameScreen for art registration, playback, transforms and simulation separation.
- Traced WorldDefinition/AreaDefinition/AreaState/TerrainArt/SolidPhysics/TerrainPhysics/EnemyNavigator/WorldNavigator/LadderTraffic/CollisionManager/Camera for geometry, navigation, spawns, exits and rendering.
- Compared existing README and world/sprite/castle/timing documentation with current source.
- Ran existing host checks; no gameplay implementation, JSON value, PNG, prior documentation or mechanics was modified.

### Audit totals

- **Structured content files inspected: 1** (`spritesheet.json`). **Registered gameplay data files: 0; Tiled maps: 0; Tiled tilesets: 0.** Binary asset inventory: 460 PNG, 34 Aseprite, 13 GIF, 2 TXT, 1 MD; these counts are inventory, not a claim to decode all unused exports.
- **Distinct actual content field paths documented: 11**: two root fields (`meta`, `frames`), four `meta` fields, five repeated `frames.<name>` fields. Dynamic entry names are dictionary keys, not additional schema fields. **Gameplay JSON fields: 0.** Supplementary Java setting/argument tables document the actual implementation, not an external content schema.
- Unused/misleading findings include every atlas JSON field, ignored per-entry scale arguments, stored groundY, continuous-mode oneWay, defensive transfer ownership, multiplier direction, dual-purpose spamRecovery, differing dash/natural-break recovery, and stale documentation claims. See section 10 for precise evidence and effects.
- Fully data-driven authoring is prevented by absent registry/parsers/validators, hardcoded entity types/stats, shared enemy moves/AI/animation mappings, compiled world geometry/trigger logic, and absent Tiled integration. New independent knight content cannot be authored through the requested JSON/Tiled workflow in this revision. This manual deliberately provides no invented schema or fixes.
