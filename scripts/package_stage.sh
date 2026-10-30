#!/usr/bin/env bash
# Builds a submission zip for one stage, straight from its git tag, so a checkpoint submission
# contains exactly the code that was tagged (nothing from later stages).
#
#   scripts/package_stage.sh 1        -> dist/LABGroup_AssignmentGrp_stage1.zip   (code at tag stage-1)
#   scripts/package_stage.sh 2        -> dist/LABGroup_AssignmentGrp_stage2.zip   (code at tag stage-2)
#   scripts/package_stage.sh 3        -> dist/LABGroup_AssignmentGrp_stage3.zip   (code at tag stage-3)
#   scripts/package_stage.sh final    -> dist/LABGroup_AssignmentGrp.zip          (current main: report, video plan...)
#
# Optional second argument: the zip name without .zip (for example FCS1_Grp1_stage1).
# Stage 1 and 2 packages also get that stage's class diagram (docs/diagrams/stages/), which is
# checked against the tagged source before packaging.
set -euo pipefail
cd "$(dirname "$0")/.."

stage="${1:?usage: scripts/package_stage.sh 1|2|3|final [zip-name]}"
case "$stage" in
  1|2|3) ref="stage-$stage"; default_name="LABGroup_AssignmentGrp_stage$stage" ;;
  final) ref="HEAD";         default_name="LABGroup_AssignmentGrp" ;;
  *) echo "stage must be 1, 2, 3 or final" >&2; exit 1 ;;
esac
name="${2:-$default_name}"

git rev-parse -q --verify "$ref^{commit}" >/dev/null || { echo "tag $ref not found" >&2; exit 1; }
commit="$(git rev-parse --short "$ref^{commit}")"

work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
dir="$work/$name"; mkdir -p "$dir"
git archive "$ref" | tar -x -C "$dir"

if [ "$stage" = 1 ] || [ "$stage" = 2 ]; then
  diagram="docs/diagrams/stages/stage$stage-class-diagram"
  python3 scripts/check_diagram.py "$diagram.puml" "$ref"
  mkdir -p "$dir/docs/diagrams"
  cp "$diagram".puml "$diagram".png "$diagram".svg "$dir/docs/diagrams/"
elif [ "$stage" = 3 ]; then
  # The final class diagram describes the Stage 3 code; the sequence diagram shows a Stage 3 run.
  python3 scripts/check_diagram.py docs/diagrams/class-diagram.puml "$ref"
  mkdir -p "$dir/docs/diagrams"
  cp docs/diagrams/class-diagram.* docs/diagrams/sequence-diagram.* "$dir/docs/diagrams/"
fi

# Check the package really builds and its own victory run still wins.
( cd "$dir" && scripts/build.sh >/dev/null 2>&1 ) || { echo "package does not compile" >&2; exit 1; }
if [ "$stage" != final ]; then
  victory="scripts/stage$stage-victory.txt"
  ( cd "$dir" && scripts/replay.sh "$victory" 2>/dev/null | grep -q "VICTORY" ) \
    || { echo "the stage $stage victory run does not reach VICTORY" >&2; exit 1; }
fi
rm -rf "$dir/out"

cat > "$dir/SUBMISSION.md" <<EOF
# Submission: $( [ "$stage" = final ] && echo "final (all stages)" || echo "Stage $stage" )

| Item | Value |
|---|---|
| Group | <LAB GROUP> / Group <N> |
| Repository | <GITHUB URL> |
| Git ref | \`$ref\` (commit $commit) |
| Packaged | $(date +%Y-%m-%d) |

## Build and run (JDK 17 or newer, no network needed)

macOS / Linux / Git Bash:

\`\`\`
scripts/build.sh
scripts/run.sh
\`\`\`

Any terminal, including Windows cmd and PowerShell (run from this folder; \`javac\` expands the \`*.java\` itself on Windows):

\`\`\`
javac --release 17 -d out src/main/java/restaurantrush/boundary/*.java src/main/java/restaurantrush/control/*.java src/main/java/restaurantrush/entity/*.java src/main/java/restaurantrush/util/*.java
java -cp out restaurantrush.boundary.Main
\`\`\`

Or open the folder in IntelliJ IDEA / Eclipse as a Maven project and run \`restaurantrush.boundary.Main\`.
EOF
if [ "$stage" != final ]; then
cat >> "$dir/SUBMISSION.md" <<EOF

## Reproducible runs for this stage (base setting, the default game at this tag)

| Run | macOS / Linux / Git Bash | Windows cmd | Windows PowerShell |
|---|---|---|---|
| Victory | \`scripts/replay.sh scripts/stage$stage-victory.txt\` | \`java -cp out restaurantrush.boundary.Main < scripts\\stage$stage-victory.txt\` | \`Get-Content scripts\\stage$stage-victory.txt \| java -cp out restaurantrush.boundary.Main\` |
| Defeat | \`scripts/replay.sh scripts/stage$stage-defeat.txt\` | \`java -cp out restaurantrush.boundary.Main < scripts\\stage$stage-defeat.txt\` | \`Get-Content scripts\\stage$stage-defeat.txt \| java -cp out restaurantrush.boundary.Main\` |

Expected results and full transcripts: \`docs/TEST_NOTES.md\` and \`docs/runs/\`.
EOF
fi
cat >> "$dir/SUBMISSION.md" <<EOF

Rules and design decisions: \`docs/GAME_NOTES.md\`.
EOF

mkdir -p dist
rm -f "dist/$name.zip"
( cd "$work" && zip -qr "$OLDPWD/dist/$name.zip" "$name" )
echo "dist/$name.zip  ($ref, commit $commit, $(unzip -l "dist/$name.zip" | tail -1 | awk '{print $2}') files)"
