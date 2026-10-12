$ErrorActionPreference = 'Stop'

# Keep native arguments intact, including CLI options and element references.
$crmNpx = Get-Command npx.cmd -ErrorAction SilentlyContinue
if (-not $crmNpx) {
    throw 'Node.js/npm must provide npx.cmd on PATH before using Playwright CLI.'
}

$crmCliArguments = @('--yes', '--package', '@playwright/cli@0.1.22', 'playwright-cli')
$crmHasSession = @($args | Where-Object { $_ -eq '--session' -or $_ -like '--session=*' }).Count -gt 0
if (-not $crmHasSession -and $env:PLAYWRIGHT_CLI_SESSION) {
    $crmCliArguments += @('--session', $env:PLAYWRIGHT_CLI_SESSION)
}
$crmCliArguments += $args

& $crmNpx.Source @crmCliArguments
exit $LASTEXITCODE
