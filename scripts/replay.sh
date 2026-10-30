#!/usr/bin/env bash
# Replays a recorded run, e.g.  scripts/replay.sh scripts/stage1-victory.txt
# Every line of the file is exactly what a human would type at a prompt.
# Each stage's scripts need that stage's setting:
#   scripts/replay.sh scripts/stage1-victory.txt --regular-only
#   scripts/replay.sh scripts/stage2-victory.txt --classic
#   scripts/replay.sh scripts/stage3-victory.txt
set -e
cd "$(dirname "$0")/.."
[ -d out ] || scripts/build.sh
SCRIPT="${1:?usage: scripts/replay.sh <script file> [--classic|--regular-only]}"
shift
java -cp out restaurantrush.boundary.Main "$@" < "$SCRIPT"
