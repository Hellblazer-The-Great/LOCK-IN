# v0.4.1 — work in progress, not release-ready

Base: main a07243c, containing merged PR #13. Branch release/v0.4.1.
Equipment/inventory/UI from v0.4.0 are preserved.

Boss milestone implemented:

- Optional strict manifest.bosses registry, referencing named enemy spawns.
- Rectangular encounter activation; sleeping actors cannot attack or take damage.
- Ordered phase health thresholds or explicit encounter events; per-phase melee
  behavior, chase speed, outgoing transfers, posture capacity.
- Timed transition action lock, optional invulnerability and sampled animation.
- Boss name, phase, HP and posture HUD; defeat hides HUD and sets a stable flag.
- One-time item rewards; overflow remains pending and retries after space opens.
- Encounter state retained across level transitions; RESET clears flags/rewards.
- Separate opt-in boss_demo level/sample; existing levels and enemies unchanged.

Completion flags are exposed by Game.completedEncounters() using registry IDs.
They are run-local, not disk persistence. Durable storage/import belongs to v0.5.0.

Still outstanding: attack movement/lunges and collision verification; knockback,
hit stun, stagger resistance, armor/interruption and attack immunity windows;
remaining typed gameplay/presentation effects; final release validation/version.
Projectiles, timed buffs and optional arena boundaries are not implemented.
No schema is advertised for these missing features. No enemy defense AI exists.

Device-only validation remains required for HUD readability, transition animation
appearance, touchscreen inventory interaction and Redmi-class performance.

Boss milestone verification: all host suites passed, including283 content checks
and148 content renderer checks. validateContent, testContent, assembleDebug and
lintDebug passed (zero lint errors, three existing warnings). Device HUD/art not
verified. Existing inventory/equipment tests remain in the suite.

Attack motion milestone implemented: optional validated motion interval/distance
with player phase movement multipliers; fixed-step displacement passes through
terrain/body sweeps. lunging_slash sample used by the sample's second phase.
Tests cover integrated displacement, real player movement, boundary collision,
completion and invalid intervals/ranges. Boss demo map simplified to a separate
small arena rather than a duplicated castle; original maps remain unchanged.

Remaining mandatory combat work is NOT complete: knockback/hit stun, stagger
resistance, armor/interruption/immunity windows, guard-breaking behavior and typed
presentation/gameplay effects. Optional projectiles/buffs/arena boundaries are
not supported. Do not label this branch release-ready.

Final verified checkpoint: validateContent, testContent, assembleDebug and
lintDebug passed again after attack motion. All host suites pass (289 content
checks, 148 content-renderer checks, existing equipment/input regressions).
Lint reports zero errors and three existing warnings. No device benchmark or
physical-device HUD/touch validation has been performed.
