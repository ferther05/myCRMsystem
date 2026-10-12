---
name: "playwright"
description: "Use when the task requires automating a real browser from the terminal (navigation, form filling, snapshots, screenshots, data extraction, UI-flow debugging) via `playwright-cli` or the bundled wrapper script."
---


# Playwright CLI Skill

## CRM project on Windows

This is the upstream browser workflow with a local Windows entrypoint. In PowerShell, check `Get-Command npx.cmd` and prefer:

```powershell
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --help
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 open <actual-local-app-url>
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 snapshot
```

The wrapper pins Playwright CLI 0.1.22 and forwards arguments without shell interpolation. Read `docs/environment.md` for local prerequisites. The Bash commands below apply only in a Bash shell and use the project-installed path. Prefer bundled dependency runtimes for browser automation when the host provides them; this Windows wrapper resolves npx.cmd from the configured Node/npm installation.


Drive a real browser from the terminal using `playwright-cli`. Prefer the bundled wrapper script so the CLI works even when it is not globally installed.
Treat this skill as CLI-first automation. Do not pivot to `@playwright/test` unless the user explicitly asks for test files.

## Prerequisite check (required)

Before proposing commands, check whether `npx` is available (the wrapper depends on it):

```bash
command -v npx >/dev/null 2>&1
```

If it is not available, pause and ask the user to install Node.js/npm (which provides `npx`). Provide these steps verbatim:

```bash
# Verify Node/npm are installed
node --version
npm --version

# If missing, install Node.js/npm, then:
npm install -g @playwright/cli@latest
playwright-cli --help
```

Once `npx` is present, proceed with the wrapper script. A global install of `playwright-cli` is optional.

## Skill path (set once)

```bash
CRM_PWCLI="$(pwd)/.agents/skills/playwright/scripts/playwright_cli.sh"
```

This project installs the skill under `.agents/skills/playwright`; run commands from the project root. Do not change CODEX_HOME.

## Quick start

Use the wrapper script:

```bash
"$CRM_PWCLI" open https://playwright.dev --headed
"$CRM_PWCLI" snapshot
"$CRM_PWCLI" click e15
"$CRM_PWCLI" type "Playwright"
"$CRM_PWCLI" press Enter
"$CRM_PWCLI" screenshot
```

If the user prefers a global install, this is also valid:

```bash
npm install -g @playwright/cli@latest
playwright-cli --help
```

## Core workflow

1. Open the page.
2. Snapshot to get stable element refs.
3. Interact using refs from the latest snapshot.
4. Re-snapshot after navigation or significant DOM changes.
5. Capture artifacts (screenshot, pdf, traces) when useful.

Minimal loop:

```bash
"$CRM_PWCLI" open https://example.com
"$CRM_PWCLI" snapshot
"$CRM_PWCLI" click e3
"$CRM_PWCLI" snapshot
```

## When to snapshot again

Snapshot again after:

- navigation
- clicking elements that change the UI substantially
- opening/closing modals or menus
- tab switches

Refs can go stale. When a command fails due to a missing ref, snapshot again.

## Recommended patterns

### Form fill and submit

```bash
"$CRM_PWCLI" open https://example.com/form
"$CRM_PWCLI" snapshot
"$CRM_PWCLI" fill e1 "user@example.com"
"$CRM_PWCLI" fill e2 "password123"
"$CRM_PWCLI" click e3
"$CRM_PWCLI" snapshot
```

### Debug a UI flow with traces

```bash
"$CRM_PWCLI" open https://example.com --headed
"$CRM_PWCLI" tracing-start
# ...interactions...
"$CRM_PWCLI" tracing-stop
```

### Multi-tab work

```bash
"$CRM_PWCLI" tab-new https://example.com
"$CRM_PWCLI" tab-list
"$CRM_PWCLI" tab-select 0
"$CRM_PWCLI" snapshot
```

## Wrapper script

The wrapper script uses `npx --package @playwright/cli@0.1.22 playwright-cli` so the CLI can run without a global install:

```bash
"$CRM_PWCLI" --help
```

Prefer the wrapper unless the repository already standardizes on a global install.

## References

Open only what you need:

- CLI command reference: `references/cli.md`
- Practical workflows and troubleshooting: `references/workflows.md`

## Guardrails

- Always snapshot before referencing element ids like `e12`.
- Re-snapshot when refs seem stale.
- Prefer explicit commands over `eval` and `run-code` unless needed.
- When you do not have a fresh snapshot, use placeholder refs like `eX` and say why; do not bypass refs with `run-code`.
- Use `--headed` when a visual check will help.
- When capturing artifacts in this repo, use `output/playwright/` and avoid introducing new top-level artifact folders.
- Default to CLI commands and workflows, not Playwright test specs.
