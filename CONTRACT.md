# DriftNotes — Shared Contract

## Data classes and interfaces (exact names; do not change them)

### data/Note.kt
```kotlin
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Float,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### data/NoteRepository.kt
`class NoteRepository`, singleton via `NoteRepository.get(context: Context)`.  
Functions:
- `suspend fun add(note: Note): Long` — returns new id
- `suspend fun update(note: Note)`
- `suspend fun delete(note: Note)`
- `suspend fun getById(id: Long): Note?`
- `suspend fun getActive(): List<Note>`
- `fun observeAll(): Flow<List<Note>>`

### geofence/GeofenceManager.kt
`class GeofenceManager(context: Context)`.  
Functions:
- `fun register(note: Note)` — requestId = note.id.toString(); re-registering the same id replaces it
- `fun remove(noteId: Long)`
- `fun registerAll(notes: List<Note>)`

### geofence/GeofenceReceiver.kt
BroadcastReceiver — geofence ENTER → notification

### geofence/BootReceiver.kt
BroadcastReceiver — BOOT_COMPLETED → re-register active notes

### geofence/NotificationHelper.kt
`object` with `createChannel(context: Context)` and `showNoteNotification(context: Context, note: Note?)`

### map/MapFragment.kt
Fragment with map, drop pin, add-note dialog

### notes/NotesListFragment.kt
Fragment with notes list, edit, mark done, delete

### permissions/PermissionsHelper.kt
`class PermissionsHelper(activity: AppCompatActivity)` — requests permissions step by step

### MainActivity.kt
Bottom navigation with 2 tabs, Map and Notes.  
Intent extra `"open_tab" = "notes"` opens the Notes tab.

---

## How the parts connect

| Action | Code |
|---|---|
| Save new note | `val id = repo.add(note)`, then `GeofenceManager(ctx).register(note.copy(id = id))` |
| Edit note | `repo.update(note)`, then if `note.isActive`, `register(note)` again (replaces old geofence) |
| Mark done | `repo.update(note.copy(isActive = false))`, then `remove(note.id)` |
| Delete | `repo.delete(note)`, then `remove(note.id)` |
| Phone restart | `registerAll(repo.getActive())` |
| App start (permissions granted) | `registerAll(repo.getActive())` |
| Tap notification | Opens `MainActivity` with extra `open_tab = "notes"` |

---

## Team rules

- **Only create or edit files in your own package and your own resource files.**  
  Resource files are prefixed by owner:
  - Layouts: `fragment_map.xml`, `item_note.xml`, etc.
  - Strings: `res/values/strings_map.xml`, `strings_notes.xml`, `strings_geofence.xml`, `strings_main.xml`

- **Never edit Gradle files, AndroidManifest.xml, CONTRACT.md, or the GitHub workflow.**  
  Ask Member A for changes there.

- **For quick testing on your own branch you may temporarily edit MainActivity**, but undo that before opening a pull request (Member D owns MainActivity).

- **Branches:**  
  - A = `feature/data`  
  - B = `feature/map`  
  - C = `feature/geofence`  
  - D = `feature/shell`

- **No debug keystore is committed.** Uninstall the previous APK before installing a new one to avoid signature conflicts.
