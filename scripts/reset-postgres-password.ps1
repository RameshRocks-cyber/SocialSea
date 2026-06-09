param(
  [string]$NewPassword = "Postgres@123",
  [string]$DbUser = "postgres",
  [string]$ServiceName = "postgresql-x64-16",
  [string]$PgDataDir = "C:\Program Files\PostgreSQL\16\data",
  [string]$PsqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-Admin {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script in an Administrator PowerShell window."
  }
}

function Escape-SqlLiteral([string]$s) {
  return $s.Replace("'", "''")
}

Assert-Admin

$hbaPath = Join-Path $PgDataDir "pg_hba.conf"
if (-not (Test-Path $hbaPath)) {
  throw "pg_hba.conf not found at: $hbaPath"
}
if (-not (Test-Path $PsqlPath)) {
  throw "psql.exe not found at: $PsqlPath"
}

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$backupPath = "$hbaPath.bak.codex.$timestamp"
Copy-Item -LiteralPath $hbaPath -Destination $backupPath -Force
Write-Host "Backup created: $backupPath" -ForegroundColor Green

$restored = $false

try {
  $hbaRaw = Get-Content -Raw $hbaPath
  $hbaRaw = $hbaRaw -replace '(?m)^local\s+all\s+all\s+\S+\s*$', 'local   all             all                                     trust'
  $hbaRaw = $hbaRaw -replace '(?m)^host\s+all\s+all\s+127\.0\.0\.1/32\s+\S+\s*$', 'host    all             all             127.0.0.1/32            trust'
  $hbaRaw = $hbaRaw -replace '(?m)^host\s+all\s+all\s+::1/128\s+\S+\s*$', 'host    all             all             ::1/128                 trust'
  Set-Content -LiteralPath $hbaPath -Value $hbaRaw -Encoding ASCII
  Write-Host "Temporarily switched localhost auth to trust." -ForegroundColor Yellow

  Restart-Service -Name $ServiceName -Force
  Start-Sleep -Seconds 2
  Write-Host "PostgreSQL restarted with temporary trust auth." -ForegroundColor Yellow

  $escapedPassword = Escape-SqlLiteral $NewPassword
  $sql = "ALTER ROLE `"$DbUser`" WITH PASSWORD '$escapedPassword';"
  & $PsqlPath -h 127.0.0.1 -U $DbUser -d postgres -c $sql
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to reset password for role '$DbUser'."
  }
  Write-Host "Password updated for role '$DbUser'." -ForegroundColor Green
}
finally {
  if (Test-Path $backupPath) {
    Copy-Item -LiteralPath $backupPath -Destination $hbaPath -Force
    $restored = $true
  }
  if ($restored) {
    Restart-Service -Name $ServiceName -Force
    Start-Sleep -Seconds 2
    Write-Host "Restored original pg_hba.conf and restarted PostgreSQL." -ForegroundColor Green
  }
}

$env:PGPASSWORD = $NewPassword
& $PsqlPath -h 127.0.0.1 -U $DbUser -d postgres -c "select current_user, current_database();"
if ($LASTEXITCODE -ne 0) {
  throw "Password reset script completed, but login verification failed."
}

Write-Host "Verification passed. PostgreSQL login works with the new password." -ForegroundColor Green
