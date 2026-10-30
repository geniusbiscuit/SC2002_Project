# Restaurant Rush (SC2002 group assignment)

A turn-based restaurant management game for the command line. You are the manager: staff time is limited,
so you decide whom to seat, whose order to take, what to serve and what the Chef cooks first.

| Item | Value |
|---|---|
| Repository | **<PASTE YOUR GITHUB REPOSITORY URL HERE>** |
| Group | **<LAB GROUP>** (Group **<N>**) |
| Members | **<NAME 1, NAME 2, NAME 3, NAME 4, NAME 5>** |
| Stage tags | `stage-1` (706c320), `stage-2` (beb2923), `stage-3` (027c290); `main` is stage-3 plus documentation |

## Build and run

Requires JDK 17 or newer (tested with 21).

```
scripts/build.sh                         # compile to ./out with plain javac
scripts/run.sh                           # play the Stage 3 game
scripts/run.sh --classic                 # Stage 2 base setting (VIP + Critic, no Host / combos / Happy Hour)
scripts/run.sh --regular-only            # Stage 1 setting (six Regular customers)
mvn test                                 # unit tests (JUnit 5, downloaded by Maven)
mvn package && java -jar target/restaurant-rush.jar
```

On Windows without Git Bash (cmd or PowerShell; `javac` expands the `*.java` itself), or on any OS:

```
javac --release 17 -d out src/main/java/restaurantrush/boundary/*.java src/main/java/restaurantrush/control/*.java src/main/java/restaurantrush/entity/*.java src/main/java/restaurantrush/util/*.java
java -cp out restaurantrush.boundary.Main [--classic | --regular-only]
java -cp out restaurantrush.boundary.Main < scripts\stage3-victory.txt                         (cmd: replay a run)
Get-Content scripts\stage3-victory.txt | java -cp out restaurantrush.boundary.Main             (PowerShell)
```

All three settings share the required base: 18 turns, 2 tables, one Waiter / Chef / Cashier, arrivals on
turns 1, 4, 7, 10, 13 and 16, and the same victory targets (at least 4 paid customers, revenue at least $45,
average satisfaction at least 60). No network access is needed.

To check out a stage: `git checkout stage-1` (or `stage-2`, `stage-3`), then build as above.

## Reproducible runs

Every line of a script is exactly what a human types, so a run is fully reproducible.

| Stage | Run | Command | Result |
|---|---|---|---|
| 1 | Victory | `scripts/replay.sh scripts/stage1-victory.txt --regular-only` | VICTORY, 5 paid, $66.00, average 79.2 |
| 1 | Defeat | `scripts/replay.sh scripts/stage1-defeat.txt --regular-only` | DEFEAT, 0 paid |
| 2 | Victory | `scripts/replay.sh scripts/stage2-victory.txt --classic` | VICTORY, 5 paid, $73.50, average 75.6 |
| 2 | Defeat | `scripts/replay.sh scripts/stage2-defeat.txt --classic` | DEFEAT, 0 paid |
| 3 | Victory | `scripts/replay.sh scripts/stage3-victory.txt` | VICTORY, 5 paid, $88.80, average 80.8 (uses Happy Hour) |
| 3 | Defeat | `scripts/replay.sh scripts/stage3-defeat.txt` | DEFEAT, 0 paid |

Transcripts: `docs/runs/`. More: `docs/TEST_NOTES.md` (tests), `docs/GAME_NOTES.md` (how we read the brief).

## Packages (Boundary / Control / Entity)

| Package | Role |
|---|---|
| `restaurantrush.boundary` | `ConsoleUI`, `Main`: read choices, print state. No game rules. |
| `restaurantrush.control` | `GameController`, `GameConfig`: the turn sequence, phases, win/lose check |
| `restaurantrush.entity` | `Customer` (+ Regular, VIP, Critic), `Staff` (+ Waiter, Host, Chef, Cashier), `MenuItem` (+ `ComboMeal`), `Menu`, `Order`, `Table`, `HappyHour`, `Restaurant` |
| `restaurantrush.util` | `Money` (integer cents) |

Diagrams: `docs/diagrams/` (final class diagram, three focused views, sequence diagram; PlantUML source + images)
and `docs/diagrams/stages/` (the class diagram as it was at `stage-1` and `stage-2`).
`python3 scripts/check_diagram.py` checks the final diagram against the code (classes, members, parameter counts);
`python3 scripts/check_diagram.py docs/diagrams/stages/stage1-class-diagram.puml stage-1` checks a stage diagram against its tag.

Submitting one stage at a time: `scripts/package_stage.sh 1` (or `2`, `3`, `final`) builds a zip in `dist/` straight
from the tag, checks that it compiles and that its victory run still wins. See `docs/WORKFLOW.md`.

---

## Stage comparison

### Stage 1 to Stage 2 (`stage-1` to `stage-2`)

Compare: `<REPO_URL>/compare/stage-1...stage-2` (local: `git diff stage-1 stage-2`)

**New files**

