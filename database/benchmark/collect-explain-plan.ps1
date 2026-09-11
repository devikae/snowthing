param(
  [string]$OutputPath = 'docs/study/sprint04/comment/benchmark/explain-plan-details.csv',
  [string]$SummaryPath = 'docs/study/sprint04/comment/benchmark/explain-plan-table.generated.md'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'benchmark-queries.ps1')
$dbUsername = $env:SNOWTHING_DB_USERNAME
$dbPassword = $env:SNOWTHING_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($dbUsername) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
  Write-Error 'SNOWTHING_DB_USERNAME and SNOWTHING_DB_PASSWORD are required.'
  exit 1
}
$schemas = [ordered]@{
  '1k' = 'snowthing_benchmark_1k'
  '10k' = 'snowthing_benchmark_10k'
  '100k' = 'snowthing_benchmark_100k'
}
$indexes = @('idx_comment_post_parent_id', 'idx_comment_parent_deleted_id')

function Invoke-MySql([string]$Sql) {
  $previousMySqlPwd = $env:MYSQL_PWD
  try {
    $env:MYSQL_PWD = $dbPassword
    $result = $Sql | docker exec -i -e MYSQL_PWD snowthing-mysql mysql -u $dbUsername -B -N 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return @($result)
  }
  finally {
    if ($null -eq $previousMySqlPwd) {
      Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
      $env:MYSQL_PWD = $previousMySqlPwd
    }
  }
}

function Set-IndexVisibility([string]$Schema, [string]$Visibility) {
  foreach ($index in $indexes) {
    Invoke-MySql "ALTER TABLE ``$Schema``.comment ALTER INDEX ``$index`` $Visibility" | Out-Null
  }
}

function Ensure-ParentDirectory([string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path)) { throw 'Output path must not be empty.' }
  $parentDirectory = Split-Path -Parent $Path
  if (-not [string]::IsNullOrWhiteSpace($parentDirectory)) {
    New-Item -ItemType Directory -Force -Path $parentDirectory | Out-Null
  }
}

function Get-Queries([string]$Schema) {
  return (Get-BenchmarkQueryContext -Schema $Schema -InvokeMySql ${function:Invoke-MySql}).Queries
}

Ensure-ParentDirectory $OutputPath
Ensure-ParentDirectory $SummaryPath

$rows = [System.Collections.Generic.List[object]]::new()
$collectionError = $null
$restoreErrors = [System.Collections.Generic.List[string]]::new()
try {
  foreach ($scale in $schemas.Keys) {
    $schema = $schemas[$scale]
    Invoke-MySql "ANALYZE TABLE ``$schema``.comment, ``$schema``.member" | Out-Null
    $queries = Get-Queries $schema
    foreach ($state in @('visible', 'invisible')) {
      Set-IndexVisibility $schema $state.ToUpperInvariant()
      foreach ($scenario in $queries.Keys) {
        foreach ($line in Invoke-MySql "EXPLAIN $($queries[$scenario])") {
          $column = $line -split "`t", 12
          $extra = if ($column.Count -ge 12) { $column[11] } else { '' }
          $rows.Add([pscustomobject]@{
            scale = $scale
            scenario = $scenario
            index_state = $state
            table = $column[2]
            access_type = $column[4]
            key = $column[6]
            key_len = $column[7]
            estimated_rows = $column[9]
            filesort = if ($extra -match 'Using filesort') { 'Y' } else { 'N' }
            temporary = if ($extra -match 'Using temporary') { 'Y' } else { 'N' }
            extra = $extra
          })
        }
      }
    }
  }
}
catch {
  $collectionError = $_
}
finally {
  foreach ($schema in $schemas.Values) {
    try {
      Set-IndexVisibility $schema 'VISIBLE'
    }
    catch {
      $restoreErrors.Add("${schema}: $($_.Exception.Message)")
    }
  }
}

if ($null -ne $collectionError) {
  if ($restoreErrors.Count -gt 0) {
    throw "EXPLAIN collection failed: $($collectionError.Exception.Message)`nIndex visibility restore also failed:`n$($restoreErrors -join "`n")"
  }
  throw $collectionError
}
if ($restoreErrors.Count -gt 0) {
  throw "Failed to restore index visibility for one or more schemas:`n$($restoreErrors -join "`n")"
}

$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$summary = [System.Collections.Generic.List[string]]::new()
$summary.Add('# EXPLAIN key length, filesort, and temporary summary')
$summary.Add('')
$summary.Add('- Environment: MySQL 8.0.46')
$summary.Add('- Scope: 1K, 10K, and 100K; nine scenarios; indexes visible and invisible')
$summary.Add('- Plan format: `table:access_type/key/key_len`, in traditional EXPLAIN order.')
$summary.Add('- FS/TMP is Y when any plan node reports `Using filesort`/`Using temporary`.')
$summary.Add('- NULL means that the plan node did not select an index.')
$summary.Add('')

foreach ($scale in $schemas.Keys) {
  $summary.Add("## $scale")
  $summary.Add('')
  $summary.Add('| Scenario | Index state | Plan (`table:type/key/key_len`) | FS | TMP |')
  $summary.Add('|---|---|---|:---:|:---:|')
  $scaleRows = $rows | Where-Object { $_.scale -eq $scale } | Group-Object scenario,index_state
  foreach ($group in $scaleRows) {
    $planRows = @($group.Group)
    $plan = ($planRows | ForEach-Object { "$($_.table):$($_.access_type)/$($_.key)/$($_.key_len)" }) -join '<br>'
    $filesort = if ($planRows.filesort -contains 'Y') { 'Y' } else { 'N' }
    $temporary = if ($planRows.temporary -contains 'Y') { 'Y' } else { 'N' }
    $summary.Add("| $($planRows[0].scenario) | $($planRows[0].index_state) | $plan | $filesort | $temporary |")
  }
  $summary.Add('')
}

$summary | Set-Content -LiteralPath $SummaryPath -Encoding utf8
$rows
