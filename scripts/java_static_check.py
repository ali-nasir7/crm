#!/usr/bin/env python3
"""
java_static_check.py - static integrity checks for the backend Java sources.

Run anywhere Python 3 runs (no dependencies):

    python scripts/java_static_check.py

Checks (all must pass; exit 1 otherwise):
  [1] String/brace/comment state-machine balance on every .java file
  [2] Every `import com.crm...` resolves to a real file (nested records included)
  [3] Common JDK types (UUID, Instant, Map, ...) are imported, wildcard-imported,
      or used fully-qualified - catches "missing type" compile errors before Maven

NOTE: this is a lint-grade net, NOT a compiler. Always finish with `mvn clean compile`.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = [os.path.join(ROOT, 'backend', 'src', 'main', 'java'),
       os.path.join(ROOT, 'backend', 'src', 'test', 'java')]

JDK_TYPES = {
    'UUID': 'java.util.UUID', 'Instant': 'java.time.Instant', 'Map': 'java.util.Map',
    'List': 'java.util.List', 'ArrayList': 'java.util.ArrayList', 'HashMap': 'java.util.HashMap',
    'LinkedHashMap': 'java.util.LinkedHashMap', 'Set': 'java.util.Set', 'HashSet': 'java.util.HashSet',
    'BigDecimal': 'java.math.BigDecimal', 'Duration': 'java.time.Duration',
    'LocalDateTime': 'java.time.LocalDateTime', 'LocalDate': 'java.time.LocalDate',
    'Optional': 'java.util.Optional', 'Collectors': 'java.util.stream.Collectors',
    'IOException': 'java.io.IOException', 'Arrays': 'java.util.Arrays', 'TreeSet': 'java.util.TreeSet',
    'TreeMap': 'java.util.TreeMap', 'LinkedHashSet': 'java.util.LinkedHashSet',
    'ChronoUnit': 'java.time.temporal.ChronoUnit', 'DateTimeFormatter': 'java.time.format.DateTimeFormatter',
}


def java_files():
    out = []
    for r in SRC:
        if os.path.isdir(r):
            for dp, _, fns in os.walk(r):
                out += [os.path.join(dp, f) for f in fns if f.endswith('.java')]
    return out


def strip_code(s):
    """Remove comments and string/char literals, preserving structure."""
    st, esc, out = 'code', False, []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        nxt = s[i + 1] if i + 1 < n else ''
        if st == 'code':
            if c == '/' and nxt == '/':
                st = 'line'; i += 2; continue
            if c == '/' and nxt == '*':
                st = 'block'; i += 2; continue
            if c == '"':
                st = 'str'; out.append('""'); i += 1; continue
            if c == "'":
                st = 'chr'; out.append("''"); i += 1; continue
            out.append(c)
        elif st == 'line':
            if c == '\n':
                st = 'code'; out.append(c)
        elif st == 'block':
            if c == '*' and nxt == '/':
                st = 'code'; i += 2; continue
            if c == '\n':
                out.append(c)
        elif st in ('str', 'chr'):
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif (st == 'str' and c == '"') or (st == 'chr' and c == "'"):
                st = 'code'
        i += 1
    return ''.join(out)


def check_balance(path, s):
    st, esc = 'code', False
    pairs = {'}': '{', ')': '(', ']': '['}
    stack, line = [], 1
    for ch in s:
        if ch == '\n':
            line += 1
        if st == 'code':
            if ch == '"':
                st = 'str'
            elif ch == "'":
                st = 'chr'
            elif ch in '({[':
                stack.append((ch, line))
            elif ch in ')}]':
                if not stack or stack[-1][0] != pairs[ch]:
                    return f'unbalanced {ch} at line {line}'
                stack.pop()
        elif st in ('str', 'chr'):
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif (st == 'str' and ch == '"') or (st == 'chr' and ch == "'"):
                st = 'code'
    if stack:
        return f'unclosed {stack[-1][0]} from line {stack[-1][1]}'
    return None


def main():
    files = java_files()
    problems = 0

    # [1] balance (comments/strings stripped first so Javadoc braces don't false-flag)
    bad = 0
    for p in files:
        e = check_balance(p, strip_code(open(p, encoding='utf-8').read()))
        if e:
            print(f'[1] FAIL {os.path.relpath(p, ROOT)}: {e}')
            bad += 1
    problems += bad
    print(f'[1] brace/string balance: {len(files) - bad}/{len(files)} files OK')

    # [2] com.crm imports resolve (nested-aware)
    def rel_key(f):
        for base in ('backend/src/main/java/', 'backend/src/test/java/'):
            idx = f.find(base)
            if idx != -1:
                return f[idx + len(base):].replace(os.sep, '.').removesuffix('.java')
        return None
    srcmap = {k: open(f, encoding='utf-8').read()
              for f in files if (k := rel_key(f))}
    total = missing = 0
    pat = re.compile(r'^import\s+(com\.crm[\w.]+);', re.M)
    for p in files:
        for m in pat.finditer(open(p, encoding='utf-8').read()):
            total += 1
            parts = m.group(1).split('.')
            ok = False
            for i in range(len(parts), 0, -1):
                top = '.'.join(parts[:i])
                if top in srcmap:
                    ok = all(re.search(r'\b(?:record|class|interface|enum)\s+' + re.escape(n) + r'\b',
                                       srcmap[top]) for n in parts[i:])
                    break
            if not ok:
                print(f'[2] FAIL unresolved import {m.group(1)} in {os.path.relpath(p, ROOT)}')
                missing += 1
    problems += missing
    print(f'[2] com.crm imports: {total - missing}/{total} resolve OK')

    # [3] JDK type usage is imported / wildcard-imported / fully qualified
    bad = 0
    for p in files:
        raw = open(p, encoding='utf-8').read()
        code = strip_code(raw)
        imports = set(re.findall(r'^import\s+(?:static\s+)?([\w.]+(?:\.\*)?);', raw, re.M))
        wildcard_pkgs = {i[:-2] for i in imports if i.endswith('.*')}
        simple = {i.split('.')[-1] for i in imports}
        fqns = set(re.findall(r'\b(?:java|jakarta)\.[\w.]*', code))
        for t, fqn in JDK_TYPES.items():
            qualified = any(tok == fqn or tok.startswith(fqn + '.') for tok in fqns)
            if re.search(r'\b' + t + r'\b', code) and t not in simple \
                    and fqn.rsplit('.', 1)[0] not in wildcard_pkgs and not qualified:
                print(f'[3] FAIL {os.path.relpath(p, ROOT)}: type {t} used but not imported/qualified')
                bad += 1
    problems += bad
    print(f'[3] JDK-type imports: {"OK" if bad == 0 else str(bad) + " problems"}')

    verdict = 'ALL STATIC CHECKS PASSED' if problems == 0 else f'{problems} PROBLEM(S)'
    print(f'RESULT: {verdict}')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
