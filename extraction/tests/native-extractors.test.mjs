import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import {webcrypto} from 'node:crypto';
import {buildNativeExtractors} from '../scripts/build-native-extractors.mjs';
import {fixtures,fixtureFetch,MEDIA} from './fixtures.mjs';
const output=await mkdtemp(path.join(os.tmpdir(),'sokoul-extractor-tests-'));
await buildNativeExtractors(output);
const require=createRequire(import.meta.url);
const native=require(path.join(output,'index.cjs'));
const source=await readFile(new URL('../runtime/extractors-core.js',import.meta.url),'utf8');
const silent={log(){},warn(){},error(){}};
function bareCore(fetchImpl) {
 const context=vm.createContext({fetch:fetchImpl,console:silent,crypto:webcrypto,TextEncoder,TextDecoder,URL,URLSearchParams,AbortController,setTimeout,clearTimeout,atob,btoa});
 vm.runInContext(source,context);
 return context.SokoulExtractionCore.createExtractors(context);
}
for(const fixture of fixtures) {
 test(`${fixture.source}: the bare core and the Node worker extract the same media and playback headers`,async()=>{
  const core=await bareCore(fixtureFetch(fixture)).extractSingle(fixture.source,fixture.url);
  assert.equal(core.success,true);assert.equal(core.m3u8Url || core.hlsUrl,fixture.expected);
  const requests=[];const result=await native.extract(fixture.url,{fetch:fixtureFetch(fixture,requests)});
  assert.equal(result.success,true,JSON.stringify(result));assert.equal(result.url,fixture.expected);assert.equal(result.source,fixture.source);
  assert.equal(result.format,fixture.expected.includes('.mp4')?'mp4':'hls');
  assert.ok(result.headers.Referer || result.headers['Referer']);assert.ok(requests.length>0);
 });
 test(`${fixture.source}: absent media is an ordinary failure`,async()=>{
  const result=await native.extract(fixture.url,{fetch:fixtureFetch({...fixture,body:'{}',api:'{}'})});
  assert.equal(result.success,false);assert.ok(['no_media','extraction_failed','deleted'].includes(result.error));
 });
 test(`${fixture.source}: an invalid server answer fails without media`,async()=>{
  const result=await native.extract(fixture.url,{fetch:async(url)=>{const response=new Response('<html>Service unavailable</html>',{status:503,headers:{'Content-Type':'text/html'}});Object.defineProperty(response,'url',{value:String(url)});return response;}});
  assert.equal(result.success,false);assert.equal(result.url,undefined);
 });
 test(`${fixture.source}: a stalled host times out with a sanitized code`,async()=>{
  const result=await native.extract(fixture.url,{timeoutMs:500,fetch:(_,init)=>new Promise((_resolve,reject)=>init.signal.addEventListener('abort',()=>reject(new Error('aborted https://secret.test/'))))});
  assert.equal(result.error,'timeout');assert.ok(!JSON.stringify(result).includes('secret.test'));
 });
}
test('ansembed.net, Anime-Sama\'s current host, uses the Vidmoly extractor',async()=>{
 const base=fixtures.find(f=>f.source==='vidmoly');const url='https://ansembed.net/embed-demo.html';
 assert.equal(native.detectHoster(url),'vidmoly');
 const result=await native.extract(url,{fetch:fixtureFetch({...base,url})});
 assert.equal(result.success,true,JSON.stringify(result));assert.equal(result.url,MEDIA);
});
test('unsupported URLs never open a transport',async()=>{
 let called=false;const result=await native.extract('https://unknown.test/player/1',{fetch:()=>{called=true;}});
 assert.equal(result.error,'unsupported_hoster');assert.equal(called,false);
 assert.equal(native.detectHoster('file:///vidmoly'),null);
});
test('worker deadline aborts pending transport and returns a sanitized code',async()=>{
 let aborted=false;const result=await native.extract('https://vidmoly.to/embed-demo.html',{timeoutMs:800,fetch:(_,init)=>new Promise((resolve,reject)=>{init.signal.addEventListener('abort',()=>{aborted=true;reject(new Error('secret https://example.test/key'));});})});
 assert.equal(result.error,'timeout');assert.equal(aborted,true);
});
test('cancelled jobs never start a transport',async()=>{
 const signal=AbortSignal.abort();const result=await native.extract('https://vidmoly.to/embed-demo.html',{signal,fetch:()=>assert.fail()});assert.equal(result.error,'cancelled');
});
test('a range redirect probe cancels the media body rather than downloading it',async()=>{
 let cancelled=false;
 const base=fixtures.find(f=>f.source==='sibnet');const normal=fixtureFetch(base);
 const result=await native.extract(base.url,{fetch:async(url,init)=>{
  if(!Object.keys(init.headers).some(k=>k.toLowerCase()==='range'))return normal(url,init);
  return {status:200,url:'https://cdn.sibnet.ru/final.mp4',headers:new Headers({'content-length':'999999999'}),body:{cancel:async()=>{cancelled=true;}},arrayBuffer:()=>assert.fail('media downloaded')};
 }});
 assert.equal(result.url,'https://cdn.sibnet.ru/final.mp4');assert.equal(cancelled,true);
});
test('oversized HTML is refused and transport errors never include signed URLs',async()=>{
 const result=await native.extract('https://vidmoly.to/embed-demo.html',{fetch:async()=>new Response('x',{headers:{'content-length':'999999999'}})});
 assert.equal(result.success,false);assert.ok(!JSON.stringify(result).includes('https://'));
});
test('QuickJS captures dynamic Fsvid player configuration under runtime limits',async()=>{
 const fixture=fixtures.find(f=>f.source==='fsvid');
 const html='<script>var x=["https://cdn.fixture.test/","master.m3u8?t=abcdefghijklmnop"].join("");jwplayer("v").setup({file:x});</script>';
 const result=await native.extract(fixture.url,{fetch:fixtureFetch({...fixture,body:html})});
 assert.equal(result.success,true,JSON.stringify(result));assert.equal(result.url,MEDIA+'?t=abcdefghijklmnop');
});

test('Uqload subtitles retain language, format and fetched contents',async()=>{
 const fixture=fixtures.find(f=>f.source==='uqload');
 const normal=fixtureFetch({...fixture,body:fixture.body+` tracks:[{file:"https://cdn.uqload.to/demo_fre.vtt",label:"French",kind:"captions"}]`});
 const result=await native.extract(fixture.url,{fetch:async(url,init)=>String(url).endsWith('.vtt')?new Response('WEBVTT\n\n00:00.000 --> 00:01.000\nBonjour'):normal(url,init)});
 assert.equal(result.success,true);assert.equal(result.subtitles[0].lang,'fr');assert.equal(result.subtitles[0].format,'vtt');assert.ok(result.subtitles[0].content.startsWith('WEBVTT'));
});
test('QuickJS stops a non-terminating player script without blocking the parent',async()=>{
 const fixture=fixtures.find(f=>f.source==='fsvid');const started=Date.now();
 const result=await native.extract(fixture.url,{timeoutMs:5000,fetch:fixtureFetch({...fixture,body:'<script>while(true){};jwplayer("v").setup({});</script>'})});
 assert.equal(result.success,false);assert.ok(Date.now()-started<4500);
});
test('native extraction requires the caller-owned egress transport',async()=>{
 const result=await native.extract('https://vidmoly.to/embed-demo.html');assert.equal(result.error,'transport_unavailable');
});
