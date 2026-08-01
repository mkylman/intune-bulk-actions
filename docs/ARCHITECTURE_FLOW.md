# Architecture Flow

This document describes the runtime flow of `intune-bulk-actions` from process start through Graph calls and final output.

## End-to-End Runtime Timeline (Default `.exe` launch -> GUI group action)

1. Program entrypoint
   - File: `src/main/java/com/mkylm/intunebulk/Main.java`
   - Behavior:
     - Starts Picocli with `RootCommand`.
     - If no arguments are passed, defaults to `gui` (quality-of-life behavior for packaged app launch).
     - `--gui` is also normalized to the `gui` command.

2. Top-level command routing
   - File: `src/main/java/com/mkylm/intunebulk/cli/RootCommand.java`
   - Behavior:
     - Registers `bulk`, `shell`, and `gui` subcommands.

3. GUI command handoff
   - File: `src/main/java/com/mkylm/intunebulk/cli/GuiCommand.java`
   - Behavior:
     - Delegates to `IntuneBulkGuiApp.launch()`.

4. GUI bootstrap and window assembly
   - Files:
     - `src/main/java/com/mkylm/intunebulk/gui/IntuneBulkGuiApp.java`
     - `src/main/java/com/mkylm/intunebulk/gui/GuiActionPanel.java`
     - `src/main/java/com/mkylm/intunebulk/gui/GuiResultsPanel.java`
     - `src/main/java/com/mkylm/intunebulk/gui/GuiRuntime.java`
   - Behavior:
     - Builds the main frame but keeps it hidden until splash flow is closed.
     - Composes GUI via extracted panel/runtime classes.
     - Creates runtime services lazily (`GraphClient`, `GroupDeviceResolver`, `DeviceActionService`).
     - Starts splash-first initialization and asynchronously loads/classifies groups.

5. Splash-first configuration and authentication
   - File: `src/main/java/com/mkylm/intunebulk/gui/IntuneBulkGuiApp.java`
   - Behavior:
     - Shows an always-on-top splash dialog.
     - Prompts for `INTUNE_TENANT_ID` and `INTUNE_CLIENT_ID` in one dialog section (prefilled from discovered `ibt.cfg` when present).
     - Requires explicit `Confirm` to persist config before auth.
     - Enables `Authenticate and Continue` only after config is saved.
     - Writes config values, pins runtime config path (`intune.config.file`), suppresses fallback GUI config prompts (`intune.gui.suppressConfigPrompt=true`), and then authenticates.
     - Displays progress/activity lines and requires explicit `OK` to close splash.

6. GUI query/actions available
   - File: `src/main/java/com/mkylm/intunebulk/gui/IntuneBulkGuiApp.java`
   - Behavior:
     - Query buttons: `Users`, `Devices`.
     - User-group controls: `User Groups` + `User Group Members`.
     - Device-group controls: `Device Groups` + `Device Group Members`.
     - Device-group actions: `Sync Group`, `Reboot Group`, `Remove Primary User Group`.
     - Mutating operations show confirmation dialogs before execution.
     - Main status line appends elapsed milliseconds after completion (e.g., `| 1843 ms`).

7. Group device resolution (shared core path)
   - File: `src/main/java/com/mkylm/intunebulk/core/GroupDeviceResolver.java`
   - Behavior:
     - Reads transitive Entra device membership:
       - `/groups/{id}/transitiveMembers/microsoft.graph.device`
     - Resolves each member to Intune managed device using:
       - `/deviceManagement/managedDevices?$filter=azureADDeviceId eq '{id}'`
     - Emits progress snapshots (used by shell progress bar; GUI can run async without console progress).

8. Resolution performance improvements
   - File: `src/main/java/com/mkylm/intunebulk/core/GroupDeviceResolver.java`
   - Behavior:
     - Managed-device lookups now run with bounded parallelism (thread pool), reducing total latency for larger groups.

9. GUI caches and filtering
   - File: `src/main/java/com/mkylm/intunebulk/gui/GuiRuntime.java`
   - Behavior:
     - GUI runtime caches resolved group devices by group ID for a short TTL (5 minutes).
     - Repeated `Group Devices`, `Sync Group`, `Reboot Group`, and `Remove Primary User Group` calls reuse recent results.
     - Caches users and devices query result rows in-memory for repeated clicks.
     - Applies search/filter text to results table via row sorter.

10. Group classification strategy for split dropdowns
    - File: `src/main/java/com/mkylm/intunebulk/gui/IntuneBulkGuiApp.java`
    - Behavior:
      - Loads all groups once.
      - Classifies each group into user/device buckets using first transitive member (`/groups/{id}/transitiveMembers?$top=1`) for speed.
      - Uses bounded parallel worker pool for classification.
      - Populates `User Groups` and `Device Groups` dropdowns separately.

