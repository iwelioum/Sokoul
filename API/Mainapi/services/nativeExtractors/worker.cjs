// Each extraction gets an isolated parser runtime and a parent-owned HTTP transport.
'use strict';
const { parentPort,workerData } = require('node:worker_threads');
const { webcrypto } = require('node:crypto');
const fs=require('node:fs');
const path=require('node:path');
let sequence=0;let userAgent=null;const pending=new Map();
function remoteFetch(url,init={}) {
  return new Promise((resolve,reject)=>{
    const id=++sequence;
    for(const [key,value] of Object.entries(init.headers || {}))if(key.toLowerCase()==='user-agent')userAgent=value;
    const abort=()=>{parentPort.postMessage({kind:'abort',id});pending.delete(id);reject(Object.assign(new Error('cancelled'),{name:'AbortError'}));};
    if(init.signal?.aborted){abort();return;}
    const clean={};for(const key of ['method','headers','body','redirect'])if(init[key]!==undefined)clean[key]=init[key];
    init.signal?.addEventListener('abort',abort,{once:true});
    pending.set(id,{resolve,reject,signal:init.signal,abort});
    parentPort.postMessage({kind:'fetch',id,url:String(url),init:clean});
  });
}
parentPort.on('message',message=>{
  const task=pending.get(message.id);if(!task)return;pending.delete(message.id);task.signal?.removeEventListener('abort',task.abort);
  if(message.error){task.reject(new Error('network_error'));return;}
  const body=Buffer.from(message.body);
  task.resolve({ok:message.status>=200&&message.status<300,status:message.status,url:message.url,headers:new Headers(message.headers),body:{cancel:async()=>{}},text:async()=>body.toString('utf8'),json:async()=>JSON.parse(body.toString('utf8')),arrayBuffer:async()=>body.buffer.slice(body.byteOffset,body.byteOffset+body.byteLength)});
});
const silent={log(){},warn(){},error(){}};
console.log=console.warn=console.error=()=>{};
globalThis.SokoulQuickJSWasm=fs.readFileSync(path.join(__dirname,'quickjs.wasm'));
const quickjs=require('./quickjs.cjs');
const {createExtractors}=require('./extractors-core.cjs');
const environment={fetch:remoteFetch,crypto:webcrypto,console:silent,atob,btoa,SokoulQuickJS:quickjs};
const extractors=createExtractors(environment);
(async()=>{
 try {
  const result=await extractors.extractSingle(workerData.source,workerData.embedUrl);
  const url=result.m3u8Url || result.hlsUrl || result.url;
  if(!result.success || typeof url!=='string'){parentPort.postMessage({kind:'result',result:{success:false,source:workerData.source,error:result.reason==='deleted'?'deleted':'no_media'}});return;}
  const parsed=new URL(url);if(!['http:','https:'].includes(parsed.protocol)||parsed.username||parsed.password||url.length>16384)throw new Error('invalid_media');
  const rule=await extractors.setupHeadersForService(workerData.source,url,result.referer || workerData.embedUrl);
  const headers={...rule?.headers};
  if(result.referer)headers.Referer=result.referer;
  if(result.origin)headers.Origin=result.origin;
  if(!headers['User-Agent'])headers['User-Agent']=userAgent || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36';
  const hlsSource=['voe','fsvid','vidzy','vidmoly','veev','lulustream','vidara','seekstreaming'].includes(workerData.source);
  // Explicit extractor hint first, then the URL path, then the host's usual delivery.
  // `m3u8Url` is also used for MP4 results by the core, so only an explicit format counts.
  const hinted=['hls','mp4'].includes(result.format)?result.format:null;
  const format=hinted || (/\.mp4(?:[?#]|$)/i.test(parsed.pathname)?'mp4':/\.m3u8(?:[?#]|$)|\/hls\//i.test(parsed.pathname)||hlsSource?'hls':'mp4');
  parentPort.postMessage({kind:'result',result:{success:true,url:parsed.href,format,headers,subtitles:result.subtitles || [],source:workerData.source}});
 }catch{parentPort.postMessage({kind:'result',result:{success:false,source:workerData.source,error:'extraction_failed'}});}
})();
