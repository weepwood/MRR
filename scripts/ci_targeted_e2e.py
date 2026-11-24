"""Select changed mock Playwright specs, without running the full browser suite."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

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
