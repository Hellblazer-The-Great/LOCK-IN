# LOCK-IN data-driven content reference

## Revision, scope and reading conventions

**Base reference verified against remote `main` at `13411a1`**, fetched 2026-11-02. This includes the JSON/Tiled implementation merged by PR #10 (`2c600f5`). This document replaces the historical pre-refactor audit in full. The Android startup path is `GameScreen` → `Content.load(assets)` → `new Game(content)` → `ContentPainter`; historical constructors and `FixtureContent` exist for regression tests and do not supply Android gameplay defaults.

All paths in content examples are relative to `app/src/main/assets`, except PNG references inside definitions: those resolve relative to the declaring JSON/TMJ/TSJ file. Units: gameplay seconds (s), world pixels (px), pixels/second, HP, posture units, dimensionless multipliers. Origin is top-left, +x right, +y down. One Tiled pixel equals one world pixel. Sprite source pixels are separate. “Owner” is the actor referencing the entity/profile; “target” is the actor hit. Table restrictions specify types, inclusive numeric ranges, required/optional status, defaults and references. **R** = required, no default; **O** = optional with the stated fallback. Current shipped values are examples, not parser defaults. For nonnumeric fields, increasing has no meaning; changing the selection/reference has the described effect. Increasing a numeric value has the inverse consequence when decreased unless explicitly stated otherwise.

Evidence links identify parser/validator methods and runtime consumers for each table or closely related field group. Unknown gameplay JSON keys are rejected by `Json.Node.only`; there is no arbitrary extension-property support. Tiled standard metadata is less strict: only the consumed/rejected subset is interpreted, while unknown custom properties on supported gameplay objects/maps/tiles are rejected. Do not confuse editor metadata with gameplay settings.

### Contents

1. Inventory and loading/validation rules
2. Manifest
3. Entity definitions
4. Defensive profiles and exact combat interactions
5. Attacks and timeline geometry
6. Combo follow-ups
7. Enemy behavior
8. Animation definitions and sprite integration
9. Tiled map, layer and gameplay object fields
10. Tilesets, GIDs and unused atlas metadata
11. Practical independent knight example
12. Unused/misleading fields and engine limits
13. Verification, source trace index and audit totals

## 1. Complete content inventory

| File | Registration / role | Shipped definitions |
|---|---|---|
| `manifest.json` | Entry point | schema 1; start level `castle` |
| `entities/player.json` | manifest.entities | `ninja` |
| `entities/enemy.json` | manifest.entities | `samurai` |
| `combat/defense.json` | manifest.defenses | `shinobi_guard` |
| `combat/attacks.json` | manifest.attacks | `player_slash`, `normal_slash`, `double_slash`, `heavy_overhead` |
| `combat/combos.json` | manifest.combos | `player_chain`, `samurai_sequence` |
| `behaviors/enemy.json` | manifest.behaviors | `samurai_melee` |
| `animations/player.json` | manifest.animations | 14 Ninja clips |
| `animations/enemy.json` | manifest.animations | 14 Samurai clips |
| `levels/test_arena.tmj` | manifest.levels: castle | 2880×1088 map, five enemies |
| `levels/tilesets/castle.tsj` | map external tileset | 192 atlas tiles, tile 0 solid |
| `sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/spritesheet.json` | Unregistered pack metadata | 150 named art entries; declared count 151 |

**12 structured files inspected: 11 active files and 1 unused art metadata file.** No additional entity/animation definitions, knight JSON, other TMJ maps or TSJ tilesets exist in this revision. The knight example below is a proposed designer addition using the verified schema, not a claim that knight content ships today. Asset inventory also includes 460 PNG, 34 Aseprite, 13 GIF, 2 TXT and 1 MD; source-art formats and pack docs are not gameplay schemas. Aseprite and GIF files are not loaded by the game.

### Loading, references and validation shared by all content

Evidence: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.load`, `put`, `ref`, `image`, `spawn`; [content/Json.java](app/src/main/java/com/lockin/game/content/Json.java) `parse`, `Node.get/only/range/integer`; [content/ContentSource.java](app/src/main/java/com/lockin/game/content/ContentSource.java) `text/path`; [GameScreen.java](app/src/main/java/com/lockin/game/GameScreen.java) constructor.

Registry IDs must match `[A-Za-z0-9_.-]+` and be unique within their registry. References are case-sensitive and must resolve. Hit IDs must be unique within an attack but do not use the registry-ID regex. Definition files other than entities contain a single required root array named for their registry (`defenses`, `attacks`, `combos`, `animations`, `behaviors`); entity files each contain one root entity object. File arrays are explicit registration: adding a JSON or PNG to a directory does not register it.

The loader reads defenses → animations → attacks → combos → behaviors → entities → levels, then validates animation `next`, player/enemy spawns, tiles, exits and startLevel. All registered definitions are validated even if unused by a spawn. Shared definitions are immutable; actors retain independent mutable HP/posture/timers/AI. All levels are instantiated up front; only the active level is ticked. Enemy/reward state survives leaving and returning to a level; inactive-level simulation is suspended. RESET resets all levels and player upgrades. There is no save or hot reload. Rebuild/reinstall applies bundled content edits; validation alone does not update a running APK.

Strict JSON rejects duplicate keys, trailing input, nonfinite numbers, invalid types and nesting beyond 64. Individual text input is capped at 8 MiB. Paths allow `..` within the asset root but reject root escape, absolute paths, backslashes, colon/URL schemes, NUL and empty paths. PNG validation checks signature/IHDR, dimensions 1..16384 per axis, frame/tile bounds and a unique referenced image budget of 16, 777, 216 pixels (~64 MiB ARGB). Runtime decoding can still fail if the remaining PNG data is corrupt; header validation is not a full decoder. No network asset import occurs. Startup catches errors, logs `LOCK-IN-Content` and displays an error instead of silently falling back.

## 2. `manifest.json`

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.load/files`; [Game.java](app/src/main/java/com/lockin/game/Game.java) constructor/restart

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `schemaVersion` | Schema discriminator | integer | Whole pack | Only schema 1 supported | R; integer exactly 1 |
| `startLevel` | First level and reset destination | level ID | World/player | Chooses different registered level | R string; manifest.levels ID must exist |
| `defenses` | Registered defensive profile files | path[] | Both actors indirectly | More profiles available | R array of strings; each root has defenses[] |
| `entities` | Registered single-entity files | path[] | Both actors | More selectable entity IDs | R array of strings; one entity per file |
| `attacks` | Registered attack files | path[] | Both actors/targets | More attack IDs available | R array; root attacks[] |
| `combos` | Registered combo files | path[] | Player/enemy scheduling | More chain/sequence choices | R array; root combos[] |
| `animations` | Registered animation files | path[] | Visuals | More clips available | R array; root animations[] |
| `behaviors` | Registered behavior files | path[] | Enemy AI | More behavior IDs available | R array; root behaviors[] |
| `levels` | Registered map entries | object[] | World | More persistent level states instantiated | R array of {id,file} |
| `levels[].id` | Registry ID for map and exits | ID | World | Different reference identity | R string; registry regex/uniqueness |
| `levels[].file` | Map file | path | World | Chooses map | R string; must end .tmj; relative to manifest |

## 3. `entities/player.json` and `entities/enemy.json`

Both files share this schema. Copying an entity under a new ID and registering its file creates independently selectable stats; all spawns sharing that ID use its definition but not its mutable state. `kind` chooses player/enemy validation, not a new Java mechanic. The enemy requires the same movement/dash/deathblow numeric fields even where its AI does not use them.

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadEntity/spawn`; [content/EntityDef.java](app/src/main/java/com/lockin/game/content/EntityDef.java) constructor; [Player.java](app/src/main/java/com/lockin/game/Player.java) constructor/update/startDash; [Enemy.java](app/src/main/java/com/lockin/game/Enemy.java) constructor/updateConfigured; [SolidPhysics.java](app/src/main/java/com/lockin/game/SolidPhysics.java) player/enemy; [WorldNavigator.java](app/src/main/java/com/lockin/game/WorldNavigator.java) update

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `id` | Entity registry key | ID | Owner/spawn references | Selects identity | R string; unique regex ID |
| `kind` | Actor role | enum string | Owner | Selects player/enemy validation | R; player or enemy |
| `maxHealth` | Initial/reset HP maximum | HP | Owner | More health before defeat | R integer 1..1e9; bars normalize to max |
| `width` | Physical body width | px | Owner/world contacts | Larger collision/center basis and hitbox origin | R number 1..500; every spawn body must fit map and all ladder openings in its level |
| `height` | Physical body height | px | Owner/world contacts | Larger collision; body top rises at fixed feet | R number 1..500; spawn cannot overlap solids |
| `speed` | Ground movement/chase speed | px/s | Owner | Faster ground travel | R number 0..5000; ninja180/samurai110 shipped |
| `acceleration` | Horizontal approach toward target velocity | px/s² | Owner | Faster acceleration/deceleration when positive | R 0..100000; 0 means instant velocity, not no movement |
| `jumpSpeed` | Magnitude of upward launch velocity | px/s | Owner | Higher jump; launched as negative y velocity | R 0..5000; player input or enemy nav must trigger jump |
| `gravity` | Downward acceleration | px/s² | Owner | Faster fall/shorter jump at fixed launch | R 1..20000 |
| `climbSpeed` | Ladder movement/alignment speed | px/s | Owner | Faster climb/alignment | R 1..2000; needs authored ladder |
| `airSpeed` | Airborne player movement / enemy nav jump horizontal speed | px/s | Owner | Faster air travel | R 0..5000; normal enemy chase uses speed |
| `dash` | Burst parameters object | object | Player owner | See child fields | R object; required and validated on enemy too, unused by enemy AI |
| `dash.duration` | Movement burst length | s | Player owner | Longer dash travel | R .008333..10 |
| `dash.speed` | Dash horizontal speed | px/s | Player owner | More burst travel | R 0..10000; terrain/bodies still block |
| `dash.cooldown` | Time from dash initiation to next eligibility | s | Player owner | Less frequent dashes | R duration..60 |
| `dash.iframeStart` | Inclusive start of health/contact dodge interval | s since dash start | Player owner | Later protection starts; shorter interval at fixed end | R 0..duration |
| `dash.iframeEnd` | Exclusive end of dodge interval | s since dash start | Player owner | Longer protection | R iframeStart..duration; equal endpoints give no interval |
| `deathblowDuration` | Execution action lock after immediate kill | s | Player performer | Longer lock/sampled execution pose | R .008333..10; enemy value unused |
| `combatProfile` | Defensive profile reference | defense ID | Owner and attacker on deflect | Chooses timing and transfer multipliers | R string; must exist; does not grant enemy guarding |
| `maxPosture` | Owner break capacity | posture | Owner | More received posture needed to break | R 1..1e9; overrides profile construction placeholder |
| `postureRecovery` | Owner quiet posture decay | posture/s | Owner | Faster recovery | R 0..1e9; 0 disables decay |
| `attack` | Default attack reference | attack ID | Owner/target | Different ordinary attack | R existing ID; player initiation uses it; enemy AI selects behavior combo instead |
| `combo` | Player buffered ATTACK followups | combo ID | Player owner | Different followup map | O string default empty; nonempty must resolve; enemy field validated but unused |
| `behavior` | Melee AI reference | behavior ID | Enemy owner | Chooses detection/selection/chase | R existing ID on enemy; O empty on player; player value stored but unused/unresolved |
| `animations` | State-to-clip dictionary | object | Owner visual | Changes poses | R; allowed keys below only; each supplied clip ID must exist |

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadEntity`; [ContentAnimations.java](app/src/main/java/com/lockin/game/ContentAnimations.java) updatePlayer/updateEnemy

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `idle` | Standing fallback | animation ID | Owner visual | Chooses a different clip | R for both roles; case-sensitive existing clip |
| `run` | Horizontal movement, climbing; enemy airborne too | animation ID | Owner visual | Chooses a different clip | R for both roles; case-sensitive existing clip |
| `jump` | Player airborne rising | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |
| `fall` | Player airborne falling | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |
| `guard` | Player held defense | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |
| `deflect` | Player successful-deflect feedback | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |
| `dash` | Player burst visual | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |
| `hurt` | Damage feedback; enemy timer .18s | animation ID | Owner visual | Chooses a different clip | R for both roles; case-sensitive existing clip |
| `stagger` | Owner broken posture | animation ID | Owner visual | Chooses a different clip | R for both roles; case-sensitive existing clip |
| `death` | Defeated pose/corpse | animation ID | Owner visual | Chooses a different clip | R for both roles; case-sensitive existing clip |
| `deathblow` | Player execution pose | animation ID | Owner visual | Chooses a different clip | R player; O enemy, unused by enemy AI; case-sensitive existing clip |

Current values: Ninja body 36×64, maxHP 100, posture 100/recovery 12, speed 180/air 180, jump 590, gravity 1500, climb 140; Samurai body 44×70, HP 100, posture 100/recovery 12, speed 110/air 250, jump 590, gravity 1500, climb 140. Both files contain dash .18 s/950 px/s/.45 s cooldown with [.04, .14) iframes, but enemy AI never invokes dash. These are file values, not optional defaults.

