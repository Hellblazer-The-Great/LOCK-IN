# LOCK IN — Phase 1

A minimal Java Android action prototype. One fixed arena, one player and one stationary melee target. Rendering uses `SurfaceView` / `Canvas`; no Kotlin, game engine, or third-party runtime libraries.

## Open, build, and run

1. Open this directory in Android Studio Meerkat (2024.3.1) or newer, with support for Android Gradle Plugin 8.9.2.
2. Use a JDK 17 or 21 for Gradle (Android Studio's bundled JDK 21 works). Install Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools through SDK Manager; accept their licenses.
3. Allow Gradle sync. Android Studio creates ignored `local.properties` with your SDK path. For command-line use, set `ANDROID_HOME` to your SDK directory instead.
4. Select an Android 6.0 / API 23 or newer device or emulator and press Run. Landscape is requested; the world also letterboxes in other window shapes.

```sh
./gradlew assembleDebug lintDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug
adb shell am start -n com.lockin.game/.MainActivity
```

On Windows use `gradlew.bat`. The Gradle 8.13 wrapper includes a distribution SHA-256 check. First sync needs access to Gradle, Google Maven, and Maven Central.

## Play and verify

Hold LEFT or RIGHT to move; tap JUMP or ATTACK. Separate fingers can hold movement while tapping either action. Actions trigger on a new press, not continuously while held. Dragging between buttons updates the held state. Releasing/cancelling touches or pausing clears inputs. Opposing movement buttons cancel each other.

Walk toward the red target and face it. A gold rectangle shows the active strike area. Each strike deals 25 damage at most once, lasts 0.16 seconds, and has a 0.45-second cooldown. Four hits defeat the target and remove its rectangle. A hit requires both horizontal and vertical overlap. Player health is displayed but cannot decrease because the target does not attack.

Manual device checklist (requires a real device or emulator):

- Hold movement and tap jump/attack with another finger; lift either finger first and verify no stuck movement.
- Jump lands on the platform, cannot double jump, and movement stops at both arena edges.
- Attacks miss when out of range, facing away, or above the target; one swing never causes multiple damage ticks.
- Defeat the target, then background/foreground and lock/unlock during movement. Verify paused simulation, cleared input, and one resumed rendering loop.
- Check a wide phone and tablet/windowed display: proportional shapes, letterboxing, safe controls clear of system bars/cutouts, unchanged world speeds.

Force-stop and relaunch to reset the prototype. State survives pause/resume within the same Activity, but is intentionally not persisted across Activity/process recreation.

## Automated core checks

With a full JDK 17+ installed:

```sh
./tools/test-core.sh
```

This compiles the production simulation directly and checks boundaries, jump/landing, air-jump rejection, frame-rate consistency, range/direction/height misses, per-strike damage, cooldown, defeat, health clamping, and invalid delta time. It requires no Android SDK and does not validate Android touch delivery or rendering.

## Architecture and units

- `Game`: simulation update and combat coordination; clamps stall recovery to 50 ms and substeps at most 1/120 second.
- `Player`: position, velocity, grounded/facing state, health, attack timing and per-strike hit state.
- `Enemy`: stationary target health and defeated state.
- `GameWorld`: fixed 960 × 540 logical world; origin top left, +x right, +y down, distances in logical pixels and speeds in logical pixels/second. Ground is y=380.
- `CollisionManager`: world/ground resolution and axis-aligned hitbox overlap. Characters may overlap; enemy body blocking is outside this phase.
- `InputHandler`: synchronized multitouch state and queued action edges, consumed by the simulation thread. Button coordinates share the world transform.
- `GameScreen`: drawing, touch-coordinate conversion, Surface lifecycle and a paced rendering thread capped around 60 FPS. Updates precede rendering; simulation and drawing share one thread.
- `MainActivity`: entry point, safe system-bar insets and lifecycle forwarding.

The first five classes have no Android dependencies. There is no inheritance-based entity framework. The renderer scales uniformly into the available safe area, adding black letterboxing. Gravity and combat use world coordinates and seconds, independent of display density.

## Limits and next steps

This is a placeholder prototype: no sprites, audio, scrolling, menus, persistence, parrying, posture, dodging, or enemy AI. It supports one target and one melee action. Health currently has no incoming damage source. Very long frame stalls drop elapsed simulation time to prevent tunnelling or sudden jumps. Rendering is paced with sleep rather than display-vsync scheduling.

Next: perform the manual device checklist and tune control sizes/combat feel; then plan the next approved phase (for example enemy attacks and player damage). Keep Android input/rendering adapters separate if migrating the simulation to LibGDX later.

## Verification in this cloud workspace

- Full JDK 21 and Android SDK 35 installed outside the checkout.
- `tools/test-core.sh`: **15 checks passed**.
- Gradle 8.13 `assembleDebug lintDebug`: **passed**; packaged APK declares the expected launcher Activity and minimum API 23 / target API 35.
- Lint: **0 errors, 3 warnings**: requested landscape orientation, backup policy metadata, and missing launcher icon. The placeholder app has no saved user data or custom icon yet.
- Wrapper JAR and distribution SHA-256 verified against Gradle's published hashes; wrapper startup passed.
- No Android device/emulator connected. On-device gameplay, multitouch and lifecycle checks remain unperformed.

The cloud machine uses `/workspace/toolchains/jdk-21.0.6+7` as `JAVA_HOME`, `/workspace/android-sdk` as `ANDROID_HOME`, and `/workspace/.gradle` as `GRADLE_USER_HOME`. These are local tooling paths, not required project settings. This machine's Java networking also requires its configured HTTPS proxy and system trust store; do not copy machine-specific proxy settings into the project.
