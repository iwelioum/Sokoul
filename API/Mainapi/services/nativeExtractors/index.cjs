// Node environment adapter for the shared extraction algorithms (extraction/runtime).
'use strict';
const path = require('node:path');
const { Worker } = require('node:worker_threads');
const HOSTS = Object.freeze(['voe', 'fsvid', 'vidzy', 'vidmoly', 'sibnet', 'uqload', 'veev', 'doodstream', 'lulustream', 'vidara', 'seekstreaming']);
const MAX_BODY_BYTES = 2 * 1024 * 1024;
function validUrl(value) {
  try { const u = new URL(value); return ['http:', 'https:'].includes(u.protocol) && !u.username && !u.password && u.href.length <= 16384 ? u : null; } catch { return null; }
}
function detectHoster(value) {
  const url = validUrl(value);
  if (!url) return null;
  // Detection examines only the hostname/path, never a query containing another host's URL.
  const { createExtractors } = require('./extractors-core.cjs');
  return createExtractors({fetch:()=>{}, console:{log(){},warn(){},error(){}}}).detectEmbedType(url.origin + url.pathname + (url.hash || ''));
}
async function boundedBody(response, skip) {
  if (skip) { await response.body?.cancel(); return Buffer.alloc(0); }
  if (Number(response.headers?.get('content-length')) > MAX_BODY_BYTES) { await response.body?.cancel(); throw new Error('response_too_large'); }
  if (response.body?.getReader) {
    const reader = response.body.getReader(); const chunks=[]; let size=0;
    try { while (true) { const {done,value}=await reader.read(); if(done) break; size+=value.byteLength; if(size>MAX_BODY_BYTES) throw new Error('response_too_large'); chunks.push(Buffer.from(value)); } }
    finally { await reader.cancel().catch(()=>{}); }
    return Buffer.concat(chunks);
  }
  const body=Buffer.from(await response.arrayBuffer()); if(body.length>MAX_BODY_BYTES) throw new Error('response_too_large'); return body;
}
/** options.fetch is the per-extraction transport; callers retain the same egress for signed media playback. */
async function extract(embedUrl, options = {}) {
  const source=detectHoster(embedUrl);
  if(!source) return {success:false,source:null,error:'unsupported_hoster'};
  if(options.signal?.aborted) return {success:false,source,error:'cancelled'};
  const timeoutMs=Math.min(30000,Math.max(50,options.timeoutMs || 15000));
  if(typeof options.fetch!=='function')return {success:false,source,error:'transport_unavailable'};
  const transport=options.fetch;
  return new Promise(resolve=>{
    const controllers=new Map(); let done=false;
    const worker=new Worker(path.join(__dirname,'worker.cjs'), { workerData:{embedUrl,source}, resourceLimits:{ maxOldGenerationSizeMb:128, stackSizeMb:4 } });
    const finish=result=> { if(done)return;done=true;clearTimeout(timer);options.signal?.removeEventListener('abort',cancel);for(const c of controllers.values())c.abort();worker.terminate();resolve(result); };
    const cancel=()=>finish({success:false,source,error:'cancelled'});
    const timer=setTimeout(()=>finish({success:false,source,error:'timeout'}),timeoutMs);
    options.signal?.addEventListener('abort',cancel,{once:true});
    worker.on('error',()=>finish({success:false,source,error:'runtime_error'}));
    worker.on('exit',()=>{if(!done)finish({success:false,source,error:'runtime_exit'});});
    worker.on('message',async message=>{
      if(done)return;
      if(message.kind==='result') { finish(message.result);return; }
      if(message.kind==='abort') { controllers.get(message.id)?.abort();return; }
      if(message.kind!=='fetch') return;
      const controller=new AbortController();controllers.set(message.id,controller);
      try {
        if(!validUrl(message.url))throw new Error('invalid_url');
        const response=await transport(message.url,{...message.init,signal:controller.signal});
        // A one-byte redirect probe must never consume a full media response.
        const range=Object.entries(message.init.headers || {}).some(([key])=>key.toLowerCase()==='range');
        const body=await boundedBody(response,range);
        const headers=Object.fromEntries(response.headers?.entries?.() || []);
        if(!done)worker.postMessage({kind:'response',id:message.id,status:response.status,url:response.url || message.url,headers,body});
      } catch { if(!done)worker.postMessage({kind:'response',id:message.id,error:'network_error'}); }
      finally {controllers.delete(message.id);}
    });
  });
}
module.exports={extract,detectHoster,supportedHosts:HOSTS};