11. Action execution pipeline
   - Files:
     - `src/main/java/com/mkylm/intunebulk/gui/IntuneBulkGuiApp.java`
     - `src/main/java/com/mkylm/intunebulk/core/DeviceActionService.java`
   - Behavior:
     - Builds `ActionRequest` (`SYNC`, `REBOOT`, `REMOVE_PRIMARY_USER`, etc.).
     - Executes device actions concurrently with retry/backoff behavior.
     - Updates result table with per-device outcomes and aggregate counts.

12. Graph transport layer
    - File: `src/main/java/com/mkylm/intunebulk/graph/GraphClient.java`
    - Behavior:
      - Handles authenticated GET/POST/DELETE requests.
      - Supports OData pagination (`@odata.nextLink`).
      - Classifies transient (429/5xx) vs permanent failures.

## Shell Runtime Timeline (`shell` -> `sync-group` / `reboot-group` / `remove-primary-user-group`)

1. Shell service bootstrap
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Behavior:
     - Builds `TokenProvider`, `GraphClient`, `GroupDeviceResolver`, and `DeviceActionService`.

2. REPL loop and dispatch
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Behavior:
     - Reads user input and dispatches commands:
       - `groups`, `users`, `devices`
       - `group-devices`
       - `sync-group`
       - `reboot-group`
       - `remove-primary-user-group`

3. Group name-to-ID resolution
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Behavior:
     - Accepts direct GUIDs.
     - Resolves exact/prefix group names through Graph.
     - Returns helpful ambiguity/not-found messages.

4. Resolve + execute progress
   - Files:
     - `src/main/java/com/mkylm/intunebulk/cli/ResolveProgressBar.java`
     - `src/main/java/com/mkylm/intunebulk/cli/ConsoleProgressBar.java`
   - Behavior:
     - Shows resolve-phase progress (`mapped`, `skipped`) and action-phase progress (`succeeded`, `failed`, `skipped`).

5. Final summary rendering
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Behavior:
     - Prints aggregate totals and per-device results table.

## Authentication and Config Resolution Timeline

1. Auth mode selection
   - File: `src/main/java/com/mkylm/intunebulk/graph/TokenProviderFactory.java`
   - Behavior:
     - Chooses provider from `INTUNE_AUTH_MODE` (`interactive`, `device_code`, `raw_token`).

2. Tenant/client/scopes lookup
   - Files:
     - `src/main/java/com/mkylm/intunebulk/graph/TokenProviderFactory.java`
     - `src/main/java/com/mkylm/intunebulk/graph/Env.java`
   - Behavior:
     - Reads settings from environment first, then `ibt.cfg`.

3. `ibt.cfg` discovery
   - File: `src/main/java/com/mkylm/intunebulk/graph/Env.java`
   - Behavior:
     - Checks:
       - `intune.config.file` JVM property (GUI flow pinning)
       - `INTUNE_CONFIG_FILE` explicit path
       - current working directory
       - classpath directory
       - classpath parent directory (app-image-friendly)
     - In GUI flow, fallback prompt behavior can be suppressed via `intune.gui.suppressConfigPrompt=true`.

4. Token acquisition behavior
   - Files:
     - `src/main/java/com/mkylm/intunebulk/graph/InteractiveBrowserTokenProvider.java`
     - `src/main/java/com/mkylm/intunebulk/graph/DeviceCodeTokenProvider.java`
   - Behavior:
     - Uses in-memory caching and silent acquisition when possible.
     - Falls back to interactive/device-code flow as needed.

## Non-REPL Bulk Path (`intune-bulk bulk ...`)

For one-shot commands, the same core path runs through:

- File: `src/main/java/com/mkylm/intunebulk/cli/BaseBulkActionCommand.java`
- Behavior:
  - auth setup -> resolve (`RESOLVE` progress) -> execute (action progress) -> final summary

## Packaging and Launch Notes

- Packaged app-image launcher: `dist/intune-bulk-actions/intune-bulk-actions.exe`
- Default packaged launch behavior opens GUI mode.
- Quick-start UX in GUI references `.exe` commands (not `mvnw`).
- Windows app-image packaging currently omits `--win-console`, so GUI launch does not create a console window by default.

## Known Tradeoffs

- Group type filtering uses a fast first-member heuristic (`/groups/{id}/transitiveMembers?$top=1`) to classify groups as user- or device-based.
- Mixed-member groups can be misclassified if the first returned member is not representative of the group.
- Classification favors startup speed over exhaustive accuracy; deeper per-group scans would be more accurate but significantly slower and more API-intensive.
- Group classification runs parallel Graph lookups; this improves latency but can increase short burst API pressure in large tenants.

## Key Layer Boundaries

- `cli`: command parsing, user interaction, progress bars, output rendering
- `gui`: desktop UI composition, splash/config/auth workflow orchestration, async query/action orchestration, GUI-only short-lived caching
- `core`: domain logic (resolve + execute + retry + action/result modeling)
- `graph`: token acquisition, config/env parsing, HTTP transport, Graph pagination/error handling

