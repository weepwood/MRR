import test from 'node:test'
import assert from 'node:assert/strict'
import { createSpringController, createFlipController } from './motion.mjs'
import { springStep } from './model.mjs'
function clock() {
  let time=0, id=0; const queue=new Map()
  return { now:()=>time, request:fn=>{queue.set(++id,fn);return id}, cancel:i=>queue.delete(i), queue,
    advance(ms,run=true){ time+=ms; if(run){const q=[...queue.values()];queue.clear();q.forEach(fn=>fn(time))} } }
}
const near=(a,b)=>assert.ok(Math.abs(a-b)<1e-8)
test('long frame uses elapsed wall time, no lost time',()=>{
 const c=clock(),s=createSpringController({...c,draw:()=>{}})
 s.retarget(1);c.advance(240);const expected=springStep({x:0,v:0},1,.24)
 near(s.snapshot().x,expected.x);near(s.snapshot().v,expected.v)
})
test('mid-frame retarget integrates old target and inherits velocity',()=>{
 const c=clock(),s=createSpringController({...c,draw:()=>{}})
 s.retarget(1);c.advance(73,false);s.retarget(0)
 const expected=springStep({x:0,v:0},1,.073)
 near(s.snapshot().x,expected.x);near(s.snapshot().v,expected.v);assert.equal(c.queue.size,1)
})
test('reduced motion and settle cancel outstanding work',()=>{
 const c=clock();let reduced=false,finished=0;const s=createSpringController({...c,draw:()=>{},reduced:()=>reduced,settled:()=>finished++})
 s.retarget(1);c.advance(40);reduced=true;s.retarget(0);assert.deepEqual(s.snapshot(),{x:0,v:0,target:0,pending:false});assert.equal(c.queue.size,0);assert.equal(finished,1)
})
test('dispose leaves no pending frame and future retarget is inert',()=>{
 const c=clock(),s=createSpringController({...c,draw:()=>{}})
 s.retarget(1);s.dispose();s.retarget(0);c.advance(1000);assert.equal(c.queue.size,0);assert.equal(s.snapshot().target,1)
})
test('FLIP feature fallback changes layout without creating animation',()=>{
 let changed=false;const el={getBoundingClientRect:()=>({left:0,top:0,width:10,height:10})};const flip=createFlipController(el)
 flip.transition(()=>{changed=true});assert.ok(changed);assert.equal(flip.pending(),false)
})
