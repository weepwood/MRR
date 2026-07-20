// Keep administrative interactions brief: motion should explain state changes,
// not delay access to medical-record workflows.
export const motionDurations = {
  instant: 0.1,
  fast: 0.14,
  standard: 0.18,
  collapse: 0.24,
}

export const motionEasings = {
  emphasized: [0.22, 1, 0.36, 1] as [number, number, number, number],
}

export const motionSprings = {
  interaction: {
    type: 'spring' as const,
    stiffness: 420,
    damping: 32,
    mass: 0.7,
  },
  layout: {
    type: 'spring' as const,
    stiffness: 400,
    damping: 34,
    mass: 0.75,
  },
}

export function entranceDelay(index: number, step = 0.035, maximum = 0.18) {
  return Math.min(Math.max(index, 0) * step, maximum)
}
