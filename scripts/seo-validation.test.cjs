const test=require('node:test'),assert=require('node:assert/strict');
const {localPage,canCrawl}=require('./lib/seo-validation.cjs');
test('local verification rejects remote redirects before following them',async()=>{
 let calls=0;
 await assert.rejects(localPage('http://localhost:3001','/',async()=>{
  calls++;return new Response(null,{status:302,headers:{Location:'https://snowthing.org/'}});
 }));assert.equal(calls,1);
});
test('local redirects resolve relative locations and bounded loops fail',async()=>{
 const seen=[];
 const result=await localPage('http://localhost:3001','/',async url=>{
  seen.push(url.pathname);return url.pathname==='/'?new Response(null,{status:302,headers:{Location:'/posts'}}):new Response('local');
 });assert.deepEqual(seen,['/','/posts']);assert.equal(result.html,'local');
 await assert.rejects(localPage('http://localhost:3001','/',async()=>new Response(null,{status:302,headers:{Location:'/'}})));
});
test('robots evaluates blanket blocks, group selection, wildcard and longest allow',()=>{
 assert.equal(canCrawl('User-agent: *\nDisallow: /','/api/v1/posts'),false);
 assert.equal(canCrawl('User-agent: *\nDisallow: /\nAllow: /api/','/api/v1/posts'),true);
 assert.equal(canCrawl('User-agent: *\nAllow: /\nUser-agent: Googlebot\nDisallow: /api/','/api/v1/posts'),false);
 assert.equal(canCrawl('User-agent: *\nDisallow: /*.json$','/test.json'),false);
 assert.equal(canCrawl('User-agent: *\nDisallow: /blocked\nAllow: /blocked','/blocked'),true);
 assert.equal(canCrawl('User-agent: Bingbot\nDisallow: /','/posts','Googlebot'),true);
});
