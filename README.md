# DriftNotes

Location-triggered notes for Android. Drop a pin, write a note, get notified when you arrive.

## Installing a build from GitHub Actions

1. Go to the **Actions** tab of this repository on GitHub.
2. Click the most recent green workflow run named **Build Debug APK**.
3. Scroll to the bottom of the run page and find **Artifacts**.
4. Download the zip named `driftnotes-<branch>-<run-number>`.
5. Unzip it — you get a file ending in `.apk`.
6. Transfer the APK to your Android phone (AirDrop, Google Drive, USB cable, email — anything works).
7. On the phone, open the file. Android will ask you to allow installs from this source; tap **Allow**, then **Install**.

> **Important:** There is no fixed debug signing key. If you already have a previous build installed, **uninstall it first**, then install the new APK. Installing over a build signed with a different key will fail.

## Team

| Member | Branch | Owns |
|---|---|---|
| A | `feature/data` | Project skeleton, Room database, Notes list screen |
| B | `feature/map` | osmdroid map, pin drop, add-note dialog |
| C | `feature/geofence` | Geofencing, notifications, boot re-registration |
| D | `feature/shell` | MainActivity shell, permissions flow |

See [CONTRACT.md](CONTRACT.md) for the shared API contract and team rules.
