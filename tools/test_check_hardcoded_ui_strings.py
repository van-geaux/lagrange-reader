import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))

from check_hardcoded_ui_strings import added_lines, is_hardcoded_ui_string


class HardcodedUiStringCheckTest(unittest.TestCase):
    def test_flags_compose_text_literal(self):
        self.assertTrue(is_hardcoded_ui_string('        Text("Home")'))

    def test_flags_ui_property_literal(self):
        self.assertTrue(is_hardcoded_ui_string('                title = "Dashboard controls"'))
        self.assertTrue(is_hardcoded_ui_string('        contentDescription = "Close"'))

    def test_allows_resource_and_context_calls(self):
        self.assertFalse(
            is_hardcoded_ui_string("        Text(stringResource(R.string.auto_home))")
        )
        self.assertFalse(
            is_hardcoded_ui_string("        title = context.getString(R.string.title)")
        )

    def test_allows_brand_technical_and_symbol_literals(self):
        self.assertFalse(is_hardcoded_ui_string('        Text("Lagrange")'))
        self.assertTrue(is_hardcoded_ui_string('        Text("Lagrange is ready")'))
        self.assertFalse(is_hardcoded_ui_string('        title = "https://example.com"'))
        self.assertFalse(is_hardcoded_ui_string('        title = "application/epub+zip"'))
        self.assertFalse(is_hardcoded_ui_string('        Text("A+")'))
        self.assertFalse(is_hardcoded_ui_string('        Text("▾")'))

    def test_allows_explicit_ignore_marker(self):
        self.assertFalse(
            is_hardcoded_ui_string('        Text("custom value") // i18n-ignore: test fixture')
        )
        self.assertTrue(
            is_hardcoded_ui_string('        Text("custom value") // i18n-ignore')
        )

    def test_does_not_flag_non_ui_literals(self):
        self.assertFalse(
            is_hardcoded_ui_string('        Log.d(TAG, "download failed")')
        )

    def test_parses_only_added_lines(self):
        diff = """diff --git a/app/src/main/java/Example.kt b/app/src/main/java/Example.kt
--- a/app/src/main/java/Example.kt
+++ b/app/src/main/java/Example.kt
@@ -3,0 +4,2 @@
+        Text("Home")
+        Text(stringResource(R.string.home))
"""
        lines = list(added_lines(diff))
        self.assertEqual(
            lines,
            [
                ("app/src/main/java/Example.kt", 4, '        Text("Home")'),
                ("app/src/main/java/Example.kt", 5, "        Text(stringResource(R.string.home))"),
            ],
        )


if __name__ == "__main__":
    unittest.main()
