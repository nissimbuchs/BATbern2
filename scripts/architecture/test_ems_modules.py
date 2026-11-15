# Tests for the EMS module scripts. Run: python3 -I scripts/architecture/test_ems_modules.py
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

DEPS = os.path.join(HERE, 'ems-module-deps.py')
TARGET = os.path.join(HERE, 'ems-module-target.py')


def write(root, rel, text):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(text)


class StripCommentsTest(unittest.TestCase):

    def strip(self, src):
        from ems_modules import strip_comments
        return strip_comments(src)

    def test_line_comment_removed_newline_kept(self):
        self.assertEqual(self.strip('a // Foo\nb'), 'a \nb')

    def test_block_comment_removed(self):
        self.assertEqual(self.strip('a /* Foo\n Bar */ b'), 'a  b')

    def test_url_in_string_is_kept_and_later_code_survives(self):
        src = 'String u = "https://x"; Later l;'
        self.assertEqual(self.strip(src), src)

    def test_comment_markers_inside_string_are_literal(self):
        src = 's = "// not a comment"; t = "/* nor this */"; Kept k;'
        self.assertEqual(self.strip(src), src)

    def test_escaped_quote_in_string(self):
        src = 's = "a\\"// b"; Kept k; // gone'
        self.assertEqual(self.strip(src), 's = "a\\"// b"; Kept k; ')

    def test_char_literals(self):
        src = "c = '\"'; d = '\\''; Kept k; // gone"
        self.assertEqual(self.strip(src), "c = '\"'; d = '\\''; Kept k; ")

    def test_text_block_kept(self):
        src = 's = """\n  // inside "quoted" https://x\n  """; Kept k; // gone\n'
        self.assertEqual(self.strip(src), 's = """\n  // inside "quoted" https://x\n  """; Kept k; \n')

    def test_text_block_with_escaped_triple_quote(self):
        src = 's = """\n a \\""" b\n """; Kept k;'
        self.assertEqual(self.strip(src), src)


class SharedRulesTest(unittest.TestCase):
    """ems-modules.json is the one copy of the naming rules; EmsModuleNamesSharedRulesTest checks the
    same examples on the Java side, so the two interpreters cannot disagree on them."""

    def test_every_example_classifies_as_recorded(self):
        from ems_modules import load_rules, module_of
        rules = load_rules()
        self.assertTrue(rules['examples'])
        for key, expected in rules['examples'].items():
            pkg, name = key.split('/')
            with self.subTest(key=key):
                self.assertEqual(module_of(rules, pkg, name), expected)

    def test_classification_follows_the_file(self):
        from ems_modules import load_rules, module_of
        rules = load_rules()
        self.assertEqual(module_of(rules, 'service', 'EventAnalyticsService'), 'analytics')
        for rule in rules['rules']:
            if rule['module'] == 'analytics':
                rule['contains'] = ['Attendance']
        self.assertEqual(module_of(rules, 'service', 'EventAnalyticsService'), 'event_core')

    def test_scripts_read_the_file(self):
        for script in (DEPS, TARGET):
            with open(script) as f:
                self.assertIn('load_rules()', f.read())


class ScriptsCountAfterUrlTest(unittest.TestCase):
    """A URL in a string before other code must not hide the rest of the file."""

    SRC = ('package ch.batbern.events.registrations;\n'
           'class RegistrationService {\n'
           '  static final String U = "https://example.com/x";\n'
           '  SpeakerService speakers; // a comment SessionService\n'
           '  /* SessionService */\n'
           '}\n')

    def run_scripts(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = os.path.join(tmp, 'src')
            write(src, 'registrations/RegistrationService.java', self.SRC)
            write(src, 'speakers/SpeakerService.java', 'class SpeakerService {}\n')
            write(src, 'sessions/SessionService.java', 'class SessionService {}\n')
            out = os.path.join(tmp, 'out')
            os.makedirs(out)
            subprocess.run([sys.executable, '-I', DEPS, '.', os.path.join(out, 'emsdeps.json')],
                           cwd=src, check=True, capture_output=True)
            subprocess.run([sys.executable, '-I', TARGET, out], cwd=src, check=True, capture_output=True)
            with open(os.path.join(out, 'emsdeps.json')) as f:
                deps = json.load(f)
            with open(os.path.join(out, 'emstarget.json')) as f:
                target = json.load(f)
            return deps, target

    def test_deps_counts_reference_after_url_and_ignores_comments(self):
        deps, _ = self.run_scripts()
        self.assertEqual(deps['edges'].get('registrations|speakers'), 1)
        self.assertNotIn('registrations|sessions', deps['edges'])

    def test_target_counts_reference_after_url_and_ignores_comments(self):
        _, target = self.run_scripts()
        self.assertEqual(target['viol'].get('registrations|speakers'),
                         ['RegistrationService->SpeakerService'])
        self.assertNotIn('registrations|sessions', target['viol'])
        self.assertNotIn('registrations|sessions', target['ok'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
