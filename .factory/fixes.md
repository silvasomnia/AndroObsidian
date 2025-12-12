## 2025-12-12 - Tile shows newest lines

**Severity:** Medium

**Error:** Watch tile showed the top of the snippet instead of the most recent note lines, and empty lines were dropped.

**Cause:** Phone sent a 12-line, non-blank-only snippet while ProtoLayout truncates overflow from the end, so the tile kept earlier lines and cut off the newest.

**Fix:** Send only the last 9 lines including blanks from the phone, and clamp the watch tile to the last 9 lines before rendering.

**Files:** app/src/main/java/com/androobsidian/mobile/DailyNoteReader.kt, wear/src/main/java/com/androobsidian/wear/tile/DailyNoteTileService.kt, README.md, .factory/fixes.md

## 2025-12-12 - Regex split limit crash

**Severity:** High

**Error:** Manual sync found no notes and never sent data; watch stayed on fallback text.

**Cause:** `Regex.split(..., limit = -1)` throws on Android because Kotlin requires a non‑negative limit, so note parsing aborted.

**Fix:** Use `limit = Int.MAX_VALUE` to preserve trailing empty lines without crashing.

**Files:** app/src/main/java/com/androobsidian/mobile/DailyNoteReader.kt, wear/src/main/java/com/androobsidian/wear/tile/DailyNoteTileService.kt, .factory/fixes.md

## 2025-12-12 - Tile truncation due to wrapping

**Severity:** Medium

**Error:** Tile still appeared to show the start of the note because long lines wrapped and ProtoLayout truncated the bottom.

**Cause:** Even with last 9 logical lines, very long lines expanded into multiple rendered lines, exceeding tile height/maxLines. Tiles truncate overflow from the end, keeping older lines.

**Fix:** Ellipsize each tile line to a conservative max character count to avoid wrapping, and set tile `maxLines` to 9.

**Files:** app/src/main/java/com/androobsidian/mobile/DailyNoteReader.kt, wear/src/main/java/com/androobsidian/wear/tile/DailyNoteTileService.kt, .factory/fixes.md
