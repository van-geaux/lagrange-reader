#!/usr/bin/env python3
"""Focused regression tests for Android localization validation."""

from __future__ import annotations

import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_android_localizations import validate_entries


PATH = Path("values-test/strings_errors.xml")


def entries(xml: str) -> dict[tuple[str, str], ET.Element]:
    root = ET.fromstring(xml)
    return {
        (element.tag, element.attrib["name"]): element
        for element in root
    }


class LocalizationValidatorTest(unittest.TestCase):
    def test_accepts_locale_specific_plural_quantities(self) -> None:
        source = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="one">Queued %1$d file from %2$s.</item>
                <item quantity="other">Queued %1$d files from %2$s.</item>
              </plurals>
            </resources>
            """
        )
        russian = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="one">Queued %1$d file from %2$s.</item>
                <item quantity="few">Queued %1$d files from %2$s.</item>
                <item quantity="many">Queued %1$d files from %2$s.</item>
                <item quantity="other">Queued %1$d files from %2$s.</item>
              </plurals>
            </resources>
            """
        )
        japanese = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="other">Queued %1$d files from %2$s.</item>
              </plurals>
            </resources>
            """
        )

        self.assertEqual(validate_entries(source, russian, PATH), [])
        self.assertEqual(validate_entries(source, japanese, PATH), [])

    def test_rejects_plural_placeholder_mismatch_by_quantity(self) -> None:
        source = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="one">Queued %1$d file from %2$s.</item>
                <item quantity="other">Queued %1$d files from %2$s.</item>
              </plurals>
            </resources>
            """
        )
        translated = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="other">Queued %1$d files.</item>
              </plurals>
            </resources>
            """
        )

        errors = validate_entries(source, translated, PATH)
        self.assertEqual(len(errors), 1)
        self.assertIn("quantity 'other'", errors[0])

    def test_rejects_invalid_plural_quantity(self) -> None:
        source = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="other">Queued %1$d files.</item>
              </plurals>
            </resources>
            """
        )
        translated = entries(
            """
            <resources>
              <plurals name="queued">
                <item quantity="invalid">Queued %1$d files.</item>
              </plurals>
            </resources>
            """
        )

        errors = validate_entries(source, translated, PATH)
        self.assertEqual(len(errors), 1)
        self.assertIn("invalid plural quantities", errors[0])

    def test_keeps_non_plural_placeholder_check_strict(self) -> None:
        source = entries(
            """
            <resources>
              <string name="message">Hello %1$s.</string>
            </resources>
            """
        )
        translated = entries(
            """
            <resources>
              <string name="message">Hello.</string>
            </resources>
            """
        )

        errors = validate_entries(source, translated, PATH)
        self.assertEqual(len(errors), 1)
        self.assertIn("placeholder mismatch", errors[0])


if __name__ == "__main__":
    unittest.main()