Changing body dimensions changes physics, attack-edge origin, detection centers and deathblow gap. It does not rescale art or hitbox dimensions; tune animation anchors/scale and attack rectangles independently. Initial point Y is feet, so body top is feet-height. Content validation requires each spawn to fit bounds, avoid solid overlaps and have width strictly smaller than **every ladder opening in that level**, even a ladder it would never reach.

## 4. `combat/defense.json`

Root `defenses` is a required array. All profile fields below are required; none silently falls back. Profile values are copied into each entity's CombatConfig together with that entity's maxPosture/postureRecovery. The profile loader's temporary capacity 100/recovery 12 and legacy block/deflect amounts 0 are not gameplay defaults for configured actors.

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadDefenses/loadEntity`; [content/DefenseDef.java](app/src/main/java/com/lockin/game/content/DefenseDef.java) forEntity; [CombatConfig.java](app/src/main/java/com/lockin/game/CombatConfig.java) constructor; [Player.java](app/src/main/java/com/lockin/game/Player.java) receiveAttack/setGuard/canDeathblow; [Posture.java](app/src/main/java/com/lockin/game/Posture.java) add/update/cancelBreak

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `defenses` | Profile collection | object[] | Registry | More choices | R array; objects follow below |
| `id` | Profile registry key | ID | References | Different identity | R string; unique regex ID |
| `deflectWindow` | Fresh guard deflection window | s | Player defender owner | More time to deflect | R 0..10; shipped .32; inclusive contact test with tolerance |
| `recoveryDelay` | Quiet wait before posture decay | s | Owner, both roles | Posture persists longer | R 0..60; shipped1 |
| `brokenDuration` | Posture-break duration | s | Owner; enemy vulnerability/player stagger | Longer stagger or deathblow opportunity | R 1/120..60; shipped1 |
| `spamStep` | Window reduction per failed short guard | s/failure | Player defender owner | More severe tap penalty | R 0..10; shipped .05 |
| `minimumDeflectWindow` | Lower bound of penalized window | s | Player defender owner | More forgiving floor | R 0..10 and <=deflectWindow; shipped .05 |
| `spamRecovery` | Failure quiet reset and short-guard cutoff | s | Player defender owner | Penalty lasts longer; more holds count short | R .001..60; shipped .8 |
| `breakImmunity` | Post-break posture immunity | s | Owner, both roles | Longer protection from posture accumulation | R .001..60; shipped1; does not protect HP |
| `deathblowRange` | Maximum horizontal body gap | px | Player performer owner; enemy recipient | Longer execution reach | R 0..2000; shipped90; enemy owner value unused |
| `blockPostureMultiplier` | Scale attacking move blockDamage | multiplier | PLAYER DEFENDER owner posture | More posture received on block | R 0..100; shipped1; enemy owner value unused |
| `deflectPostureMultiplier` | Scale attacking move deflectDamage | multiplier | ENEMY ATTACKER posture when player owner deflects | More posture dealt back to attacker | R 0..100; shipped1; enemy owner value unused |

### Defensive ownership — exact formulas

For configured Player P receiving configured Enemy E's current attack A, `Player.receiveAttack` consults **P's entity/profile**, not E's profile, for block/deflect multipliers and P's guard timing. `Enemy.blockDamage/deflectDamage` supply **A's amounts**. E's profile separately governs E's own posture recovery/break/immunity. There is no automatic symmetric defensive behavior.

```text
if E's active hit overlaps P and is not yet consumed:
    if P is dash-invulnerable: consume event; no pressure/damage/defense feedback
    else:
        reset both actors' posture quiet timers
        if P.guarding && A.deflectable && guardAge <= latchedWindow + 1e-7:
            E.posture += A.deflectDamage * P.profile.deflectPostureMultiplier
            P loses no HP/posture
            reset failed tap penalty; restore full P deflect window (not press age)
        else if P.guarding && A.blockable:
            P.posture += A.blockDamage * P.profile.blockPostureMultiplier
            P loses no HP
        else:
            P.health -= A.damage
            P.posture += A.postureDamage
    consume current hit event
```

Deflect is tested first and independently of blockable: deflectable=true/blockable=false still allows a timely deflect; outside its window the hit bypasses guard. deflectable=false/blockable=true permits blocking even during the nominal deflect window. Both false bypass guard. Guard facing is irrelevant.

Player ordinary hit on Enemy has no enemy guard test: HP=`min(INT_MAX,A.damage * playerPower)`; posture=`A.postureDamage * playerPower`. Power pickup sets `playerPower=max(existing,amount)`, not repeated multiplication. Defensive amounts are not power-scaled. Enemy outgoing health/posture/block/deflect numbers come directly from its AttackDef; `style=heavy` adds no hidden multiplier. Shipped normal/double are 20 HP/25 posture, block 25, deflect 35; heavy explicitly stores 40/50/50/70. Player slash stores 25/25. If player deflectPostureMultiplier=2, a heavy deflect adds 140 to enemy posture; raising the **enemy's** same multiplier does nothing to that transfer. Raising player's block multiplier to 2 makes heavy block add 100 to player posture.

Both components of a hit are committed even when health becomes lethal; later corpse contacts do nothing. Health clamps at zero; positive posture clamps at capacity. Player attacks resolve before enemy attacks within the tick, so a lethal player hit cancels that enemy's response. Each hit event consumes once per execution, including dash-invulnerable contact; a lingering box cannot hit again after iframes end. A single player event contacts at most one enemy because consumption occurs during enemy iteration.

### Posture decay, break, grace

`Posture.add` ignores nonfinite/nonpositive amounts. Positive pressure resets quietTime even while broken/immune; no meter is added in either state. Otherwise meter=min(capacity, meter+amount); capacity triggers brokenDuration. Recovery integrates only time after the quiet delay:

```text
eligible = max(0, quietTime+dt-recoveryDelay) - max(0, quietTime-recoveryDelay)
posture = max(0, posture-postureRecovery*eligible)
quietTime += dt
```

Natural break expiry resets posture/quietTime to zero and grants breakImmunity (accounting for timestep leftover). New contacts cannot extend brokenDuration. Health remains vulnerable while broken/immune. Player stagger suppresses guarding, normal movement, attack and jump but gravity/collision continue. Enemy break interrupts remaining hits and resolves the current move once. It then uses **behavior.staggerRecovery**, not the interrupted attack's recovery length.

Dash escape `Posture.cancelBreak` instead retains committed posture, ends stagger and grants grace. Subsequent quiet decay uses owner recovery values. Posture grace is not dash invulnerability. An amount of zero after multiplier 0 still encounters the surrounding receiveAttack pressure/reset logic.

### Anti-spam details

`Player.finishGuardAttempt/failTaps/setGuard/updateDefense` classify release of an unsuccessful guard with age<spamRecovery as one failed tap. Blocking without deflecting is still “unsuccessful” for this classification. Holds at least spamRecovery avoid failure classification. Input counts every press, including complete taps between render frames; extra presses can add extra failures.

```text
latchedWindow = max(minimumDeflectWindow, deflectWindow - failedTaps*spamStep)
failedTaps capped at 1,000,000
on each failed tap: quietTapTime=0
on quietTapTime >= spamRecovery: failedTaps=0
on successful deflect: failedTaps=0; latchedWindow=deflectWindow; quietTapTime=0
```

Holding guard does not restart its press age. Quiet recovery clears failedTaps but does not rewrite an already latched guardWindow. Successful deflection restores the full window without resetting guardAge. Guard held through stagger/dash resumes ordinary blocking; a fresh press is needed for another deflect window. No guard input cooldown or guard activation delay is configured.

### Deathblow

Only Player performs `canDeathblow/tryDeathblow(Enemy)`. Enemy is the recipient. Both must be alive; enemy posture must be broken; player cannot be staggered/dashing/executing. Strict vertical body overlap and facing toward enemy center are required. Horizontal gap=`abs(enemyCenterX-playerCenterX)-(player.width+enemy.width)/2` must be <= **player profile deathblowRange**. Enemy profile brokenDuration supplies the opportunity; player entity deathblowDuration supplies the execution lock.

A fresh ATTACK edge attempts deathblow before DASH and again after player movement. Existing active attacks never automatically become executions. An eligible deathblow ignores ordinary attack cooldown, kills immediately via `enemy.takeDamage(enemy.health)`, cancels the player's attack, and starts a non-cancellable execution lock. It resets configured attack cooldown to the player's default attack cooldown. It does not wait for a particular sprite frame. There is no HP threshold or animation-derived range. Enemy deathblowDuration/range have no AI consumer.

## 5. `combat/attacks.json`

Root `attacks` is required. Each execution owns its AttackRun age and per-hit consumed flags. Multiple ordered hit events create multi-strikes, independently blockable/dodgeable through the attack's common flags; per-hit flags are not supported; optional per-hit damage transfers are described in the foundation extension below. Runtime interval/cancel comparisons use ~1e-7 numeric tolerances around the intended [start, end) bounds.

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadAttacks`; [content/AttackDef.java](app/src/main/java/com/lockin/game/content/AttackDef.java) canDash; [AttackRun.java](app/src/main/java/com/lockin/game/AttackRun.java) start/advance/phase/strike/canHit/left/progress/event; [Player.java](app/src/main/java/com/lockin/game/Player.java) updateConfiguredAttack/tryHit/receiveAttack; [Enemy.java](app/src/main/java/com/lockin/game/Enemy.java) updateConfigured/tryHit/receiveHit

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `attacks` | Attack collection | object[] | Registry | More moves available | R array |
| `id` | Attack registry key | ID | Both actor references | Different move identity | R unique regex ID |
| `style` | Telegraph/compatibility category | enum | Visuals | Different cue category | R normal/double/heavy; no hidden damage/strike multiplier |
| `damage` | Unblocked health loss per hit event | HP | Target | More HP loss | R integer0..1e9; player ordinary hits multiplied by power |
| `postureDamage` | Unblocked posture per hit event | posture | Target | Earlier target break | R number0..1e9; player ordinary hits multiplied by power |
| `blockDamage` | Blocked attack posture transfer | posture | Player defender | More defender posture on block | R0..1e9; scaled by defender profile |
| `deflectDamage` | Deflected attack posture transfer | posture | Enemy attacker | More attacker posture on deflect | R0..1e9; scaled by player defender profile |
| `duration` | Whole execution from initiation | s | Actor scheduling | Longer occupied/recovery period at fixed hits | R .008333..60; hits and cancel windows must fit |
| `cooldown` | Minimum restart delay from initiation | s | Actor scheduling | Less frequent ordinary starts | R0..60; player also waits for run completion; buffered combo can bypass |
| `directional` | Mirror hitbox with facing | boolean | Target geometry | false fixes right-edge basis | O true; no effect on sprite mirroring |
| `blockable` | Allow held guard block branch | boolean | Player defender | true can prevent HP loss | O true; does not enable enemy guard |
| `deflectable` | Allow timed guard deflect branch | boolean | Player defender/enemy attacker | true enables reflected posture | O true; independent of blockable |
| `interruptOnHit` | Cancel owner current attack on ordinary incoming hit | boolean | Owner | true makes offense interruptible | O false; evaluated on struck actor current attack, not incoming attack |
| `hits` | Ordered independently consumed hit events | object[] | Targets | More opportunities to hit | R1..32; sorted non-overlapping intervals; fields below |
| `hits[].id` | Event identity inside move | string | Attack tracking/designer | Different identity | R unique within attack; ID regex not applied here |
| `hits[].start` | Hit activation age | s | Target/actor windup | Later hit / longer preparation | R >=previous hit end (first >=0), <=duration |
| `hits[].end` | Hit deactivation age | s | Target | Longer active interval at fixed start | R start+1/120..duration |
| `hits[].forward` | Offset from facing body edge | px | Target hitbox | Moves box farther forward | R -2000..2000; non-directional uses right edge |
| `hits[].top` | Hitbox top relative to actor top | px | Target hitbox | Moves box downward | R -2000..2000; negative reaches above |
| `hits[].width` | Hitbox horizontal span | px | Target hitbox | Wider reach | R1..2000; distinct from body/art width |
| `hits[].height` | Hitbox vertical span | px | Target hitbox | Taller reach | R1..2000 |
| `hits[].windupAnimation` | Preparation phase clip | animation ID | Owner visual | Different pose sequence | R existing clip; may never play when start=0 |
| `hits[].activeAnimation` | Active phase clip | animation ID | Owner visual | Different strike pose sequence | R existing clip; cannot trigger damage |
| `dashCancel` | Allowed cancellation windows | object[] | Player owner | More windows allow dash more often | R array, empty forbids ordinary attack dash cancel; enemy AI unused |
| `dashCancel[].start` | Inclusive cancel eligibility start | s | Player owner | Later cancellation access | R0..duration |
| `dashCancel[].end` | Exclusive cancel eligibility end | s | Player owner | Longer access | R start..duration; equal gives empty window |
| `recoveryAnimation` | After last hit clip | animation ID | Owner visual | Different follow-through | R existing clip; phase can be masked by player guard/run/air feedback |

