# Bluke improvements implementation ledger

Source reviewed: `Bluke Improvements.pdf` (13 pages). The document is treated as a product/design specification; it does not override repository safety, licensing, or build constraints.

## Scope and decisions

- Keep the current SDK, AGP, Kotlin, Compose BOM, and Material 3 versions. The UI uses the existing Material APIs to reproduce connected/segmented list treatment instead of adopting alpha-only components.
- Add no third-party dependency. The keyboard color picker is implemented with Compose primitives.
- Preserve existing preferences and defaults so an app update does not reset users' layouts, themes, audio, modes, or case colors.
- Keep keyboard geometry, character layout, keyboard theme, switch sound, input mode, and case color as separate concepts.
- Make tap cycle a setting and long-press open its configuration wherever a compact controller toolbar exposes both actions.

## Traceability

| ID | PDF page | Requirement | Resolution |
|---|---:|---|---|
| IMP-01 | 1 | Reorganize fragmented settings | Group the Settings landing page into Personalization, Controls & connection, and Support sections. |
| IMP-02 | 1 | Reuse update notices for required actions | Keep the version-aware update dialog and give descriptor refresh its own explicit action copy. |
| IMP-03 | 1 | Add novice-friendly FAQ/troubleshooting | Expand Help into connection, pairing repair, Steam/controller setup, browser/native D-pad behavior, and known limitations. |
| IMP-04 | 1 | Add a one-time controller guide | Show an opt-out first-launch guide before opening Gamepad mode; Help remains the permanent entry point. |
| IMP-05 | 1 | Context-aware troubleshooting nudges | Offer Help after three connection actions, a 12-second unsuccessful attempt, or four connection-state transitions within two minutes, without claiming a device is unsupported. |
| IMP-06 | 2 | Material Expressive custom key sounds | Present sound packs as one connected list with a distinct selected state and a clear import action. |
| IMP-07 | 3 | Material Expressive keyboard theme library | Use connected theme rows, section context instead of redundant labels, selected-container feedback, and distinct Create/Copy/Edit actions. |
| IMP-08 | 4 | Remove D-pad jargon | Rename the choices to Native games and Browser games, with plain-language guidance. |
| IMP-09 | 5 | Remove repeated typing-layout descriptions | Use one settings row that opens a single-choice layout dialog and explains the host-layout requirement once. |
| IMP-10 | 6 | Clarify host audio routing | Rename it to Keep audio on this phone and describe the Linux-focused, best-effort behavior. |
| IMP-11 | 7 | Redesign quick-cycle configuration | Move cycle membership to a dedicated screen with connected sections for layouts, sounds, modes, themes, and case colors. Custom themes remain eligible. |
| IMP-12 | 8 | Gate developer-only features | Keep developer controls unavailable when Developer Mode is off and clear active mock state when it is disabled. |
| IMP-13 | 9 | Redesign keyboard theme editor | Use focused Material cards, concise copy, live preview, group/key targeting, and sticky save intent. |
| IMP-14 | 9 | Replace RGB/hex-first editing | Add a shared visual hue/saturation/value picker for keyboard themes and custom case colors; keep hex as an optional precise input. |
| IMP-15 | 10 | Standardize keyboard legends | Base legend size on available key-top width instead of shrinking named modifier keys by character count alone. |
| IMP-16 | 10 | Fn should transform F-key legends | Replace F1-F12 legends in place while Fn is held; remove the overlay that obscured the keyboard. |
| IMP-17 | 10 | Standardize toolbar tap/hold | Tap cycles; long-press opens the relevant configuration for layout, sound, theme, D-pad behavior, mode, and case color where applicable. |
| IMP-18 | 10 | Remove connected/offline text | Keep the accessible colored status indicator and host name, without the bracketed duplicate status. |
| IMP-19 | 11 | Match D-pad toolbar styling | Use the same neutral toolbar surface as adjacent controls; communicate selection through icon and label. |
| IMP-20 | 12 | Style touchpad modifiers like keys | Add a mechanical-key modifier strip using the active keyboard theme. |
| IMP-21 | 12 | Modifier strip position control | Cycle Off, Left, and Right from the touchpad toolbar and persist the choice. The retired duplicate-bank Both value migrates to Left. |
| IMP-22 | 12 | Add slower pointer/scroll speeds | Extend both cycles with 0.25x and 0.5x while preserving existing user values. |
| IMP-23 | 13 | Cohesive adaptive media/presentation remote | Use one responsive surface with playback, slides, and touchpad; adapt to available width and move phone-volume-button control to Settings, disabled by default. |

## Verification targets

- Unit-test pure policy and preference-normalization logic.
- Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` after implementation.
- Manually verify compact-width and landscape layouts, Fn legend replacement, touchpad modifier positions, long-press navigation, imported sound selection, theme selection/editing, and the one-time Gamepad guide.

## Follow-up device checks

- Confirm the first-use Gamepad guide and browser/native D-pad copy on Windows, Linux, Android/TV, macOS, and iOS/iPadOS hosts.
- Confirm the media touchpad and hardware volume interception on API 28, 31, and 36.
- Treat audio-profile disconnection as best effort: OEM Bluetooth stacks can ignore or re-establish audio routing.

## Maintainer UX feedback follow-up

| ID | Feedback | Resolution |
|---|---|---|
| UX-01 | Media/Presentation felt dense and divided into artificial sections | Rebuilt it around one dominant touchpad, a dedicated previous/next rail, one media transport dock, and an in-place secondary presentation-tools dock. |
| UX-02 | Touchpad modifiers obscured the gesture surface and Both duplicated every key | Moved one full-height bank of square modifier keys into a dedicated left or right rail; enabling it reduces the touchpad width instead of drawing over it. |
| UX-03 | Sound-pack hit target ignored the rounded card shape | Use Material's clickable `Surface` so clipping, ripple, and interaction shape share the same rounded outline. |
| UX-04 | Import loader looked malformed and the action lacked hierarchy | Moved import to an extended FAB with a bounded 20 dp progress indicator and Snackbar result feedback. |
| UX-05 | Imported key sounds were isolated from built-in choices | Unified built-in and imported profiles into one selectable list, persisted the built-in selection, and included imported profiles in toolbar cycling. |
| UX-06 | Typing layout choices repeated country names | Shortened visible choices to QWERTY, AZERTY, QWERTZ, Dvorak, and Colemak while retaining host-layout metadata internally. |
| UX-07 | Host filtering and device address used the same icon | Assigned distinct filter-off and fingerprint icons. |
| UX-08 | The non-metallic Black case appeared as a gray-black gradient | Render non-metallic case colors as their exact solid color; reserve gradients for metallic finishes. |
