const assert=require('node:assert/strict');
const {withLocalBrowser}=require('./lib/local-browser.cjs');
withLocalBrowser('camera-check',async({navigate,evaluate,waitFor,screenshot})=>{
  const checks=[];
  for(const resort of ['high1','muju','o2-resort','phoenix']){
    await navigate(`/resort-cam/${resort}/cam-01`);
    await waitFor('document.querySelector("video")?.readyState>=2 && document.querySelector("video").currentTime>0.5');
    const state=await evaluate(`(()=>{const video=document.querySelector('video');return {width:video.videoWidth,height:video.videoHeight,time:video.currentTime,resources:performance.getEntriesByType('resource').map(r=>r.name).filter(name=>name.includes('stream-proxy')||name.includes('-stream/')||name.includes('streaming.phoenixhnr.co.kr'))};})()`);
    assert.ok(state.width>0&&state.height>0,'Actual decoded video dimensions');
    assert.ok(state.resources.some(url=>url.includes(resort==='phoenix'?'streaming.phoenixhnr.co.kr':'/stream-proxy/')));
    assert.ok(!state.resources.some(url=>url.includes('/api/high1-stream/')||url.includes('/api/muju-stream/')||url.includes('/api/o2-stream/')));
    checks.push({resort,width:state.width,height:state.height});
    await screenshot(resort);
  }
  await navigate('/resort-cam/phoenix/cam-02');
  await waitFor('document.body.innerText.includes("현재 리조트 공식 공개 목록에 없는 카메라")');
  assert.equal(await evaluate('!!document.querySelector("video")'),false);
  await navigate('/resort-cam/alpensia/cam-01');
  assert.equal(await evaluate('document.querySelector("iframe")?.src.includes("vvRfdby4YxI")'),true);
  await navigate('/resort-cam/eden-valley/cam-01');
  await waitFor('document.body.innerText.includes("영상 제공처에서 현재 재생을 제한")');
  assert.equal(await evaluate('!!document.querySelector("iframe")'),false);
  await navigate('/resort-cam/elysian/cam-01');
  await waitFor('document.body.innerText.includes("공식 웹캠에 재생 가능한 영상이 등록되지 않았습니다")');
  assert.equal(await evaluate('!!document.querySelector("iframe")'),false);
  await navigate('/resort-cam/jisan/cam-01');
  await waitFor('[...document.querySelectorAll("button")].some(button=>button.textContent==="다시 연결")');
  assert.equal(await evaluate('document.body.innerText.includes("시즌 개장 시")'),false);
  await screenshot('source-unavailable');
  await evaluate('[...document.querySelectorAll("button")].find(button=>button.textContent==="다시 연결").click()');
  await waitFor('[...document.querySelectorAll("button")].some(button=>button.textContent==="다시 연결")');
  await evaluate('[...document.querySelectorAll("nav a")].find(a=>a.getAttribute("href")==="/resort-cam/high1").click()');
  await waitFor('location.pathname==="/resort-cam/high1" && [...document.querySelectorAll("video")].some(video=>video.readyState>=2)');
  checks.push({failedSource:'accurate message and retry',switchFromFailure:'plays after client navigation'});
  return {status:'passed',checks};
}).then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error(error.message);process.exitCode=1;});
