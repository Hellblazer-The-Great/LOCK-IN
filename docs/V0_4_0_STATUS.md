# v0.4.0 implementation status — incomplete

Branch: `feature/v0.4.0-combat-equipment`, based on remote main `cd9967b`.
This branch is work in progress and must not be released as v0.4.0.

Implemented and exercised through the production loader and combat consumers:

- Optional per-hit HP/posture/block/deflect transfers with attack-level fallback.
- Original JSON and Java Hit constructor compatibility.
- Independent once-per-event consumption and player-only defensive transfers.
- An opt-in multi-hit sample using existing assets; original enemies/maps unchanged.
- Regression coverage for actual damage, defensive ownership and invalid content.

Equipment/inventory runtime and menu model milestone implemented:

- Optional strict JSON item registries and run inventory configuration.
- Owned stack acquisition, world item pickups, equip/unequip, accessory/quick
  slots, healing/posture consumables and a fixed passive modifier vocabulary.
- Weapon attack/combo/state-animation overrides; modifier rebuilds and HP clamp
  rather than healing on equip. Starting samples are owned but never auto-equipped.
- Headless inspection/menu command model, pause isolation and simulation-thread
  mutations; Android touch surface is the next commit.
- Regression tests for runtime effects, item acquisition, old manifests, menu
  actions, pause isolation, overrides and validation. Device interaction remains
  unverified. validateContent, testContent, assembleDebug and lintDebug passed;
  all host suites passed (265 content checks, 148 content-renderer checks,
  input adapter checks included). Lint: zero errors, three existing warnings.

Still required before release acceptance:

- Android touch inventory/equipment rendering and adapter integration.

- Boss encounter registry, deterministic phase conditions/events, phase pools,
  stat changes, transition locks/animations, HUD, rewards and completion flags.
- Typed attack effects: movement/lunges, knockback/hit stun, guard break,
  stagger resistance, distance eligibility, explicit sweep/thrust mechanics,
  armor/invulnerability windows and presentation hitstop/shake.
- Optional projectile/buff systems if included; they are not supported here.
- Two-phase boss working sample and tests (weapon/armor/charm/consumable samples exist).
- Full reference/recipe updates for those systems, Android/device interaction
  checks, final version bump and release validation.

No enemy guard, parry, equipment inventory, tactical planner, behavior tree or
arbitrary JSON scripting is introduced. The unfinished local Knight is not used.
