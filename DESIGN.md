# Dduo AI-Chat Design System

## Direction

Dduo AI-Chat is a focused AI workspace. The interface borrows the calm spatial
structure of Poe without copying its marketplace: a narrow activity rail,
optional context drawers, a quiet main canvas, and a composer that receives the
most visual emphasis.

The default scene is a user working in a dim room on a desktop or phone,
switching between chat, search, and knowledge-base tasks without visual noise.
The product uses a restrained dark theme with a single violet accent.

## Color

Use OKLCH tokens from `frontend/src/assets/styles/variables.css`. The accent
color should stay below 10% of the visible surface.

- Canvas: tinted near-black violet neutral.
- Raised surfaces: two graphite layers separated by subtle borders.
- Primary accent: violet for focus, selection, and primary actions.
- Semantic colors: green for success, amber for warning, red for destructive
  actions and errors.
- Avoid pure black, pure white, gradients, decorative blur, and heavy shadows.

## Typography

Use the system sans stack. UI hierarchy is compact:

- Product mark: 24-30px, weight 700.
- Workspace heading: 18-22px, weight 650.
- Section heading: 14-16px, weight 650.
- Body and controls: 14-16px.
- Metadata: 12-14px.

Prose and chat responses should not exceed `72ch`.

## Layout

- Desktop activity rail: `64px`.
- Desktop history drawer: `288px`, hidden by default.
- Main content: flexible width with `min-width: 0`.
- Chat reading and composer column: shared maximum `860px`.
- Mobile: activity rail is replaced by a left drawer opened from a `44px`
  menu button.

## Components

- Buttons use one vocabulary: quiet, primary, danger, and icon-only.
- Inputs use graphite surfaces, a one-pixel border, and a violet focus ring.
- Cards are reserved for grouped resources such as knowledge bases. Lists use
  rows, not nested cards.
- Modals remain for bounded forms and document previews only.
- Every interactive control needs default, hover, focus, active, disabled, and
  loading states where applicable.

## Motion

- Use `150-220ms` transitions with ease-out curves.
- Animate `transform` and `opacity`, not layout properties.
- Respect `prefers-reduced-motion`.
- Do not animate page entry for decoration.

## Responsive

- Keep touch targets at least `44 x 44px`.
- Chat remains the primary mobile task.
- Search and knowledge base remain independent workspaces.
- Knowledge base detail becomes a drill-in view on narrow screens.
