package com.androobsidian.mobile

import android.content.Context
import android.util.Log
import com.androobsidian.shared.DataLayerPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class DataLayerSender(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    suspend fun sendNote(note: NoteContent): Boolean {
        Log.d(TAG, "sendNote called for date: ${note.date}, text length: ${note.fullText.length}")
        
        // Truncate full text if too large (Data Layer limit is ~100KB, stay safe at 50KB)
        val maxTextSize = 50_000
        val fullText = if (note.fullText.length > maxTextSize) {
            Log.w(TAG, "Note truncated from ${note.fullText.length} to $maxTextSize chars")
            note.fullText.take(maxTextSize) + "\n\n[... truncated for watch display]"
        } else {
            note.fullText
        }
        
        return try {
            val request = PutDataMapRequest.create(DataLayerPaths.DAILY_NOTE_PATH).apply {
                dataMap.putString(DataLayerPaths.Keys.DATE, note.date)
                dataMap.putString(DataLayerPaths.Keys.FULL_TEXT, fullText)
                dataMap.putString(DataLayerPaths.Keys.LAST_LINES, note.lastLines)
                dataMap.putLong(DataLayerPaths.Keys.TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Log.d(TAG, "Calling putDataItem...")
            val result = dataClient.putDataItem(request).await()
            Log.d(TAG, "putDataItem success: ${result.uri}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "putDataItem failed", e)
            false
        }
    }
    
    companion object {
        private const val TAG = "DataLayerSender"
    }
}
