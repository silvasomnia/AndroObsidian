# AndroObsidian

Display your Obsidian daily notes on your Galaxy Watch.

## Features

- **Watch Tile**: Shows the last 9 lines (newest entries) with compact 9sp font
- **Full Reader**: Scroll through the complete note using the rotating bezel (10sp font)
- **Auto-sync**: Updates every 30 minutes in the background
- **Manual sync**: Tap "Sync Now" in the phone app anytime
- **Last updated time**: Shows "Updated HH:MM" on both tile and reader
- **Task support**: `- [ ]` → ☐, `- [x]` → ☑
- **Markdown stripping**: Headers, bold, italic, links, lists, highlights, comments, frontmatter
- **Obsidian syntax**: Wikilinks `[[page]]`, embeds `![[image]]`, tags `#tag`
- **Robust**: Handles large notes (truncates at 50KB), corrupted cache, malformed input

## Architecture

**Companion app model** — the watch can't access phone storage directly:

```
Phone App                          Watch App
─────────────────                  ─────────────────
SAF folder picker                  WearableListenerService
     ↓                                  ↓
DailyNoteReader                    NoteRepository
     ↓                             (SharedPreferences)
DataLayerSender  ──── Wearable ────     ↓
(truncates >50KB)     Data Layer   Tile + Reader UI
```

## Setup

1. **Install both APKs**:
   - `app-debug.apk` → Phone
   - `wear-debug.apk` → Watch

2. **Phone app**:
   - Open AndroObsidian
   - Tap "Select Daily Notes Folder"
   - Navigate to your Obsidian vault's `Daily Notes` folder
   - Tap "Use this folder"

3. **Watch tile**:
   - Long-press watch face
   - Swipe to Tiles → tap "+"
   - Select "Daily Note"

## Daily Note Format

Expects files named `YYYY-MM-DD.md` (e.g., `2025-12-09.md`).

The app reads today's note first; if empty/missing, it finds the most recent non-empty note.

## Markdown Handling

| Syntax | Display |
|--------|---------|
| `- [ ] task` | ☐ task |
| `- [x] done` | ☑ done |
| `# Header` | Header (no #) |
| `**bold**` | bold |
| `*italic*` | italic |
| `[[link]]` | link |
| `[[link\|alias]]` | alias |
| `![[image.png]]` | [image] |
| `==highlight==` | highlight |
| `%%comment%%` | (removed) |
| `#tag` | tag |
| YAML frontmatter | (removed) |

## Project Structure

```
app/        Phone companion app (SAF + Data Layer sender)
wear/       Watch app (Tile + Compose reader with Horologist rotary scroll)
shared/     Common constants (DataLayerPaths)
```

## Key Dependencies

- **Wear Compose**: Material design for Wear OS
- **Horologist**: Rotary bezel scroll support
- **ProtoLayout**: Tile rendering
- **Wearable Data Layer API**: Phone-watch communication
- **WorkManager**: Background sync scheduling

## Building

```bash
./gradlew assembleDebug
```

APKs output to:
- `app/build/outputs/apk/debug/app-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

## Robustness

- Large notes (>50KB) auto-truncated with indicator
- Stale data rejected via timestamp comparison
- Corrupted SharedPreferences handled gracefully
- Malformed markdown falls back to original text
- All errors logged, never crash

## Requirements

- Phone: Android 8.0+ (API 26+)
- Watch: Wear OS 3+ (API 30+), tested on Galaxy Watch 8 Classic
- Both devices must be paired via Wear OS app
