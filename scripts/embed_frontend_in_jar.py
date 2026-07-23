#!/usr/bin/env python3
"""Embed a Vite distribution into a Spring Boot executable JAR."""

from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path, PurePosixPath
from uuid import uuid4
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

STATIC_PREFIX = PurePosixPath("BOOT-INF/classes/static")
STATIC_PREFIX_TEXT = f"{STATIC_PREFIX}/"
INDEX_ENTRY = str(STATIC_PREFIX / "index.html")


def _frontend_files(dist: Path) -> list[Path]:
    index = dist / "index.html"
    assets = dist / "assets"
    if not index.is_file():
        raise ValueError(f"Frontend distribution is missing index.html: {index}")
    if not assets.is_dir() or not any(path.is_file() for path in assets.rglob("*")):
        raise ValueError(f"Frontend distribution has no generated assets: {assets}")
    return sorted(path for path in dist.rglob("*") if path.is_file())


def _is_static_entry(name: str) -> bool:
    return name == str(STATIC_PREFIX) or name.startswith(STATIC_PREFIX_TEXT)


def _copy_entry(source: ZipFile, target: ZipFile, info: ZipInfo) -> None:
    if info.is_dir():
        target.writestr(info, b"")
        return
    with source.open(info, "r") as reader:
        with target.open(info, "w", force_zip64=True) as writer:
            shutil.copyfileobj(reader, writer, length=1024 * 1024)


def embed_frontend(jar: Path, dist: Path) -> int:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")

    files = _frontend_files(dist)
    temporary = jar.with_name(f".{jar.name}.frontend-{uuid4().hex}.tmp")
    try:
        with ZipFile(jar, "r") as source:
            with ZipFile(temporary, "w", allowZip64=True) as target:
                target.comment = source.comment
                for info in source.infolist():
                    if _is_static_entry(info.filename):
                        continue
                    _copy_entry(source, target, info)

                for frontend_file in files:
                    relative = PurePosixPath(frontend_file.relative_to(dist).as_posix())
                    target.write(
                        frontend_file,
                        str(STATIC_PREFIX / relative),
                        compress_type=ZIP_DEFLATED,
                        compresslevel=9,
                    )

        os.replace(temporary, jar)
    finally:
        temporary.unlink(missing_ok=True)

    verify_frontend(jar)
    return len(files)


def verify_frontend(jar: Path) -> tuple[int, int]:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")
    with ZipFile(jar) as archive:
        names = archive.namelist()
        if names.count(INDEX_ENTRY) != 1:
            raise ValueError(
                f"Backend JAR must contain exactly one {INDEX_ENTRY}; "
                f"found {names.count(INDEX_ENTRY)}"
            )
        static_entries = [name for name in names if name.startswith(STATIC_PREFIX_TEXT)]
        asset_entries = [
            name for name in static_entries
            if name.startswith(f"{STATIC_PREFIX}/assets/") and not name.endswith("/")
        ]
        if not asset_entries:
            raise ValueError("Backend JAR contains index.html but no generated frontend assets")
        duplicate_entries = {
            name for name in static_entries if static_entries.count(name) > 1
        }
        if duplicate_entries:
            raise ValueError(
                "Backend JAR contains duplicate frontend entries: "
                + ", ".join(sorted(duplicate_entries)[:10])
            )
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
