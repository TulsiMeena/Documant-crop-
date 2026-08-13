package com.example.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.PreferencesViewModel
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.ui.components.BottomNavigationBar
import com.example.ui.components.BottomTab
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.DocumentListViewModel

@Composable
fun MainContainer(
    preferencesViewModel: PreferencesViewModel,
    documentListViewModel: DocumentListViewModel,
    onOpenScanner: () -> Unit,
    onRevisitOnboarding: () -> Unit,
    onDocumentClick: (ScannedDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

    val appTheme by preferencesViewModel.appTheme.collectAsState()
    val defaultScanMode by preferencesViewModel.defaultScanMode.collectAsState()
    val autoCropEnabled by preferencesViewModel.autoCropEnabled.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_container"),
        bottomBar = {
            BottomNavigationBar(
                currentTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    BottomTab.HOME -> {
                        HomeScreen(
                            viewModel = documentListViewModel,
                            onScanClick = onOpenScanner,
                            onImportClick = onOpenScanner,
                            onSettingsClick = {
                                selectedTab = BottomTab.SETTINGS
                            },
                            onViewAllClick = {
                                selectedTab = BottomTab.DOCUMENTS
                            },
                            onDocumentClick = onDocumentClick
                        )
                    }

                    BottomTab.DOCUMENTS -> {
                        DocumentsScreen(
                            viewModel = documentListViewModel,
                            onScanClick = onOpenScanner,
                            onSettingsClick = {
                                selectedTab = BottomTab.SETTINGS
                            },
                            onDocumentClick = onDocumentClick
                        )
                    }

                    BottomTab.SETTINGS -> {
                        SettingsScreen(
                            currentTheme = appTheme,
                            onThemeChange = { preferencesViewModel.setAppTheme(it) },
                            defaultScanMode = defaultScanMode,
                            onScanModeChange = { preferencesViewModel.setDefaultScanMode(it) },
                            autoCropEnabled = autoCropEnabled,
                            onAutoCropChange = { preferencesViewModel.setAutoCropEnabled(it) },
                            onResetOnboarding = onRevisitOnboarding
                        )
                    }
                }
            }
        }
    }
}
