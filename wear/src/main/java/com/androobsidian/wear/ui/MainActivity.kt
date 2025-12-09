package com.androobsidian.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.*
import androidx.compose.ui.unit.sp
import com.androobsidian.wear.data.NoteRepository
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = NoteRepository.getInstance(this)

        setContent {
            NoteReaderScreen(repository)
        }
    }
}

@Composable
fun NoteReaderScreen(noteRepository: NoteRepository) {
    val note by noteRepository.noteFlow.collectAsState()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // Request focus on composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Re-request focus on resume
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

    MaterialTheme {
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
                // Top padding for time text
                Spacer(modifier = Modifier.height(32.dp))

                // Date header with update time
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val currentNote = note
                val headerText = if (currentNote != null) {
                    val updateTime = timeFormat.format(Date(currentNote.receivedAt))
                    "${currentNote.date} • Updated $updateTime"
                } else ""
                
                Text(
                    text = headerText,
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Full note content (60% smaller - ~10sp vs default 16sp)
                Text(
                    text = note?.fullText?.ifBlank { "No entries yet." } 
                        ?: "No note available.\n\nOpen the phone app to sync.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colors.onSurface
                )

                // Bottom padding for scroll comfort
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
