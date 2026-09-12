#!/usr/bin/env python3
"""
shape-audit.py - frontend/backend response-shape contract audit.

Finds the class of bug where the frontend types an api.get<> call as PageResponse<>
but the backend endpoint returns a bare List (or anything else). That mismatch made
`templates?.content.map(...)` crash the EmailComposer and CampaignsPage with
"Cannot read properties of undefined (reading 'map')".

Run anywhere Python 3 runs:

    python scripts/shape-audit.py

Exit 0 = no mismatches, 1 = mismatches listed.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def norm(p):
    # normalize JS template literals (/${id}/) and Spring patterns (/{id}/) to {}
    p = re.sub(r'\$\{[^}]+\}', '{}', p)
    return re.sub(r'\{[^}]+\}', '{}', p)


def backend_get_kinds():
    kinds = {}
    base_root = os.path.join(ROOT, 'backend', 'src', 'main', 'java')
    for dp, _, fns in os.walk(base_root):
        for fn in fns:
            if not fn.endswith('Controller.java'):
                continue
            s = open(os.path.join(dp, fn), encoding='utf-8').read()
            bm = re.search(r'@RequestMapping\s*\(\s*"([^"]+)"', s)
            base = (bm.group(1) if bm else '').replace('/api/v1', '').rstrip('/')
            for m in re.finditer(r'@GetMapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?', s):
                sub = (m.group(1) or '').rstrip('/')
                full = re.sub(r'/+', '/', base + sub) or '/'
                seg = s[m.end():m.end() + 300]
                pm = re.search(r'public\s+([\w<>,\.\s]+?)\s+\w+\s*\(', seg)
                if pm:
                    rt = re.sub(r'\s+', ' ', pm.group(1).strip())
                    # SSE endpoints (SseEmitter) are not JSON lists; skip them
                    if 'SseEmitter' in rt:
                        continue
                    kinds[norm(full)] = rt
    return kinds


def frontend_page_typed_calls():
    out = []
    fe_root = os.path.join(ROOT, 'frontend', 'src')
    for dp, _, fns in os.walk(fe_root):
        for fn in fns:
            if not fn.endswith(('.ts', '.tsx')):
                continue
            p = os.path.join(dp, fn)
            s = open(p, encoding='utf-8').read()
            for m in re.finditer(r"api\.get<PageResponse<[^>]+>>\(\s*['\"`]([^'\"`]+)['\"`]", s):
                url = norm(m.group(1).split('?')[0].rstrip('/'))
                out.append((os.path.relpath(p, ROOT), url))
    return sorted(set(out))


def main():
    be = backend_get_kinds()
    problems = 0
    for file, url in frontend_page_typed_calls():
        rt = be.get(url)
        if rt is None:
            # endpoint with path variables may be normalized differently; only report if no candidate exists
            print(f'MISMATCH {file}: GET {url} typed PageResponse but NO such backend GET endpoint exists')
            problems += 1
        elif 'PageResponse' not in rt:
            print(f'MISMATCH {file}: GET {url} typed PageResponse but backend returns {rt}')
            problems += 1
    print(f'RESULT: {"NO SHAPE MISMATCHES (" + str(len(be)) + " GET endpoints audited)" if problems == 0 else f"{problems} MISMATCH(ES)"}')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
