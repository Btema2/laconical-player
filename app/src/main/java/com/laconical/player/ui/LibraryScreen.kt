package com.laconical.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Main library screen that displays local audio files and handles media permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Laconical Library") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) {
                val tracks by viewModel.tracks.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.loadTracks()
                }

                if (tracks.isEmpty()) {
                    Text("No tracks found")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tracks) { track ->
                            ListItem(
                                headlineContent = { Text(track.title) },
                                supportingContent = { Text(track.artist) },
                                modifier = Modifier.clickable {
                                    viewModel.playTrack(track)
                                }
                            )
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to access audio files")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
