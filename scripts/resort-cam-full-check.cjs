// Read-only live audit. Requires local frontend/backend; external availability varies.
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const ts=require('../frontend/node_modules/typescript');
const {withLocalBrowser}=require('./lib/local-browser.cjs');
function loadSource(file){
  const source=fs.readFileSync(path.join(__dirname,'../frontend/app/resort-cam',file),'utf8');
  const context={exports:{}};
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context);
  return context.exports;
}
const {resolveCameraSourceUrl,resortSlug,cameraSlug}=loadSource('types.ts');
const {updateOfficialCameraSource}=loadSource('sourceUpdates.ts');
async function probeHls(source){
  let url=new URL(source,'http://localhost:3000');
  const stages=[];
  try{
    for(let depth=0;depth<3;depth++){
      const response=await fetch(url,{signal:AbortSignal.timeout(10000),headers:{Origin:'http://localhost:3000'}});
      const text=await response.text();
      stages.push({kind:'playlist',status:response.status,hls:text.trimStart().startsWith('#EXTM3U')});
      if(!response.ok||!text.trimStart().startsWith('#EXTM3U'))return{active:false,stages};
      const first=text.split(/\r?\n/).find(line=>line.trim()&&!line.startsWith('#'));
      if(!first)return{active:false,stages,reason:'empty-playlist'};
      url=new URL(first,url);
      if(/\.m3u8(?:$|\?)/i.test(url.href))continue;
      const segment=await fetch(url,{signal:AbortSignal.timeout(10000)});
      stages.push({kind:'segment',status:segment.status});
      await segment.body?.cancel();
      return{active:segment.ok,live:!text.includes('#EXT-X-ENDLIST'),stages};
    }
    return{active:false,stages,reason:'playlist-depth'};
  }catch(error){return{active:false,stages,reason:error.name};}
}

