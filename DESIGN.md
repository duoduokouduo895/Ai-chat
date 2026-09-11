# Dduo AI-Chat design system

## Direction

Dduo is a focused AI workspace with a restrained graphite interface. The main
canvas stays quiet so chat, search, and knowledge tasks remain easy to scan.
The ASCII intro establishes the product identity without changing the workspace
layout or delaying repeat visits.

## Color and surfaces

Use the shared tokens in `frontend/src/assets/styles/variables.css`.

- Canvas and surfaces use neutral graphite tones.
- Text uses cool white and gray with clear hierarchy.
- Primary controls use white-on-graphite or graphite-on-white contrast.
- Semantic green, amber, and red remain reserved for status and errors.
- Surfaces use one-pixel borders instead of decorative blur or heavy shadows.

## Typography

- Product marks, ASCII graphics, compact labels, and metadata use the shared
  monospace stack.
- Conversation content and task controls use the system sans-serif stack.
- Chat prose should remain comfortably readable and avoid excessive line width.

## Layout

- Desktop uses a narrow activity rail, an optional history drawer, and a
  centered chat column.
- The composer and conversation share the same content width.
- Mobile replaces the activity rail with a drawer and preserves 44 px touch
  targets for primary actions.

## Components

- Inputs and message surfaces use 4 px corners and crisp focus treatment.
- Cards are reserved for grouped resources; ordinary content uses rows and
  dividers.
- Every interactive control must define keyboard focus, disabled, loading, and
  error states where applicable.
- Product identity uses text or line-based marks rather than decorative avatar
  images.

## Motion

- The first-session ASCII intro may use the full-screen canvas sequence.
- Repeat navigation and routine controls use short opacity or transform
  transitions.
- Motion must respect `prefers-reduced-motion`, clean up frame loops, and never
  block access to the real workspace after an error.

## Accessibility and responsive behavior

- Core workflows remain usable by keyboard and screen reader.
- Text and controls target WCAG AA contrast.
- Desktop and mobile share the same product hierarchy; narrow screens may alter
  navigation placement but not remove core capabilities.
