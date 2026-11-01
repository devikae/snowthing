const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('../frontend/node_modules/typescript');
function loadTool(file) {
  const source = fs.readFileSync(path.join(__dirname, '../frontend/app/lib', file), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const context = { exports: {}, URLSearchParams, URL };
  vm.runInNewContext(compiled, context);
  return context.exports;
}
const { calculateSeasonPass, parseSeasonPassSearch, seasonPassSearch } = loadTool('seasonPass.ts');
const { localChatCommand } = loadTool('chatCommands.ts');
test('chat commands match only exact local shortcuts and never classify ordinary conversation', () => {
  assert.equal(localChatCommand(' /도움말 '), 'help');
  assert.equal(localChatCommand('/help'), 'help');
  assert.equal(localChatCommand('/시즌권'), 'season-pass');
  assert.equal(localChatCommand('/데크'), 'deck-size');
  for (const content of ['시즌권 얼마예요?', '/시즌권 50만원', '@bot 데크 추천', '', '/unknown']) {
    assert.equal(localChatCommand(content), null);
  }
});
const baseline = { passPrice: 500000, ticketPrice: 50000, passTripCost: 10000, ticketTripCost: 10000, visited: 3, planned: 20 };

test('season pass includes recurring costs and breaks even on the first tied visit', () => {
  const result = calculateSeasonPass(baseline);
  assert.equal(result.breakEvenVisits, 10);
  assert.equal(result.remainingVisits, 7);
  assert.equal(result.planned.passTotal, 700000);
  assert.equal(result.planned.ticketsTotal, 1200000);
  assert.equal(result.planned.saving, 500000);
});
test('non-divisible cost rounds upward and zero visits have no invented average', () => {
  const result = calculateSeasonPass({ ...baseline, passPrice: 500001, visited: 0 });
  assert.equal(result.breakEvenVisits, 11);
  assert.equal(result.current.costPerVisit, null);
});
test('season pass cannot recover cost when recurring costs erase savings', () => {
  assert.equal(calculateSeasonPass({ ...baseline, passTripCost: 60000 }).breakEvenVisits, null);
  assert.equal(calculateSeasonPass({ ...baseline, passTripCost: 70000 }).breakEvenVisits, null);
});
test('a free pass with higher trip costs is not claimed to stay beneficial', () => {
  const result = calculateSeasonPass({ ...baseline, passPrice: 0, passTripCost: 70000 });
  assert.equal(result.breakEvenVisits, 0);
  assert.equal(result.staysBeneficial, false);
  assert.ok(result.planned.saving < 0);
});
test('rejects invalid numbers, decimals, empty fields, and reversed visit counts', () => {
  for (const passPrice of [NaN, Infinity, -1, 100000001, 5.1]) assert.equal(calculateSeasonPass({ ...baseline, passPrice }).valid, false);
  assert.equal(calculateSeasonPass({ ...baseline, visited: 21 }).valid, false);
  assert.equal(calculateSeasonPass({ ...baseline, planned: 731 }).valid, false);
});
test('share links round-trip valid inputs and reject malformed or incomplete values', () => {
  assert.equal(JSON.stringify(parseSeasonPassSearch(seasonPassSearch(baseline))), JSON.stringify(baseline));
  assert.equal(parseSeasonPassSearch('passPrice=500000'), null);
  assert.equal(parseSeasonPassSearch(seasonPassSearch(baseline) + '&passPrice=1'), null);
  assert.equal(parseSeasonPassSearch(seasonPassSearch(baseline).replace('500000', '1e6')), null);
  assert.equal(parseSeasonPassSearch(seasonPassSearch(baseline).replace('500000', '-1')), null);
});

const { calculateDeckSize, deckLengthBands, checkModelWeight, deckSearch, parseDeckSearch, deckQuestionDraft } = loadTool('deckSize.ts');
const rider = { weight: 75, bootMm: 280 };

test('Korean millimeter sizing is preserved in calculation, sharing, and question drafts', () => {
  const input = { ...rider, bootMm: 270 };
  assert.equal(calculateDeckSize(input).valid, true);
  assert.equal(calculateDeckSize(input).minimumWaistMm, null);
  assert.ok(deckSearch(input).includes('bootMm=270'));
  assert.ok(!deckSearch(input).includes('bootUS'));
  assert.ok(!deckSearch(input).includes('level'));
  assert.ok(!deckSearch(input).includes('style'));
  assert.equal(parseDeckSearch(deckSearch(input)).bootMm, 270);
  assert.ok(deckQuestionDraft(input, '152~159cm').includes('부츠: 270mm'));
  assert.ok(!deckQuestionDraft(input, '152~159cm').includes('실력:'));
  assert.equal(calculateDeckSize({ ...rider, bootMm: 285 }).minimumWaistMm, 261);
  assert.equal(calculateDeckSize({ ...rider, bootMm: 305 }).minimumWaistMm, 271);
  for (const bootMm of [NaN, Infinity, 200, 345, 271, 270.5]) {
    assert.equal(calculateDeckSize({ ...rider, bootMm }).valid, false);
  }
});

test('legacy US links restore millimeter inputs and ambiguous units are rejected', () => {
  assert.equal(parseDeckSearch('weight=75&bootUS=9&style=allround&level=intermediate').bootMm, 270);
  assert.equal(parseDeckSearch('weight=75&bootUS=10&style=allround&level=intermediate').bootMm, 280);
  for (const suffix of ['bootMm=270&bootUS=9', 'bootMm=270&bootMm=275', 'bootMm=270.5', 'bootMm=2.7e2']) {
    assert.equal(parseDeckSearch(`weight=75&${suffix}&style=allround&level=intermediate`), null);
  }
});
test('deck reference includes all overlapping manufacturer weight rows', () => {
  const result = calculateDeckSize(rider);
  assert.equal(result.minimum, 152); assert.equal(result.maximum, 159);
  assert.equal(result.rowsMatched, 2); assert.equal(result.minimumWaistMm, 259);
});
test('one result presents short, balanced, and long ranges without a style input', () => {
  const bands = calculateDeckSize(rider).bands;
  assert.equal(JSON.stringify(bands), JSON.stringify([
    { position: 'short', minimum: 152, maximum: 153 },
    { position: 'middle', minimum: 154, maximum: 157 },
    { position: 'long', minimum: 158, maximum: 159 },
  ]));
  const legacy = parseDeckSearch('weight=75&bootMm=280&style=riding&level=beginner');
  assert.equal(Object.hasOwn(legacy, 'level'), false);
  assert.equal(Object.hasOwn(legacy, 'style'), false);
  assert.equal(JSON.stringify(calculateDeckSize(legacy).bands), JSON.stringify(bands));
});

test('length guide covers bounded results without overlaps and never fabricates an open upper limit', () => {
  for (let weight=34; weight<=200; weight++) {
    const result = calculateDeckSize({ ...rider, weight });
    if (result.maximum===null) { assert.equal(result.bands.length,0); continue; }
    const lengths=result.bands.flatMap(band=>Array.from({length:band.maximum-band.minimum+1},(_,i)=>band.minimum+i));
    assert.equal(lengths[0],result.minimum);
    assert.equal(lengths.at(-1),result.maximum);
    assert.equal(lengths.length,result.maximum-result.minimum+1);
    assert.equal(new Set(lengths).size,lengths.length);
  }
  for (const bounds of [[165,null],[152,153],[153,152],[NaN,159]]) assert.equal(deckLengthBands(...bounds).length,0);
});
test('preserves open-ended manufacturer bounds and documents rounded lookup weight', () => {
  const result = calculateDeckSize({ ...rider, weight: 104.5, bootMm: 330 });
  assert.equal(result.lookupWeight, 105); assert.equal(result.minimum, 165); assert.equal(result.maximum, null);
  assert.equal(result.minimumWaistMm, 278); assert.equal(result.widthIsOpenEnded, true);
  assert.equal(calculateDeckSize({ ...rider, bootMm: 275 }).minimumWaistMm, null);
});
test('rejects ungrounded out-of-chart weights and invalid shared input', () => {
  for (const weight of [NaN, Infinity, 0, 33, 201]) assert.equal(calculateDeckSize({ ...rider, weight }).valid, false);
  assert.equal(calculateDeckSize({ ...rider, bootMm: 272 }).valid, false);
  assert.equal(JSON.stringify(parseDeckSearch(deckSearch(rider))), JSON.stringify(rider));
  assert.equal(parseDeckSearch(deckSearch(rider) + '&weight=60'), null);
  assert.equal(parseDeckSearch('weight=oops&bootMm=280'), null);
});
test('model-specific weight fit is separate from generic length guidance', () => {
  assert.equal(checkModelWeight(75, 54, 77).withinRange, true);
  assert.equal(checkModelWeight(80, 54, 77).position, 'above');
  assert.equal(checkModelWeight(50, 54, 77).position, 'below');
  assert.equal(checkModelWeight(75, 80, 70).valid, false);
  assert.ok(deckQuestionDraft(rider, '152~159cm').includes('75kg'));
});
const { parseToolQuestionDraft } = loadTool('toolQuestionDraft.ts');
test('question drafts expire and malformed session data is ignored', () => {
  const now = 1793490000000;
  const draft = { title: '질문', content: '조건', savedAt: now };
  assert.equal(parseToolQuestionDraft(JSON.stringify(draft), now).title, '질문');
  assert.equal(parseToolQuestionDraft(JSON.stringify({ ...draft, savedAt: now - 3600001 }), now), null);
  assert.equal(parseToolQuestionDraft('{', now), null);
  assert.equal(parseToolQuestionDraft(JSON.stringify({ ...draft, content: 1 }), now), null);
});

const { safeReturnUrl } = loadTool('safeReturnUrl.ts');
test('login return paths preserve tool drafts and reject external and backslash redirects', () => {
  assert.equal(safeReturnUrl('/posts/create?category=QNA&draft=deck'), '/posts/create?category=QNA&draft=deck');
  for (const candidate of [null, 'https://evil.example', '//evil.example', '/\\evil.example', '/\nevil.example', 'javascript:alert(1)']) assert.equal(safeReturnUrl(candidate), '/');
});
