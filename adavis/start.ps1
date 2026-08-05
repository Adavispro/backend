$env:PORT = "3000"
$env:HOSTNAME = "0.0.0.0"

# Standalone runtime needs static/public assets under .next/standalone.
if (!(Test-Path ".next/standalone/.next")) {
  New-Item -ItemType Directory -Path ".next/standalone/.next" | Out-Null
}

if (Test-Path ".next/static") {
  Remove-Item ".next/standalone/.next/static" -Recurse -Force -ErrorAction SilentlyContinue
  Copy-Item -Path ".next/static" -Destination ".next/standalone/.next/static" -Recurse -Force
}

if (Test-Path "public") {
  Remove-Item ".next/standalone/public" -Recurse -Force -ErrorAction SilentlyContinue
  Copy-Item -Path "public" -Destination ".next/standalone/public" -Recurse -Force
}

Get-Content .env.production | ForEach-Object {
  if ($_ -and -not $_.StartsWith("#")) {
    $parts = $_ -split "=", 2
    if ($parts.Length -eq 2) {
      [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
    }
  }
}

node .next/standalone/server.js
