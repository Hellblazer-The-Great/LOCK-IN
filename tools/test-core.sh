#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
javac --release 17 -d "$out" "$src/GameWorld.java" "$src/Player.java" "$src/Enemy.java" "$src/EnemyMove.java" "$src/CollisionManager.java" "$src/Game.java" "$src/CombatConfig.java" "$src/Posture.java" "$src/Controls.java" "$src/Vitals.java" tools/CoreChecks.java tools/EnemyChecks.java tools/GuardChecks.java tools/ControlChecks.java tools/RefinementChecks.java tools/CollisionChecks.java tools/DashChecks.java tools/MovesetChecks.java
java -cp "$out" com.lockin.game.CoreChecks
java -cp "$out" com.lockin.game.EnemyChecks
java -cp "$out" com.lockin.game.GuardChecks
java -cp "$out" com.lockin.game.ControlChecks
# Compile only the Android adapter against a JVM test double; never package it in the APK.
javac --release 17 -cp "$out" -d "$out" tools/test-support/android/view/MotionEvent.java "$src/InputHandler.java" tools/InputChecks.java
java -cp "$out" com.lockin.game.InputChecks
java -cp "$out" com.lockin.game.RefinementChecks
java -cp "$out" com.lockin.game.CollisionChecks
java -cp "$out" com.lockin.game.DashChecks
java -cp "$out" com.lockin.game.MovesetChecks
