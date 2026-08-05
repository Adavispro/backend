$src = 'C:\Sabariraj\Workspace\PROJECTS\ADAVIS\ADAVIS_PLATFORM_FRONTEND\adavis-frontend'
$dest = 'C:\Sabariraj\Workspace\PROJECTS\ADAVIS\ADAVIS_PLATFORM_BACKEND\adavis'

New-Item -ItemType Directory -Path $dest -Force | Out-Null

if (Test-Path "$src\.next") {
  Copy-Item -Path "$src\.next" -Destination $dest -Recurse -Force
}

if (Test-Path "$src\public") {
  Copy-Item -Path "$src\public" -Destination $dest -Recurse -Force
}

Copy-Item -Path "$src\package.json" -Destination "$dest\package.json" -Force
Copy-Item -Path "$src\package-lock.json" -Destination "$dest\package-lock.json" -Force

$envLines = @(
  'NEXT_PUBLIC_API_BASE_URL=http://3.24.80.154',
  'API_GATEWAY_URL=http://3.24.80.154',
  'AUTH_SERVICE_URL=http://3.24.80.154',
  'MDM_SERVICE_URL=http://3.24.80.154',
  'IIOT_SERVICE_URL=http://3.24.80.154',
  'LICENSE_SERVICE_URL=http://3.24.80.154',
  'AUDIT_SERVICE_URL=http://3.24.80.154',
  'PORT=3000',
  'HOSTNAME=0.0.0.0'
)
Set-Content -Path "$dest\.env.production" -Value $envLines

$startScript = @(
  '$env:PORT = "3000"',
  '$env:HOSTNAME = "0.0.0.0"',
  'Get-Content .env.production | ForEach-Object {',
  '  if ($_ -and -not $_.StartsWith("#")) {',
  '    $parts = $_ -split "=", 2',
  '    if ($parts.Length -eq 2) {',
  '      [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")',
  '    }',
  '  }',
  '}',
  '',
  'node .next/standalone/server.js'
)
Set-Content -Path "$dest\start.ps1" -Value $startScript

Write-Host "Frontend deployment prepared at $dest"
Get-ChildItem $dest | Select-Object Name, Mode