Hitbox formula: if directional and facing left, left=`actor.x-forward-hit.width`; otherwise left=`actor.x+actor.width+forward`. Right=left+hit.width; top=actor.y+hit.top; bottom=top+hit.height. Overlap is strict AABB (edges touching alone miss). Negative forward can cover/extend behind the body. With directional=false, the right-edge coordinate basis stays fixed even when sprite flips left.

AttackRun starts at age 0; it advances once per simulation tick until duration. Before a hit start it is WINDUP; between that start/end ACTIVE; gaps before subsequent hits are the next WINDUP; after the last end it is RECOVERY. Each event can miss at first and contact later in its interval. Sprite frames do not enable boxes. AttackRun.event exposes `hitbox_on`, `hitbox_off`, `complete` boundary queries; no production effect/audio dispatcher consumes them.

Current timelines (seconds):

| Attack | Hit intervals | Total duration / cooldown | Health / posture / block / deflect | Box forward,top,width,height |
|---|---|---|---|---|
| player_slash | [0,.16) | .45 / .45 |25/25/25/35|0,10,68,46|
| normal_slash | [.50,.68) |1.38 /0|20/25/25/35|0,10,64,52|
| double_slash | [.40,.54), [.82,.96) |1.61 /0|20/25/25/35 per event|0,10,54,52|
| heavy_overhead | [.85,1.07) |2.07 /0|40/50/50/70|0,-32,48,102|

Shipped attacks all directional/blockable/deflectable=true, interruptOnHit=false. Player slash allows dash cancellation [0, .45); enemy attack cancel arrays are empty. A player ordinary attack cannot restart until both the current run is done and its configured cooldown expires; lowering cooldown below duration does not overlap attacks. Enemy restarts also depend on behavior.betweenMoves and normal melee eligibility. Dash cancels only inside configured windows unless escaping stagger; it preserves damage already committed and the cooldown. Deathblows cannot be cancelled regardless of windows.

`interruptOnHit` is read on the **struck actor's current attack**. For player, only ordinary HIT feedback (not block/deflect) invokes that optional interruption; stagger/death always cancel. Enemy.receiveHit cancels its running interruptible attack and adds behavior.staggerRecovery. Deflection alone only interrupts if it breaks posture. A source attack with interruptOnHit=true does not automatically interrupt its target.

## 6. `combat/combos.json`

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadCombos/loadBehaviors`; [Player.java](app/src/main/java/com/lockin/game/Player.java) updateConfiguredAttack/beginAttack; [Enemy.java](app/src/main/java/com/lockin/game/Enemy.java) select/updateConfigured

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `combos` | Combo definitions | object[] | Registry | More choices | R array |
| `id` | Combo registry key | ID | References | Different selection | R unique regex ID |
| `sequence` | Enemy selectable/repeating attack IDs | attack ID[] | Enemy scheduling | More moves in sequence/weighted pool | R nonempty; every ID must exist; player does not consume sequence |
| `followups` | Buffered ATTACK edges | object[] | Player scheduling | More source transitions available | R array; one record per source attack; enemy does not consume |
| `followups[].from` | Current attack requiring input | attack ID | Player | Different source move | R existing; unique from per combo; need not appear in sequence |
| `followups[].to` | Next move to execute | attack ID | Player/target | Different followup | R existing; need not appear in sequence |
| `followups[].open` | Input buffering opens | s since source initiation | Player | Later opening / smaller window | R0..source.duration; inclusive with tolerance |
| `followups[].close` | Input buffering closes | s since source initiation | Player | Longer input window | R open..source.duration; inclusive with tolerance |
| `followups[].executeAt` | Buffered move initiation time | s since source initiation | Player | Later continuation | R close..source.duration |

`Player.updateConfiguredAttack` checks a press against the running attack's age **before** advancing it that tick. If age is in [open, close], it stores the followup; after advancing, at executeAt it starts `to` even if the prior attack has remaining duration/cooldown. This starts a fresh consumed-event set and replaces the prior run. Otherwise a press while busy/cooling down is discarded. Dash/stagger/death/travel cancel buffered followups. There is one ATTACK input type, not a branching button grammar. `sequence` does not choose the player's initial move; entity.attack does. Enemy behavior copies combo.sequence and ignores its followups.

Shipped player_chain has sequence[player_slash] and empty followups; samurai_sequence has [normal_slash, double_slash, heavy_overhead] and empty followups. To enable a player continuation using existing schema:

```json
{"from":"player_slash","to":"player_slash","open":0.08,"close":0.15,"executeAt":0.2}
```

Place it inside player_chain.followups. It starts a new player_slash at .2 s when buffered; it does not replay an already-consumed contact.

## 7. `behaviors/enemy.json`

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadBehaviors`; [content/BehaviorDef.java](app/src/main/java/com/lockin/game/content/BehaviorDef.java) constructor; [Enemy.java](app/src/main/java/com/lockin/game/Enemy.java) updateConfigured/select/resolveConfigured/seed/canChase; [WorldNavigator.java](app/src/main/java/com/lockin/game/WorldNavigator.java) update/walk; [AreaState.java](app/src/main/java/com/lockin/game/AreaState.java) reset

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `behaviors` | Behavior collection | object[] | Registry | More AI configurations | R array |
| `id` | Behavior registry key | ID | Enemy owner | Different behavior reference | R unique regex ID |
| `type` | Supported AI family | enum | Enemy | Only melee accepted | R string exactly melee; validation discriminator, not script |
| `selection` | Attack choice strategy | enum | Enemy | sequence repeats; weighted samples | R sequence or weighted |
| `combo` | Attack pool/sequence | combo ID | Enemy | Different allowed moves | R existing ID; uses sequence, ignores followups |
| `weights` | Relative attack probabilities | number[] | Enemy | More relative chance for corresponding entry | R length=combo.sequence length, each0..1e6, sum>0; ignored by sequence but still validated |
| `detectionRadius` | Center-to-center awareness circle | px | Enemy | Detects farther including height | R0..32768; 0 disables detection; existing committed route may continue |
| `engagementDistance` | Maximum body gap for initiating melee | px | Enemy/player target | Begins windup farther away | R0..2000; needs hardcoded height-overlap, grounded, not navigating/climbing |
| `betweenMoves` | Pause after resolved move | s | Enemy | Slower next move | R0..60; cooldown/eligibility also apply |
| `staggerRecovery` | Additional recovery after posture break or interruptible hit | s | Enemy | Longer recovery lock | R0..60; independent of defense brokenDuration |
| `chase` | Enable pursuit/new navigation planning | boolean | Enemy | false disables normal pursuit | O true; attack in range still allowed; committed/yield motion has separate handling |
| `facePlayer` | Track target facing outside attacks | boolean | Enemy visual/attack direction | false retains previous facing in local attack AI | O true; navigation walk separately faces travel direction |
| `seed` | Deterministic weighted selection seed | integer | Enemy | Changes sequence realization, not quality/difficulty | R1..2147483647; per-spawn hash XOR salt |
| `transitions` | Required supported state conditions | string[] | Validation only | Cannot add states/conditions | R exact ordered detect,in_melee_range,timeline_complete,posture_broken,health_zero; not stored as runtime script |

The configured state machine is IDLE/CHASE/WINDUP/ACTIVE/RECOVERY/POSTURE_BROKEN/DEAD. Sequence resolves once on completion or interruption; a miss still resolves. Weighted selection uses a deterministic linear-congruential stream, weights normalized by their sum, salted with spawn name hash (`AreaState.reset` → Enemy.seed). Equal weights do not make selection adaptive; sequence ignores weights/seed for attack choice. Restart reproduces per-spawn seeded behavior. All attacks commit facing through their timeline.

Enemies can chase, climb authored ladders, physically jump authored routes/apertures, and yield at ladder exits. They cannot block, deflect, dash or execute deathblows. Referencing a defensive profile/dash values/extra animation states does not add those actions. Navigation uses breadth-first named-surface links, not a tile navmesh. Same-surface aperture jumps and ladder links are automatic; arbitrary platform jumps require navigation objects. New planning checks detection/chase; a route already committed may continue outside detection. Route stall timeout 2.5 s and yield timeout 1.5 s are hardcoded. FacePlayer=false does not prevent navigation from facing motion. Body contact does not itself cause damage.

## 8. `animations/player.json`, `animations/enemy.json` and additional packs

Both files use root `animations`, a required array. New files with the same schema can be registered in manifest.animations. PNGs are referenced directly by clips/frames and require no separate image registry. Registry IDs are global across files, so use distinct prefixes for new packs.

Implementation: [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) `Loader.loadAnimations/image`; [content/AnimationDef.java](app/src/main/java/com/lockin/game/content/AnimationDef.java) constructor/frameAt; [ContentAnimationClock.java](app/src/main/java/com/lockin/game/ContentAnimationClock.java) play/advance/sample/event; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) character/visible

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `animations` | Clip collection | object[] | Registry/visuals | More registered clips | R array |
| `id` | Animation registry key | ID | Visual references | Chooses clip identity | R unique regex ID |
| `asset` | Default image for integer/frame-object entries | PNG path | Visual clip | Different source image | O empty; resolved relative to this animation JSON; required in effect if any nonplaceholder frame lacks override |
| `frameWidth` | Default grid cell/source width | art px | Visual clip | Wider cells/sample | R integer1..4096 even if explicit frames override |
| `frameHeight` | Default grid cell/source height | art px | Visual clip | Taller cells/sample | R integer1..4096 even for explicit frames |
| `columns` | Grid columns for index conversion | count | Visual clip | Changes row-major mapping | R integer1..4096; sheet need not exactly equal columns*frameWidth if all rectangles fit |
| `frames` | Playback order, allowing skips/repeats/per-file frames | array | Visual clip | More frames; longer free duration at fixed fps | R1..1024 entries; integer indexes or frame objects |
| `frames[] (integer)` | Row-major source grid index | index | Visual clip | Samples later cell | Integer0..1e6; x=(index%columns)*frameWidth,y=floor(index/columns)*frameHeight |
| `frames[].index` | Grid index fallback for missing x/y | index | Visual clip | Samples later cell when not overridden | O integer0..1e6 default0 |
| `frames[].asset` | Per-frame source override | PNG path | Visual clip | Different PNG | O clip asset; relative to same animation JSON, not to clip asset directory |
| `frames[].x` | Explicit source rectangle left | art px | Visual clip | Samples farther right | O integer0..16384; default from index/columns |
| `frames[].y` | Explicit source rectangle top | art px | Visual clip | Samples farther down | O integer0..16384; default from index/columns |
| `frames[].width` | Explicit source rectangle width | art px | Visual clip | Wider drawn source at same scale | O integer1..4096 default frameWidth |
| `frames[].height` | Explicit source rectangle height | art px | Visual clip | Taller drawn source at same scale | O integer1..4096 default frameHeight |
| `frames[].duration` | Override this playback entry time | s | Visual clip | Slower/longer entry and clip | O .001..60 default1/fps |
| `fps` | Default frame rate | frames/s | Visual clip | Faster free playback; uniformly scaling all phase frames does not move hit timing | R .01..1000; ignored per frame if duration overridden |
| `loop` | Repeat instead of holding/completion transition | boolean | Visual clip | true repeats | O false; sampled progress1 can wrap a looping clip to frame0 |
| `facesRight` | Native art orientation | boolean | Visual clip | Toggling reverses flip decision | O true; mirrored if actor.facingRight differs |
| `anchorX` | Body-center/feet reference inside each source rectangle | art px | Visual clip | Right-facing art moves left relative to body center | R -4096..4096; clip-wide, not absolute sheet coordinate |
| `anchorY` | Feet reference inside source rectangle | art px | Visual clip | Art moves upward relative to feet | R -4096..4096 |
| `scale` | Uniform source-to-world size | world px/art px | Visual clip | Bigger art, unchanged physics | R .01..30; actually honored by ContentPainter |
| `events` | Queryable presentation event list | object[] | Presentation API only | More potential event queries | O empty; sorted by at; no production dispatcher |
| `events[].name` | Event query key | string | Presentation API only | Different event name | R string; names beginning hitbox_ rejected; arbitrary other names do not add effects |
| `events[].at` | Event location in clip time | s | Presentation API only | Later query crossing | R0..sum(frame seconds), inclusive |
| `next` | Clip after free nonlooping completion | animation ID | Visual clip | Different completion pose | O empty; nonempty must resolve; no cycle rejection; advance limits1024 transitions per call |
| `placeholder` | Render rectangle and skip image validation/loading for clip | boolean | Visual clip | true replaces art with rectangle | O false; frame/timing/schema validation still applies |

For ordinary frames, source x+width/y+height must fit the actual referenced PNG. Explicit x/y override their index-derived coordinate independently. There is no separate frame-count field; valid cells are exactly those selected by the list. Transparent/blank cells are not rejected by the production loader, so exclude them when exporting. There is no automatic PNG folder enumeration or `%02d` expansion in this schema: each separate PNG frame needs a frame object with its asset path.

### Frame timing, next transitions and events

Clip duration=sum(frame seconds). `AnimationDef.frameAt` accumulates entry durations, chooses the first endpoint after age and holds last for a nonlooping clip. Free playback uses `ContentAnimationClock.advance(dt)`: looping wraps via frameAt; nonlooping can follow `next`, preserving leftover time, otherwise holds the final frame. `next` does not change entity/game state. Cyclic `next` chains are permitted references, bounded by 1024 transitions per advance.

