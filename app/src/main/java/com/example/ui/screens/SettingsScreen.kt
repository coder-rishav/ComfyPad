package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settingsManager = viewModel.settingsManager

    // State bindings
    var ip by remember { mutableStateOf(settingsManager.serverIp) }
    var portStr by remember { mutableStateOf(settingsManager.serverPort.toString()) }
    var stepsStr by remember { mutableStateOf(settingsManager.defaultSteps.toString()) }
    var cfgStr by remember { mutableStateOf(settingsManager.defaultCfg.toString()) }
    var widthStr by remember { mutableStateOf(settingsManager.defaultWidth.toString()) }
    var heightStr by remember { mutableStateOf(settingsManager.defaultHeight.toString()) }

    var selectedTheme by remember { mutableStateOf(settingsManager.appTheme) }
    var saveToGallery by remember { mutableStateOf(settingsManager.saveToGallery) }

    val connectionResult by viewModel.connectionResult.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = 50.dp) // buffer
    ) {
        // Name & description
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = "Customize server connectivity, UI style, and defaults",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Connection Card
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SERVER CONFIGURATION",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = {
                        ip = it
                        settingsManager.serverIp = it.trim()
                    },
                    label = { Text("Server IP Address", style = MaterialTheme.typography.labelMedium) },
                    placeholder = { Text("e.g. 192.168.1.100") },
                    leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth().testTag("settings_ip_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                OutlinedTextField(
                    value = portStr,
                    onValueChange = {
                        portStr = it
                        val p = it.toIntOrNull() ?: 8188
                        settingsManager.serverPort = p
                    },
                    label = { Text("Server Port", style = MaterialTheme.typography.labelMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("settings_port_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Button(
                    onClick = {
                        viewModel.setServerConfig(ip.trim(), portStr.toIntOrNull() ?: 8188)
                        viewModel.testConnection()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isTesting,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test and Save Connection", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                connectionResult?.let { res ->
                    val isSuccess = res.startsWith("Success")
                    Surface(
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        contentColor = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Default generation parameters Card
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "GENERATION DEFAULT OVERRIDES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = stepsStr,
                        onValueChange = {
                            stepsStr = it
                            it.toIntOrNull()?.let { steps ->
                                settingsManager.defaultSteps = steps
                                viewModel.updateSteps(steps)
                            }
                        },
                        label = { Text("Steps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = cfgStr,
                        onValueChange = {
                            cfgStr = it
                            it.toFloatOrNull()?.let { cfg ->
                                settingsManager.defaultCfg = cfg
                                viewModel.updateCfg(cfg)
                            }
                        },
                        label = { Text("CFG Scale") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = {
                            widthStr = it
                            it.toIntOrNull()?.let { w ->
                                settingsManager.defaultWidth = w
                                viewModel.updateWidth(w)
                            }
                        },
                        label = { Text("Width px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = {
                            heightStr = it
                            it.toIntOrNull()?.let { h ->
                                settingsManager.defaultHeight = h
                                viewModel.updateHeight(h)
                            }
                        },
                        label = { Text("Height px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // GLOBAL FACE SWAP SETTINGS CARD
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "GLOBAL FACE SWAP SETTINGS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_face_swap_card"),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                Text(
                    text = "Default Face Swap Engine",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                var showEngineDropdown by remember { mutableStateOf(false) }
                val currentEngine = settingsManager.faceSwapEngine
                
                Box {
                    Button(
                        onClick = { showEngineDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (currentEngine == "reactor") "RE-ACTOR NODE (STANDARD COMFYUI)" else "FACEFUSION REST API (EXTERNAL POST-PROCESSING)")
                    }
                    DropdownMenu(
                        expanded = showEngineDropdown,
                        onDismissRequest = { showEngineDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("ReActor Node (Standard ComfyUI Workflow)") },
                            onClick = {
                                settingsManager.faceSwapEngine = "reactor"
                                showEngineDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("FaceFusion REST API (External Server)") },
                            onClick = {
                                settingsManager.faceSwapEngine = "facefusion"
                                showEngineDropdown = false
                            }
                        )
                    }
                }
                
                if (currentEngine == "facefusion") {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var facefusionUrlStr by remember { mutableStateOf(settingsManager.faceFusionUrl) }
                    
                    OutlinedTextField(
                        value = facefusionUrlStr,
                        onValueChange = {
                            facefusionUrlStr = it
                            settingsManager.faceFusionUrl = it.trim()
                        },
                        label = { Text("FaceFusion REST API URL", style = MaterialTheme.typography.labelMedium) },
                        placeholder = { Text("e.g. http://192.168.1.100:7860") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_facefusion_url"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Style & Preferences Cards
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "APP CUSTOMIZER",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Theme Toggle
                Text("App Theme Mode", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("dark", "light", "system")
                    themes.forEach { theme ->
                        val isSelected = selectedTheme == theme
                        Button(
                            onClick = {
                                selectedTheme = theme
                                settingsManager.appTheme = theme
                                Toast.makeText(context, "Restart app or toggle tab to trigger full theme redraw", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(theme.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Save to Gallery switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save to Device Photos", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Automatically index generated images into your public Photos app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Switch(
                        checked = saveToGallery,
                        onCheckedChange = {
                            saveToGallery = it
                            settingsManager.saveToGallery = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Clear Cache Button
                Button(
                    onClick = {
                        viewModel.clearCache()
                        Toast.makeText(context, "Local image download cache successfully cleared!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purge Local Cache Images", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Version info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ComfyPad Client", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text("v1.0.0 Stable (API 36)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Developed for ComfyUI REST & WebSocket API Integration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
