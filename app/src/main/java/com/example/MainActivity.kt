package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.FaceSwapScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.GenerateScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.WorkflowsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Retrieve dynamic theme setting
            var activeThemeState by remember { mutableStateOf(viewModel.settingsManager.appTheme) }
            val systemDark = isSystemInDarkTheme()

            val isDarkTheme = remember(activeThemeState, systemDark) {
                when (activeThemeState) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }
            }

            // Bind layout state
            var isConfigured by remember {
                mutableStateOf(viewModel.settingsManager.serverIp.isNotEmpty())
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                if (!isConfigured) {
                    SetupScreen(
                        viewModel = viewModel,
                        onSetupComplete = {
                            isConfigured = true
                            // Trigger full theme configuration reload
                            activeThemeState = viewModel.settingsManager.appTheme
                        }
                    )
                } else {
                    MainWorkspace(
                        viewModel = viewModel,
                        onThemeChanged = {
                            activeThemeState = viewModel.settingsManager.appTheme
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainWorkspace(
    viewModel: MainViewModel,
    onThemeChanged: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }

    // Trigger theme update if Settings redraws
    LaunchedEffect(activeTab) {
        onThemeChanged()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Palette, contentDescription = "Generate") },
                    label = { Text("Generate") },
                    modifier = Modifier.testTag("nav_generate_tab")
                )

                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "Face Swap") },
                    label = { Text("Face Swap") },
                    modifier = Modifier.testTag("nav_faceswap_tab")
                )

                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                    label = { Text("Gallery") },
                    modifier = Modifier.testTag("nav_gallery_tab")
                )

                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Hub, contentDescription = "Workflows") },
                    label = { Text("Workflows") },
                    modifier = Modifier.testTag("nav_workflows_tab")
                )

                NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings_tab")
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> GenerateScreen(viewModel = viewModel)
                1 -> FaceSwapScreen(viewModel = viewModel)
                2 -> GalleryScreen(
                    viewModel = viewModel,
                    onNavigateToGenerate = { activeTab = 0 }
                )
                3 -> WorkflowsScreen(
                    viewModel = viewModel,
                    onWorkflowLoaded = { activeTab = 0 }
                )
                4 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