Phase playback uses `sample(progress)`: age=clip.duration*clamp(progress, 0, 1). Thus relative frame durations distribute a fixed windup/active/recovery phase. Multiplying every duration by the same factor (or uniformly changing fps) leaves the distribution unchanged. Changing one frame's duration changes its share of the phase. **Damage remains owned by attack hits[].start/end.** Sample does not follow next; use loop=false on phase clips if a final-frame hold is desired. Player deathblow also samples execution progress; player deflect/hurt samples feedback progress. No frame event can damage, move actors or extend a hitbox.

Animation `events` are available through ContentAnimationClock.event(name), including skipped visual frames and loop repetitions. AttackRun separately offers hitbox_on/off/complete timeline queries. Searching production sources shows no caller dispatching either query API into sound/effects/gameplay. Declaring `footstep` does not play audio. Events are supported as queryable metadata, not an implemented event-response scripting system.

### Visual priorities and supported states

Evidence: ContentAnimations.updatePlayer/updateEnemy. Player order: death → deathblow → dash → stagger → active/windup attack phase → deflect feedback → hurt feedback → guard → climbing(run) → airborne jump/fall → moving(run) → attack recovery → idle. Consequently movement/guard/air/feedback can mask **player recovery** art while its attack clock continues. Enemy order: death → posture break → all running attack phases → .18 s HP-loss hurt feedback → moving/climbing/airborne run → idle. Attacks mask enemy hurt art. Restart/level transition revision clears clocks; different attack serial, strike and phase restart phase clips. Pause stops presentation updates.

Entity animation state names are a closed vocabulary; there is no `attack` state key. AttackDefs select windupAnimation/activeAnimation/recoveryAnimation separately. Additional entity keys are rejected; enemies can carry optional player keys but their animation selector never chooses them. Death clips should normally be nonlooping with no next to retain a corpse; the schema allows alternatives deliberately.

### Current clip mappings (verified from both files)

Paths in this table are relative to assets/sprites. Ninja entries use individually listed PNG files; numerical ranges below are filename ranges, not a runtime sequence-pattern feature. Samurai entries use sheet indexes. All Ninja anchors 30, 56/scale 3.4/native-right; all Samurai scale 8.2. FPS values and loops are file values, not defaults.

| Clip ID | Source / frames | Cell / columns | FPS / loop | Anchor / native direction |
|---|---|---|---|---|
| ninja_idle | DarkNinja/frames/Idle/ 01.png,02.png,03.png,04.png,05.png,06.png,07.png,08.png | 64×64 / 1 | 12 / true | 30,56 / right |
| ninja_run | DarkNinja/frames/Move/ 01.png,02.png,03.png,04.png,05.png,06.png,07.png,08.png | 64×64 / 1 | 12 / true | 30,56 / right |
| ninja_jump | DarkNinja/frames/Jump/ 01.png,02.png,03.png,04.png,05.png,06.png | 64×64 / 1 | 12 / false | 30,56 / right |
| ninja_fall | DarkNinja/frames/Jump/ 09.png,10.png,11.png,12.png | 64×64 / 1 | 12 / false | 30,56 / right |
| ninja_guard | DarkNinja/frames/Defend/ 01.png,02.png,03.png,04.png,05.png,06.png,07.png,08.png | 64×64 / 1 | 12 / false | 30,56 / right |
| ninja_deflect | DarkNinja/frames/Defend/ 01.png,02.png,03.png,04.png | 64×64 / 1 | 12 / false | 30,56 / right |
| ninja_dash | DarkNinja/frames/Move/ 05.png,06.png,07.png,08.png | 64×64 / 1 | 24 / true | 30,56 / right |
| ninja_hurt | DarkNinja/frames/Hurt/ 01.png,02.png,03.png | 64×64 / 1 | 12 / false | 30,56 / right |
| ninja_stagger | DarkNinja/frames/Hurt/ 01.png,02.png,03.png | 64×64 / 1 | 9 / false | 30,56 / right |
| ninja_death | DarkNinja/frames/Hurt/ 01.png,02.png,03.png | 64×64 / 1 | 9 / false | 30,56 / right |
| ninja_deathblow | DarkNinja/frames/Attack2/ 04.png,05.png,06.png | 96×64 / 1 | 12 / false | 30,56 / right |
| ninja_prepare | DarkNinja/frames/Attack1/ 01.png,02.png,03.png | 96×64 / 1 | 12 / false | 30,56 / right |
| ninja_strike | DarkNinja/frames/Attack1/ 04.png,05.png,06.png | 96×64 / 1 | 12 / false | 30,56 / right |
| ninja_recover | DarkNinja/frames/Attack1/ 06.png | 96×64 / 1 | 12 / false | 30,56 / right |
| samurai_idle | Samurai_Bundle/Samurai/Idle/Idle_1.png / 0,1,2 | 16×16 / 2 | 8 / true | 8,14 / left |
| samurai_run | Samurai_Bundle/Samurai/Run/Samurai_Run.png / 0,1,2,3,4,5 | 16×16 / 3 | 12 / true | 8,14 / left |
| samurai_hurt | Samurai_Bundle/Samurai/Hit/Samurai_Hit.png / 0,1 | 16×16 / 2 | 12 / false | 8,16 / left |
| samurai_stagger | Samurai_Bundle/Samurai/Hit/Samurai_Hit.png / 0 | 16×16 / 2 | 12 / false | 8,16 / left |
| samurai_death | Samurai_Bundle/Samurai/Death/Samurai_Death_1.png / 0,1,2,3,4,5,6 | 32×16 / 3 | 10 / false | 8,16 / left |
| samurai_prepare | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 0,1,2,3 | 48×48 / 5 | 12 / false | 21,32 / left |
| samurai_strike | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 4,5,6,7 | 48×48 / 5 | 12 / false | 21,32 / left |
| samurai_recover | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 8,9 | 48×48 / 5 | 12 / false | 21,32 / left |
| samurai_return_prepare | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 8,9 | 48×48 / 5 | 12 / false | 21,32 / right |
| samurai_return_strike | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 10,11,12,13 | 48×48 / 5 | 12 / false | 21,32 / right |
| samurai_return_recover | Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png / 14,15,16,17 | 48×48 / 5 | 12 / false | 21,32 / right |
| samurai_heavy_prepare | Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png / 0,1 | 48×32 / 4 | 12 / false | 24,13 / left |
| samurai_heavy_strike | Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png / 2,3,4,5 | 48×32 / 4 | 12 / false | 24,13 / left |
| samurai_heavy_recover | Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png / 6,7,8,9,10 | 48×32 / 4 | 12 / false | 24,13 / left |

Normal/double first hit use samurai_prepare/strike/recover; second double uses samurai_return_prepare/strike/recover with native facing reversed on the same sheet. Heavy uses samurai_heavy_prepare/strike/recover. Player slash uses ninja_prepare/strike/recover; start=0 means ninja_prepare has no positive-duration windup. These mappings replace the old Java SpriteClip list for Android.

### Anchors, offsets, facing and body size

ContentPainter.character receives `(bodyCenterX,feetY)`. It draws a source rectangle into local destination `[-anchorX,-anchorY, frame.width-anchorX,frame.height-anchorY]`, translates to world feet/body center and scales uniformly with x sign flipped when facing differs from facesRight. For unflipped art:

```text
visualLeft = bodyCenterX - anchorX*scale
visualTop  = feetY - anchorY*scale
visualWidth = sourceWidth*scale
visualHeight = sourceHeight*scale
```

Mirroring is around this world anchor, not the sheet origin. Negative/outside anchors are allowed; there are no separate offsetX/offsetY fields. Anchor adjustments or transparent padding provide visual offsets. Nonuniform scale, rotated frames, skeletal animation, per-frame anchors, top/down directional state maps, automatic body extraction and animation-driven hitboxes are unsupported. Independent opposite-facing clips may be referenced by different supported states/attack phases, but there is no automatic separate left/right animation selection beyond mirroring.

A knight with large sword art can use a 48×48 source at scale 2 while its body remains 44×70; tune anchors 24, 44 for feet alignment. Its sword extending 96 world px does not increase configured hit width. Always inspect body/attack debug overlays when judging contact; opacity and pixel alpha do not define physics.

## 9. `levels/test_arena.tmj`: map root and custom properties

Only finite orthogonal JSON-array maps with right-down render order are accepted. World width=map.width*tilewidth, height=map.height*tileheight; each axis must produce at least 960×540 and at most 32768×32768 world px. Tile/grid dimensions do not rescale actors. Standard Tiled root fields not explicitly consumed can be retained as editor metadata, but do not become engine features.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load/props/properties; [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) Loader.load; [Game.java](app/src/main/java/com/lockin/game/Game.java) level/loadLevel/regionName; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) setLevel/worldBackground/build

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `type` | Document type | string | World | Only map accepted | R exactly map |
| `orientation` | Grid orientation | string | World | Only orthogonal accepted | R exactly orthogonal |
| `renderorder` | Tile draw order | string | World visual | Only right-down accepted | O right-down |
| `infinite` | Chunked/infinite input flag | boolean | World | true rejected | O false |
| `width` | Map columns | cells | World | Larger world width | R integer1..1024; pixel bounds above |
| `height` | Map rows | cells | World | Larger world height | R integer1..1024; pixel bounds above |
| `tilewidth` | Cell width | px | World grid/render/collision | Wider cell geometry | R integer1..512 |
| `tileheight` | Cell height | px | World grid/render/collision | Taller cell geometry | R integer1..512 |
| `tilesets` | External tileset references | object[] | World | More atlas definitions | R array |
| `tilesets[].firstgid` | First global ID for this atlas | integer GID | World art/tile collision | Moves GID range | R integer1..1e6; ranges must not overlap |
| `tilesets[].source` | External tileset path | path | World art/tile collision | Different atlas | R string ending .tsj; relative to TMJ |
| `layers` | Ordered tile/object/group layers | object[] | World | More layers/objects | R array; types below |
| `properties` | Map custom properties | property[] | World | Only listed names supported | O structurally, but required properties below must exist |
| `properties.name` | Area/HUD fallback label | string | World visual | Different label | O level registry ID |
| `properties.playerEntity` | Player entity for this level | entity ID | Player | Changes player definition at game creation | R existing kind=player; all connected levels must use same ID |
| `properties.startSpawn` | Default player point for this level | spawn name | Player | Changes level start/reset placement | R existing player_spawn name |
| `properties.groundSurface` | Navigation root floor name | surface name | World/AI | Selects root | R platform/collision; must be first such object in flattened object order |
| `properties.backgroundImage` | Screen-sized background | PNG path | World visual | Different backdrop | O empty; relative to TMJ; stretched to960×540; no parallax |

### Custom property representation and validation

Every Tiled custom property is an object `{ "name": "openingWidth", "type": "int", "value": 112 }` in the owning node's properties array. Runtime reads `name` and `value`; `type` is editor metadata, not a coercion instruction. Numeric value must actually be a JSON number; booleans/strings must actually have their JSON type. Duplicate names are rejected. `props` does not import template/class inherited defaults; author concrete properties explicitly. Class/Type selects object behavior but does not load a Tiled project schema.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) props/number/flag/prop/required

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `name` | Custom property lookup key | string | Owning definition | Different key | R string; duplicate names rejected; allowed vocabulary per owner |
| `value` | Typed custom value | JSON value | Owning definition | Depends on property | R; consumer validates actual JSON type |
| `type` | Tiled property editor type | metadata string | None at runtime | No runtime effect | Not read; retained by editor; no coercion |

### Layers, inherited visibility and opacity

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) layers/load; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) build

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `type` | Layer family | enum | World | Selects processing | R tilelayer/objectgroup/group; other types rejected |
| `name` | Stored tile-layer name/editor label | string | Metadata | No gameplay effect | O empty; not a collision naming convention |
| `offsetx` | Pixel x offset added through groups | px | Art/objects/tile collision | Moves layer right | O0 finite JSON number; object bounds checked after offset |
| `offsety` | Pixel y offset added through groups | px | Art/objects/tile collision | Moves layer down | O0; same inherited behavior |
| `visible` | Inherited visibility conjunction | boolean | Art/tile collision; not object gameplay | false removes tile solidity and visuals | O true; parent false dominates |
| `opacity` | Inherited multiplication of opacity | fraction | Tile visuals only | More opaque tiles | O1; inherited product must be0..1; zero-opacity visible tile remains solid |
| `parallaxx` | Horizontal layer parallax | number | Validation | Non1 rejected | O1; no per-layer parallax support |
| `parallaxy` | Vertical layer parallax | number | Validation | Non1 rejected | O1 |
| `x` | Tile layer cell-origin offset | number | Validation only for tilelayer | Nonzero rejected on tilelayer | O0; use offsetx; objectgroup/group x is not applied |
| `y` | Tile layer cell-origin offset | number | Validation only for tilelayer | Nonzero rejected on tilelayer | O0; use offsety; objectgroup/group y is not applied |
| `width` | Tile layer columns | cells | Tile geometry | Wider data grid | R tilelayer integer1..1024; <=262144 total cells |
| `height` | Tile layer rows | cells | Tile geometry | Taller data grid | R tilelayer integer1..1024 |
| `data` | Row-major tile GIDs | uint32[] | Tile visuals/collision | Different tiles | R tilelayer; exactly width*height; integer0..4294967295; flags below |
| `layers` | Child layers | object[] | Group descendants | More children | R group; recursively flattened with offsets/visibility/opacity |
| `objects` | Gameplay objects | object[] | World/actors | More gameplay/decoration | R objectgroup; flattened in traversal order |
| `encoding` | Encoded tile data marker | string | Validation | Presence rejected | Must be absent; even an empty marker is unsupported |
| `compression` | Compressed data marker | string | Validation | Presence rejected | Must be absent |
| `chunks` | Infinite map chunk data | array | Validation | Presence rejected | Must be absent |

