"""Ratchet existing direct imports without introducing a new architecture framework."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
LEGACY = {
    "backend-repo/src/main/java/com/zjcxph/imgapi/controller/PatientController.java": {"SearchMapper"},
    "backend-repo/src/main/java/com/zjcxph/imgapi/controller/ClassificationController.java": {
        "*", "ClassificationMapper", "OcrMapper", "ClassificationCatalogMapper",
    },
    # Existing API exposes a mapper's nested result type; track it without widening this PR.
    "backend-repo/src/main/java/com/zjcxph/imgapi/controller/OcrBatchController.java": {"AutomaticClassificationMapper"},
}


def violations(root: Path, legacy: dict[str, set[str]] | None = None) -> list[str]:
    legacy = LEGACY if legacy is None else legacy
    problems = []
    observed = {}
    mapper_types = {file.stem for file in (root / "backend-repo/src/main/java/com/zjcxph/imgapi/mapper").glob("*.java")}
    for file in (root / "backend-repo/src/main/java/com/zjcxph/imgapi/controller").rglob("*.java"):
        source = re.sub(r"/\*.*?\*/|//[^\n]*", "", file.read_text(encoding="utf-8"), flags=re.S)
        # Count concrete types used under wildcard imports, including fields,
        # constructor arguments and nested return types; import count is insufficient.
        edges = set(re.findall(r"\bcom\.zjcxph\.imgapi\.mapper\.([\w*]+)", source))
        if "*" in edges:
            # Match repository Mapper types, not unrelated Jackson ObjectMapper.
            edges.update(set(re.findall(r"\b[A-Z][A-Za-z0-9_]*\b", source)) & mapper_types)
        path = file.relative_to(root).as_posix()
        observed[path] = edges
        if edges - legacy.get(path, set()):
            problems.append(f"{path}: controller must use a service, not mapper imports")
    for path, allowed in legacy.items():
        if allowed - observed.get(path, set()):
            problems.append(f"{path}: stale legacy allowance; reduce/remove it")
    ui = root / "frontend-fantastic-admin/src/ui"
    frontend = root / "frontend-fantastic-admin/src"
    for file in ui.rglob("*"):
        if file.suffix not in {".ts", ".vue", ".tsx"}:
            continue
        for target in re.findall(r'''(?:from\s*|import\s*(?:\(\s*)?)["']([^"']+)["']''', file.read_text(encoding="utf-8")):
            resolved = (frontend / target[2:] if target.startswith("@/") else file.parent / target).resolve()
            if any(resolved.is_relative_to((frontend / layer).resolve()) for layer in ("api", "views", "store")):
                problems.append(f"{file.relative_to(root).as_posix()}: shared UI imports business layer")
    return problems


if __name__ == "__main__":
    problems = violations(ROOT)
    for problem in problems:
        print(problem)
    raise SystemExit(bool(problems))
