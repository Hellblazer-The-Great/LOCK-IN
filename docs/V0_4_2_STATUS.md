# LOCK-IN v0.4.2 — combat mechanics completion

Base: main 0114eef (merged PR #14 / v0.4.1). Branch release/v0.4.2.
Existing release branches are preserved. This release does not replace inventory,
bosses, phase pools, transitions, HUD, reward flags or attack motion.

Implemented:
- JSON horizontal/vertical knockback; fixed-step horizontal integration, gravity
  and existing terrain/world/body collision sweeps.
- Hit stun, action suppression, interruption and deterministic recovery.
- Entity stagger-resistance thresholds and timed super-armor windows, with
  ordinary HP/posture damage retained and posture-break/death priority.
- Independent timed HP invulnerability and stagger/posture immunity windows.
- Owner-on-attack-start healing, timed damage/posture buffs/debuffs, global
  hitstop and deterministic render-only camera shake. Fixed bounded stacking
  rules, expiration, pause behavior and reset; no schema-only effects.
- Opt-in player/enemy sample attacks and an ungranted sample weapon using
  existing assets. Existing maps/entities/movesets/starting equipment preserved.
- Strict field/type/range/window validation and runtime regression tests.

Projectiles are intentionally omitted (optional scope). No arbitrary effect
scripts, new AI, enemy defense or enemy inventory. Buff stacking is fixed
newest-value/max-duration, not customizable expressions; HP/posture modifiers
are the supported temporary stats. Healing/effects are owner-on-initiation,
reactions are target-on-unblocked-contact. Durable saves remain outside scope.

Device testing is required for knockback feel, shake comfort, boss HUD/transition
art, physical multitouch/inventory and Redmi 12-class performance. Host tests
validate deterministic runtime state, collision and rendering commands, not GPU
pixels or a real phone's input delivery. Final check results are recorded below.

Verified checkpoint: all five required systems are implemented and tested.
./tools/test-core.sh passed 2,419 host assertions, including343 content/runtime
checks and148 renderer checks. validateContent, testContent, assembleDebug and
lintDebug passed on JDK21/SDK35; lint zero errors and three existing warnings.
Existing boss, equipment/inventory, guard/deflect, dash and motion regressions
remain green. New tests cover reactions/direction/gravity/bounds, stun suppression
and recovery, armor/damage retention, immunity separation/boundaries, resistance,
healing, buff combination/refresh/expiration, hitstop/animation freeze, shake
state, inventory pause, reset and invalid content. Ready for device testing,
not a claim of verified phone performance or final ship approval.
Version metadata is0.4.2 / versionCode9. Draft PR targets main because v0.4.1
was merged. No existing branch is deleted or rewritten; PR is not merged.
