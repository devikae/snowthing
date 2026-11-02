const assert = require('node:assert/strict');
const {withLocalBrowser} = require('./lib/local-browser.cjs');
const email=process.env.PROFILE_CHECK_EMAIL;
const password=process.env.PROFILE_CHECK_PASSWORD;
if(!email||!password)throw new Error('Set local PROFILE_CHECK_EMAIL and PROFILE_CHECK_PASSWORD fixture credentials');

withLocalBrowser('chat-help-check',async({navigate,evaluate,waitFor,fill,viewport,screenshot})=>{
  await navigate('/');
  await evaluate('document.querySelector(".live-chat-help-toggle").click()');
  await waitFor('!!document.querySelector("#live-chat-local-help")');
  assert.equal(await evaluate('document.querySelectorAll("#live-chat-local-help a").length'),2);
  await viewport(390,844);
  assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);
  await screenshot('anonymous-mobile-light');
  await evaluate('localStorage.setItem("snowthing-theme","dark"); document.documentElement.dataset.theme="dark"');
  await screenshot('anonymous-mobile-dark');
  await navigate('/login');
  const status=await evaluate(`(async()=>{
    const base='http://localhost:8080/api/v1';
    const csrf=await(await fetch(base+'/csrf',{credentials:'include'})).json();
    const response=await fetch(base+'/auth/login',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:JSON.stringify(${JSON.stringify({email,password})})});
    return response.status;
  })()`);
  assert.equal(status,200);
  try{
    await navigate('/');
    await waitFor('document.querySelector(".live-chat-compose-input")?.placeholder==="메세지를 입력하세요"');
    await evaluate(`(()=>{window.chatSendCount=0;const original=WebSocket.prototype.send;WebSocket.prototype.send=function(data){if(typeof data==='string'&&data.startsWith('SEND'))window.chatSendCount++;return original.call(this,data);};})()`);
    await fill('.live-chat-compose-input','/데크');
    await evaluate('document.querySelector(".live-chat-compose-submit").click()');
    await waitFor('[...document.querySelectorAll("#live-chat-local-help a")].some(a=>a.getAttribute("href")==="/tools/deck-size")');
    assert.equal(await evaluate('document.querySelectorAll("#live-chat-local-help a").length'),1);
    assert.equal(await evaluate('window.chatSendCount'),0,'Local command must not publish to the public chat');
    assert.equal(await evaluate('document.querySelector(".live-chat-compose-input").value'),'');
    await fill('.live-chat-compose-input','/도움말');
    await evaluate('document.querySelector(".live-chat-compose-submit").click()');
    await waitFor('document.querySelectorAll("#live-chat-local-help a").length===2');
    assert.equal(await evaluate('window.chatSendCount'),0);
    await waitFor('document.documentElement.dataset.theme==="dark"');
    await viewport(1440,900);await screenshot('member-desktop-dark');
    assert.equal(await evaluate('[...document.querySelectorAll(".community-header a")].some(a=>a.getAttribute("href")==="/crews")'),false);
    return {status:'passed',checks:['anonymous help','two real tool links','390px overflow','light/dark','exact local command','no STOMP SEND for local commands','no calculation data broadcast','crew navigation excluded']};
  }finally{
    await evaluate(`(async()=>{const base='http://localhost:8080/api/v1';const csrf=await(await fetch(base+'/csrf',{credentials:'include'})).json();await fetch(base+'/auth/logout',{method:'POST',credentials:'include',headers:{'X-XSRF-TOKEN':csrf.token}});})()`);
  }
}).then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error(error.message);process.exitCode=1;});
