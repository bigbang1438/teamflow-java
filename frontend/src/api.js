const API='/api/v1';
export async function request(path,options={}) {
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),15000);
  try {
    const response=await fetch(API+path,{...options,headers:{'Content-Type':'application/json',...options.headers},signal:controller.signal});
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
