const API='/api/v1';
let csrf=null;
export async function request(path,options={}) {
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),15000);
  try {
    const mutating=['POST','PUT','PATCH','DELETE'].includes((options.method || 'GET').toUpperCase());
    const headers={'Content-Type':'application/json',...options.headers};
    if (mutating && csrf) headers[csrf.headerName]=csrf.token;
    const response=await fetch(API+path,{...options,credentials:'same-origin',headers,signal:controller.signal});
    const body=await response.json().catch(()=>null);
    if (!response.ok) {
      const error=new Error(body?.error?.message || '请求未完成');
      error.status=response.status;
      error.code=body?.error?.code;
      error.details=body?.error?.details;
      throw error;
    }
    if (!body) throw new Error('服务未返回有效数据');
    return body;
  } finally {clearTimeout(timer);}
}
export async function refreshSession() {
  const session=await request('/auth/session');
  csrf=session.csrf;
  return session.data;
}
export async function login(username,password) {
  await request('/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username,password}).toString()});
  return refreshSession();
}
export async function logout() {
  await request('/auth/logout',{method:'POST'});
  csrf=null;
  return refreshSession();
}