Custom properties on layers/groups are not read or validated by TiledLoader.layers; they add no mechanic. Layer/editor IDs, object draworder, tint and other unused metadata do not customize rendering. Layers preserve tile-layer relative order after flattening; **all generated object decorations draw after all tile layers**, so full interleaving of object and tile layers is not preserved. Characters/HUD are rendered separately. Objectgroup opacity is multiplied/validated during traversal but discarded for object/generated art: their rendering is full opacity. Hiding an object/object layer suppresses platform/ladder/decoration art but **does not delete gameplay collision, spawns, pickups, exits or regions**. Tile-layer visibility false suppresses both its art and its solid cells.

A visible tile layer at opacity 0 remains collidable. Tile-layer offsets move solid cells as well as art; tile solids are not additionally clipped/validated against world bounds by load. Tile grids must reference tiles whose source dimensions equal map.tilewidth/tileheight, including on hidden layers; use decoration for scaled atlas art. Total tile-cell budget across the pack is 1, 048, 576 (hidden layers count too).

### Common gameplay object fields

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) type/layers/load/bounds; [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) Loader.spawn

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `class` | Preferred gameplay type discriminator | string | World/actors | Selects object handling | O empty; nonempty overrides type; allowed vocabulary below |
| `type` | Legacy gameplay type discriminator | string | World/actors | Selects object handling | O empty; used when class empty; unsupported/empty type rejected |
| `name` | Stable surface/spawn/pickup ID or region label | string | World/AI/actors | Different reference/seed/label | O empty generally; R nonempty platform/collision/spawn/pickup; every nonempty name unique across level objects |
| `x` | Rectangle left or point body-left/launch x | px | Object-specific | Moves right | O0; layer/group pixel offsets added; must remain in world bounds |
| `y` | Rectangle top or point feet y | px | Object-specific | Moves down | O0; offsets added; must remain in bounds |
| `width` | Rectangle width | px | Object-specific | Wider region/trigger/collision/art | O0 generally; R platform/collision1..map pixel width; positive on ladder/pickup/exit/decoration |
| `height` | Rectangle height | px | Object-specific | Taller region/trigger/collision/art | O0 generally; R platform/collision1..map pixel height; positive on ladder/pickup/exit/decoration |
| `visible` | Object visual visibility | boolean | Generated platform/ladder/decoration art | false hides art only | O true; does not disable gameplay objects |
| `rotation` | Rotation angle | degrees | Validation | Nonzero rejected | O0 |
| `point` | Tiled point marker | boolean metadata | None directly | No direct effect | Not read; spawn/nav interpretation uses object type and x/y; point shape not enforced |
| `properties` | Explicit custom properties | property[] | Object-specific | Adds allowed settings | O empty; allowed names depend on class/type |

Any object containing ellipse, polygon, polyline, gid (tile object), template or text is rejected, even if other fields could describe a rectangle. Rotation must be zero. For all objects x/y/width/height must be finite, nonnegative, and extent<=world bounds(+.01 tolerance); zero-size nav/spawn/region objects are allowed. Spawn body bounds and solid overlap are validated later with entity dimensions; rectangle width/height on a spawn are not used as body size. Object IDs are editor metadata; references use names. Any nonempty duplicate object name is rejected even across different object types.

### Every supported object type and custom property

`platform` and `collision` share the same rectangular solid/navigation-surface implementation. They are not one-way platforms. A `collision` tall wall still exposes its top as a named navigation surface. `ladder` cuts a physical hole only in its named top platform, and automatically generates up/down graph links. `navigation` creates explicit graph edges; no arbitrary jump links are inferred from tile art. Custom properties not in the following type-specific tables are rejected.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load/tileRef; [SolidPhysics.java](app/src/main/java/com/lockin/game/SolidPhysics.java) horizontal/vertical; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) build

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `oneWay` | Legacy-looking one-way switch | boolean | Validation only | true rejected | O false; continuous solid model does not support one-way floors |
| `tileId` | Repeated art by stable atlas label | string | Visual only | Different texture | O absent→tileGid fallback; existing <tileset name>:<zero-based tile index>; mutually exclusive tileGid |
| `tileGid` | Repeated art by global tile number | integer | Visual only | Different texture | O0 fallback rectangle; 0..1e6; nonzero must resolve; no flip flags |

Platform rectangle height is full collision thickness, including underside/sides. Loader splits its solids around top-ladder apertures and generates matching decoration pieces with tileSize 32. No art reference (GID 0) draws a colored rectangle; it does not remove collision. Tile solidity flags are irrelevant for explicit platform/collision rectangles.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load; [SolidPhysics.java](app/src/main/java/com/lockin/game/SolidPhysics.java) ladderAt/player; [WorldNavigator.java](app/src/main/java/com/lockin/game/WorldNavigator.java) update; [LadderTraffic.java](app/src/main/java/com/lockin/game/LadderTraffic.java) update

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `bottom` | Lower platform name | surface reference | Both actors/navigation | Different climb endpoint | R existing named platform/collision |
| `top` | Upper platform name and aperture target | surface reference | Both actors/world | Different upper endpoint | R existing named platform/collision |
| `openingWidth` | Physical hole cut in upper platform | px | World/actors | Wider hole/pullout distance | O rectangle width+32; numeric -1e5..1e5 then >=ladder width; must fit top platform; no overlap with another aperture |
| `entryRadius` | Climb alignment threshold | px | Both actors | More forgiving entry | O30; numeric -1e5..1e5 then >0 and <=openingWidth/2 |
| `tileId` | Ladder artwork | tile ID | Visual | Different image | O; same stable ID/mutual exclusion rules as platform |
| `tileGid` | Ladder artwork fallback | GID | Visual | Different image | O0; same numeric range/reference rules |

Ladder rectangle x+width/2 is the centerline; its y must match top surface.top, y+height must match bottom surface.top within .1 px. Width/height must be positive. Art repeats in width×width cells and clips to the ladder rectangle; this visual width does not replace openingWidth or entryRadius. Validation requires every spawn body width to be strictly less than every level ladder aperture. Lower/upper relation follows the positive rectangle height/endpoints, not an arbitrary named direction. Parent visibility affects art only; climb links and holes still exist.

Player climbs with vertical input near the ladder, using entity.climbSpeed for alignment/travel; at the upper lip horizontal input pulls onto the floor. DOWN on ordinary solid floors does nothing. Enemy traversal uses its own climb/jump/air settings. Floor solidity stays active during dashes/iframes; clearing an enemy body requires jumping above it. Hidden art does not open a floor or remove a body.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load; [WorldNavigator.java](app/src/main/java/com/lockin/game/WorldNavigator.java) update/pathLaunch

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `from` | Source surface | surface name | Enemy routing | Different route origin | R existing platform/collision |
| `to` | Destination surface | surface name | Enemy routing | Different route goal | R existing platform/collision |
| `travel` | Traversal operation | enum string | Enemy routing | Different movement method | R walk/jump/drop; case-normalized with toUpperCase; ladder value rejected, use ladder object |
| `destinationX` | Target center/connection x | px | Enemy routing | Landing/walk goal farther right | O object effective x; number -1e5..1e5 then0..world width |
| `bidirectional` | Also generate reverse edge | boolean | Enemy routing | true adds reverse route | O true; same travel kind in reverse; no reachability test |

Object effective x is launch center coordinate, y is only bounds-checked and is not used as launch height. Important offset detail: an omitted destinationX uses offset-adjusted x, but an explicit destinationX is already a world coordinate and does **not** receive layer offsets. Surface names map to ordered indexes. Walk uses destinationX; jump approaches launch, applies -entity.jumpSpeed then moves toward destination with airSpeed; drop walks toward launch and relies on falling/surface change. Bidirectionally mirroring a drop does not turn the reverse into a jump. Physics/reachability of every authored edge is not validated; test routes manually. Only explicit platform/collision objects are named graph surfaces; painted solid tile tops are not added as named graph nodes.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load; [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) Loader.spawn/load; [AreaState.java](app/src/main/java/com/lockin/game/AreaState.java) reset; [Game.java](app/src/main/java/com/lockin/game/Game.java) loadLevel

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `entityId` | Definition instantiated at point | entity ID | Spawned actor | Different stats/visual/AI bundle | R existing; player_spawn must equal map.playerEntity and kind player; enemy_spawn kind enemy |
| `facing` | Initial/reset orientation | enum string | Spawned actor | Select left/right | O right for player_spawn; left for enemy_spawn; exact lowercase only |

Spawn object name is required and stable; per-spawn enemy seed uses name.hashCode. x=body-left, y=feet, not center/top; runtime body y=y-entity.height. Add/delete enemy_spawn objects to change enemy count (max 128 per level; zero allowed). `visible=false` does not remove an enemy. Multiple player spawns are supported; startSpawn and exit.spawnId choose by name. There is no per-spawn stat override beyond selecting entityId. Spawn-overlap validation checks terrain, not arbitrary actor-vs-actor reachability/game feel.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load; [Game.java](app/src/main/java/com/lockin/game/Game.java) upgrades; [Player.java](app/src/main/java/com/lockin/game/Player.java) healthUpgrade/powerUpgrade

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `kind` | Reward mechanic | enum string | Player target | Changes reward category | R health or power; positive named rectangle required |
| `amount` | Health increment or minimum attack multiplier | integer | Player | More maxHP/heal or stronger power floor | O1; integer1..1e6 |

Contact is strict body/rectangle overlap, once per pickup per run. Health adds amount to **current** maxHealth (saturates INT_MAX), then fully heals; separate health pickups can stack. Power sets max(existing multiplier, amount); two power 2 rewards do not produce 4. Power multiplies ordinary player attack HP and posture, not block/deflect/deathblow. Flags belong to each level's AreaState, persist on transitions and clear on reset. Notification lasts hardcoded 3 s. Hiding a pickup object does not disable its trigger or hide the dedicated gameplay pickup drawing.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load; [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) Loader.load exit reference checks; [Game.java](app/src/main/java/com/lockin/game/Game.java) exits/loadLevel

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `levelId` | Destination level or completion | level ID | Player/world | Different level selection | O empty; empty completes; nonempty must resolve |
| `spawnId` | Destination player spawn | spawn name | Player | Different arrival point | O empty; must resolve in target when levelId nonempty |

Exits require positive contact rectangles. Touching an exit completes or transitions regardless of move direction, grounded state or enemies remaining. A transition resets travel/attack/dash/guard state and queued input, relocates the existing player with its HP/upgrades retained, increments animation reset revision and applies a hardcoded .4 s exit lock. Destination must use the same playerEntity ID. An empty levelId ignores spawnId and completes the run; no content file is loaded by path at contact because registered levels were already loaded. Reset returns to manifest.startLevel/startSpawn, resetting all level enemies/rewards.

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load/tileRef; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) build

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `tileId` | Atlas tile repeated over rectangle | tile ID | World visual only | Different art | O but either it or valid nonzero tileGid required; exclusive with tileGid |
| `tileGid` | Numeric atlas tile fallback | GID | World visual only | Different art | O0 but0 invalid for decoration; integer0..1e6 then must resolve |
| `tileSize` | Square world repeat size | px | World visual only | Bigger repeated cells/fewer repeats | O32; numeric -1e5..1e5 then>0 |

A decoration requires positive rectangle and valid tile reference; repeats square tileSize cells clipped to it. Source tiles may be non-square and are stretched into square target repeats. Its tile's `solid` flag does not add collision here; only painting on a visible tile layer does. Platform/ladder art uses generated decorations separately.

`region` has **no supported custom properties** (any custom name is rejected). Its common name is the HUD label and rectangle is tested against the player's **top-left point**, not center/feet/body overlap. First matching region in object order wins; fallback is map name. Zero-size regions are allowed but never match; regions do not load scenes or affect AI. All nine distinct object families plus platform/collision alias are covered above.

### Actual authored map inventory

The shipped map is 180×68 cells of 16×16 (2880×1088). It contains one tile layer `Castle backdrop` (opacity .65) and one object layer `Gameplay objects`:16 platform rectangles (ground plus deck_1..deck_15), 14 ladders, 4 navigation objects, 1 player spawn, 5 enemy spawns, 2 pickups, 1 exit, 3 regions (46 objects total). Generated ladder links 28 plus bidirectional navigation links 8 yield 36 explicit directed edges. Same-surface aperture jumps can be planned automatically; no general navmesh is generated.

