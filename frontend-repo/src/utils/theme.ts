/**
 * Theme Utility
 * Handles dynamic CSS variable manipulation for runtime theme customization.
 */

/**
 * Apply theme settings to the document root (:root).
 * @param settings Theme configuration object
 */
export const applyTheme = (settings: Record<string, any>) => {
  if (typeof document === 'undefined') return

  const root = document.documentElement

  // Primary Action Color
  if (settings.primaryColor) {
    root.style.setProperty('--pmr-color-action-primary', settings.primaryColor)
    
    // Auto-generate some variations if needed (or just let the bridge handle it)
    // For Element Plus, we often need light/dark variations
    // Here we'll just set the main one, as our element-plus.css bridge 
    // is designed to follow this token.
  }

  // Border Radius
  if (settings.borderRadius !== undefined) {
    const radius = Number(settings.borderRadius)
    root.style.setProperty('--pmr-radius-xl', `${radius}px`)
    
    // Scale other radii proportionally
    root.style.setProperty('--pmr-radius-2xl', `${radius + 2}px`)
    root.style.setProperty('--pmr-radius-lg', `${radius - 2}px`)
    root.style.setProperty('--pmr-radius-md', `${radius - 4}px`)
    root.style.setProperty('--pmr-radius-sm', `${radius - 6}px`)
  }
}
