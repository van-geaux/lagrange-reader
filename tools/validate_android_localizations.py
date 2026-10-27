#!/usr/bin/env python3
"""Validate Android string resources against the English source files."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
SOURCE_DIR = ROOT / "values"
FORMAT_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")


def resource_entries(path: Path) -> tuple[dict[tuple[str, str], ET.Element], list[str]]:
    entries: dict[tuple[str, str], ET.Element] = {}
    errors: list[str] = []
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        return {}, [f"{path}: XML parse error: {error}"]

    for element in root:
        if element.tag not in {"string", "plurals"}:
            continue
        name = element.attrib.get("name")
        if not name:
            errors.append(f"{path}: {element.tag} is missing a name")
            continue
        key = (element.tag, name)
        if key in entries:
            errors.append(f"{path}: duplicate {element.tag} name {name!r}")
        entries[key] = element
    return entries, errors


def element_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholders(element: ET.Element) -> list[str]:
    return sorted(FORMAT_RE.findall(element_text(element)))


def plural_quantities(element: ET.Element) -> list[str]:
    return sorted(item.attrib.get("quantity", "") for item in element if item.tag == "item")


def main() -> int:
    source_files = sorted(SOURCE_DIR.glob("strings*.xml"))
    if not source_files:
        print(f"No source files found under {SOURCE_DIR}", file=sys.stderr)
        return 1

    source: dict[Path, dict[tuple[str, str], ET.Element]] = {}
    errors: list[str] = []
    for path in source_files:
        entries, parse_errors = resource_entries(path)
        source[path] = entries
        errors.extend(parse_errors)

    locale_dirs = sorted(
        path for path in ROOT.iterdir() if path.is_dir() and path.name.startswith("values-")
    )
    if not locale_dirs:
        print(f"No translated resource directories found under {ROOT}", file=sys.stderr)
        return 1

    for locale_dir in locale_dirs:
        for source_path, source_entries in source.items():
            translation_path = locale_dir / source_path.name
            if not translation_path.exists():
                errors.append(f"{translation_path}: missing translation file")
                continue
            translated, parse_errors = resource_entries(translation_path)
            errors.extend(parse_errors)

            expected = {
                key
                for key, element in source_entries.items()
                if element.attrib.get("translatable", "true") != "false"
            }
            actual = set(translated)
            for key in sorted(expected - actual):
                errors.append(f"{translation_path}: missing {key[0]} {key[1]!r}")
            for key in sorted(actual - expected):
                errors.append(f"{translation_path}: unexpected {key[0]} {key[1]!r}")

            for key in sorted(expected & actual):
                source_element = source_entries[key]
                translated_element = translated[key]
                if placeholders(source_element) != placeholders(translated_element):
                    errors.append(
                        f"{translation_path}: placeholder mismatch for {key[1]!r}: "
                        f"source={placeholders(source_element)!r}, "
                        f"translation={placeholders(translated_element)!r}"
                    )
                if key[0] == "plurals":
                    source_quantities = plural_quantities(source_element)
                    translated_quantities = plural_quantities(translated_element)
                    if source_quantities != translated_quantities:
                        errors.append(
                            f"{translation_path}: plural quantity mismatch for {key[1]!r}: "
                            f"source={source_quantities!r}, translation={translated_quantities!r}"
                        )

    if errors:
        print("Android localization validation failed:", file=sys.stderr)
        print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
        return 1

    translated_files = len(locale_dirs) * len(source_files)
    print(
        f"Android localization validation passed: {len(source_files)} source files, "
        f"{len(locale_dirs)} locales, {translated_files} translated files."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
