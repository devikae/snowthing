// Run against a new disposable schema in the local MySQL container only.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const database = `snowthing_registration_notice_check_${Date.now()}`;
if (!/^snowthing_registration_notice_check_\d+$/.test(database)) throw Error('Disposable schema only');
function sql(statement, useDatabase = true) {
  return execFileSync('docker', ['exec', '-i', 'snowthing-mysql', 'sh', '-c',
    `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names ${useDatabase ? database : ''}`],
    { input: statement, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}
assert.equal(sql(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${database}'`, false), '0');
sql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`, false);
try {
  const directory = path.join(__dirname, '../database/production');
  for (const file of fs.readdirSync(directory).filter(file => /^\d{3}_.*\.sql$/.test(file)).sort()) {
    const version = Number(file.slice(0, 3));
    assert.ok(version <= 18 || (version >= 21 && version <= 26), 'Crew migrations must not enter this release');
    sql(fs.readFileSync(path.join(directory, file), 'utf8'));
  }
  sql("INSERT INTO member(public_id,email,nickname) VALUES(UUID(),'migration-fixture@example.test','migration_fixture')");
  const memberId = sql("SELECT member_id FROM member WHERE email='migration-fixture@example.test'");
  sql(`INSERT INTO member_registration_consent VALUES(${memberId},'fixture-version',TRUE,FALSE,NOW())`);
  sql("INSERT INTO profile_image_cleanup(image_key,image_url,not_before) VALUES('public/profiles/11111111-1111-1111-1111-111111111111.jpg','https://example.test/photo',NOW())");
  for (const file of ['017_migration_registration_consent.sql', '018_migration_notice_category.sql', '021_migration_profile_image_cleanup.sql', '022_migration_shared_request_budget.sql', '023_migration_member_profile_image_url_index.sql', '024_migration_post_image_upload.sql']) {
    sql(fs.readFileSync(path.join(directory, file), 'utf8'));
  }
  assert.equal(sql("SELECT COUNT(*) FROM post_category WHERE code='NOTICE'"), '1');
  assert.equal(sql('SELECT COUNT(*) FROM member_registration_consent'), '1');
  assert.equal(sql('SELECT marketing_email_agreed FROM member_registration_consent'), '0');
  assert.equal(sql('SELECT COUNT(*) FROM profile_image_cleanup'), '1');
  assert.throws(() => sql("INSERT INTO member_registration_consent VALUES(9223372036854775807,'fixture',TRUE,FALSE,NOW())"));
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('crew_post','crew_comment','crew_application','crew_media','crew_visit','crew_resort')"), '0');
  sql(`DELETE FROM member WHERE member_id=${memberId}`);
  assert.equal(sql('SELECT COUNT(*) FROM member_registration_consent'), '0');
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='request_budget_window'"), '1');
  sql(fs.readFileSync(path.join(directory, '025_migration_market_thumbnail.sql'), 'utf8'));
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('post_image_upload','market_thumbnail')"), '2');
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='member' AND index_name='idx_member_profile_image_url'"), '1');
  const key = 'public/posts/originals/m-11111111-1111-1111-1111-111111111111.jpg';
  sql(`INSERT INTO post_image_upload VALUES('${key}','legacy','PENDING',NOW())`);
  sql(`INSERT INTO post(public_id,category_id,title,content,writer_ip,is_anonymous) SELECT UUID(),category_id,'inline fixture','![photo](${key})','127.0.0.1',TRUE FROM post_category WHERE code='FREE'`);
  const postId = sql("SELECT post_id FROM post WHERE title='inline fixture'");
  for (let i = 0; i < 2; i++) sql(fs.readFileSync(path.join(directory, '026_migration_post_content_image_reference.sql'), 'utf8'));
  assert.equal(sql(`SELECT COUNT(*) FROM post_content_image_reference WHERE post_id=${postId} AND image_key='${key}'`), '1');
  assert.equal(sql(`SELECT COUNT(*) FROM post_image WHERE post_id=${postId}`), '0');
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='post_content_image_reference' AND index_name='idx_content_image_key'"), '1');
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='market_thumbnail' AND column_name='attempts'"), '1');
  sql(`DELETE FROM post WHERE post_id=${postId}`);
  assert.equal(sql('SELECT COUNT(*) FROM post_content_image_reference'), '0');
  console.log('001-018 + 021-026 chain, backfill, indexed references, cascade and repeated migrations: passed');
} finally {
  sql(`DROP DATABASE ${database}`, false);
}
