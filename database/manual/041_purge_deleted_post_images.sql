-- IKA-41: 이 변경 전에 이미 삭제된 글의 사진을 정리하는 수동 절차.
--
-- 이 파일은 자동으로 실행되지 않는다. database/production 밖에 둔 이유는 그 폴더가 앱 교체 전에 자동 실행되기 때문이다.
-- 아래 조회는 읽기 전용이다. 실제 삭제는 관리자 API가 하며, 파일 삭제는 되돌릴 수 없다.
--
-- 왜 SQL로 지우지 않는가:
--   본문에만 있는 옛 사진, 다른 글과 공유하는 사진, 장터 썸네일, 감사 기록을 API가 한곳에서 처리한다.
--   SQL로 따로 지우면 살아 있는 글이 쓰는 파일까지 지울 수 있다.
--
-- 절차:
--   1. DB 백업이 있는지 확인한다.
--   2. 아래 1번 조회로 대상 글을 뽑고 사람이 검토한다.
--   3. 글마다 관리자 세션으로 호출한다. CSRF 토큰이 필요하다.
--        POST /api/v1/admin/posts/{public_id}/images/purge
--        {"reason": "IKA-41 이전 삭제 글 정리"}
--      응답의 queuedKeys는 약 1분 뒤 지워지고, sharedKeys는 다른 글이 쓰고 있어 남는다.
--   4. 몇 분 뒤 아래 2번 조회로 남은 작업이 없는지 확인한다.
--   5. 불법촬영물처럼 바로 막아야 하면 응답의 키를 CloudFront에서 무효화한다.
--      새 사진은 CDN에 최대 1시간, 옛 사진은 최대 1년 캐시될 수 있다.
--        aws cloudfront create-invalidation --distribution-id <배포 ID> --paths "/<키>"

-- 1. 대상 글: 삭제되었고 사진 참조나 본문 사진이 남아 있는 글
SELECT p.public_id, p.deleted_at
FROM post p
WHERE p.is_deleted = true
  AND (
      EXISTS (SELECT 1 FROM post_image pi WHERE pi.post_id = p.post_id)
      OR EXISTS (SELECT 1 FROM post_content_image_reference r WHERE r.post_id = p.post_id)
      OR p.content LIKE '%public/posts/%'
  )
ORDER BY p.deleted_at ASC, p.post_id ASC;

-- 2. 남은 작업: 0건이어야 한다. 남아 있으면 S3 삭제가 재시도 중이므로 앱 로그를 본다.
SELECT state, COUNT(*) AS files
FROM post_image_upload
WHERE state = 'DELETING'
   OR (state = 'PENDING' AND due_at <= UTC_TIMESTAMP(6))
GROUP BY state;
