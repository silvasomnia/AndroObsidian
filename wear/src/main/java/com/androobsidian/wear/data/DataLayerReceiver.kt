package com.androobsidian.wear.data

import android.util.Log
import com.androobsidian.shared.DataLayerPaths
import com.androobsidian.wear.tile.DailyNoteTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

class DataLayerReceiver : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged called with ${dataEvents.count} events")
        dataEvents.use {
            it.forEach { event ->
                Log.d(TAG, "Event type: ${event.type}, path: ${event.dataItem.uri.path}")
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val dataItem = event.dataItem
                    if (dataItem.uri.path == DataLayerPaths.DAILY_NOTE_PATH) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

                            val incomingTimestamp = dataMap.getLong(DataLayerPaths.Keys.TIMESTAMP)
                            val existingNote = NoteRepository.getInstance(this).getNote()
                            
                            // Skip if incoming data is older than what we have
                            if (existingNote != null && incomingTimestamp < existingNote.receivedAt) {
                                Log.d(TAG, "Skipping stale data (incoming: $incomingTimestamp, existing: ${existingNote.receivedAt})")
                                return@forEach
                            }
                            
                            val note = CachedNote(
                                date = dataMap.getString(DataLayerPaths.Keys.DATE) ?: "",
                                fullText = dataMap.getString(DataLayerPaths.Keys.FULL_TEXT) ?: "",
                                lastLines = dataMap.getString(DataLayerPaths.Keys.LAST_LINES) ?: "",
                                receivedAt = incomingTimestamp
                            )

                            Log.d(TAG, "Received note for date: ${note.date}, saving...")
                            // Save to local cache
                            NoteRepository.getInstance(this).saveNote(note)

                            // Trigger tile refresh
                            TileService.getUpdater(this)
                                .requestUpdate(DailyNoteTileService::class.java)
                            Log.d(TAG, "Note saved and tile refresh requested")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing data", e)
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "DataLayerReceiver"
    }
}
