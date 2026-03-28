# Design System Document: The Clinical Lens

## 1. Overview & Creative North Star
**Creative North Star: "The Clinical Sanctuary"**

Medical interfaces are traditionally cluttered, anxiety-inducing, and cognitively taxing. This design system rejects the "dashboard-as-a-cockpit" trope. Instead, we embrace **The Clinical Sanctuary**—a high-end editorial approach to medical data. We treat patient records like a premium publication: authoritative, spacious, and meticulously organized.

By utilizing intentional asymmetry, we move away from rigid, boxy templates. Important clinical summaries might sit in a wide, airy column, while secondary vitals are tucked into a dense, high-contrast sidebar. We break the "grid-lock" by overlapping surface layers and using dramatic shifts in typography scale to guide the clinician’s eye to what matters most.

---

## 2. Colors & Surface Philosophy
The palette is anchored by **Trustworthy Deep Blue (#1A56DB)**, but its application is surgical. We use color to denote "Action" and "Authority," while the rest of the interface breathes through a sophisticated neutral scale.

### The "No-Line" Rule
**Borders are forbidden for sectioning.** To separate a patient’s history from their current meds, do not draw a line. Instead, shift the background color. Use `surface-container-low` for the page background and `surface-container-lowest` for the content modules. The transition of tone creates the boundary.

### Surface Hierarchy & Nesting
Treat the UI as a series of stacked, physical layers. 
- **Base Level:** `surface` (#f8f9fa) – The global canvas.
- **Section Level:** `surface-container-low` (#f3f4f5) – Grouping large areas of data.
- **Card/Module Level:** `surface-container-lowest` (#ffffff) – The primary focal point for data entry.
- **Floating Level:** Glassmorphism using `surface` at 80% opacity with a 12px backdrop blur for popovers and global search.

### The "Glass & Gradient" Rule
To prevent the "flat-and-boring" trap, use **Signature Textures**. Apply a subtle linear gradient (from `primary` to `primary_container`) on primary action buttons and high-level analytics cards. This depth signifies "Premium Utility" and separates professional tools from consumer apps.

---

## 3. Typography: The Editorial Scale
We use a dual-font strategy to balance clinical precision with human warmth.

- **The Voice (Manrope):** Used for `display`, `headline`, and `title` roles. Its geometric yet friendly curves provide a modern, high-end feel for patient names, section headers, and high-level stats.
- **The Data (Inter):** Used for `body` and `label` roles. Inter is the workhorse. It remains legible at `label-sm` (0.6875rem) for high-density medical tables and technical values.

**Hierarchy as Navigation:** A `headline-lg` patient name sitting next to a `body-sm` date of birth creates a clear, editorial hierarchy that reduces cognitive load faster than any icon could.

---

## 4. Elevation & Depth
Depth in this system is a result of **Tonal Layering**, not structural scaffolding.

- **The Layering Principle:** Instead of shadows, stack `surface-container-high` elements inside `surface-container-low` containers to create "wells" for secondary information.
- **Ambient Shadows:** For floating modals, use an "Ambient Glow." The shadow color is not black; it is a 6% opacity tint of `on_surface`.
    - *Spec:* `0px 12px 32px rgba(25, 28, 29, 0.06)`
- **The "Ghost Border" Fallback:** If a data table requires a border for alignment, use `outline_variant` at **15% opacity**. It should be felt, not seen.
- **Glassmorphism:** Use for persistent navigation or "Search" overlays. A `surface_container_lowest` background with 70% opacity and `blur(20px)` allows the clinical data to peak through, maintaining the user’s context.

---

## 5. Components

### High-Density Tables
*   **The Rule:** No vertical or horizontal lines. 
*   **Separation:** Use `3` (1rem) vertical spacing between rows. Use a `surface-container-low` hover state to highlight the active record.
*   **Typography:** Use `body-md` for primary data and `label-md` with `on_surface_variant` for metadata.

### Buttons & Inputs
*   **Primary Action:** A gradient of `primary` to `primary_container` with `DEFAULT` (0.5rem) rounded corners.
*   **Input Fields:** Use `surface_container_highest` for the field background with a "Ghost Border." Focus state shifts the border to 100% `primary`.
*   **Chips:** Use `secondary_container` for status badges (e.g., "Stable," "Discharged"). No borders; just soft, tonal fills.

### Medical Status Badges
*   **Critical:** `error_container` fill with `on_error_container` text.
*   **Stable:** `primary_fixed` fill with `on_primary_fixed` text.
*   **Pending:** `tertiary_fixed` fill with `on_tertiary_fixed` text.

### The "Clinical Timeline" (Custom Component)
Instead of a list, use a vertical "thread." Events are marked by `primary` dots connected by a `surface_dim` track, using `spacing.5` to create an airy, readable history of care.

---

## 6. Do’s and Don’ts

### Do:
*   **Do** use `spacing.8` (2.75rem) between major sections to let the clinician’s eyes rest.
*   **Do** use `title-lg` for section headers to provide a clear entry point for scanning.
*   **Do** use `full` (pill) rounding for status badges, but stick to `DEFAULT` (8px) for cards and containers.

### Don't:
*   **Don't** use pure black (#000) for text. Always use `on_surface` to keep the interface soft.
*   **Don't** use 1px solid dividers to separate content. If it feels messy, increase the `spacing` scale instead.
*   **Don't** use "Drop Shadows" on buttons. Use tonal shifts or the "Ambient Glow" for depth.
*   **Don't** crowd the "Search" bar. It should sit in a `surface_container_low` area with at least `spacing.6` of internal padding.