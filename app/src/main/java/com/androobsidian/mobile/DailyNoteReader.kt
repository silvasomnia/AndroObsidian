package com.androobsidian.mobile

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class NoteContent(
    val date: String,
    val fullText: String,
    val lastLines: String
)

private const val TILE_LINE_COUNT = 9
private const val TILE_MAX_CHARS_PER_LINE = 50

class DailyNoteReader(private val context: Context) {

    fun readLatestNote(treeUri: Uri): NoteContent? {
        val dailyNotesFolder = findDailyNotesFolder(treeUri) ?: return null
        
        // Try today first
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        var note = readNoteForDate(dailyNotesFolder, today)
        
        // If today's note is empty or missing, find most recent non-empty note
        if (note == null || note.fullText.isBlank()) {
            note = findMostRecentNote(dailyNotesFolder)
        }
        
        return note
    }
    
    private fun readNoteForDate(folder: DocumentFile, date: String): NoteContent? {
        val filename = "$date.md"
        val noteFile = folder.findFile(filename) ?: return null
        
        return readNoteFile(noteFile, date)
    }
    
    private fun findMostRecentNote(folder: DocumentFile): NoteContent? {
        val datePattern = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")
        
        // Get all date-named files, sorted descending
        val noteFiles = folder.listFiles()
            .filter { it.isFile && it.name?.matches(datePattern) == true }
            .sortedByDescending { it.name }
        
        // Find first non-empty note
        for (file in noteFiles) {
            val date = file.name?.removeSuffix(".md") ?: continue
            val note = readNoteFile(file, date)
            if (note != null && note.fullText.isNotBlank()) {
                return note
            }
        }
        
        return null
    }
    
    private fun readNoteFile(file: DocumentFile, date: String): NoteContent? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                NoteContent(
                    date = date,
                    fullText = text,
                    lastLines = extractLastLines(text, lineCount = TILE_LINE_COUNT)
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("DailyNoteReader", "Failed to read note: ${file.name}", e)
            null
        }
    }

    private fun findDailyNotesFolder(treeUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        
        // Check if the selected folder itself IS the Daily Notes folder
        // (contains date-named .md files)
        val datePattern = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")
        val hasDateFiles = root.listFiles().any { 
            it.isFile && it.name?.matches(datePattern) == true 
        }
        if (hasDateFiles) return root
        
        // Otherwise look for "Daily Notes" subfolder
        return root.findFile("Daily Notes") 
            ?: root.listFiles().find { it.isDirectory && it.name?.contains("Daily", ignoreCase = true) == true }
    }

    private fun extractLastLines(text: String, lineCount: Int): String {
        val cleanedText = preprocessText(text)
        
        // Process lines: strip markdown, filter blanks, then wrap long lines
        val wrappedLines = cleanedText.lines()
            .map { stripMarkdown(it) }
            .filter { it.isNotBlank() }
            .flatMap { wrapLine(it, TILE_MAX_CHARS_PER_LINE) }
        
        return wrappedLines.takeLast(lineCount).joinToString("\n")
    }
    
    private fun wrapLine(line: String, maxChars: Int): List<String> {
        if (maxChars <= 0) return listOf(line)
        if (line.length <= maxChars) return listOf(line)
        
        val result = mutableListOf<String>()
        var remaining = line
        
        while (remaining.length > maxChars) {
            // Find last space within limit for word wrap
            val breakPoint = remaining.lastIndexOf(' ', maxChars)
            var splitAt = if (breakPoint > 0) breakPoint else maxChars
            
            // Avoid splitting surrogate pairs (emojis)
            if (splitAt > 0 && Character.isHighSurrogate(remaining[splitAt - 1])) {
                splitAt--
            }
            
            result.add(remaining.take(splitAt).trimEnd())
            remaining = remaining.drop(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) {
            result.add(remaining)
        }
        return result
    }

    private fun stripMarkdown(line: String): String {
        if (line.trim() == "---") return ""
        
        return try {
            var result = line
            for ((pattern, replacement) in MARKDOWN_PATTERNS) {
                result = result.replace(pattern, replacement)
            }
            result.trim()
        } catch (e: Exception) {
            android.util.Log.w("DailyNoteReader", "stripMarkdown failed, using original", e)
            line.trim()
        }
    }
    
    private fun preprocessText(text: String): String {
        return try {
            text.replace(FRONTMATTER_REGEX, "")
        } catch (e: Exception) {
            text
        }
    }
    
    companion object {
        private val FRONTMATTER_REGEX = Regex("""^---\r?\n[\s\S]*?\r?\n---\r?\n*""")
        
        private val MARKDOWN_PATTERNS = listOf(
            Regex("""^\s*[-*+]\s*\[\s*[xX]\s*]\s*""") to "☑ ",      // Checked task
            Regex("""^\s*[-*+]\s*\[\s*]\s*""") to "☐ ",             // Unchecked task
            Regex("""^#+\s*""") to "",                               // Headers
            Regex("""\*\*(.+?)\*\*""") to "$1",                      // Bold
            Regex("""\*(.+?)\*""") to "$1",                          // Italic
            Regex("""__(.+?)__""") to "$1",                          // Bold alt
            Regex("""_(.+?)_""") to "$1",                            // Italic alt
            Regex("""~~(.+?)~~""") to "$1",                          // Strikethrough
            Regex("""`(.+?)`""") to "$1",                            // Inline code
            Regex("""^\s*[-*+]\s+""") to "• ",                       // List items
            Regex("""^\s*\d+\.\s+""") to "• ",                       // Numbered lists
            Regex("""\[(.+?)]\(.+?\)""") to "$1",                    // Links
            Regex("""!\[.*?]\(.+?\)""") to "",                       // Images
            Regex("""!\[\[.+?]]""") to "[image]",                    // Obsidian embeds
            Regex("""\[\[(.+?)\|(.+?)]]""") to "$2",                 // Aliased wikilinks
            Regex("""\[\[(.+?)]]""") to "$1",                        // Wikilinks
            Regex("""==(.+?)==""") to "$1",                          // Highlights
            Regex("""%%.*?%%""") to "",                              // Comments
            Regex("""#([a-zA-Z0-9_-]+)""") to "$1"                   // Tags
        )
    }
}
