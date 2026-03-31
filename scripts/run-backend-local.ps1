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

if (-not $env:OPENAI_API_KEY -or $env:OPENAI_API_KEY.Trim() -eq "") {
  Write-Host "OPENAI_API_KEY is missing or empty (check .env.local)." -ForegroundColor Yellow
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
  Write-Host "Starting backend with system Maven..." -ForegroundColor Green
  mvn -DskipTests spring-boot:run
  exit $LASTEXITCODE
}

if (Test-Path ".\mvnw.cmd") {
  Write-Host "Starting backend with Maven Wrapper..." -ForegroundColor Green
  .\mvnw.cmd -DskipTests spring-boot:run
  exit $LASTEXITCODE
}

Write-Host "Maven not found (mvn) and Maven Wrapper missing." -ForegroundColor Red
exit 1
