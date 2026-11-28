import { allocateColumns } from './model.mjs'
import { createFlipController, createSpringController } from './motion.mjs'
import { applyThemePalette, gr8rPrimary } from '../../src/utils/theme-palette.ts'
import { buildThemeColorScale, toCssVariables } from '../../src/utils/theme-color.ts'
import { lightTheme, darkTheme } from '../../themes/index.ts'
// Vite's app-loading plugin also injects its shell into this separate HTML entry.
// The research page has no router, so it owns removal of its own loading overlay.
document.querySelector('[data-app-loading]')?.remove()
const $=id=>document.getElementById(id)
const reduced=matchMedia('(prefers-reduced-motion: reduce)')
const columns=[{min:56,max:56,weight:0},{min:132,max:190,weight:1},{min:140,max:420,weight:3},{min:92,max:120,weight:1},{min:100,max:160,weight:1}]
const rows=Array.from({length:23},(_,i)=>[i+1,String(940000+i).padStart(8,'0'),i%2?'合成科室 · 长名称测试':'合成科室',12+i,i%4?'正常':'待核对'])
let applied='',page=1;const size=5
function layout(){const a=allocateColumns(columns,$('cols').closest('.table-scroll').clientWidth);$('cols').innerHTML=a.widths.map(w=>`<col style="width:${w}px">`).join('');$('cols').parentElement.style.width=a.total+'px'}
function render(){const filtered=rows.filter(r=>r[1].includes(applied)), pages=Math.max(1,Math.ceil(filtered.length/size));page=Math.max(1,Math.min(page,pages));$('rows').replaceChildren(...filtered.slice((page-1)*size,page*size).map(r=>{const tr=document.createElement('tr');for(const val of r){const td=document.createElement('td');td.textContent=val;tr.append(td)}return tr}));$('count').textContent=`共 ${filtered.length} 条`;$('page-number').textContent=`${page} / ${pages}`;$('prev').disabled=page===1;$('next').disabled=page===pages;$('jump').max=pages;$('jump').value=page;$('filter-state').textContent=applied?'已应用：'+applied:'无筛选条件';layout()}
$('apply').onclick=()=>{applied=$('query').value.trim();page=1;render()};$('query').oninput=()=>{$('filter-state').textContent=$('query').value.trim()===applied?(applied?'已应用：'+applied:'无筛选条件'):'条件已修改，尚未应用'};$('query').onkeydown=e=>{if(e.key==='Enter')$('apply').click()};$('reset').onclick=()=>{$('query').value='';$('apply').click()};$('prev').onclick=()=>{page--;render()};$('next').onclick=()=>{page++;render()};$('go').onclick=()=>{page=Math.floor(Number($('jump').value))||1;render()};$('density').onclick=()=>{const on=$('cols').parentElement.classList.toggle('compact');$('density').setAttribute('aria-pressed',String(on))};const observer=new ResizeObserver(layout);observer.observe($('cols').closest('.table-scroll'));render()
$('theme').onchange=()=>{const t=$('theme').value,scheme=t.includes('dark')?'dark':'light',warm=t.startsWith('warm'),root=document.documentElement;root.classList.toggle('dark',scheme==='dark');applyThemePalette(root,'mrr',scheme);for(const [k,v] of Object.entries(scheme==='dark'?darkTheme:lightTheme))root.style.setProperty(k,v);if(warm)applyThemePalette(root,'gr8r',scheme);for(const [k,v] of Object.entries(toCssVariables(buildThemeColorScale(warm?gr8rPrimary(scheme):'#2563eb',scheme))))root.style.setProperty(k,v)};$('theme').onchange()
let selected=1,zoom=100;for(let i=1;i<=8;i++){const b=document.createElement('button');b.className='thumb';b.textContent=`第 ${i} 页`;b.setAttribute('aria-pressed',String(i===1));b.onclick=()=>{selected=i;$('sheet-no').textContent=i;for(const [j,el] of [...$('rail').children].entries())el.setAttribute('aria-pressed',String(j+1===selected))};$('rail').append(b)}
function setZoom(v){zoom=Math.max(50,Math.min(150,v));$('zoom-value').textContent=zoom+'%';$('sheet-content').style.transform=`scale(${zoom/100})`};$('zoom-in').onclick=()=>setZoom(zoom+10);$('zoom-out').onclick=()=>setZoom(zoom-10)
const flip=createFlipController($('sheet'),{reduced:()=>reduced.matches})
$('immersive').onclick=()=>flip.transition(()=>{const on=$('reader').classList.toggle('immersive');$('immersive').setAttribute('aria-pressed',String(on))})
let target=0
function draw({x,v,pending}) {
 const p=Math.max(0,Math.min(1,x)),el=$('spring')
 el.style.width=(124+Math.max(0,el.parentElement.clientWidth-124)*p)+'px'
 el.style.height=(38+110*p)+'px'
 el.dataset.position=String(x);el.dataset.velocity=String(v);el.dataset.pending=String(pending)
}
const spring=createSpringController({now:()=>performance.now(),request:fn=>requestAnimationFrame(fn),cancel:id=>cancelAnimationFrame(id),draw,reduced:()=>reduced.matches,settled:()=>{if(target){$('spring-content').hidden=false;$('spring-content').removeAttribute('inert')}}})
$('spring-toggle').onclick=()=>{
 target=1-target
 $('spring-toggle').setAttribute('aria-expanded',String(!!target))
 $('spring-symbol').textContent=target?'−':'＋'
 if(!target&&$('spring-content').contains(document.activeElement))$('spring-toggle').focus()
 $('spring-content').hidden=true;$('spring-content').setAttribute('inert','')
 spring.retarget(target)
}
function resize(){flip.cancel();draw(spring.snapshot())}
function preference(){if(reduced.matches){flip.cancel();spring.settle()}}
function hide(e){flip.cancel();spring.settle();if(!e.persisted){observer.disconnect();spring.dispose();window.removeEventListener('resize',resize);if(reduced.removeEventListener)reduced.removeEventListener('change',preference);else reduced.removeListener(preference)}}
window.addEventListener('resize',resize)
if(reduced.addEventListener)reduced.addEventListener('change',preference);else reduced.addListener(preference)
window.addEventListener('pagehide',hide)
draw(spring.snapshot())
