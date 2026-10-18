"""Read-only developer prerequisite checks; never loads application configuration."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def check_version(tool: str, output: str) -> tuple[bool, str]:
    match = re.search(r"(\d+)\.(\d+)(?:\.(\d+))?", output)
    version = tuple(int(x or 0) for x in match.groups()) if match else (0, 0, 0)
    supported = {
        "java": version[0] == 21,
        "node": (version[0] == 24 and version >= (24, 15, 0)) or version[0] >= 26,
        "pnpm": version == (10, 33, 0),
        "python": version >= (3, 10, 0),
    }
    return supported.get(tool, False), ".".join(map(str, version))


def run(command: list[str], cwd: Path | None = None) -> str:
    try:
        result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, errors="replace", timeout=20)
        return result.stdout + result.stderr if result.returncode == 0 else ""
    except (OSError, subprocess.TimeoutExpired):
        return ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=("backend", "frontend", "all"), default="all")
    parser.add_argument("--integration", action="store_true", help="Require external PostgreSQL configuration or Docker")
    args = parser.parse_args()
    commands = {"python": [os.environ.get("MRR_TEST_PYTHON", sys.executable), "--version"]}
    if args.scope != "frontend":
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
        commands["java"] = [java, "-version"]
    if args.scope != "backend":
        for tool in ("node", "pnpm"):
            commands[tool] = [shutil.which(tool) or tool, "--version"]
    valid = True
    for tool, command in commands.items():
        directory = Path(__file__).resolve().parents[1] / "frontend-fantastic-admin" if tool in ("node", "pnpm") else None
        ok, version = check_version(tool, run(command, directory))
        print(f"{'PASS' if ok else 'FAIL'} {tool}: {version if version != '0.0.0' else 'missing'}")
        valid &= ok
    if args.integration:
        external = bool(os.environ.get("MRR_TEST_POSTGRES_URL", "").strip())
        docker = bool(run(["docker", "info", "--format", "{{.ServerVersion}}"]).strip()) if not external else False
        print("PASS PostgreSQL test prerequisite" if external or docker else "FAIL configure test PostgreSQL or start Docker")
        valid &= external or docker
    print("Set MRR_TEST_PYTHON to this Python executable when running Maven OCR process tests.")
    return 0 if valid else 1


if __name__ == "__main__":
    raise SystemExit(main())