Ground is(0, 940, 2880, 140). Player start is body-left 180/feet 940. Samurai spawns are(670, 940), (1450, 796), (2040, 940), (2460, 812), (2660, 620). Health_reward rectangle(284, 244, 32, 32) adds 50 HP; power_reward(1514, 180, 32, 32) sets minimum multiplier 2. Exit(2878, 850, 2, 90) has empty destination and completes. Regions are three 960 px-wide rectangles at x 0/960/1920, y 0, height 1080; lowest 8 map pixels lie outside region rectangles and use fallback name. All these values are editable data, not hardcoded production geometry.

## 10. `levels/tilesets/castle.tsj` and GIDs

Implementation: [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load/tileRef/layers; [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) Loader.image; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) tile/build

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `type` | Document discriminator | string | World atlas | Only tileset accepted | R exactly tileset |
| `name` | Stable atlas ID prefix | string | Art references | Changes all tileId labels | R string; resulting <name>:index must be unique; not registry regex checked |
| `tilewidth` | Source tile width | art px | Tiles | Wider source cell | R integer1..512; layer tiles must match map width |
| `tileheight` | Source tile height | art px | Tiles | Taller source cell | R integer1..512; layer tiles must match map height |
| `tilecount` | Number of generated tile definitions | count | World atlas | More available IDs | R integer1..65536; every rectangle must fit declared/actual image |
| `columns` | Row-major grid columns | count | World atlas | Different source mapping | R integer1..65536 |
| `margin` | Atlas outer cell offset | art px | World atlas | Moves grid origin inward | O integer0..4096 default0 |
| `spacing` | Space between source cells | art px | World atlas | Farther-separated cells | O integer0..4096 default0 |
| `image` | Atlas PNG | path | World visual | Different source | R string; relative to TSJ; must exist/validate PNG |
| `imagewidth` | Declared source width | art px | Validation | Larger declared allowed area | R integer1..32768; generated rectangles must also fit actual PNG |
| `imageheight` | Declared source height | art px | Validation | Larger declared allowed area | R integer1..32768; actual PNG axes separately max16384 |
| `tiles` | Per-tile metadata overrides | object[] | Tile collision | More solid overrides | O empty |
| `tiles[].id` | Local tile index | index | Tile metadata selection | Different tile selected | R integer0..tilecount-1 |
| `tiles[].properties` | Tile custom property records | array | Tile collision | Only solid supported | O empty; unsupported custom names rejected |
| `tiles[].properties.solid` | Create full-cell solid when painted on visible tilelayer | boolean | Both actors/world | true makes painted cells block | O false; no surface names/nav links generated |
| `tileoffset` | Source render offset object | object | Validation only | Nonzero x/y rejected | O absent; component defaults0 |
| `tileoffset.x` | Horizontal tileset offset | px | Validation only | Nonzero rejected | O0 if tileoffset present |
| `tileoffset.y` | Vertical tileset offset | px | Validation only | Nonzero rejected | O0 if tileoffset present |
| `tiles[].animation` | Tiled tile animation | array | Validation only | Presence rejected | Must be absent |
| `tiles[].objectgroup` | Tile collision geometry | object | Validation only | Presence rejected | Must be absent; use solid or map rectangle |
| `tiles[].image` | Image collection tile | path | Validation only | Presence rejected | Must be absent; atlas PNG only |

Source origin for local tile i is `margin+(i%columns)*(tilewidth+spacing)`, `margin+floor(i/columns)*(tileheight+spacing)`. GID=`firstgid+i`. Stable object tileId is `tileset.name + ":" + i`, zero-based. Example castle:158 resolves to GID 159 when firstgid=1, and source(480, 64) for 32 columns/16 px cells. Changing firstgid changes numeric GIDs but not the stable label. Supplying both tileId and tileGid is an error.

Raw tile-layer GIDs are integers 0..2^32-1;0 is empty. High bits0x80000000/0x40000000 flip horizontally/vertically in rendering. 0x20000000/0x10000000 diagonal/hexagonal rotation flags are rejected. Base tile index is raw&0x0fffffff. Flips do not change full-cell collision. Object tileGid does not accept encoded flip flags (range 0..1e6).

castle.tsj: tilewidth/height 16, tilecount 192, columns 32, image 512×96, margin/spacing 0, source spritesheet.png. Only local tile 0 explicitly has solid=true. This flag is independent of visual appearance or the unused pack JSON's category. All other local tiles are decorative when painted; explicit map platforms are solid regardless of their selected art tile. Duplicate per-tile metadata records are not explicitly rejected; a true solid entry adds that local ID to a set and a later false does not remove it. Avoid duplicate tile IDs even though the parser does not enforce uniqueness there.

### Ignored editor metadata and unsupported features

TiledLoader does not apply root `version`, `tiledversion`, `nextlayerid`, `nextobjectid`, node numeric `id`, object-layer `draworder`, or tileset editor metadata. They can be preserved for Tiled but do not alter the simulation. Standard Tiled keys are not generally checked by a root `only` allowlist, so an unrecognized standard field can be silently ignored; this is not support for that feature. Map/object/tile **custom property** vocabularies are strict as described above. Layer/tileset-level custom properties are not consumed. Source atlas label categories have no effect.

Explicitly rejected: nonorthogonal/infinite maps, non-right-down renderorder, encoding/compression/chunks, nonzero tilelayer x/y origins, per-layer parallax !=1, unsupported layer types (including image layers), nonzero object rotation, polygon/polyline/ellipse/text/tile/template objects, diagonal/hexagonal tile flags, non-TSJ external tilesets, nonzero tileset tileoffset, animated/collection tiles and tile objectgroup collision, oneWay=true, unsupported custom property names. Missing PNG/reference errors are fatal. Unsupported mechanics include slopes, physics from alpha shapes, tile animation, trigger scripts, arbitrary AI scripts and on-device content hot reload.

### Unregistered `spritesheet.json` (every field)

The additional pack metadata is **not** the castle TSJ and is not read by Content, TiledLoader or ContentPainter. Every field below is descriptive only. Their observed types are not runtime acceptance requirements; there is no parser, default or effect. `meta.count=151` differs from 150 actual named frame entries; neither count registers TSJ tiles (TSJ has 192 cells).

Implementation: Production load chain [content/Content.java](app/src/main/java/com/lockin/game/content/Content.java) load and [content/TiledLoader.java](app/src/main/java/com/lockin/game/content/TiledLoader.java) load uses TSJ image rectangles; [ContentPainter.java](app/src/main/java/com/lockin/game/ContentPainter.java) loads those PNGs, never this file

| Field | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `meta` | Pack header | object | None | No effect | Observed object; unregistered/unvalidated |
| `meta.name` | Pack title | string | None | No effect | Observed string; no runtime default |
| `meta.tile_size` | Nominal source cell size | art px | None | No effect | Observed integer16; TSJ/map configure actual cells |
| `meta.image` | Pack atlas filename | path string | None | No effect | Observed spritesheet.png; not runtime path resolution |
| `meta.count` | Declared named entry count | integer | None | No effect | Observed151; actual150 |
| `frames` | Name-to-rectangle dictionary | object | None | No effect | Dynamic keys are labels, not entity/tile IDs |
| `frames.<name>.x` | Rectangle left | art px | None | No effect | Observed integer |
| `frames.<name>.y` | Rectangle top | art px | None | No effect | Observed integer |
| `frames.<name>.w` | Rectangle width | art px | None | No effect | Observed integer |
| `frames.<name>.h` | Rectangle height | art px | None | No effect | Observed integer |
| `frames.<name>.category` | Organizational art category | string | None | No effect | hazards/climb/collectibles labels grant no mechanic |

## 11. Practical independent knight example — existing schema, no Java changes

No knight definition/assets ship at the audited revision. This example creates a new independent enemy using the supported schema. It is a designer recipe, not a modification made to gameplay/content files for this documentation task. New files must be registered; do not overwrite Samurai definitions if both types should coexist.

### Step 1: supply a compatible new PNG

Export `app/src/main/assets/sprites/knight/knight.png` as 240×192:48×48 cells, 5 columns, 4 rows (20 cells), with meaningful frames 0..17. This is a chosen example layout, not a requirement that all packs match Samurai. Author frames: idle 0..2, run 3..5, hurt/stagger 6, death 7..9, prepare 10..11, strike 12..14, recover 15..17; leave 18..19 unselected. Face art left (`facesRight=false`), place feet/body-center at local(24, 44), and use scale 2. A differently sized sheet can instead use its own grid or explicit frame rectangles with adjusted anchors.

Actual knight appearance is **UNVERIFIED** until the designer supplies/inspects that art. Validation for this example uses a same-dimension existing PNG as a temporary stand-in in a scratch pack; it proves schema/reference/body compatibility, not knight art quality. No placeholder or stand-in is installed into the repository.

### Step 2: register new animation IDs

Create `animations/knight.json` with an `animations` array. The following is the first complete clip; add the other seven by copying this object and changing id/frames/loop using the table. All eight use the same asset, 48×48 cells, 5 columns, 12 fps, facesRight=false, anchor 24, 44, scale 2, empty events. Only idle/run loop. No Java enum registration is needed.

```json
{
  "animations": [
    {
      "id": "knight_idle",
      "asset": "../sprites/knight/knight.png",
      "frameWidth": 48,
      "frameHeight": 48,
      "columns": 5,
      "frames": [
        0,
        1,
        2
      ],
      "fps": 12,
      "loop": true,
      "facesRight": false,
      "anchorX": 24,
      "anchorY": 44,
      "scale": 2,
      "events": []
    }
  ]
}
```

| Clip ID | frames | loop |
|---|---|---|
| knight_idle | [0, 1, 2] | true |
| knight_run | [3, 4, 5] | true |
| knight_hurt | [6] | false |
| knight_stagger | [6] | false |
| knight_death | [7, 8, 9] | false |
| knight_prepare | [10, 11] | false |
| knight_strike | [12, 13, 14] | false |
| knight_recover | [15, 16, 17] | false |

### Step 3: independent entity stats and references

Create `entities/knight.json`. This is a complete entity, including required enemy dash/deathblow values that its AI does not use. It shares no mutable state with Samurai; it can share selected immutable clips/attacks if desired instead.

```json
{
  "id": "knight",
  "kind": "enemy",
  "maxHealth": 250,
  "width": 44,
  "height": 70,
  "speed": 90,
  "acceleration": 0,
  "jumpSpeed": 590,
  "gravity": 1500,
  "climbSpeed": 140,
  "airSpeed": 250,
  "dash": {
    "duration": 0.18,
    "speed": 950,
    "cooldown": 0.45,
    "iframeStart": 0.04,
    "iframeEnd": 0.14
  },
  "deathblowDuration": 0.3,
  "attack": "knight_slash",
  "combo": "",
  "behavior": "knight_melee",
  "animations": {
    "idle": "knight_idle",
    "run": "knight_run",
    "hurt": "knight_hurt",
    "stagger": "knight_stagger",
    "death": "knight_death"
  },
  "combatProfile": "knight_guard",
  "maxPosture": 160,
  "postureRecovery": 8
}
```

### Step 4: defensive profile

Create `combat/knight-defense.json`:

```json
{
  "defenses": [
    {
      "id": "knight_guard",
      "deflectWindow": 0.32,
      "recoveryDelay": 1,
      "brokenDuration": 1.4,
      "spamStep": 0.05,
      "minimumDeflectWindow": 0.05,
      "spamRecovery": 0.8,
      "breakImmunity": 1.2,
      "deathblowRange": 90,
      "blockPostureMultiplier": 0.5,
      "deflectPostureMultiplier": 2
    }
  ]
}
```

This gives the knight its own break/grace timing; maxPosture 160/recovery 8 still come from its entity. **The .5 block multiplier and 2 deflect multiplier have no effect on enemy AI**, because it cannot guard. They do not make the knight receive half ordinary posture damage or deal twice ordinary posture damage. To change its posture received before breaking, tune maxPosture/recovery/timing. To change its outgoing hit/block/deflect amounts, tune its attacks. If a player entity referenced this profile, .5 would halve posture it receives on blocks and 2 would double posture it returns to the enemy attacker on deflects.

### Step 5: unique move and sequence

Create `combat/knight-attacks.json`:

```json
{
  "attacks": [
    {
      "id": "knight_slash",
      "style": "normal",
      "damage": 30,
      "postureDamage": 40,
      "blockDamage": 45,
      "deflectDamage": 60,
      "duration": 1.5,
      "cooldown": 1.5,
      "directional": true,
      "blockable": true,
      "deflectable": true,
      "interruptOnHit": false,
      "hits": [
        {
          "id": "slash",
          "start": 0.6,
          "end": 0.8,
          "forward": 0,
          "top": 10,
          "width": 72,
          "height": 52,
          "windupAnimation": "knight_prepare",
          "activeAnimation": "knight_strike"
        }
      ],
      "dashCancel": [],
      "recoveryAnimation": "knight_recover"
    }
  ]
}
```

