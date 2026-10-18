"""Regenerate the full API document from the common preamble and per-feature documents."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'docs/api'
target = root / '00-全项目接口说明与请求示例.md'
text = target.read_text(encoding='utf-8-sig')
preamble = text.split('\n## BASE', 1)[0].rstrip()
parts = [preamble]
for source in sorted(root.glob('[0-9][0-9]-*.md')):
    if source == target:
        continue
    result = []
    in_code = False
    for line in source.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('```'):
            in_code = not in_code
        if not in_code and line.startswith('#'):
            line = '#' + line
        result.append(line)
    parts.append('\n'.join(result))
target.write_text('\n\n'.join(parts).rstrip() + '\n', encoding='utf-8')
print('API summary synchronized from feature documents')
