param(
  [string]$Scale = '1m',
  [string]$Schema = 'snowthing_test',
  [string]$ExplainDirectory = 'docs/study/sprint04/comment/benchmark/explain'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'benchmark-queries.ps1')
$dbUsername = $env:SNOWTHING_DB_USERNAME
$dbPassword = $env:SNOWTHING_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($dbUsername) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
  Write-Error 'SNOWTHING_DB_USERNAME and SNOWTHING_DB_PASSWORD are required.'
  exit 1
}

function Invoke-MySql([string]$Sql) {
  $previousMySqlPwd = $env:MYSQL_PWD
  try {
    $env:MYSQL_PWD = $dbPassword
    $result = $Sql | docker exec -i -e MYSQL_PWD snowthing-mysql mysql -u $dbUsername -N 2>&1
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

$queryContext = Get-BenchmarkQueryContext -Schema $Schema -InvokeMySql ${function:Invoke-MySql}
$queries = $queryContext.Queries
$postId = $queryContext.PostId
$hotRoot = $queryContext.HotRoot
$rootMiddle = $queryContext.RootMiddle
$rootLast = $queryContext.RootLast
$replyMiddle = $queryContext.ReplyMiddle
$rootIds = $queryContext.RootIds

New-Item -ItemType Directory -Force -Path $ExplainDirectory | Out-Null
$results = foreach ($entry in $queries.GetEnumerator()) {
  $name = $entry.Key
  $query = $entry.Value
  $explain = Invoke-MySql "EXPLAIN ANALYZE $query"
  @("schema=$Schema", "scale=$Scale", "postId=$postId", "hotRootId=$hotRoot", "rootMiddleCursor=$rootMiddle", "rootLastCursor=$rootLast", "replyMiddleCursor=$replyMiddle", "rootIds=$rootIds", "sql=$query", '') + $explain |
    Set-Content -LiteralPath (Join-Path $ExplainDirectory "$name-$Scale.txt") -Encoding utf8

  $batch = ""
  for ($i=0; $i -lt 25; $i++) {
    $batch += "SET @t=NOW(6); $query; SELECT 'duration_us',TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`n"
  }
  $times = @((Invoke-MySql $batch) | Where-Object { $_ -match '^duration_us\s+([0-9]+)$' } | ForEach-Object { [double]$Matches[1] / 1000 }) | Select-Object -Last 20
  if ($times.Count -ne 20) { throw "Expected 20 measurements for $name, got $($times.Count)" }
  $sorted = @($times | Sort-Object)
  [pscustomobject]@{
    scale = $Scale
    query = $name
    avg_ms = [math]::Round(($times | Measure-Object -Average).Average, 3)
    p95_ms = [math]::Round($sorted[18], 3)
  }
}

$results
