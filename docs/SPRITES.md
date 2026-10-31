# v0.3.0 sprite integration

Base: `5f93895` on `main`. Only rendering and read-only presentation accessors were added; combat durations, damage, collision bodies, attack hitboxes, input bounds and fixed-step ordering remain authoritative and unchanged.

## Assets actually loaded

Paths below are relative to `app/src/main/assets/sprites/`. Frame indices in animation mappings are zero-based; DarkNinja filenames start at `01.png`.

| Pack / PNG(s) | Actual frame grid | Nonblank frames used | Purpose |
|---|---|---|---|
| `DarkNinja/frames/Idle/01..08.png` | 64×64 separate frames | 8 | Idle breathing |
| `DarkNinja/frames/Move/01..08.png` | 64×64 | 8 | Running; frames 4–7 reused at 24 FPS for dash |
| `DarkNinja/frames/Jump/01..12.png` | 64×64 | 12 loaded; 0–5 rising, 8–11 falling | Jump/fall |
| `DarkNinja/frames/Attack1/01..06.png` | **96×64**, not the README's 64×64 | 6 loaded; 3–5 active, 5 recovery | Ordinary attack |
| `DarkNinja/frames/Attack2/01..06.png` | 96×64 | 6 loaded; 3–5 execution | Deathblow execution reuse |
| `DarkNinja/frames/Defend/01..08.png` | 64×64 | 8 | Guard; 0–3 reused for deflect |
| `DarkNinja/frames/Hurt/01..03.png` | 64×64 | 3 | Hurt, stagger, player defeat fallback |
| `Samurai_Bundle/Samurai/Idle/Idle_1.png` | 16×16, 2 columns | 3; fourth cell blank | Enemy idle |
| `Samurai_Bundle/Samurai/Run/Samurai_Run.png` | 16×16, 3 columns | 6 | Enemy chase |
| `Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png` | 48×48, 5 columns | 18; final two cells blank | Normal/double slash |
| `Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png` | **48×32**, 4 columns | 11; final cell blank | Heavy downward strike |
| `Samurai_Bundle/Samurai/Hit/Samurai_Hit.png` | 16×16, 2 columns | 2 | Enemy hurt; first frame held for posture break |
| `Samurai_Bundle/Samurai/Death/Samurai_Death_1.png` | **32×16**, 3 columns | 7; final two cells blank | Enemy death, final corpse frame held |
| `Samurai_Bundle/Map/Zen_Island/Background_1..5.png` | 192×108 each | Static layers | Sky, distant hills, near hills, sun, village silhouettes |
| `Samurai_Bundle/Map/Zen_Island/Tileset_1.png` | 64×24 atlas | Ground `(8,0)-(16,24)`; tree `(48,4)-(64,24)` | Flat platform and two decorative trees |

DarkNinja's README and some Samurai Aseprite metadata disagree with the PNG exports. Layouts above were verified from PNG dimensions, nontransparent cells and enlarged contact sheets. Blank tail cells are excluded. All original PNGs, Aseprite files, readmes and unused moves/effects remain untouched. The renderer does not load `All.png`, alternate idle/attacks, teleport/invisibility, healing, GUI, jump or Zen-mode effects from the Samurai pack.

The explicit manifest is `SpriteClip.java`: 14 clip descriptors share 13 unique animation atlases (two directions/ranges of the same Samurai attack sheet), plus six environment bitmaps. The 51 individual ninja PNGs are decoded once and combined into seven in-memory atlases; temporary frame bitmaps are recycled. Sheets/backgrounds are cached once per GameScreen. Restart reuses those bitmaps. No decoding, frame cropping, bitmap creation or new rectangle allocation occurs in the render loop.

## Attack synchronization

The simulation owns all hit events. `CharacterAnimations` only reads state and normalized progress; `AnimationClock.sample()` adapts frames to each actual phase duration. It never invokes collision, deals damage or modifies game state.

| Enemy phase | Normal / double strike 1 | Double strike 2 | Heavy |
|---|---|---|---|
| Windup | Attack_1 frames 0–3 | Attack_1 frames 8–9 | Attack_Down frames 0–1 |
| Active | Attack_1 frames 4–7 | Attack_1 frames 10–13 | Attack_Down frames 2–5 |
| Recovery | Attack_1 frames 8–9 | Attack_1 frames 14–17 | Attack_Down frames 6–10 |

The return-swing range has opposite native facing, so its clip independently flips toward the opponent while sharing the original sheet. The second windup/strike is a new state token, not a continuation of the first animation. Heavy's longer preparation uses the existing 850 ms windup and a restrained orange marker/HEAVY cue. The player attack already hits immediately in v0.2.4; it therefore begins on a striking frame rather than introducing a cosmetic windup that disagrees with damage.

Looping idle/run and one-shot guard/hurt/death clips reset only on actual state/range changes, not every frame. Enemy death holds the last real corpse frame. Restart revisions clear both animation clocks, hurt feedback and prior-position tracking. Pausing stops the render loop, so presentation time does not elapse in the background.

Priority: player death → deathblow → dash → stagger → active attack → deflect/hurt → guard → airborne → follow-through/run/idle. Enemy death → posture break → combat phases → hurt → run/idle. Hits never hide an enemy windup or active attack. Dash cancellation immediately removes the ordinary attack/recoil pose.

## Anchors, scaling and debugging

Physical bodies stay at player 36×64 and enemy 44×70. Fixed art-space foot anchors align different sheet padding to those bodies; alpha bounds never affect collision or reach. Ninja scales uniformly by 1.6, Samurai by 5. `SpritePainter` keeps bitmap density unset, disables bitmap filtering/dithering, uses integer source rectangles and mirrors around the anchored body center. `Viewport` preserves the existing 960×540 letterbox transform and touch mapping. Drawing is clipped to the virtual arena so sprite padding cannot spill into black letterboxes.

Render order is background/parallax → decorative trees and ground → shadows/characters → combat cues → bars/controls. Background drift is bounded decorative parallax based on player position; there is no scrolling camera or new level. The native sun is repositioned into open sky and all background/tiles retain their aspect ratios.

Set `GameScreen.DEBUG_VISUALS` to `true` to see physical bodies, active hitboxes, current animation and combat state. It is **false** normally. Weapon/swoosh pixels are presentation, never extra attack reach.

## Compromises and validation limits

- No dedicated ninja dash, deflect, deathblow or death clip exists in the supplied frame folders. Dash reuses running, deflect reuses defense with existing contact sparks, execution reuses Attack2, and defeat plays/holds Hurt behind the defeat overlay.
- Samurai posture break reuses its hit pose. Heavy uses the actual downward strike plus the longer existing telegraph; it is not a newly invented attack or asset.
- The packs have different native pixel densities. Uniform scaling aligns body height while retaining the provided art style.
- Host checks decode every selected source, reject blank frames, exercise animation priorities/phase boundaries/restart/flip metadata and verify landscape viewport geometry. These are not an Android screenshot or physical-device gameplay test. Build/lint and local art-placement previews supplement them; touch delivery, GPU rendering and combat feel still need the existing device checklist.
