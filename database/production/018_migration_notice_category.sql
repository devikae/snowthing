INSERT INTO post_category (name, code)
SELECT '공지사항', 'NOTICE'
WHERE NOT EXISTS (SELECT 1 FROM post_category WHERE code = 'NOTICE');
