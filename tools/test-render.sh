#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
javac --release 17 -d "$out" tools/render-support/android/content/res/AssetManager.java tools/render-support/android/graphics/*.java "$src/ChunkLayout.java" "$src/WorldDefinition.java" "$src/GameWorld.java" "$src/AreaDefinition.java" "$src/SpriteClip.java" "$src/AnimationClock.java" "$src/TerrainArt.java" "$src/SpritePainter.java" tools/RenderChecks.java
java -Djava.awt.headless=true -cp "$out" com.lockin.game.RenderChecks "$@"
