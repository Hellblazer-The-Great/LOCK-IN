# v0.3.1 world and run state

The production `Game` owns three `AreaState` instances for the entire run. Each contains an immutable `AreaDefinition`, its independent `Enemy[]`, and matching `EnemyNavigator[]`. Inactive areas freeze rather than respawn or simulate off-screen. `game.enemy` remains a compatibility alias for the first active enemy; gameplay iterates `game.enemies()`.

| Area | Enemies | One-way decks (left–right, feet height) | Ladder centers |
| --- | --- | --- | --- |
| Training Grounds | 1, on ground | 320–576 at 268 | 448 |
| Upper Courtyard | 1, upper deck | 192–416 at 284; 448–704 at 188 | 288, 544 |
| Castle Approach | 3, ground and both decks | 224–480 at 268; 544–800 at 172 | 352, 672 |

All areas use the existing 960×540 logical viewport and floor at y=380. Exits are the ground-level screen edges: hold toward the edge to travel, spawning 48 pixels inside the next entrance with a 400 ms transition lock. Both horizontal exits are visible doors; Area 1's left edge stays closed. Area 3's right edge completes the prototype. Completion does not require every enemy to be defeated; enemies remain optional combat encounters. No scrolling or fourth area exists.

## Controls and reset

LEFT/RIGHT, JUMP, ATTACK, GUARD and DASH retain their original positions. UP and DOWN occupy the previously unused space between RIGHT and DASH. Release horizontal movement and hold UP/DOWN near a ladder to climb; release vertical input to hold position. Horizontal movement, JUMP, or DASH exits climbing. JUMP can launch from a ladder. At either endpoint, the character returns to standing/gravity. Hold DOWN on a deck away from its ladder to drop through it. All elevated decks are intentionally one-way; the floor is always solid. You can also walk off a deck.

RESET is always at top center, including after defeat/completion. It pauses simulation and presentation and opens CONFIRM/CANCEL. Cancel preserves the run and ongoing attack timers. Confirm restores the Area 1 spawn, full configured health, zero posture, all five enemy spawns/health/posture/move sequences, navigation, transitions, input queues, cooldowns and visual clocks. Underlying touches are cleared; lift existing fingers before resuming actions. UI commands cross to the simulation through the synchronized input adapter; Android UI callbacks never mutate the world. Lifecycle cancellation cannot lose a pending modal command.

Legacy JUMP-to-restart is replaced in production by confirmed RESET. It remains only inside `Game.combatArena(...)`, the flat fixture used by the historical combat regression suite. The Android screen never uses that fixture.

## Physics and navigation

The simulation still advances at 120 Hz with the existing 50 ms incoming-frame cap. Terrain landing sweeps old/new feet against descending platforms, so a fast fall cannot tunnel through a thin deck. Dropping ignores elevated decks for 250 ms, never the floor. Ladder speed is 140 pixels/second; jump impulse remains -590. The visual sprite padding does not affect 36×64 player or 44×70 enemy bodies. Dashes retain swept solid character collision and their original cooldown/i-frames.

Each area has a small authored surface graph with directed ladder, jump and drop links. Enemy BFS chooses a deterministic route and holds a connection until arrival, avoiding frame-by-frame route oscillation. Ladder links provide reliable access from the floor to every elevation. Direct upper-deck links allow real jumps; jump traversal uses 250 px/s horizontally while ordinary chase stays 110 px/s. Drop connections can walk off a deck to the floor. Enemy gravity, landing, body collision, attacks and navigation all run in the same fixed-step loop. Posture breaks interrupt traversal; an enemy then falls naturally and can plan again. Enemies require the existing detection radius to acquire a target. An unavailable route waits instead of teleporting. There is no adaptive AI or crowd avoidance/path reservation; opponents wait at occupied ladder exits and resume once the player clears the exit; there is no path reservation or alternate-route crowd planning.

Each enemy independently advances normal/double/heavy, health, posture, hit events and animation clocks. Different enemies may each hit during the same tick; a strike still affects the player once, and dash immunity consumes that contact. Player attacks retain the existing single-target commitment per swing, evaluated in deterministic spawn order. A deathblow selects the first eligible living target and prevents a duplicate execution. All combat damage, phase timings, multipliers and deflection windows remain unchanged.

## Art and licensing

Inspected `sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/` recursively: native 16×16 tiles (doors 32×32), x4 exports, 512×96 RGBA `spritesheet.png`, its JSON source rectangles, sample and animation files. Rendering loads **only the atlas**, once, alongside the original character/Zen Island assets. `TerrainArt` explicitly lists verified source rectangles; no README, license, sample, GIF, or unused per-tile PNG is decoded.

Used entries: swamp_top/swamp_center for grass and dirt; log for wood deck detailing; castle_top/castle_center; castle_plat_left/mid/right; castle_wall_bg; ladder_top/mid; door_open; exit_sign; red/blue banners; torch_stand. Tiles normally draw at 2× with filtering disabled. Wood logs decorate a simple Canvas beam aligned to the collision top. Castle platforms have a decorative underside; their collision is the visible top. Zen Island layers remain behind the outdoor/courtyard scene; shaded stone walls distinguish the castle. The existing DarkNinja and Samurai mappings are unchanged; Run is reused for climbing/airborne enemy travel because no dedicated climb/jump clips are integrated.

License: **Pixel Platformer Tileset Vol. 3 by reodesuxz**, ©2026. The committed LICENSE.txt allows game use/modification and forbids standalone asset redistribution/resale and AI training. Original sources/licenses remain untouched. No replacement packs or external artwork were downloaded.

## Validation and limitations

**1,243 checks pass** in the completed release. The scripted end-to-end route uses movement, ladders and ordinary attacks to defeat five stationary practice targets, complete all areas and reset; independent tests exercise active AI and simultaneous attacks.

`./tools/test-core.sh` runs the original flat-arena combat suite plus production world/terrain/reset integration checks and actual InputHandler multitouch tests. New coverage includes both-way transitions, defeated-state persistence, pause/cancel/confirm from all areas, defeat/completion reset, independent and simultaneous enemy hits/animations, climbing/holding/leaving ladders, platform ascent/landing/drop-through, fast descents, dash/body separation, graph climbs/descents/jumps, terrain atlas bounds/pixels and frame-rate agreement through transitions.

`./gradlew --no-daemon assembleDebug lintDebug` builds the APK and validates Android sources. Hardware/GPU rendering, landscape control comfort, actual Android touch delivery, lifecycle interruptions and three-opponent game balance require device playtesting. The JVM MotionEvent double validates adapter logic, not Android's delivery itself. No disk save exists: persistence lasts only for the current process/run. Very long stalls retain the existing elapsed-time cap.

Enable `GameScreen.DEBUG_VISUALS` to see collision bodies, attack hitboxes and combat/animation labels. It is false for ordinary gameplay.
