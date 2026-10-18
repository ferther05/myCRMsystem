"""Validate installed skill metadata and local Markdown references.

Run with Python 3.10+ and PyYAML, for example:
    E:/python/python.exe -X utf8 tools/validate_skills.py
This checks discoverability structure, not the application's business behavior.
"""
from __future__ import annotations

import importlib.util
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    raise SystemExit("PyYAML is required. Install tools/requirements-skills.txt into the selected Python environment.")

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "playwright", "security-threat-model", "crm-spec-delivery",
    "springboot-mybatis-development", "vue3-elementplus-development",
    "crm-business-state", "crm-security-permissions", "crm-jobs-cache",
    "crm-verification", "crm-design-contracts", "crm-graduation-delivery",
    "crm-deployment-recovery", "crm-ui-design",
}


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    errors = []
    entries = []
    skill_root = ROOT / ".agents" / "skills"
    folders = sorted(p for p in skill_root.iterdir() if p.is_dir())
    names = {p.name for p in folders}
    for missing in sorted(EXPECTED - names):
        errors.append(f"Missing expected skill: {missing}")

    validator_file = Path.home() / ".codex" / "skills" / ".system" / "skill-creator" / "scripts" / "quick_validate.py"
    official = None
    if validator_file.is_file():
        spec = importlib.util.spec_from_file_location("codex_skill_validator", validator_file)
        official = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(official)

    for folder in folders:
        entry = folder / "SKILL.md"
        if not entry.is_file():
            errors.append(f"Missing SKILL.md: {folder.name}")
            continue
        content = entry.read_text(encoding="utf-8")
        match = re.match(r"\A---\r?\n(.*?)\r?\n---(?:\r?\n|$)", content, re.S)
        if not match:
            errors.append(f"Invalid frontmatter: {folder.name}")
            continue
        meta = yaml.safe_load(match.group(1))
        if not isinstance(meta, dict):
            errors.append(f"Frontmatter is not a mapping: {folder.name}")
            continue
        name, description = meta.get("name"), meta.get("description")
        if name != folder.name or not isinstance(name, str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", name) or len(name) > 64:
            errors.append(f"Invalid or mismatched name: {folder.name}")
        if not isinstance(description, str) or not description.strip() or len(description) > 1024:
            errors.append(f"Invalid description: {folder.name}")
        if len(content.splitlines()) >= 500:
            errors.append(f"Entrypoint too long: {folder.name}")
        if official:
            valid, message = official.validate_skill(folder)
            if not valid:
                errors.append(f"Official validator: {folder.name}: {message}")
        entries.append({"name": name, "lines": len(content.splitlines()), "path": entry.relative_to(ROOT).as_posix()})

    markdown_files = sorted((ROOT / "docs").rglob("*.md")) + [ROOT / "AGENTS.md"]
    markdown_files += sorted(skill_root.rglob("*.md"))
    link_count = 0
    for file in markdown_files:
        text = file.read_text(encoding="utf-8")
        # Fenced examples may show paths for other operating systems; skip them.
        text = re.sub(r"```[^\n]*\n.*?```", "", text, flags=re.S)
        for target in re.findall(r"\[[^\]\n]*\]\(([^)\n]+)\)", text):
            target = target.strip().split("#", 1)[0]
            if not target or re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target):
                continue
            link_count += 1
            if not (file.parent / target).resolve().exists():
                errors.append(f"Broken link: {file.relative_to(ROOT)} -> {target}")

    result = {
        "purpose": "Skill structure, metadata and local reference validation; not CRM business tests",
        "official_validator_used": bool(official),
        "skill_count": len(entries), "checked_local_links": link_count,
        "skills": entries, "errors": errors, "passed": not errors,
    }
    (ROOT / "docs" / "skills-validation.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
