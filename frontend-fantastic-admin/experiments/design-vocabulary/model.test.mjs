import test from 'node:test'
import assert from 'node:assert/strict'
import { allocateColumns, springStep } from './model.mjs'
const cols=[{min:100,max:100,weight:0},{min:120,max:280,weight:2},{min:80,max:160,weight:1}]
const near=(a,b)=>assert.ok(Math.abs(a-b)<1e-8, `${a} != ${b}`)
test('minima survive narrow budget',()=>assert.deepEqual(allocateColumns(cols,200),{widths:[100,120,80],total:300}))
test('weighted width and cap redistribution',()=>{const a=allocateColumns(cols,480);near(a.total,480);near(a.widths[1],240);near(a.widths[2],140);assert.deepEqual(allocateColumns(cols,1000),{widths:[100,280,160],total:540})})
test('budget sweep preserves bounds, conservation and monotonicity',()=>{let prev=[0,0,0];for(let budget=0;budget<=1000;budget++){const {widths,total}=allocateColumns(cols,budget);near(total,Math.max(300,Math.min(540,budget)));widths.forEach((w,i)=>{assert.ok(w>=cols[i].min&&w<=cols[i].max+1e-8&&w>=prev[i]-1e-8)});prev=widths}})
test('invalid parameters rejected',()=>{assert.throws(()=>allocateColumns(cols,NaN));assert.throws(()=>springStep({x:0,v:0},1,-1))})
test('spring exact solution independent of frame subdivision',()=>{const a=springStep({x:0,v:0},1,.1);let b={x:0,v:0};for(let i=0;i<10;i++)b=springStep(b,1,.01);near(a.x,b.x);near(a.v,b.v)})
test('retarget preserves position and velocity at interruption',()=>{const a=springStep({x:0,v:0},1,.08);assert.deepEqual(springStep(a,0,0),a);const b=springStep(a,0,.001);assert.ok(b.x>a.x);assert.ok(Math.abs((b.x-a.x)/.001-a.v)<.2)})
test('multiple reversals settle without residual velocity',()=>{let s={x:0,v:0};for(const t of [1,0,1,0,1])s=springStep(s,t,.07);s=springStep(s,1,1);assert.ok(Math.abs(s.x-1)<1e-6&&Math.abs(s.v)<1e-5)})
