// Research-only: fixed medical identifiers/actions never shrink below their minimum.
export function allocateColumns(columns, available) {
  if (!Number.isFinite(available) || available < 0 || columns.some(c => !Number.isFinite(c.min) || !Number.isFinite(c.max) || !Number.isFinite(c.weight) || c.min < 0 || c.max < c.min || c.weight < 0)) throw new RangeError('Invalid column budget')
  const widths = columns.map(c => c.min)
  let remaining = Math.max(0, available - widths.reduce((a,b) => a+b,0))
  while (remaining > 1e-7) {
    const active = columns.map((c,i)=>({ ...c,i })).filter(c => c.weight > 0 && c.max - widths[c.i] > 1e-7)
    const weight = active.reduce((a,c)=>a+c.weight,0)
    if (!weight) break
    let spent = 0
    for (const c of active) { const n = Math.min(c.max-widths[c.i],remaining*c.weight/weight); widths[c.i]+=n; spent+=n }
    if (spent < 1e-7) break
    remaining-=spent
  }
  return { widths, total: widths.reduce((a,b)=>a+b,0) }
}
// Exact critically damped spring solution; retarget without replacing velocity.
export function springStep({ x,v }, target, dt, omega=18) {
  if (![x,v,target,dt,omega].every(Number.isFinite) || dt<0 || omega<=0) throw new RangeError('Invalid spring input')
  const y=x-target, b=v+omega*y, decay=Math.exp(-omega*dt)
  return { x:target+(y+b*dt)*decay, v:(v-omega*b*dt)*decay }
}
