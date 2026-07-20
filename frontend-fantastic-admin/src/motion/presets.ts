export const motionDurations = {
  instant: 0.1,
  fast: 0.14,
  standard: 0.18,
  collapse: 0.24,
} as const

export const motionEasings = {
  emphasized: [0.22, 1, 0.36, 1],
} as const

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
} as const

export function entranceDelay(index: number, step = 0.035, maximum = 0.18) {
  return Math.min(Math.max(index, 0) * step, maximum)
}
