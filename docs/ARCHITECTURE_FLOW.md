# Architecture Flow

This document describes the runtime flow of `intune-bulk-actions` from process start through Graph calls and final output.

## End-to-End Runtime Timeline (Default `.exe` launch -> `sync-group`)

1. Program entrypoint
   - File: `src/main/java/com/mkylm/intunebulk/Main.java`
   - Lines: `8-13`
   - Behavior:
     - Starts Picocli with `RootCommand`
     - If no arguments are passed, defaults to `shell`

2. Top-level command routing
   - File: `src/main/java/com/mkylm/intunebulk/cli/RootCommand.java`
   - Lines: `6-10`
   - Behavior:
     - Registers `bulk` and `shell` commands

3. Shell service bootstrap
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Lines: `31-37`
   - Behavior:
     - Builds `TokenProvider`, `GraphClient`, `GroupDeviceResolver`, `DeviceActionService`

4. Shell REPL loop and dispatch
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Lines: `43-79`
   - Behavior:
     - Reads user input and dispatches commands (`groups`, `group-devices`, `sync-group`, etc.)

5. `sync-group` command pipeline
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Lines: `229-257`
   - Behavior:
     - Parses CLI flags
     - Resolves group ID
     - Resolves devices
     - Executes action
     - Prints summary

6. Group name-to-ID resolution
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Lines: `330-384`
   - Behavior:
     - Accepts direct GUIDs
     - Otherwise performs exact/prefix group lookup via Graph
     - Handles ambiguity and not-found cases

7. Resolve-phase progress bar
   - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
   - Lines: `324-328`
   - File: `src/main/java/com/mkylm/intunebulk/cli/ResolveProgressBar.java`
   - Lines: `16-44`
   - Behavior:
     - Displays live member-resolution progress (`mapped`, `skipped`)

8. Group membership -> managedDevice mapping
   - File: `src/main/java/com/mkylm/intunebulk/core/GroupDeviceResolver.java`
   - Lines: `36-124`
   - Behavior:
     - Reads transitive group device members
     - Maps each AAD device to Intune managedDevice
     - Emits progress snapshots

9. Graph reads used for resolution
   - File: `src/main/java/com/mkylm/intunebulk/core/GroupDeviceResolver.java`
   - Lines: `42-47`, `72-79`
   - File: `src/main/java/com/mkylm/intunebulk/graph/GraphClient.java`
   - Lines: `76-83`, `98-129`, `135-156`
   - Behavior:
     - Uses paged GET (`@odata.nextLink`) and filtered lookup requests

10. Action-phase progress bar
    - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
    - Lines: `318-322`
    - File: `src/main/java/com/mkylm/intunebulk/cli/ConsoleProgressBar.java`
    - Lines: `16-41`
    - Behavior:
      - Displays action completion progress with success/fail/skip counters

11. Concurrent bulk action execution
    - File: `src/main/java/com/mkylm/intunebulk/core/DeviceActionService.java`
    - Lines: `44-85`
    - Behavior:
      - Executes actions concurrently with completion-order progress updates

12. Per-device action execution and retry
    - File: `src/main/java/com/mkylm/intunebulk/core/DeviceActionService.java`
    - Lines: `95-125`
    - File: `src/main/java/com/mkylm/intunebulk/core/RetryPolicy.java`
    - Lines: `23-40`
    - Behavior:
      - Runs each device operation with transient retry and backoff

13. Action-to-endpoint mapping
    - File: `src/main/java/com/mkylm/intunebulk/core/DeviceActionService.java`
    - Lines: `127-151`
    - Behavior:
      - Maps `ActionType` values to specific Graph action endpoints (`syncDevice`, `rebootNow`, `wipe`, `autopilotReset`, remove primary user)

14. Graph writes (POST/DELETE)
    - File: `src/main/java/com/mkylm/intunebulk/graph/GraphClient.java`
    - Lines: `191-230`, `232-261`
    - Behavior:
      - Sends authenticated write requests
      - Classifies transient vs permanent failures

15. Final action summary rendering
    - File: `src/main/java/com/mkylm/intunebulk/cli/ShellCommand.java`
    - Lines: `533-555`
    - Behavior:
      - Prints aggregate totals and per-device outcome table

## Authentication and Config Resolution Timeline

1. Auth mode selection
   - File: `src/main/java/com/mkylm/intunebulk/graph/TokenProviderFactory.java`
   - Lines: `25-37`
   - Behavior:
     - Chooses provider from `INTUNE_AUTH_MODE` (`interactive`, `device_code`, `raw_token`)

2. Tenant/client/scopes lookup
   - File: `src/main/java/com/mkylm/intunebulk/graph/TokenProviderFactory.java`
   - Lines: `41-48`, `65-67`, `86-95`
   - File: `src/main/java/com/mkylm/intunebulk/graph/Env.java`
   - Lines: `44-54`
   - Behavior:
     - Reads values from env first, then `ibt.cfg`

3. `ibt.cfg` discovery
   - File: `src/main/java/com/mkylm/intunebulk/graph/Env.java`
   - Lines: `56-120`
   - Behavior:
     - Checks:
       - `INTUNE_CONFIG_FILE` explicit path
       - current working directory
       - classpath directory
       - classpath parent directory (app-image-friendly)

4. Token acquisition behavior
   - Interactive provider:
     - File: `src/main/java/com/mkylm/intunebulk/graph/InteractiveBrowserTokenProvider.java`
     - Lines: `35-58`
   - Device code provider:
     - File: `src/main/java/com/mkylm/intunebulk/graph/DeviceCodeTokenProvider.java`
     - Lines: `40-60`
   - Behavior:
     - Uses in-memory cache, attempts silent acquisition, then falls back to interactive flow

## Non-REPL Bulk Path (`intune-bulk bulk ...`)

For one-shot commands, the same core flow runs through:

- File: `src/main/java/com/mkylm/intunebulk/cli/BaseBulkActionCommand.java`
- Lines: `82-100`
- Behavior:
  - Auth setup -> resolve with `RESOLVE` progress -> execute with action progress -> print summary

## Key Layer Boundaries

- `cli`: command parsing, user interaction, progress bars, output rendering
- `core`: domain logic (resolve + execute + retry + action/result modeling)
- `graph`: token acquisition, config/env parsing, HTTP transport, Graph pagination and error handling

