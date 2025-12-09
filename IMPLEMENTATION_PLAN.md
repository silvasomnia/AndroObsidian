# AndroObsidian - Final Implementation Plan

## Overview

A native Android app that displays your Obsidian daily notes on your Galaxy Watch 8 Classic. Read-only, simple, robust.

**Design Principles:**
- Personal use only - no production hardening needed
- Simple over clever - SharedPreferences over Room/DataStore
- Notes are small (~1-3KB) - no chunking/Assets needed
- Vault already plaintext - no encryption layer needed

**Architecture:**
```
┌─────────────────────┐     Data Layer API      ┌─────────────────────┐
│   Phone Companion   │ ─────────────────────▶  │     Watch App       │
│                     │     (Bluetooth/WiFi)    │                     │
│  • Reads vault via  │                         │  • Tile: Last lines │
│    SAF permission   │                         │  • Reader: Full note│
│  • Sends note text  │                         │    with bezel scroll│
│  • WorkManager sync │                         │                     │
└─────────────────────┘                         └─────────────────────┘
        │                                                │
        ▼                                                ▼
  /Documents/Scratchpad/                          Local cache
  Scratchpad/Daily Notes/                      (SharedPreferences)
     2025-12-09.md
```

---

## Confirmed Details

| Item | Value |
|------|-------|
| Vault Path | `/storage/emulated/0/Documents/Scratchpad/Scratchpad/` |
| Daily Notes | `Daily Notes/YYYY-MM-DD.md` |
| Tile Display | Last ~5-8 lines (most recent entries at bottom) |
| Full Reader | Entire note with rotary scroll (Horologist) |
| Sync | Read-only; Obsidian Sync handles vault sync |
| Watch | Galaxy Watch 8 Classic (Wear OS 6) |
| Phone | Android 16 |

---

## Project Structure

```
AndroObsidian/
├── app/                          # Phone companion module
│   ├── src/main/
│   │   ├── java/.../mobile/
│   │   │   ├── MainActivity.kt           # Setup UI (folder picker)
│   │   │   ├── DailyNoteReader.kt        # SAF file reading logic
│   │   │   ├── DataLayerSender.kt        # Send to watch
│   │   │   └── SyncWorker.kt             # WorkManager periodic sync
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── wear/                         # Watch app module
│   ├── src/main/
│   │   ├── java/.../wear/
│   │   │   ├── DataLayerReceiver.kt      # WearableListenerService
│   │   │   ├── NoteRepository.kt         # Local cache management
│   │   │   ├── tile/
│   │   │   │   └── DailyNoteTileService.kt  # ProtoLayout tile
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt       # Compose entry
│   │   │       ├── NoteReaderScreen.kt   # Full note with scroll
│   │   │       └── theme/Theme.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── shared/                       # Shared constants
│   └── src/main/java/.../shared/
│       └── DataLayerPaths.kt     # Path constants, data keys
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Phase 1: Phone Companion App

### 1.1 Initial Setup Screen

Simple single-screen UI:
- "Select Daily Notes Folder" button
- Status indicator (connected/syncing/last sync time)
- "Sync Now" manual trigger button

```kotlin
// On first launch, prompt folder selection
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}
folderPickerLauncher.launch(intent)

// Persist permission forever
contentResolver.takePersistableUriPermission(
    uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION
)
```

### 1.2 Daily Note Reader

```kotlin
class DailyNoteReader(private val context: Context) {
    
    fun readTodaysNote(treeUri: Uri): NoteContent? {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filename = "$today.md"
        
        val dailyNotesUri = findDailyNotesFolder(treeUri) ?: return null
        val noteUri = findFile(dailyNotesUri, filename) ?: return null
        
        return context.contentResolver.openInputStream(noteUri)?.use { stream ->
            val text = stream.bufferedReader().readText()
            NoteContent(
                date = today,
                fullText = text,
                lastLines = extractLastLines(text, lineCount = 8)
            )
        }
    }
    
    private fun extractLastLines(text: String, lineCount: Int): String {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.takeLast(lineCount).joinToString("\n")
    }
}

data class NoteContent(
    val date: String,
    val fullText: String,
    val lastLines: String  // For tile display
)
```

### 1.3 Data Layer Sender

```kotlin
class DataLayerSender(private val context: Context) {
    private val dataClient = Wearable.getDataClient(context)
    
