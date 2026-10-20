"""Select full release validation from changed paths (fail closed at the workflow)."""
from pathlib import Path
import argparse


EXACT = {
    "VERSION", "release-baseline.json", "backend-repo/pom.xml",
    ".github/workflows/windows-release-package.yml",
    ".github/workflows/standalone-jar-release.yml",
    "scripts/ci_build_scope.py", "scripts/tests/test_ci_build_reuse.py",
    "scripts/release_baseline.py", "scripts/embed_frontend_in_jar.py",
    "scripts/build_standalone_jar.py", "scripts/tests/test_build_standalone_jar.py",
    "scripts/tests/test_embed_frontend_in_jar.py",
}
PREFIXES = ("backend-repo/src/main/resources/", "backend-repo/.mvn/",
            "deploy/windows/", "vitepress-doc/")


def needs_release_build(paths: list[str]) -> bool:
    for raw in paths:
        path = raw.strip().replace("\\", "/").removeprefix("./")
        if path in EXACT or path.startswith(PREFIXES):
            return True
        # Frontend build inputs include dotfiles, root configs, public and src assets.
        # Ordinary Vue/TS business changes still receive the normal quality gate.
        if path.startswith("frontend-fantastic-admin/"):
            local = path.removeprefix("frontend-fantastic-admin/")
            if not local.startswith("src/") or local.startswith("src/assets/"):
                return True
    return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--changed-files", type=Path, required=True)
    args = parser.parse_args()
    print("true" if needs_release_build(args.changed_files.read_text(encoding="utf-8").splitlines()) else "false")
