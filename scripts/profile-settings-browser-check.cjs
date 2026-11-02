/* Local-only browser smoke check. Requires the existing local fixture credentials
 * in PROFILE_CHECK_EMAIL / PROFILE_CHECK_PASSWORD. Restores profile edits. */
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const WebSocket = require('../frontend/node_modules/next/dist/compiled/ws');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const frontend = 'http://localhost:3000';
const backend = 'http://localhost:8080/api/v1';
const email = process.env.PROFILE_CHECK_EMAIL;
const password = process.env.PROFILE_CHECK_PASSWORD;
if (!email || !password) throw new Error('Set local PROFILE_CHECK_EMAIL and PROFILE_CHECK_PASSWORD');

(async () => {
  const browserPath = ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
  if (!browserPath) throw new Error('No supported local browser found');
  const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), 'snowthing-profile-check-'));
  const profileDir = path.join(artifacts, 'browser');
  const browser = spawn(browserPath, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${profileDir}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
  let ws, original, csrfToken, report, checkFailure;
  let settingsChanged = false;
  const pending = new Map(); let nextId = 0;
  let dialogAnswer = null;
  let dialogCount = 0;
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value); }, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.exception?.description || 'Browser evaluation failed');
    return response.result.value;
  };
  const waitFor = async expression => {
    for (let attempt = 0; attempt < 100; attempt++) { if (await evaluate(expression)) return; await pause(100); }
    throw new Error('Browser condition did not become true');
  };
  const screenshot = async name => {
    const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
    fs.writeFileSync(path.join(artifacts, `${name}.png`), Buffer.from(shot.data, 'base64'));
  };
  try {
    const portFile = path.join(profileDir, 'DevToolsActivePort');
    for (let attempt = 0; !fs.existsSync(portFile); attempt++) {
      if (attempt > 150 || browser.exitCode !== null) throw new Error('Browser did not start');
      await pause(100);
    }
    const port = fs.readFileSync(portFile, 'utf8').split(/\r?\n/)[0];
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    const page = targets.find(target => target.type === 'page');
    ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
    ws.on('message', bytes => {
      const result = JSON.parse(bytes.toString());
      if (result.method === 'Page.javascriptDialogOpening' && dialogAnswer !== null) {
        dialogCount++;
        void send('Page.handleJavaScriptDialog', { accept: dialogAnswer });
      }
      const entry = pending.get(result.id);
      if (!entry) return;
      pending.delete(result.id);
      if (result.error) entry.reject(new Error(result.error.message)); else entry.resolve(result.result);
    });
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
    await send('Page.navigate', { url: frontend + '/login' });
    await waitFor('document.readyState === "complete" && !!document.querySelector(".community-header")');
    const loginResult = await evaluate(`(async () => {
      const tokenResponse = await fetch(${JSON.stringify(backend + '/csrf')}, {credentials:'include'});
      const token = (await tokenResponse.json()).token;
      const response = await fetch(${JSON.stringify(backend + '/auth/login')}, {method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify(${JSON.stringify({ email, password })})});
      return response.status;
    })()`);
    assert.equal(loginResult, 200, 'Local fixture login failed');
    await send('Page.navigate', { url: frontend });
    await waitFor('!!document.querySelector(".header-profile-menu summary")');
    await evaluate('document.querySelector(".header-profile-menu summary").click()');
    assert.equal(await evaluate('document.querySelector(".header-profile-menu").open'), true);
    await evaluate('document.querySelector(".header-profile-dropdown a").click()');
    await waitFor('!!document.querySelector(".profile-nickname input")');
    assert.equal(await evaluate('location.pathname'), '/profile');
    original = await evaluate(`fetch(${JSON.stringify(backend + '/members/me/settings')},{credentials:'include'}).then(r=>r.json())`);
    csrfToken = await evaluate(`fetch(${JSON.stringify(backend + '/csrf')},{credentials:'include'}).then(r=>r.json()).then(x=>x.token)`);
    assert.ok(Array.isArray(original.resorts));
    // Use nonempty preferences so a missing selection cannot pass as an empty-array comparison.
    const masters = await evaluate(`Promise.all([fetch(${JSON.stringify(backend + '/master/resorts')},{credentials:'include'}).then(r=>r.json()),fetch(${JSON.stringify(backend + '/master/riding-styles')},{credentials:'include'}).then(r=>r.json())])`);
    assert.ok(masters[0].length >= 2 && masters[1].length >= 1, 'Local masters are required');
    const preferenceSetup = { nickname: original.nickname, bio: original.bio, departureRegion: original.departureRegion, resortIds: masters[0].slice(0,2).map(x=>x.id), ridingStyleIds: masters[1].slice(0,1).map(x=>x.id) };
    settingsChanged = true; // The request may commit even if the CDP response is lost.
    assert.equal(await evaluate(`fetch(${JSON.stringify(backend + '/members/me/settings')},{method:'PUT',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':${JSON.stringify(csrfToken)}},body:JSON.stringify(${JSON.stringify(preferenceSetup)})}).then(r=>r.status)`),200);
    await evaluate('location.reload()');
    await waitFor('!!document.querySelector(".profile-preferences")');
    const baseline = await evaluate(`fetch(${JSON.stringify(backend + '/members/me/settings')},{credentials:'include'}).then(r=>r.json())`);
    const selectedNames = await evaluate('[...document.querySelectorAll(".profile-preferences input:checked")].map(x=>x.parentElement.textContent)');
    assert.deepEqual(selectedNames.sort(), [...baseline.resorts,...baseline.ridingStyles].map(x=>x.name).sort(), 'Saved preferences must be selected on entry');
    const summaries = await evaluate('[...document.querySelectorAll(".profile-saved-preferences")].map(x=>x.textContent).join(" ")');
    for(const value of [...baseline.resorts,...baseline.ridingStyles]) assert.ok(summaries.includes(value.name));
    assert.equal(await evaluate('!!document.querySelector("input[autocomplete=current-password]")'),false,'Password fields must start closed');
    const nickname = '검수' + Date.now().toString().slice(-6);
    await evaluate(`(() => { const input=document.querySelector('.profile-nickname input'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,${JSON.stringify(nickname)}); input.dispatchEvent(new Event('input',{bubbles:true})); })()`);
    await pause(200);
    assert.equal(await evaluate('document.querySelector(".profile-nickname input").value'), nickname);
    assert.equal(await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="정보 저장").disabled'), true);
    await evaluate('document.querySelector(".profile-nickname button").click()');
    await waitFor('document.querySelector(".profile-nickname").parentElement.textContent.includes("사용할 수 있는 닉네임")');
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="정보 저장").click()');
    await waitFor('document.querySelector("[role=status]")?.textContent.includes("내 정보를 저장했습니다")');
    const saved = await evaluate(`fetch(${JSON.stringify(backend + '/members/me/settings')},{credentials:'include'}).then(r=>r.json())`);
    assert.equal(saved.nickname, nickname);
    assert.deepEqual(saved.resorts, baseline.resorts);
    assert.deepEqual(saved.ridingStyles, baseline.ridingStyles);
    await send('Network.enable');
    await send('Network.emulateNetworkConditions', { offline: false, latency: 600, downloadThroughput: -1, uploadThroughput: -1 });
    const fillNickname = async value => {
      await evaluate(`(() => { const input=document.querySelector('.profile-nickname input'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,${JSON.stringify(value)}); input.dispatchEvent(new Event('input',{bubbles:true})); })()`);
      await pause(100);
    };
    await fillNickname('검수A' + Date.now().toString().slice(-5));
    await evaluate('document.querySelector(".profile-nickname button").click()');
    await fillNickname('검수B' + Date.now().toString().slice(-5));
    await waitFor('!document.querySelector(".profile-nickname button").textContent.includes("확인 중")');
    assert.equal(await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="정보 저장").disabled'), true, 'A delayed nickname response must not validate another nickname');
    dialogAnswer = false;
    await evaluate('document.querySelector(".profile-settings-heading a").click()');
    assert.equal(dialogCount, 1, 'Unsaved profile edits must ask before leaving');
    assert.equal(await evaluate('location.pathname'), '/profile');
    dialogAnswer = null;
    await send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await fillNickname(saved.nickname);
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="비밀번호 변경").click()');
    await waitFor('!!document.querySelector("input[autocomplete=current-password]")');
    await evaluate(`(() => { const input=document.querySelector('input[autocomplete=current-password]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'discard-this-local-draft'); input.dispatchEvent(new Event('input',{bubbles:true})); })()`);
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="변경 취소").click()');
    assert.equal(await evaluate('!!document.querySelector("input[autocomplete=current-password]")'),false);
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="비밀번호 변경").click()');
    await waitFor('!!document.querySelector("input[autocomplete=current-password]")');
    assert.equal(await evaluate('document.querySelector("input[autocomplete=current-password]").value'),'','Cancelled password must be cleared');
    await evaluate('document.querySelector(".profile-password-toggle input").click()');
    assert.equal(await evaluate('document.querySelector("input[autocomplete=current-password]").type'), 'text');
    await evaluate('document.querySelector(".profile-password-toggle input").click()');
    assert.equal(await evaluate('document.querySelector("input[autocomplete=current-password]").type'), 'password');
    const documentNode = await send('DOM.getDocument');
    const fileNode = await send('DOM.querySelector', { nodeId: documentNode.root.nodeId, selector: 'input[type=file]' });
    await send('DOM.setFileInputFiles', { nodeId: fileNode.nodeId, files: [path.resolve(__dirname, '../frontend/public/images/default-profile-avatar.png')] });
    await waitFor('[...document.querySelectorAll("button")].some(b=>b.textContent==="사진 저장")');
    assert.equal(await evaluate('document.querySelector(".profile-photo-editor > img").getAttribute("src").startsWith("blob:")'), true);
    const actionStyles = await evaluate(`Array.from(document.querySelectorAll('.profile-settings button,.profile-file-label'),button=>{const s=getComputedStyle(button);return {text:button.textContent.trim(),size:s.fontSize,lineHeight:s.lineHeight,padding:s.padding,radius:s.borderRadius,family:s.fontFamily,spacing:s.letterSpacing,weight:s.fontWeight,primary:button.classList.contains('profile-button-primary'),cancel:button.classList.contains('profile-button-cancel'),background:s.backgroundColor,borderColor:s.borderColor}})`);
    assert.ok(actionStyles.length >= 8, 'Inspect photo, password, nickname and profile actions together');
    for (const button of actionStyles) {
      assert.equal(button.size,'14px',button.text);
      assert.equal(button.lineHeight,'20px',button.text);
      assert.equal(button.padding,'8px 12px',button.text);
      assert.equal(button.radius,'8px',button.text);
      assert.equal(button.family,actionStyles[0].family,button.text);
      assert.equal(button.spacing,actionStyles[0].spacing,button.text);
      assert.equal(button.weight,button.primary?'600':'500',button.text);
      if(button.cancel) { assert.equal(button.background,'rgba(0, 0, 0, 0)'); assert.equal(button.borderColor,'rgba(0, 0, 0, 0)'); }
    }
    assert.equal(await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="정보 저장").disabled'), true, 'A photo selection must not enable unrelated profile saving');
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="선택 취소").click()');
    await waitFor('!document.querySelector(".profile-photo-editor > img").getAttribute("src").startsWith("blob:")');
    await screenshot('password-expanded');
    await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="변경 취소").click()');
    await screenshot('desktop-light');
    await evaluate('localStorage.setItem("snowthing-theme","dark"); location.reload()');
    await waitFor('!!document.querySelector(".profile-nickname input") && document.documentElement.dataset.theme === "dark"');
    await screenshot('desktop-dark');
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
    await pause(200);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'Mobile horizontal overflow');
    await screenshot('mobile-dark');
    await evaluate('document.documentElement.dataset.theme="light"');
    await screenshot('mobile-light');
    report = { status: 'passed', checks: ['profile menu navigation', 'nickname confirmation before save', 'stale nickname response rejection', 'unsaved changes navigation guard', 'password visibility toggle', 'profile save', 'preference preservation', 'photo preview and cancellation', 'light and dark rendering', '390px horizontal overflow'], artifacts };
  } catch (error) {
    checkFailure = error;
  } finally {
    let restoreFailure;
    try {
    if (settingsChanged && original) {
      const restore = { nickname: original.nickname, bio: original.bio, departureRegion: original.departureRegion, resortIds: original.resorts.map(x => x.id), ridingStyleIds: original.ridingStyles.map(x => x.id) };
      fs.writeFileSync(path.join(artifacts, 'fixture-recovery.json'), JSON.stringify({email, settings: restore}, null, 2));
      if (ws?.readyState !== WebSocket.OPEN) throw new Error(`Fixture restoration unavailable; recover using ${path.join(artifacts, 'fixture-recovery.json')}`);
      const status = await evaluate(`fetch(${JSON.stringify(backend + '/members/me/settings')},{method:'PUT',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':${JSON.stringify(csrfToken)}},body:JSON.stringify(${JSON.stringify(restore)})}).then(r=>r.status)`);
      assert.equal(status, 200, 'Local fixture restoration failed; inspect local profile');
    }
    } catch (error) { restoreFailure = error; }
    if (ws) ws.close();
    browser.kill();
    if (checkFailure && restoreFailure) throw new AggregateError([checkFailure, restoreFailure], 'Profile check and fixture restoration both failed');
    if (restoreFailure) throw restoreFailure;
    if (checkFailure) throw checkFailure;
  }
  console.log(JSON.stringify(report));
})().catch(error => { console.error(error); process.exitCode = 1; });
