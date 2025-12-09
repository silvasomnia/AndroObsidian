package com.androobsidian.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CachedNote(
    val date: String,
    val fullText: String,
    val lastLines: String,
    val receivedAt: Long
)

class NoteRepository(private val context: Context) {
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _noteFlow = MutableStateFlow<CachedNote?>(null)
    val noteFlow: StateFlow<CachedNote?> = _noteFlow.asStateFlow()
    
    init {
        _noteFlow.value = getNote()
    }
    
    fun saveNote(note: CachedNote) {
        try {
            prefs.edit()
                .putString(KEY_DATE, note.date)
                .putString(KEY_FULL_TEXT, note.fullText)
                .putString(KEY_LAST_LINES, note.lastLines)
                .putLong(KEY_RECEIVED_AT, note.receivedAt)
                .apply()
            
            _noteFlow.value = note
        } catch (e: Exception) {
            // SharedPreferences write failed - continue with in-memory value
            _noteFlow.value = note
        }
    }
    
    fun getNote(): CachedNote? {
        return try {
            val date = prefs.getString(KEY_DATE, null) ?: return null
            val fullText = prefs.getString(KEY_FULL_TEXT, "") ?: ""
            val lastLines = prefs.getString(KEY_LAST_LINES, "") ?: ""
            val receivedAt = prefs.getLong(KEY_RECEIVED_AT, 0)
            
            CachedNote(date, fullText, lastLines, receivedAt)
        } catch (e: Exception) {
            // SharedPreferences corrupted - return null, will show "no note" message
            null
        }
    }
    
    companion object {
        private const val PREFS_NAME = "androobsidian_watch"
        private const val KEY_DATE = "date"
        private const val KEY_FULL_TEXT = "full_text"
        private const val KEY_LAST_LINES = "last_lines"
        private const val KEY_RECEIVED_AT = "received_at"
        
        @Volatile
        private var instance: NoteRepository? = null
        
        fun getInstance(context: Context): NoteRepository {
            return instance ?: synchronized(this) {
                instance ?: NoteRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
