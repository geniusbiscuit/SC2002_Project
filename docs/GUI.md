# Windowed game (optional extra)

A Swing front end for Restaurant Rush. The brief says a GUI earns no extra marks, so this is **optional** and
kept **outside the graded source tree**: it lives in `extras/gui/`, not in `src/main/java`. That way the class
diagram (`docs/diagrams/class-diagram.puml`), `scripts/check_diagram.py`, `mvn test` and the stage zips are
exactly what they were.

It is only a new boundary. Like `ConsoleUI`, it asks the same `GameController` to act and shows what comes back,
so every rule (one task per turn, Happy Hour, victory) is unchanged. No game class was edited. The pictures are
drawn with Java2D code, so there are no image files.

> This front end was written with AI assistance (Claude). Mention it in your GAI declaration.

## Run

```
scripts/run-gui.sh                  # macOS / Linux / Git Bash - Stage 3 game
scripts/run-gui.sh --classic        # Stage 2 setting
scripts/run-gui.sh --regular-only   # Stage 1 setting
scripts\run-gui.bat                 # Windows cmd / PowerShell (same flags)
```

IntelliJ: right-click the folder `extras/gui/src` > **Mark Directory as > Sources Root**, then right-click
`GuiMain` > Run. (Run `scripts/build.sh` or let Maven compile once first so the game classes exist.)
In the window: Game > New game switches the stage; keys 1-9 press the numbered choices and W makes the
current staff member wait.

## What you see

* Left: the queue on a red carpet outside the door; guests walk in, are seated, eat, pay at the cashier and leave.
* Top: the kitchen with order tickets (green dots = cooking progress) and the pass where READY dishes wait.
* Faces show mood; the bar over a head is the real satisfaction; bubbles show what each guest waits for.
* Right: the manager's choices for whoever acts now (Host, Waiter, Chef). Hover a button to highlight that guest,
  hover a guest in the scene for details. Only valid choices are offered; the controller still validates everything.

## Files

| File | Role |
|---|---|
| `GuiMain` | entry point, same flags as `Main` |
| `GameWindow` | window, buttons, log; asks `GameController` to act (boundary) |
| `RestaurantView` | animated picture of the restaurant; reads game state only |
| `Art` | drawing helpers for people, food, bubbles |
