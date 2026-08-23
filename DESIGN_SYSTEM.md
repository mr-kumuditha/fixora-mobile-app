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


TechFix — UI/UX Enhancement Specification

Design System Authority

This document extends the existing TechFix Design System.

The existing design tokens defined above are the single source of truth.

Immutable Tokens

Do not change:

* Color palette
* Typography scale
* Font family
* Spacing scale
* Corner radius
* Icon system
* Existing motion timings

Do not create screen-specific design tokens.

Do not invent new colors, spacing values, typography sizes, corner radii, or icon styles.

All screens and reusable components must consume the centralized design system.

⸻

UI/UX Quality Standard

TechFix should have the visual quality of a modern premium commercial Android application.

The UI must feel:

* Premium
* Modern
* Clean
* Sophisticated
* Technical
* Trustworthy
* Minimal
* Responsive
* Consistent
* Highly polished

The application must not look like a default Android template or a basic student CRUD application.

Premium quality should come from:

* Strong visual hierarchy
* Consistent spacing
* Excellent typography
* High-quality imagery
* Consistent iconography
* Subtle depth
* Meaningful animations
* Excellent interaction states
* Clear information architecture
* Consistent component behavior

Avoid visual excess.

Do not use decoration simply to make a screen look busy.

⸻

Design Principles

1. Visual Hierarchy

Every screen must clearly communicate:

1. Where the user is
2. What information is important
3. What action the user should take next

Primary actions must be visually stronger than secondary actions.

Do not give equal visual weight to every element.

⸻

2. Simplicity

Prefer fewer high-quality components over many decorative components.

Do not place every section inside a card.

Use cards only when they create meaningful information grouping.

⸻

3. Consistency

The same interaction must look and behave the same throughout the application.

Examples:

* All primary buttons use the same component.
* All repair statuses use the same status component.
* All service cards follow the same structure.
* All section headers use the same typography.
* All icons use Material Symbols Rounded.
* All screens follow the same spacing system.

⸻

Design System Structure

Keep the centralized design system under:

core/designsystem/

Recommended organization:

core/
└── designsystem/
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   ├── Shape.kt
    │   ├── Spacing.kt
    │   ├── Elevation.kt
    │   └── Theme.kt
    │
    ├── components/
    │   ├── TechFixButton
    │   ├── TechFixCard
    │   ├── TechFixTextField
    │   ├── TechFixSearchBar
    │   ├── TechFixChip
    │   ├── TechFixStatusChip
    │   ├── TechFixTopBar
    │   ├── TechFixBottomNavigation
    │   ├── TechFixSectionHeader
    │   ├── TechFixServiceCard
    │   ├── TechFixRepairCard
    │   ├── TechFixBranchCard
    │   ├── TechFixTechnicianCard
    │   ├── TechFixPaymentCard
    │   ├── TechFixTimeline
    │   ├── TechFixImagePicker
    │   ├── TechFixSkeleton
    │   ├── TechFixEmptyState
    │   └── TechFixErrorState
    │
    └── motion/
        ├── MotionTokens
        ├── ScreenTransitions
        └── ComponentAnimations

If equivalent components already exist, reuse and improve them instead of creating duplicates.

⸻

Component System

All reusable components should support the appropriate states.

Buttons

Required variants:

* Primary
* Secondary
* Outlined
* Text
* Icon
* Destructive

Required states:

* Default
* Pressed
* Focused
* Disabled
* Loading

Primary actions include:

* Book Repair
* Continue
* Submit Request
* Confirm Appointment
* Pay Now
* Save

Buttons must provide subtle visual feedback.

Use a 0.96x press scale where appropriate.

⸻

Cards

Use a consistent 12dp radius.

Cards should support:

* Content
* Optional image
* Status
* Metadata
* Primary action
* Secondary action

Do not overload cards with unnecessary information.

⸻

Service Cards

Service cards should visually communicate:

* Service image
* Service name
* Short description
* Starting price
* Device category
* Action

Use consistent image ratios.

