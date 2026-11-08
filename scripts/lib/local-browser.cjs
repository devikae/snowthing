const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const WebSocket = require('../../frontend/node_modules/next/dist/compiled/ws');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

async function withLocalBrowser(label, check) {
  const localOrigin = new URL(process.env.LOCAL_BROWSER_ORIGIN || 'http://localhost:3000');
  if (localOrigin.hostname !== 'localhost' || localOrigin.protocol !== 'http:') throw new Error('Local browser checks require HTTP localhost');
  const browserPath = ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
  if (!browserPath) throw new Error('No supported local browser found');
  const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), `snowthing-${label}-`));
  const userData = path.join(artifacts, 'browser');
  const browser = spawn(browserPath, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${userData}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
  const pending = new Map(); let nextId = 0; let ws; let dialogAnswer = null;
  const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(id, { timer, resolve, reject });
    ws.send(JSON.stringify({ id, method, params, ...(sessionId ? {sessionId} : {}) }));
  });
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.exception?.description || 'Browser evaluation failed');
    return response.result.value;
  };
  const waitFor = async (expression, timeoutMs = 10000) => {
    for (let i = 0; i < Math.ceil(timeoutMs / 100); i++) { if (await evaluate(expression)) return; await pause(100); }
    throw new Error(`Browser condition timed out: ${expression}`);
  };
  try {
    const portFile = path.join(userData, 'DevToolsActivePort');
    for (let i = 0; !fs.existsSync(portFile); i++) {
      if (i > 150 || browser.exitCode !== null) throw new Error('Browser did not start');
      await pause(100);
    }
    const port = fs.readFileSync(portFile, 'utf8').split(/\r?\n/)[0];
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
    ws.on('message', bytes => {
      const response = JSON.parse(bytes.toString());
      if (response.method === 'Page.javascriptDialogOpening' && dialogAnswer !== null) {
        const accept=dialogAnswer; dialogAnswer=null; void send('Page.handleJavaScriptDialog',{accept});
      }
      const entry = pending.get(response.id);
      if (!entry) return;
      clearTimeout(entry.timer); pending.delete(response.id);
      if (response.error) entry.reject(new Error(response.error.message)); else entry.resolve(response.result);
    });
    await send('Page.enable');
    const navigate = async route => {
      const url = new URL(route, localOrigin);
      if (url.origin !== localOrigin.origin) throw new Error('This check only navigates the local app');
      const marker = `navigation-${Date.now()}-${nextId}`;
      await evaluate(`window.__localBrowserNavigationMarker=${JSON.stringify(marker)}`);
      await send('Page.navigate', { url: url.toString() });
      await waitFor(`location.href===${JSON.stringify(url.toString())} && window.__localBrowserNavigationMarker!==${JSON.stringify(marker)} && document.readyState === "complete" && !!document.querySelector(".community-header")`);
    };
    const fill = async (selector, value) => {
      await evaluate(`(() => { const element=document.querySelector(${JSON.stringify(selector)}); if(!element) throw new Error('Missing form field'); const prototype=element.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(prototype,'value').set.call(element,${JSON.stringify(value)}); element.dispatchEvent(new Event(element.tagName==='SELECT'?'change':'input',{bubbles:true})); })()`);
      await pause(50);
    };
    const viewport = (width, height = 900) => send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 });
    const screenshot = async name => {
      const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
      fs.writeFileSync(path.join(artifacts, `${name}.png`), Buffer.from(result.data, 'base64'));
    };
    await viewport(1440);
    const confirmNext = (accept = true) => { dialogAnswer=accept; };
    const result = await check({ send, evaluate, waitFor, navigate, fill, viewport, screenshot, pause, artifacts, confirmNext });
    return { ...result, artifacts };
  } catch (error) {
    if (ws?.readyState === WebSocket.OPEN) {
      try {
        const snapshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
        fs.writeFileSync(path.join(artifacts, 'failure.png'), Buffer.from(snapshot.data, 'base64'));
        const state = await evaluate('JSON.stringify({url:location.href,title:document.title,text:document.body.innerText.slice(0,2000)})');
        fs.writeFileSync(path.join(artifacts, 'failure-state.json'), state);
      } catch { /* Keep the original failure if the page is unavailable. */ }
    }
    error.message += ` (artifacts: ${artifacts})`;
    throw error;
  } finally {
    for (const entry of pending.values()) clearTimeout(entry.timer);
    if (ws) ws.close();
    browser.kill();
  }
}
module.exports = { withLocalBrowser };
