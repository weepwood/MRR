import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
const base=process.env.LAB_BASE_URL||'http://127.0.0.1:19382'
const b=await chromium.launch()
const results=[]
try {
 for(const rate of [1,4,6]){
  const p=await b.newPage({viewport:{width:1440,height:1000}})
  const cdp=await p.context().newCDPSession(p)
  await cdp.send('Emulation.setCPUThrottlingRate',{rate})
  await p.goto(base+'/experiments/design-vocabulary/index.html')
  await p.locator('#rows tr').first().waitFor()
  await p.locator('#sheet').scrollIntoViewIfNeeded()
  const metrics=await p.evaluate(async(rate)=>{
   const raf=()=>new Promise(r=>requestAnimationFrame(r)), sleep=ms=>new Promise(r=>setTimeout(r,ms))
   const quantile=(a,q)=>[...a].sort((x,y)=>x-y)[Math.min(a.length-1,Math.floor(a.length*q))]||0
   const idle=[];let prev=await raf()
   for(let i=0;i<30;i++){const t=await raf();idle.push(t-prev);prev=t}
   const nominal=quantile(idle,.5)
   const sheet=document.getElementById('sheet'),spring=document.getElementById('spring')
   const flipToggle=document.getElementById('immersive'),springToggle=document.getElementById('spring-toggle')
   const rect=()=>{const r=sheet.getBoundingClientRect();return {x:r.left,y:r.top}}
   const report=(samples)=>({frames:samples.length,p50:+quantile(samples,.5).toFixed(2),p95:+quantile(samples,.95).toFixed(2),max:+Math.max(...samples).toFixed(2),over50:samples.filter(x=>x>50).length,estimatedMissed:samples.reduce((n,x)=>n+Math.max(0,Math.round(x/nominal)-1),0)})
   async function run(toggle,read,block=false){
    let done=false;const intervals=[],speed=[],errors=[];const start=performance.now();let last=await raf(),old=read()
    const sampling=(async()=>{while(!done){const t=await raf(),v=read(),dt=t-last;if(dt>0){intervals.push(dt);speed.push(Math.abs(v.x-old.x)*1000/dt);if(v.velocity!=null&&old.velocity!=null&&v.target===old.target&&dt<=50&&v.pending&&old.pending)errors.push(Math.abs((v.x-old.x)*1000/dt-(v.velocity+old.velocity)/2))}last=t;old=v}})()
    for(let i=0;i<10;i++){toggle.click();if(block&&i===3){const until=performance.now()+120;while(performance.now()<until){/* deliberately inject long task */}}await sleep(37)}
    await sleep(850);done=true;await sampling
    return {...report(intervals),duration:Math.round(performance.now()-start),speedP95:+quantile(speed,.95).toFixed(3),speedMax:+Math.max(...speed).toFixed(3),velocityResidualP95:+quantile(errors,.95).toFixed(3)}
   }
   const flip=await run(flipToggle,rect)
   const springRead=()=>({x:Number(spring.dataset.position),velocity:Number(spring.dataset.velocity),target:springToggle.getAttribute('aria-expanded'),pending:spring.dataset.pending==='true'})
   const physical=await run(springToggle,springRead)
   const blocked=await run(springToggle,springRead,true)
   return {rate,nominal:+nominal.toFixed(2),flip,physical,blocked,remainingAnimations:sheet.getAnimations().length,pending:spring.dataset.pending,finalVelocity:Number(spring.dataset.velocity)}
  },rate)
  assert.equal(metrics.remainingAnimations,0);assert.equal(metrics.pending,'false');assert.equal(metrics.finalVelocity,0)
  console.log(JSON.stringify(metrics));results.push(metrics);await p.close()
 }
}finally{await b.close()}
await writeFile(process.env.LAB_OUTPUT||'/tmp/mrr-design-vocabulary-r5/motion-performance.json',JSON.stringify(results,null,2))
