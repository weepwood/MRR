"""Build a pinned local model directory from already acquired trusted PP-OCRv5 files."""
import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser()
    for name in ("det", "cls", "rec", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("OUTPUT_ALREADY_EXISTS")
    inputs = {name: getattr(args, name).resolve(strict=True) for name in ("det", "cls", "rec")}
    if not all(path.is_file() and path.suffix == ".onnx" for path in inputs.values()):
        raise ValueError("INVALID_MODELS")
    from onnxruntime import InferenceSession, SessionOptions
    options = SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    metadata = InferenceSession(str(inputs["rec"]), sess_options=options, providers=["CPUExecutionProvider"]).get_modelmeta().custom_metadata_map
    if not metadata.get("character"):
        raise ValueError("MODEL_DICTIONARY_MISSING")
    args.output.mkdir(parents=True)
    for name, path in inputs.items():
        shutil.copyfile(path, args.output / (name + ".onnx"))
    (args.output / "keys.txt").write_text(metadata["character"], encoding="utf-8")
    manifest = {"model": "PP-OCRv5-mobile", "preprocessing": "rgb-exif-v1",
                "versions": {name: importlib.metadata.version(name) for name in ("rapidocr", "onnxruntime", "Pillow")},
                "files": {}}
    for name, filename in (("det", "det.onnx"), ("cls", "cls.onnx"), ("rec", "rec.onnx"), ("keys", "keys.txt")):
        with (args.output / filename).open("rb") as stream:
            manifest["files"][name] = {"name": filename, "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}
    content = json.dumps(manifest, ensure_ascii=False, indent=2)
    (args.output / "manifest.json").write_text(content, encoding="utf-8", newline="\n")
    print("MODEL_MANIFEST_SHA256=" + hashlib.sha256((args.output / "manifest.json").read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
