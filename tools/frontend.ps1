[CmdletBinding()]
param(
    [ValidateSet('Install', 'Build', 'Test', 'Run', 'Start', 'Stop', 'Status')]
    [string]$Action = 'Status'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'frontend'
$frontendOutput = Join-Path $projectRoot 'output/frontend'
$frontendState = Join-Path $frontendOutput 'process.json'
$frontendUrl = 'http://127.0.0.1:5173'
$node = (Get-Command node.exe -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$vite = Join-Path $frontendRoot 'node_modules/vite/bin/vite.js'

function Invoke-FrontendNpm([string[]]$NpmArguments) {
    Push-Location $frontendRoot
    try {
        & $npm @NpmArguments
        if ($LASTEXITCODE -ne 0) { throw "npm failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
}

function Get-ManagedFrontendProcess {
    if (-not (Test-Path -LiteralPath $frontendState)) { return $null }
    $state = Get-Content -Raw -LiteralPath $frontendState | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.ProcessName -ne 'node' -or $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.startTicks) { return $null }
    return $process
}

switch ($Action) {
    'Install' { Invoke-FrontendNpm @('ci', '--registry=https://registry.npmjs.org', '--no-fund') }
    'Build' { Invoke-FrontendNpm @('run', 'build') }
    'Test' { Invoke-FrontendNpm @('test') }
    'Run' { Invoke-FrontendNpm @('run', 'dev') }
    'Start' {
        if (Get-ManagedFrontendProcess) { Write-Output 'Frontend is already running. Use Status or Stop.'; return }
        if (-not (Test-Path -LiteralPath $vite)) { throw 'Install first: tools/frontend.ps1 -Action Install' }
        $portCheck = [System.Net.Sockets.TcpClient]::new()
        try {
            $connection = $portCheck.ConnectAsync('127.0.0.1', 5173)
            try { $inUse = $connection.Wait(1000) -and $portCheck.Connected } catch { $inUse = $false }
        } finally { $portCheck.Dispose() }
        if ($inUse) { throw 'Port 5173 is already in use; no existing process was stopped.' }
        New-Item -ItemType Directory -Path $frontendOutput -Force | Out-Null
        $process = Start-Process -FilePath $node -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru `
            -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort') `
            -RedirectStandardOutput (Join-Path $frontendOutput 'stdout.log') `
            -RedirectStandardError (Join-Path $frontendOutput 'stderr.log')
        @{ processId=$process.Id; startTicks=$process.StartTime.ToUniversalTime().Ticks.ToString() } |
            ConvertTo-Json | Set-Content -LiteralPath $frontendState -Encoding UTF8
        $deadline = [DateTime]::UtcNow.AddSeconds(25)
        do {
            if ($process.HasExited) { throw 'Frontend exited; check output/frontend/stdout.log and stderr.log.' }
            try {
                $response = Invoke-WebRequest -Uri $frontendUrl -TimeoutSec 3
                if ($response.StatusCode -eq 200 -and $response.Content.Contains('/src/main.js')) {
                    Write-Output "Frontend ready: $frontendUrl (PID $($process.Id))."
                    return
                }
            } catch { $lastProbeError = $_.Exception.Message }
            Start-Sleep -Milliseconds 300
        } while ([DateTime]::UtcNow -lt $deadline)
        throw "Frontend did not become ready. Inspect output/frontend logs. $lastProbeError"
    }
    'Stop' {
        $process = Get-ManagedFrontendProcess
        if ($process) { Stop-Process -Id $process.Id; Write-Output "Stopped managed frontend PID $($process.Id)." }
        else { Write-Output 'No managed frontend process is running.' }
        if (Test-Path -LiteralPath $frontendState) { Remove-Item -LiteralPath $frontendState }
    }
    'Status' {
        $process = Get-ManagedFrontendProcess
        if ($process) { Write-Output "Managed frontend PID: $($process.Id)" }
        else { Write-Output 'No managed frontend process recorded.' }
        $response = Invoke-WebRequest -Uri $frontendUrl -TimeoutSec 5
        Write-Output "Frontend HTTP: $($response.StatusCode) ($frontendUrl)"
        Invoke-RestMethod -Uri "$frontendUrl/api/health" -TimeoutSec 5 | ConvertTo-Json -Depth 5
    }
}
