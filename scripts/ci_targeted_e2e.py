"""Select changed mock Playwright specs, without running the full browser suite."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

if __package__:
    from scripts.frontend_test_scope import classify_paths as frontend_scope
else:
    from frontend_test_scope import classify_paths as frontend_scope

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = "frontend-fantastic-admin/"
SPEC = re.compile(r"e2e/[A-Za-z0-9_][A-Za-z0-9_-]*\.spec\.ts")
RELATED = {
    "e2e/monitoring-refresh-status.spec.ts": ["e2e/monitoring-polling-performance.spec.ts"],
}


def select_specs(paths):
    specs = {
        path[len(FRONTEND):]
        for path in paths
        if path.startswith(FRONTEND) and SPEC.fullmatch(path[len(FRONTEND):])
    }
    for spec in tuple(specs):
        specs.update(RELATED.get(spec, []))
    return sorted(specs)


# Known domain entrypoints. Shared infrastructure or unknown source changes
# conservatively select the full mock suite rather than silently omitting tests.
DOMAIN_SPECS = {
    "src/views/patients/": ("list-workspace-ui", "patient-id-card"),
    "src/views/archive-boxes/": ("list-workspace-ui",),
    "src/views/settings/": ("settings-layout", "settings-section-width", "settings-load-failure",
                            "settings-workspace-polish", "runtime-settings-safety", "appearance-section-layout"),
    "src/views/statistics/": ("statistics-query-results", "statistics-year-shortcut", "query-page-consistency"),
    "src/assets/styles/": ("business-visual-audit", "global-visual", "form-control-parity",
                           "settings-section-width", "table-column-widths", "button-parity"),
}


def select_plan(paths, *, critical=False):
    paths = list(paths)
    specs = set(select_specs(paths))
    # Changes to selection logic must validate the whole suite, even if a
    # changed spec also supplies a narrow selection.
    full = any(path in {
        "scripts/ci_targeted_e2e.py",
        "scripts/frontend_test_scope.py",
        ".github/workflows/quality-gate.yml",
    } for path in paths)
    for path in paths:
        if not path.startswith(FRONTEND) or path.lower().endswith('.md'):
            continue
        relative = path[len(FRONTEND):]
        if SPEC.fullmatch(relative):
            continue
        if relative.startswith('e2e/'):
            # A static Pages-only smoke script is a separate artifact check.
            if relative != 'e2e/smoke-pages-demo.mjs':
                full = True
            continue
        if relative.startswith('e2e-live/'):
            continue
        if frontend_scope([path]).e2e_changed:
            full = True
            continue
        for prefix, names in DOMAIN_SPECS.items():
            if relative.startswith(prefix):
                specs.update(f'e2e/{name}.spec.ts' for name in names)
                break
        else:
            full = True
    if critical and not specs:
        full = True
    for spec in tuple(specs):
        specs.update(RELATED.get(spec, []))
    return full, sorted(specs)


def playwright_args(specs):
    if not isinstance(specs, list) or not specs or not all(
        isinstance(spec, str) and SPEC.fullmatch(spec) for spec in specs
    ):
        raise ValueError("Expected a nonempty list of mock spec paths")
    return ["pnpm", "exec", "playwright", "test", "--project=chromium", "--workers=2",
            *[re.escape(spec) + "$" for spec in specs]]


def main():
    parser = argparse.ArgumentParser()
    subs = parser.add_subparsers(dest="command", required=True)
    select = subs.add_parser("select")
    select.add_argument("--base", required=True)
    select.add_argument("--head", required=True)
    select.add_argument("--output", required=True)
    subs.add_parser("run")
    args = parser.parse_args()
    if args.command == "select":
        for sha in (args.base, args.head):
            if not re.fullmatch(r"[a-f0-9]{40}", sha):
                raise ValueError("Expected full commit SHA")
        result = subprocess.check_output(
            ["git", "diff", "--name-only", "-z", "--no-renames", "--diff-filter=AM",
             f"{args.base}...{args.head}", "--", FRONTEND + "e2e/"], cwd=ROOT
        )
        specs = select_specs(result.decode("utf-8").split("\0"))
        with Path(args.output).open("a", encoding="utf-8") as output:
            output.write("specs=" + json.dumps(specs, separators=(",", ":")) + "\n")
    else:
        specs = json.loads(os.environ["TARGETED_E2E_SPECS"])
        cmd = playwright_args(specs)
        print("Selected browser specs:", ", ".join(specs), flush=True)
        subprocess.run(cmd, cwd=ROOT / FRONTEND, check=True)


if __name__ == "__main__":
    main()
