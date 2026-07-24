#!/usr/bin/env python3
"""Embed generated static sites into a Spring Boot executable JAR."""

from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path, PurePosixPath
from uuid import uuid4
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

STATIC_PREFIX = PurePosixPath("BOOT-INF/classes/static")


def _site_files(dist: Path) -> list[Path]:
    index = dist / "index.html"
    assets = dist / "assets"
    if not index.is_file():
        raise ValueError(f"Static site distribution is missing index.html: {index}")
    if not assets.is_dir() or not any(path.is_file() for path in assets.rglob("*")):
        raise ValueError(f"Static site distribution has no generated assets: {assets}")
    return sorted(path for path in dist.rglob("*") if path.is_file())


def _resolve_mount_prefix(mount: str) -> PurePosixPath:
    normalized = mount.strip().replace("\\", "/").strip("/")
    if not normalized:
        return STATIC_PREFIX

    parts = normalized.split("/")
    if any(not part or part in {".", ".."} for part in parts):
        raise ValueError(f"Invalid static site mount path: {mount}")
    return STATIC_PREFIX.joinpath(*parts)


def _is_site_entry(name: str, prefix: PurePosixPath) -> bool:
    prefix_text = f"{prefix}/"
    return name == str(prefix) or name.startswith(prefix_text)


def _copy_entry(source: ZipFile, target: ZipFile, info: ZipInfo) -> None:
    if info.is_dir():
        target.writestr(info, b"")
        return
    with source.open(info, "r") as reader:
        with target.open(info, "w", force_zip64=True) as writer:
            shutil.copyfileobj(reader, writer, length=1024 * 1024)


def embed_site(jar: Path, dist: Path, mount: str = "") -> int:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")

    files = _site_files(dist)
    mount_prefix = _resolve_mount_prefix(mount)
    temporary = jar.with_name(f".{jar.name}.static-site-{uuid4().hex}.tmp")
    try:
        with ZipFile(jar, "r") as source:
            with ZipFile(temporary, "w", allowZip64=True) as target:
                target.comment = source.comment
                for info in source.infolist():
                    if _is_site_entry(info.filename, mount_prefix):
                        continue
                    _copy_entry(source, target, info)

                for site_file in files:
                    relative = PurePosixPath(site_file.relative_to(dist).as_posix())
                    target.write(
                        site_file,
                        str(mount_prefix / relative),
                        compress_type=ZIP_DEFLATED,
                        compresslevel=9,
                    )

        os.replace(temporary, jar)
    finally:
        temporary.unlink(missing_ok=True)

    verify_site(jar, mount)
    return len(files)


def verify_site(jar: Path, mount: str = "") -> tuple[int, int]:
    if not jar.is_file():
        raise ValueError(f"Backend JAR does not exist: {jar}")

    mount_prefix = _resolve_mount_prefix(mount)
    prefix_text = f"{mount_prefix}/"
    index_entry = str(mount_prefix / "index.html")
    assets_prefix = f"{mount_prefix}/assets/"

    with ZipFile(jar) as archive:
        names = archive.namelist()
        if names.count(index_entry) != 1:
            raise ValueError(
                f"Backend JAR must contain exactly one {index_entry}; "
                f"found {names.count(index_entry)}"
            )
        site_entries = [name for name in names if name.startswith(prefix_text)]
        asset_entries = [
            name for name in site_entries
            if name.startswith(assets_prefix) and not name.endswith("/")
        ]
        if not asset_entries:
            raise ValueError(
                f"Backend JAR contains {index_entry} but no generated assets under {assets_prefix}"
            )
        duplicate_entries = {
            name for name in site_entries if site_entries.count(name) > 1
        }
        if duplicate_entries:
            raise ValueError(
                "Backend JAR contains duplicate static site entries: "
                + ", ".join(sorted(duplicate_entries)[:10])
            )
        index = archive.read(index_entry)
        if b"<html" not in index.lower():
            raise ValueError(f"Bundled {index_entry} does not look like an HTML document")
    return len(site_entries), len(asset_entries)


def embed_frontend(jar: Path, dist: Path) -> int:
    return embed_site(jar, dist)


def verify_frontend(jar: Path) -> tuple[int, int]:
    return verify_site(jar)


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    embed_parser = subparsers.add_parser("embed")
    embed_parser.add_argument("--jar", type=Path, required=True)
    embed_parser.add_argument("--dist", type=Path, required=True)
    embed_parser.add_argument(
        "--mount",
        default="",
        help="Path below BOOT-INF/classes/static, for example docs/internal",
    )

    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--jar", type=Path, required=True)
    verify_parser.add_argument(
        "--mount",
        default="",
        help="Path below BOOT-INF/classes/static, for example docs/internal",
    )

    args = parser.parse_args()
    try:
        if args.command == "embed":
            count = embed_site(args.jar.resolve(), args.dist.resolve(), args.mount)
            mount_label = f"/{args.mount.strip('/')}" if args.mount.strip("/") else "/"
            print(f"Embedded {count} static site files at {mount_label} into {args.jar}")
        else:
            static_count, asset_count = verify_site(args.jar.resolve(), args.mount)
            mount_label = f"/{args.mount.strip('/')}" if args.mount.strip("/") else "/"
            print(
                f"Verified bundled static site at {mount_label}: {static_count} entries, "
                f"{asset_count} asset entries"
            )
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
