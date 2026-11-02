const assert = require('node:assert/strict');

async function localPage(base, route, request = fetch) {
  const origin = new URL(base);
  assert.equal(origin.hostname, 'localhost');
  let url = new URL(route, origin);
  for (let hops = 0; hops < 6; hops++) {
    assert.equal(url.origin, origin.origin, 'SEO verification must stay on the candidate local server');
    const response = await request(url, {redirect:'manual', headers:{'User-Agent':'Googlebot'}});
    if ([301,302,303,307,308].includes(response.status)) {
      assert.ok(response.headers.get('location'), 'Redirect must have a destination');
      url = new URL(response.headers.get('location'), url);
      continue;
    }
    return {status:response.status, html:await response.text()};
  }
  throw new Error('Local redirect limit exceeded');
}

function canCrawl(text, pathname, agent='Googlebot') {
  const groups=[];let group=null;
  for (const raw of text.split(/\r?\n/)) {
    const line=raw.split('#')[0].trim();const colon=line.indexOf(':');if(colon<0)continue;
    const field=line.slice(0,colon).trim().toLowerCase(),value=line.slice(colon+1).trim();
    if(field==='user-agent') {
      if(!group||group.rules.length){group={agents:[],rules:[]};groups.push(group);}
      group.agents.push(value.toLowerCase());
    } else if(group&&['allow','disallow'].includes(field)&&value) group.rules.push({allow:field==='allow',pattern:value});
  }
  const score=g=>Math.max(-1,...g.agents.map(a=>a==='*'?0:agent.toLowerCase().includes(a)?a.length:-1));
  const best=Math.max(-1,...groups.map(score));let match=null;
  for(const rule of groups.filter(g=>score(g)===best).flatMap(g=>g.rules)) {
    const pattern=rule.pattern;const terminal=pattern.endsWith('$');
    const regex='^'+pattern.slice(0,terminal?-1:undefined).split('*').map(part=>part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('.*')+(terminal?'$':'');
    if(new RegExp(regex).test(pathname)) {
      const length=pattern.replace(/[*$]/g,'').length;
      if(!match||length>match.length||(length===match.length&&rule.allow))match={length,allow:rule.allow};
    }
  }
  return match?.allow??true;
}
module.exports={localPage,canCrawl};