Images must load asynchronously.

Provide:

* Loading state
* Error state
* Placeholder state

⸻

Repair Cards

Repair cards should prioritize:

1. Device
2. Repair type
3. Current status
4. Repair ID
5. Appointment information
6. Next action

Example structure:

Device
Repair Type
Repair ID
Current Status
Branch
Appointment
Track Repair

Do not display unnecessary metadata.

⸻

Status Components

Use one reusable status component throughout the application.

Supported states may include:

* Submitted
* Pending
* Confirmed
* Device Received
* Diagnosis
* Awaiting Approval
* Repair In Progress
* Quality Check
* Ready for Collection
* Payment Pending
* Completed
* Cancelled
* Failed

Status colors must use the existing semantic color tokens.

Never create screen-specific status colors.

Important statuses must not rely on color alone.

Use:

* Icon
* Text
* Color

⸻

Repair Timeline

The repair timeline is a signature TechFix component.

It must clearly distinguish:

* Completed stages
* Current stage
* Upcoming stages

Example:

✓ Request Submitted
│
✓ Appointment Confirmed
│
● Diagnosis
│
○ Repair In Progress
│
○ Quality Check
│
○ Ready for Collection
│
○ Completed

When a status changes:

* Animate the current node
* Animate the connecting line
* Animate the status chip

Do not replace the entire timeline abruptly.

⸻

Navigation

Use a modern Material 3 navigation structure.

Customer navigation should remain simple and focused.

Recommended destinations:

* Home
* Services
* Book
* Repairs
* Profile

Selected navigation icons:

* Filled Material Symbols Rounded

Inactive icons:

* Outlined Material Symbols Rounded where available

Navigation should remain visually stable between screens.

Do not use excessive navigation animations.

⸻

Top App Bars

Top app bars should remain minimal.

Use:

* Back navigation
* Screen title
* Contextual actions only when required

Avoid placing many icons in the top bar.

⸻

Home Screen

The Home screen should be personalized and action-oriented.

Recommended hierarchy:

Greeting
↓
Active Repair
↓
Primary Repair Action
↓
Repair Services
↓
Nearby Branch
↓
Recent Repairs

The primary user action should be immediately visible.

Do not turn the Home screen into a statistics dashboard.

⸻

Booking Experience

The booking process should feel like a guided workflow.

Recommended structure:

Device
↓
Problem
↓
Images
↓
Location
↓
Branch
↓
Appointment
↓
Review
↓
Confirmation

The user must always understand:

* Current step
* Completed steps
* Remaining steps
* Next action

Use clear progress indication.

Avoid presenting a very long single form.

⸻

Form Design

Forms must use:

* Clear labels
* Helpful hints
* Correct keyboard types
* Validation
* Focus states
* Disabled states
* Loading states

Validation messages should be short and understandable.

Do not expose technical exceptions.

⸻

Search

Search must provide clear states:

* Default
* Focused
* Searching
* Results
* No Results
* Error

Search results should transition smoothly.

Use existing search and filter components consistently.

⸻

Filters

Use Material 3 chips.

Selected filters should use:

Primary Container

Do not introduce new filter colors.

⸻

Image UX

Image functionality is an important part of the repair workflow.

Support:

* Camera
* Gallery
* Preview
* Remove
* Upload progress
* Upload failure
* Retry

Use consistent image proportions.

Images must not be distorted.

⸻

Image Presentation

Use high-quality images for:

* Repair services
* Devices
* Repair samples
* Branches where appropriate
* User profile images

Image presentation must use:

* Consistent aspect ratios
* Appropriate cropping
* Correct corner radius
* Loading placeholder
* Error fallback

Do not use random image styles between screens.

⸻

Full-Screen Image Viewer

When viewing a repair image:

* Preserve original aspect ratio
* Provide clear close/back action
* Use smooth entry/exit transition
* Support dark and light themes correctly

Shared-element transitions may be used where they improve continuity.

⸻

GPS and Branch UI

Branch recommendations should feel intelligent and useful.

