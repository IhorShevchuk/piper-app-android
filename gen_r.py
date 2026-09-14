#!/usr/bin/env python3
"""Generate minimal R.kt per module from src/main/res for sandbox kotlinc compiles.
Usage: gen_r.py <module-dir> <package> <out-dir>
Writes <out-dir>/R.kt with unique int IDs per resource."""
import os
import re
import sys
import xml.etree.ElementTree as ET

module_dir, package, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
res_dir = os.path.join(module_dir, "src", "main", "res")

# type -> set(names)
resources: dict[str, set[str]] = {}

# Values XML files: <string name=..>, <color name=..>, <style name=..>, etc.
values_dir = os.path.join(res_dir, "values")
if os.path.isdir(values_dir):
    for fn in os.listdir(values_dir):
        if not fn.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(values_dir, fn)).getroot()
        except ET.ParseError:
            continue
        for child in root:
            # strip android: namespace quirks; tag is the resource type
            rtype = child.tag.split("}")[-1]
            name = child.get("name")
            if name:
                resources.setdefault(rtype, set()).add(name)

# File-based resources: res/<type>[-qualifiers]/<name>.<ext>
for entry in os.listdir(res_dir) if os.path.isdir(res_dir) else []:
    full = os.path.join(res_dir, entry)
    if not os.path.isdir(full) or entry == "values":
        continue
    rtype = entry.split("-")[0]
    for fn in os.listdir(full):
        name = os.path.splitext(fn)[0]
        if name:
            resources.setdefault(rtype, set()).add(name)

# View/menu IDs: android:id="@+id/..." anywhere under res (menus, layouts).
id_re = re.compile(r'@\+id/([A-Za-z0-9_.]+)')
for dirpath, _, filenames in os.walk(res_dir):
    for fn in filenames:
        if not fn.endswith(".xml"):
            continue
        with open(os.path.join(dirpath, fn), encoding="utf-8",
                  errors="replace") as f:
            for m in id_re.finditer(f.read()):
                resources.setdefault("id", set()).add(m.group(1))

def sanitize(name: str) -> str:
    # AGP replaces dots in style names (Theme.Piper -> Theme_Piper).
    safe = re.sub(r"[^A-Za-z0-9_]", "_", name)
    if re.match(r"^[0-9]", safe):
        safe = "_" + safe
    return safe

next_id = 0x7F000001
lines = [f"package {package}", "", "object R {"]
for rtype in sorted(resources):
    lines.append(f"    object {rtype} {{")
    for name in sorted(resources[rtype]):
        # Kotlin identifiers: some resource names could clash with keywords
        safe = sanitize(name)
        if not re.match(r"^[a-zA-Z_][a-zA-Z0-9_]*$", safe):
            safe = f"`{safe}`"
        lines.append(f"        const val {safe} = {next_id}")
        next_id += 1
    lines.append("    }")
lines.append("}")
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "R.kt"), "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"generated R.kt for {package}: " +
      ", ".join(f"{t}={len(n)}" for t, n in sorted(resources.items())))
