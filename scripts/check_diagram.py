#!/usr/bin/env python3
"""Checks a class diagram against the Java sources.

Usage:
  python3 scripts/check_diagram.py                       # final diagram vs. the working tree
  python3 scripts/check_diagram.py DIAGRAM.puml TAG      # any diagram vs. the sources at a git tag,
                                                         # e.g. docs/diagrams/stages/stage1-class-diagram.puml stage-1

* every class/enum in the diagram exists in src/main/java
* every attribute and operation listed in the diagram exists in that class' file
* every top-level and nested type in src/main/java appears in the diagram
"""
import pathlib, re, subprocess, sys

root = pathlib.Path(__file__).resolve().parent.parent
diagram = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / "docs/diagrams/class-diagram.puml"
ref = sys.argv[2] if len(sys.argv) > 2 else None
puml = diagram.read_text()
if ref is None:
    src = {p.stem: p.read_text() for p in (root / "src/main/java").rglob("*.java")}
else:
    git = lambda *a: subprocess.run(["git", "-C", str(root), *a], check=True, capture_output=True, text=True).stdout
    files = [f for f in git("ls-tree", "-r", "--name-only", ref, "src/main/java").split() if f.endswith(".java")]
    src = {pathlib.Path(f).stem: git("show", f"{ref}:{f}") for f in files}

# nested types and where they live
FILE_OF = {"Phase": "GameController", "ArrivalSlot": "GameConfig", "Outcome": "GameResult"}
# external type used only as a note, not part of the code base
IGNORE = {"RuntimeException"}

blocks = {}
for m in re.finditer(r'^\s*(?:abstract class|class|enum) (?:"[^"]+" as )?(\w+)[^\n{]*\{\n(.*?)^\s*\}\n', puml, re.S | re.M):
    blocks[m.group(1)] = m.group(2)
# classes declared without a body
for m in re.finditer(r'^\s*class (\w+)(?: <<[^>]+>>)?\s*$', puml, re.M):
    blocks.setdefault(m.group(1), "")

problems = []
for name, body in blocks.items():
    if name in IGNORE:
        continue
    text = src.get(FILE_OF.get(name, name))
    if text is None:
        problems.append(f"class {name}: no Java file")
        continue
    if not re.search(rf'\b(class|enum|record|interface)\s+{name}\b', text):
        problems.append(f"{name}: type declaration not found in source")
    for line in body.splitlines():
        line = line.strip()
        if not line or line.startswith("'"):
            continue
        # enum constants are upper-case words only
        if re.fullmatch(r'[A-Z][A-Z_]*', line):
            if not re.search(rf'\b{line}\b', text):
                problems.append(f"{name}: enum constant {line} missing")
            continue
        line = re.sub(r'\{(static|abstract)\}\s*', '', line)
        mm = re.match(r'[+\-#~]\s*(\w+)\s*\(([^)]*)\)', line)
        if mm:  # operation: the name must exist, declared with the same number of parameters
            op, params = mm.group(1), mm.group(2).strip()
            if not re.search(rf'\b{op}\s*\(', text):
                problems.append(f"{name}: operation {op}() not in source")
                continue
            want = 0 if not params else params.count(",") + 1
            counts = set()
            for d in re.finditer(rf'\b{op}\s*\(([^)]*)\)\s*(?:throws [\w., ]+)?\s*[{{;]', text):
                args = re.sub(r'<[^<>]*>', '', d.group(1)).strip()
                counts.add(0 if not args else args.count(",") + 1)
            if counts and want not in counts:
                problems.append(f"{name}: {op}() has {want} parameter(s) in the diagram, {sorted(counts)} in the source")
            continue
        mm = re.match(r'[+\-#~]\s*(\w+)\s*:', line)
        if mm:  # attribute / record component / constant
            if not re.search(rf'\b{mm.group(1)}\b', text):
                problems.append(f"{name}: attribute {mm.group(1)} not in source")

# coverage: every type declared in the sources appears in the diagram
declared = set()
for stem, text in src.items():
    for m in re.finditer(r'\b(?:class|enum|record)\s+(\w+)', text):
        word = m.group(1)
        if word in ("extends",):
            continue
        declared.add(word)
declared -= {"Case"}
for t in sorted(declared):
    if t not in blocks:
        problems.append(f"type {t} exists in the code but not in the class diagram")

print(f"checked {len(blocks)} diagram classes in {diagram.name} against {len(src)} source files"
      + (f" at {ref}" if ref else ""))
if problems:
    print("\n".join(problems))
    sys.exit(1)
print("OK: the class diagram matches the code")
