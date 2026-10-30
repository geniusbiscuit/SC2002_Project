# Test notes

## How to run

```
# unit tests (needs Maven and internet the first time, to download JUnit)
mvn test

# build and play yourself (no Maven needed)
scripts/build.sh
scripts/run.sh

# replay a recorded run (each stage needs its own setting flag)
scripts/replay.sh scripts/stage3-victory.txt            # Stage 3: Host + combos + Happy Hour
scripts/replay.sh scripts/stage3-defeat.txt
scripts/replay.sh scripts/stage2-victory.txt --classic  # Stage 2: VIP + Critic
scripts/replay.sh scripts/stage2-defeat.txt --classic
scripts/replay.sh scripts/stage1-victory.txt --regular-only  # Stage 1: six Regular customers
scripts/replay.sh scripts/stage1-defeat.txt --regular-only
```

Each line of a script file is exactly what a human types at a prompt, and the game echoes it, so
the replay output reads like a real session. Full transcripts are saved in `docs/runs/`.

## Reproducible runs (base setting)

| Stage | Run | Script (and flag) | Result |
|---|---|---|---|
| 1 | Victory | `scripts/stage1-victory.txt --regular-only` | VICTORY: 5 paid, revenue $66.00, average satisfaction 79.2, 0 unhappy departures |
| 1 | Defeat | `scripts/stage1-defeat.txt --regular-only` | DEFEAT: a manager who only presses Wait. 0 paid, $0.00, 2 unhappy departures |
| 2 | Victory | `scripts/stage2-victory.txt --classic` | VICTORY: 5 paid, revenue $73.50 (VIP pays $13.50 for a $15.00 Burger), average satisfaction 75.6 |
| 2 | Defeat | `scripts/stage2-defeat.txt --classic` | DEFEAT: a manager who only presses Wait. 0 paid, $0.00, 2 unhappy departures (the Regular and the Critic) |
| 3 | Victory | `scripts/stage3-victory.txt` | VICTORY: 5 paid, revenue $88.80, average satisfaction 80.8. Uses the Host every turn, a $24.30 Salad + Pasta combo, a $29.70 Burger + Pasta combo for the Critic, and Happy Hour when the VIP arrives (VIP Burger $15.00 -> $10.80) |
| 3 | Defeat | `scripts/stage3-defeat.txt` | DEFEAT: a manager who only presses Wait (including "No" to Happy Hour). 0 paid, $0.00, 2 unhappy departures |

Transcripts of every run are in `docs/runs/`. **Regression check:** the four Stage 1 and Stage 2 transcripts are
word-for-word identical when replayed on the Stage 3 code (`earlierRunsReplayIdenticallyOnTheStage3Code` compares each
replay with the saved transcript), so earlier behaviour is unchanged.

How the victory run is played: each turn the Waiter serves a READY dish if there is one, otherwise takes an
order, otherwise seats the least satisfied queued customer; the Chef works on the order with the fewest units
left. The script was produced by `src/test/java/restaurantrush/support/GreedyManager.java` and is replayed
through the real console UI by `ScriptedRunTest`.

## What the automated tests cover

| Test class | What it proves |
|---|---|
| `TurnRulesTest` | The worked example of the brief, turn by turn (satisfaction 92, 84, 76, 76, 76, paid on turn 6); Waiter before Chef; one task per turn; a dish finished this turn is served next turn; invalid choices explain themselves and keep the task; abandonment at 0 cancels the order and frees the table; abandoned orders never count as revenue; victory targets are inclusive at the boundary; the game always runs 18 turns; at most five customers can pay |
| `EntityRulesTest` | Deterministic food rule; Cashier pays one customer per turn, oldest ready first, never on the turn they became ready; payment frees the table and records satisfaction; served customers lose nothing; satisfaction never below 0 |
| `ScriptedRunTest` | All six victory and defeat scripts through the real UI; the earlier runs replay word-for-word on the Stage 3 code; bad input (text, out of range, unknown id) is rejected and asked again |
| `CustomerBehaviourTest` (Stage 2) | Waiting loss 8 / 5 / 12; VIP 10% off each dish; Critic orders the most expensive dish; all customer kinds follow the same flow to payment (Regular pays $18.00 at satisfaction 76, VIP $16.20 at 85, Critic $18.00 at 64); a Critic leaves before a Regular and a VIP after; base setting still has six arrivals with a VIP and a Critic; the controller contains no type checks |
| `StageThreeEntityTest` (Stage 3) | ComboMeal price (90%, half-up), units and normal ordering/preparation; who orders a combo; the Critic and combos; VIP discount then Happy Hour = $10.80; Happy Hour lasts 2 turns, once per game, only when enabled; recovery only for customers still waiting and capped at 100; Host and Waiter share the seating rules |
| `StageThreeFeaturesTest` (Stage 3) | Host seats in addition to the Waiter and acts first; same-turn seat-and-order; no Host in earlier settings; rejected Host choices keep the task; Happy Hour at turn start only, once, remaining duration 2 then 1; +15 recovery on both turns and not after; price locked at order time; the full setting is winnable with and without Happy Hour |
| `MoneyTest` | Integer cents formatting and rounding |

## Running the tests on Windows (finding)

`mvn test` first failed on Windows with 7 failures, all in `ScriptedRunTest` (the six victory/defeat script tests and
`earlierRunsReplayIdenticallyOnTheStage3Code`). Cause: the console prints with the platform line separator, which is
`\r\n` on Windows, but the tests looked for `"\nVICTORY\n"` and compared with transcripts saved with `\n`.
The game itself was correct. Fix (test code only): `ScriptedRunTest` now normalises `\r\n` to `\n` on both sides
before comparing, and `.gitattributes` keeps `scripts/` and `docs/runs/` text files on `\n` in every checkout.
Result after the fix: <confirm: 68 tests pass on the Windows laptop>. This was not visible on macOS/Linux, which is why each stage
package must also be tested on Windows (see `docs/WORKFLOW.md`).
