# Design System Specification

## 1. Overview & Creative North Star
**Creative North Star: The Lucid Professional**

This design system moves beyond the generic "SaaS dashboard" by embracing a philosophy of high-end editorial clarity. We treat data not as a series of boxes, but as information layered onto a sophisticated, light-filled workspace. By rejecting heavy structural lines and embracing tonal depth, we create an environment that feels expansive, quiet, and premium. 

The visual signature is defined by **intentional asymmetry**—utilizing generous whitespace (`spacing-24`) to separate the navigation from the canvas—and **layered translucency**. The goal is a "Lucid" experience where the interface feels like thin sheets of refined paper stacked in a clean, sunlit room.

---

## 2. Colors & Surface Logic

### The "No-Line" Rule
Traditional 1px solid borders are strictly prohibited for structural sectioning. You must define boundaries through **background color shifts**. For example, a sidebar using `surface_container_low` sits against a main canvas of `background`. This creates a sophisticated "edge" that is felt rather than seen.

### Surface Hierarchy & Nesting
Depth is achieved through a tiered layering system. Use the following logic to "nest" importance:
- **Level 0 (Base):** `background` (#f7f9fb) for the global canvas.
- **Level 1 (Navigation/Sidebar):** `surface_container_low` (#f2f4f6) to provide a soft offset.
- **Level 2 (Primary Cards):** `surface_container_lowest` (#ffffff) to make actionable areas "pop" against the background.
- **Level 3 (Search/Inputs):** `surface_container` (#eceef0) for recessed elements that need to feel "cut into" the surface.

### Signature Accents
- **The Core Blue:** Use `primary_container` (#4f46e5) for high-impact actions. For main CTAs, apply a subtle linear gradient from `primary` (#3525cd) to `primary_container` (#4f46e5) at a 135-degree angle to add "soul" and dimension.
- **Glassmorphism:** For floating modals or dropdown menus, use `surface_container_lowest` with 80% opacity and a `20px` backdrop-blur. This keeps the user grounded in the dashboard context while maintaining focus.

---

## 3. Typography
Our typography scale creates an authoritative hierarchy by mixing the functional **Inter** for utility and the expressive **Manrope** for editorial impact.

- **Display & Headlines (Manrope):** These are the "anchors." Use `display-md` for high-level data summaries (e.g., "$7,406,323"). The slightly geometric character of Manrope provides a modern, bespoke feel.
- **Titles & Labels (Inter):** Use `title-sm` for section headers and `label-md` for secondary metadata. Inter’s high x-height ensures maximum readability at small sizes within dense data tables.
- **Hierarchy through Tonal Contrast:** Do not rely solely on weight. Use `on_surface` (#191c1e) for primary data and `on_surface_variant` (#464555) for descriptions and secondary labels to create an effortless visual path for the eye.

---

## 4. Elevation & Depth

### The Layering Principle
Avoid "drop shadow" effects for standard cards. Instead, place a `surface_container_lowest` (Pure White) card on the `surface_container_low` (Light Grey) sidebar area to create a "lift" through color contrast alone.

### Ambient Shadows
When an element must float (e.g., an active Modal or a Hover state), use a tinted shadow to mimic natural light:
- **Shadow Token:** `0px 12px 32px rgba(53, 37, 205, 0.06)`. 
The inclusion of a tiny percentage of the `primary` color in the shadow ensures the elevation feels integrated with the brand's palette, rather than a generic "grey smudge."

### The "Ghost Border" Fallback
If high-density data requires a container (like an input field), use a **Ghost Border**: `outline_variant` (#c7c4d8) at **30% opacity**. This provides enough definition for accessibility without breaking the "No-Line" Rule.

---

## 5. Components

### Buttons
- **Primary:** Gradient fill (`primary` to `primary_container`), `rounded-md` (0.75rem).
- **Secondary:** `surface_container_high` background with `primary` text. No border.
- **Ghost/Tertiary:** No background. `primary` text. On hover, apply a `surface_container_lowest` background with an `ambient shadow`.

### Input Fields
Inputs should feel recessed. Use `surface_container` (#eceef0) with a `rounded-md` (0.75rem) corner. The active state should not use a thick border, but rather a `2px` "Ghost Border" using the `primary` token at 40% opacity.

### Cards & Lists
- **The "No-Divider" Rule:** Never use horizontal lines to separate list items. Use the **Spacing Scale** (`spacing-3` to `spacing-4`) to create breathing room. 
- **Zebra Layering:** If separation is required for large tables, use alternating background tints: `surface_container_lowest` and `surface_container_low`.

### Sidebar Navigation
- **Inactive State:** `on_surface_variant` text on `surface_container_low`.
- **Active State:** A `primary_container` vertical pill (4px wide, `rounded-full`) on the leading edge, with a background shift to `primary_fixed_dim` at 20% opacity.

---

## 6. Do's and Don'ts

### Do
- **Do** use `spacing-16` or `spacing-20` for page margins to create a "gallery" feel.
- **Do** use `rounded-lg` (1rem) for large dashboard cards to soften the data density.
- **Do** use minimalist, thin-stroke icons (1.5px stroke weight) in `on_surface_variant`.

### Don't
- **Don't** use 100% black for text. Always use `on_surface`.
- **Don't** use sharp corners. The minimum radius should be `rounded-sm` (0.25rem).
- **Don't** use "Standard Blue" (#0000FF). Stick to the sophisticated, slightly violet-tinted `primary` tones provided.
- **Don't** use borders to separate the header from the content; use a subtle background color change or a very wide white-space gap.