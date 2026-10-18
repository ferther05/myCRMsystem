[CmdletBinding()]
param(
    [ValidateSet('Build', 'Test', 'Verify', 'Run', 'Start', 'Stop', 'Status')]
    [string]$Action = 'Status'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$backendJar = Join-Path $backendRoot 'target/crm-backend.jar'
$backendOutput = Join-Path $projectRoot 'output/backend'
$backendState = Join-Path $backendOutput 'process.json'
$backendPort = if ($env:CRM_SERVER_PORT) { [int]$env:CRM_SERVER_PORT } else { 8080 }
$backendUrl = "http://127.0.0.1:$backendPort/api/health"

function Get-Java17 {
    $candidates = @($env:CRM_JAVA_HOME, $env:JAVA_HOME, 'C:/Program Files/Microsoft/jdk-17.0.8.101-hotspot')
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $executable = Join-Path $candidate 'bin/java.exe'
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $executable) -and (Test-Path -LiteralPath $releaseFile)) {
            if (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="17[.\"]' -Quiet) {
                return $candidate
            }
        }
    }
    throw 'JDK 17 is required. Set CRM_JAVA_HOME to its installation directory.'
}

function Invoke-BackendMaven {
    param([string[]]$Goals)
    $maven = if ($env:CRM_MAVEN_HOME) { Join-Path $env:CRM_MAVEN_HOME 'bin/mvn.cmd' }
             elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { (Get-Command mvn.cmd).Source }
             else { 'E:/apache-maven-3.9.16/bin/mvn.cmd' }
    if (-not (Test-Path -LiteralPath $maven)) { throw 'Maven not found. Set CRM_MAVEN_HOME.' }
    $originalJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = Get-Java17
        Push-Location $backendRoot
        try {
            & $maven -B -ntp @Goals
            if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE." }
        }
        finally { Pop-Location }
    }
    finally { $env:JAVA_HOME = $originalJavaHome }
}

function Get-ManagedBackendProcess {
    if (-not (Test-Path -LiteralPath $backendState)) { return $null }
    $state = Get-Content -Raw -LiteralPath $backendState | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    # Validate process identity before stopping it; a recycled PID must not target another app.
    if ($process.ProcessName -ne 'java' -or $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.startTicks) {
        return $null
    }
    return $process
}

switch ($Action) {
    'Build' { Invoke-BackendMaven -Goals @('package') }
    'Test' { Invoke-BackendMaven -Goals @('test') }
    'Verify' { Invoke-BackendMaven -Goals @('-Plocal-integration', 'verify') }
    'Run' {
        if (-not (Test-Path -LiteralPath $backendJar)) { throw 'Build first: tools/backend.ps1 -Action Build' }
        $java = Join-Path (Get-Java17) 'bin/java.exe'
        Push-Location $backendRoot
        try {
            & $java '-Dfile.encoding=UTF-8' -jar $backendJar '--spring.profiles.active=local'
            if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
        }
        finally { Pop-Location }
    }
    'Start' {
        if (Get-ManagedBackendProcess) { Write-Output 'Backend is already running. Use Status or Stop.'; return }
        if (-not (Test-Path -LiteralPath $backendJar)) { throw 'Build first: tools/backend.ps1 -Action Build' }
        $portCheck = [System.Net.Sockets.TcpClient]::new()
        try {
            $connection = $portCheck.ConnectAsync('127.0.0.1', $backendPort)
            try { $inUse = $connection.Wait(1000) -and $portCheck.Connected } catch { $inUse = $false }
        }
        finally { $portCheck.Dispose() }
        if ($inUse) { throw "Port $backendPort is already in use; no existing process was stopped." }
        New-Item -ItemType Directory -Path $backendOutput -Force | Out-Null
        $java = Join-Path (Get-Java17) 'bin/java.exe'
        $process = Start-Process -FilePath $java -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru `
            -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $backendJar + '"'), '--spring.profiles.active=local') `
            -RedirectStandardOutput (Join-Path $backendOutput 'stdout.log') `
            -RedirectStandardError (Join-Path $backendOutput 'stderr.log')
        @{processId=$process.Id; startTicks=$process.StartTime.ToUniversalTime().Ticks.ToString()} |
            ConvertTo-Json | Set-Content -LiteralPath $backendState -Encoding UTF8
        $deadline = [DateTime]::UtcNow.AddSeconds(50)
        do {
            if ($process.HasExited) { throw "Backend exited; check output/backend/stdout.log and stderr.log." }
            try {
                $health = Invoke-RestMethod -Uri $backendUrl -TimeoutSec 4
                if ($health.code -eq 0 -and $health.data.status -eq 'UP') {
                    Write-Output "Backend ready: $backendUrl (PID $($process.Id))."
                    return
                }
            }
            catch { $lastProbeError = $_.Exception.Message }
            Start-Sleep -Milliseconds 500
        } while ([DateTime]::UtcNow -lt $deadline)
        throw "Backend did not become healthy. Process may still be running; inspect logs. $lastProbeError"
    }
    'Stop' {
        $process = Get-ManagedBackendProcess
        if ($process) {
            # Get-ManagedBackendProcess already checked name and start time against our saved identity.
            # Process.Kill avoids a Stop-Process null-reference failure on this PowerShell host.
            $process.Kill()
            if (-not $process.WaitForExit(10000)) { throw 'Backend process did not exit within 10 seconds.' }
            Write-Output "Stopped managed backend PID $($process.Id)."
        }
        else { Write-Output 'No managed backend process is running.' }
        if (Test-Path -LiteralPath $backendState) { Remove-Item -LiteralPath $backendState }
    }
    'Status' {
        $process = Get-ManagedBackendProcess
        if ($process) { Write-Output "Managed backend PID: $($process.Id)" }
        else { Write-Output 'No managed backend process recorded.' }
        try { Invoke-RestMethod -Uri $backendUrl -TimeoutSec 5 | ConvertTo-Json -Depth 5 }
        catch { throw "Health check failed at $backendUrl. $($_.Exception.Message)" }
    }
}
