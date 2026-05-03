# intune-bulk-actions (CLI scaffold)

CLI-first Java scaffold for performing bulk actions on Intune-enrolled devices via Microsoft Graph.

## What’s included
- CLI skeleton (Picocli): `bulk sync|reboot|wipe|autopilot-reset`
- Targeting by Azure AD Group (real Graph calls)
- Reusable core layer (`DeviceActionService`, `GroupDeviceResolver`)
- Graph abstraction (`GraphClient`) + token provider abstraction (`TokenProvider`)
- Throttling-aware retry framework
- Interactive terminal shell: `shell` (browse groups/users/devices, resolve group members)

## Build
```bash
.\mvnw.cmd -q package
```

## Run
```bash
java -jar target/intune-bulk-actions-0.1.0.jar --help
java -jar target/intune-bulk-actions-0.1.0.jar shell
java -jar target/intune-bulk-actions-0.1.0.jar gui
java -jar target/intune-bulk-actions-0.1.0.jar --gui
java -jar target/intune-bulk-actions-0.1.0.jar bulk sync --groupId <GUID> --dryRun
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
- `group-devices <groupId>`

## Architecture
- End-to-end runtime flow (with file and line references): `docs/ARCHITECTURE_FLOW.md`

