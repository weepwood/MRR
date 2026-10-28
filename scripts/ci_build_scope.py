"""Select full release validation from changed paths (fail closed at the workflow)."""
from pathlib import Path
import argparse


EXACT = {
    "release-baseline.json",
    ".github/workflows/quality-gate.yml",
    ".github/workflows/release-on-version-bump.yml",
    ".github/workflows/release-validation.yml",
    ".github/workflows/windows-release-package.yml",
    ".github/workflows/standalone-jar-release.yml",
    "scripts/ci_build_scope.py", "scripts/tests/test_ci_build_reuse.py",
    "scripts/release_baseline.py", "scripts/embed_frontend_in_jar.py",
    "scripts/build_standalone_jar.py", "scripts/tests/test_build_standalone_jar.py",
    "scripts/tests/test_embed_frontend_in_jar.py",
    "scripts/ci_gate.py", "scripts/release_candidate.py",
    "scripts/tests/test_ci_gate.py", "scripts/tests/test_release_candidate.py",
}


def needs_release_build(paths: list[str]) -> bool:
    for raw in paths:
        path = raw.strip().replace("\\", "/").removeprefix("./")
        if path in EXACT:
            return True
        # Application/configuration and deployment changes use their scoped checks.
        # Only release machinery itself requires an additional complete candidate.
    return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--changed-files", type=Path, required=True)
    args = parser.parse_args()
    print("true" if needs_release_build(args.changed_files.read_text(encoding="utf-8").splitlines()) else "false")
