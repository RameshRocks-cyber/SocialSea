param(
  [string]$EnvFile = ".env.local",
  [string]$Profiles = ""
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

$effectiveProfiles = ""
if ($Profiles -and $Profiles.Trim() -ne "") {
  $effectiveProfiles = $Profiles.Trim()
} elseif ($env:SPRING_PROFILES_ACTIVE -and $env:SPRING_PROFILES_ACTIVE.Trim() -ne "") {
  $effectiveProfiles = $env:SPRING_PROFILES_ACTIVE.Trim()
} else {
  $effectiveProfiles = "dev,h2"
  Write-Host "SPRING_PROFILES_ACTIVE was not set; defaulting to dev,h2 for local startup." -ForegroundColor Yellow
}

[System.Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", $effectiveProfiles, "Process")
Write-Host "SPRING_PROFILES_ACTIVE=$effectiveProfiles" -ForegroundColor Green

$profileList = $effectiveProfiles.ToLower().Split(",") | ForEach-Object { $_.Trim() }
if ($profileList -contains "h2") {
  $currentUrl = [System.Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_URL", "Process")
  $shouldOverrideDatasource = (-not $currentUrl) -or $currentUrl.Trim().ToLower().StartsWith("jdbc:postgresql:")
  if ($shouldOverrideDatasource) {
    [System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_URL", "jdbc:h2:file:./tmp/h2/socialsea;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE", "Process")
    [System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_USERNAME", "sa", "Process")
    [System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "", "Process")
    [System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver", "Process")
    Write-Host "Applied H2 datasource defaults for local startup." -ForegroundColor Yellow
  }
}

# Use repo-local Maven cache by default (avoids user-home permission issues in some environments).
if (-not $env:MAVEN_USER_HOME -or $env:MAVEN_USER_HOME.Trim() -eq "") {
  $repoM2 = Join-Path (Get-Location) ".m2"
  if (-not (Test-Path $repoM2)) {
    New-Item -ItemType Directory -Force -Path $repoM2 | Out-Null
  }
  $repoM2 = (Resolve-Path $repoM2).Path
  [System.Environment]::SetEnvironmentVariable("MAVEN_USER_HOME", $repoM2, "Process")
}

$mavenRepoLocalArg = "-Dmaven.repo.local=.m2/repository"

if (-not $env:OPENAI_API_KEY -or $env:OPENAI_API_KEY.Trim() -eq "") {
  Write-Host "OPENAI_API_KEY is missing or empty (check .env.local)." -ForegroundColor Yellow
}

if (-not $env:GEMINI_API_KEY -or $env:GEMINI_API_KEY.Trim() -eq "") {
  Write-Host "GEMINI_API_KEY is missing or empty (check .env.local)." -ForegroundColor Yellow
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
  Write-Host "Starting backend with system Maven..." -ForegroundColor Green
  mvn $mavenRepoLocalArg -DskipTests spring-boot:run
  exit $LASTEXITCODE
}

if (Test-Path ".\mvnw.cmd") {
  Write-Host "Starting backend with Maven Wrapper..." -ForegroundColor Green
  .\mvnw.cmd $mavenRepoLocalArg -DskipTests spring-boot:run
  exit $LASTEXITCODE
}

Write-Host "Maven not found (mvn) and Maven Wrapper missing." -ForegroundColor Red
exit 1
