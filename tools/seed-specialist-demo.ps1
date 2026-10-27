[CmdletBinding()]
param([switch]$ApplyRules)

$ErrorActionPreference = 'Stop'
$crmRoot = Split-Path -Parent $PSScriptRoot
$crmMysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
$crmDump = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe'
$crmConfig = Get-Content -Raw -LiteralPath (Join-Path $crmRoot 'backend/config/application-local.yml')
$crmOldPassword = $env:MYSQL_PWD
$crmOldEncoding = $OutputEncoding
$Username = '1234'
try {
    $env:MYSQL_PWD = [regex]::Match($crmConfig,'\$\{CRM_DB_PASSWORD:([^}]+)\}').Groups[1].Value
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    # Hex encoding keeps the user-selected account out of SQL string interpolation.
    $crmUsernameHex = [Convert]::ToHexString([System.Text.Encoding]::UTF8.GetBytes($Username))
    $crmQuery = "SELECT COUNT(*) FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.username=CONVERT(0x$crmUsernameHex USING utf8mb4) AND u.account_status='ACTIVE' AND u.role_code='SPECIALIST' AND t.enabled=1;"
    $crmReady = & $crmMysql --protocol=TCP -h 127.0.0.1 -u crm_app --database=crm_system --batch --skip-column-names --execute=$crmQuery
    if ($LASTEXITCODE -ne 0 -or [int]$crmReady -ne 1) { throw 'An active specialist in an enabled team is required; no mutation was performed.' }
    $crmBackupDir = Join-Path $crmRoot 'output/database-backups'
    New-Item -ItemType Directory -Path $crmBackupDir -Force | Out-Null
    $crmBackup = Join-Path $crmBackupDir ('crm_system-before-claim-demo-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
    & $crmDump --protocol=TCP -h 127.0.0.1 -u crm_app --single-transaction --no-tablespaces --skip-lock-tables --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file=$crmBackup crm_system
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed; no data was changed.' }
    if ($ApplyRules) {
        Get-Content -Raw -LiteralPath (Join-Path $crmRoot 'database/014_claim_limit_and_chinese_labels.sql') |
            & $crmMysql --protocol=TCP -h 127.0.0.1 -u crm_app --database=crm_system --default-character-set=utf8mb4 --batch
        if ($LASTEXITCODE -ne 0) { throw 'Rule migration failed; demo data was not added.' }
    }
    $crmSeed = "SET @crm_demo_username=CONVERT(0x$crmUsernameHex USING utf8mb4);`n" +
        (Get-Content -Raw -LiteralPath (Join-Path $crmRoot 'database/015_seed_specialist_claim_demo.sql'))
    $crmSeed | & $crmMysql --protocol=TCP -h 127.0.0.1 -u crm_app --database=crm_system --default-character-set=utf8mb4 --batch
    if ($LASTEXITCODE -ne 0) { throw 'Demo seed failed; uncommitted demo changes were rolled back.' }
    Write-Output "Backup saved: $crmBackup"
    Write-Output 'Demo seed finished; repeated execution preserves existing scenario data.'
} finally {
    $OutputEncoding = $crmOldEncoding
    $env:MYSQL_PWD = $crmOldPassword
    $crmConfig = $null
}
