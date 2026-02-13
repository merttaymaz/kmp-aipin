package com.jetbrains.kmpapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show confirmation dialog if needed
    if (uiState.showConfirmationDialog && uiState.pendingDownload != null) {
        DownloadConfirmationDialog(
            modelInfo = uiState.pendingDownload!!,
            networkType = uiState.networkType,
            onConfirm = { viewModel.confirmDownload() },
            onDismiss = { viewModel.dismissConfirmationDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Network status
            NetworkStatusCard(uiState.networkType)

            Spacer(modifier = Modifier.height(16.dp))

            // Current download progress
            if (uiState.currentDownload !is ModelDownloadStatus.Idle) {
                DownloadProgressCard(uiState.currentDownload)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error message
            if (uiState.error != null) {
                ErrorCard(
                    error = uiState.error,
                    onDismiss = { viewModel.clearError() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Models list
            Text(
                text = "Available Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.availableModels) { model ->
                        ModelCard(
                            modelInfo = model,
                            onDownload = { viewModel.downloadModelWithConfirmation(model) },
                            onDelete = { viewModel.deleteModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkStatusCard(networkType: NetworkType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (networkType) {
                NetworkType.WIFI, NetworkType.ETHERNET -> MaterialTheme.colorScheme.primaryContainer
                NetworkType.CELLULAR -> MaterialTheme.colorScheme.tertiaryContainer
                NetworkType.NONE -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Network Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (networkType) {
                        NetworkType.WIFI -> "Connected to WiFi ✓"
                        NetworkType.ETHERNET -> "Connected to Ethernet ✓"
                        NetworkType.CELLULAR -> "Using Cellular Data"
                        NetworkType.NONE -> "No Connection"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Icon(
                imageVector = when (networkType) {
                    NetworkType.WIFI, NetworkType.ETHERNET -> Icons.Default.Check
                    NetworkType.CELLULAR -> Icons.Default.Warning
                    NetworkType.NONE -> Icons.Default.Close
                },
                contentDescription = null
            )
        }
    }
}

@Composable
fun DownloadProgressCard(status: ModelDownloadStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (status) {
                is ModelDownloadStatus.Checking -> {
                    Text("Checking model availability...")
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                is ModelDownloadStatus.Downloading -> {
                    val progressPercent = (status.progress * 100).toInt()
                    val downloadedMB = status.downloaded / (1024 * 1024)
                    val totalMB = status.total / (1024 * 1024)

                    Text("Downloading... $progressPercent%")
                    Text(
                        text = "$downloadedMB MB / $totalMB MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                is ModelDownloadStatus.Downloaded -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("Download completed!")
                    }
                }

                is ModelDownloadStatus.Failed -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("Download failed: ${status.error}")
                    }
                }

                ModelDownloadStatus.Idle -> {
                    // No download in progress
                }
            }
        }
    }
}

@Composable
fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
fun ModelCard(
    modelInfo: ModelInfo,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Language: ${modelInfo.language?.uppercase() ?: "Multiple"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Size: ${modelInfo.size / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (modelInfo.isDownloaded) {
                        Text(
                            text = "✓ Downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (modelInfo.isDownloaded) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    FilledIconButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download model"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadConfirmationDialog(
    modelInfo: ModelInfo,
    networkType: NetworkType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Download using Cellular Data?") },
        text = {
            Column {
                Text(
                    "You are not connected to WiFi. Downloading \"${modelInfo.displayName}\" " +
                            "(${modelInfo.size / (1024 * 1024)} MB) will use your cellular data."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Do you want to continue?",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Download Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
