"""Verify immutable runtime hashes and optionally recognize a generated non-patient test page offline."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time


def verify(bundle):
    bundle = bundle.resolve(strict=True)
    path = bundle / "runtime-manifest.json"
    if path.stat().st_size > 16 * 1024 * 1024:
        raise ValueError("MANIFEST_LIMIT")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1 or not manifest.get("files"):
        raise ValueError("INVALID_MANIFEST")
    for name, expected in manifest["files"].items():
        file = (bundle / name).resolve(strict=True)
        if not file.is_relative_to(bundle) or not file.is_file():
            raise ValueError("INVALID_BUNDLE_PATH")
        with file.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise ValueError("BUNDLE_CHECKSUM_MISMATCH")
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--smoke", action="store_true")
    args = parser.parse_args()
    manifest = verify(args.bundle)
    result = {"status": "VERIFIED", "version": manifest["version"], "modelManifestSha256": manifest["modelManifestSha256"]}
    if args.smoke:
        if Path(sys.prefix).resolve() != (args.bundle / "python").resolve():
            raise ValueError("RUNTIME_NOT_RELOCATABLE")
        from PIL import Image, ImageDraw
        with tempfile.TemporaryDirectory(prefix="mrr-ocr-doctor-") as directory:
            root = Path(directory)
            image = Image.new("RGB", (1000, 1400), "white")
            ImageDraw.Draw(image).text((120, 100), "SYNTHETIC OCR 123", fill="black", font_size=48)
            input_file = root / "synthetic.ocr"
            image.save(input_file, format="PNG")
            started = time.monotonic()
            process = subprocess.run([sys.executable, "-X", "utf8", "-I", str(args.bundle / "worker.py"),
                                      "--models", str(args.bundle / "models"), "--input-root", str(root), "--threads", "2"],
                                     input=json.dumps({"id": "doctor", "path": str(input_file)}) + "\n",
                                     text=True, encoding="utf-8", capture_output=True, timeout=30)
            if process.returncode != 0 or len(process.stdout) > 4 * 1024 * 1024:
                raise ValueError("OFFLINE_SMOKE_FAILED")
            response = json.loads(process.stdout)
            if response.get("error") or not response.get("lines") or response.get("engineVersion") != manifest["modelManifestSha256"]:
                raise ValueError("OFFLINE_SMOKE_FAILED")
            result["status"] = "OFFLINE_SMOKE_PASSED"
            result["elapsedSeconds"] = round(time.monotonic() - started, 3)
    print(json.dumps(result))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("OCR_RUNTIME_CHECK_FAILED: verify private configuration and pinned files", file=sys.stderr)
        sys.exit(1)
