SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS assert_benchmark_schema;
DELIMITER $$
CREATE PROCEDURE assert_benchmark_schema()
BEGIN
  IF DATABASE() IS NULL
     OR (DATABASE() NOT LIKE '%test%' AND DATABASE() NOT LIKE '%benchmark%') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Benchmark seed is allowed only on a test or benchmark schema';
  END IF;
END$$
DELIMITER ;
CALL assert_benchmark_schema();
DROP PROCEDURE assert_benchmark_schema;
SET FOREIGN_KEY_CHECKS = 0;
SET @seed = COALESCE(@seed, 20260907);
SET @prefix = 'benchmark-sprint04-';

DELETE c FROM comment c JOIN comment parent ON parent.comment_id = c.parent_id JOIN post p ON p.post_id = parent.post_id WHERE p.public_id LIKE CONCAT(@prefix, '%');
DELETE c FROM comment c JOIN post p ON p.post_id = c.post_id WHERE p.public_id LIKE CONCAT(@prefix, '%');
DELETE FROM post WHERE public_id LIKE CONCAT(@prefix, '%');
DELETE FROM member WHERE public_id = CONCAT(@prefix, 'member');
SET FOREIGN_KEY_CHECKS = 1;
INSERT INTO post_category (name, code) VALUES ('자유게시판', 'FREE') ON DUPLICATE KEY UPDATE category_id = category_id;
INSERT INTO member (public_id,email,password,nickname,role,status,created_at,updated_at)
VALUES (CONCAT(@prefix,'member'),CONCAT(@prefix,'member@snowthing.test'),'benchmark-password-hash','벤치마크회원','ROLE_USER','ACTIVE',NOW(),NOW());
SET @member_id = (SELECT member_id FROM member WHERE public_id = CONCAT(@prefix,'member'));
SET @category_id = (SELECT category_id FROM post_category WHERE code = 'FREE' LIMIT 1);

DROP TEMPORARY TABLE IF EXISTS benchmark_posts;
CREATE TEMPORARY TABLE benchmark_posts (seq INT PRIMARY KEY, post_id BIGINT UNIQUE);
CREATE TEMPORARY TABLE benchmark_roots (seq INT PRIMARY KEY, comment_id BIGINT UNIQUE, post_id BIGINT);
DROP PROCEDURE IF EXISTS seed_benchmark;
DELIMITER $$
CREATE PROCEDURE seed_benchmark()
BEGIN
SET @i = 0;
WHILE @i < 100 DO
  INSERT INTO post (public_id,member_id,category_id,title,content,writer_ip,is_anonymous,view_count,comment_count,like_count,dislike_count,has_image,status,is_deleted,created_at,updated_at)
  VALUES (CONCAT(@prefix,'post-',@i),@member_id,@category_id,
    CONCAT('스키장 ', ELT(1+MOD(@i,5),'용평','휘닉스','하이원','곤지암','무주'),' 설질과 리프트 이용 후기 ',@i),
    CONCAT('이번 방문에서 설질과 리프트 대기 시간을 직접 확인했습니다. 방문 예정인 분들께 도움이 되길 바랍니다. 후기 번호 ',@i),
    '127.0.0.1',MOD(@i,10)=0,0,0,0,0,FALSE,'NORMAL',FALSE,NOW(),NOW());
  INSERT INTO benchmark_posts (seq, post_id) VALUES (@i, LAST_INSERT_ID());
  SET @i = @i + 1;
END WHILE;

SET @root_count = GREATEST(100, FLOOR(@target_comments / 5));
SET @i = 0;
WHILE @i < @root_count DO
  SET @post_offset = CASE
    WHEN @i < FLOOR(@root_count * 0.45) THEN MOD(@i,80)
    WHEN @i < FLOOR(@root_count * 0.90) THEN 80 + MOD(@i,19)
    ELSE 99
  END;
  SET @new_post_id = (SELECT post_id FROM benchmark_posts WHERE seq = @post_offset);
  INSERT INTO comment (post_id,member_id,parent_id,content,writer_ip,is_anonymous,is_deleted,`version`,created_at,updated_at)
  VALUES (@new_post_id,@member_id,NULL,
    CONCAT(ELT(1+MOD(@i,4),'현장 정보 감사합니다','이번 주말 방문 예정이라 참고하겠습니다','설질이 좋아 보이네요','저도 비슷하게 느꼈습니다'),' (댓글 ',@i,') [benchmark-sprint04-root-',@i,']'),
    '127.0.0.1',MOD(@i,10)=0,MOD(@i,5)=0,0,NOW(),NOW());
  SET @new_comment_id = LAST_INSERT_ID();
  INSERT INTO benchmark_roots (seq, comment_id, post_id) VALUES (@i, @new_comment_id, @new_post_id);
  SET @i = @i + 1;
END WHILE;

SET @reply_count = @target_comments - @root_count;
SET @hotspot_reply_count = LEAST(100, @reply_count);
SET @i = 0;
WHILE @i < @reply_count DO
  SET @root_offset = CASE WHEN @i < @hotspot_reply_count THEN 0 ELSE MOD(@i,@root_count) END;
  INSERT INTO comment (post_id,member_id,parent_id,content,writer_ip,is_anonymous,is_deleted,`version`,created_at,updated_at)
  SELECT r.post_id,@member_id,r.comment_id,
    CONCAT(ELT(1+MOD(@i,4),'저도 같은 경험이었어요','오전에는 대기가 짧았습니다','도움 되는 정보 감사합니다','정상 쪽이 더 좋았습니다'),' (답글 ',@i,') [benchmark-sprint04-reply-',@i,']'),
    '127.0.0.1',MOD(@i,10)=0,(@i >= @hotspot_reply_count AND MOD(@i,5)=0),0,NOW(),NOW()
  FROM benchmark_roots r WHERE r.seq = @root_offset;
  -- The selected root is deterministic and spreads replies across all roots.
  SET @i = @i + 1;
END WHILE;

UPDATE post p SET comment_count=(SELECT COUNT(*) FROM comment c WHERE c.post_id=p.post_id AND c.is_deleted=FALSE)
WHERE p.public_id LIKE CONCAT(@prefix,'%');
SELECT p.public_id, COUNT(c.comment_id) comment_count
FROM post p LEFT JOIN comment c ON c.post_id=p.post_id
WHERE p.public_id LIKE CONCAT(@prefix,'%')
GROUP BY p.public_id ORDER BY p.public_id;
END$$
DELIMITER ;
CALL seed_benchmark();
DROP PROCEDURE seed_benchmark;
SELECT COUNT(*) total_comments, SUM(c.parent_id IS NULL) roots, SUM(c.parent_id IS NOT NULL) replies,
       SUM(c.is_deleted=TRUE) deleted FROM comment c JOIN post p ON p.post_id=c.post_id
WHERE p.public_id LIKE CONCAT(@prefix,'%');
