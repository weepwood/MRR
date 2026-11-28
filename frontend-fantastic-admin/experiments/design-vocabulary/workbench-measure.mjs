import { chromium } from 'playwright'
import { readFile, mkdir } from 'node:fs/promises'
const base=process.env.LAB_BASE_URL||'http://127.0.0.1:19382', out=process.env.LAB_OUTPUT||'/tmp/mrr-design-vocabulary-r5/before'
await mkdir(out,{recursive:true})
const storage=JSON.parse(await readFile(process.env.LAB_AUTH||'e2e/.auth/admin.json','utf8'))
for(const o of storage.origins)if(o.origin==='http://127.0.0.1:9000')o.origin=base
const b=await chromium.launch()
try{
 for(const [palette,scheme] of [['mrr','light'],['gr8r','dark']]){
  const c=await b.newContext({storageState:storage,viewport:{width:390,height:900},hasTouch:true})
  const p=await c.newPage();p.setDefaultTimeout(15000)
  await p.route('**/api/v1/settings/ui',r=>r.fulfill({json:{code:200,data:{}}}))
  await p.addInitScript(({palette,scheme})=>localStorage.setItem('MRR-ADMIN:app-settings',JSON.stringify({app:{themePalette:palette,colorScheme:scheme,enableMotion:false},menu:{mode:'single'},layout:{enableMobileAdaptation:true}})),{palette,scheme})
  await p.goto(base+'/archive-workbench')
  const input=p.getByRole('textbox',{name:'病案查询值'});await input.fill('26070000');await input.press('Enter')
  await p.getByRole('group',{name:'有效影像列表'}).getByRole('button').first().waitFor()
  for(const width of [390,760,1440]){
   await p.setViewportSize({width,height:900});await p.waitForTimeout(250)
   const m=await p.evaluate(()=>{
    const header=document.querySelector('.archive-header-card'),counts=document.querySelector('.relation-count-grid'),nav=document.querySelector('.archive-section-nav'),img=document.querySelector('[data-archive-section="images"]'),rail=document.querySelector('.archive-search-rail')
    const a=header.getBoundingClientRect(),b=img.getBoundingClientRect()
    return {countsHeight:counts.getBoundingClientRect().height,navHeight:nav.getBoundingClientRect().height,headerToImages:b.top-a.top,railToImages:b.top-rail.getBoundingClientRect().top,doc:document.documentElement.scrollWidth,warningCount:document.querySelectorAll('.archive-detail-stack .el-alert').length}
   });console.log(palette,scheme,width,JSON.stringify(m));await p.screenshot({path:out+'/'+palette+'-'+width+'.png',fullPage:true})
  }
  await p.setViewportSize({width:390,height:900})
  await p.getByRole('button',{name:'定位影像核对'}).click()
  const section=p.getByRole('region',{name:'影像核对分区'})
  await section.getByRole('group',{name:'有效影像列表'}).getByRole('button').first().click()
  await p.getByRole('button',{name:'专注原图'}).click()
  const disclosure=section.locator('.scan-list-disclosure')
  const viewport=section.getByRole('region',{name:'原图滚动查看区'})
  const distance=()=>viewport.evaluate(el=>el.getBoundingClientRect().top-el.closest('[data-archive-section="images"]').getBoundingClientRect().top)
  const before=await distance()
  await p.screenshot({path:out+'/'+palette+'-focus-expanded-390.png',fullPage:true})
  await disclosure.click()
  await p.waitForTimeout(150)
  console.log('DISCLOSURE',palette,scheme,JSON.stringify({before,after:await distance(),expanded:await disclosure.getAttribute('aria-expanded')}))
  await p.screenshot({path:out+'/'+palette+'-focus-collapsed-390.png',fullPage:true})
  await c.close()
 }
}finally{await b.close()}
