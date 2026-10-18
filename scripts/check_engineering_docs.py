"""Check maintained onboarding instructions against repository source-of-truth files."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET


def validate(root: Path) -> list[str]:
    problems: list[str] = []
    version = (root / "VERSION").read_text(encoding="utf-8").strip()
    readme = (root / "README.md").read_text(encoding="utf-8")
    package = root / "frontend-fantastic-admin/package.json"
    if package.exists():
        node = json.loads(package.read_text(encoding="utf-8"))["engines"]["node"]
        if node not in readme:
            problems.append("README.md: Node requirement differs from package.json")
    for value in re.findall(r"当前值为 \*\*([^*]+)\*\*", readme):
        if value != version:
            problems.append("README.md: product version differs from VERSION")
    pom = root / "backend-repo/pom.xml"
    if pom.exists():
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        boot = ET.parse(pom).findtext("m:parent/m:version", namespaces=ns)
        for value in re.findall(r"Spring Boot ([\d.]+)", readme):
            if value != boot:
                problems.append("README.md: Spring Boot version differs from pom.xml")
    for relative in ("frontend-fantastic-admin/ENGINEERING.md", "backend-repo/ENGINEERING_GUIDE.md"):
        path = root / relative
        if path.exists() and re.search(r"git (?:checkout|switch) (?:dev|develop)\b", path.read_text(encoding="utf-8")):
            problems.append(f"{relative}: development must start from main")
    return problems


if __name__ == "__main__":
    findings = validate(Path(__file__).resolve().parents[1])
    print("\n".join(findings) if findings else "Engineering documentation consistent.")
    raise SystemExit(bool(findings))