The strike becomes active at .6 s until .8 s, then recovers until 1.5 s. Damage 30/posture 40 go to player on unblocked contact; block 45 goes to player's posture times **player** block multiplier; deflect 60 goes to knight posture times **player** deflect multiplier. Knight's own multiplier 2 does not scale that return. No dashCancel windows are needed for its current AI.

Create `combat/knight-combos.json`:

```json
{
  "combos": [
    {
      "id": "knight_sequence",
      "sequence": [
        "knight_slash"
      ],
      "followups": []
    }
  ]
}
```

To give this knight more distinct moves, add independently named AttackDefs and list their IDs in this sequence; set behavior.weights to the same length. Do not assume entity.combo selects enemy moves: behavior.combo does.

### Step 6: unique melee AI configuration

Create `behaviors/knight.json`:

```json
{
  "behaviors": [
    {
      "id": "knight_melee",
      "type": "melee",
      "selection": "sequence",
      "combo": "knight_sequence",
      "weights": [
        1
      ],
      "detectionRadius": 400,
      "engagementDistance": 30,
      "betweenMoves": 0.25,
      "staggerRecovery": 0.9,
      "chase": true,
      "facePlayer": true,
      "seed": 2026,
      "transitions": [
        "detect",
        "in_melee_range",
        "timeline_complete",
        "posture_broken",
        "health_zero"
      ]
    }
  ]
}
```

This is an independently tuned instance of supported melee AI, not a new guard/jump-combat script. Engagement 30 and chase/facePlayer true retain existing approach rules; detection 400, betweenMoves .25 and staggerRecovery .9 differ from Samurai. Change selection to weighted and weights as desired; seed controls deterministic sampling.

### Step 7: register all files and spawn in Tiled

Append each path to its existing manifest array (retain all existing entries):

```json
{
  "entities": [
    "entities/knight.json"
  ],
  "defenses": [
    "combat/knight-defense.json"
  ],
  "attacks": [
    "combat/knight-attacks.json"
  ],
  "combos": [
    "combat/knight-combos.json"
  ],
  "behaviors": [
    "behaviors/knight.json"
  ],
  "animations": [
    "animations/knight.json"
  ]
}
```

Those paths show additions, not a replacement manifest. In Tiled select `samurai_1` and change entityId to `knight` to replace just that actor. Alternatively add a uniquely named enemy_spawn point with entityId=knight, facing=left, body-left x 900/feet y 940 on the ground, ensuring no body/terrain overlap and leaving existing spawns intact. Existing ladders'112 px openings fit its 44 px body; the validator checks this globally. This point's JSON representation uses the existing supported fields:

```json
{
  "name": "knight_1",
  "type": "enemy_spawn",
  "point": true,
  "x": 900,
  "y": 940,
  "width": 0,
  "height": 0,
  "rotation": 0,
  "visible": true,
  "properties": [
    {
      "name": "entityId",
      "type": "string",
      "value": "knight"
    },
    {
      "name": "facing",
      "type": "string",
      "value": "left"
    }
  ]
}
```

### Step 8: validate and test

Run `./tools/validate-content.sh` after saving; it checks the whole registered pack, including new IDs, frame PNG dimensions, body placement and exit references. `./tools/test-core.sh` includes content regression/renderer checks. Test both enemy types independently:HP 250 vs 100, posture 160 vs 100, damage 30 vs 20; verify each attack's windup/contact/recovery, deflect return ownership, sprite feet/facing, all navigation destinations, enemy break opportunity and reset. For a preview on Android, rebuild/reinstall after authorized content changes; this documentation task does not require an APK build.

## 12. Parsed-but-unused, conditional and misleading fields

| Field/group | Verified runtime limitation / direction caveat | Evidence |
|---|---|---|
| Enemy entity dash.*, deathblowDuration | Required/validated/stored but never used by enemy actions. No enemy dash/execution method is called. | EntityDef constructor; Enemy constructor/updateConfigured; Player action methods |
| Enemy entity.combo | Reference validated if nonempty; runtime enemy selection uses behavior.combo instead. | Content.loadEntity/loadBehaviors; Enemy.select/updateConfigured |
| Player entity.behavior | Optional string stored; not resolved for player and no player behavior consumer. | Content.loadEntity; EntityDef constructor; Player |
| Enemy entity.attack | Reference validated/default attack accessor fallback; normal AI attack initiation chooses behavior's combo sequence. Changing it alone does not replace AI moves. | Enemy.currentAttack/updateConfigured |
| Profile blockPostureMultiplier | Used on player defender's posture RECEIVED on block. Enemy-owned value has no guard consumer. | Player.receiveAttack; Enemy.blockDamage |
| Profile deflectPostureMultiplier | Owned by player defender, scales posture DEALT TO ENEMY ATTACKER. Not posture received/resistance; enemy-owned value has no deflect consumer. | Player.receiveAttack; Enemy.deflected |
| Enemy profile deflectWindow/minimumDeflectWindow/spamStep/spamRecovery/deathblowRange | Copied into CombatConfig, but enemy has no guard/spam/deathblow-eligibility consumer. Its posture subsystem does use recoveryDelay/brokenDuration/breakImmunity. | DefenseDef.forEntity; Player; Posture |
| Player attack blockDamage/deflectDamage/blockable/deflectable | Validated, but ordinary player→enemy contacts do not run an enemy defense branch. Enemy cannot block/deflect the player. | Player.tryHit; Enemy.receiveHit |
| Player combo.sequence | Required/validated but not used to choose player initial/followup attacks. | Player.beginAttack/updateConfiguredAttack; entity.attack |
| Enemy behavior combo.followups | Validated, but enemy uses sequence only. | Content.loadBehaviors; Enemy.select |
| Behavior.type/transitions | Checked against fixed melee/transition vocabulary then not retained as executable rules. Cannot add/disable transitions through arbitrary entries. | Content.loadBehaviors; BehaviorDef |
| weights/seed in sequence mode | Still validated; no effect on selected sequence order. | Enemy.select |
| facePlayer=false | Local attack AI stops facing target; WorldNavigator.walk separately faces motion. Does not freeze facing globally. | Enemy.updateConfigured; WorldNavigator.walk |
| chase=false | Stops normal pursuit and creation of new routes; attack in range remains. A previously active route/yield path has its own handling. | WorldNavigator.update; Enemy.updateConfigured |
| Animation events / AttackRun events | Queryable API, no production dispatcher calls event(). Does not play audio or apply damage. | ContentAnimationClock.event; AttackRun.event; production call search |
| Animation next | Only free nonlooping advance follows it; sampling does not. A looping clip ignores next. | ContentAnimationClock.advance/sample |
| fps on phase-sampled clip | Defines base frame weights; uniform FPS change does not alter combat time or relative allocation when all entries use1/fps. | ContentAnimations; ContentAnimationClock.sample |
| placeholder=true | Deliberate rectangle; skips clip image validation/loading only. Does not provide collision or fix other missing references. | Content.loadAnimations; ContentPainter |
| interruptOnHit | Consults struck actor's current AttackDef; not an offensive hit-stun flag on incoming attack. | Player.receiveAttack; Enemy.receiveHit |
| style=heavy/double | Telegraph/legacy move category; does not double damage or create a second hit. Author damage and hits explicitly. | Content.loadAttacks; Enemy.updateConfigured |
| damage/cooldown vs duration | Cannot infer attack frequency from cooldown alone. Duration, enemy betweenMoves/recovery, and eligibility also constrain starts. Combo followups bypass normal restart test. | Player.updateConfiguredAttack; Enemy.updateConfigured |
| zero multiplier | Suppresses the relevant posture increment, not contact consumption or surrounding pressure/feedback. | Player.receiveAttack; Posture.add |
| spamRecovery | Both quiet penalty reset and failed-short-hold cutoff, not just one of them. | Player.finishGuardAttempt/updateDefense |
| Quiet failure recovery | Clears failedTaps, does not automatically refresh a latched guardWindow. | Player.setGuard/updateDefense |
| breakImmunity | Prevents new posture accumulation, not HP loss. Dash escape retains posture unlike natural expiry. | Posture.add/update/cancelBreak |
| power pickup amount | Minimum multiplier, not an added/multiplied bonus each pickup. Health amount does stack across different pickups against current maximum. | Player.powerUpgrade/healthUpgrade; Game.upgrades |
| layer/object visibility | Tile visibility disables tile collision; hidden object gameplay still exists. Opacity0 tiles remain solid. | TiledLoader.load/layers |
| Objectgroup opacity | Parsed/inherited/validated then discarded for object decoration rendering. | TiledLoader objectOffsets; ContentPainter.build |
| Object layer draworder / layer names / editor IDs | Ignored or stored as metadata. No automatic gameplay semantics or depth sorting. | TiledLoader.layers/load; LevelDef.Layer |
| Object/tile layer order | Tile layers maintain relative order; generated object decoration is rendered afterward, not fully interleaved. | ContentPainter.build |
| point marker / spawn width,height | Point flag not read; spawn placement uses type/x/y and entity body dimensions. Nonzero common rectangle still bounds-checked. | TiledLoader.load/bounds; Content.spawn |
| Navigation object y | Bounds-checked but traversal uses named surface heights; explicit destinationX is world-space even in offset layer. | TiledLoader.load; WorldNavigator.update |
| Tile solid property | Only painted visible tile layers generate full-cell solids; decoration art does not. Does not create named nav surfaces. | TiledLoader.load |
| oneWay | False accepted; true explicitly rejected. No drop-through solid-floor setting. | TiledLoader.load |
| Tiled property record type | Ignored; JSON value actual type validated. Class defaults/templates are not inherited. | TiledLoader.props |
| Layer/tileset-level custom properties | Not consumed; unlike map/object/tile custom lists, no per-name strict handling here. | TiledLoader.layers/load |
| spritesheet.json all fields | Unregistered pack metadata. Changing categories/coordinates/image has no effect; TSJ defines actual atlas cells. | Content.load; TiledLoader.load |
| Historical Java SpriteClip/EnemyMove/CombatConfig defaults | Remain for fixtures/compatibility and some visual categories; not the source of configured PNG grids/damage/timings. | GameScreen startup; configured actor branches |

### Differences from existing documentation

`CONTENT_EDITING.md` correctly describes the JSON/Tiled production path and defensive multiplier direction. This manual adds the exact limits/defaults and caveats above. Its broad statement about nested layers preserving order should be read with ContentPainter's tile-first/object-decoration-last limitation; full cross-type interleaving is not implemented. Its statement about presentation events does not imply a response handler. Objectgroup opacity affects neither gameplay nor generated object art.

Older README/docs sections retain pre-refactor Java constant tuning, earlier render-scale multipliers, camera descriptions and validation counts. Follow the configured path and current JSON values for Android. ContentPainter honors clip.scale directly; unlike historical SpriteClip, it does not override new pack scales by path prefix. GameScreen constructs Game(content), not the old Java-only authored castle. The historical absence findings previously in this document have been removed rather than repurposed as current facts.

### Limits that still require Java work

The data supports new enemy variants, independent stats, attacks/timelines, allowed melee behavior tuning, sprite packs and connected Tiled levels. It does not add new mechanics, new object/action types, arbitrary AI transitions/scripts, enemy defense/dash/execution, inventory/save systems, event-driven effects/audio, nonrectangular/one-way geometry, arbitrary input grammars or hot reload. Frame grids/anchors/scale are data; animation state priorities, fixed simulation rate 120 Hz, input actions, feedback .25/.6 s, hurt .18 s, exit lock .4 s, pickup notice 3 s, navigation timeouts and renderer cache 12×480×540 remain implementation choices. Definition limits do not guarantee smooth phone performance for 128 enemies/large maps.

## 13. Validation, evidence and audit totals

### Read-only checks run for this revision

```sh
./tools/validate-content.sh
./tools/test-core.sh
```

The provided JDK 21 ran these existing host scripts; no APK build or Android lint/build task was run. Validator result:1 level, 2 entities, 4 attacks, 28 animations, start=castle, enemies 5. The full script passed every suite, including 199 content parsing/editing/runtime/animation/transition checks and 148 content-renderer/cache/orientation checks. These checks exercise actual JSON edits via production parsers/runtime rather than fixtures alone; legacy regression suites also remain. A separate scratch-pack validation checks the knight recipe's IDs/references/geometry using a same-sized existing PNG as stand-in. All existing gameplay/content and CONTENT_EDITING.md remain unchanged.

A future designer can use `./tools/validate-content.sh [asset-root]` to validate an isolated pack, `./tools/test-content.sh` for focused content tests, or Gradle validateContent/testContent with configured toolchains. test-core already calls test-content; repeating it is unnecessary. Validation errors include file/field paths (for example maxHealth outside 1..1e9 or missing animation ID). Failed validation does not silently ignore the offending definition.

Host tests validate parser constraints, damage/timeline behavior, references, PNG dimensions and rendering commands. **UNVERIFIED:** physical-device GPU rendering, touch comfort, performance/game feel for new packs, actual knight artwork, and physical reachability of arbitrary designer-authored nav routes. No host check can prove visual sword-to-contact quality on a real device. PNG header checks alone cannot prove an image fully decodes on Android; runtime decode errors remain explicit.

### Source trace index

