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
    assert.ok(version <= 18, 'Crew migrations must not enter this release');
    sql(fs.readFileSync(path.join(directory, file), 'utf8'));
  }
  sql("INSERT INTO member(public_id,email,nickname) VALUES(UUID(),'migration-fixture@example.test','migration_fixture')");
  const memberId = sql("SELECT member_id FROM member WHERE email='migration-fixture@example.test'");
  sql(`INSERT INTO member_registration_consent VALUES(${memberId},'fixture-version',TRUE,FALSE,NOW())`);
  for (const file of ['017_migration_registration_consent.sql', '018_migration_notice_category.sql']) {
    sql(fs.readFileSync(path.join(directory, file), 'utf8'));
  }
  assert.equal(sql("SELECT COUNT(*) FROM post_category WHERE code='NOTICE'"), '1');
  assert.equal(sql('SELECT COUNT(*) FROM member_registration_consent'), '1');
  assert.equal(sql('SELECT marketing_email_agreed FROM member_registration_consent'), '0');
  assert.throws(() => sql("INSERT INTO member_registration_consent VALUES(9223372036854775807,'fixture',TRUE,FALSE,NOW())"));
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('crew_post','crew_comment','crew_application','crew_media','crew_visit','crew_resort')"), '0');
  sql(`DELETE FROM member WHERE member_id=${memberId}`);
  assert.equal(sql('SELECT COUNT(*) FROM member_registration_consent'), '0');
  console.log('001-018 chain, repeated 017/018, consent preservation, FK cascade and crew exclusion: passed');
} finally {
  sql(`DROP DATABASE ${database}`, false);
}