withLocalBrowser('camera-full-check',async browser=>{
  const {navigate,evaluate,waitFor,pause,send,screenshot,artifacts,viewport}=browser;
  const metadata=await(await fetch('http://localhost:8080/api/v1/resort-cams')).json();
  const cameras=metadata.resorts.flatMap(resort=>resort.cameras.map(camera=>({
    resort:resort.code,
    camera:updateOfficialCameraSource(resort.code,{...camera,sourceUrl:resolveCameraSourceUrl(camera.sourceUrl)}),
  })));
  const jobs=cameras.filter(item=>item.camera.sourceType==='HLS');
  const probes=new Map();
  const pending=[...jobs];
  await Promise.all(Array.from({length:4},async()=>{
    while(pending.length){const item=pending.shift();probes.set(item.resort+':'+item.camera.code,await probeHls(item.camera.sourceUrl));}
  }));
  console.log(JSON.stringify({phase:'source-probe',hls:jobs.length,active:[...probes.values()].filter(value=>value.active).length,artifacts}));
  const results=[];const photographed=new Set();
  const persist=()=>fs.writeFileSync(path.join(artifacts,'results.json'),JSON.stringify({checkedAt:new Date().toISOString(),results},null,2));
  for(const item of cameras){
    const {camera,resort}=item;
    const result={resort,camera:camera.code,type:camera.sourceType,probe:probes.get(resort+':'+camera.code)};
    try{
      await navigate(`/resort-cam/${resortSlug(resort)}/${cameraSlug(camera.code)}`);
      if(camera.sourceType==='HLS'){
        const ready='document.querySelector("video")?.readyState>=2 && document.querySelector("video").videoWidth>0 && document.querySelector("video").currentTime>0.5';
        let played=false;
        for(let attempt=0;attempt<2;attempt++){
          await waitFor(`(${ready}) || !!document.querySelector('main [role="status"]')`,20000);
          if(await evaluate(ready)){played=true;break;}
          if(!result.probe?.active||attempt===1)break;
          await evaluate('[...document.querySelectorAll("main button")].find(button=>button.textContent==="다시 연결")?.click()');
        }
        if(played){
          result.status='playing';result.video=await evaluate(`(()=>{const v=document.querySelector('video');return{width:v.videoWidth,height:v.videoHeight,time:v.currentTime}})()`);
        }else{
          result.status=result.probe?.active?'browser-failed':'source-unavailable-guide';
          result.message=await evaluate('document.querySelector("main [role=status]")?.textContent??""');
          if(!result.message||!await evaluate('[...document.querySelectorAll("main button")].some(button=>button.textContent==="다시 연결")'))result.status='missing-failure-guide';
        }
      }else if(camera.sourceType==='IFRAME'&&camera.sourceUrl.startsWith('/vivaldi.html')){
        await waitFor(`(()=>{const d=document.querySelector('iframe')?.contentDocument;return [...(d?.images??[])].some(image=>image.complete&&image.naturalWidth>0&&image.src.startsWith('blob:'));})()`,20000);
        const first=await evaluate(`(()=>{const image=document.querySelector('iframe').contentDocument.images[0];return{url:image.src,width:image.naturalWidth,height:image.naturalHeight}})()`);
        await waitFor(`document.querySelector('iframe').contentDocument.images[0].src!==${JSON.stringify(first.url)}`,8000);
        result.status='playing';result.video={width:first.width,height:first.height,movingFrames:true};
      }else if(camera.sourceType==='YOUTUBE'){
        let sessionId;
        for(let retry=0;retry<30;retry++){
          const target=(await send('Target.getTargets')).targetInfos.find(t=>t.type==='iframe'&&t.url.startsWith(camera.sourceUrl));
          if(target){sessionId=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true})).sessionId;break;}
          await pause(200);
        }
        if(!sessionId)throw new Error('YouTube frame missing');
        let video;
        for(let retry=0;retry<60;retry++){
          const state=await send('Runtime.evaluate',{returnByValue:true,expression:`(()=>{const v=document.querySelector('video');return v?{width:v.videoWidth,height:v.videoHeight,time:v.currentTime,ready:v.readyState,paused:v.paused}:null})()`},sessionId);
          video=state.result.value;
          if(video?.ready>=2&&video.width>0&&video.time>0.5&&!video.paused)break;
          await pause(200);
        }
        if(!video||video.ready<2||!video.width||video.paused)throw new Error('YouTube video did not play');
        result.status='playing';result.video=video;
      }else if(camera.sourceType==='EXTERNAL_LINK'){
        await waitFor('!!document.querySelector("main [role=status]")');
        result.message=await evaluate('document.querySelector("main [role=status]").textContent');
        result.status=camera.availabilityNote&&result.message.includes(camera.availabilityNote)?'source-unavailable-guide':'missing-failure-guide';
      }else throw new Error('Unsupported audit renderer');
      if(!photographed.has(resort)||result.status.includes('failed')){
        await screenshot(`${resort}-${camera.code}`);photographed.add(resort);
      }
    }catch(error){result.status='browser-failed';result.reason=error.message;await screenshot(`${resort}-${camera.code}-failure`);}
    results.push(result);persist();
    if(results.length%5===0||result.status==='browser-failed')console.log(JSON.stringify({checked:results.length,total:cameras.length,last:resort+':'+camera.code,status:result.status}));
  }
  await viewport(390,844);await navigate('/resort-cam/jisan/cam-01');
  await waitFor('!!document.querySelector("main [role=status]")');
  const fits=await evaluate(`(()=>{const panel=document.querySelector('main [role=status]');const box=panel.getBoundingClientRect();return document.documentElement.scrollWidth<=innerWidth&&[...panel.querySelectorAll('a,button')].every(e=>{const r=e.getBoundingClientRect();return r.top>=box.top&&r.bottom<=box.bottom&&r.left>=box.left&&r.right<=box.right;});})()`);
  if(!fits)throw new Error('Mobile status actions clipped');
  await screenshot('status-mobile-light');await evaluate('document.documentElement.dataset.theme="dark"');await screenshot('status-mobile-dark');
  const failures=results.filter(result=>['browser-failed','missing-failure-guide'].includes(result.status));
  return{status:failures.length?'failed':'passed',total:results.length,playing:results.filter(result=>result.status==='playing').length,sourceUnavailable:results.filter(result=>result.status==='source-unavailable-guide').length,failures};
}).then(result=>{console.log(JSON.stringify(result));if(result.status!=='passed')process.exitCode=1;}).catch(error=>{console.error(error.message);process.exitCode=1;});
