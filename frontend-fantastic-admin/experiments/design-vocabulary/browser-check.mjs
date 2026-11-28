import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
const dir=process.env.LAB_OUTPUT || '/tmp/mrr-design-vocabulary-r3'
await mkdir(dir,{recursive:true})
const browser=await chromium.launch()
try {
 const p=await browser.newPage({viewport:{width:1440,height:1000}});p.setDefaultTimeout(8000)
 const errors=[];p.on('pageerror',e=>errors.push(e.message))
 await p.goto((process.env.LAB_BASE_URL||'http://127.0.0.1:19381')+'/experiments/design-vocabulary/index.html')
 await p.locator('#rows tr').first().waitFor();assert.equal(await p.locator('#rows tr').count(),5)
 await p.locator('#next').click();assert.equal(await p.locator('#page-number').innerText(),'2 / 5')
 await p.locator('#query').fill('00940000');assert.match(await p.locator('#filter-state').innerText(),/尚未应用/);await p.locator('#apply').click();assert.equal(await p.locator('#rows tr').count(),1)
 await p.locator('#reset').click();await p.locator('#density').click();assert.equal(await p.locator('#density').getAttribute('aria-pressed'),'true');console.log('table/filter/pagination/density PASS')
 for(const theme of ['light','dark','warm','warm-dark']){
  await p.locator('#theme').selectOption(theme)
  for(const w of [1440,1100,760,390]){
   await p.setViewportSize({width:w,height:1000});await p.waitForTimeout(100)
   const data=await p.evaluate(()=>{
    const luminance=(color)=>{const ctx=document.createElement('canvas').getContext('2d');ctx.fillStyle=color;ctx.fillRect(0,0,1,1);const rgb=[...ctx.getImageData(0,0,1,1).data].slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4});return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722}
    const contrast=(a,b)=>{const v=[luminance(a),luminance(b)].sort((a,b)=>b-a);return (v[0]+.05)/(v[1]+.05)}
    const button=getComputedStyle(document.querySelector('#apply')),note=getComputedStyle(document.querySelector('.surface .note')),surface=getComputedStyle(document.querySelector('.surface'))
    return {vw:innerWidth,doc:document.documentElement.scrollWidth,table:document.querySelector('table').offsetWidth,viewport:document.querySelector('.table-scroll').clientWidth,stage:document.querySelector('#stage').clientWidth,bar:document.querySelector('.floating').offsetWidth,touchMin:Math.min(...[...document.querySelectorAll('.floating button')].map(e=>Math.min(e.offsetHeight,e.offsetWidth))),buttonContrast:contrast(button.color,button.backgroundColor),noteContrast:contrast(note.color,surface.backgroundColor)}
   });console.log(theme,w,JSON.stringify(data));assert.ok(data.doc<=w+1);assert.ok(data.bar<=data.stage);assert.ok(data.buttonContrast>=4.5);assert.ok(data.noteContrast>=4.5);if(w<=760)assert.ok(data.touchMin>=44)
   if(w===390||w===1440)await p.screenshot({path:`${dir}/lab-${theme}-${w}.png`,fullPage:true})
  }
 }
 await p.setViewportSize({width:1440,height:1000});await p.locator('#rail button').nth(4).click();assert.equal(await p.locator('#sheet-no').innerText(),'5')
 await p.locator('#immersive').click();await p.waitForTimeout(50);await p.locator('#immersive').click();await p.waitForTimeout(300);assert.equal(await p.locator('#immersive').getAttribute('aria-pressed'),'false')
 await p.locator('#immersive').focus();await p.keyboard.press('Tab');assert.equal(await p.evaluate(()=>document.activeElement.id),'spring-toggle');assert.equal(await p.locator('#spring-toggle').evaluate(e=>getComputedStyle(e).outlineStyle),'solid');await p.locator('#spring-toggle').press('Enter');await p.waitForTimeout(70);await p.locator('#spring-toggle').click();await p.waitForTimeout(60);await p.locator('#spring-toggle').click();await p.waitForTimeout(800);assert.equal(await p.locator('#spring-content').isVisible(),true);assert.equal(await p.locator('#spring').getAttribute('data-velocity'),'0')
 await p.emulateMedia({reducedMotion:'reduce'});await p.locator('#spring-toggle').click();assert.equal(await p.locator('#spring').getAttribute('data-position'),'0');assert.equal(await p.locator('#spring-content').isVisible(),false);assert.deepEqual(errors,[]);console.log('reader/FLIP reversal/spring/reduced-motion PASS')
}finally{await browser.close()}
