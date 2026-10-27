#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
javac --release 17 -d "$out" "$src/GameWorld.java" "$src/Player.java" "$src/Enemy.java" "$src/CollisionManager.java" "$src/Game.java" tools/CoreChecks.java
java -cp "$out" com.lockin.game.CoreChecks
