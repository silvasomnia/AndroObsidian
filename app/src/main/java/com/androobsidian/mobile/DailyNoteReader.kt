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
                    lastLines = extractLastLines(text, lineCount = 12)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyNoteReader", "Failed to read note file: ${file.name}", e)
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
        val lines = cleanedText.lines()
            .map { stripMarkdown(it) }
            .filter { it.isNotBlank() }
        return lines.takeLast(lineCount).joinToString("\n")
    }
    
    private fun stripMarkdown(line: String): String {
        // Skip frontmatter delimiters and blank results
        if (line.trim() == "---") return ""
        
        // Wrap in try-catch to handle pathological regex input gracefully
        return try {
            line
            // Tasks: - [ ] → ☐, - [x] → ☑
            .replace(Regex("""^\s*[-*+]\s*\[\s*[xX]\s*]\s*"""), "☑ ")
            .replace(Regex("""^\s*[-*+]\s*\[\s*]\s*"""), "☐ ")
            // Headers: # ## ###
            .replace(Regex("""^#+\s*"""), "")
            // Bold/Italic
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""\*(.+?)\*"""), "$1")
            .replace(Regex("""__(.+?)__"""), "$1")
            .replace(Regex("""_(.+?)_"""), "$1")
            // Strikethrough
            .replace(Regex("""~~(.+?)~~"""), "$1")
            // Inline code
            .replace(Regex("""`(.+?)`"""), "$1")
            // List items → bullet (after task handling)
            .replace(Regex("""^\s*[-*+]\s+"""), "• ")
            // Numbered lists
            .replace(Regex("""^\s*\d+\.\s+"""), "")
            // Standard markdown links/images
            .replace(Regex("""\[(.+?)]\(.+?\)"""), "$1")
            .replace(Regex("""!\[.*?]\(.+?\)"""), "")
            // Obsidian wikilinks: [[link]] → link, [[link|alias]] → alias
            .replace(Regex("""!\[\[.+?]]"""), "[image]")  // Embedded images
            .replace(Regex("""\[\[(.+?)\|(.+?)]]"""), "$2")  // Aliased links
            .replace(Regex("""\[\[(.+?)]]"""), "$1")  // Simple links
            // Obsidian highlights and comments
            .replace(Regex("""==(.+?)=="""), "$1")  // Highlights
            .replace(Regex("""%%.*?%%"""), "")  // Comments
            // Tags (keep them, just remove #)
            .replace(Regex("""#([a-zA-Z0-9_-]+)"""), "$1")
            .trim()
        } catch (e: Exception) {
            // Regex failed on pathological input - return original line trimmed
            line.trim()
        }
    }
    
    private fun preprocessText(text: String): String {
        return try {
            // Remove YAML frontmatter block
            val frontmatterRegex = Regex("""^---\n[\s\S]*?\n---\n*""", RegexOption.MULTILINE)
            text.replace(frontmatterRegex, "")
        } catch (e: Exception) {
            text // Return original if regex fails
        }
    }
}
