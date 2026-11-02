#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
files=$(rg -L '^import android' "$src" --glob '*.java' --files-without-match)
javac --release 17 -d "$out" $files tools/ValidateContent.java tools/ContentChecks.java
java -cp "$out:app/src/main/assets" com.lockin.game.ContentChecks

javac --release 17 -cp "$out" -d "$out" tools/render-support/android/content/res/AssetManager.java tools/render-support/android/graphics/*.java "$src/ContentPainter.java" tools/ContentRenderChecks.java
java -Djava.awt.headless=true -cp "$out:app/src/main/assets" com.lockin.game.ContentRenderChecks