| File | Why |
|---|---|
| `entity/VIPCustomer.java` | VIP: loses 5 per waiting turn, 10% off the bill (overrides `getWaitingLoss`, `billFor`) |
| `entity/CriticCustomer.java` | Critic: loses 12 per waiting turn, always orders the most expensive dish (overrides `getWaitingLoss`, `chooseItem`) |
| `test/.../CustomerBehaviourTest.java` | Tests for the three customer kinds and the "no type checks in the controller" rule |
| `scripts/stage2-victory.txt`, `scripts/stage2-defeat.txt` | Reproducible Stage 2 runs |

**Existing files changed**

| File | What and why |
|---|---|
| `entity/Menu.java` | Added `mostExpensive()` so the Critic's preference is data-driven. No existing method changed. |
| `control/GameConfig.java` | The base setting now schedules a VIP (turn 4) and a Critic (turn 10). Only the registration of the arrivals changed (`RegularCustomer::new` replaced by `VIPCustomer::new` / `CriticCustomer::new`). Added `regularOnlySetting()` so Stage 1 can still be replayed. |
| `control/GameController.java` | The order message shows the list price when a discount applies (`list price $15.00`). **The turn logic is untouched**: that is the point of the polymorphic design. |
| `boundary/Main.java` | `--regular-only` flag for replaying Stage 1. |
| `scripts/replay.sh` | Passes the setting flag through. |
| `docs/*`, `README.md`, tests | Documentation, and tests updated to name the crowd they use. |

**Deleted files:** none. **Evidence that Stage 1 still works:** the Stage 1 transcripts replay word for word with `--regular-only`.

### Stage 2 to Stage 3 (`stage-2` to `stage-3`)

Compare: `<REPO_URL>/compare/stage-2...stage-3` (local: `git diff stage-2 stage-3`)

**New files**

| File | Why |
|---|---|
| `entity/Host.java` | The new staff role: seats one queued customer per turn |
| `entity/Seating.java` | Seating rules extracted from `Waiter` so the Host and the Waiter share them |
| `entity/ComboMeal.java` | A `MenuItem` made of two items (90% of the pair, units added) |
| `entity/HappyHour.java` | The once-per-game pricing policy (20% off, +15 satisfaction, two turns) |
| `test/.../StageThreeEntityTest.java`, `StageThreeFeaturesTest.java` | Tests for the three features |
| `scripts/stage3-victory.txt`, `scripts/stage3-defeat.txt` | Reproducible Stage 3 runs |

**Existing files changed**

| File | What and why |
|---|---|
| `entity/Waiter.java` | `seat` now delegates to `Seating` (same behaviour). `takeOrder` takes the `HappyHour` so the bill is discounted once, when the order is taken. **Signature change** (one extra parameter) with its single caller updated. |
| `entity/Menu.java` | Optional list of combos; `defaultChoiceFor` sends every third customer to a combo **only when combos exist**; `mostExpensive` looks at combos too. Without combos the behaviour is identical to Stage 2. |
| `entity/Customer.java` | One new method `recoverSatisfaction` for Happy Hour. |
| `entity/Restaurant.java` | Owns a `Host` and a `HappyHour`; resets the Host each turn; `applyHappyHourRecovery`. |
| `control/GameController.java` | New `HOST` phase (only when a Host is in the setting), `hostSeat`/`hostWait`, `activateHappyHour`, Happy Hour tick at the end of the turn and recovery at the start. The six-step turn order is unchanged. |
| `control/GameConfig.java` | Two switches (`hostEnabled`, `happyHourEnabled`) and `fullSetting()`. The old constructor still works and leaves both off. |
| `boundary/ConsoleUI.java`, `Main.java` | Host and Happy Hour prompts, status line, combo list; `--classic` flag. Nothing is shown for a feature that is off, so earlier transcripts are unchanged. |
| `scripts/*.sh`, `docs/*`, tests | Flags, documentation, bot extended for the Host and Happy Hour. |

**Deleted files:** none. **Evidence that earlier behaviour still works:** `ScriptedRunTest.earlierRunsReplayIdenticallyOnTheStage3Code` replays all four Stage 1 and 2 runs on the Stage 3 code and requires the output to equal the saved transcripts.

---

## Video timestamps

**<FILL IN AFTER RECORDING>** (about 15 minutes; each member explains a part they understand; see `docs/VIDEO_PLAN.md`)

| # | Segment | Start | Presenter |
|---|---|---|---|
| 1 | The game launching from the submitted project | <mm:ss> | <name> |
| 2 | Meaningful manager choices, including a decision where staff are constrained | <mm:ss> | <name> |
| 3 | A dish going READY, SERVED and paid | <mm:ss> | <name> |
| 4 | A VIP or Critic behaviour | <mm:ss> | <name> |
| 5 | A Stage 3 feature | <mm:ss> | <name> |
| 6 | One victory and one defeat | <mm:ss> | <name> |
| 7 | The stage tag comparisons and the diagrams | <mm:ss> | <name> |

Video link (if not included in the zip): **<LINK>**
