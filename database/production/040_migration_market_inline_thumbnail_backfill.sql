-- IKA-36: 본문에 base64 사진만 있는 기존 매물의 썸네일 작업을 넣는다. 스키마와 본문은 바꾸지 않는다.
-- 앱의 MarketThumbnailService가 작업을 읽어 S3에 썸네일을 만들므로, 이 파일은 IKA-36 백엔드 배포 후에 실행한다.
-- 이전 버전 앱이 먼저 읽으면 inline: 값을 잘못된 원본 키로 보고 작업을 멈춘다. 그러면 썸네일이 만들어지지 않는다.
-- 이미 작업에 사진이 잡힌 매물은 건드리지 않으므로 다시 실행해도 된다.
INSERT INTO market_thumbnail (post_public_id, source_key, revision, attempts, due_at)
SELECT p.public_id, 'inline:backfill', 0, 0, '2000-01-01 00:00:00'
FROM market_listing m
JOIN post p ON p.post_id = m.post_id
WHERE p.is_deleted = 0
  AND p.content LIKE '%data:image/%;base64,%'
  AND p.content NOT LIKE '%public/posts/%'
  AND NOT EXISTS (SELECT 1 FROM post_image i WHERE i.post_id = p.post_id)
  AND NOT EXISTS (
      SELECT 1 FROM market_thumbnail t
      WHERE t.post_public_id = p.public_id AND t.source_key IS NOT NULL
  )
ON DUPLICATE KEY UPDATE
    source_key = VALUES(source_key),
    revision = revision + 1,
    attempts = 0,
    due_at = VALUES(due_at);
