import { chromium } from 'playwright'
import assert from 'node:assert/strict'
const b=await chromium.launch()
try {
 const p=await b.newPage({viewport:{width:1440,height:1000}});p.setDefaultTimeout(10000)
 await p.goto((process.env.LAB_BASE_URL||'http://127.0.0.1:19382')+'/experiments/design-vocabulary/index.html')
 await p.locator('#rows tr').first().waitFor()
 const probe=await p.evaluate(async()=>{
   const el=document.getElementById('sheet'),toggle=document.getElementById('immersive')
   const rect=()=>{const r=el.getBoundingClientRect();return [r.left,r.top,r.width,r.height]}
   const diff=(a,b)=>Math.max(...a.map((v,i)=>Math.abs(v-b[i])))
   const sleep=ms=>new Promise(r=>setTimeout(r,ms))
   const changes=[]
   for(let i=0;i<10;i++){const first=rect();toggle.click();const after=rect();changes.push(diff(first,after));await sleep(37)}
   await sleep(350)
   const animations=el.getAnimations().length
   const final=rect();toggle.click();await sleep(350);toggle.click();await sleep(350)
   return {changes,maxJump:Math.max(...changes),animations,settledGeometryDiff:diff(final,rect())}
 })
 console.log('FLIP',JSON.stringify(probe));assert.ok(probe.maxJump<=1);assert.equal(probe.animations,0);assert.ok(probe.settledGeometryDiff<=1)
 await p.locator('#spring-toggle').click();await p.waitForTimeout(50);await p.emulateMedia({reducedMotion:'reduce'})
 await p.waitForFunction(()=>document.getElementById('spring').dataset.position==='1'&&document.getElementById('spring').dataset.pending==='false')
 await p.locator('#immersive').click()
 assert.equal(await p.locator('#sheet').evaluate(e=>e.getAnimations().length),0)
 await p.locator('#immersive').click()
 await p.emulateMedia({reducedMotion:'no-preference'})
 await p.locator('#spring-toggle').click()
 const cleaned=await p.evaluate(()=>{window.dispatchEvent(new PageTransitionEvent('pagehide',{persisted:false}));return {spring:document.getElementById('spring').dataset.pending,flip:document.getElementById('sheet').getAnimations().length}})
 assert.deepEqual(cleaned,{spring:'false',flip:0});console.log('reduced-motion change/pagehide cleanup PASS')
}finally{await b.close()}
