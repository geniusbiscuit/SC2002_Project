#!/usr/bin/env bash
# Builds (if needed) and starts the game interactively.
#   scripts/run.sh                 Stage 3 game (Host, combos, Happy Hour)
#   scripts/run.sh --classic       Stage 2 base setting
#   scripts/run.sh --regular-only  Stage 1 setting
set -e
cd "$(dirname "$0")/.."
[ -d out ] || scripts/build.sh
java -cp out restaurantrush.boundary.Main "$@"
