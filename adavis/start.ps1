$env:PORT = "3000"
$env:HOSTNAME = "0.0.0.0"
Get-Content .env.production | ForEach-Object {
  if ($_ -and -not $_.StartsWith("#")) {
    $parts = $_ -split "=", 2
    if ($parts.Length -eq 2) {
      [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
    }
  }
}

node .next/standalone/server.js
