# Phase 8 — Design

## 1. Current architecture (Phases 1–7)

```
automation/model      Pure Kotlin domain: Automation(id, name, enabled, trigger,
                      conditions: List<Condition>, actions: List<Action>),
                      Trigger (12 kinds), Condition (17 kinds incl. And/Or/Not),
                      Action (9 kinds), UiStep/UiSelector, TriggerPayload
                      (FileEvent, NotificationEvent, SystemEvent.*), Execution.
automation/engine     AutomationEngine (validate → conditions → actions, pure),
                      ActionHandler SPI + ActionContext, ConditionEvaluator
                      (recursive, DeviceStateProvider for system conditions),
                      AutomationRunner (engine + persistence), Scheduler,
                      EventDispatcher, TemplateResolver ({{payload.*}} only),
                      UiAutomationExecutor + UiWorkflowValidator (UI-only).
services/*            Android adapters: accessibility, notification listener,
                      system monitors, files, background work.
data/local            Room v2: automations / executions / notification_records.
                      Workflow stored as type-tagged JSON TEXT (WorkflowJson) —
                      new types never change the SQL schema.
ui/*                  Compose: Home, Automations, Editor, History, Settings,
                      Inspector, Test Lab. Dark-first hand-rolled theme.
```

Key invariants to preserve:
- Domain and engine stay pure Kotlin (unit-testable with fakes).
- Polymorphic workflow parts serialize through WorkflowJson type tags.
- No INTERNET permission, no telemetry, local-first.
- Safety rails: sensitive-field refusal, confirmation before consequential
  UI steps, one UI-automation session, bounded timeouts.

## 2. Phase 8 model extensions (backward compatible)

Compositional — no new node framework. Existing rows decode unchanged.

### Variables & data flow
- `ActionContext.variables: MutableMap<String, String>` — run-scoped, local.
- New `Action.SetVariableAction(name, value)`; `value` may use templates.
- `VariableResolver` (evolves TemplateResolver): resolves `{{payload.*}}`
  allow-list + `{{automation.name}}` + workflow-local `{{name}}` +
  `{{result.*}}` action outputs. Deterministic string substitution only —
  no expressions, no scripting.
- Action outputs: handlers write `result.*` keys into `context.variables`
  (e.g. `result.count` from Instagram analysis, `result.fileName` from file
  actions). Documented per handler.
- Templated fields: ShowNotification title/message, Log message, SetText
  text (already), RenameFile newName, SetVariable value.

### Branching
- New `Action.BranchAction(condition: Condition, thenActions, elseActions)`.
  Engine recurses through the same handler loop; ConditionEvaluator reused.
  Depth capped by the validator (3). Loops: deferred (not trivial + safe).

### Step disable & groups
- `Action.DisabledAction(wrapped: Action)` — engine logs "Skipped (disabled)"
  and moves on; editor toggle wraps/unwraps. Confirmation-bearing UI
  automations cannot be disabled around their safety validator (validator
  re-checks the effective action list).
- `Action.GroupMarker(label)` — no-op organizational header in the editor
  and run log. No separate persistence model.

### Serialization
- WorkflowJson gains `set_variable`, `branch` (recursive), `disabled`
  (wraps inner), `group_marker`. Stored-row compatibility: old JSON has none
  of these; decoding is unchanged for old types.

## 3. Import / export / share / backup

- `WorkflowFileCodec` (data/transfer): versioned envelope
  `{format:"autoflow", schemaVersion:1, kind:"workflows"|"backup",
  exportedAt, automations:[{name, description, trigger, conditions,
  actions}]}` reusing WorkflowJson encoders. Never contains: ids, execution
  history, notification records, Instagram data, device identifiers.
- Import: parse → schemaVersion gate → WorkflowValidator per automation →
  fresh ids → inserted **disabled** → required-capability sheet before
  enabling. Malformed/unsupported → structured error, nothing written.
- Share/export via SAF (`CreateDocument`) and `ACTION_SEND` with a
  non-exported FileProvider; import via `OpenDocument`. Extension `.autoflow`
  (JSON inside).
