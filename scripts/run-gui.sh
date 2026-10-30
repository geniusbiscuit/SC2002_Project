#!/usr/bin/env bash
# Builds the game and the optional windowed front end, then starts the window.
#   scripts/run-gui.sh                 Stage 3 game (Host, combos, Happy Hour)
#   scripts/run-gui.sh --classic       Stage 2 base setting
#   scripts/run-gui.sh --regular-only  Stage 1 setting
set -e
cd "$(dirname "$0")/.."
scripts/build.sh >/dev/null
javac --release 17 -cp out -d out $(find extras/gui/src -name '*.java')
java -cp out restaurantrush.boundary.gui.GuiMain "$@"
