param(
  [string]$EnvFile = ".env.local"
)

if (-not (Test-Path $EnvFile)) {
  Write-Host "Missing $EnvFile. Copy .env.local.example to .env.local and fill values." -ForegroundColor Yellow
  exit 1
}

Get-Content $EnvFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line) { return }
  if ($line.StartsWith("#")) { return }
  $parts = $line -split "=", 2
  if ($parts.Length -ne 2) { return }
  $key = $parts[0].Trim()
  $val = $parts[1].Trim()
  if ($val.StartsWith('"') -and $val.EndsWith('"')) {
    $val = $val.Substring(1, $val.Length - 2)
  } elseif ($val.StartsWith("'") -and $val.EndsWith("'")) {
    $val = $val.Substring(1, $val.Length - 2)
  }
  if ($key) {
    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
  }
}

Write-Host "Loaded env from $EnvFile" -ForegroundColor Green

if (Test-Path ".\mvnw.cmd") {
  .\mvnw.cmd -DskipTests spring-boot:run
} else {
  mvn -DskipTests spring-boot:run
}
