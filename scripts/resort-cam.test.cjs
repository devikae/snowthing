const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('../frontend/node_modules/typescript');
const source=fs.readFileSync(path.join(__dirname,'../frontend/app/resort-cam/types.ts'),'utf8');
const context={exports:{}};
vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context);
const {resolveCameraSourceUrl,sortResortCameras}=context.exports;
const updatesContext={exports:{}};
const updatesSource=fs.readFileSync(path.join(__dirname,'../frontend/app/resort-cam/sourceUpdates.ts'),'utf8');
vm.runInNewContext(ts.transpileModule(updatesSource,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,updatesContext);
const {updateOfficialCameraSource}=updatesContext.exports;

test('stored legacy camera paths resolve to the existing Next proxy routes',()=>{
  for(const [oldPath,newPath] of [
    ['/api/high1-stream/1/playlist.m3u8','/stream-proxy/high1/1/playlist.m3u8'],
    ['/api/muju-stream/01/chunklist.m3u8?e=123&h=test','/stream-proxy/muju/01/chunklist.m3u8?e=123&h=test'],
    ['/api/o2-stream/cam0.m3u8','/stream-proxy/o2/cam0.m3u8'],
  ]) assert.equal(resolveCameraSourceUrl(oldPath),newPath);
});

test('current proxy URLs, external URLs, and unrelated API paths are unchanged',()=>{
  for(const url of ['/stream-proxy/high1/1/playlist.m3u8','/vivaldi.html?channel=8',
    'https://example.com/api/high1-stream/1/playlist.m3u8','/api/high1-streaming/1/playlist.m3u8']) {
    assert.equal(resolveCameraSourceUrl(url),url);
  }
});

test('requested resort order comes first without mutating the API data or losing other resorts',()=>{
  const input=['PHOENIX','VIVALDI','HIGH1','YONGPYONG','WELLI_HILLI','JISAN','KONJIAM','MUJU','EDEN_VALLEY','ELYSIAN','ALPENSIA','OAK_VALLEY','O2_RESORT'].map(code=>({code,cameras:[]}));
  const original=input.map(resort=>resort.code).join(',');
  assert.equal(sortResortCameras(input).map(resort=>resort.code).join(','),
    'KONJIAM,WELLI_HILLI,YONGPYONG,HIGH1,JISAN,MUJU,O2_RESORT,PHOENIX,VIVALDI,EDEN_VALLEY,ELYSIAN,ALPENSIA,OAK_VALLEY');
  assert.equal(input.map(resort=>resort.code).join(','),original);
});

// IKA-100 (2026-11-20): 공식 페이지가 쓰는 원본에서도 영상이 나오지 않는 카메라는 공식 페이지에서 보기로 돌린다.
// 이전에는 재생기를 유지했지만, 무주 02/03은 프록시 수정으로 다시 나오고 나머지는 원본이 꺼져 있다.
test('off-air cameras confirmed on the official source open the official page instead of an empty player',()=>{
  for(const [resort,sourceType,sourceUrl] of [
    ['JISAN','HLS','https://ant.livecity.co.kr:5443/jisancam/streams/jisan1.m3u8'],
    ['JISAN','HLS','https://ant.livecity.co.kr:5443/jisancam/streams/jisan5.m3u8'],
    ['OAK_VALLEY','HLS','https://cctv-oak9.ktcdn.co.kr/cctv/ch2.stream/chunklist.m3u8'],
    ['WELLI_HILLI','HLS','https://live.wellihillipark.com/wellihillipark/_definst_/cam05.stream/playlist.m3u8'],
    ['VIVALDI','IFRAME','/vivaldi.html?channel=8&serial=TW0014A15451&autoplay=true'],
    ['VIVALDI','IFRAME','/vivaldi.html?channel=10&serial=TW0014A15451&autoplay=true'],
  ]){
    const camera=updateOfficialCameraSource(resort,{code:'CAM_01',name:'camera',sourceType,sourceUrl,externalPageUrl:'https://official.example/cam',displayOrder:1});
    assert.equal(camera.sourceType,'EXTERNAL_LINK',sourceUrl);
    assert.equal(camera.sourceIssue,'upstream-unavailable');
    assert.equal(camera.externalPageUrl,'https://official.example/cam');
    assert.ok(camera.availabilityNote.includes('공식 페이지'));
  }
});

test('cameras still on air keep their player, including streams fixed by the proxy',()=>{
  for(const [resort,sourceType,sourceUrl] of [
    ['MUJU','HLS','/stream-proxy/muju/02/playlist.m3u8'],
    ['JISAN','HLS','https://ant.livecity.co.kr:5443/jisancam/streams/jisan2.m3u8'],
    ['OAK_VALLEY','HLS','https://cctv-oak9.ktcdn.co.kr/cctv/ch5.stream/chunklist.m3u8'],
    ['WELLI_HILLI','HLS','https://live.wellihillipark.com/wellihillipark/_definst_/cam04.stream/playlist.m3u8'],
    ['VIVALDI','IFRAME','/vivaldi.html?channel=1&serial=TW0014A15451&autoplay=true'],
    ['VIVALDI','IFRAME','/vivaldi.html?channel=8&serial=XU0121A37904&autoplay=true'],
    ['VIVALDI','IFRAME','/vivaldi.html?channel=10&serial=TD0314A17496&autoplay=true'],
  ]){
    const input={code:'CAM_01',name:'camera',sourceType,sourceUrl,externalPageUrl:null,displayOrder:1};
    assert.equal(updateOfficialCameraSource(resort,input),input,sourceUrl);
  }
});

test('moved official webcam pages replace only the confirmed old links',()=>{
  for(const [resort,from,to] of [
    ['OAK_VALLEY','https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do','https://oakvalley.co.kr/ski/introduction/realtime'],
    ['WELLI_HILLI','https://m.wellihillipark.com/customer/webcam','https://www.wellihillipark.com/home/customer/webcam'],
    ['VIVALDI','https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S','https://www.sonohotelsresorts.com/skiboard'],
  ]){
    const camera={code:'CAM_02',name:'camera',sourceType:'HLS',sourceUrl:'https://custom.example/live.m3u8',externalPageUrl:from,displayOrder:2};
    assert.equal(updateOfficialCameraSource(resort,camera).externalPageUrl,to);
    const custom={...camera,externalPageUrl:'https://custom.example/page'};
    assert.equal(updateOfficialCameraSource(resort,custom),custom);
  }
});

test('verified official camera changes update old sources without replacing custom settings',()=>{
  const camera={code:'CAM_01',name:'old name',sourceType:'HLS',sourceUrl:'https://streaming.phoenixhnr.co.kr/hls/yh_02.m3u8',externalPageUrl:null,displayOrder:1};
  const updated=updateOfficialCameraSource('PHOENIX',camera);
  assert.equal(updated.sourceUrl,'https://streaming.phoenixhnr.co.kr/hls/yh_02_02.m3u8');
  assert.equal(camera.name,'old name');
  const absent=updateOfficialCameraSource('PHOENIX',{...camera,sourceUrl:'https://streaming.phoenixhnr.co.kr/hls/sp_01.m3u8'});
  assert.equal(absent.sourceType,'EXTERNAL_LINK');
  assert.ok(absent.availabilityNote);
  const custom={...camera,sourceUrl:'https://custom.example/camera.m3u8'};
  assert.equal(updateOfficialCameraSource('PHOENIX',custom),custom);
  const alpensia=updateOfficialCameraSource('ALPENSIA',{...camera,sourceType:'YOUTUBE',sourceUrl:'https://www.youtube.com/embed/I3azd8f2HhM'});
  assert.equal(alpensia.sourceUrl,'https://www.youtube.com/embed/vvRfdby4YxI');
  for (const [before,after] of [['2kTsKt35/','nKGZGiRD/'],['ry9aTdQh','dh6R7d4k/']]) {
    const result=updateOfficialCameraSource('EDEN_VALLEY',{...camera,sourceType:'IFRAME',sourceUrl:'https://rtsp.me/embed/'+before});
    assert.equal(result.sourceUrl,'https://rtsp.ru/embed/'+after);
    assert.equal(result.sourceType,'EXTERNAL_LINK');
    assert.ok(result.availabilityNote);
  }
  const elysian=updateOfficialCameraSource('ELYSIAN',{...camera,sourceType:'YOUTUBE',sourceUrl:'https://www.youtube.com/embed/O6_8VBu4FSA'});
  assert.equal(elysian.sourceType,'EXTERNAL_LINK');
  assert.equal(elysian.externalPageUrl,'https://elysian.co.kr/about-gangchon/ski');
});
