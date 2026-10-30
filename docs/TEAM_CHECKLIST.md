# What only the group can do

Everything else (code, tests, diagrams, README, report draft, video plan, per-stage packages) is done. The items below
need a real person and cannot be done by an AI assistant. **Who does what, and when:** `docs/WORKFLOW.md`.

## Must do (graded or required to submit)

- [ ] **Ask the TA what each checkpoint must contain** and the zip name for each. (Our copy of the brief says all stages are submitted together; you were told otherwise.)
- [ ] **Sign the Declaration of Original Work** (course form, one per group) and have **each member submit the Declaration on Use of GAI Assistance** (course forms). Be honest about how AI was used.
- [ ] **Create the GitHub repository and push one stage at a time** (commands in `docs/WORKFLOW.md` section 1): Stage 1 now, each later stage just before its checkpoint, `main` and all tags at the end. Check each tag appears under Tags and builds on its own.
- [ ] **Fill the placeholders** (search for `<` in these files):
  - `README.md`: repository URL, group name, member names, the two compare links (`.../compare/stage-1...stage-2` and `.../compare/stage-2...stage-3`), video timestamps and link.
  - `docs/report/SC2002_Restaurant_Rush_Report.docx`: group, members, repository, video, contribution table, GAI-use paragraph, individual reflections (section 7.2). Then re-export the PDF (File > Save as PDF), because the brief asks for a PDF report.
- [ ] **Understand the code.** Each member must explain a part in the video. Read `docs/GAME_NOTES.md`, the report section 4, and the classes of the part you present. Run the game yourself at least once.
- [ ] **Record the video** (about 15 minutes, every member speaks) following `docs/VIDEO_PLAN.md`; list the timestamps in the README.
- [ ] **Write your own reflections** (report 7.2). The project-level lessons in 7.1 are a draft: edit them to match what you actually experienced.
- [ ] **Submit each checkpoint** with `scripts/package_stage.sh 1` / `2` / `3` (zip built from the tag, in `dist/`), after one member on Windows and one on Mac has unzipped it and followed its `SUBMISSION.md`.
- [ ] **Final submission**: one zip named `LABGroup_AssignmentGrp.zip` (for example `FCS1_Grp1.zip`) from `scripts/package_stage.sh final FCS1_Grp1`, plus the PDF report, video (or a link that stays available) and the signed declarations.

## Should check

- [ ] **Real git author names.** The commits in the provided repository carry the placeholder author "SC2002 Group 8 <group@example.com>" (the group number is not real). If graders look at commit history for each member's contribution, set `git config user.name/email` and make your own commits for your own changes (for example report and README edits). Do not rewrite history to fake authorship.
- [ ] **Maven.** `pom.xml` could not be tested here (no access to Maven Central). Run `mvn test` on your machine (or run the tests from IntelliJ). If it fails, the game itself still builds with `scripts/build.sh` or the `javac` line in the README; send the error and it can be fixed.
- [ ] **Java version.** The code targets Java 17 and was built with JDK 21. Confirm it compiles on the JDK your group uses (`java -version`).
- [ ] **Git history in the zip.** The stage packages contain the source at the tag, not the `.git` folder; the GitHub repository is the history evidence. If the TA wants the history inside the zip too, zip the whole cloned folder instead.

## Your call (decisions we made, in `docs/GAME_NOTES.md`)

The brief left these open. If your tutor or group prefers a different reading, tell us or edit the code and the notes:

- The Critic's "special behaviour" is: always orders the most expensive dish. (Brief: "documented special behaviour".)
- Which customers order a combo (every third customer) and what customers order (deterministic rule).
- Arrivals happen before the start-of-turn display; the Host acts before the Waiter.
- Happy Hour is offered at the start of every turn while unused, and the VIP discount is applied before the Happy Hour discount.

## Optional

- A GUI earns no extra marks (brief), so we did not build one.
- If you change any code, run `scripts/build.sh`, `mvn test` and `python3 scripts/check_diagram.py` again; if you change a class, update the class diagram `.puml`, run `python3 scripts/stage_diagrams.py`, and regenerate the images with **two separate** commands (`plantuml -tpng docs/diagrams/*.puml docs/diagrams/stages/*.puml`, then the same with `-tsvg`); giving both flags at once silently produces only one format.
