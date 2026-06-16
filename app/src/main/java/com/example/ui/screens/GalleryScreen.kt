package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.database.GeneratedImage
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onNavigateToGenerate: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawImages by viewModel.allImages.collectAsState(initial = emptyList())
    val gridColumns by viewModel.galleryGridColumns.collectAsState()
    val sortOrder by viewModel.gallerySortOrder.collectAsState()
    val filterWorkflow by viewModel.galleryFilterWorkflow.collectAsState()

    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedImages by viewModel.selectedGalleryImages.collectAsState()

    var activeImageForViewer by remember { mutableStateOf<GeneratedImage?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    // Format final sorted & filtered images
    val displayedImages = remember(rawImages, sortOrder, filterWorkflow) {
        val fWorkflow = filterWorkflow
        var list = rawImages.filter { img ->
            fWorkflow == null || img.workflowName == fWorkflow || img.prompt.contains(fWorkflow, ignoreCase = true)
        }
        if (sortOrder == "oldest") {
            list = list.sortedBy { it.timestamp }
        } else {
            list = list.sortedByDescending { it.timestamp }
        }
        list
    }

    // Extract unique workflow/prompt categories for filtering
    val uniqueFilters = remember(rawImages) {
        val set = mutableSetOf<String>()
        rawImages.forEach { img ->
            if (!img.workflowName.isNullOrEmpty()) {
                set.add(img.workflowName)
            } else {
                // heuristic: first word of prompt
                val firstWord = img.prompt.split(" ").firstOrNull()?.replace(",", "")?.replace(".", "")?.trim()
                if (!firstWord.isNullOrEmpty() && firstWord.length > 3) {
                    set.add(firstWord)
                }
            }
        }
        set.toList()
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMultiSelectMode) "${selectedImages.size} Selected" else "Gallery",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMultiSelectMode) {
                                IconButton(onClick = { viewModel.deleteSelectedImages() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { viewModel.exitMultiSelectMode() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit select")
                                }
                            } else {
                                // Toggle Grid Dimensions
                                IconButton(
                                    onClick = {
                                        viewModel.setGalleryGridColumns(if (gridColumns == 2) 3 else 2)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (gridColumns == 2) Icons.Default.GridView else Icons.Default.ViewColumn,
                                        contentDescription = "Toggle Grid Columns"
                                    )
                                }

                                // Sorting toggle
                                IconButton(
                                    onClick = {
                                        viewModel.setGallerySortOrder(if (sortOrder == "newest") "oldest" else "newest")
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (sortOrder == "newest") Icons.Default.SortByAlpha else Icons.Default.VerticalAlignBottom,
                                        contentDescription = "Toggle sorting order"
                                    )
                                }

                                // Filtering Dropdown
                                Box {
                                    IconButton(onClick = { filterMenuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter Images",
                                            tint = if (filterWorkflow != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = filterMenuExpanded,
                                        onDismissRequest = { filterMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Images") },
                                            onClick = {
                                                viewModel.setGalleryFilterWorkflow(null)
                                                filterMenuExpanded = false
                                            }
                                        )
                                        uniqueFilters.forEach { fName ->
                                            DropdownMenuItem(
                                                text = { Text(fName) },
                                                onClick = {
                                                    viewModel.setGalleryFilterWorkflow(fName)
                                                    filterMenuExpanded = false
                                                }
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
    ) { innerPadding ->
        if (displayedImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AppShortcut,
                        contentDescription = "Empty Gallery",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No generated artwork found.", style = MaterialTheme.typography.titleMedium)
                    if (filterWorkflow != null) {
                        Text(
                            "No match for '$filterWorkflow'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            "Head over to Generate screen to create some!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("gallery_grid")
            ) {
                items(displayedImages) { image ->
                    val isSelected = selectedImages.contains(image)
                    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        .format(Date(image.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.82f)
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        viewModel.toggleSelectImage(image)
                                    } else {
                                        activeImageForViewer = image
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        viewModel.startMultiSelectMode(image)
                                    } else {
                                        viewModel.toggleSelectImage(image)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1.2f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = File(image.localPath)),
                                        contentDescription = "Gallery Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    if (image.isFavorite) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp))
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick text details under card thumbnail
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.5f)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = image.prompt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${image.width}x${image.height}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = dateStr,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Selection overlay
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Viewer with zoom, share, delete, etc.
        activeImageForViewer?.let { activeImage ->
            FullScreenViewerDialog(
                image = activeImage,
                onDismiss = { activeImageForViewer = null },
                onShare = {
                    shareImage(context, activeImage)
                },
                onDelete = {
                    viewModel.deleteImage(activeImage)
                    activeImageForViewer = null
                    Toast.makeText(context, "Image deleted.", Toast.LENGTH_SHORT).show()
                },
                onCopyPrompt = {
                    clipboardManager.setText(AnnotatedString(activeImage.prompt))
                    Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                onReGenerate = {
                    viewModel.loadSettingsFromImage(activeImage)
                    activeImageForViewer = null
                    onNavigateToGenerate()
                    Toast.makeText(context, "Generation parameters loaded back!", Toast.LENGTH_SHORT).show()
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite(activeImage)
                }
            )
        }
    }
}

@Composable
fun FullScreenViewerDialog(
    image: GeneratedImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopyPrompt: () -> Unit,
    onReGenerate: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Interactive pinch-to-zoom container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .clickable {
                        // Reset zoom on tap
                        scale = 1f
                        offset = Offset.Zero
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = File(image.localPath)),
                    contentDescription = "Full visual zoom",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            // Top bar controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Star toggle button
                    var isFav by remember { mutableStateOf(image.isFavorite) }
                    IconButton(
                        onClick = {
                            isFav = !isFav
                            onToggleFavorite()
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (isFav) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    IconButton(
                        onClick = onCopyPrompt,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy prompt", tint = Color.White)
                    }

                    IconButton(
                        onClick = onShare,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }

                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }

            // Bottom drawer details & Re-generate button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = image.prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Steps: ${image.steps} | CFG: ${image.cfg} | Seed: ${image.seed} | Size: ${image.width}x${image.height}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onReGenerate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-generate with these settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Share helper function
fun shareImage(context: Context, image: GeneratedImage) {
    try {
        val file = File(image.localPath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Generated via ComfyPad:\n\n${image.prompt}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share generated artwork"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
