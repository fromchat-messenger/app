#!/usr/bin/env python3
"""Apply mechanical fixes from IntelliJ inspection XML reports (style / weak warnings)."""

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

SKIP_INSPECTIONS = {
    "GrazieInspection",
    "GrazieStyle",
    "HtmlDeprecatedAttribute",
    "HtmlUnknownTarget",
    "HtmlRequiredLangAttribute",
    "CheckTagEmptyBody",
    "MarkdownIncorrectTableFormatting",
    "MarkdownUnresolvedFileReference",
    "KDocUnresolvedReference",
    "AndroidLintMissingOnRenderProcessGone",
    "AndroidLintQueryPermissionsNeeded",
    "AndroidLintDiscouragedApi",
    "AndroidLintMissingFirebaseInstanceTokenRefresh",
    "BlockingMethodInNonBlockingContext",
    "KotlinConstantConditions",
    "RunBlocking",
    "ReturnFromFinallyBlock",
}

SKIP_PATH_PARTS = ("/build/", "node_modules", "dmg-background", ".gradle", "/generated/")


def should_skip_path(path: str) -> bool:
    return any(part in path for part in SKIP_PATH_PARTS)


def load_problems() -> list[dict]:
    problems: list[dict] = []
    for xml_path in sorted(REPORT_DIR.glob("*.xml")):
        if xml_path.name.startswith("."):
            continue
        inspection_id = xml_path.stem
        if inspection_id in SKIP_INSPECTIONS:
            continue
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        for p in root.findall("problem"):
            pc = p.find("problem_class")
            if pc is None:
                continue
            sev = pc.get("severity", "")
            if sev not in ("WARNING", "WEAK WARNING"):
                continue
            file_el = p.find("file")
            if file_el is None or not file_el.text:
                continue
            rel = file_el.text.replace("file://$PROJECT_DIR$/", "")
            if should_skip_path(rel):
                continue
            if not rel.endswith((".kt", ".kts")):
                continue
            line_el = p.find("line")
            hi_el = p.find("highlighted_element")
            desc_el = p.find("description")
            hint_els = [h.get("value", "") for h in p.findall("hints/hint")]
            problems.append(
                {
                    "id": inspection_id,
                    "path": rel,
                    "line": int(line_el.text) if line_el is not None and line_el.text else None,
                    "highlight": hi_el.text if hi_el is not None and hi_el.text else "",
                    "description": html.unescape(desc_el.text or "") if desc_el is not None else "",
                    "hints": hint_els,
                }
            )
    return problems


def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines(keepends=True)


def write_lines(path: Path, lines: list[str]) -> None:
    path.write_text("".join(lines), encoding="utf-8")


def line_index(lines: list[str], one_based: int | None) -> int | None:
    if one_based is None or one_based < 1 or one_based > len(lines):
        return None
    return one_based - 1


def remove_line(lines: list[str], idx: int) -> None:
    if 0 <= idx < len(lines):
        del lines[idx]


def remove_import(lines: list[str], import_text: str) -> bool:
    target = import_text.strip()
    if not target.startswith("import "):
        target = f"import {target}"
    changed = False
    new_lines: list[str] = []
    for line in lines:
        if line.strip() == target:
            changed = True
            continue
        new_lines.append(line)
    if changed:
        lines[:] = new_lines
    return changed


def remove_annotation_line(lines: list[str], idx: int, snippet: str) -> bool:
    line = lines[idx]
    if snippet in line:
        stripped = line.replace(snippet, "").strip()
        if not stripped or stripped == "@":
            remove_line(lines, idx)
        else:
            lines[idx] = stripped + ("\n" if line.endswith("\n") else "")
        return True
    return False


def fix_redundant_qualifier(lines: list[str], idx: int, highlight: str) -> bool:
    """Remove redundant `package.` prefix when the simple name is highlighted."""
    line = lines[idx]
    # Match longest qualified prefix ending with .highlight
    pattern = re.compile(r"\b[\w.]+\." + re.escape(highlight) + r"\b")
    new_line, n = pattern.subn(highlight, line, count=1)
    if n:
        lines[idx] = new_line
        return True
    return False


def fix_may_be_constant(lines: list[str], idx: int, highlight: str) -> bool:
    line = lines[idx]
    if "const val" in line or highlight not in line:
        return False
    if re.search(r"\bval\s+" + re.escape(highlight) + r"\b", line):
        lines[idx] = line.replace("val ", "const val ", 1)
        return True
    return False


def fix_unused_parameter(lines: list[str], idx: int, param: str) -> bool:
    return False


