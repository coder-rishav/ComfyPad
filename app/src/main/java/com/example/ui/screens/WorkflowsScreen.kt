package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.data.database.WorkflowPreset
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkflowsScreen(
    viewModel: MainViewModel,
    onWorkflowLoaded: () -> Unit
) {
    val context = LocalContext.current
    val presets by viewModel.allPresets.collectAsState(initial = emptyList())
    val activeWorkflowId by viewModel.activeWorkflowId.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var workflowToRename by remember { mutableStateOf<WorkflowPreset?>(null) }
    var textInput by remember { mutableStateOf("") }

    var selectedExportPreset by remember { mutableStateOf<WorkflowPreset?>(null) }

    // Import file picker Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importWorkflowPreset(context, uri, "Imported Workflow ${System.currentTimeMillis() / 10000}")
            Toast.makeText(context, "Workflow JSON imported successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Export file picker Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && selectedExportPreset != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(selectedExportPreset!!.jsonContent.toByteArray())
                }
                Toast.makeText(context, "Workflow exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Workflow Presets",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Load, create, import, and export ComfyUI node workflows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Import FAB
                SmallFloatingActionButton(
                    onClick = { importLauncher.launch("application/json") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("import_workflow_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Import JSON Workflow")
                }

                // Add Preset FAB
                FloatingActionButton(
                    onClick = {
                        textInput = ""
                        showAddDialog = true
                    },
                    modifier = Modifier.testTag("add_workflow_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save Current as Workflow")
                }
            }
        }
    ) { innerPadding ->
        if (presets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Empty Workflows Info",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No local workflows found.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Press the + button to save your current setup as a preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("workflows_preset_grid")
            ) {
                items(presets) { preset ->
                    val isActive = preset.id == activeWorkflowId
                    val formattedDate = SimpleDateFormat("MMM d, yyyy - HH:mm", Locale.getDefault())
                        .format(Date(preset.dateModified))

                    // Card representing each preset
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    viewModel.loadWorkflow(preset)
                                    onWorkflowLoaded()
                                    Toast
                                        .makeText(
                                            context,
                                            "Loaded: ${preset.name}",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                },
                                onLongClick = {
                                    workflowToRename = preset
                                    textInput = preset.name
                                    showRenameDialog = true
                                }
                            ),
                        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail / Abstract Gradient Indicator
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (preset.thumbnailPath != null && File(preset.thumbnailPath).exists()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = File(preset.thumbnailPath)),
                                        contentDescription = "Workflow Thumbnail Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Metadata & text details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Modified: $formattedDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (preset.isDefault) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "BUILT-IN",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Actions
                            Row {
                                // Export Button
                                IconButton(
                                    onClick = {
                                        selectedExportPreset = preset
                                        exportLauncher.launch("workflow_${preset.name.lowercase().replace(" ", "_")}.json")
                                    }
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = "Export JSON")
                                }

                                if (!preset.isDefault) {
                                    // Delete Button
                                    IconButton(
                                        onClick = {
                                            viewModel.deletePreset(preset)
                                            Toast.makeText(context, "Preset deleted.", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete preset", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Active preset saving prompt
        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Save Workflow Preset",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Workflow Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (textInput.trim().isNotEmpty()) {
                                        viewModel.saveCurrentAsWorkflowPreset(textInput.trim())
                                        showAddDialog = false
                                        Toast.makeText(context, "Preset saved!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = textInput.trim().isNotEmpty()
                            ) {
                                Text("Save Settings")
                            }
                        }
                    }
                }
            }
        }

        // Long press Rename dialog
        if (showRenameDialog && workflowToRename != null) {
            Dialog(onDismissRequest = { showRenameDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Rename Preset",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("New Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (textInput.trim().isNotEmpty()) {
                                        viewModel.updatePresetName(workflowToRename!!, textInput.trim())
                                        showRenameDialog = false
                                        workflowToRename = null
                                        Toast.makeText(context, "Preset renamed!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = textInput.trim().isNotEmpty()
                            ) {
                                Text("Rename")
                            }
                        }
                    }
                }
            }
        }
    }
}
