# Student UI design system

The student V2 UI uses role-based CSS custom properties from
`src/assets/student-v2.css`. New student UI should use these tokens and shared
components instead of copying hex colors or page-header markup.

## Token model

Tokens are split into two layers:

1. Foundation tokens store raw palette values such as `--student-blue-600`.
2. Semantic tokens describe product intent, such as `--student-primary`,
   `--student-text-subtle`, or `--student-surface-hover`.

Application code should normally use semantic tokens. Foundation tokens are for
building or updating the theme and for rare data-visualization accents.

```css
/* Avoid: the meaning disappears and theme changes require a search. */
color: #2457d6;

/* Prefer: this element represents the primary action. */
color: var(--student-primary);
```

The scale also includes spacing, typography, control height, radius, elevation,
focus, motion, and z-index tokens. Reuse the closest scale value before adding a
new one.

## Shared patterns

- `StudentPageHeader.vue`: page eyebrow, title, subtitle, metadata, and action.
- `.student-surface`: standard elevated content container.
- `.student-list` and `.student-list-row`: tappable mobile list.
- `.student-stat-card`: summary metric with an optional navigation action.
- `.student-pill`: compact status label; use success, warning, or danger modifiers.
- `.student-empty-state`: empty and filtered-empty feedback.
- `.student-icon-button`: 48px accessible icon action.
- `.student-bottom-action` and `.student-sticky-action`: safe-area-aware mobile actions.

## Interaction rules

- Trigger navigation on `click`, not `pointerdown`; `:active` supplies pressed feedback.
- Keep interactive targets at least 44px high.
- Preserve `:focus-visible` and use `--student-focus-ring`.
- Use semantic success, warning, and danger colors consistently.
- Respect safe-area insets for fixed navigation, sheets, and sticky actions.
- Prefer a bottom sheet below 600px for modal tasks.

## Review checklist

- No new raw hex colors in student V2 views or components.
- No duplicated page header markup when `StudentPageHeader` fits.
- Layout works at 320px, 375px, 430px, and a tablet width.
- Text can wrap without horizontal overflow.
- Loading, empty, error, pressed, disabled, and focus states are covered.
- Teleported Element Plus overlays use a student overlay class so they inherit the theme.
