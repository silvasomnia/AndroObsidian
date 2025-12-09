package com.androobsidian.mobile

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val treeUri = prefs.getString(KEY_VAULT_URI, null)?.toUri() ?: return Result.failure()

        val reader = DailyNoteReader(applicationContext)
        val sender = DataLayerSender(applicationContext)

        val note = reader.readLatestNote(treeUri)
        
        return if (note != null) {
            val sent = sender.sendNote(note)
            if (sent) {
                prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .putString(KEY_LAST_NOTE_DATE, note.date)
                    .apply()
                Result.success()
            } else {
                Result.retry()
            }
        } else {
            Result.success() // No note to send is not a failure
        }
    }

    companion object {
        const val PREFS_NAME = "androobsidian_prefs"
        const val KEY_VAULT_URI = "vault_uri"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_NOTE_DATE = "last_note_date"
        private const val WORK_NAME = "daily_note_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
