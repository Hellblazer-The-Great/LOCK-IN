#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
javac --release 17 -d "$out" "$src"/content/*.java "$src/ContentAnimationClock.java" "$src/ContentAnimations.java" "$src/AttackRun.java" "$src/FixtureContent.java" "$src/WorldDefinition.java" "$src/SolidPhysics.java" "$src/WorldNavigator.java" "$src/LadderTraffic.java" "$src/Camera.java" "$src/FrameStats.java" "$src/TerrainArt.java" "$src/AreaDefinition.java" "$src/AreaState.java" "$src/TerrainPhysics.java" "$src/EnemyNavigator.java" "$src/GameWorld.java" "$src/Player.java" "$src/Enemy.java" "$src/EnemyMove.java" "$src/CollisionManager.java" "$src/Game.java" "$src/CombatConfig.java" "$src/Posture.java" "$src/Controls.java" "$src/Vitals.java" "$src/SpriteClip.java" "$src/AnimationClock.java" "$src/CharacterAnimations.java" "$src/Viewport.java" tools/CoreChecks.java tools/EnemyChecks.java tools/GuardChecks.java tools/ControlChecks.java tools/RefinementChecks.java tools/CollisionChecks.java tools/DashChecks.java tools/MovesetChecks.java tools/VisualChecks.java tools/WorldChecks.java tools/TimingChecks.java tools/TimingProbe.java tools/CastleChecks.java
java -cp "$out:app/src/main/assets" com.lockin.game.CoreChecks
java -cp "$out:app/src/main/assets" com.lockin.game.EnemyChecks
java -cp "$out:app/src/main/assets" com.lockin.game.GuardChecks
java -cp "$out:app/src/main/assets" com.lockin.game.ControlChecks
# Compile only the Android adapter against a JVM test double; never package it in the APK.
javac --release 17 -cp "$out:app/src/main/assets" -d "$out" tools/test-support/android/view/MotionEvent.java "$src/InputHandler.java" tools/InputChecks.java
java -cp "$out:app/src/main/assets" com.lockin.game.InputChecks
java -cp "$out:app/src/main/assets" com.lockin.game.RefinementChecks
java -cp "$out:app/src/main/assets" com.lockin.game.CollisionChecks
java -cp "$out:app/src/main/assets" com.lockin.game.DashChecks
java -cp "$out:app/src/main/assets" com.lockin.game.MovesetChecks
java -Djava.awt.headless=true -cp "$out:app/src/main/assets" com.lockin.game.VisualChecks

java -cp "$out:app/src/main/assets" com.lockin.game.WorldChecks

java -cp "$out:app/src/main/assets" com.lockin.game.TimingChecks

./tools/test-render.sh

if [ "${1:-}" = "--profile" ]; then
    java -cp "$out:app/src/main/assets" com.lockin.game.TimingProbe
fi

java -cp "$out:app/src/main/assets" com.lockin.game.CastleChecks

java -cp "$out:app/src/main/assets" com.lockin.game.CastleChecks --content

./tools/test-content.sh