Recommended structure:

Recommended Branch
TechFix Colombo
Distance
Technician availability
Required parts availability
View on Map

The recommended branch should receive stronger visual emphasis.

Do not change the existing branch-selection business logic during UI work.

⸻

Map UI

Maps should provide useful context without dominating the screen.

Use:

* Current location
* Branch markers
* Selected branch
* Distance
* Branch information
* Directions action

A bottom sheet or floating information card may be used.

Avoid covering excessive map area.

⸻

Payment UI

The payment interface should feel realistic and polished while remaining a simulated payment system.

Recommended flow:

Payment Summary
↓
Payment Method
↓
Payment Details
↓
Review
↓
Processing
↓
Success / Failure
↓
Receipt

Payment processing should have a clear visual state.

Success should use subtle motion.

Do not use excessive celebration effects.

⸻

Success States

Successful actions should have clear confirmation.

Examples:

* Booking successful
* Payment successful
* Repair completed
* Profile updated

Use:

* Appropriate icon
* Clear title
* Supporting message
* Relevant next action

Use subtle success animation.

⸻

Loading States

Every data-driven screen must support:

Loading → Content

For content-heavy screens use skeleton loading rather than a full-screen spinner.

Skeletons should match the structure of the final content.

When content loads, use crossfade.

Do not introduce artificial loading delays.

⸻

Empty States

Every important list must have a meaningful empty state.

Examples:

* No Active Repairs
* No Repair History
* No Notifications
* No Search Results
* No Saved Devices
* No Appointments

Use:

* Relevant Material Symbol or approved illustration
* Short explanation
* Relevant action where appropriate

Do not use only:

No Data

⸻

Error States

Errors must be understandable and actionable.

Example:

Something went wrong
We couldn't load your repairs.
Try Again

Never show:

* Stack traces
* Firebase exceptions
* SQL errors
* HTTP response bodies
* Internal technical messages

⸻

Motion System

Motion should communicate:

* Navigation
* State change
* Selection
* Completion
* Spatial relationships

Motion must never exist only for decoration.

Screen Transitions

Top-level destinations:

Fade-through, 200ms

Drill-down navigation:

Subtle slide or shared-element transition

⸻

Component Motion

Buttons:

0.96x press scale

Content loading:

Crossfade

Status changes:

Animate status chip and timeline

Bottom sheets:

Use Material 3 sheet motion

All custom animations should remain below 300ms.

⸻

List Motion

On first appearance only:

* Fade in
* Slight upward movement
* Small stagger

Example:

Item 1 → 0ms
Item 2 → 30ms
Item 3 → 60ms
Item 4 → 90ms

Do not replay entrance animations during scrolling or unnecessary recomposition.

⸻

Shared Element Motion

Use shared-element transitions where they improve the user’s understanding of navigation.

Recommended:

Service Card
→ Service Details
Repair Card
→ Repair Details
Image Thumbnail
→ Full Screen Image
Profile Image
→ Profile Details

Do not use shared-element animation everywhere.

⸻

Micro-Interactions

Use subtle interaction feedback for:

* Selection
* Favorite
* Toggle
* Image added
* Image removed
* Booking completion
* Payment completion
* Status changes

Avoid excessive bouncing or decorative movement.

⸻

Haptic Feedback

Use haptic feedback selectively for meaningful actions.

Appropriate examples:

* Important confirmation
* Successful booking
* Successful payment
* Important selection

Do not use haptics for every interaction.

⸻

Dark Mode

Dark mode is a separately designed theme.

Do not invert the light theme.

Use the existing dark-mode tokens exactly.

Check:

* Background
* Surface
* Surface Variant
* Text
* Icons
* Borders
* Status colors
* Navigation
* Dialogs
* Bottom sheets
* Images

Dark mode must maintain visual hierarchy through surface tones rather than heavy shadows.

⸻

Accessibility

All interactive UI must have:

* Appropriate touch targets
* Meaningful content descriptions
* Readable text
* Sufficient contrast
* Clear focus states

