#!/usr/bin/env python3
"""Fix RedundantIf, UnnecessaryVariable, and KotlinUnusedImport from inspection XML."""

from __future__ import annotations

import html
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = Path(os.environ.get("INSPECTION_REPORT_DIR", "/Users/denis0001-dev/Desktop/report2"))
SKIP_PATH = ("/build/", "node_modules", "dmg-background", ".gradle", "/generated/")


def load_problems(inspection_id: str) -> list[dict]:
    path = REPORT_DIR / f"{inspection_id}.xml"
    if not path.is_file():
        return []
    root = ET.parse(path).getroot()
    out = []
    for p in root.findall("problem"):
        fe = p.find("file")
        if fe is None or not fe.text:
            continue
        rel = fe.text.replace("file://$PROJECT_DIR$/", "")
        if any(s in rel for s in SKIP_PATH) or not rel.endswith(".kt"):
            continue
        line_el = p.find("line")
        hi = p.find("highlighted_element")
        desc = p.find("description")
        out.append(
            {
                "path": rel,
                "line": int(line_el.text) if line_el is not None and line_el.text else None,
                "highlight": hi.text.strip() if hi is not None and hi.text else "",
                "description": html.unescape(desc.text or "") if desc is not None else "",
            }
        )
    return out


def remove_imports(path: Path, imports: set[str]) -> int:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    new = []
    n = 0
    for line in lines:
        stripped = line.strip()
        if stripped in imports or any(stripped == imp.strip() for imp in imports):
            n += 1
            continue
        new.append(line)
    if n:
        path.write_text("".join(new), encoding="utf-8")
    return n


def fix_redundant_if_block(text: str) -> str | None:
    """if (c) return true else return false -> return c; similar patterns."""
    patterns = [
        (r"if\s*\(([^)]+)\)\s*return\s+true\s+else\s+return\s+false", r"return \1"),
        (r"if\s*\(([^)]+)\)\s*return\s+false\s+else\s+return\s+true", r"return !(\1)"),
        (r"if\s*\(([^)]+)\)\s*\{\s*return\s+true\s*\}\s*else\s*\{\s*return\s+false\s*\}", r"return \1"),
        (r"if\s*\(([^)]+)\)\s*\{\s*return\s+false\s*\}\s*else\s*\{\s*return\s+true\s*\}", r"return !(\1)"),
    ]
    for pat, repl in patterns:
        m = re.search(pat, text.strip())
        if m:
            return re.sub(pat, repl, text.strip())
    return None


def apply_redundant_if(problems: list[dict]) -> int:
    by_file: dict[str, list[int]] = defaultdict(list)
    for p in problems:
        if p["line"]:
            by_file[p["path"]].append(p["line"])
    fixed = 0
    for rel, lines in by_file.items():
        path = PROJECT_ROOT / rel
        if not path.is_file():
            continue
        content = path.read_text(encoding="utf-8").splitlines()
        for ln in sorted(set(lines), reverse=True):
            idx = ln - 1
            if idx < 0 or idx >= len(content):
                continue
            # try single line
            new_expr = fix_redundant_if_block(content[idx])
            if new_expr:
                indent = content[idx][: len(content[idx]) - len(content[idx].lstrip())]
                content[idx] = f"{indent}{new_expr}"
                fixed += 1
                continue
            # try 5-line block starting at idx
            chunk = "\n".join(content[idx : idx + 5])
            new_expr = fix_redundant_if_block(chunk)
            if new_expr:
                indent = content[idx][: len(content[idx]) - len(content[idx].lstrip())]
                content[idx] = f"{indent}{new_expr}"
                del content[idx + 1 : idx + 5]
                fixed += 1
    # write back files that changed - need to track per file
    return fixed


def apply_redundant_if_v2(problems: list[dict]) -> int:
    by_file: dict[str, set[int]] = defaultdict(set)
    for p in problems:
        if p["line"]:
            by_file[p["path"]].add(p["line"])
    fixed = 0
    for rel, line_nums in by_file.items():
        path = PROJECT_ROOT / rel
        if not path.is_file():
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        changed = False
        for ln in sorted(line_nums, reverse=True):
            idx = ln - 1
            if idx < 0 or idx >= len(lines):
                continue
            for span in (1, 2, 3, 4, 5):
                chunk = "\n".join(lines[idx : idx + span])
                new_expr = fix_redundant_if_block(chunk)
                if new_expr:
                    indent = lines[idx][: len(lines[idx]) - len(lines[idx].lstrip())]
                    lines[idx : idx + span] = [f"{indent}{new_expr}"]
                    changed = True
                    fixed += 1
                    break
        if changed:
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return fixed


def function_range(lines: list[str], problem_line: int) -> tuple[int, int]:
    """Approximate function body range containing problem_line (1-based)."""
    start = problem_line - 1
    while start > 0 and not re.search(r"^\s*(fun |actual fun |internal fun |private fun |suspend fun )", lines[start]):
        start -= 1
    depth = 0
    end = start
    started = False
    for i in range(start, len(lines)):
        line = lines[i]
        for ch in line:
            if ch == "{":
                depth += 1
                started = True
            elif ch == "}":
                depth -= 1
                if started and depth == 0:
                    return start, i + 1
        if not started and i > start and re.match(r"^\s*(?=return |val |var |if |when )", line):
            # expression-bodied or single statement without braces
            return start, min(i + 3, len(lines))
    return start, min(start + 40, len(lines))


def apply_unnecessary_variable(problems: list[dict]) -> int:
    fixed = 0
    by_file: dict[str, list[dict]] = defaultdict(list)
    for p in problems:
        by_file[p["path"]].append(p)

    for rel, items in by_file.items():
        path = PROJECT_ROOT / rel
        if not path.is_file():
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        for p in sorted(items, key=lambda x: x["line"] or 0, reverse=True):
            var = p["highlight"]
            m = re.search(r"Variable is same as '([^']+)'", p["description"])
            if not m or not var or not p["line"]:
                continue
            source = m.group(1)
            start, end = function_range(lines, p["line"])
            block = lines[start:end]
            block_text = "\n".join(block)
            val_pat = rf"^\s*val\s+{re.escape(var)}\s*=\s*{re.escape(source)}\s*$"
            new_block = []
            removed = False
            for line in block:
                if not removed and re.match(val_pat, line):
                    removed = True
                    continue
                new_block.append(re.sub(rf"\b{re.escape(var)}\b", source, line))
            if removed:
                lines[start:end] = new_block
                fixed += 1
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return fixed


def main() -> int:
    if not REPORT_DIR.is_dir():
        print(f"Missing report dir: {REPORT_DIR}", file=sys.stderr)
        return 1

    imports_by_file: dict[str, set[str]] = defaultdict(set)
    for p in load_problems("KotlinUnusedImport"):
        imp = p["highlight"]
        if imp.startswith("import "):
            imports_by_file[p["path"]].add(imp)
        else:
            imports_by_file[p["path"]].add(f"import {imp}")

    imp_n = 0
    for rel, imps in imports_by_file.items():
        path = PROJECT_ROOT / rel
        if path.is_file():
            imp_n += remove_imports(path, imps)

    if_n = apply_redundant_if_v2(load_problems("RedundantIf"))
    var_n = apply_unnecessary_variable(load_problems("UnnecessaryVariable"))

    print(f"removed imports: {imp_n} lines in {len(imports_by_file)} files")
    print(f"redundant if fixes: {if_n}")
    print(f"inlined variables: {var_n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
