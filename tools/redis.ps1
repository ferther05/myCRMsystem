[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Restart', 'Status')]
    [string]$Action = 'Status'
)

$ErrorActionPreference = 'Stop'
$redisDistro = 'Ubuntu-24.04'

function Invoke-LocalRedis {
    param([string[]]$RedisCommand)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync('127.0.0.1', 6380)
        if (-not $connection.Wait(3000)) { throw 'Redis connection timed out.' }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 3000
        $request = '*' + $RedisCommand.Count + "`r`n"
        foreach ($argument in $RedisCommand) {
            $request += '$' + [System.Text.Encoding]::UTF8.GetByteCount($argument) + "`r`n" + $argument + "`r`n"
        }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($request)
        $stream.Write($bytes, 0, $bytes.Length)
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
        $firstLine = $reader.ReadLine()
        if ([string]::IsNullOrEmpty($firstLine)) { throw 'Redis returned an empty response.' }
        if ($firstLine[0] -eq '+') { return $firstLine.Substring(1) }
        if ($firstLine[0] -eq '$') {
            $length = [int]$firstLine.Substring(1)
            if ($length -lt 0) { return $null }
            # INFO server returns ASCII text, so its byte and character counts match.
            $buffer = [char[]]::new($length)
            $offset = 0
            while ($offset -lt $length) {
                $count = $reader.Read($buffer, $offset, $length - $offset)
                if ($count -eq 0) { throw 'Redis response ended early.' }
                $offset += $count
            }
            return -join $buffer
        }
        throw "Unexpected Redis response: $firstLine"
    }
    finally { $client.Dispose() }
}

function Get-CrmRedisStatus {
    if ((Invoke-LocalRedis -RedisCommand @('PING')) -ne 'PONG') { throw 'Redis PING failed.' }
    $serverInfo = Invoke-LocalRedis -RedisCommand @('INFO', 'server')
    $version = [regex]::Match($serverInfo, '(?m)^redis_version:([^\r\n]+)').Groups[1].Value
    if ($version -notmatch '^7\.') { throw "Redis 7 required; actual version: $version" }
    Write-Output "Redis $version is ready at 127.0.0.1:6380 (PONG)."
}

function Wait-CrmRedis {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        try { Get-CrmRedisStatus; return }
        catch { $connectionError = $_ }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Redis did not become ready: $connectionError"
}

function Invoke-CrmRedisService {
    param([string]$ServiceAction)
    & wsl.exe --distribution $redisDistro --user root --exec systemctl $ServiceAction crm-redis.service
    if ($LASTEXITCODE -ne 0) { throw "Linux Redis service action failed: $ServiceAction" }
}

function Start-CrmRedisHost {
    # Linux flock allows only one keepalive, even after repeated Start commands.
    $wslPath = Join-Path $env:SystemRoot 'System32\wsl.exe'
    Start-Process -FilePath $wslPath -WindowStyle Hidden -ArgumentList @(
        '--distribution', $redisDistro,
        '--user', 'root',
        '--exec', '/usr/local/sbin/crm-redis-keepalive'
    )
}

switch ($Action) {
    'Start' {
        Start-CrmRedisHost
        Wait-CrmRedis
    }
    'Stop' {
        Invoke-CrmRedisService -ServiceAction 'stop'
        & wsl.exe --distribution $redisDistro --user root --exec bash -lc "pkill -f '^crm-redis-keepalive infinity$' || true"
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the CRM Redis keepalive.' }
        Write-Output 'CRM Redis on port 6380 stopped.'
    }
    'Restart' {
        Start-CrmRedisHost
        Invoke-CrmRedisService -ServiceAction 'restart'
        Wait-CrmRedis
    }
    'Status' { Get-CrmRedisStatus }
}
