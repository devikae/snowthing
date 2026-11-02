// Inspect server HTML without executing browser JavaScript or signing in.
const assert = require('node:assert/strict');
const {localPage,canCrawl} = require('./lib/seo-validation.cjs');
const base = process.env.SEO_CHECK_URL || 'http://localhost:3000';
if (new URL(base).hostname !== 'localhost') throw Error('This check is local-only');
const origin = 'https://snowthing.org';
async function page(path) {
  return localPage(base,path);
}
function meta(html,name) {
  const tags=html.match(/<meta\b[^>]*>/gi)||[];
  return tags.find(tag=>tag.includes(`name="${name}"`)||tag.includes(`property="${name}"`))?.match(/content="([^"]*)"/)?.[1];
}
function canonical(html) { const value=html.match(/<link\b[^>]*rel="canonical"[^>]*href="([^"]+)"/)?.[1]; return value ? new URL(value).href : undefined; }
(async()=>{
  const home=await page('/');assert.equal(home.status,200);assert.match(home.html,/<title>눈팅 SnowThing/);
  const schemas=[...home.html.matchAll(/<script[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)].map(match=>JSON.parse(match[1]));
  const website=schemas.filter(schema=>schema['@type']==='WebSite');assert.equal(website.length,1);assert.equal(website[0].name,'눈팅');assert.ok(website[0].alternateName.includes('SnowThing'));assert.equal(website[0].url,origin+'/');assert.equal(canonical(home.html),origin+'/');assert.equal(meta(home.html,'og:site_name'),'눈팅');
  const index=await page('/resort-cam');assert.equal(index.status,200);assert.match(index.html,/<h1[^>]*>스키장 웹캠<\/h1>/);assert.match(index.html,/<title>스키장 웹캠/);assert.ok(index.html.includes('곤지암리조트'));assert.ok(!index.html.includes('리조트캠으로 이동 중입니다.'));assert.equal(canonical(index.html),origin+'/resort-cam');
  const map=await page('/sitemap.xml');assert.equal(map.status,200);const urls=[...map.html.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match=>match[1]);assert.equal(new Set(urls).size,urls.length);assert.ok(urls.includes(origin+'/'));assert.ok(urls.includes(origin+'/resort-cam'));assert.ok(!urls.some(url=>/\/crews|\/profile|\/login|\/market|\/new|\/edit/.test(url)));
  const resortUrls=urls.filter(url=>/\/resort-cam\/[^/]+$/.test(url));assert.ok(resortUrls.length>=13);
  const titles=new Set();for(const url of resortUrls){const path=new URL(url).pathname;const result=await page(path);assert.equal(result.status,200,path);const title=result.html.match(/<title>([^<]+)<\/title>/)?.[1];assert.match(title,/웹캠/);assert.match(title,/눈팅 SnowThing/);assert.ok(!titles.has(title));titles.add(title);assert.equal(canonical(result.html),url,path);assert.ok(meta(result.html,'description')?.includes('웹캠'));}
  const camera=await page('/resort-cam/konjiam/cam-01');assert.equal(camera.status,200);assert.equal(canonical(camera.html),origin+'/resort-cam/konjiam/cam-01');assert.match(camera.html,/<title>[^<]*웹캠/);
  for(const route of ['/login','/signup','/profile','/forgot-password','/market','/posts/create','/carpool/new']){const result=await page(route);assert.ok(meta(result.html,'robots')?.includes('noindex'),route);}
  assert.equal((await page('/crews')).status,404,'Crew feature is excluded from this release');
  assert.ok(!home.html.includes('href="/crews"'),'Crew navigation is excluded');
  const invalid=await page('/resort-cam/does-not-exist');assert.ok(invalid.status===404||meta(invalid.html,'robots')?.includes('noindex'));
  const robots=await page('/robots.txt');assert.equal(robots.status,200);assert.ok(robots.html.includes('Sitemap: '+origin+'/sitemap.xml'));assert.ok(!robots.html.includes('Disallow: /api/'),'Public rendering data must stay crawlable');
  for(const path of ['/','/posts','/resort-cam','/tools','/api/v1/master/resorts','/api/v1/posts','/_next/static/app.js']) {
    assert.ok(canCrawl(robots.html,path),`Public crawl path is blocked: ${path}`);
  }
  console.log(JSON.stringify({status:'passed',publicSitemapUrls:urls.length,resortMetadata:titles.size,checks:['Korean site name in server HTML','WebSite JSON-LD','canonical URLs','webcam content without JS','unique resort titles','camera metadata','private page noindex','robots sitemap link','invalid resort excluded']}));
})().catch(error=>{console.error(error);process.exitCode=1;});
