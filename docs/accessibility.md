# Why AutoFlow uses an AccessibilityService

Phase 7 internal documentation. Audience: contributors and future policy
review. This is not a claim of Google Play eligibility — policy review
happens separately before any public release.

## Purpose

AutoFlow's AccessibilityService (`AutoFlowAccessibilityService`) exists for
exactly two user-facing features:

1. **UI Automation actions** — the user builds a workflow (target app +
   ordered steps such as *Launch app → Wait for element → Tap → Enter
   text*) in the editor, attaches it to a trigger, and AutoFlow performs
   those steps through semantic accessibility actions (`ACTION_CLICK`,
   `ACTION_SET_TEXT`, `ACTION_SCROLL_*`, `GLOBAL_ACTION_BACK`).
2. **The Inspect UI tool** — an explicitly user-started mode that captures
   the attributes (text, content description, view ID, class, capability
   flags) of elements the user taps, so they can build selectors.

There is no other consumer of accessibility data in the codebase. The
service is a thin platform adapter; all workflow logic lives in the
platform-free engine layer (`UiAutomationExecutor` and friends).

## The user is always in control

- The service is enabled only by the user in Android's Accessibility
  settings. The Permission Center links there; AutoFlow never opens that
  screen unprompted, never nags, and cannot enable itself.
- Workflows exist only because the user created them, step by step, in the
  editor. There are no built-in, downloaded, or app-specific workflows.
- Every run is visible (in-app banner + progress notification) and
  cancellable at any moment.
- `RequireUserConfirmation` steps pause the run until the user explicitly
  approves; the editor refuses to save workflows that tap payment/OTP-like
  controls without a confirmation step directly before them.

## Data handling

- Accessibility data never leaves the process: no INTERNET permission, no
  backend, no telemetry, no analytics, no remote control channel of any
  kind (verified each phase; the manifest has no network permission).
- The UI tree is read only while a UI automation session is active or
  Inspect mode is on. The idle event handler does constant-time work on two
  event types (window state, view clicked) and never traverses the tree.
- Nothing from the screen is persisted. Execution history records step
  labels and selector summaries only — never text entered into fields and
  never screen content. The inspector holds at most 25 tapped elements in
  memory and clears them when turned off (auto-off after 10 minutes).
- Password nodes: their text is never read (`AccessibilityUiNode.text`
  returns null for password fields) and `SetText` refuses to act on them.

## Hard limits built into the engine

- No coordinate tapping / gesture injection (`canPerformGestures` is not
  requested). Interaction is semantic node actions only.
- No automation of credentials, OTPs, payment confirmation, or security
  settings — blocked at edit time (validator keyword rules), at run time
  (password-node refusal), and by the confirmation-step requirement.
- Target-package boundary: steps act only while the chosen app is in the
  foreground; drift to another app or a system dialog stops the run.
- Device lock stops the run (documented behavior: **cancel**, not pause —
  see `UiAutomationExecutor.ensureUnlocked`); AutoFlow never attempts to
  unlock or interact with the keyguard.
- One session at a time, an overall timeout on every run, and bounded
  per-step waits.
- The service is not designed to — and must never be extended to —
  circumvent Android or third-party app restrictions.
