# v0.4.0 implementation status — incomplete

Branch: `feature/v0.4.0-combat-equipment`, based on remote main `cd9967b`.
This branch is work in progress and must not be released as v0.4.0.

Implemented and exercised through the production loader and combat consumers:

- Optional per-hit HP/posture/block/deflect transfers with attack-level fallback.
- Original JSON and Java Hit constructor compatibility.
- Independent once-per-event consumption and player-only defensive transfers.
- An opt-in multi-hit sample using existing assets; original enemies/maps unchanged.
- Regression coverage for actual damage, defensive ownership and invalid content.

Still required before release acceptance:

- Player inventory/item registries, stacking, acquisition, configurable equipment
  slots, modifier recomputation, weapon/combo/animation overrides, consumables,
  and touch inventory/equipment UI.
- Boss encounter registry, deterministic phase conditions/events, phase pools,
  stat changes, transition locks/animations, HUD, rewards and completion flags.
- Typed attack effects: movement/lunges, knockback/hit stun, guard break,
  stagger resistance, distance eligibility, explicit sweep/thrust mechanics,
  armor/invulnerability windows and presentation hitstop/shake.
- Optional projectile/buff systems if included; they are not supported here.
- Weapon/armor/charm/consumable and two-phase boss working samples and tests.
- Full reference/recipe updates for those systems, Android/device interaction
  checks, final version bump and release validation.

No enemy guard, parry, equipment inventory, tactical planner, behavior tree or
arbitrary JSON scripting is introduced. The unfinished local Knight is not used.
