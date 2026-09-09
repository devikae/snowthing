param(
  [string]$Scale = '1m',
  [string]$Schema = 'snowthing_test',
  [string]$ExplainDirectory = 'docs/study/sprint04/comment/benchmark/explain'
)

$ErrorActionPreference = 'Stop'

function Invoke-MySql([string]$Sql) {
  $result = $Sql | docker exec -i -e MYSQL_PWD='snowthing_pass_2026!' snowthing-mysql mysql -u snowuser -N 2>&1
  if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
  return @($result)
}

$postId = @(Invoke-MySql "SELECT p.post_id FROM ``$Schema``.post p JOIN ``$Schema``.comment c ON c.post_id=p.post_id WHERE p.public_id LIKE 'benchmark-sprint04-%' AND c.parent_id IS NULL GROUP BY p.post_id ORDER BY COUNT(*) DESC,p.post_id LIMIT 1")[-1]
$hotRoot = @(Invoke-MySql "SELECT r.comment_id FROM ``$Schema``.comment r JOIN ``$Schema``.post p ON p.post_id=r.post_id LEFT JOIN ``$Schema``.comment c ON c.parent_id=r.comment_id WHERE p.public_id LIKE 'benchmark-sprint04-%' AND r.parent_id IS NULL GROUP BY r.comment_id ORDER BY COUNT(c.comment_id) DESC,r.comment_id LIMIT 1")[-1]
$allRoots = @(Invoke-MySql "SELECT comment_id FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL ORDER BY comment_id")
$hotReplies = @(Invoke-MySql "SELECT comment_id FROM ``$Schema``.comment WHERE parent_id=$hotRoot ORDER BY comment_id LIMIT 21")
if ($allRoots.Count -lt 3 -or $hotReplies.Count -lt 21) { throw "Insufficient cursor data for $Schema" }
$rootMiddle = $allRoots[[math]::Floor($allRoots.Count / 2) - 1]
$rootLastIndex = if ($allRoots.Count -ge 22) { $allRoots.Count - 22 } else { [math]::Max(0, $allRoots.Count - 6) }
$rootLast = $allRoots[$rootLastIndex]
$replyMiddle = $hotReplies[20]
$rootIds = ($allRoots | Select-Object -First 20) -join ','
if ([string]::IsNullOrWhiteSpace($rootIds)) { throw "No root IDs for $Schema" }

$responseColumns = "c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url"
$queries = [ordered]@{
  'root-first' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL ORDER BY c.comment_id ASC LIMIT 21"
  'root-middle' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootMiddle ORDER BY c.comment_id ASC LIMIT 21"
  'root-last' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootLast ORDER BY c.comment_id ASC LIMIT 21"
  'reply-stats' = "SELECT parent_id,COUNT(CASE WHEN is_deleted=FALSE THEN 1 END) active_count,COUNT(*) total_count FROM ``$Schema``.comment WHERE parent_id IN ($rootIds) GROUP BY parent_id"
  'reply-top5' = "SELECT r.comment_id,r.post_id,r.parent_id,r.content,r.is_deleted,r.is_anonymous,r.writer_ip,r.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url FROM ``$Schema``.comment root CROSS JOIN LATERAL (SELECT c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,c.member_id FROM ``$Schema``.comment c WHERE c.parent_id=root.comment_id ORDER BY c.comment_id ASC LIMIT 5) r LEFT JOIN ``$Schema``.member m ON m.member_id=r.member_id WHERE root.comment_id IN ($rootIds) ORDER BY root.comment_id ASC,r.comment_id ASC"
  'reply-hotspot-first' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot ORDER BY c.comment_id ASC LIMIT 21"
  'reply-hotspot-middle' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot AND c.comment_id>$replyMiddle ORDER BY c.comment_id ASC LIMIT 21"
  'reply-count-active' = "SELECT COUNT(*) active_reply_count FROM ``$Schema``.comment WHERE parent_id=$hotRoot AND is_deleted=FALSE"
  'deleted-root-placeholder' = "SELECT $responseColumns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.is_deleted=TRUE ORDER BY c.comment_id ASC LIMIT 21"
}

New-Item -ItemType Directory -Force -Path $ExplainDirectory | Out-Null
$results = foreach ($entry in $queries.GetEnumerator()) {
  $name = $entry.Key
  $query = $entry.Value
  $explain = Invoke-MySql "EXPLAIN ANALYZE $query"
  @("schema=$Schema", "scale=$Scale", "postId=$postId", "hotRootId=$hotRoot", "rootMiddleCursor=$rootMiddle", "rootLastCursor=$rootLast", "replyMiddleCursor=$replyMiddle", "rootIds=$rootIds", "sql=$query", '') + $explain |
    Set-Content -LiteralPath (Join-Path $ExplainDirectory "$name-$Scale.txt") -Encoding utf8

  $batch = ""
  for ($i=0; $i -lt 25; $i++) {
    $batch += "SET @t=NOW(6); SELECT COUNT(*) FROM ($query) measured; SELECT 'duration_us',TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`n"
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
