"""Prepare a relocatable Windows OCR release from trusted local runtimes and pinned models; no downloads."""
import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def normalize_package_name(name):
    return re.sub(r"[-_.]+", "-", name).lower()


def read_pinned_requirements(lock_path):
    pinned = {}
    for line in lock_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.count("==") != 1:
            raise ValueError("INVALID_RUNTIME_DEPENDENCY_LOCK")
        name, version = line.split("==")
        key = normalize_package_name(name.strip())
        if not key or not version.strip() or key in pinned:
            raise ValueError("INVALID_RUNTIME_DEPENDENCY_LOCK")
        pinned[key] = version.strip()
    if not pinned:
        raise ValueError("INVALID_RUNTIME_DEPENDENCY_LOCK")
    return pinned


def validate_runtime_packages(installed, lock_path):
    pinned = read_pinned_requirements(lock_path)
    normalized = {normalize_package_name(name): version for name, version in installed.items()}
    if any(normalized.get(name) != version for name, version in pinned.items()):
        raise ValueError("RUNTIME_DEPENDENCY_MISMATCH")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--python-home", required=True, type=Path)
    parser.add_argument("--site-packages", required=True, type=Path)
    parser.add_argument("--models", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--zip", action="store_true")
    args = parser.parse_args()
    if args.output.exists() or (args.zip and args.output.with_suffix(".zip").exists()):
        raise ValueError("OUTPUT_ALREADY_EXISTS")
    home, packages, models = (p.resolve(strict=True) for p in (args.python_home, args.site_packages, args.models))
    output = args.output.resolve()
    if any(output.is_relative_to(source) for source in (home, packages, models)):
        raise ValueError("OUTPUT_MUST_NOT_BE_INSIDE_INPUT")
    if not (home / "python.exe").is_file() or not (packages / "rapidocr").is_dir():
        raise ValueError("INVALID_WINDOWS_RUNTIME")
    # Enforce the manifest against the actual preparation environment before copying anything.
    spec = __import__("importlib.util", fromlist=["spec_from_file_location"])
    worker_spec = spec.spec_from_file_location("worker", Path(__file__).with_name("worker.py"))
    worker = spec.module_from_spec(worker_spec)
    worker_spec.loader.exec_module(worker)
    worker.load_manifest(models)
    distributions = list(importlib.metadata.distributions(path=[str(packages)]))
    installed_versions = {d.metadata["Name"]: d.version for d in distributions}
    validate_runtime_packages(installed_versions, Path(__file__).with_name("requirements-validated.lock"))
    python = output / "python"
    python.mkdir(parents=True)
    for path in home.iterdir():
        if path.is_file() and (path.suffix.lower() in (".exe", ".dll", ".zip") or path.name == "LICENSE.txt"):
            shutil.copy2(path, python / path.name)
    ignore = shutil.ignore_patterns("site-packages", "__pycache__", "test", "tests", "idlelib", "tkinter", "ensurepip")
    for directory in ("Lib", "DLLs"):
        if (home / directory).is_dir():
            shutil.copytree(home / directory, python / directory, ignore=ignore)
    def ignore_packages(directory, names):
        ignored = {name for name in names if name in ("__pycache__", "tests", "test")}
        if Path(directory) == packages / "rapidocr":
            ignored.add("models")  # Only explicitly pinned v5 models ship; no default v6 fallback.
        return ignored
    shutil.copytree(packages, python / "Lib" / "site-packages", ignore=ignore_packages)
    shutil.copytree(models, output / "models")
    for filename in ("worker.py", "evaluate.py", "doctor.py", "requirements-validated.lock"):
        shutil.copyfile(Path(__file__).with_name(filename), output / filename)
    installed = sorted(f"{d.metadata['Name']}=={d.version}" for d in distributions)
    (output / "requirements.lock").write_text("\n".join(installed) + "\n", encoding="utf-8")
    manifest = {"schemaVersion": 1, "version": args.version, "pythonVersion": sys.version.split()[0],
                "modelManifestSha256": digest(output / "models" / "manifest.json"),
                "files": {str(path.relative_to(output)).replace('\\', '/'): digest(path)
                          for path in sorted(output.rglob("*")) if path.is_file()}}
    (output / "runtime-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    subprocess.run([str(python / "python.exe"), "-X", "utf8", "-I", str(output / "doctor.py"),
                    "--bundle", str(output), "--smoke"], check=True, timeout=120)
    if args.zip:
        shutil.make_archive(str(output), "zip", root_dir=output)
    print("OFFLINE_BUNDLE_READY")


if __name__ == "__main__":
    main()