def fix_redundant_if(lines: list[str], idx: int) -> bool:
    """if (x) return true else return false -> return x"""
    line = lines[idx].strip()
    m = re.match(r"if\s*\((.+)\)\s*return\s+true\s+else\s+return\s+false", line)
    if m:
        indent = lines[idx][: len(lines[idx]) - len(lines[idx].lstrip())]
        lines[idx] = f"{indent}return {m.group(1).strip()}\n"
        return True
    return False


def fix_if_then_elvis(lines: list[str], idx: int) -> bool:
    line = lines[idx]
    m = re.search(r"if\s*\(([^)]+)\)\s*(\S+)\s+else\s+null", line)
    if m:
        cond, then_expr = m.group(1), m.group(2)
        indent = line[: len(line) - len(line.lstrip())]
        lines[idx] = f"{indent}{then_expr}.takeIf {{ {cond.strip()} }}\n"
        return True
    return False


def ensure_file_suppress(lines: list[str], suppressions: set[str]) -> bool:
    if not suppressions:
        return False
    joined = ", ".join(f'"{s}"' for s in sorted(suppressions))
    marker = f"@file:Suppress({joined})"
    for i, line in enumerate(lines[:5]):
        if line.startswith("@file:Suppress"):
            return False
    lines.insert(0, marker + "\n\n")
    return True


def apply_fixes(problems: list[dict]) -> dict[str, int]:
    by_file: dict[str, list[dict]] = defaultdict(list)
    for p in problems:
        by_file[p["path"]].append(p)

    stats: dict[str, int] = defaultdict(int)
    interop_suppress_files: set[str] = set()

    for rel, items in sorted(by_file.items()):
        path = PROJECT_ROOT / rel
        if not path.is_file():
            stats["missing_file"] += 1
            continue
        lines = read_lines(path)
        original = list(lines)

        # Process line-descending so indices stay valid for removals
        items_sorted = sorted(items, key=lambda x: x["line"] or 0, reverse=True)

        for p in items_sorted:
            pid = p["id"]
            idx = line_index(lines, p["line"])
            hi = p["highlight"]
            applied = False

            if pid == "KotlinUnusedImport":
                applied = remove_import(lines, hi)
            elif pid == "UnnecessaryOptInAnnotation" and idx is not None:
                line = lines[idx]
                # Only remove whole-line single annotations; never strip one entry from a multi-line @OptIn.
                if hi in line and line.strip().startswith("@OptIn") and ")" in line:
                    applied = remove_annotation_line(lines, idx, hi)
                elif hi in line and line.strip() == hi.strip():
                    remove_line(lines, idx)
                    applied = True
            elif pid == "RedundantSuppression" and idx is not None:
                remove_line(lines, idx)
                applied = True
            elif pid == "RemoveRedundantQualifierName" and idx is not None:
                applied = fix_redundant_qualifier(lines, idx, hi)
            elif pid == "MayBeConstant" and idx is not None:
                applied = fix_may_be_constant(lines, idx, hi)
            elif pid == "RedundantIf" and idx is not None:
                applied = fix_redundant_if(lines, idx)
            elif pid == "IfThenToElvis" and idx is not None:
                applied = fix_if_then_elvis(lines, idx)
            elif pid == "unused" and idx is not None and p["hints"]:
                param = p["hints"][0]
                if "Parameter" in p["description"]:
                    if "@com.sun.jna" in "".join(lines) or "org.freedesktop.dbus" in "".join(lines):
                        interop_suppress_files.add(rel)
            elif pid == "RedundantSuspendModifier" and idx is not None:
                lines[idx] = lines[idx].replace("suspend ", "", 1)
                applied = True
            elif pid == "RemoveExplicitTypeArguments":
                applied = False
            elif pid == "RemoveToStringInStringTemplate" and idx is not None:
                lines[idx] = re.sub(r"\$\{([^}]+)\.toString\(\)\}", r"${\1}", lines[idx])
                applied = True
            elif pid == "RedundantUnitExpression" and idx is not None:
                applied = False

            if applied:
                stats[pid] += 1

        if rel in interop_suppress_files:
            if ensure_file_suppress(lines, {"unused", "UnusedSymbol"}):
                stats["file_suppress_interop"] += 1

        if lines != original:
            write_lines(path, lines)
            stats["files_changed"] += 1

    return stats


def main() -> int:
    if not REPORT_DIR.is_dir():
        print(f"Report dir not found: {REPORT_DIR}", file=sys.stderr)
        return 1
    problems = load_problems()
    print(f"Loaded {len(problems)} style problems (source .kt only)")
    stats = apply_fixes(problems)
    for key, val in sorted(stats.items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"  {key}: {val}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
