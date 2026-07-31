# intune-bulk-actions

Java tool for performing bulk actions on Intune-enrolled devices via Microsoft Graph, with both CLI and desktop GUI workflows.

## What’s included
- CLI (Picocli): `bulk sync|reboot|wipe|autopilot-reset|remove-primary-user`
- Targeting by Azure AD Group (real Graph calls)
- Reusable core layer (`DeviceActionService`, `GroupDeviceResolver`)
- Graph abstraction (`GraphClient`) + token provider abstraction (`TokenProvider`)
- Throttling-aware retry framework
- Interactive terminal shell: `shell` (browse groups/users/devices, resolve group members, run group actions)
- Desktop GUI mode: `gui` (queries, group device resolution, and group actions)

## Recent updates
- Default no-args startup now launches GUI mode (instead of shell).
- GUI layout updates:
  - query row now uses `Users` + `Devices` (Groups button removed)
  - group row now uses `Group Members`
  - dedicated action rows for `Sync Group`, then `Reboot Group` + `Remove Primary User Group`
  - `Reboot Group` is highlighted yellow; `Remove Primary User Group` is highlighted red
- GUI now includes a result search box (case-insensitive filter) and `Export CSV`.
- GUI now shows an elapsed timer next to status/progress text while queries/actions are running.
- Group device resolution is now faster due to parallel managed-device lookup in `GroupDeviceResolver`.
- GUI reuses resolved group devices with a short in-session cache for repeated actions.
- GUI now caches Users and Devices query results in-memory for repeated button clicks within a session.
- GUI internals were refactored into smaller files: `GuiRuntime`, `GuiActionPanel`, `GuiResultsPanel`, and `GroupOption`.
- GUI Quick Start commands now reference packaged `.exe` usage.

## Build
```bash
.\mvnw.cmd -q package
```

## Build Windows app image (`.exe`)
```powershell
powershell -ExecutionPolicy Bypass -File .\build-appimage.ps1
```

Expected launcher:
```text
.\dist\intune-bulk-actions\intune-bulk-actions.exe
```

## Run
```bash
java -jar target/intune-bulk-actions-0.1.0.jar --help
java -jar target/intune-bulk-actions-0.1.0.jar shell
java -jar target/intune-bulk-actions-0.1.0.jar gui
java -jar target/intune-bulk-actions-0.1.0.jar --gui
java -jar target/intune-bulk-actions-0.1.0.jar bulk sync --groupId <GUID> --dryRun
```

Packaged app-image examples:
```powershell
.\dist\intune-bulk-actions\intune-bulk-actions.exe
.\dist\intune-bulk-actions\intune-bulk-actions.exe shell
.\dist\intune-bulk-actions\intune-bulk-actions.exe gui
.\dist\intune-bulk-actions\intune-bulk-actions.exe bulk sync --groupId <GUID> --dryRun
```

## Auth
Two easy options:

- Interactive browser login (default mode; requires an app registration + redirect URI):

```powershell
$env:INTUNE_AUTH_MODE="interactive"
$env:INTUNE_CLIENT_ID="<client-id>"
$env:INTUNE_REDIRECT_URI="http://localhost"  # must be registered on the app
java -jar target/intune-bulk-actions-0.1.0.jar shell
```

- Device code (works without app redirect URI setup):

```powershell
$env:INTUNE_AUTH_MODE="device_code"
java -jar target/intune-bulk-actions-0.1.0.jar shell
```

## Config file (`ibt.cfg`)
You can put auth settings in a config file instead of exporting env vars each time.

- Optional explicit path: `INTUNE_CONFIG_FILE`
- Otherwise auto-discovery checks:
  - current working directory (`.\ibt.cfg`)
  - jar/classpath folder (`...\ibt.cfg`)
  - parent of jar/classpath folder (useful for packaged app-image layout)
- Precedence: environment variables override config file values
- If no `ibt.cfg` is found, the app creates one with a default template.
- If `INTUNE_TENANT_ID` or `INTUNE_CLIENT_ID` is missing/blank in `ibt.cfg`, the app prompts for values and writes them back to the file.

Example `ibt.cfg`:

```ini
INTUNE_AUTH_MODE=interactive
INTUNE_TENANT_ID=your-tenant-guid
INTUNE_CLIENT_ID=your-client-guid
INTUNE_REDIRECT_URI=http://localhost
```

## Shell commands
Inside `shell`:
- `groups [--top N] [--prefix TEXT]`
- `users [--top N] [--prefix TEXT]`
- `devices [--top N] [--prefix TEXT]`
- `group-devices <groupId|groupName>`
- `sync-group <groupId|groupName> [--dryRun]`
- `reboot-group <groupId|groupName> [--dryRun]`
- `remove-primary-user-group <groupId|groupName> [--dryRun]`

## GUI actions
Inside `gui`:
- Query buttons: `Users`, `Devices`
- Group selector actions: `Group Members`, `Sync Group`, `Reboot Group`, `Remove Primary User Group`
- Query results support live filtering and CSV export.
- Progress/status area includes an elapsed timer while tasks are running.
- Actions execute against all resolvable managed devices in the selected group (with confirmation prompts for mutating operations).

## Architecture
- End-to-end runtime flow (with file and line references): `docs/ARCHITECTURE_FLOW.md`

