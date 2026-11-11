-- 051: 게시글 has_image를 본문 사진까지 반영해 다시 채운다 (IKA-84).
-- 지금까지 has_image는 첨부 사진(post_image)만 보고 켰고, 에디터 본문에 넣은 사진(post_content_image_reference)은 보지 않았다.
-- 그래서 본문에 사진을 붙여 넣은 글이 목록에서 사진 없는 글로 보였다. 애플리케이션은 이제 둘 중 하나라도 있으면 켠다.
-- 한 번만 도는 데이터 보정이다. 이미 켜진 글은 건드리지 않고, 수정 시각(updated_at)은 바꾸지 않는다.
UPDATE post p
SET p.has_image = TRUE
WHERE p.has_image = FALSE
  AND (
      EXISTS (SELECT 1 FROM post_image i WHERE i.post_id = p.post_id)
      OR EXISTS (SELECT 1 FROM post_content_image_reference r WHERE r.post_id = p.post_id)
  );