- Backup = same codec, `kind:"backup"`, plus safe settings; restore uses the
  import path (validation + new ids + disabled).
- Capability detection: scan trigger/actions → NOTIFICATION_ACCESS,
  ACCESSIBILITY, FILE_ACCESS, POST_NOTIFICATIONS.

## 4. Central validation, test, simulation

- `WorkflowValidator` (engine): returns `List<ValidationIssue(severity
  ERROR|WARNING|INFO, message)>`. Checks: trigger config, condition tree
  (depth, blank fields), action config, timeout ranges, variable references
  (defined-before-use for locals, allow-list for payload vars), branch depth,
  UiWorkflowValidator delegation, permission requirements (INFO), limits.
  Used by: editor save, import, enable, Test.
- `AutomationTester`: validator + live capability checks → per-item report.
  No actions executed.
- `SimulationEngine` (pure): synthetic payload (notification / file /
  battery / system forms) → trigger.matches + ConditionEvaluator → matched /
  not-matched trace. Never executes actions, never mutates device state.

## 5. Execution diagnostics

- Structured logs: `logs_json` becomes `{v:2, entries:[{t, line}],
  vars:{...}}` with a backward-compatible decoder (plain JSON array = v1).
  No SQL migration needed. Variables snapshot redacts nothing sensitive by
  construction (SetText values never logged; password fields never read).
- Execution detail screen (`history/{id}`): status, timing, duration,
  trigger, condition outcome, per-action results, variables, timeline list
  (timestamps from log entries).
- Failure diagnostics: map known failure strings to actionable explanations
  (selector not found → reasons + "test this selector" hint).

## 6. Health, retry, limits

- `AutomationHealthCalculator` (pure): (automation, recent executions,
  capability snapshot) → HEALTHY | NEEDS_PERMISSION | CONFIG_ISSUE |
  FAILING | DISABLED. No invented scores — real data only.
- Auto-disable: `Automation.disableAfterFailures: Int?` (DB v3 adds column).
  Runner counts consecutive failures; threshold reached → disable + local
  notification.
- Retry: engine-level, allow-list of idempotent actions only
  (ShowNotification, Log, SaveNotification, InstagramAnalysis, CopyFile).
  Never: UiAutomation, MoveFile, RenameFile. Fixed policy 2 retries × 2 s.
- `EngineLimits`: max run time 10 min, max actions 50, branch depth 3, max
  UI steps 30/automation, history pruned to newest 1000, notification
  records already capped 500, one queued run per automation.

## 7. UX work

- Home: health dashboard (counts from real data) + recent activity.
- Automations: search, filter (all/active/disabled/needs attention), card
  overflow (run, duplicate, export, share, delete), health badge with
  permission deep-link.
- History: date grouping, status filter, search, tap → detail screen.
- Settings: reorganized sections (Automation / Permissions / Privacy /
  Appearance / Storage / Developer tools / About).
- Onboarding: first-launch pager (DataStore flag), permissions explained as
  optional. Privacy Center screen. Data management screen (export/import
  all, clear history, clear notifications, reset — confirmed destructive).
- Templates: bundled asset `.autoflow` files + user templates in
  filesDir/templates; Templates screen instantiates into the editor.
- Duplicate: copy with new id, name "(Copy)", lastRun reset.
- Scheduler diagnostics: time-triggered automations with honest "next
  expected window" + WorkManager WorkInfo state. Developer tools adds Event
  Log (opt-in bounded in-memory ring of event names — no content),
  Database/Scheduler diagnostics.

## 8. Deferred (explicit)

- Loops in workflows (safety/complexity — revisit when needed).
- Disabling individual UiSteps (actions only this phase).
- Typed variables beyond strings (numeric coercion where needed only).
- Cloud anything, template marketplace, remote features — out of scope
  permanently per product constraints.

## 9. DB changes

- v3 migration: `automations.disable_after_failures INTEGER` (nullable).
  Everything else rides in existing JSON TEXT columns.
