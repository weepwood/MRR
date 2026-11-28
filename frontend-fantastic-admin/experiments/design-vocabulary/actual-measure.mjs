import { chromium } from 'playwright'
import { readFile, mkdir } from 'node:fs/promises'
import { allocateColumns } from './model.mjs'
const base=process.env.LAB_BASE_URL||'http://127.0.0.1:19382'
const out=process.env.LAB_OUTPUT||'/tmp/mrr-design-vocabulary-r4/measure'
await mkdir(out,{recursive:true})
const storage=JSON.parse(await readFile(process.env.LAB_AUTH||'e2e/.auth/admin.json','utf8'))
for(const o of storage.origins) if(o.origin==='http://127.0.0.1:9000')o.origin=base
const b=await chromium.launch()
try {
 for(const [palette,scheme] of [['mrr','light'],['gr8r','dark']]){
  const c=await b.newContext({storageState:storage,viewport:{width:1920,height:1000},hasTouch:true})
  const p=await c.newPage();p.setDefaultTimeout(15000)
  await p.route('**/api/v1/settings/ui',r=>r.fulfill({json:{code:200,data:{}}}))
  await p.addInitScript(({palette,scheme})=>localStorage.setItem('MRR-ADMIN:app-settings',JSON.stringify({app:{themePalette:palette,colorScheme:scheme,enableMotion:false},menu:{mode:'single'},layout:{enableMobileAdaptation:true}})),{palette,scheme})
  await p.goto(base+'/records');const table=p.locator('.records-table');await table.waitFor()
  for(const width of [1920,1440,390]){
   await p.setViewportSize({width,height:1000});await p.waitForTimeout(350)
   const m=await table.evaluate(el=>{
    const hs=[...el.querySelectorAll('.el-table__header thead tr:first-child th:not(.gutter)')]
    return {container:el.clientWidth,columns:hs.map(h=>({name:h.textContent.trim(),width:h.getBoundingClientRect().width})),scroll:el.querySelector('.el-scrollbar__wrap')?.scrollWidth,doc:document.documentElement.scrollWidth}
   })
   const elastic={'文件名':{min:240,max:640,weight:3},'目录':{min:140,max:320,weight:1},'迁移状态':{min:132,max:200,weight:0}}
   const budget=m.columns.map(c=>elastic[c.name]||{min:c.width,max:c.width,weight:0})
   const alt=allocateColumns(budget,m.container)
   console.log('TABLE',palette,scheme,width,JSON.stringify({...m,alternative:m.columns.map((c,i)=>({name:c.name,width:+alt.widths[i].toFixed(1)})),alternativeTotal:alt.total}))
  }
  await p.setViewportSize({width:1440,height:900});await p.goto(base+'/archive-workbench')
  const search=p.getByRole('textbox',{name:'病案查询值'});await search.fill('26070000');await search.press('Enter')
  await p.getByRole('group',{name:'有效影像列表'}).getByRole('button').first().click()
  const img=p.getByAltText('待核对影像');await img.waitFor();await p.waitForFunction(()=>{const e=document.querySelector('img[alt="待核对影像"]');return e?.complete&&e.naturalWidth>0})
  const measure=()=>p.evaluate(()=>{
   const vp=document.querySelector('.scan-viewport'),bar=document.querySelector('.scan-viewer-toolbar'),a=vp.getBoundingClientRect(),b=bar.getBoundingClientRect()
   const btn=[...bar.querySelectorAll('button')].map(e=>({name:e.textContent.trim(),w:e.offsetWidth,h:e.offsetHeight,disabled:e.disabled}))
   const lum=color=>{const ctx=document.createElement('canvas').getContext('2d');ctx.fillStyle=color;ctx.fillRect(0,0,1,1);const rgb=[...ctx.getImageData(0,0,1,1).data].slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4});return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722}
   const c=bar.querySelector('button[aria-pressed="true"]'),s=c&&getComputedStyle(c)
   const bg=c&&(()=>{const ctx=document.createElement('canvas').getContext('2d'),chain=[];for(let e=c;e;e=e.parentElement)chain.unshift(getComputedStyle(e).backgroundColor);ctx.fillStyle='#fff';ctx.fillRect(0,0,1,1);for(const color of chain){ctx.fillStyle=color;ctx.fillRect(0,0,1,1)}return 'rgb('+[...ctx.getImageData(0,0,1,1).data].slice(0,3).join(',')+')'})()
   return {viewport:{w:a.width,h:a.height,area:a.width*a.height},toolbar:{w:b.width,h:b.height,overlap:Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top))},buttons:btn,selectedContrast:s?(Math.max(lum(s.color),lum(bg))+.05)/(Math.min(lum(s.color),lum(bg))+.05):null,doc:document.documentElement.scrollWidth,coarse:matchMedia('(pointer: coarse)').matches}
  })
  const before=await measure();await p.getByRole('button',{name:'专注原图'}).click();await p.waitForTimeout(200);const focused=await measure()
  console.log('READER',palette,scheme,1440,JSON.stringify({before,focused,ratio:focused.viewport.area/before.viewport.area}));await p.screenshot({path:out+'/'+palette+'-reader-1440.png',fullPage:true})
  for(const width of [1100,760,390]){await p.setViewportSize({width,height:900});await p.waitForTimeout(350);console.log('READER',palette,scheme,width,JSON.stringify(await measure()));if(width===390)await p.screenshot({path:out+'/'+palette+'-reader-390.png',fullPage:true})}
  await c.close()
 }
}finally{await b.close()}
