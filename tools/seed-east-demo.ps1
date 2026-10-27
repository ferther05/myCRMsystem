[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$crmRoot = Split-Path -Parent $PSScriptRoot
$crmMysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
$crmDump = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe'
$crmPython = if ($env:CRM_PYTHON) { $env:CRM_PYTHON } else { 'C:/Users/psc/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' }
if (-not (Test-Path -LiteralPath $crmPython)) { throw 'Set the Python runtime path in this local seed helper; reportlab and pypdf are required.' }
$crmConfig = Get-Content -Raw -LiteralPath (Join-Path $crmRoot 'backend/config/application-local.yml')
$env:MYSQL_PWD = [regex]::Match($crmConfig,'\$\{CRM_DB_PASSWORD:([^}]+)\}').Groups[1].Value
function Invoke-DemoSql([string]$Query) {
    $crmResult = & $crmMysql --protocol=TCP -h 127.0.0.1 -u crm_app --database=crm_system --default-character-set=utf8mb4 --batch --skip-column-names --execute=$Query
    if ($LASTEXITCODE -ne 0) { throw 'East demo SQL failed; uncommitted changes were rolled back on disconnect.' }
    return $crmResult
}
try {
    # Fail before mutation if the expected accounts, team, or seed prefix are inconsistent.
    $crmReady = Invoke-DemoSql "SELECT COUNT(*) FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE t.team_code='EAST' AND u.account_status='ACTIVE' AND ((u.username='east.sales' AND u.role_code='SPECIALIST') OR (u.username='east.manager' AND u.role_code='MANAGER'));"
    if ([int]$crmReady -ne 2) { throw 'The active East manager and specialist accounts are required.' }
    $crmAdminReady = Invoke-DemoSql "SELECT COUNT(*) FROM sys_user WHERE username='admin' AND role_code='ADMIN' AND account_status='ACTIVE';"
    if ([int]$crmAdminReady -ne 1) { throw 'An active administrator is required for demo initialization audit.' }
    $crmConflicts = Invoke-DemoSql "SELECT COUNT(*) FROM crm_customer WHERE unified_social_credit_code BETWEEN '913100000000000001' AND '913100000000000022' AND COALESCE(remark,'') NOT LIKE 'CRM-DEMO-EAST-20261022；%';"
    if ([int]$crmConflicts -ne 0) { throw 'Demo identity collides with existing customer data; no data was changed.' }
    $crmBackupDir = Join-Path $crmRoot 'output/database-backups'
    New-Item -ItemType Directory -Path $crmBackupDir -Force | Out-Null
    $crmBackup = Join-Path $crmBackupDir ('crm_system-before-east-demo-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
    # Read-only backup using the existing app account. No credential is written into files.
    & $crmDump --protocol=TCP -h 127.0.0.1 -u crm_app --single-transaction --no-tablespaces --skip-lock-tables --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file=$crmBackup crm_system
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed; demo data was not inserted.' }
    & $crmPython (Join-Path $crmRoot 'tools/create-east-demo-attachment.py')
    if ($LASTEXITCODE -ne 0) { throw 'Demo file creation failed; demo data was not inserted.' }
    $crmFile = (Join-Path $crmRoot 'output/contract-files/demo-east-20261022/demo-only.pdf').Replace('\','/')
    $crmFileSize = (Get-Item -LiteralPath $crmFile).Length
    $crmSeedPath = (Join-Path $crmRoot 'database/013_seed_east_demo_data.sql').Replace('\','/')
    $crmSeedSql = "SET @demo_file_path='" + $crmFile.Replace("'","''") + "'; SET @demo_file_size=" + $crmFileSize + ";`n" + (Get-Content -Raw -LiteralPath $crmSeedPath)
    $crmPreviousEncoding = $OutputEncoding
    try {
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $crmSeedSql | & $crmMysql --protocol=TCP -h 127.0.0.1 -u crm_app --database=crm_system --default-character-set=utf8mb4 --batch
        if ($LASTEXITCODE -ne 0) { throw 'East demo SQL failed; the uncommitted transaction was rolled back.' }
    } finally { $OutputEncoding = $crmPreviousEncoding }
    Write-Output "Backup saved: $crmBackup"
    Write-Output 'East demo seed finished. Existing scenario groups are preserved on repeat runs.'
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    $crmConfig = $null
}
