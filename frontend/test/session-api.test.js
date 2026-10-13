import test from 'node:test';
import assert from 'node:assert/strict';
import * as api from '../src/api.js';

test('session API sends CSRF on mutations, keeps cookies in browser and refreshes after auth',async()=>{
  const original=globalThis.fetch;
  const calls=[];
  let token='before-login';
  globalThis.fetch=async(url,options)=>{
    calls.push({url,options});
    if(url.endsWith('/auth/login')) token='after-login';
    if(url.endsWith('/auth/logout')) token='after-logout';
    return {ok:true,status:200,json:async()=>url.endsWith('/auth/session') ? {data:{username:'demo_owner'},csrf:{token,headerName:'X-CSRF-TOKEN'}} : {data:{id:1}}};
  };
  try {
    assert.equal(typeof api.refreshSession,'function');
    await api.refreshSession();
    await api.login('demo_owner','test password');
    await api.request('/projects',{method:'POST',body:'{"name":"example"}'});
    assert.equal(calls.at(-1).options.headers['X-CSRF-TOKEN'],'after-login');
    assert.equal(calls.at(-1).options.credentials,'same-origin');
    assert.ok(calls.find(c=>c.url.endsWith('/auth/login')).options.body.includes('password=test+password'));
    await api.logout();
    assert.equal(calls.at(-1).url,'/api/v1/auth/session');
    await api.request('/projects');
    assert.equal(calls.at(-1).options.headers['X-CSRF-TOKEN'],undefined);
  } finally {globalThis.fetch=original;}
});
