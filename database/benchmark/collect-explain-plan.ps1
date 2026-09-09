param(
  [string]$OutputPath = 'docs/study/sprint04/comment/benchmark/explain-plan-details.csv',
  [string]$SummaryPath = 'docs/study/sprint04/comment/benchmark/explain-plan-table.generated.md'
)

$ErrorActionPreference = 'Stop'
$schemas = [ordered]@{
  '1k' = 'snowthing_benchmark_1k'
  '10k' = 'snowthing_benchmark_10k'
  '100k' = 'snowthing_benchmark_100k'
}
$indexes = @('idx_comment_post_parent_id', 'idx_comment_parent_deleted_id')

function Invoke-MySql([string]$Sql) {
  $result = $Sql | docker exec -i -e MYSQL_PWD='snowthing_pass_2026!' snowthing-mysql mysql -u snowuser -B -N 2>&1
  if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
  return @($result)
}

function Set-IndexVisibility([string]$Schema, [string]$Visibility) {
  foreach ($index in $indexes) {
    Invoke-MySql "ALTER TABLE ``$Schema``.comment ALTER INDEX ``$index`` $Visibility" | Out-Null
  }
}

function Get-Queries([string]$Schema) {
  $bindingSql = @"
USE ``$Schema``;
SET @post_id=(SELECT post_id FROM post WHERE public_id='benchmark-sprint04-post-0');
SET @hot_root=(SELECT r.comment_id FROM comment r JOIN post p ON p.post_id=r.post_id LEFT JOIN comment c ON c.parent_id=r.comment_id WHERE p.public_id LIKE 'benchmark-sprint04-%' AND r.parent_id IS NULL GROUP BY r.comment_id ORDER BY COUNT(c.comment_id) DESC,r.comment_id LIMIT 1);
SET @reply_middle=(SELECT comment_id FROM comment WHERE parent_id=@hot_root ORDER BY comment_id LIMIT 20,1);
SELECT @post_id,@hot_root,@reply_middle;
"@
  $bindings = @((Invoke-MySql $bindingSql))[-1] -split "`t"
  if ($bindings.Count -ne 3 -or $bindings -contains 'NULL') { throw "Invalid bindings for $Schema" }
  $postId,$hotRoot,$replyMiddle = $bindings
  $rootCount = [long]@((Invoke-MySql "SELECT COUNT(*) FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL"))[-1]
  $rootMiddleOffset = [math]::Max(0, [math]::Floor($rootCount / 2) - 1)
  $rootLastOffset = [math]::Max(0, $rootCount - 22)
  $rootMiddle = @((Invoke-MySql "SELECT comment_id FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL ORDER BY comment_id LIMIT $rootMiddleOffset,1"))[-1]
  $rootLast = @((Invoke-MySql "SELECT comment_id FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL ORDER BY comment_id LIMIT $rootLastOffset,1"))[-1]
  $rootIds = (Invoke-MySql "SELECT comment_id FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL ORDER BY comment_id LIMIT 20") -join ','
  $columns = 'c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url'

  return [ordered]@{
    'root-first' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL ORDER BY c.comment_id ASC LIMIT 21"
    'root-middle' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootMiddle ORDER BY c.comment_id ASC LIMIT 21"
    'root-last' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootLast ORDER BY c.comment_id ASC LIMIT 21"
    'reply-top5' = "SELECT r.comment_id,r.post_id,r.parent_id,r.content,r.is_deleted,r.is_anonymous,r.writer_ip,r.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url FROM ``$Schema``.comment root CROSS JOIN LATERAL (SELECT c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,c.member_id FROM ``$Schema``.comment c WHERE c.parent_id=root.comment_id ORDER BY c.comment_id ASC LIMIT 5) r LEFT JOIN ``$Schema``.member m ON m.member_id=r.member_id WHERE root.comment_id IN ($rootIds) ORDER BY root.comment_id ASC,r.comment_id ASC"
    'reply-hotspot-first' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot ORDER BY c.comment_id ASC LIMIT 21"
    'reply-hotspot-middle' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot AND c.comment_id>$replyMiddle ORDER BY c.comment_id ASC LIMIT 21"
    'reply-count-active' = "SELECT COUNT(*) active_reply_count FROM ``$Schema``.comment WHERE parent_id=$hotRoot AND is_deleted=FALSE"
    'deleted-root-placeholder' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_ID=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.is_deleted=TRUE ORDER BY c.comment_id ASC LIMIT 21"
    'deleted-reply-hidden' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot AND c.is_deleted=FALSE ORDER BY c.comment_id ASC LIMIT 21"
  }
}

$rows = [System.Collections.Generic.List[object]]::new()
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
finally {
  foreach ($schema in $schemas.Values) { Set-IndexVisibility $schema 'VISIBLE' }
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
