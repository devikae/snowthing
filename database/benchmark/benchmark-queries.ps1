function Get-BenchmarkQueryContext {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,
    [Parameter(Mandatory = $true)]
    [scriptblock]$InvokeMySql
  )

  $postId = @(& $InvokeMySql "SELECT p.post_id FROM ``$Schema``.post p JOIN ``$Schema``.comment c ON c.post_id=p.post_id WHERE p.public_id LIKE 'benchmark-sprint04-%' AND c.parent_id IS NULL GROUP BY p.post_id ORDER BY COUNT(*) DESC,p.post_id LIMIT 1")[-1]
  $hotRoot = @(& $InvokeMySql "SELECT r.comment_id FROM ``$Schema``.comment r JOIN ``$Schema``.post p ON p.post_id=r.post_id LEFT JOIN ``$Schema``.comment c ON c.parent_id=r.comment_id WHERE p.public_id LIKE 'benchmark-sprint04-%' AND r.parent_id IS NULL GROUP BY r.comment_id ORDER BY COUNT(c.comment_id) DESC,r.comment_id LIMIT 1")[-1]
  $allRoots = @(& $InvokeMySql "SELECT comment_id FROM ``$Schema``.comment WHERE post_id=$postId AND parent_id IS NULL ORDER BY comment_id")
  $hotReplies = @(& $InvokeMySql "SELECT comment_id FROM ``$Schema``.comment WHERE parent_id=$hotRoot ORDER BY comment_id LIMIT 21")
  if ($allRoots.Count -lt 3 -or $hotReplies.Count -lt 21) { throw "Insufficient cursor data for $Schema" }

  $rootMiddle = $allRoots[[math]::Floor($allRoots.Count / 2) - 1]
  $rootLastIndex = if ($allRoots.Count -ge 22) { $allRoots.Count - 22 } else { [math]::Max(0, $allRoots.Count - 6) }
  $rootLast = $allRoots[$rootLastIndex]
  $replyMiddle = $hotReplies[20]
  $rootIds = ($allRoots | Select-Object -First 20) -join ','
  if ([string]::IsNullOrWhiteSpace($rootIds)) { throw "No root IDs for $Schema" }

  $columns = 'c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url'
  $queries = [ordered]@{
    'root-first' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL ORDER BY c.comment_id ASC LIMIT 21"
    'root-middle' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootMiddle ORDER BY c.comment_id ASC LIMIT 21"
    'root-last' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.comment_id>$rootLast ORDER BY c.comment_id ASC LIMIT 21"
    'reply-stats' = "SELECT parent_id,COUNT(CASE WHEN is_deleted=FALSE THEN 1 END) active_count,COUNT(*) total_count FROM ``$Schema``.comment WHERE parent_id IN ($rootIds) GROUP BY parent_id"
    'reply-top5' = "SELECT r.comment_id,r.post_id,r.parent_id,r.content,r.is_deleted,r.is_anonymous,r.writer_ip,r.created_at,m.public_id member_public_id,m.nickname,m.profile_image_url FROM ``$Schema``.comment root CROSS JOIN LATERAL (SELECT c.comment_id,c.post_id,c.parent_id,c.content,c.is_deleted,c.is_anonymous,c.writer_ip,c.created_at,c.member_id FROM ``$Schema``.comment c WHERE c.parent_id=root.comment_id ORDER BY c.comment_id ASC LIMIT 5) r LEFT JOIN ``$Schema``.member m ON m.member_id=r.member_id WHERE root.comment_id IN ($rootIds) ORDER BY root.comment_id ASC,r.comment_id ASC"
    'reply-hotspot-first' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot ORDER BY c.comment_id ASC LIMIT 21"
    'reply-hotspot-middle' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.parent_id=$hotRoot AND c.comment_id>$replyMiddle ORDER BY c.comment_id ASC LIMIT 21"
    'reply-count-active' = "SELECT COUNT(*) active_reply_count FROM ``$Schema``.comment WHERE parent_id=$hotRoot AND is_deleted=FALSE"
    'deleted-root-placeholder' = "SELECT $columns FROM ``$Schema``.comment c LEFT JOIN ``$Schema``.member m ON m.member_id=c.member_id WHERE c.post_id=$postId AND c.parent_id IS NULL AND c.is_deleted=TRUE ORDER BY c.comment_id ASC LIMIT 21"
  }

  [pscustomobject]@{
    Queries = $queries
    PostId = $postId
    HotRoot = $hotRoot
    RootMiddle = $rootMiddle
    RootLast = $rootLast
    ReplyMiddle = $replyMiddle
    RootIds = $rootIds
  }
}
