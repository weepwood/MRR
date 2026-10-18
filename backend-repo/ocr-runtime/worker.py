"""Offline JSONL OCR worker. No database access, HTTP listener, or remote inputs."""
import argparse
import contextlib
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import sys


def load_manifest(directory):
    directory = Path(directory).resolve(strict=True)
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("model") != "PP-OCRv5-mobile" or manifest.get("preprocessing") != "rgb-exif-v1":
        raise ValueError("MODEL_INVALID")
    paths = {}
    for kind in ("det", "cls", "rec", "keys"):
        entry = manifest["files"][kind]
        path = (directory / entry["name"]).resolve(strict=True)
        if not path.is_relative_to(directory) or not path.is_file():
            raise ValueError("MODEL_INVALID")
        with path.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != entry["sha256"]:
            raise ValueError("MODEL_INVALID")
        paths[kind] = str(path)
    for package in ("rapidocr", "onnxruntime", "Pillow"):
        if importlib.metadata.version(package) != manifest["versions"][package]:
            raise ValueError("MODEL_INVALID")
    return paths, hashlib.sha256((directory / "manifest.json").read_bytes()).hexdigest()


def input_path(raw, root):
    path = Path(raw).resolve(strict=True)
    if not path.is_relative_to(root) or not path.is_file() or path.suffix != ".ocr":
        raise ValueError("INVALID_IMAGE")
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models", required=True)
    parser.add_argument("--input-root", required=True)
    parser.add_argument("--threads", type=int, choices=(1, 2), default=2)
    args = parser.parse_args()
    root = Path(args.input_root).resolve(strict=True)
    # Disallow runtime HTTP(S), including accidental upstream model downloads.
    import socket
    def deny_network(*unused, **kwargs):
        raise OSError("NETWORK_DISABLED")
    socket.socket.connect = deny_network
    socket.create_connection = deny_network
    with contextlib.redirect_stdout(sys.stderr):
        paths, fingerprint = load_manifest(args.models)
        from PIL import Image, ImageOps
        import numpy as np
        from rapidocr import EngineType, ModelType, OCRVersion, RapidOCR
        Image.MAX_IMAGE_PIXELS = 40_000_000
        engine = RapidOCR(params={
            "Det.engine_type": EngineType.ONNXRUNTIME,
            "Rec.engine_type": EngineType.ONNXRUNTIME,
            "Cls.engine_type": EngineType.ONNXRUNTIME,
            "Det.ocr_version": OCRVersion.PPOCRV5,
            "Rec.ocr_version": OCRVersion.PPOCRV5,
            "Det.model_type": ModelType.MOBILE,
            "Rec.model_type": ModelType.MOBILE,
            "Det.model_path": paths["det"], "Cls.model_path": paths["cls"],
            "Rec.model_path": paths["rec"], "Rec.rec_keys_path": paths["keys"],
            "EngineConfig.onnxruntime.intra_op_num_threads": args.threads,
            "EngineConfig.onnxruntime.inter_op_num_threads": 1,
        })
    for raw in sys.stdin.buffer:
        identity = ""
        try:
            if len(raw) > 16384:
                raise ValueError("INVALID_IMAGE")
            request = json.loads(raw)
            identity = request["id"]
            path = input_path(request["path"], root)
            with contextlib.redirect_stdout(sys.stderr), Image.open(path) as image:
                if image.width * image.height > 40_000_000:
                    raise ValueError("PIXEL_LIMIT")
                if getattr(image, "n_frames", 1) != 1 or image.format not in ("JPEG", "PNG", "TIFF", "BMP"):
                    raise ValueError("INVALID_IMAGE")
                rgb = ImageOps.exif_transpose(image).convert("RGB")
                result = engine(np.asarray(rgb))
                lines = []
                if result.boxes is not None:
                    for box, text, score in zip(result.boxes, result.txts, result.scores):
                        points = np.asarray(box)
                        lines.append({"text": text, "confidence": float(score), "box": [
                            max(0., float(points[:, 0].min())), max(0., float(points[:, 1].min())),
                            min(float(rgb.width), float(points[:, 0].max())),
                            min(float(rgb.height), float(points[:, 1].max()))]})
                lines.sort(key=lambda line: (line["box"][1], line["box"][0]))
                response = {"id": identity, "width": rgb.width, "height": rgb.height,
                            "lines": lines, "engineVersion": fingerprint}
        except Exception as failure:
            code = str(failure)
            response = {"id": identity, "error": code if code in ("PIXEL_LIMIT", "INVALID_IMAGE") else "INVALID_IMAGE"}
        sys.stdout.write(json.dumps(response, ensure_ascii=False, allow_nan=False) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Do not disclose model paths, package internals, or document content.
        sys.stdout.write('{"error":"MODEL_INVALID"}\n')
        sys.stdout.flush()
        sys.exit(1)
