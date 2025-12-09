package com.androobsidian.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupScreen()
                }
            }
        }
    }
    
    @Composable
    fun SetupScreen() {
        val prefs = remember { getSharedPreferences(SyncWorker.PREFS_NAME, MODE_PRIVATE) }
        var vaultUri by remember { mutableStateOf(prefs.getString(SyncWorker.KEY_VAULT_URI, null)) }
        var lastSync by remember { mutableStateOf(prefs.getLong(SyncWorker.KEY_LAST_SYNC, 0)) }
        var lastNoteDate by remember { mutableStateOf(prefs.getString(SyncWorker.KEY_LAST_NOTE_DATE, null)) }
        var isSyncing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        
        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                // Persist permission
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                prefs.edit().putString(SyncWorker.KEY_VAULT_URI, it.toString()).apply()
                vaultUri = it.toString()
                
                // Schedule periodic sync
                SyncWorker.schedule(this@MainActivity)
                
                // Trigger immediate sync (direct, not via WorkManager)
                scope.launch {
                    isSyncing = true
                    val success = performDirectSync(it, prefs)
                    if (success) {
                        lastSync = prefs.getLong(SyncWorker.KEY_LAST_SYNC, 0)
                        lastNoteDate = prefs.getString(SyncWorker.KEY_LAST_NOTE_DATE, null)
                    }
                    isSyncing = false
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "AndroObsidian",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Daily Notes on Your Watch",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            if (vaultUri == null) {
                Text(
                    text = "Select your Obsidian vault's Daily Notes folder to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                Button(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Daily Notes Folder")
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "✓ Vault connected",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (lastSync > 0) {
                            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            Text(
                                text = "Last sync: ${dateFormat.format(Date(lastSync))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (lastNoteDate != null) {
                            Text(
                                text = "Latest note: $lastNoteDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            val uri = vaultUri?.toUri()
                            if (uri != null) {
                                val success = performDirectSync(uri, prefs)
                                if (success) {
                                    lastSync = prefs.getLong(SyncWorker.KEY_LAST_SYNC, 0)
                                    lastNoteDate = prefs.getString(SyncWorker.KEY_LAST_NOTE_DATE, null)
                                }
                            }
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSyncing) "Syncing..." else "Sync Now")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Folder")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Syncs automatically every 30 minutes.\nAdd the tile on your watch to see your notes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    
    private suspend fun performDirectSync(treeUri: Uri, prefs: android.content.SharedPreferences): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("MainActivity", "performDirectSync starting with uri: $treeUri")
                val reader = DailyNoteReader(this@MainActivity)
                val sender = DataLayerSender(this@MainActivity)
                
                val note = reader.readLatestNote(treeUri)
                if (note == null) {
                    android.util.Log.w("MainActivity", "No note found!")
                    return@withContext false
                }
                android.util.Log.d("MainActivity", "Read note: ${note.date}, ${note.fullText.length} chars")
                
                val sent = sender.sendNote(note)
                android.util.Log.d("MainActivity", "sendNote result: $sent")
                
                if (sent) {
                    prefs.edit()
                        .putLong(SyncWorker.KEY_LAST_SYNC, System.currentTimeMillis())
                        .putString(SyncWorker.KEY_LAST_NOTE_DATE, note.date)
                        .apply()
                }
                sent
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Sync failed", e)
                false
            }
        }
    }
}
