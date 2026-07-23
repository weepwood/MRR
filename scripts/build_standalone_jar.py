#!/usr/bin/env python3
"""Build and verify a directly runnable MRR standalone JAR."""

from __future__ import annotations

import argparse
import os
import shutil
import sys
from collections import Counter
from pathlib import Path
from uuid import uuid4
from zipfile import ZipFile, ZipInfo

APPLICATION_PROPERTIES = "BOOT-INF/classes/application.properties"
FRONTEND_INDEX = "BOOT-INF/classes/static/index.html"
FRONTEND_ASSET_PREFIX = "BOOT-INF/classes/static/assets/"
PORT_PROPERTY_PREFIX = "server.port=${SERVER_PORT:"


def _copy_entry(
    source: ZipFile,
    target: ZipFile,
    info: ZipInfo,
    data: bytes | None = None,
) -> None:
    if info.is_dir():
        target.writestr(info, b"")
        return
    if data is not None:
        target.writestr(info, data)
        return
    with source.open(info, "r") as reader:
        with target.open(info, "w", force_zip64=True) as writer:
            shutil.copyfileobj(reader, writer, length=1024 * 1024)


def _duplicate_entries(names: list[str]) -> set[str]:
    return {name for name, count in Counter(names).items() if count > 1}


def _replace_default_port(properties: str, default_port: int) -> str:
    lines = properties.splitlines(keepends=True)
    matches = [
        index
        for index, line in enumerate(lines)
        if line.startswith(PORT_PROPERTY_PREFIX)
    ]
    if len(matches) != 1:
        raise ValueError(
            "application.properties must contain exactly one configurable "
            f"server.port entry; found {len(matches)}"
        )
    index = matches[0]
    if lines[index].endswith("\r\n"):
        line_ending = "\r\n"
    elif lines[index].endswith("\n"):
        line_ending = "\n"
    else:
        line_ending = ""
    lines[index] = f"server.port=${{SERVER_PORT:{default_port}}}{line_ending}"
    return "".join(lines)


def build_standalone_jar(
    source_jar: Path,
    output_jar: Path,
    default_port: int = 8002,
) -> None:
    if not source_jar.is_file():
        raise ValueError(f"Source JAR does not exist: {source_jar}")
    if not 1 <= default_port <= 65535:
        raise ValueError(f"Default port is outside the valid range: {default_port}")
    if source_jar.resolve() == output_jar.resolve():
        raise ValueError("Standalone output must be different from the source JAR")

    output_jar.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_jar.with_name(f".{output_jar.name}.{uuid4().hex}.tmp")
    try:
        with ZipFile(source_jar, "r") as source:
            names = source.namelist()
            duplicates = _duplicate_entries(names)
            if duplicates:
                raise ValueError(
                    "Source JAR contains duplicate entries: "
                    + ", ".join(sorted(duplicates)[:10])
                )
            if names.count(APPLICATION_PROPERTIES) != 1:
                raise ValueError(
                    f"Source JAR must contain exactly one {APPLICATION_PROPERTIES}"
                )

            properties = source.read(APPLICATION_PROPERTIES).decode("utf-8")
            updated_properties = _replace_default_port(
                properties,
                default_port,
            ).encode("utf-8")

            with ZipFile(temporary, "w", allowZip64=True) as target:
                target.comment = source.comment
                for info in source.infolist():
                    replacement = (
                        updated_properties
                        if info.filename == APPLICATION_PROPERTIES
                        else None
                    )
                    _copy_entry(source, target, info, replacement)

        os.replace(temporary, output_jar)
    finally:
        temporary.unlink(missing_ok=True)

    verify_standalone_jar(output_jar, default_port)


def verify_standalone_jar(
    jar: Path,
    default_port: int = 8002,
) -> tuple[int, int]:
    if not jar.is_file():
        raise ValueError(f"Standalone JAR does not exist: {jar}")
    if not 1 <= default_port <= 65535:
        raise ValueError(f"Default port is outside the valid range: {default_port}")

    with ZipFile(jar, "r") as archive:
        names = archive.namelist()
        duplicates = _duplicate_entries(names)
        if duplicates:
            raise ValueError(
                "Standalone JAR contains duplicate entries: "
                + ", ".join(sorted(duplicates)[:10])
            )
        if names.count(APPLICATION_PROPERTIES) != 1:
            raise ValueError(
                f"Standalone JAR must contain exactly one {APPLICATION_PROPERTIES}"
            )
        properties = archive.read(APPLICATION_PROPERTIES).decode("utf-8")
        expected = f"server.port=${{SERVER_PORT:{default_port}}}"
        matching_lines = [
            line
            for line in properties.splitlines()
            if line.startswith(PORT_PROPERTY_PREFIX)
        ]
        if matching_lines != [expected]:
            raise ValueError(
                f"Standalone JAR default port must be {default_port}; "
                f"found {matching_lines or 'none'}"
            )
        if names.count(FRONTEND_INDEX) != 1:
            raise ValueError(
                f"Standalone JAR must contain exactly one {FRONTEND_INDEX}"
            )
        asset_entries = [
            name
            for name in names
            if name.startswith(FRONTEND_ASSET_PREFIX) and not name.endswith("/")
        ]
        if not asset_entries:
            raise ValueError("Standalone JAR has no embedded frontend assets")
        index = archive.read(FRONTEND_INDEX)
        if b"<html" not in index.lower():
            raise ValueError("Embedded frontend index does not look like HTML")

    return len(names), len(asset_entries)


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build")
    build_parser.add_argument("--source", type=Path, required=True)
    build_parser.add_argument("--output", type=Path, required=True)
    build_parser.add_argument("--default-port", type=int, default=8002)

    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--jar", type=Path, required=True)
    verify_parser.add_argument("--default-port", type=int, default=8002)

    args = parser.parse_args()
    try:
        if args.command == "build":
            build_standalone_jar(
                args.source.resolve(),
                args.output.resolve(),
                args.default_port,
            )
            print(
                f"Built standalone JAR: {args.output} "
                f"(default port {args.default_port})"
            )
        else:
            entry_count, asset_count = verify_standalone_jar(
                args.jar.resolve(),
                args.default_port,
            )
            print(
                f"Verified standalone JAR: {entry_count} entries, "
                f"{asset_count} frontend assets, default port {args.default_port}"
            )
    except (OSError, UnicodeError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
