#!/usr/bin/env python3
"""Fail on newly added hardcoded user-facing Kotlin strings.

Android lint does not detect string literals passed to Compose ``Text`` or to
properties such as ``title``/``contentDescription``. This check looks only at
lines added by the current change, so it does not need a baseline of existing
code. It is intentionally conservative: use ``// i18n-ignore`` on a line with a
real exception (brand, protocol value, etc.).

Usage:
    python3 tools/check_hardcoded_ui_strings.py --base origin/main
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass

UI_LITERAL_PATTERNS = (
    re.compile(r'\bText\(\s*"'),
    re.compile(
        r'\b(?:title|subtitle|label|text|message|contentDescription|placeholder|headline|summary|description)\s*=\s*"'
    ),
    re.compile(r'\.setDisplayName\(\s*"'),
    re.compile(r'\bOrbitEyebrow\(\s*"'),
)
QUOTED_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
ALLOWED_EXACT = {
    "lagrange",
    "lagrange reader",
    "android auto",
    "bookorbit",
    "komga",
    "kavita",
    "gb",
    "a-",
    "a+",
}
IGNORE_MARKER = re.compile(r"//\s*i18n-ignore(?::\s*(?P<reason>\S.*))?")
TECHNICAL_LITERAL = re.compile(
    r"^(?:"
    r"https?://\S*|content://\S*|file://\S*|"
    r"[A-Za-z0-9][A-Za-z0-9+.-]*/[A-Za-z0-9][A-Za-z0-9+.-]*|"
    r"%[0-9]*\$?[a-zA-Z]"
    r")$"
)


@dataclass(frozen=True)
class Finding:
    path: str
    line_number: int
    text: str


def is_hardcoded_ui_string(line: str) -> bool:
    """Return True when a source line contains an obvious hardcoded UI literal."""
    code = line.strip()
    if not code or code.startswith("//"):
        return False
    ignored = IGNORE_MARKER.search(code)
    if ignored and ignored.group("reason") and ignored.group("reason").strip():
        return False
    if "stringResource(" in code or "getString(" in code or "R.string." in code:
        return False
    if not any(pattern.search(code) for pattern in UI_LITERAL_PATTERNS):
        return False
    match = QUOTED_LITERAL.search(code)
    if not match:
        return False
    literal = match.group(1).strip()
    if not literal:
        return False
    if literal.lower() in ALLOWED_EXACT:
        return False
    if TECHNICAL_LITERAL.fullmatch(literal):
        return False
    # Pure symbols/numbers (for example "·", "A+", "1.0x") are not translatable words.
    if not any(character.isalpha() for character in literal):
        return False
    return True


def added_lines(diff: str):
    """Yield (new_line_number, content) for added lines in a unified diff."""
    path = ""
    new_line = 0
    for raw in diff.splitlines():
        if raw.startswith("+++ b/"):
            path = raw[6:]
            continue
        if raw.startswith("@@"):
            match = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", raw)
            if match:
                new_line = int(match.group(1))
            continue
        if raw.startswith("+") and not raw.startswith("+++"):
            yield path, new_line, raw[1:]
            new_line += 1
        elif raw.startswith("-") and not raw.startswith("---"):
            continue


def check_diff(base: str) -> list[Finding]:
    command = [
        "git",
        "diff",
        "--unified=0",
        "--no-color",
        "--no-ext-diff",
        "--no-textconv",
        "--diff-filter=AMR",
        f"{base}...HEAD",
        "--",
        "app/src/main/java",
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stderr.strip(), file=sys.stderr)
        raise SystemExit(f"Could not read diff against {base!r}")
    findings: list[Finding] = []
    for path, line_number, content in added_lines(result.stdout):
        if not path.endswith(".kt"):
            continue
        if is_hardcoded_ui_string(content):
            findings.append(Finding(path, line_number, content.strip()))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default="origin/main",
        help="Git ref to compare against (default: origin/main)",
    )
    args = parser.parse_args()
    findings = check_diff(args.base)
    if not findings:
        print("No newly added hardcoded UI strings found.")
        return 0
    print("Hardcoded user-facing strings must use Android resources:", file=sys.stderr)
    for finding in findings:
        print(f"  {finding.path}:{finding.line_number}: {finding.text}", file=sys.stderr)
    print(
        "Use stringResource(...)/context.getString(...), or add "
        "`// i18n-ignore` with a reason for a real exception.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