Never communicate critical information through color alone.

Example:

Failed status:

Error color + error icon + “Failed” text

not just a red indicator.

⸻

Responsive UI

The application must remain usable across different Android phone sizes.

Avoid:

* Fixed screen widths
* Absolute positioning where unnecessary
* Text clipping
* Overflowing buttons
* Non-scrollable long content

Use responsive Compose layouts.

⸻

Modern Android UI Libraries

Use modern Android libraries where they provide clear value.

Preferred:

* Jetpack Compose
* Material 3
* Material Symbols Rounded
* Navigation Compose
* Compose Animation
* Material 3 Adaptive where appropriate
* Coil for image loading
* CameraX for camera functionality
* Google Maps Compose for maps

Do not add dependencies unnecessarily.

Before adding a library:

1. Check whether the project already includes equivalent functionality.
2. Check whether AndroidX or Material 3 already provides it.
3. Add a dependency only when it provides a meaningful benefit.
4. Keep dependency versions compatible with the existing project.

Do not combine multiple competing UI libraries.

⸻

Reusable Component Rule

When the same UI pattern appears on multiple screens, extract it into a reusable component.

Examples:

* Status chips
* Service cards
* Repair cards
* Buttons
* Section headers
* Search bars
* Empty states
* Loading states
* Image pickers
* Timeline items

Do not create a reusable component for a pattern that appears only once unless there is a clear architectural reason.

⸻

Compose Quality

When using Jetpack Compose:

* Prefer reusable stateless components
* Hoist state where appropriate
* Use stable list keys
* Avoid unnecessary recomposition
* Keep business logic outside composables
* Keep UI state clear
* Use previews for reusable components
* Avoid large monolithic composables

UI improvements must not move business logic into UI code.

⸻

Preview Requirements

Reusable components should have previews for:

* Light mode
* Dark mode
* Default state
* Pressed/selected state where relevant
* Disabled state where relevant
* Loading state where relevant
* Long content where relevant

Previews should use the real TechFix design system.

Do not create separate preview-only colors or styles.

⸻

Performance

UI polish must not reduce application performance.

Avoid:

* Excessive recomposition
* Huge unoptimized images
* Unnecessary animations
* Excessive blur
* Heavy shadows
* Unnecessary network requests
* Large nested layouts

Images should be loaded at an appropriate size.

Animations should remain smooth on the physical Android device.

⸻

Screen Quality Checklist

Every completed screen should be checked for:

Visual

* Correct colors
* Correct typography
* Correct spacing
* Correct shapes
* Correct icons
* Correct image treatment
* Clear hierarchy

Interaction

* Pressed state
* Loading state
* Error state
* Empty state
* Disabled state
* Navigation
* Animation

Dark Mode

* Correct contrast
* Correct surface hierarchy
* Correct status colors

Accessibility

* Touch target size
* Content descriptions
* Text readability

Performance

* Smooth scrolling
* No unnecessary animations
* No large unoptimized images

⸻

Premium UI Definition

Premium does not mean:

* More colors
* More gradients
* More shadows
* More animations
* More glassmorphism
* More decoration

Premium means:

* Better hierarchy
* Better spacing
* Better typography
* Better imagery
* Better interaction
* Better consistency
* Better motion
* Better feedback
* Better restraint

⸻

UI Implementation Boundary

UI/UX improvements must not modify existing:

* Business logic
* Firebase architecture
* Supabase architecture
* SQLite/Room architecture
* Authentication logic
* GPS algorithms
* Payment logic
* Repair workflow logic
* Role permissions
* Database schema
* API contracts

If a visual improvement requires a functional or architectural change, identify the dependency before making the change.

⸻

Final UI Quality Standard

The completed TechFix application should feel like one coherent premium Android product.

All screens must share:

* One visual language
* One design system
* One icon system
* One typography system
* One spacing system
* One motion language
* One component language

The final UI should be:

Modern + Premium + Clean + Consistent + Fast + Accessible + Functional

without unnecessary visual complexity.