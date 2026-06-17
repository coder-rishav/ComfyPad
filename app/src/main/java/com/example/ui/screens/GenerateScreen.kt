package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SelectedLora
import com.example.ui.viewmodel.ModelType
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReusableDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("None available", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)) },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    options.forEach { option ->
                        val badge = if (label == "Checkpoint Model") {
                            val name = option.lowercase()
                            when {
                                name.contains("flux") || name.contains("gguf") -> "FLUX"
                                name.contains("turbo") || name.contains("lightning") || name.contains("hyper") -> "TURBO"
                                name.contains("xl") || name.contains("sdxl") || name.contains("pony") || name.contains("illustrious") -> "SDXL"
                                else -> "SD1.5"
                            }
                        } else null

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (badge != null) {
                                        val containerColor = when (badge) {
                                            "FLUX" -> MaterialTheme.colorScheme.primaryContainer
                                            "SDXL" -> MaterialTheme.colorScheme.secondaryContainer
                                            "TURBO" -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val textColor = when (badge) {
                                            "FLUX" -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "SDXL" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            "TURBO" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Surface(
                                            color = containerColor,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = textColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(viewModel: MainViewModel) {
    val positivePrompt by viewModel.positivePrompt.collectAsState()
    val negativePrompt by viewModel.negativePrompt.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val cfg by viewModel.cfg.collectAsState()
    val width by viewModel.width.collectAsState()
    val height by viewModel.height.collectAsState()
    val seed by viewModel.seed.collectAsState()
    val isSeedRandom by viewModel.isSeedRandom.collectAsState()

    val assets by viewModel.assets.collectAsState()
    val assetsLoading by viewModel.assetsLoading.collectAsState()
    val assetsError by viewModel.assetsError.collectAsState()

    val selectedCheckpoint by viewModel.selectedCheckpoint.collectAsState()
    val selectedVae by viewModel.selectedVae.collectAsState()
    val sampler by viewModel.sampler.collectAsState()
    val selectedScheduler by viewModel.selectedScheduler.collectAsState()
    val clipSkip by viewModel.clipSkip.collectAsState()
    val hiresEnabled by viewModel.hiresEnabled.collectAsState()
    val hiresUpscaler by viewModel.hiresUpscaler.collectAsState()
    val hiresScale by viewModel.hiresScale.collectAsState()
    val hiresSteps by viewModel.hiresSteps.collectAsState()
    val hiresDenoise by viewModel.hiresDenoise.collectAsState()
    val batchCount by viewModel.batchCount.collectAsState()
    val selectedLoras by viewModel.selectedLoras.collectAsState()
    val modelType by viewModel.modelType.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val totalSteps by viewModel.totalSteps.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val queueRemaining by viewModel.queueRemaining.collectAsState()
    val lastGeneratedImage by viewModel.lastGeneratedImage.collectAsState()
    val generationError by viewModel.generationError.collectAsState()

    val promptHistory by viewModel.promptHistory.collectAsState(initial = emptyList())
    val favoritePrompts by viewModel.favoritePrompts.collectAsState(initial = emptyList())

    // UI Local controls
    var negativeExp by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAddLoraMenu by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    if (isGenerating) {
        KeepScreenOn()
    }

    // Show error toast style snackbar if exists
    var showErrorSnackbar by remember { mutableStateOf(false) }
    LaunchedEffect(generationError) {
        if (generationError != null) {
            showErrorSnackbar = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 90.dp) // buffer for generate button sheet
        ) {
            // Header Row (Geometric Balance layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ComfyPad",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Glowing status indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isGenerating) Color(0xFFD0BCFF) else Color(0xFF4ADE80),
                                    shape = RoundedCornerShape(100.dp)
                                )
                        )
                        Text(
                            text = "${viewModel.settingsManager.serverIp}:${viewModel.settingsManager.serverPort}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Refresh Server assets
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.loadAssetsFromServer()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50.dp))
                    ) {
                        if (assetsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Options",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // History Prompts
                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50.dp))
                            .testTag("prompt_history_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Prompt History",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show connection / elements error banner if server fetch fails
            assetsError?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error info",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Server assets load issue: $err (Using cached fallback)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Positive text prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "POSITIVE PROMPT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = positivePrompt,
                        onValueChange = { viewModel.updatePositivePrompt(it) },
                        placeholder = { Text("Describe the image you want build...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .testTag("positive_prompt_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal quick styles
                    Text(
                        text = "QUICK STYLE TAGS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(viewModel.quickStyleTags) { tag ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.appendStyleTag(tag)
                                },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                    }
                }
            }

            if (modelType != ModelType.FLUX) {
                Spacer(modifier = Modifier.height(10.dp))

                // Collapsible Negative Prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { negativeExp = !negativeExp },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NEGATIVE PROMPT",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (negativeExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand collapsible",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = negativeExp) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = negativePrompt,
                                    onValueChange = { viewModel.updateNegativePrompt(it) },
                                    placeholder = { Text("What to exclude e.g. ugly, low resolution, deformed") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .testTag("negative_prompt_input"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.error,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIPELINE MODEL / ASSETS SECTION
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cyclone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PIPELINE MODELS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (modelType == ModelType.FLUX) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Using Flux pipeline (Euler, Simple, Auto CLIP & VAE)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else if (modelType == ModelType.TURBO) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Turbo model — low steps recommended",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Checkpoint Dropdown
                    ReusableDropdown(
                        label = "Checkpoint Model",
                        selectedValue = selectedCheckpoint ?: "Standard / None selected",
                        options = assets.checkpoints,
                        onValueChange = { viewModel.updateSelectedCheckpoint(it) }
                    )

                    // VAE Dropdown
                    ReusableDropdown(
                        label = "VAE Loader",
                        selectedValue = if (modelType == ModelType.FLUX) "ae.safetensors" else selectedVae,
                        options = if (modelType == ModelType.FLUX) listOf("ae.safetensors") else listOf("Automatic") + assets.vaes,
                        onValueChange = { viewModel.updateSelectedVae(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReusableDropdown(
                            label = "Sampler",
                            selectedValue = if (modelType == ModelType.FLUX) "euler" else sampler,
                            options = if (modelType == ModelType.FLUX) listOf("euler") else assets.samplers,
                            onValueChange = { viewModel.updateSelectedSampler(it) },
                            modifier = Modifier.weight(1f)
                        )

                        ReusableDropdown(
                            label = "Scheduler",
                            selectedValue = if (modelType == ModelType.FLUX) "simple" else selectedScheduler,
                            options = if (modelType == ModelType.FLUX) listOf("simple") else assets.schedulers,
                            onValueChange = { viewModel.updateSelectedScheduler(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LoRA MANAGEMENT PANEL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE LoRAs",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Add LoRA controller
                Box {
                    Button(
                        onClick = { showAddLoraMenu = !showAddLoraMenu },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add LoRA", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    DropdownMenu(
                        expanded = showAddLoraMenu,
                        onDismissRequest = { showAddLoraMenu = false }
                    ) {
                        val unusedLoras = assets.loras.filter { l -> selectedLoras.none { it.name == l } }
                        if (unusedLoras.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No unused LoRAs", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)) },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            unusedLoras.forEach { loraName ->
                                DropdownMenuItem(
                                    text = { Text(loraName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        viewModel.addLora(loraName)
                                        showAddLoraMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (selectedLoras.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active LoRAs selected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        selectedLoras.forEachIndexed { i, activeLora ->
                            if (i > 0) Spacer(modifier = Modifier.height(14.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeLora.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%.2f", activeLora.strength),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { viewModel.removeLora(activeLora.name) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete LoRA",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = activeLora.strength,
                                    onValueChange = { viewModel.updateLoraStrength(activeLora.name, it) },
                                    valueRange = -2.0f..2.0f,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Standard Sliders section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PARAMETERS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter Adjustment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Steps Slider
                    val isTurbo = modelType == ModelType.TURBO
                    val isFlux = modelType == ModelType.FLUX

                    val stepsRange = if (isTurbo) 1f..8f else 1f..50f
                    val stepsCount = if (isTurbo) 7 else 49

                    val cfgLabel = if (isFlux) "Guidance Scale" else "CFG Scale"
                    val cfgRange = if (isFlux) 1.0f..10.0f else if (isTurbo) 1.0f..2.0f else 1.0f..20.0f
                    val cfgSteps = if (isFlux) 90 else if (isTurbo) 10 else 190

                    val sizePresets = if (isFlux || modelType == ModelType.SDXL) {
                        listOf(Pair(1024, 1024), Pair(1152, 896), Pair(896, 1152), Pair(1216, 832))
                    } else {
                        listOf(Pair(512, 512), Pair(512, 768), Pair(768, 512), Pair(768, 768))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Generation Steps",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "$steps",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Slider(
                        value = steps.toFloat().coerceIn(stepsRange),
                        onValueChange = { viewModel.updateSteps(it.roundToInt()) },
                        valueRange = stepsRange,
                        steps = stepsCount,
                        modifier = Modifier.testTag("steps_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CFG Slider / Guidance Scale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cfgLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = String.format("%.1f", cfg),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Slider(
                        value = cfg.coerceIn(cfgRange),
                        onValueChange = { viewModel.updateCfg(it) },
                        valueRange = cfgRange,
                        steps = cfgSteps,
                        modifier = Modifier.testTag("cfg_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Size presets selection
                    Text(
                        text = "DIMENSIONS PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sizePresets.forEach { (w, h) ->
                            val isSelected = width == w && height == h
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateWidth(w)
                                    viewModel.updateHeight(h)
                                },
                                label = { Text("${w}x${h}", fontSize = 11.sp) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Width & Height Sliders (256 to 2048, snapping to 64)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Width",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${width}px",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Slider(
                        value = width.toFloat(),
                        onValueChange = {
                            val roundedValue = ((it / 64).roundToInt() * 64).coerceIn(256, 2048)
                            viewModel.updateWidth(roundedValue)
                        },
                        valueRange = 256f..2048f,
                        modifier = Modifier.testTag("width_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Height",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${height}px",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Slider(
                        value = height.toFloat(),
                        onValueChange = {
                            val roundedValue = ((it / 64).roundToInt() * 64).coerceIn(256, 2048)
                            viewModel.updateHeight(roundedValue)
                        },
                        valueRange = 256f..2048f,
                        modifier = Modifier.testTag("height_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Seed Row with Dice Randomizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Seed",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isSeedRandom) "Random at runtime" else "$seed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Toggle random state
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isSeedRandom) {
                                    viewModel.setUserSeedRandom(false)
                                    viewModel.updateSeed((0..999999999L).random())
                                } else {
                                    viewModel.setUserSeedRandom(true)
                                }
                            },
                            modifier = Modifier.testTag("randomize_seed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = "Randomize Seed Toggle",
                                tint = if (isSeedRandom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        if (!isSeedRandom) {
                            OutlinedTextField(
                                value = seed.toString(),
                                onValueChange = { viewModel.updateSeed(it.toLongOrNull() ?: 0L) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .width(140.dp)
                                    .testTag("seed_input_field"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ADVANCED ACCORDION (Clip skip, batch count, hires fix toggles)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADVANCED PARAMETERS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand advance panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Clip Skip slider (1 to 4)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Clip Skip",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "$clipSkip",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                            Slider(
                                value = clipSkip.toFloat(),
                                onValueChange = { viewModel.updateClipSkip(it.roundToInt()) },
                                valueRange = 1f..4f,
                                steps = 2,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Batch count slider (1 to 8)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Batch Count",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "$batchCount",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                            Slider(
                                value = batchCount.toFloat(),
                                onValueChange = { viewModel.updateBatchCount(it.roundToInt()) },
                                valueRange = 1f..8f,
                                steps = 6,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Hires Fix Sub-panel
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "High-Resolution Fix (Hires Fix)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Upscales elements with double KSampler pass",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hiresEnabled,
                                    onCheckedChange = { viewModel.updateHiresEnabled(it) }
                                )
                            }

                            AnimatedVisibility(visible = hiresEnabled) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    // Upscaler dropdown
                                    ReusableDropdown(
                                        label = "Latent Upscaling Method",
                                        selectedValue = hiresUpscaler ?: "nearest-exact",
                                        options = assets.upscaleModels.ifEmpty { listOf("nearest-exact", "bilinear", "area", "bicubic") },
                                        onValueChange = { viewModel.updateHiresUpscaler(it) }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Scale slider (1.0x to 3.0x)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Upscale Scale Factor",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = String.format("%.2fx", hiresScale),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                    }
                                    Slider(
                                        value = hiresScale,
                                        onValueChange = { viewModel.updateHiresScale(it) },
                                        valueRange = 1.0f..3.0f,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            thumbColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Hires steps (5 to 30)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Hires Steps pass",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "$hiresSteps",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                    }
                                    Slider(
                                        value = hiresSteps.toFloat(),
                                        onValueChange = { viewModel.updateHiresSteps(it.roundToInt()) },
                                        valueRange = 5f..30f,
                                        steps = 24,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            thumbColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Denoise (0.0 to 1.0)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Denoising Strength",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = String.format("%.2f", hiresDenoise),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                    }
                                    Slider(
                                        value = hiresDenoise,
                                        onValueChange = { viewModel.updateHiresDenoise(it) },
                                        valueRange = 0.0f..1.0f,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            thumbColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FACE SWAP PARAMETERS ACCORDION CARD
            Spacer(modifier = Modifier.height(16.dp))
            var showFaceSwapPanel by remember { mutableStateOf(false) }
            val genFaceSwapEnabled by viewModel.genFaceSwapEnabled.collectAsState()
            val genFaceSwapSourceFaceUri by viewModel.genFaceSwapSourceFaceUri.collectAsState()
            val genFaceSwapRestoreModel by viewModel.genFaceSwapRestoreModel.collectAsState()
            val genFaceSwapVisibility by viewModel.genFaceSwapVisibility.collectAsState()
            val genFaceSwapWeight by viewModel.genFaceSwapWeight.collectAsState()
            val genFaceSwapSourceUploading by viewModel.genFaceSwapSourceUploading.collectAsState()
            val genFaceSwapSourceUploadError by viewModel.genFaceSwapSourceUploadError.collectAsState()
            val genFaceSwapSourceFilename by viewModel.genFaceSwapSourceFilename.collectAsState()
            val settingsManager = viewModel.settingsManager
            val faceSwapEngine = settingsManager.faceSwapEngine

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.updateGenFaceSwapSourceFaceUri(uri)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("face_swap_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFaceSwapPanel = !showFaceSwapPanel },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FACE SWAP PARAMETERS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (genFaceSwapEnabled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (showFaceSwapPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand face swap panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showFaceSwapPanel) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Switch to toggle ON/OFF
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Face Swap Integration",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Swap source face into the generated character",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = genFaceSwapEnabled,
                                    onCheckedChange = { viewModel.updateGenFaceSwapEnabled(it) },
                                    modifier = Modifier.testTag("gen_face_swap_switch")
                                )
                            }

                            if (genFaceSwapEnabled) {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Source Face Selector
                                Text(
                                    text = "Source Target Face Image",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { launcher.launch("image/*") }
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (genFaceSwapSourceFaceUri != null) {
                                            AsyncImage(
                                                model = genFaceSwapSourceFaceUri,
                                                contentDescription = "Selected Target Face Thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = "No photo selected icon",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Button(
                                            onClick = { launcher.launch("image/*") },
                                            modifier = Modifier.testTag("select_source_face_button"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(if (genFaceSwapSourceFaceUri != null) "Change Photo" else "Select Photo")
                                        }
                                        if (genFaceSwapSourceUploading) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Uploading to server...",
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else if (genFaceSwapSourceUploadError != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = genFaceSwapSourceUploadError ?: "Upload failed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else if (genFaceSwapSourceFilename != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Uploaded: $genFaceSwapSourceFilename",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (genFaceSwapSourceFaceUri != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Selected locally",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Face Swap Engine configuration indicator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Swapping Pipeline Engine",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "Determined in global settings panel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = faceSwapEngine.uppercase(),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                if (faceSwapEngine == "reactor") {
                                    val reactorLoading by viewModel.reactorLoading.collectAsState()
                                    val reactorError by viewModel.reactorError.collectAsState()
                                    val reactorNodeInfo by viewModel.reactorNodeInfo.collectAsState()

                                    val selectedSwapModel by viewModel.reactorSelectedSwapModel.collectAsState()
                                    val selectedDetect by viewModel.reactorSelectedFaceDetection.collectAsState()
                                    val selectedFaceRestore by viewModel.reactorSelectedRestoreModel.collectAsState()
                                    val restoreVisibility by viewModel.reactorRestoreVisibility.collectAsState()
                                    val codeformerWeight by viewModel.reactorCodeformerWeight.collectAsState()
                                    val selectedGenSource by viewModel.reactorSelectedGenderSource.collectAsState()
                                    val selectedGenInput by viewModel.reactorSelectedGenderInput.collectAsState()
                                    val sourceFacesIndex by viewModel.reactorSourceFacesIndex.collectAsState()
                                    val inputFacesIndex by viewModel.reactorInputFacesIndex.collectAsState()

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (reactorLoading) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Fetching ReActor configuration...", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    } else if (reactorError != null) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = reactorError ?: "ReActor error",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(
                                                    onClick = { viewModel.fetchReActorOptions() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Text("Retry Connect", color = Color.White)
                                                }
                                            }
                                        }
                                    } else if (reactorNodeInfo != null && reactorNodeInfo!!.isAvailable) {
                                        val info = reactorNodeInfo!!

                                        // Swap Model Selector
                                        var showSwapMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Swap Model",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Box {
                                                Button(
                                                    onClick = { showSwapMenu = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text(selectedSwapModel)
                                                }
                                                DropdownMenu(
                                                    expanded = showSwapMenu,
                                                    onDismissRequest = { showSwapMenu = false }
                                                ) {
                                                    info.swapModels.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                viewModel.updateReactorSelectedSwapModel(opt)
                                                                showSwapMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Face Detection Selector
                                        var showDetectMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Face Detection",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Box {
                                                Button(
                                                    onClick = { showDetectMenu = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text(selectedDetect)
                                                }
                                                DropdownMenu(
                                                    expanded = showDetectMenu,
                                                    onDismissRequest = { showDetectMenu = false }
                                                ) {
                                                    info.faceDetections.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                viewModel.updateReactorSelectedFaceDetection(opt)
                                                                showDetectMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Restore Model Selector
                                        var showRestoreModelDropdown by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Face Restoration Model",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Box {
                                                Button(
                                                    onClick = { showRestoreModelDropdown = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text(viewModel.getFriendlyRestoreModelName(selectedFaceRestore).uppercase())
                                                }
                                                DropdownMenu(
                                                    expanded = showRestoreModelDropdown,
                                                    onDismissRequest = { showRestoreModelDropdown = false }
                                                ) {
                                                    info.faceRestoreModels.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(viewModel.getFriendlyRestoreModelName(opt)) },
                                                            onClick = {
                                                                viewModel.updateReactorSelectedRestoreModel(opt)
                                                                showRestoreModelDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Face Restore Visibility Slider
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Face Restore Visibility",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = String.format("%.2f", restoreVisibility),
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                            )
                                        }
                                        Slider(
                                            value = restoreVisibility,
                                            onValueChange = { viewModel.updateReactorRestoreVisibility(it) },
                                            valueRange = info.faceRestoreVisibilityMin..info.faceRestoreVisibilityMax,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                thumbColor = MaterialTheme.colorScheme.primary
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Codeformer Weight Slider
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "CodeFormer Weight",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = String.format("%.2f", codeformerWeight),
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                                            )
                                        }
                                        Slider(
                                            value = codeformerWeight,
                                            onValueChange = { viewModel.updateReactorCodeformerWeight(it) },
                                            valueRange = info.codeformerWeightMin..info.codeformerWeightMax,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                thumbColor = MaterialTheme.colorScheme.primary
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Detect Gender Source Selector
                                        var showGenSrcMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Detect Gender Source",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Box {
                                                Button(
                                                    onClick = { showGenSrcMenu = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text(selectedGenSource)
                                                }
                                                DropdownMenu(
                                                    expanded = showGenSrcMenu,
                                                    onDismissRequest = { showGenSrcMenu = false }
                                                ) {
                                                    info.detectGenderSources.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                viewModel.updateReactorSelectedGenderSource(opt)
                                                                showGenSrcMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Detect Gender Input Selector
                                        var showGenInputMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Detect Gender Input",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Box {
                                                Button(
                                                    onClick = { showGenInputMenu = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text(selectedGenInput)
                                                }
                                                DropdownMenu(
                                                    expanded = showGenInputMenu,
                                                    onDismissRequest = { showGenInputMenu = false }
                                                ) {
                                                    info.detectGenderInputs.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                viewModel.updateReactorSelectedGenderInput(opt)
                                                                showGenInputMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Source Faces Index
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Source Faces Index",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = sourceFacesIndex,
                                                onValueChange = { viewModel.updateReactorSourceFacesIndex(it) },
                                                placeholder = { Text("0") },
                                                modifier = Modifier.width(100.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Input Faces Index
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Input Faces Index",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = inputFacesIndex,
                                                onValueChange = { viewModel.updateReactorInputFacesIndex(it) },
                                                placeholder = { Text("0") },
                                                modifier = Modifier.width(100.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )
                                        }

                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "ReActor extension not found on server. Please install ComfyUI-ReActor and restart ComfyUI.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Real-time Preview / Progress Card
            AnimatedVisibility(visible = isGenerating || previewBitmap != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE PREVIEW",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )

                            if (isGenerating) {
                                Text(
                                    text = "Step $currentStep/$totalSteps (Queue: $queueRemaining)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Generator Preview Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Awaiting live stream...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (isGenerating) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = if (totalSteps > 0) currentStep.toFloat() / totalSteps else 0f,
                                modifier = Modifier.fillMaxWidth()
                                    .height(6.dp)
                                        .clip(RoundedCornerShape(100.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { viewModel.cancelGeneration() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cancel Generation",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Generate Bottom Sheet / Float Button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.triggerGeneration()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("generate_artwork_button"),
                    enabled = !isGenerating && positivePrompt.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Generate Artwork", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showHistoryDialog) {
            PromptHistoryDialog(
                history = promptHistory,
                favorites = favoritePrompts,
                onSelect = { selectedPrompt ->
                    viewModel.updatePositivePrompt(selectedPrompt)
                    showHistoryDialog = false
                },
                onToggleFavorite = { promptText, isFav ->
                    viewModel.toggleFavoritePrompt(promptText, isFav)
                },
                onDismiss = { showHistoryDialog = false }
            )
        }
    }
}

@Composable
fun PromptHistoryDialog(
    history: List<com.example.data.database.PromptHistory>,
    favorites: List<com.example.data.database.FavoritePrompt>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = History, 1 = Starred

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Prompts Panel",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Recent History") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Starred") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        if (history.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No recent history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                history.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(item.prompt) }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.prompt,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        val isCurrentlyFavorite = favorites.any { it.prompt == item.prompt }
                                        IconButton(
                                            onClick = { onToggleFavorite(item.prompt, !isCurrentlyFavorite) }
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentlyFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Star Prompt Toggle",
                                                tint = if (isCurrentlyFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    } else {
                         if (favorites.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No starred prompts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                favorites.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(item.prompt) }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.prompt,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        IconButton(
                                            onClick = { onToggleFavorite(item.prompt, false) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Unstar prompt",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
