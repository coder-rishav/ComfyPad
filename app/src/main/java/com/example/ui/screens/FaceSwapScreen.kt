package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun FaceSwapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Screen-level state for sub-tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("SOURCE FACE", "TARGET IMAGE", "ENGINE CONFIG")

    // Collect variables from ViewModel
    val dediSourceUri by viewModel.dediFaceSwapSourceUri.collectAsState()
    val dediTargetUri by viewModel.dediFaceSwapTargetUri.collectAsState()
    
    val isSwapping by viewModel.isSwapping.collectAsState()
    val swapResultImage by viewModel.swapResultImage.collectAsState()
    val swapError by viewModel.swapError.collectAsState()

    val engine = viewModel.settingsManager.faceSwapEngine
    val allImages by viewModel.allImages.collectAsState(initial = emptyList())

    // Activity launchers for selecting images
    val sourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateDediFaceSwapSourceUri(uri)
        }
    }

    val targetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateDediFaceSwapTargetUri(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        // HEADER ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "DEDICATED FACE SWAP",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Overlay faces onto characters with surgical precision",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("face_swap_sub_tab_$index"),
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                )
            }
        }

        // TABS BODY AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // SOURCE FACE UPLOAD
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Identify Target Face",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { sourceLauncher.launch("image/*") }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dediSourceUri != null) {
                                    AsyncImage(
                                        model = dediSourceUri,
                                        contentDescription = "Source Face Uploaded",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(24.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.15f))
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Tap to choose Source Face",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Supports JPEG, PNG up to 10MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        if (dediSourceUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { sourceLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("select_source_face_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Change Photo")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.updateDediFaceSwapSourceUri(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear")
                                }
                            }
                        }

                        // Guidelines note card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Photo Requirements",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Use well-lit, direct, front-facing target camera angles. High contrast, obscured features, extreme tilt, or heavy makeup can distort swapping fidelity.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TARGET IMAGE SELECTION
                    var targetSourceSubTab by remember { mutableStateOf(0) }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Target Canvas",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Sub-segment tab
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (targetSourceSubTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { targetSourceSubTab = 0 }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "UPLOAD",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (targetSourceSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (targetSourceSubTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { targetSourceSubTab = 1 }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "GENERATED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (targetSourceSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (targetSourceSubTab == 0) {
                            // DEVICE UPLOAD
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = { targetLauncher.launch("image/*") }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dediTargetUri != null && !dediTargetUri.toString().contains("comfypad_")) {
                                        AsyncImage(
                                            model = dediTargetUri,
                                            contentDescription = "Target Image Uploaded",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(24.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.15f))
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Tap to choose Target Canvas Picture",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (dediTargetUri != null && !dediTargetUri.toString().contains("comfypad_")) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { targetLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Change Target")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.updateDediFaceSwapTargetUri(null) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Clear")
                                    }
                                }
                            }
                        } else {
                            // CHOOSE FROM GENERATED GALLERY IMAGES
                            if (allImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No images available in Gallery.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(allImages) { img ->
                                        val isSelected = dediTargetUri?.toString() == img.localPath || dediTargetUri?.toString() == Uri.fromFile(File(img.localPath)).toString()
                                        Card(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clickable {
                                                    val u = Uri.fromFile(File(img.localPath))
                                                    viewModel.updateDediFaceSwapTargetUri(u)
                                                }
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = img.localPath,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.TopEnd
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Selected Target",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(6.dp).size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // ENGINE SPECIFIC PARAMETERS CONFIG
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Core Swapping Pipeline",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // Info indicator card about Engine currently active
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Active Swapping Engine",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (engine == "reactor") "ComfyUI Workflow Embedded" else "Standalone External Server",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = engine.uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (engine == "reactor") {
                            // REACTOR CONFIGS
                            val reactorLoading by viewModel.reactorLoading.collectAsState()
                            val reactorError by viewModel.reactorError.collectAsState()
                            val reactorNodeInfo by viewModel.reactorNodeInfo.collectAsState()

                            Text(
                                text = "ReActor Settings (ComfyUI)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (reactorLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Fetching ReActor configuration...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else if (reactorError != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = reactorError ?: "ReActor error",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                
                                // Swap Model Dropdown
                                val selectedSwapModel by viewModel.reactorSelectedSwapModel.collectAsState()
                                var showSwapMenu by remember { mutableStateOf(false) }

                                Column {
                                    Text(
                                        text = "Swap Model",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box {
                                        OutlinedButton(
                                            onClick = { showSwapMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(selectedSwapModel, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showSwapMenu,
                                            onDismissRequest = { showSwapMenu = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            info.swapModels.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { Text(model) },
                                                    onClick = {
                                                        viewModel.updateReactorSelectedSwapModel(model)
                                                        showSwapMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Face Detection Dropdown
                                val selectedDetect by viewModel.reactorSelectedFaceDetection.collectAsState()
                                var showDetectMenu by remember { mutableStateOf(false) }

                                Column {
                                    Text(
                                        text = "Face Detection",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box {
                                        OutlinedButton(
                                            onClick = { showDetectMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(selectedDetect, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showDetectMenu,
                                            onDismissRequest = { showDetectMenu = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            info.faceDetections.forEach { detect ->
                                                DropdownMenuItem(
                                                    text = { Text(detect) },
                                                    onClick = {
                                                        viewModel.updateReactorSelectedFaceDetection(detect)
                                                        showDetectMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Face Restoration Model Dropdown
                                val selectedFaceRestore by viewModel.reactorSelectedRestoreModel.collectAsState()
                                var showFaceRestoreMenu by remember { mutableStateOf(false) }

                                Column {
                                    Text(
                                        text = "Face Restoration Model",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box {
                                        OutlinedButton(
                                            onClick = { showFaceRestoreMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(viewModel.getFriendlyRestoreModelName(selectedFaceRestore), fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showFaceRestoreMenu,
                                            onDismissRequest = { showFaceRestoreMenu = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            info.faceRestoreModels.forEach { restore ->
                                                DropdownMenuItem(
                                                    text = { Text(viewModel.getFriendlyRestoreModelName(restore)) },
                                                    onClick = {
                                                        viewModel.updateReactorSelectedRestoreModel(restore)
                                                        showFaceRestoreMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Face Restore Visibility Slider
                                val restoreVisibility by viewModel.reactorRestoreVisibility.collectAsState()
                                Column {
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
                                            fontWeight = FontWeight.Bold
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
                                }

                                // Codeformer Weight Slider
                                val codeformerWeight by viewModel.reactorCodeformerWeight.collectAsState()
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Codeformer Restoring Weight",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = String.format("%.2f", codeformerWeight),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
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
                                }

                                // Detect Gender Source Dropdown
                                val selectedGenSource by viewModel.reactorSelectedGenderSource.collectAsState()
                                var showGenSrcMenu by remember { mutableStateOf(false) }

                                Column {
                                    Text(
                                        text = "Detect Gender Source",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box {
                                        OutlinedButton(
                                            onClick = { showGenSrcMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(selectedGenSource, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showGenSrcMenu,
                                            onDismissRequest = { showGenSrcMenu = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            info.detectGenderSources.forEach { gender ->
                                                DropdownMenuItem(
                                                    text = { Text(gender) },
                                                    onClick = {
                                                        viewModel.updateReactorSelectedGenderSource(gender)
                                                        showGenSrcMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Detect Gender Input Dropdown
                                val selectedGenInput by viewModel.reactorSelectedGenderInput.collectAsState()
                                var showGenInputMenu by remember { mutableStateOf(false) }

                                Column {
                                    Text(
                                        text = "Detect Gender Input",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box {
                                        OutlinedButton(
                                            onClick = { showGenInputMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(selectedGenInput, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showGenInputMenu,
                                            onDismissRequest = { showGenInputMenu = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            info.detectGenderInputs.forEach { gender ->
                                                DropdownMenuItem(
                                                    text = { Text(gender) },
                                                    onClick = {
                                                        viewModel.updateReactorSelectedGenderInput(gender)
                                                        showGenInputMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Source Faces Index Input Field
                                val sourceFacesIndex by viewModel.reactorSourceFacesIndex.collectAsState()
                                Column {
                                    Text(
                                        text = "Source Faces Index",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = sourceFacesIndex,
                                        onValueChange = { viewModel.updateReactorSourceFacesIndex(it) },
                                        placeholder = { Text("e.g. 0, 1") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }

                                // Input Faces Index Input Field
                                val inputFacesIndex by viewModel.reactorInputFacesIndex.collectAsState()
                                Column {
                                    Text(
                                        text = "Input Faces Index",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = inputFacesIndex,
                                        onValueChange = { viewModel.updateReactorInputFacesIndex(it) },
                                        placeholder = { Text("e.g. 0, 1") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "ReActor extension not found on server. Please install ComfyUI-ReActor and restart ComfyUI.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = { viewModel.fetchReActorOptions() }) {
                                            Text("Refresh Status")
                                        }
                                    }
                                }
                            }
                        } else {
                            // FACEFUSION CONFIGS
                            Text(
                                text = "FaceFusion Configurations",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Mode selection
                            val ffMode by viewModel.facefusionMode.collectAsState()
                            var showFfModeMenu by remember { mutableStateOf(false) }

                            Column {
                                Text(
                                    text = "Fusion Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box {
                                    OutlinedButton(
                                        onClick = { showFfModeMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(ffMode.uppercase(), fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showFfModeMenu,
                                        onDismissRequest = { showFfModeMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("REFERENCE") },
                                            onClick = {
                                                viewModel.updateFaceFusionMode("reference")
                                                showFfModeMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("SWAP") },
                                            onClick = {
                                                viewModel.updateFaceFusionMode("swap")
                                                showFfModeMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Distance Slider
                            val ffDistance by viewModel.facefusionDistance.collectAsState()
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Face Similarity Similarity",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = String.format("%.2f", ffDistance),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = ffDistance,
                                    onValueChange = { viewModel.updateFaceFusionDistance(it) },
                                    valueRange = 0f..1.5f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            // Enhancer Selection
                            val ffEnhancer by viewModel.facefusionEnhancer.collectAsState()
                            var showFfEnMenu by remember { mutableStateOf(false) }

                            Column {
                                Text(
                                    text = "Target Enhancer",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box {
                                    OutlinedButton(
                                        onClick = { showFfEnMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(ffEnhancer.uppercase(), fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showFfEnMenu,
                                        onDismissRequest = { showFfEnMenu = false }
                                    ) {
                                        val boosters = listOf("none", "gfpgan", "codeformer", "restoreformer")
                                        boosters.forEach { item ->
                                            DropdownMenuItem(
                                                text = { Text(item.uppercase()) },
                                                onClick = {
                                                    viewModel.updateFaceFusionEnhancer(item)
                                                    showFfEnMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Quality slider
                            val ffQuality by viewModel.facefusionQuality.collectAsState()
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Render Encoding Jpeg Quality",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "$ffQuality%",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = ffQuality.toFloat(),
                                    onValueChange = { viewModel.updateFaceFusionQuality(it.toInt()) },
                                    valueRange = 10f..100f,
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

        // BOTTOM RUN EXECUTION AREA
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Symmetrical Source + Target display visual flow leading to result
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source node circular thumbnail
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dediSourceUri != null) {
                            AsyncImage(
                                model = dediSourceUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // Target node circular thumbnail
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dediTargetUri != null) {
                            AsyncImage(
                                model = dediTargetUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Error layout if present
                if (swapError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = "Error icon", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = swapError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearSwapResult() }) {
                                Icon(Icons.Default.Close, contentDescription = "dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Swapped image preview container if present
                if (swapResultImage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = swapResultImage?.localPath,
                                        contentDescription = "Swapped Outcome",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Successful Swap",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Saved in App gallery",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.clearSwapResult() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear result", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Run swap execute button
                val buttonEnabled = dediSourceUri != null && dediTargetUri != null && !isSwapping
                Button(
                    onClick = { viewModel.executeDedicatedFaceSwap(context) },
                    enabled = buttonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_face_swap_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSwapping) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Merging Faces...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INTENSIFY FACE SWAP",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
