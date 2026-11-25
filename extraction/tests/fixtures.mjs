import { createCipheriv } from 'node:crypto';
export const MEDIA='https://cdn.fixture.test/master.m3u8';
function lzwEncode(input) {
 const dictionary=new Map();let next=256,current='',out=[];
 for(const char of input){const combined=current+char;if(dictionary.has(combined)||combined.length===1){current=combined;continue;}out.push(current.length===1?current.charCodeAt(0):dictionary.get(current));dictionary.set(combined,next++);current=char;}
 if(current)out.push(current.length===1?current.charCodeAt(0):dictionary.get(current));return String.fromCharCode(...out);
}
const veevChallenge=lzwEncode('111');
const veevMedia=lzwEncode(Buffer.from(MEDIA).toString('hex').split('').reverse().join(''));
const seekCipher=createCipheriv('aes-128-cbc',Buffer.from('kiemtienmua911ca'),Buffer.from('1234567890oiuytr'));
const seekPayload=Buffer.concat([seekCipher.update(JSON.stringify({source:MEDIA})),seekCipher.final()]).toString('hex');
export const fixtures=[
 {source:'voe',url:'https://voe.sx/e/demo',body:`{"hls":"${MEDIA}"}`,expected:MEDIA},
 {source:'fsvid',url:'https://fsvid.lol/embed-demo.html',body:`<script>sources:[{file:"${MEDIA}?t=abcdefghijklmnop"}]</script>`,expected:MEDIA+'?t=abcdefghijklmnop'},
 {source:'vidzy',url:'https://vidzy.org/embed-demo.html',body:`<script>sources:[{file:"${MEDIA}?t=abcdefghijklmnop"}]</script>`,expected:MEDIA+'?t=abcdefghijklmnop'},
 {source:'vidmoly',url:'https://vidmoly.to/embed-demo.html',body:`sources: [{file: "${MEDIA}"}]`,expected:MEDIA},
 {source:'sibnet',url:'https://video.sibnet.ru/video123-test.html',body:'player.src([{src:"/v/123.mp4"}]);',expected:'https://video.sibnet.ru/v/123.mp4'},
 {source:'uqload',url:'https://uqload.to/embed-demo.html',body:'<script>file:"https://cdn.uqload.to/master.m3u8"</script>',expected:'https://cdn.uqload.to/master.m3u8'},
 {source:'veev',url:'https://veev.to/e/demo',body:` fc:'${veevChallenge}'`,api:JSON.stringify({file:{file_status:'OK',dv:[{s:veevMedia}]}}),expected:MEDIA},
 {source:'doodstream',url:'https://d0000d.com/e/demo',body:"/pass_md5/a/demo",api:'https://fixture.cloudflarestorage.com/video.mp4',expected:'https://fixture.cloudflarestorage.com/video.mp4'},
 {source:'lulustream',url:'https://lulustream.com/e/demo',body:`sources:[{file:"${MEDIA}"}]`,expected:MEDIA},
 {source:'vidara',url:'https://vidara.to/e/demo',body:JSON.stringify({streaming_url:MEDIA}),expected:MEDIA},
 {source:'seekstreaming',url:'https://embed4me.test/#demo',body:seekPayload,expected:MEDIA},
];
export function fixtureFetch(fixture,requests=[]) {
 return async (value,init={})=>{
  const url=String(value);requests.push({url,init});
  let body=fixture.body;
  if(fixture.source==='veev'&&url.includes('/dl?'))body=fixture.api;
  if(fixture.source==='doodstream'&&url.includes('/pass_md5/'))body=fixture.api;
  const bytes=Buffer.from(body);const response=new Response(bytes,{status:200,headers:{'Content-Type':'text/html'}});
  Object.defineProperty(response,'url',{value:url});return response;
 };
}
