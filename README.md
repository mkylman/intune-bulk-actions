# intune-bulk-actions

Java tool for performing bulk actions on Intune-enrolled devices via Microsoft Graph, with both CLI and desktop GUI workflows.

## What’s included
- CLI (Picocli): `bulk sync|reboot|wipe|autopilot-reset|remove-primary-user`
- Targeting by Azure AD Group (real Graph calls)
- Reusable core layer (`DeviceActionService`, `GroupDeviceResolver`)
- Graph abstraction (`GraphClient`) + token provider abstraction (`TokenProvider`)
- Throttling-aware retry framework
- Interactive terminal shell: `shell` (browse groups/users/devices, resolve group members, run group actions)
- Desktop GUI mode: `gui` (config-driven reports, group device resolution, and group actions)
- Secure MSAL token cache on Windows (DPAPI-backed) for interactive / device-code auth
- Config-driven GUI reports via `reports.json` (add custom reports without rebuilding)

## Recent updates
- Default no-args startup now launches GUI mode (instead of shell).
- Interactive and device-code auth can persist the MSAL token cache via a Windows DPAPI-backed store, so reopens often skip a fresh browser login.
- GUI reports are config-driven from `reports.json` (created next to `ibt.cfg` if missing, with built-in defaults). This lets users add or change custom reports without rebuilding the app.
- Reports support Graph endpoints, column/field mapping, optional post-query filters (`eq`, `contains`, `doesnotcontain`, plus `and`/`or` compounds), sorting, max items, and per-report caching.
- GUI layout updates:
  - query row uses a `Reports` dropdown + `Run Report` (replacing hardcoded Users/Devices buttons)
  - split group selectors into `User Groups` + `User Group Members` and `Device Groups` + `Device Group Members`
  - dropdowns are filtered by inferred member type during initial load/classification
  - dedicated action rows for `Sync Group`, then `Reboot Group` + `Remove Primary User Group` (device-group scoped)
  - `Reboot Group` is highlighted yellow; `Remove Primary User Group` is highlighted red
- GUI now includes a result search box (case-insensitive filter) and `Export CSV`.
- GUI status now includes elapsed time in milliseconds after operations (e.g., `| 1843 ms`).
- Initial group loading/classification now shows an always-on-top splash dialog with progress/activity text and an `OK` close button.
- Group device resolution is now faster due to parallel managed-device lookup in `GroupDeviceResolver`.
- GUI reuses resolved group devices with a short in-session cache for repeated actions.
- GUI caches report results in-memory by report id (when `cacheable` is true) for repeated runs within a session.
- GUI internals were refactored into smaller files: `GuiRuntime`, `GuiActionPanel`, `GuiResultsPanel`, `GroupOption`, and report models (`ReportDefinition`, `ReportFilter`, `ReportCondition`, `ReportRegistry`).
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

On Windows, interactive and device-code flows can persist the MSAL token cache using DPAPI so later launches can acquire tokens silently when possible.

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

## Reports config (`reports.json`)
GUI report options are loaded from `reports.json` in the same directory as the resolved `ibt.cfg`.

- If the file is missing, the app creates one with built-in defaults (All Users, All Devices, Expired Passwords).
- If the file is present but invalid, the GUI falls back to those built-in defaults.
- Editing `reports.json` lets users define custom reports (new Graph endpoints, columns, fields, filters) without rebuilding or repackaging the app.
- Restart the GUI after editing `reports.json` to pick up changes.

Each report entry includes:
- `id`, `label` — stable id and dropdown text
- `endpoint` — Microsoft Graph v1 path (including `$select` as needed)
- `columns` / `fields` — table headers and matching Graph field paths (same length/order)
- `filter` — optional; either a simple `{ fieldPath, op, value }` or a compound `{ logic, conditions }` where `logic` is `and`/`or` and each condition uses `eq`, `contains`, or `doesnotcontain`
- `sortByField`, `sortDirection` — optional sort (`asc`/`desc`)
- `maxItems` — optional page cap
- `cacheable` — when `true`, reuse in-session results for that report id

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
- Reports dropdown + `Run Report` (options come from `reports.json`)
- User group selector: `User Groups` + `User Group Members`
- Device group selector: `Device Groups` + `Device Group Members`
- Group actions (`Sync Group`, `Reboot Group`, `Remove Primary User Group`) run against the selected device group.
- Query results support live filtering and CSV export.
- Query/action completion messages include elapsed milliseconds (`| ### ms`).
- Actions execute against all resolvable managed devices in the selected group (with confirmation prompts for mutating operations).

## Architecture
- End-to-end runtime flow (with file and line references): `docs/ARCHITECTURE_FLOW.md`

