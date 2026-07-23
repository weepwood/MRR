#!/usr/bin/env python3
"""Embed a Vite distribution into a Spring Boot executable JAR."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path, PurePosixPath
from zipfile import ZIP_DEFLATED, ZipFile

STATIC_PREFIX = PurePosixPath("BOOT-INF/classes/static")
INDEX_ENTRY = str(STATIC_PREFIX / "index.html")


def _frontend_files(dist: Path) -> list[Path]:
    index = dist / "index.html"
    assets = dist / "assets"
    if not index.is_file():
        raise ValueError(f"Frontend distribution is missing index.html: {index}")
    if not assets.is_dir() or not any(path.is_file() for path in assets.rglob("*")):
        raise ValueError(f"Frontend distribution has no generated assets: {assets}")
    return sorted(path for path in dist.rglob("*") if path.is_file())


def embed_frontend(jar: Path, dist: Path) -> int:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")

    files = _frontend_files(dist)
    with ZipFile(jar, "a", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        existing = set(archive.namelist())
        embedded = [name for name in existing if name.startswith(f"{STATIC_PREFIX}/")]
        if embedded:
            raise ValueError(
                "Backend JAR already contains bundled frontend resources; "
                "rebuild the JAR before embedding again"
            )
        for source in files:
            relative = PurePosixPath(source.relative_to(dist).as_posix())
            archive.write(source, str(STATIC_PREFIX / relative))

    verify_frontend(jar)
    return len(files)


def verify_frontend(jar: Path) -> tuple[int, int]:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")
    with ZipFile(jar) as archive:
        names = archive.namelist()
        if INDEX_ENTRY not in names:
            raise ValueError(f"Backend JAR is missing {INDEX_ENTRY}")
        static_entries = [name for name in names if name.startswith(f"{STATIC_PREFIX}/")]
        asset_entries = [
            name for name in static_entries
            if name.startswith(f"{STATIC_PREFIX}/assets/") and not name.endswith("/")
        ]
        if not asset_entries:
            raise ValueError("Backend JAR contains index.html but no generated frontend assets")
        index = archive.read(INDEX_ENTRY)
        if b"<html" not in index.lower():
            raise ValueError("Bundled index.html does not look like an HTML document")
    return len(static_entries), len(asset_entries)


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    embed_parser = subparsers.add_parser("embed")
    embed_parser.add_argument("--jar", type=Path, required=True)
    embed_parser.add_argument("--dist", type=Path, required=True)

    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--jar", type=Path, required=True)

    args = parser.parse_args()
    try:
        if args.command == "embed":
            count = embed_frontend(args.jar.resolve(), args.dist.resolve())
            print(f"Embedded {count} frontend files into {args.jar}")
        else:
            static_count, asset_count = verify_frontend(args.jar.resolve())
            print(
                f"Verified bundled frontend: {static_count} static entries, "
                f"{asset_count} asset entries"
            )
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
