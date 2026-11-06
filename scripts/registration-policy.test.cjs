const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const directory=path.join(__dirname,'../shared/registration-policies');
const read=(name)=>JSON.parse(fs.readFileSync(path.join(directory,name+'.json'),'utf8'));
test('registration policy manifest selects an immutable published snapshot',()=>{
 const manifest=read('current');
 assert.match(manifest.version,/^[a-z0-9-]+$/);
 const policy=read(manifest.version);
 assert.equal(policy.version,manifest.version);assert(policy.sections.length>=4);assert(policy.collection.items);assert(policy.collection.retention);
 const frontend=fs.readFileSync(path.join(__dirname,'../frontend/app/lib/registrationPolicy.ts'),'utf8');
 assert(frontend.includes(manifest.version+'.json'));
 assert(fs.readFileSync(path.join(__dirname,'../backend/build.gradle'),'utf8').includes("from('../shared/registration-policies')"));
});
test('terms manifest selects a snapshot the frontend can open',()=>{
 const manifest=read('current');
 assert.match(manifest.termsVersion,/^[a-z0-9-]+$/);
 const terms=read(manifest.termsVersion);
 assert.equal(terms.version,manifest.termsVersion);assert(terms.sections.length>=4);
 assert(fs.readFileSync(path.join(__dirname,'../frontend/app/lib/registrationPolicy.ts'),'utf8').includes(manifest.termsVersion+'.json'));
});
test('a document with unresolved operator facts stays a draft',()=>{
 for(const file of fs.readdirSync(directory).filter(name=>name!=='current.json')){
  const text=fs.readFileSync(path.join(directory,file),'utf8');
  if(text.includes('[확정 필요'))assert.equal(JSON.parse(text).draft,true,file);
 }
});
