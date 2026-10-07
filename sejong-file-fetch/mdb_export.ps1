param(
    [Parameter(Mandatory=$true)][string]$DatabasePath,
    [Parameter(Mandatory=$true)][string]$TableName,
    [Parameter(Mandatory=$true)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'
if ($TableName -notmatch '^[A-Za-z][A-Za-z0-9_]*$') { throw 'Unsafe table name' }
$connection = $null
$providers = @('Microsoft.ACE.OLEDB.16.0', 'Microsoft.ACE.OLEDB.12.0', 'Microsoft.Jet.OLEDB.4.0')
foreach ($provider in $providers) {
    $candidate = $null
    try {
        $candidate = [System.Data.OleDb.OleDbConnection]::new("Provider=$provider;Data Source=$DatabasePath;Persist Security Info=False;Mode=Read;")
        $candidate.Open()
        $connection = $candidate
        break
    } catch {
        if ($candidate) { $candidate.Dispose() }
    }
}
if (-not $connection) { throw 'No Microsoft Access OLE DB provider could open this MDB. Install the matching Microsoft Access Database Engine.' }
try {
    $found = $false
    $schema = $connection.GetSchema('Tables')
    foreach ($row in $schema.Rows) {
        if ([string]::Equals([string]$row['TABLE_NAME'], $TableName, [System.StringComparison]::OrdinalIgnoreCase)) {
            $found = $true
            break
        }
    }
    if (-not $found) { exit 3 }
    $adapter = [System.Data.OleDb.OleDbDataAdapter]::new("SELECT * FROM [$TableName]", $connection)
    $table = New-Object System.Data.DataTable
    [void]$adapter.Fill($table)
    $parent = Split-Path -Parent $OutputPath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    if ($table.Rows.Count -eq 0) {
        ($table.Columns | ForEach-Object { $_.ColumnName }) -join ',' | Set-Content -Path $OutputPath -Encoding UTF8
    } else {
        $objects = foreach ($dataRow in $table.Rows) {
            $record = [ordered]@{}
            foreach ($column in $table.Columns) { $record[$column.ColumnName] = $dataRow[$column] }
            [pscustomobject]$record
        }
        $objects | Export-Csv -Path $OutputPath -NoTypeInformation -Encoding UTF8
    }
    Write-Output "ROWS=$($table.Rows.Count)"
} finally {
    $connection.Close()
    $connection.Dispose()
}
