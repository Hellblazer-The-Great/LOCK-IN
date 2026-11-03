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
