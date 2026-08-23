# TechFix — Project Instructions for Claude Code

## Project
Native Android app, Kotlin, SDK 35. NIBM HND Software Engineering, Mobile App Dev CW1. Full requirements: `docs/techfix-requirement-analysis.md`. Full architecture and build order: `docs/techfix-requirements-architecture.md`. Read both before starting any block of work.

## Locked Decisions, Do Not Silently Change
- Native Android, Kotlin, SDK 35. No Flutter, no React Native, no web wrapper.
- Firebase Authentication for everyone (customers and staff), providers: email/password and Google Sign-In.
- Firebase Authentication plus Firestore and Firestore Security Rules own user identity, roles, and technician CRUD. This must work on the free Firebase plan without Cloud Functions or custom claims.
- Supabase remains for Postgres spare parts/spare-part stock and Storage repair images. The six original Supabase technician rows were copied to Firestore with the same UUIDs and remain as an untouched migration archive; do not delete them automatically.
- Room (SQLite) caches only the service catalog and one draft repair request. No sync queue.
- Admin, Branch Manager, and Technician are separate roles in the data model, sharing one staff screen set gated by a permission flag. Not three separate screen sets.
- GPS branch matching uses distance plus technician and spare-part availability, never distance alone.
- Payment is a simulated demo flow only. Never a real charge, always labeled as a demo.
- Camera and multi-image upload required for repair requests.
- Light and dark theme, both designed on purpose, not inverted.
- "Premium" means UI/UX polish only. No paywall, no subscription, anywhere.
- Timeline is 2 days. If a choice is between polish and finishing the mandatory + 5 deliverable areas, finish the requirements first.

## Working Rules
- Follow the newest explicit instruction from the user over an older assumption.
- Inspect existing code before changing it. Prefer extending over rewriting.
- Never claim something is done, tested, or working without having actually verified it. State clearly what level of verification was actually performed.
- Keep a recommendation labeled as a recommendation. Never convert it into a requirement silently.
- Preserve existing functionality unless removal is explicitly requested.
- Do not add dependencies, frameworks, or services beyond what's already decided above without asking first.
- Do not run destructive git commands (reset --hard, force push, clean -fd) without explicit authorization.
- Never expose or hardcode API keys, tokens, or credentials.
- When something fails, reproduce it and find the root cause before proposing a fix. Don't guess.
- If the user says "do not change anything else," only touch what the task requires.

## Build Order
See `docs/techfix-requirements-architecture.md` section 12 for the full 2-day, block-by-block order (Day 1: setup, auth, data model + seed data, catalog + booking start. Day 2: GPS matching, tracking, payment, staff, offline cache + QA + demo). Work through it in order unless told otherwise. If running behind, cut staff-side polish before customer-side or the matching logic.


## Reporting Style
Report status as a short bullet list. Don't restate context already in
CLAUDE.md or the docs. Only flag what changed, what's verified vs
compile-only, and what needs my input.

# TechFix — Design System

Concrete tokens. Use these exactly, don't invent new colors or spacing per screen. Lives in core/designsystem/.

## Color Palette

**Light mode**
| Token | Hex | Use |
|---|---|---|
| Primary | #4F46E5 | brand, primary buttons, active nav |
| Primary Container | #E0E1FC | selected chips, highlighted cards |
| Accent | #FF7A45 | key CTAs (Book Repair, Submit, Pay) |
| Success | #22C55E | ready/completed states |
| Warning | #F59E0B | pending/attention states |
| Error | #EF4444 | failed states, validation |
| Background | #F7F8FA | screen background |
| Surface | #FFFFFF | cards, sheets |
| Surface Variant | #EEF1F5 | secondary cards, input fields |
| Text Primary | #111827 | headings, body |
| Text Secondary | #6B7280 | captions, hints |
| Border | #E5E7EB | dividers, outlines |

**Dark mode** (not an inversion, separately tuned for contrast and depth)
| Token | Hex | Use |
|---|---|---|
| Primary | #8B93FF | brand, primary buttons, active nav |
| Primary Container | #2C2F5C | selected chips, highlighted cards |
| Accent | #FF9466 | key CTAs |
| Success | #34D399 | ready/completed states |
| Warning | #FBBF24 | pending/attention states |
| Error | #F87171 | failed states, validation |
| Background | #0F1115 | screen background |
| Surface | #1A1D24 | cards, sheets |
| Surface Variant | #23262E | secondary cards, input fields |
| Text Primary | #F3F4F6 | headings, body |
| Text Secondary | #9CA3AF | captions, hints |
| Border | #2A2E37 | dividers, outlines |

Reasoning: indigo primary reads as trustworthy and technical, the warm orange accent is reserved only for the action the user is meant to take next, so it stays meaningful instead of decorative. Status colors are desaturated slightly in dark mode so they don't glare against the dark background.

## Typography

One scale, one font family (Inter, bundled via Compose, not the default system font):
- Display: 28sp, SemiBold — screen titles like "Book a Repair"
- Title: 20sp, SemiBold — section headers, card titles
- Body: 16sp, Regular — main content
- Label: 13sp, Medium — captions, chip text, timestamps

## Spacing and Shape

- 8dp grid: 4, 8, 16, 24, 32
- Corner radius: 12dp for cards, 8dp for inputs/buttons, 20dp for bottom sheets and dialogs
- Elevation: subtle shadows in light mode, in dark mode use a lighter surface tone instead of shadow to show elevation (shadows barely read on dark backgrounds)

## Iconography

- One icon set throughout: Material Symbols Rounded
- Outlined weight for inactive/default state, Filled weight for selected/active state (e.g. bottom nav)
- No emoji as functional icons, inconsistent across devices and reads as unpolished
- Consistent 24dp icon size in lists and app bars, 20dp in chips/labels

## Motion

- Screen transitions: fade-through (200ms) between top-level destinations, shared-element or slide for drill-down navigation (list -> detail)
- Loading to content: crossfade, not a hard cut
- List items: staggered fade + slight upward slide on first appearance, skip on scroll/recompose
- Buttons: subtle scale-down (0.96x) on press, standard Material3 easing
- Status/timeline changes: animate the chip color and the connecting line, don't just swap state instantly
- Keep every animation under 300ms. Motion should confirm what happened, not slow the user down.

## Screen States (required on every data-driven screen)

Loading (skeleton, not a spinner, for content-heavy screens like the catalog and history), Empty (illustration/icon + short message + action if relevant), Error (short message + retry action, never a raw exception), Content.
s
