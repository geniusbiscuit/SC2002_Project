#!/usr/bin/env python3
"""Derives the Stage 1 and Stage 2 class diagrams from the final (Stage 3) diagram.

For each earlier stage it removes the classes that did not exist yet, removes every attribute,
operation and enum constant that is not in that stage's source (read from the git tag), applies the
few signature changes listed below, and replaces the annotation notes with ones that are true for
that stage. Then check with:

  python3 scripts/check_diagram.py docs/diagrams/stages/stage1-class-diagram.puml stage-1
"""
import pathlib, re, subprocess

root = pathlib.Path(__file__).resolve().parent.parent
master = (root / "docs/diagrams/class-diagram.puml").read_text()
out_dir = root / "docs/diagrams/stages"
out_dir.mkdir(parents=True, exist_ok=True)

FILE_OF = {"Phase": "GameController", "ArrivalSlot": "GameConfig", "Outcome": "GameResult"}

STAGES = {
    "stage-1": {
        "title": "Restaurant Rush - class diagram at stage-1 (Regular customers only)",
        "drop": {"VIPCustomer", "CriticCustomer", "Host", "Seating", "ComboMeal", "HappyHour"},
        "replace": [("+ takeOrder(customer : Customer, menu : Menu, happyHour : HappyHour) : Order",
                     "+ takeOrder(customer : Customer, menu : Menu) : Order")],
        "notes": '''
note right of Customer
  <b>Encapsulation</b>
  All state is private. Only the staff and Restaurant
  (same package) can move a customer through its life
  cycle via package-private methods (~), and every
  transition validates the current state first.
end note

note bottom of RegularCustomer
  <b>Polymorphism (prepared for Stage 2)</b>
  Customer is abstract: getWaitingLoss(), billFor() and
  chooseItem() are the extension points. GameController
  and Restaurant only call the Customer API.
end note

note left of Restaurant
  <b>Composition</b>
  Restaurant owns its Tables, staff and every
  Customer (they do not exist without it).
end note

note bottom of Staff
  <b>Inheritance</b>
  "One task per turn" is implemented once in Staff
  (ensureAvailable / markActed) and shared by
  Waiter, Chef and Cashier.
end note
''',
    },
    "stage-2": {
        "title": "Restaurant Rush - class diagram at stage-2 (VIP and Critic customers)",
        "drop": {"Host", "Seating", "ComboMeal", "HappyHour"},
        "replace": [("+ takeOrder(customer : Customer, menu : Menu, happyHour : HappyHour) : Order",
                     "+ takeOrder(customer : Customer, menu : Menu) : Order")],
        "notes": '''
note right of Customer
  <b>Encapsulation</b>
  All state is private. Only the staff and Restaurant
  (same package) can move a customer through its life
  cycle via package-private methods (~), and every
  transition validates the current state first.
end note

note bottom of CriticCustomer
  <b>Polymorphism</b>
  GameController and Restaurant only call the
  Customer API. getWaitingLoss() (8 / 5 / 12),
  billFor() (VIP -10%) and chooseItem() (Critic)
  are overridden, so no instanceof / type checks.
end note

note left of Restaurant
  <b>Composition</b>
  Restaurant owns its Tables, staff and every
  Customer (they do not exist without it).
end note

note bottom of Staff
  <b>Inheritance</b>
  "One task per turn" is implemented once in Staff
  (ensureAvailable / markActed) and shared by
  Waiter, Chef and Cashier.
end note
''',
    },
}


def git(*args):
    return subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True, text=True).stdout


def sources(ref):
    files = [f for f in git("ls-tree", "-r", "--name-only", ref, "src/main/java").split() if f.endswith(".java")]
    return {pathlib.Path(f).stem: git("show", f"{ref}:{f}") for f in files}


def member_name(line):
    line = re.sub(r'\{(static|abstract)\}\s*', '', line.strip())
    m = re.match(r'[+\-#~]\s*(\w+)', line)
    if m:
        return m.group(1)
    if re.fullmatch(r'[A-Z][A-Z_]*', line):
        return line
    return None


def build(ref, spec):
    src = sources(ref)
    text = master
    for old, new in spec["replace"]:
        text = text.replace(old, new)
    text = re.sub(r'^title .*$', f'title {spec["title"]}', text, count=1, flags=re.M)
    text = text.split("' ---------------- annotations required by the brief")[0] + \
        "' ---------------- annotations\n" + spec["notes"].lstrip("\n") + "@enduml\n"

    out, current, skipping = [], None, False
    for line in text.splitlines():
        decl = re.match(r'^\s*(?:abstract class|class|enum) (?:"[^"]+" as )?(\w+)', line)
        if decl:
            current = decl.group(1)
            skipping = current in spec["drop"]
            if skipping:
                if not line.rstrip().endswith("{"):   # one-line declaration
                    skipping = False
                continue
            out.append(line)
            continue
        if skipping:
            if re.match(r'^\s*\}\s*$', line):
                skipping = False
            continue
        if current and re.match(r'^\s*\}\s*$', line):
            current = None
            out.append(line)
            continue
        if current:
            name = member_name(line)
            body = src.get(FILE_OF.get(current, current), "")
            if name and not re.search(rf'\b{name}\b', body):
                continue
            out.append(line)
            continue
        # relationship lines mentioning a dropped class
        if any(re.search(rf'\b{d}\b', line) for d in spec["drop"]) and not line.lstrip().startswith(("note", "'")):
            continue
        out.append(line)
    return "\n".join(out) + "\n"


for ref, spec in STAGES.items():
    path = out_dir / f"{ref.replace('-', '')}-class-diagram.puml"
    path.write_text(build(ref, spec))
    print("wrote", path.relative_to(root))
