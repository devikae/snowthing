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

test('known upstream failures keep the HLS player so a resumed stream can play again',()=>{
  for(const [resort,sourceUrl] of [
    ['JISAN','https://ant.livecity.co.kr:5443/jisancam/streams/jisan1.m3u8'],
    ['MUJU','/stream-proxy/muju/02/playlist.m3u8'],
    ['OAK_VALLEY','https://cctv-oak9.ktcdn.co.kr/cctv/ch2.stream/chunklist.m3u8'],
  ]){
    const camera=updateOfficialCameraSource(resort,{code:'CAM_01',name:'camera',sourceType:'HLS',sourceUrl,externalPageUrl:null,displayOrder:1});
    assert.equal(camera.sourceType,'HLS');
    assert.equal(camera.sourceIssue,'upstream-unavailable');
    assert.ok(camera.availabilityNote.includes('원본'));
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
