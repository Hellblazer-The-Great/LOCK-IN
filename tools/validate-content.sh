#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/lockin/game
# Pure Java files only. Android remains an adapter, never needed by the content validator.
files=$(rg -L '^import android' "$src" --glob '*.java' --files-without-match)
javac --release 17 -d "$out" $files tools/ValidateContent.java
java -cp "$out" com.lockin.game.ValidateContent "${1:-app/src/main/assets}"
