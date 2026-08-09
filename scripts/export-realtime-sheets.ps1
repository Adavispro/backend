[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "realtime_sample_data"
$outDir = Join-Path $sourceDir "json"

if (-not (Test-Path $sourceDir)) {
    throw "Realtime sample directory not found: $sourceDir"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Get-ChildItem -Path $outDir -File -Filter "*.json" | Remove-Item -Force

function Convert-ToCamelCase {
    param([Parameter(Mandatory)][string]$Text)

    $clean = ($Text -replace "[^A-Za-z0-9]+", " ").Trim()
    if ([string]::IsNullOrWhiteSpace($clean)) {
        return "field"
    }

    $parts = @($clean -split "\s+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($parts.Count -eq 0) {
        return "field"
    }

    $result = $parts[0].Substring(0,1).ToLowerInvariant() + $parts[0].Substring(1)
    for ($i = 1; $i -lt $parts.Count; $i++) {
        $part = $parts[$i]
        if ($part.Length -eq 1) {
            $result += $part.ToUpperInvariant()
        }
        else {
            $result += $part.Substring(0,1).ToUpperInvariant() + $part.Substring(1)
        }
    }

    return $result
}

function Convert-CellValue {
    param([AllowNull()][string]$Value)

    if ($null -eq $Value) {
        return $null
    }

    $trimmed = $Value.Trim()
    if ($trimmed -eq "") {
        return $null
    }

    $numericCandidate = $trimmed -replace "[^0-9.+-]", ""
    if ($numericCandidate -ne "") {
        $num = 0.0
        if ([double]::TryParse($numericCandidate, [System.Globalization.NumberStyles]::Any, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$num)) {
            return $num
        }
    }

    if ($trimmed -match "^(true|false)$") {
        return [bool]::Parse($trimmed)
    }

    return $trimmed
}

function Convert-DateValue {
    param([AllowNull()]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [double] -or $Value -is [float] -or $Value -is [decimal] -or $Value -is [int] -or $Value -is [long]) {
        try {
            return [datetime]::FromOADate([double]$Value)
        }
        catch {
            return $null
        }
    }

    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    $dt = [datetime]::MinValue
    if ([datetime]::TryParse($text, [ref]$dt)) {
        return $dt
    }

    return $null
}

$files = @(
    @{ Path = (Join-Path $sourceDir "WEG.xlsx"); EqType = "WEG"; Prefix = "PR_WEG_003_" },
    @{ Path = (Join-Path $sourceDir "FBD.xlsx"); EqType = "FBD"; Prefix = "PR_FBD_004_" },
    @{ Path = (Join-Path $sourceDir "BLE (1).xlsx"); EqType = "BLE"; Prefix = "PR_BLE_003_" }
)

$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false

try {
    foreach ($file in $files) {
        if (-not (Test-Path $file.Path)) {
            Write-Warning "Skipping missing file: $($file.Path)"
            continue
        }

        $records = New-Object System.Collections.Generic.List[object]
        $wb = $excel.Workbooks.Open($file.Path)
        try {
            foreach ($ws in $wb.Worksheets) {
                $maxHeaderCols = 120
                $rawHeaderRange = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item(1, $maxHeaderCols)).Value2
                $headers = @()
                for ($c = 1; $c -le $maxHeaderCols; $c++) {
                    $hv = $rawHeaderRange[1, $c]
                    if ($null -eq $hv) {
                        $headers += ""
                    }
                    else {
                        $headers += [string]$hv
                    }
                }

                $dateCol = -1
                for ($c = 1; $c -le $headers.Count; $c++) {
                    if ($headers[$c - 1] -eq "DateTimeField") {
                        $dateCol = $c
                        break
                    }
                }
                if ($dateCol -eq -1) {
                    continue
                }

                # Avoid UsedRange row inflation from sheet formatting; detect true last data row by DateTimeField.
                $xlUp = -4162
                $rowCount = [int]$ws.Cells.Item($ws.Rows.Count, $dateCol).End($xlUp).Row
                if ($rowCount -lt 2) {
                    continue
                }

                $rawRange = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item($rowCount, $maxHeaderCols)).Value2

                $recipeCol = -1
                $operatorCol = -1
                $statusCol = -1
                for ($c = 1; $c -le $headers.Count; $c++) {
                    $h = $headers[$c - 1]
                    if ($recipeCol -eq -1 -and $h -match "Recipe_Name|RECIPE_NAME") { $recipeCol = $c }
                    if ($operatorCol -eq -1 -and $h -match "User_ID|_USER$") { $operatorCol = $c }
                    if ($statusCol -eq -1 -and $h -match "Batch_Status|Process_Manual_Auto_Status|Recipe_State") { $statusCol = $c }
                }

                for ($r = 2; $r -le $rowCount; $r++) {
                    $dt = Convert-DateValue -Value $rawRange[$r, $dateCol]
                    if ($null -eq $dt) {
                        continue
                    }

                    $recipe = $null
                    if ($recipeCol -gt 0) {
                        $recipe = [string]$rawRange[$r, $recipeCol]
                    }

                    $operator = $null
                    if ($operatorCol -gt 0) {
                        $operator = [string]$rawRange[$r, $operatorCol]
                    }

                    $status = $null
                    if ($statusCol -gt 0) {
                        $status = [string]$rawRange[$r, $statusCol]
                    }

                    $metrics = [ordered]@{}
                    for ($c = 1; $c -le $headers.Count; $c++) {
                        if ($c -eq $dateCol) { continue }

                        $header = [string]$headers[$c - 1]
                        if ([string]::IsNullOrWhiteSpace($header)) { continue }

                        $cellValue = $rawRange[$r, $c]
                        $raw = ""
                        if ($null -ne $cellValue) {
                            $raw = [string]$cellValue
                        }
                        if ([string]::IsNullOrWhiteSpace($raw)) { continue }

                        $key = $header
                        if ($key.StartsWith($file.Prefix)) {
                            $key = $key.Substring($file.Prefix.Length)
                        }

                        $key = Convert-ToCamelCase -Text $key
                        if ($key -eq "") { continue }

                        $metrics[$key] = Convert-CellValue -Value $raw
                    }

                    $records.Add([pscustomobject]@{
                        equipmentType = $file.EqType
                        batchNo = $ws.Name
                        observedAt = $dt.ToUniversalTime().ToString("o")
                        recipeNameRaw = $recipe
                        operatorNameRaw = $operator
                        statusRaw = $status
                        metrics = $metrics
                    }) | Out-Null
                }
            }
        }
        finally {
            $wb.Close($false)
        }

        $outFile = Join-Path $outDir ($file.EqType.ToLowerInvariant() + ".json")
        $records | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -Path $outFile
        Write-Host "Exported $($records.Count) records -> $outFile"
    }
}
finally {
    $excel.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
}
