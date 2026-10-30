#!/usr/bin/env bash
# Compiles the game with plain javac (no Maven needed). Output goes to ./out
set -e
cd "$(dirname "$0")/.."
rm -rf out && mkdir -p out
javac --release 17 -d out $(find src/main -name '*.java')
echo "Built into ./out"
