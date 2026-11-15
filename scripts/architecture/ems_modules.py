# Shared helpers of the EMS module scripts (ems-module-deps.py, ems-module-target.py).
import json
import os
import re


def strip_comments(src):
    """Remove Java comments, keep every string, char and text-block literal as it is.

    A line comment is removed up to (not including) its newline; a block comment entirely.
    Literals stay, because the scripts count capitalized tokens in them on purpose.
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('"""', i):
            j = i + 3
            while j < n and not src.startswith('"""', j):
                j += 2 if src[j] == '\\' else 1
            j = min(j + 3, n)
            out.append(src[i:j]); i = j
        elif c == '"' or c == "'":
            j = i + 1
            while j < n and src[j] != c and src[j] != '\n':
                j += 2 if src[j] == '\\' else 1
            j = min(j + 1, n)
            out.append(src[i:j]); i = j
        elif src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c); i += 1
    return ''.join(out)


RULES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ems-modules.json')


def load_rules(path=RULES_FILE):
    """The naming rules of ems-modules.json (shared with the EMS ArchUnit rules, EmsModuleNames)."""
    with open(path) as f:
        return json.load(f)


def base_module(rules, pkg, name):
    """Module as ems-module-deps.py assigns it: first matching rule, then the overrides."""
    module = 'platform'
    for rule in rules['rules']:
        if (any(pkg.startswith(p) for p in rule['packages'])
                or any(name.startswith(k) for k in rule['startsWith'])
                or any(k in name for k in rule['contains'])):
            module = rule['module']
            break
    return rules['overrides'].get(name, module)


def target_module(rules, name, module):
    """Module as ems-module-target.py reassigns it: the last matching target group, then the renames."""
    for target in rules['target']:
        if any(re.fullmatch(p, name) for p in target['patterns']):
            module = target['module']
    return rules['renames'].get(module, module)


def module_of(rules, pkg, name):
    """Final module of a top-level EMS class (first package segment below the EMS root, simple name)."""
    return target_module(rules, name, base_module(rules, pkg, name))
