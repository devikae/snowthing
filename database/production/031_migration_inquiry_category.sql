INSERT INTO post_category (name, code)
SELECT '1:1 문의', 'INQUIRY'
WHERE NOT EXISTS (SELECT 1 FROM post_category WHERE code = 'INQUIRY');