- Content.Loader.load/loadDefenses/loadEntity/loadAttacks/loadCombos/loadBehaviors/loadAnimations: registration, defaults, field bounds, cross-reference validation.
- TiledLoader.load/layers/props/tileRef/bounds: all supported map/tileset/object/layer/custom-property fields, generated solids/apertures/nav links.
- EntityDef/DefenseDef/AttackDef/ComboDef/BehaviorDef/AnimationDef/LevelDef: immutable parsed definitions and conversions.
- GameScreen constructor; Game(Content)/update/loadLevel/upgrades/exits/restart; AreaState constructor/reset: actual Android instantiation, per-run/per-level state, action order.
- Player.receiveAttack/setGuard/updateConfiguredAttack/startDash/tryHit/tryDeathblow; Enemy.receiveHit/deflected/updateConfigured/select/resolveConfigured: actor ownership, formulas, cancellation, sequence/weighted scheduling.
- Posture.add/update/cancelBreak; CombatConfig constructor: meter, break/grace and tuning invariants.
- AttackRun.phase/strike/canHit/consume/progress/left/event: authoritative attack timelines, box origin and phase selection.
- ContentAnimations.updatePlayer/updateEnemy; ContentAnimationClock.play/advance/sample/event; AnimationDef.frameAt; ContentPainter.character/build/tile: visual mapping, frame timings/events/transitions, transforms, tile ordering.
- SolidPhysics/CollisionManager; WorldNavigator/EnemyNavigator/LadderTraffic: physical collision, supported navigation and hardcoded route handling.

Source links in each field table point to these exact classes; method names identify the reading/applying code without depending on unstable line offsets. This manual describes current implementations, not inferred future behavior.

### Final counts and findings

- **12 content/metadata files inspected:**11 active manifest/definition/map/tileset files plus 1 unregistered atlas metadata JSON. All existing TMJ and TSJ files are included. No knight file or extra entity/animation pack ships in this revision.
- **244 distinct contextual field paths documented** in the tables, counting containers, optional alternative frame representation, explicitly rejected feature markers and identified unused/editor metadata. Repeated definitions share one schema count; entity state animation keys count individually; Tiled custom-property keys count by owning object/schema context. This count is not the number of key occurrences in the shipped files. The breakdown below makes the total reproducible.
- Unused/conditional findings:enemy dash/deathblow/defense-action settings, enemy entity.combo, player behavior, sequence-mode weights/seed, fixed behavior transitions, query-only events, ignored object opacity/metadata, conditional next/FPS behavior, oneWay rejection and unregistered atlas metadata. Full actor ownership and effects are listed in section 12.
- Data-driven limits:closed action/object/state vocabulary, existing melee AI only, no enemy guard/dash/execution, no event response scripting or hot reload, bounded rectangular Tiled subset, and hardcoded simulation/presentation/navigation policies. No gameplay fixes or new features were implemented.

| Schema context | Distinct documented paths |
|---|---|
| manifest | 11 |
| entity | 25 |
| entity.animations | 11 |
| defense | 12 |
| attack | 31 |
| combo | 9 |
| behavior | 14 |
| animation | 26 |
| map | 18 |
| propertyRecord | 3 |
| layer | 18 |
| object | 11 |
| platformProperty | 3 |
| ladderProperty | 6 |
| navigationProperty | 5 |
| spawnProperty | 2 |
| pickupProperty | 2 |
| exitProperty | 2 |
| decorationProperty | 3 |
| tileset | 21 |
| atlasMetadata | 11 |
| **Total** | **244** |


## v0.4.0 foundation extension: per-hit transfers (implemented)

This branch extends the preceding schema with four optional fields. The
baseline revision/inventory above describes the pre-extension pack; this branch
also registers `combat/flurry.json` (one extra attack, no existing moveset/map
changes). This is not a claim that the overall v0.4.0 release is complete.

| Field in attacks[].hits[] | Meaning | Unit | Affects whom | What happens if increased | Restrictions |
|---|---|---|---|---|---|
| `damage` | Current event HP loss | HP | Target | More HP lost | Optional integer0..1e9; omitted inherits attack.damage |
| `postureDamage` | Current unblocked event posture | posture | Target | Earlier break | Optional number0..1e9; omitted inherits attack.postureDamage |
| `blockDamage` | Current blocked event posture | posture | Player defender | Earlier player break | Optional number0..1e9; omitted inherits attack.blockDamage; scaled by defender profile |
| `deflectDamage` | Current deflected event posture | posture | Enemy attacker | Earlier enemy break | Optional number0..1e9; omitted inherits attack.deflectDamage; scaled by player defender profile |

Implementation: `Content.Loader.loadAttacks` validates these values;
`AttackDef.Hit` holds overrides; `AttackRun.damage/postureDamage/blockDamage/deflectDamage`
resolves the current strike's fallback. `Player.healthDamage/postureDamage` and
`Enemy.healthDamage/postureDamage/blockDamage/deflectDamage` consume these
resolved values. Existing formulas above use these resolved amounts in place
of the attack-level amounts. Explicit zero is honored; negative JSON values
are rejected (the internal -1 inheritance sentinel cannot be authored).

The original nine-argument Java Hit constructor remains available for fixtures.
No per-tick allocations are added. Existing once-per-event consumption and
player-only guard/deflect behavior remain unchanged. ContentChecks.perHitTransfers
covers distinct hit values, fallback, HP/posture application, consumption,
player power once, block/deflect direction, and invalid overrides.

## v0.4.0 implemented inventory/equipment schema

The following extends the baseline reference and field count above. It is an
implemented milestone, not a declaration that boss/effect release work is done.
Parser: `Content.Loader.load/loadItems`, `ItemDef` constructor. Runtime:
`Inventory`, `Player`, `Game.upgrades`, `InventoryMenu`, `InputHandler`,
`GameScreen.drawInventory`, `ContentPainter.itemIcon`.

| Field | Meaning/unit and direction | Accepted values/defaults |
|---|---|---|
| manifest.items | Registry file paths, player-only | Optional string array, absent empty |
| manifest.inventory | Player run configuration | Optional object, defaults below |
| inventory.accessorySlots | Accessory slots | Optional integer0..8, default1 |
| inventory.quickSlots | Consumable assignments | Optional integer0..8, default2 |
| inventory.initialItems | Starting/reset owned stacks | Optional array, defaultempty |
| initialItems[].id | Registered item ID | Required existing item, unique in grants |
| initialItems[].quantity | Owned count | Required integer1..item.stackLimit |
| items | Registry root collection | Required array |
| items[].id | Registry identity | Required unique ID, same registry regex |
| name, description | Inspection text | Required strings |
| icon | Item PNG, square UI rendering | Optional relative path, defaultempty drawn square; PNG validation/budget applies |
| category | Slot compatibility | Required weapon/armor/accessory/consumable |
| stackLimit | Maximum owned count | Required integer1..999 |
| attack | Weapon default attack ID | Required on weapon, existing attack; other categories cannot override |
| combo | Weapon buffered followup set | Optional existing combo or empty(default); empty suppresses entity followups while equipped |
| animations | Weapon state animation overrides | Optional object; same state vocabulary as player entity, all clip IDs resolve |
| modifiers | Passive equipped modifiers | Optional object; neutral defaults |
| modifiers.damageMultiplier | Ordinary outgoing player HP multiplier | Optional0..10 default1; larger means more damage dealt |
| modifiers.postureMultiplier | Ordinary outgoing posture multiplier | Optional0..10 default1; larger means more target posture; no defensive transfer scaling |
| modifiers.incomingDamageMultiplier | Player HP damage received | Optional0..10 default1; smaller means armor protection; posture unaffected |
| modifiers.maxHealthBonus | Added player maximum HP | Optional integer0..1e6 default0; no healing on equip |
| effects | Typed consumable effects | Optional object, but consumable must have positive heal or postureRestore |
| effects.heal | HP restored to living owner | Optional integer0..1e6 default0; clamps at maximum |
| effects.postureRestore | Unbroken owner posture removed | Optional0..1e9 default0; does not cancel stagger or alter immunity |
| pickup.kind=item | World inventory acquisition | New enum value; existing health/power retained |
| pickup.itemId | Target item registry ID | Required for item pickup; forbidden on health/power; amount<=stackLimit |

Unknown fields, wrong types/ranges, duplicate registry IDs/grants, unresolved
attacks/combos/animations/icons, incompatible category effects and zero-effect
consumables fail validation. Icons/animation assets are decoded once; no item
image decoding occurs in the menu draw loop. Effects are fixed typed data,
not JSON scripts or event names. Equipment slots cannot be shared by the same
item type; consumable slots reference an owned stack rather than duplicating it.

`Inventory.recompute` rebuilds aggregate passive modifiers from neutral values.
HP bonuses sum; multipliers multiply, and damage is rounded once at final integer
HP application. `Player.healthUpgrade` retains run pickup bonuses separately.
Equipping never heals; unequipping clamps current HP. Reset restores base HP,
clears equipment and repopulates configured starting grants. Inventory actions
reject dead/staggered/dashing/executing/actively attacking owners. Consuming uses
one unit, clears its assignment on the last unit, and can waste healing at full
HP. World item pickups are atomic and remain collectible when a stack is full;
programmatic acquire returns accepted count so callers can handle overflow.

`InventoryMenu` is a simulation-thread model. `InputHandler` queues bounded touch
commands; `Game.setInventoryPaused` clears queued time/input and guard state and
prevents updates even if called by an external game loop. Menu close also clears
queued time/guard. The screen stops animation/camera updates while inspecting.
Descriptions, equipped ID, selected slot, HP and success/failure feedback are
shown. Weapon attacks use their own hitbox/timing/phase-animation definitions;
`ContentAnimations` applies optional weapon state clip overrides. Enemy actors
never acquire inventories or defensive actions.

Limitations: fixed one main weapon/one armor slot;0..8 accessory/quick slots;
no disk save, selling, dropping, crafting or arbitrary passive scripts. Device
visual layout and multitouch delivery remain UNVERIFIED until tested on Android
hardware; host tests exercise model, pause/input isolation and adapter hit areas.

## v0.4.1 encounter registry extension (boss milestone)

Parser/validation: `BossDef` and `Content.Loader.load`. Runtime: `BossEncounter`,
`Game.updateEncounters/signalEncounter/completedEncounters`,
`Enemy.configurePhase`, `ContentAnimations.updateEnemy`, `GameScreen.drawGame`.
All existing JSON remains valid when manifest.bosses is absent.

| Field | Meaning / units / direction | Restrictions and defaults |
|---|---|---|
| manifest.bosses | Encounter registry paths | Optional string array; absent empty |
| bosses | Root encounter collection | Required array |
| id | Stable encounter/completion flag ID | Required unique registry ID |
| name | Boss HUD label | Required string |
| level | Encounter level ID | Required registered level |
| spawn | Named enemy actor | Required existing enemy_spawn; unique ownership per level |
| trigger.x, trigger.y | Activation rectangle origin, world px | Required0..32768 |
| trigger.width, trigger.height | Activation span, world px | Required1..32768; full rectangle inside level |
| phases | Ordered reusable phase definitions | Required1..16 entries |
| phases[].healthBelow | HP fraction eligibility, owner | Required0..1; first1; strictly decreasing |
| phases[].event | Alternate explicit event condition | Optional empty string; nonempty query uses Game.signalEncounter |
| phases[].behavior | Phase moveset/AI settings | Required existing melee behavior |
| phases[].speedMultiplier | Owner ground/nav speed multiplier | Optional0..10 default1; larger means faster |
| phases[].damageMultiplier | Outgoing HP/posture/block/deflect multiplier | Optional0..10 default1; larger magnifies transfers, not HP/posture resistance |
| phases[].maxPosture | Owner break capacity | Required1..1e9; posture resets on entry |
| phases[].transitionDuration | Action lock, seconds | Required0..60; zero skips lock |
| phases[].invulnerable | Prevent damage/posture/deathblow during lock | Optionalfalse; applies only while transition timer positive |
| phases[].animation | Transition clip | Optionalempty, otherwise registered clip; sampled, not animation-driven combat |
| rewards | One-time item unit grants | Required array, max16; unique item IDs |
| rewards[].item | Reward item registry ID | Required existing item |
| rewards[].quantity | Units granted | Required integer1..999 and <=stackLimit |

Before activation the boss remains a physical enemy body but cannot attack or
receive damage. Gravity/terrain collision remain active. Phases change chase/
nav speed, AI pool, outgoing transfers and posture capacity; they do not change
body size, maximum HP or refill HP. Transition resets posture and cancels the
current move. No enemy block/deflect action is added. Explicit event latches can
advance a phase without HP loss. A zero health threshold cannot naturally fire
before defeat, so use an explicit event for that configuration.

Transitions and phase checks run on fixed ticks; a post-contact pass detects
defeat/threshold changes in the same tick without advancing timers twice.
Defeat sets the stable completion flag, hides active HUD and grants each reward
unit once. Overflow stays pending until capacity is available while the level
is active. No per-tick collections/bitmaps are created; a new Posture instance is
created only on phase entry. RESET resets all actors, encounters, reward counters
and completion flags. Exposed completion flags are read-only and run-local;
no disk-save claim is made. Optional arena boundaries are not implemented.