    suspend fun sendNote(note: NoteContent) {
        val request = PutDataMapRequest.create("/daily_note").apply {
            dataMap.putString("date", note.date)
            dataMap.putString("full_text", note.fullText)
            dataMap.putString("last_lines", note.lastLines)
            // CRITICAL: timestamp forces update even if text unchanged
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        
        dataClient.putDataItem(request).await()
    }
}
```

### 1.4 Background Sync (WorkManager)

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
        val treeUri = prefs.getString("vault_uri", null)?.toUri() ?: return Result.failure()
        
        val reader = DailyNoteReader(applicationContext)
        val sender = DataLayerSender(applicationContext)
        
        val note = reader.readTodaysNote(treeUri) ?: return Result.retry()
        sender.sendNote(note)
        
        return Result.success()
    }
}

// Schedule on app startup
fun scheduleDailySync(context: Context) {
    val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
        repeatInterval = 1, 
        repeatIntervalTimeUnit = TimeUnit.HOURS
    ).setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    ).build()
    
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_note_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        syncRequest
    )
}
```

---

## Phase 2: Watch App

### 2.1 Data Layer Receiver

```kotlin
class DataLayerReceiver : WearableListenerService() {
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == "/daily_note") {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    
                    val note = CachedNote(
                        date = dataMap.getString("date", ""),
                        fullText = dataMap.getString("full_text", ""),
                        lastLines = dataMap.getString("last_lines", ""),
                        receivedAt = System.currentTimeMillis()
                    )
                    
                    // Save to local cache
                    NoteRepository(this).saveNote(note)
                    
                    // Trigger tile refresh
                    TileService.getUpdater(this)
                        .requestUpdate(DailyNoteTileService::class.java)
                }
            }
        }
    }
}
```

### 2.2 Tile (ProtoLayout)

```kotlin
class DailyNoteTileService : SuspendingTileService() {
    
    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): Tile {
        val note = NoteRepository(this).getNote()
        
        return Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        createLayout(note)
                    ).build()
                ).build()
            ).build()
    }
    
    private fun createLayout(note: CachedNote?): Layout {
        val context = this
        
        return Layout.Builder().setRoot(
            PrimaryLayout.Builder(deviceParameters)
                .setTitleSlot(
                    Text.Builder(context, note?.date ?: "No Note")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(0xFFAAAAAA.toInt()))
                        .build()
                )
                .setMainSlot(
                    Text.Builder(context, note?.lastLines ?: "Open phone app to sync")
                        .setTypography(Typography.TYPOGRAPHY_BODY2)
                        .setMaxLines(8)
                        .setOverflow(TextOverflowProp.OVERFLOW_ELLIPSIZE)
                        .build()
                )
                .setBottomSlot(
                    CompactChip.Builder(
                        context,
                        "Open Full",
                        Clickable.Builder()
                            .setOnClick(
                                LaunchAction.Builder()
                                    .setAndroidActivity(
                                        AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName("$packageName.ui.MainActivity")
                                            .build()
                                    ).build()
                            ).build(),
                        deviceParameters
                    ).build()
                )
                .build()
        ).build()
    }
}
```

### 2.3 Full Reader (Compose + Horologist)

```kotlin
@Composable
fun NoteReaderScreen(noteRepository: NoteRepository) {
    val note by noteRepository.noteFlow.collectAsState(initial = null)
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    
    // Request focus on composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Re-request focus on resume (after dialogs, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                focusRequester.requestFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scrollState = scrollState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .rotaryWithScroll(scrollState, focusRequester)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Date header
            Text(
                text = note?.date ?: "",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Full note content
            Text(
                text = note?.fullText ?: "No note available.\nOpen phone app to sync.",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface
            )
            
            // Bottom padding for scroll comfort
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
```

---

## Phase 3: Integration & Manifest

### Phone Manifest Additions
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />

<application>
    <!-- Main setup activity -->
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    
    <!-- WorkManager needs this for boot persistence -->
    <receiver android:name="androidx.work.impl.background.systemalarm.RescheduleReceiver"
        android:enabled="true" android:exported="false" />
</application>
```

### Watch Manifest Additions
```xml
<uses-feature android:name="android.hardware.type.watch" />

<application>
    <!-- Data receiver service -->
    <service android:name=".DataLayerReceiver" android:exported="true">
        <intent-filter>
            <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
            <data android:scheme="wear" android:host="*" android:pathPrefix="/daily_note" />
        </intent-filter>
    </service>
    
    <!-- Tile service -->
    <service android:name=".tile.DailyNoteTileService"
        android:exported="true"
        android:label="Daily Note"
        android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
        <intent-filter>
            <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
        </intent-filter>
        <meta-data android:name="androidx.wear.tiles.PREVIEW"
            android:resource="@drawable/tile_preview" />
    </service>
    
    <!-- Main activity -->
    <activity android:name=".ui.MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

---

## Key Dependencies

### Phone (app/build.gradle.kts)
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

### Watch (wear/build.gradle.kts)
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.wear:wear:1.3.0")
    
    // Compose for Wear OS
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.wear.compose:compose-navigation:1.3.0")
    
    // Tiles
    implementation("androidx.wear.tiles:tiles:1.3.0")
    implementation("androidx.wear.protolayout:protolayout:1.1.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.1.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.1.0")
    
    // Horologist for rotary input
    implementation("com.google.android.horologist:horologist-compose-layout:0.5.28")
    
    // Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

---

## Implementation Order

1. **Project Setup** (~30 min)
   - Create Android Studio project with mobile + wear modules
   - Configure Gradle dependencies
   - Set up package structure

2. **Phone Companion** (~2 hrs)
   - Folder picker UI with SAF
   - Daily note reader logic
   - Data Layer sender
   - WorkManager scheduler
   - Simple status UI

3. **Watch Receiver** (~1 hr)
   - WearableListenerService
   - Local note repository (SharedPreferences)
   
4. **Watch Tile** (~1.5 hrs)
   - ProtoLayout tile showing last lines
   - "Open Full" button
   - Tile refresh on data change

5. **Watch Reader** (~1 hr)
   - Compose screen with full note
   - Horologist rotary scroll
   - Focus handling for bezel

6. **Testing & Polish** (~1 hr)
   - Test on device
   - Handle edge cases (empty note, no sync, etc.)
   - Add loading states

**Estimated Total: ~7 hours**

---

## Edge Cases to Handle

1. **Empty daily note**: Show "No entries yet" message
2. **No daily note for today**: Fall back to most recent note with date shown
3. **No folder selected**: Prompt to open phone app
4. **Watch disconnected**: Use cached note, show "Last synced: X"
5. **First install**: Guide user through folder selection
6. **Permission revoked**: Detect and prompt re-selection

---

## Questions Before Starting?

This plan is ready for implementation. I can proceed directly, or if you'd like to modify anything (sync frequency, tile content length, etc.), let me know.
